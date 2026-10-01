package com.anindra.messages.sms

import android.content.Context
import android.telephony.CarrierConfigManager
import android.util.Log
import com.anindra.messages.data.MmsConfig
import com.anindra.messages.data.SimCard
import com.anindra.messages.data.SimCards
import com.anindra.messages.data.SimMmsCheck
import com.anindra.messages.data.SimMmsCountry

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
        val region = SimMmsCountry.regionOf(number).ifBlank { sim.countryIso?.uppercase().orEmpty() }
        val config = carrierConfig(context, sim.subscriptionId)
        val result = SimMmsCheck(
            subscriptionId = sim.subscriptionId,
            slotIndex = sim.slotIndex,
            carrierName = sim.carrierName ?: sim.displayName,
            number = number,
            callingCode = SimMmsCountry.callingCodeOf(number),
            region = region.ifBlank { null },
            mmsEnabledByCarrier = config?.getBoolean(CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL, true),
            maxMessageBytes = config?.getInt(
                CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT,
                MmsConfig.DEFAULT_MAX_MESSAGE_SIZE
            )
        )
        Log.i(TAG, "SIM ${sim.subscriptionId} (${sim.carrierName}): ${result.verdict}")
        return result
    }

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
}
