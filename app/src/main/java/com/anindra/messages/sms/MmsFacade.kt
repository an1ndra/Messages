package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
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
import com.anindra.messages.mms.spi.MmsDownloadTarget
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

    fun of(context: Context): Mms {
        val app = context.applicationContext
        val profiles = profileStore ?: synchronized(this) {
            profileStore ?: CarrierProfileStore.create(app).also { profileStore = it }
        }
        val transport = SystemMmsTransport(
            fileProviderAuthority = app.packageName + ".fileprovider",
            cacheDir = app.cacheDir,
            targets = AnnouncementRows(app),
            carrierProfiles = profiles,
            platform = SmsManagerMmsPlatform(app),
            diagnostics = traced,
        )
        return Mms(
            store = TelephonyMmsStore(
                resolver = app.contentResolver,
                lineOneNumber = { ownNumber(app, DEFAULT_SUBSCRIPTION) },
            ),
            transports = TransportRegistry(listOf(transport)),
            fitter = DefaultAttachmentFitter(),
            carrierProfiles = profiles,
            autoDownload = RoamingAwareAutoDownload(
                enabled = { true },
                roaming = { subscriptionId -> isRoaming(app, subscriptionId) },
            ),
            sendAddress = SendAddressSource { subscriptionId -> ownNumber(app, subscriptionId) },
            carrierConfig = CarrierProfileStore.platformSource(app),
            diagnostics = traced,
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

    @Suppress("DEPRECATION")
    private fun isRoaming(context: Context, subscriptionId: Int): Boolean = runCatching {
        val manager = context.getSystemService(TelephonyManager::class.java)
            ?: return@runCatching false
        val forSubscription =
            if (subscriptionId > 0) manager.createForSubscriptionId(subscriptionId) else manager
        forSubscription.isNetworkRoaming
    }.getOrDefault(false)

    /**
     * The provider row an announced message is fetched into.
     *
     * The platform files that row when it announces the message, so it is found
     * by the notification's transaction id — the `tr_id` the row carries.
     * Returning null (no row) defers the fetch rather than naming a row that
     * does not exist.
     */
    private class AnnouncementRows(private val context: Context) : MmsDownloadTarget {
        override fun contentUriOf(notification: Pdu, subscriptionId: Int): String? {
            val transactionId = notification.transactionId ?: return null
            return context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.TRANSACTION_ID} = ?",
                arrayOf(transactionId),
                "${Telephony.Mms.DATE} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                Uri.withAppendedPath(
                    Telephony.Mms.CONTENT_URI,
                    cursor.getLong(0).toString(),
                ).toString()
            }
        }
    }
}
