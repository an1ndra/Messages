package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Messages tab only ever holds keyword catches: a blocked *sender* is stored
 *  without a blocked_reason, so it lands in the Conversations tab instead. */
class SpamRestoreTest {

    @Test
    fun blockedKeywordStillMatchesSoItStaysCaught() {
        assertFalse(
            SpamRestore.canReturnToChat(
                "Your parcel is on hold, pay the customs fee",
                setOf("customs fee")
            )
        )
    }

    @Test
    fun removingTheKeywordMakesItRestorable() {
        assertTrue(
            SpamRestore.canReturnToChat(
                "Your parcel is on hold, pay the customs fee",
                setOf("free money")
            )
        )
    }

    @Test
    fun anEmptyBlockListMakesEverythingRestorable() {
        assertTrue(SpamRestore.canReturnToChat("anything at all", emptySet()))
    }

    @Test
    fun matchingIgnoresCase() {
        assertFalse(SpamRestore.canReturnToChat("CLAIM YOUR FREE VOUCHER", setOf("free voucher")))
    }

    @Test
    fun anyOneMatchingKeywordIsEnoughToBlock() {
        assertFalse(
            SpamRestore.canReturnToChat("claim your free voucher now", setOf("lottery", "voucher"))
        )
    }

    @Test
    fun blankKeywordsDoNotMatchEverything() {
        // An empty entry on the block list must not make every message unrestorable.
        assertTrue(SpamRestore.canReturnToChat("hello there", setOf("", "   ")))
    }
}
