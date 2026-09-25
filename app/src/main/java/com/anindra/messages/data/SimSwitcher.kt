package com.anindra.messages.data

/** Pure helpers backing the in-field SIM switcher in the chat input bar. */
object SimSwitcher {

    /** The SIM after [currentSubId] in [sims], wrapping around. Falls back to
     *  the first SIM when the saved id no longer matches any of them. */
    fun next(currentSubId: Int, sims: List<SimCard>): SimCard? {
        if (sims.isEmpty()) return null
        val idx = sims.indexOfFirst { it.subscriptionId == currentSubId }
        return sims[(idx + 1) % sims.size]
    }

    /** Stays visible for the whole draft, not just while it is empty: a typed
     *  message often changes which SIM you want to send it from, and the
     *  keyboard hides the control exactly when that choice is made. */
    fun shouldShowSwitch(simCount: Int): Boolean = simCount > 1
}
