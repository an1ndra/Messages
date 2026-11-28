package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The inbox tabs go away on a swipe up over the chip row. The gesture's own
 *  threshold is pure; the wiring is checked in source because a JVM test has
 *  no Compose input. */
class CategoryRowDismissTest {

    @Test
    fun anUpwardSwipePastTheThresholdDismissesTheRow() {
        assertTrue(CategoryRowDismiss.reached(-48f, 48f))
        assertTrue(CategoryRowDismiss.reached(-120f, 48f))
    }

    @Test
    fun aShortOrDownwardSwipeLeavesTheRowAlone() {
        assertFalse(CategoryRowDismiss.reached(-47f, 48f))
        assertFalse(CategoryRowDismiss.reached(0f, 48f))
        assertFalse(CategoryRowDismiss.reached(120f, 48f))
    }

    @Test
    fun theDismissalGoesBackToAllAndHidesTheChips() {
        val screen = sourceOf("com/anindra/messages/ui/ConversationsScreen.kt")
        val chips = screen.substringAfter("internal fun InboxCategoryChips(")
            .substringBefore("internal val InboxCategory.labelRes")
        assertTrue(chips.contains("detectVerticalDragGestures"))
        assertTrue(chips.contains("CategoryRowDismiss.reached"))

        val call = screen.substringAfter("                InboxCategoryChips(")
            .substringBefore("            syncProgress?")
        assertTrue(call.contains("vm.inboxCategory = null"))
        assertTrue(call.contains("vm.inboxCategoriesRevealed = false"))
    }

    @Test
    fun theThresholdIsHalfAnInchOfDrag() {
        assertEquals(48, CategoryRowDismiss.THRESHOLD.value.toInt())
    }

    private fun sourceOf(relativePath: String): String {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir: File? = File(workingDir).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from $workingDir")
    }
}
