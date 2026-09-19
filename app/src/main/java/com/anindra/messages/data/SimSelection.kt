package com.anindra.messages.data

/** Resolves the effective sending SIM against the currently available ones. */
object SimSelection {
    /** The persisted subscription id if it still exists in [sims], otherwise -1
     *  (system default). A saved id that no longer matches any SIM — removed SIM,
     *  or the debug fake list being turned off — must not surface as "Unknown
     *  SIM". */
    fun effective(persistedId: Int, sims: List<SimCard>): Int = when {
        persistedId == -1 -> -1
        sims.any { it.subscriptionId == persistedId } -> persistedId
        else -> -1
    }
}
