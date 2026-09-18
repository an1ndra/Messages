package com.anindra.messages.diagnostics

object DisplayModeSelector {
    /** Highest-refresh mode that keeps the current resolution, or null when the
     *  current mode is already the best (or unknown). Never returns a
     *  different-resolution mode: forcing one is what rescaled the whole UI in
     *  issue #192 (a display mode bundles resolution + refresh rate). */
    fun bestModeId(current: DisplayModeInfo?, modes: List<DisplayModeInfo>): Int? {
        if (current == null) return null
        val sameResolution = modes.filter {
            it.width == current.width && it.height == current.height
        }
        val best = sameResolution.maxByOrNull { it.refreshRate } ?: return null
        return best.modeId.takeIf { it != current.modeId }
    }
}
