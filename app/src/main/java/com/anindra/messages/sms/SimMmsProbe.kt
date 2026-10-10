package com.anindra.messages.sms

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.util.Log
import com.anindra.messages.data.MmsConfig
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimCards
import com.anindra.messages.data.CarrierMmsValues
import com.anindra.messages.data.SimMmsCheck
import com.anindra.messages.data.SimMmsCountry
import com.anindra.messages.data.SimMmsVerdict

/**
 * Reads, for every active SIM, whether its carrier has MMS switched on.
 *
 * `enabledMMS` from the carrier config is the platform's own answer for a
 * subscription, so the check follows the SIM rather than the country: two SIMs
 * from the same country can sit on carriers that disagree. The country calling
 * code and region are carried alongside as context.
 */
object SimMmsProbe {
    private const val TAG = "SimMmsProbe"

    fun run(context: Context): List<SimMmsCheck> =
        SimCards.load(context).map { check(context, it) }

    private fun check(context: Context, sim: SimCard): SimMmsCheck {
        val number = sim.number?.takeIf { it.isNotBlank() }
        val simRegion = sim.countryIso?.uppercase()?.takeIf { it.isNotBlank() }
        val region = SimMmsCountry.regionOf(number, simRegion).ifBlank { simRegion.orEmpty() }
        val config = carrierConfig(context, sim.subscriptionId)
        val result = SimMmsCheck(
            subscriptionId = sim.subscriptionId,
            slotIndex = sim.slotIndex,
            carrierName = sim.carrierName ?: sim.displayName,
            number = number,
            callingCode = SimMmsCountry.callingCodeOf(number, simRegion),
            region = region.ifBlank { null },
            mmsEnabledByCarrier = carrierMmsEnabled(config),
            maxMessageBytes = config?.getInt(
                CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT,
                MmsConfig.DEFAULT_MAX_MESSAGE_SIZE
            ),
            hasNetwork = !sim.mccMnc.isNullOrBlank()
        )
        Log.i(TAG, "SIM ${sim.subscriptionId} (${sim.carrierName}): ${result.verdict}")
        return result
    }

    /**
     * A carrier that states nothing about MMS has not enabled it. Defaulting the
     * lookup to `true` reported "Supported" for every carrier whose config
     * simply omits the key, which is the common case. Absent key is "unknown",
     * not "yes".
     */
    private fun carrierMmsEnabled(config: PersistableBundle?): Boolean? =
        SimMmsVerdict.carrierEnabled(config?.let { cfg ->
            object : CarrierMmsValues {
                override fun hasMmsKey() =
                    cfg.containsKey(CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL)
                override fun mmsEnabled() =
                    cfg.getBoolean(CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL, false)
            }
        })

    private fun carrierConfig(context: Context, subscriptionId: Int) = try {
        val manager = context.getSystemService(CarrierConfigManager::class.java)
        if (manager == null || subscriptionId <= 0) {
            null
        } else {
            @Suppress("DEPRECATION")
            try {
                manager.getConfigForSubId(subscriptionId)
            } catch (_: SecurityException) {
                // READ_PHONE_STATE denied; SimMmsCheck falls back to "unknown".
                null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "carrier config unavailable for sub=$subscriptionId", e)
        null
    }

    /**
     * The MMS-related carrier-config values for [subscriptionId], verbatim, for
     * the diagnostics report.
     *
     * `KEY_MMS_MMS_ENABLED_BOOL` is the only capability signal a normal app can
     * read: the MMSC itself lives in the APN table, which is behind a
     * privileged permission. So when a carrier's verdict disagrees with the
     * platform's own messaging app, this is the evidence needed to see what the
     * carrier actually declared. Read from the on-device config only — nothing
     * here touches the network.
     */
    fun carrierFacts(context: Context, subscriptionId: Int): List<String> {
        val config = carrierConfig(context, subscriptionId)
            ?: return listOf("carrierConfig: unreadable")
        fun bool(key: String) =
            if (config.containsKey(key)) config.getBoolean(key, false).toString() else "absent"
        fun int(key: String) =
            if (config.containsKey(key)) config.getInt(key, 0).toString() else "absent"
        fun string(key: String) =
            config.getString(key, null)?.takeIf { it.isNotBlank() } ?: "absent"

        val imageWidth = int(CarrierConfigManager.KEY_MMS_MAX_IMAGE_WIDTH_INT)
        val imageHeight = int(CarrierConfigManager.KEY_MMS_MAX_IMAGE_HEIGHT_INT)
        // The send path only enforces a dimension cap when the carrier declared
        // both bounds. When they are absent the AOSP 640x480 baseline is a guess,
        // not a limit, and applying it is what makes a photo arrive blurry — so
        // this verdict is the answer to "why is this carrier's picture soft?".
        val imageLimitsReported = imageWidth.toIntOrNull()?.let { it > 0 } == true &&
            imageHeight.toIntOrNull()?.let { it > 0 } == true

        return listOf(
            "verdict: ${run(context).firstOrNull { it.subscriptionId == subscriptionId }?.verdict}",
            "${CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL}: ${bool(CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL)}",
            "${CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT}: ${int(CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT)}",
            "${CarrierConfigManager.KEY_MMS_MAX_IMAGE_WIDTH_INT}: $imageWidth",
            "${CarrierConfigManager.KEY_MMS_MAX_IMAGE_HEIGHT_INT}: $imageHeight",
            "imageLimitsReported: $imageLimitsReported",
            "${CarrierConfigManager.KEY_MMS_HTTP_SOCKET_TIMEOUT_INT}: ${int(CarrierConfigManager.KEY_MMS_HTTP_SOCKET_TIMEOUT_INT)}",
            "${CarrierConfigManager.KEY_MMS_GROUP_MMS_ENABLED_BOOL}: ${bool(CarrierConfigManager.KEY_MMS_GROUP_MMS_ENABLED_BOOL)}",
            "${com.anindra.messages.data.MmsConfig.KEY_DELIVERY_REPORT}: ${bool(com.anindra.messages.data.MmsConfig.KEY_DELIVERY_REPORT)}",
            "${com.anindra.messages.data.MmsConfig.KEY_READ_REPORT}: ${bool(com.anindra.messages.data.MmsConfig.KEY_READ_REPORT)}",
            "${CarrierConfigManager.KEY_MMS_USER_AGENT_STRING}: ${string(CarrierConfigManager.KEY_MMS_USER_AGENT_STRING)}",
            "${CarrierConfigManager.KEY_MMS_SMS_TO_MMS_TEXT_THRESHOLD_INT}: ${int(CarrierConfigManager.KEY_MMS_SMS_TO_MMS_TEXT_THRESHOLD_INT)}",
            "${CarrierConfigManager.KEY_MMS_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD_INT}: ${int(CarrierConfigManager.KEY_MMS_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD_INT)}",
            "${CarrierConfigManager.KEY_MMS_RECIPIENT_LIMIT_INT}: ${int(CarrierConfigManager.KEY_MMS_RECIPIENT_LIMIT_INT)}"
        )
    }
}
