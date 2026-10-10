package com.anindra.messages.sms

import android.content.Context
import android.telephony.TelephonyManager
import com.anindra.messages.data.SimCards
import com.anindra.messages.mms.Mms
import com.anindra.messages.mms.RoamingAwareAutoDownload
import com.anindra.messages.mms.SendAddressSource
import com.anindra.messages.mms.debug.LogcatMmsDiagnostics
import com.anindra.messages.mms.debug.MmsDebugRecorder
import com.anindra.messages.mms.fit.DefaultAttachmentFitter
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.MmsDiagnostics
import com.anindra.messages.mms.spi.MmsDownloadTarget
import com.anindra.messages.mms.store.MmsStore
import com.anindra.messages.mms.store.TelephonyMmsStore
import com.anindra.messages.mms.transport.SmsManagerMmsPlatform
import com.anindra.messages.mms.transport.SystemMmsTransport
import com.anindra.messages.mms.transport.TransportRegistry

/**
 * Wires the `:mms` stack for this app.
 *
 * The platform transport is the default because the modem, the MMSC and the
 * notification response all stay where the carrier expects them. Everything
 * platform-shaped is injected here: the store writes the provider, the
 * download target names the row an announced message is fetched into, and the
 * transport writes the composed PDU into the cache root the app's FileProvider
 * exposes (`file_paths.xml`), so those two cannot drift apart.
 */
internal object MmsFacade {

    /** "whatever SIM carries the default SMS role", the app's convention. */
    private const val DEFAULT_SUBSCRIPTION = -1

    /**
     * One recorder per process, so a Diagnostics read and a send see the same
     * history. The stack has no other way to report what it did: every
     * `MmsDiagnostics` callback defaults to a no-op, and a default-constructed
     * stack is silent.
     */
    private val recorder = MmsDebugRecorder()

    /**
     * One per process: `CarrierProfileStore.create` registers a carrier-config
     * receiver on the application context, so building a store per send would
     * leak one receiver per message.
     */
    @Volatile
    private var profileStore: CarrierProfileStore? = null

    /** The recent MMS events, oldest first. Diagnostics prints these as text. */
    fun trace(): List<com.anindra.messages.mms.debug.MmsDebugRecorder.MmsEvent> = recorder.snapshot()

    /** Records an event from outside the `:mms` stack (downloads, provider reads). */
    val diagnostics: com.anindra.messages.mms.spi.MmsDiagnostics get() = traced

    private val traced: com.anindra.messages.mms.spi.MmsDiagnostics by lazy {
        LogcatMmsDiagnostics(recorder)
    }

    /**
     * The carrier profiles, shared with [of].
     *
     * Built here as well as inside [of] because the read limit for an outgoing
     * attachment is a carrier limit, and asking a fresh store for it would
     * register a second carrier-config receiver per send.
     */
    fun profiles(context: Context): CarrierProfileStore {
        val app = context.applicationContext
        return profileStore ?: synchronized(this) {
            profileStore ?: CarrierProfileStore.create(app).also { profileStore = it }
        }
    }

    /** The provider-backed MMS store, for code that handles a message without a send.
     *  [of] builds the same store, so both go through here: two constructions of
     *  it is two places to keep in step. */
    fun store(context: Context): MmsStore {
        val app = context.applicationContext
        return TelephonyMmsStore(
            resolver = app.contentResolver,
            lineOneNumber = { ownNumber(app, DEFAULT_SUBSCRIPTION) },
        )
    }

    fun of(context: Context, diagnostics: MmsDiagnostics = traced): Mms {
        val app = context.applicationContext
        val profiles = profiles(app)
        val transport = SystemMmsTransport(
            fileProviderAuthority = app.packageName + ".fileprovider",
            cacheDir = app.cacheDir,
            targets = AnnouncementRows,
            carrierProfiles = profiles,
            platform = SmsManagerMmsPlatform(app),
            diagnostics = diagnostics,
        )
        return Mms(
            store = store(app),
            transports = TransportRegistry(listOf(transport)),
            fitter = DefaultAttachmentFitter(),
            carrierProfiles = profiles,
            autoDownload = RoamingAwareAutoDownload(
                enabled = { true },
                roaming = { subscriptionId -> isRoaming(app, subscriptionId) },
            ),
            // See sendableNumber: the MSISDN is written out only when the device can name
            // the line it belongs to, and otherwise left to the carrier.
            sendAddress = SendAddressSource { subscriptionId -> sendableNumber(app, subscriptionId) },
            carrierConfig = CarrierProfileStore.platformSource(app),
            diagnostics = diagnostics,
        )
    }

    private fun ownNumber(context: Context, subscriptionId: Int): String? = runCatching {
        val cards = SimCards.load(context)
        val card = if (subscriptionId > 0) {
            cards.firstOrNull { it.subscriptionId == subscriptionId }
        } else {
            null
        }
        (card ?: cards.firstOrNull())?.number?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * The MSISDN to write as `From`, or null for the insert-address token.
     *
     * Unlike [ownNumber] this never falls back to another line: a submission
     * whose `From` is not the line it leaves on is refused, so an unreadable
     * number is worth less than no number at all. Only a genuinely single-line
     * device is allowed to answer for the line it cannot name.
     */
    private fun sendableNumber(context: Context, subscriptionId: Int): String? = runCatching {
        val cards = SimCards.load(context)
        val card = cards.firstOrNull { it.subscriptionId == subscriptionId }
        when {
            card != null -> card.number?.takeIf { it.isNotBlank() }
            subscriptionId <= 0 && cards.size == 1 -> cards.first().number?.takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun isRoaming(context: Context, subscriptionId: Int): Boolean = runCatching {
        val manager = context.getSystemService(TelephonyManager::class.java)
            ?: return@runCatching false
        val forSubscription =
            if (subscriptionId > 0) manager.createForSubscriptionId(subscriptionId) else manager
        forSubscription.isNetworkRoaming
    }.getOrDefault(false)

    /**
     * The destination an announced message is fetched into.
     *
     * It deliberately refuses. `Mms.receive` hands this destination to the
     * platform download API, which writes it from the MMS service's own process:
     * a `content://mms/<id>` message row is not a destination it can open, and
     * every attempt fails with `MMS_ERROR_IO_ERROR`. Returning null defers the
     * fetch, which `Mms.receive` already handles, rather than guaranteeing the
     * failure.
     *
     * The working answer is the staging file `MmsDownloader` uses, but this path
     * would then also have to read the PDU back out of it, and nothing in
     * `:mms` does: `receive` returns AWAITING_PLATFORM and leaves the platform's
     * own broadcast to the app's receiver. Pointing it at a staging file before
     * that exists would store nothing at all, which is harder to diagnose than
     * the code 5 it replaces. So this stays a refusal until the read-back
     * exists — see `MmsStaging` for the one way to name a destination.
     */
    private object AnnouncementRows : MmsDownloadTarget {
        override fun contentUriOf(notification: Pdu, subscriptionId: Int): String? {
            MmsTrace.w(
                "MmsDownload",
                "the :mms retrieve path has no writable destination for " +
                    "${notification.transactionId ?: "an unnamed transaction"}; deferring",
            )
            return null
        }
    }
}
