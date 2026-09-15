package com.anindra.messages.data

import android.content.Context
import android.telephony.SubscriptionManager

/** A single active SIM/subscription, decoupled from [android.telephony.SubscriptionInfo]
 *  so the UI can also be driven by a debug fake on the single-SIM emulator. */
data class SimCard(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    val mccMnc: String?,
    val countryIso: String?,
    val embedded: Boolean
)

object SimCards {
    @Volatile
    private var debugOverride: List<SimCard>? = null

    /**
     * Debug builds only: make the app see a fake dual-SIM setup, so the dual-SIM
     * UI can be exercised on the single-SIM emulator. Real devices/emulators are
     * unaffected (gated on [debuggable]).
     */
    fun setDebugOverride(enabled: Boolean, debuggable: Boolean) {
        debugOverride = if (enabled && debuggable) debugFake() else null
    }

    fun isOverridden(): Boolean = debugOverride != null

    /** Active subscriptions, or the debug fake when enabled. Never throws. */
    fun load(context: Context): List<SimCard> {
        debugOverride?.let { return it }
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                ?.map {
                    SimCard(
                        subscriptionId = it.subscriptionId,
                        slotIndex = it.simSlotIndex,
                        carrierName = it.carrierName?.toString()?.ifBlank { null },
                        displayName = it.displayName?.toString()?.ifBlank { null },
                        mccMnc = "${it.mccString ?: ""}${it.mncString ?: ""}".ifBlank { null },
                        countryIso = it.countryIso?.ifBlank { null },
                        embedded = it.isEmbedded
                    )
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Two fake SIMs. The subscriptionIds (1 and 7) intentionally do NOT match
     * slot+1, so the label logic is exercised (subId 7 on slot 1 must render
     * "Vodafone (SIM 2)", never "SIM 7").
     */
    fun debugFake(): List<SimCard> = listOf(
        SimCard(1, 0, "T-Mobile", "T-Mobile", "310260", "us", false),
        SimCard(7, 1, "Vodafone", "Vodafone UK", "23415", "gb", false)
    )
}
