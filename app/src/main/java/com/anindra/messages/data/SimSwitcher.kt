package com.anindra.messages.data

/** Which SIM glyph the in-field switcher paints for the current selection. */
enum class SimIcon { SIM_1, SIM_2, DUAL }

/** Pure helpers backing the in-field SIM switcher in the chat input bar. */
object SimSwitcher {
    fun iconFor(simCount: Int, currentIndex: Int): SimIcon = when {
        simCount >= 3 -> SimIcon.DUAL
        currentIndex == 1 -> SimIcon.SIM_2
        else -> SimIcon.SIM_1
    }

    /** The SIM after [currentSubId] in [sims], wrapping around. Falls back to
     *  the first SIM when the saved id no longer matches any of them. */
    fun next(currentSubId: Int, sims: List<SimCard>): SimCard? {
        if (sims.isEmpty()) return null
        val idx = sims.indexOfFirst { it.subscriptionId == currentSubId }
        return sims[(idx + 1) % sims.size]
    }
}
