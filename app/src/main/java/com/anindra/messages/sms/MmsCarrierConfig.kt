package com.anindra.messages.sms

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import com.anindra.messages.data.CarrierValues
import com.anindra.messages.data.MmsConfig
import java.util.concurrent.ConcurrentHashMap

private class BundleCarrierValues(private val bundle: PersistableBundle?) : CarrierValues {
    override fun boolean(key: String, fallback: Boolean): Boolean =
        bundle?.getBoolean(key, fallback) ?: fallback

    override fun integer(key: String, fallback: Int): Int =
        bundle?.getInt(key, fallback) ?: fallback
}

/**
 * Reads the per-SIM MMS parameters the send and download paths depend on.
 *
 * Carrier config is cached because it is consulted on every send and download
 * and only changes when the carrier config broadcast fires; [invalidate] is
 * called on resume so a SIM swap or carrier change is picked up.
 */
internal object MmsCarrierConfig {
    private const val TAG = "MmsCarrierConfig"

    private val cache = ConcurrentHashMap<Int, MmsConfig>()

    fun of(context: Context, subscriptionId: Int): MmsConfig =
        cache.getOrPut(subscriptionId) { load(context, subscriptionId) }

    fun invalidate() = cache.clear()

    private fun load(context: Context, subscriptionId: Int): MmsConfig {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
            ?: return MmsConfig()
        // A negative id means "whatever SIM carries the default SMS role".
        val subId = if (subscriptionId > 0) subscriptionId
        else SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subId <= 0) return MmsConfig()
        // getConfigForSubId is deprecated in favour of getConfigByComponentForSubId,
        // but the latter returns a component-filtered bundle that is empty for this
        // app, which would silently discard every MMS limit. Verified on API 36.
        @Suppress("DEPRECATION")
        val bundle = try {
            manager.getConfigForSubId(subId)
        } catch (_: SecurityException) {
            // READ_PHONE_STATE denied. A pre-flight check cannot close this: the
            // user can revoke the grant between the check and the call.
            null
        }
        val config = MmsConfig.from(BundleCarrierValues(bundle))
        android.util.Log.i(TAG, "MMS carrier config sub=$subId: $config")
        return config
    }
}
