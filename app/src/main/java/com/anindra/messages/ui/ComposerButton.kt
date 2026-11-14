package com.anindra.messages.ui

/** What the composer's single circle is currently for. */
internal enum class ComposerCircle { Record, Send }

/** The glyph drawn inside that circle. */
internal enum class ComposerGlyph { Wave, Send }

/**
 * The composer button's state, decided once.
 *
 * WhatsApp keeps one accent-filled circle whether it records or sends, and only
 * the glyph changes; the disc turns red for the length of a note. Deciding the
 * mode, glyph and fill here keeps the drawing code from re-deriving them
 * separately and disagreeing with itself.
 */
internal object ComposerButton {

    fun modeFor(micMode: Boolean): ComposerCircle =
        if (micMode) ComposerCircle.Record else ComposerCircle.Send

    fun glyphFor(mode: ComposerCircle): ComposerGlyph =
        if (mode == ComposerCircle.Record) ComposerGlyph.Wave else ComposerGlyph.Send

    /** True everywhere except a running note, which is red. */
    fun usesAccentFill(mode: ComposerCircle, recording: Boolean): Boolean =
        !(mode == ComposerCircle.Record && recording)
}

/** How the glyph swaps: a quarter turn with a small pinch, then a spring back. */
internal object ComposerSwap {
    const val TURN_DEGREES = 90f
    const val SCALE = 0.5f

    /** A little overshoot on the settle, as WhatsApp's icon swap has. */
    const val DAMPING = 0.55f
    const val STIFFNESS = 1500f
}
