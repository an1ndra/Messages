package com.anindra.messages.mms.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import java.util.Collections

/**
 * Typed access to one layer of carrier MMS settings.
 *
 * Every getter takes the value to use when its own layer has nothing to say, so
 * a layer that is absent — a carrier with no config at all — degrades instead of
 * throwing or silently reporting a zero.
 */
interface CarrierConfigValues {
    fun boolean(key: String, fallback: Boolean): Boolean
    fun integer(key: String, fallback: Int): Int
    fun long(key: String, fallback: Long): Long
    fun string(key: String, fallback: String): String
}

/** A fixed set of values, used for the app-default layer and for tests. */
class MapCarrierValues(private val values: Map<String, Any>) : CarrierConfigValues {
    override fun boolean(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback
    override fun integer(key: String, fallback: Int): Int = (values[key] as? Number)?.toInt() ?: fallback
    override fun long(key: String, fallback: Long): Long = (values[key] as? Number)?.toLong() ?: fallback
    override fun string(key: String, fallback: String): String = values[key] as? String ?: fallback
}

/** Where a subscription's platform layer comes from. */
fun interface CarrierConfigSource {
    /** Null when the subscription has no carrier config at all. */
    fun load(subscriptionId: Int): CarrierConfigValues?
}

/**
 * A subscription's MMS limits and MMSC settings, as two layers.
 *
 * The layers are app defaults first and the platform's carrier config on top, so
 * the modem wins wherever the two disagree — the APN database is the authority on
 * what a carrier supports, and app-side defaults only fill gaps it leaves.
 * Reading one value out of both layers rather than merging the two structures
 * also keeps a stale app default from shadowing a config the platform updated
 * under it.
 */
class CarrierProfile(
    private val defaults: CarrierConfigValues,
    private val platform: CarrierConfigValues?,
) {
    fun maxMessageSize(): Int = layeredInt(KEY_MAX_MESSAGE_SIZE, DEFAULT_MAX_MESSAGE_SIZE)
        .coerceAtLeast(0)

    fun maxImageWidth(): Int = layeredInt(KEY_MAX_IMAGE_WIDTH, DEFAULT_MAX_IMAGE_WIDTH)

    fun maxImageHeight(): Int = layeredInt(KEY_MAX_IMAGE_HEIGHT, DEFAULT_MAX_IMAGE_HEIGHT)

    fun notifyWapMmsc(): Boolean = layeredBoolean(KEY_NOTIFY_WAP_MMSC, false)

    fun transIdEnabled(): Boolean = layeredBoolean(KEY_TRANS_ID_ENABLED, false)

    fun groupMmsEnabled(): Boolean = layeredBoolean(KEY_GROUP_MMS_ENABLED, true)

    fun httpSocketTimeout(): Int = layeredInt(KEY_HTTP_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_MS)
        .takeIf { it > 0 } ?: DEFAULT_SOCKET_TIMEOUT_MS

    fun uaProfUrl(): String? = layeredString(KEY_UA_PROFILE_URL)?.ifEmpty { null }

    fun userAgent(): String? = layeredString(KEY_USER_AGENT)?.ifEmpty { null }

    // Each getter reads the platform layer, handing it the app-default layer's
    // answer as the fallback. Reading both layers per key is what keeps the modem
    // winning on conflict while an absent layer simply falls through.
    private fun layeredInt(key: String, fallback: Int): Int {
        val fromDefaults = defaults.integer(key, fallback)
        return platform?.integer(key, fromDefaults) ?: fromDefaults
    }

    private fun layeredBoolean(key: String, fallback: Boolean): Boolean {
        val fromDefaults = defaults.boolean(key, fallback)
        return platform?.boolean(key, fromDefaults) ?: fromDefaults
    }

    private fun layeredString(key: String): String? {
        val fromDefaults = defaults.string(key, "").trim()
        return (platform?.string(key, fromDefaults) ?: fromDefaults).trim()
    }

    companion object {
        /**
         * The `KEY_MMS_*` carrier-config names, spelled out.
         *
         * `CarrierConfigManager.KEY_MMS_*` are all `@hide`, so they cannot be
         * referenced from an ordinary app; the strings themselves are part of the
         * carrier-config schema and are stable. `MmsConfig` in the app module
         * spells the same keys the same way — one spelling, two readers.
         */
        const val KEY_MAX_MESSAGE_SIZE = "maxMessageSize"
        const val KEY_MAX_IMAGE_WIDTH = "maxImageWidth"
        const val KEY_MAX_IMAGE_HEIGHT = "maxImageHeight"
        const val KEY_NOTIFY_WAP_MMSC = "enabledNotifyWapMMSC"
        const val KEY_TRANS_ID_ENABLED = "enableMmsTransId"
        const val KEY_GROUP_MMS_ENABLED = "enableGroupMms"
        const val KEY_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout"
        const val KEY_UA_PROFILE_URL = "mmsUaProfileUrl"
        const val KEY_USER_AGENT = "mmsUserAgent"

        const val DEFAULT_MAX_MESSAGE_SIZE = 307_200
        const val DEFAULT_MAX_IMAGE_WIDTH = 640
        const val DEFAULT_MAX_IMAGE_HEIGHT = 480
        const val DEFAULT_SOCKET_TIMEOUT_MS = 60_000
    }
}

/** The layer the platform reports for a subscription. */
class PersistableBundleCarrierValues(private val bundle: PersistableBundle?) : CarrierConfigValues {
    override fun boolean(key: String, fallback: Boolean): Boolean =
        bundle?.getBoolean(key, fallback) ?: fallback

    override fun integer(key: String, fallback: Int): Int = bundle?.getInt(key, fallback) ?: fallback

    override fun long(key: String, fallback: Long): Long = bundle?.getLong(key, fallback) ?: fallback

    override fun string(key: String, fallback: String): String = bundle?.getString(key) ?: fallback
}

/**
 * Per-subscription [CarrierProfile]s, cached until the carrier config changes.
 *
 * The platform broadcasts `ACTION_CARRIER_CONFIG_CHANGED` when a subscription's
 * config is replaced, which is also when a SIM swap lands, so the cache is
 * dropped there rather than on a timer.
 */
class CarrierProfileStore(
    private val defaults: CarrierConfigValues,
    private val source: CarrierConfigSource,
    private val defaultSubscriptionId: () -> Int = { SubscriptionManager.getDefaultSmsSubscriptionId() },
) {
    private val cache: MutableMap<Int, CarrierProfile> =
        Collections.synchronizedMap(HashMap<Int, CarrierProfile>())

    fun of(subscriptionId: Int): CarrierProfile {
        val key = if (subscriptionId > 0) subscriptionId else defaultSubscriptionId()
        synchronized(cache) { cache[key]?.let { return it } }
        val loaded = CarrierProfile(defaults, source.load(key))
        synchronized(cache) { cache[key] = loaded }
        return loaded
    }

    fun invalidate() = synchronized(cache) { cache.clear() }

    companion object {
        fun platformSource(context: Context): CarrierConfigSource = CarrierConfigSource { subscriptionId ->
            val manager = context.applicationContext.getSystemService(CarrierConfigManager::class.java)
                ?: return@CarrierConfigSource null
            if (subscriptionId <= 0) return@CarrierConfigSource null
            // getConfigForSubId is deprecated in favour of
            // getConfigByComponentForSubId, but the latter returns a
            // component-filtered bundle that is empty for a library, which would
            // silently discard every MMS limit. Verified on API 36.
            @Suppress("DEPRECATION")
            val bundle = try {
                manager.getConfigForSubId(subscriptionId)
            } catch (_: SecurityException) {
                // READ_PHONE_STATE denied, and it can be revoked between a check
                // and the call, so a pre-flight check cannot close this.
                null
            }
            PersistableBundleCarrierValues(bundle)
        }

        /**
         * Builds a store bound to [context] and subscribes it to carrier-config
         * changes for the process lifetime.
         */
        fun create(context: Context): CarrierProfileStore {
            val store = CarrierProfileStore(MapCarrierValues(emptyMap()), platformSource(context))
            val applicationContext = context.applicationContext
            applicationContext.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        if (intent?.action == CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED) {
                            store.invalidate()
                        }
                    }
                },
                IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED),
            )
            return store
        }
    }
}