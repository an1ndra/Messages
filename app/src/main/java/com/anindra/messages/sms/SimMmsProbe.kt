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
            )
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
}
