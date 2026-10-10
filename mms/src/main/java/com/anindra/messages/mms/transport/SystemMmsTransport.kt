package com.anindra.messages.mms.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import com.anindra.messages.mms.WspMmsCodec
import com.anindra.messages.mms.net.CarrierProfile
import com.anindra.messages.mms.net.CarrierProfileStore
import com.anindra.messages.mms.pdu.Pdu
import com.anindra.messages.mms.spi.MmsDiagnostics
import com.anindra.messages.mms.spi.MmsDownloadTarget
import com.anindra.messages.mms.spi.MmsPduCodec
import com.anindra.messages.mms.spi.MmsTransport
import com.anindra.messages.mms.spi.PlatformTransaction
import com.anindra.messages.mms.spi.TransportListener
import java.io.File
import java.util.UUID

/**
 * One callback the platform delivers back through a PendingIntent.
 *
 * Described rather than constructed, so the flags are assertable without a
 * device: `PendingIntent.getBroadcast` is a framework call, and under plain JUnit
 * it returns nothing to inspect. Every callback here is mutable on purpose --
 * the platform is the sender and it fills in the result code and the response
 * PDU, which a framework that refuses the fill will drop. What must never be
 * absent is the flag that says the fill is wanted, which is why the flags live
 * on this value and are asserted from it.
 */
data class MmsCallback(
    val action: String,
    val requestCode: Int,
    val extras: Map<String, String>,
    val flags: Int,
) {
    /** Non-zero only when one of the two mutability flags was actually chosen. */
    val mutabilityFlags: Int get() = flags and MUTABILITY_MASK

    val hasMutabilityFlag: Boolean get() = mutabilityFlags != 0

    companion object {
        const val MUTABLE = PendingIntent.FLAG_MUTABLE
        const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
        const val MUTABILITY_MASK = MUTABLE or IMMUTABLE

        /** Mutable so the platform can fill the result in, and updated so a retry reuses it. */
        const val RESULT_FLAGS = MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

/** Builds the [PendingIntent] behind an [MmsCallback]. */
fun interface MmsPendingIntentFactory {
    fun broadcast(callback: MmsCallback): PendingIntent
}

/**
 * Builds the PendingIntents for the app's own receivers.
 *
 * The intent is targeted at this package rather than at a component: the
 * receivers live in the app module, which this library must not know about, and
 * a package-targeted intent is already explicit for broadcast purposes.
 */
class PlatformMmsPendingIntents(context: Context) : MmsPendingIntentFactory {
    private val applicationContext = context.applicationContext

    override fun broadcast(callback: MmsCallback): PendingIntent {
        val intent = Intent(callback.action).setPackage(applicationContext.packageName)
        callback.extras.forEach { (name, value) -> intent.putExtra(name, value) }
        return PendingIntent.getBroadcast(applicationContext, callback.requestCode, intent, callback.flags)
    }
}

/**
 * The two platform MMS calls, as a seam.
 *
 * `SmsManager` has no accessible constructor, so it cannot be faked; going
 * through this interface is what lets the whole transport run under plain JUnit.
 * The config overrides arrive as a plain map because a `Bundle` under plain JUnit
 * accepts writes and answers null to every read, which would make the one
 * carrier-derived input in this file untestable.
 */
interface MmsPlatform {
    /** False when this process may not use the MMS APIs for [subscriptionId]. */
    fun canSend(subscriptionId: Int): Boolean

    fun sendMultimedia(
        subscriptionId: Int,
        locationUri: String,
        configOverrides: Map<String, Any>,
        sent: MmsCallback,
    )

    fun downloadMultimedia(
        subscriptionId: Int,
        locationUrl: String,
        contentUri: String,
        configOverrides: Map<String, Any>,
        completion: MmsCallback,
    )
}

/** [MmsPlatform] over `SmsManager`. */
class SmsManagerMmsPlatform(
    context: Context,
    private val pendingIntents: MmsPendingIntentFactory = PlatformMmsPendingIntents(context),
) : MmsPlatform {
    private val applicationContext = context.applicationContext

    private companion object {
        const val TAG = "MmsTransport"
    }

    override fun canSend(subscriptionId: Int): Boolean = try {
        managerFor(subscriptionId)
        true
    } catch (_: SecurityException) {
        false
    }

    override fun sendMultimedia(
        subscriptionId: Int,
        locationUri: String,
        configOverrides: Map<String, Any>,
        sent: MmsCallback,
    ) {
        val outgoing = Uri.parse(locationUri)
        if (!PlatformMmsAccess.grant(applicationContext, outgoing, write = false)) {
            Log.w(TAG, "no platform package could be granted $outgoing; the send will read no PDU")
        }
        managerFor(subscriptionId).sendMultimediaMessage(
            applicationContext,
            outgoing,
            null,
            configOverrides.toBundle(),
            pendingIntents.broadcast(sent),
        )
    }

    override fun downloadMultimedia(
        subscriptionId: Int,
        locationUrl: String,
        contentUri: String,
        configOverrides: Map<String, Any>,
        completion: MmsCallback,
    ) {
        if (contentUri.startsWith("content://")) {
            val destination = Uri.parse(contentUri)
            if (!PlatformMmsAccess.grant(applicationContext, destination, write = true)) {
                Log.w(TAG, "no platform package could be granted $destination; the download will write nothing")
            }
        }
        managerFor(subscriptionId).downloadMultimediaMessage(
            applicationContext,
            locationUrl,
            Uri.parse(contentUri),
            configOverrides.toBundle(),
            pendingIntents.broadcast(completion),
        )
    }

    /**
     * The manager for one subscription, or a refusal.
     *
     * Throwing rather than returning null is what lets [canSend] and the two
     * send paths share one lookup: the caller has already asked whether this
     * process may use the API, and a refusal after that is a bug rather than a
     * condition to branch on a second time. `getSmsManagerForSubscriptionId` is
     * deprecated in favour of the instance method, which needs API 31, and the
     * deprecated call is the only route at this module's minimum.
     */
    @Suppress("DEPRECATION")
    private fun managerFor(subscriptionId: Int): SmsManager =
        SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

    private fun Map<String, Any>.toBundle() = Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putInt(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
    }
}

/**
 * The MMS_CONFIG_* overrides for one subscription, as a map.
 *
 * Every entry comes from [CarrierProfile]; nothing here is a constant of this
 * file's own. The MMSC itself is deliberately absent: the platform resolves it
 * from the telephony APN database, which is the authority on where a carrier's
 * MMSC is, so overriding it from an app-side read of that same table could only
 * ever disagree with the modem. The image limits and the report flags are
 * absent for the same reason: this module fits the attachment and writes the
 * report headers into the PDU itself, so the platform never acts on them.
 */
fun configOverridesFor(profile: CarrierProfile): Map<String, Any> = buildMap {
    put(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, profile.maxMessageSize())
    put(SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT, profile.httpSocketTimeout())
    put(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH, profile.maxImageWidth())
    put(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT, profile.maxImageHeight())
    profile.userAgent()?.let { put(SmsManager.MMS_CONFIG_USER_AGENT, it) }
    profile.uaProfUrl()?.let { put(SmsManager.MMS_CONFIG_UA_PROF_URL, it) }
}

/**
 * Sends and retrieves over the platform MMS API.
 *
 * `sendMultimediaMessage` does not take the message: it takes a location the
 * radio reads the composed PDU from, so the PDU is written to [cacheDir] and
 * served through the app's FileProvider at [fileProviderAuthority]. That
 * provider is declared by the app, not by this library, and neither is any
 * receiver for [MmsCallback.action].
 *
 * The listener argument goes unused on both paths, and that is the contract
 * rather than an oversight: this transport hands the platform its own
 * completion PendingIntents, so the result reaches the app as a broadcast and
 * there is nothing left for the listener to be told.
 */
class SystemMmsTransport(
    private val fileProviderAuthority: String,
    private val cacheDir: File,
    private val targets: MmsDownloadTarget,
    private val carrierProfiles: CarrierProfileStore,
    private val platform: MmsPlatform,
    private val codec: MmsPduCodec = WspMmsCodec(),
    private val diagnostics: MmsDiagnostics = object : MmsDiagnostics {},
) : MmsTransport, PlatformTransaction {

    override val id: String = TransportChoice.SYSTEM.id

    override val reportsThroughPendingIntent: Boolean = true

    override val ownsNotificationRow: Boolean = true

    override fun isAvailable(subscriptionId: Int): Boolean = platform.canSend(subscriptionId)

    override fun send(pdu: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        val transactionId = pdu.transactionId ?: return false
        val bytes = codec.compose(pdu) ?: return false
        val file = writeToCache(bytes) ?: return false
        diagnostics.sendBuilt(bytes.size, pdu.to.size)
        return try {
            platform.sendMultimedia(
                subscriptionId = subscriptionId,
                locationUri = contentUriOf(file),
                configOverrides = configOverridesFor(carrierProfiles.of(subscriptionId)),
                sent = callback(ACTION_SEND_SENT, transactionId, subscriptionId, file.name),
            )
            true
        } catch (_: Exception) {
            file.delete()
            false
        }
    }

    override fun retrieve(notification: Pdu, subscriptionId: Int, listener: TransportListener): Boolean {
        val transactionId = notification.transactionId ?: return false
        val locationUrl = notification.contentLocation ?: return false
        val contentUri = targets.contentUriOf(notification, subscriptionId) ?: return false
        return try {
            platform.downloadMultimedia(
                subscriptionId = subscriptionId,
                locationUrl = locationUrl,
                contentUri = contentUri,
                configOverrides = configOverridesFor(carrierProfiles.of(subscriptionId)),
                completion = callback(ACTION_DOWNLOAD_COMPLETE, transactionId, subscriptionId, contentUri),
            )
            true
        } catch (_: Exception) {
            false
        }
    }


    private fun callback(action: String, transactionId: String, subscriptionId: Int, location: String) =
        MmsCallback(
            action = action,
            // Intent equality ignores extras, so the transaction id arriving here
            // as a request code is what keeps two messages' callbacks apart.
            requestCode = transactionId.hashCode(),
            extras = mapOf(
                EXTRA_TRANSACTION_ID to transactionId,
                EXTRA_SUBSCRIPTION_ID to subscriptionId.toString(),
                EXTRA_LOCATION to location,
            ),
            flags = MmsCallback.RESULT_FLAGS,
        )

    /**
     * A UUID, not `abs(Random.nextLong())`: the download path in the stack being
     * replaced names its temp files that way, so two concurrent transfers land on
     * one name and the second overwrites the first.
     */
    private fun writeToCache(bytes: ByteArray): File? {
        val file = File(cacheDir, "$FILE_PREFIX${UUID.randomUUID()}$FILE_SUFFIX")
        return try {
            file.outputStream().use { it.write(bytes) }
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /**
     * The cache-path name declared by the app's FileProvider is "mms", so the
     * content URI has to include that segment. A URI missing it is rejected by
     * FileProvider before the platform can read the PDU.
     */
    private fun contentUriOf(file: File): String = "content://$fileProviderAuthority/mms/${file.name}"

    companion object {
        const val ACTION_SEND_SENT = "com.anindra.messages.mms.action.SEND_SENT"
        const val ACTION_DOWNLOAD_COMPLETE = "com.anindra.messages.mms.action.DOWNLOAD_COMPLETE"

        const val EXTRA_TRANSACTION_ID = "mms_transaction_id"
        const val EXTRA_SUBSCRIPTION_ID = "mms_subscription_id"
        const val EXTRA_LOCATION = "mms_location"

        const val FILE_PREFIX = "mms-send-"
        const val FILE_SUFFIX = ".dat"
    }
}