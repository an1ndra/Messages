package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Reported: "one Draft message I open in chat Text field I cant see them it
 * disappere".
 *
 * The restore was a bare `{ … }` lambda literal inside `LaunchedEffect(convo)`
 * that was never invoked, so a saved draft was silently dropped on re-open. The
 * repo's own TODO had recorded it as "found, not fixed (separate bug)" and the
 * bidi script worked around it by seeding the composer through a share intent.
 */
class ChatDraftRestoreTest {

    @Test
    fun aSavedDraftIsPutBackOnce() {
        assertEquals("see you at 6", restoredDraft("see you at 6", alreadyLoaded = false))
    }

    @Test
    fun nothingIsRestoredAfterTheDraftHasBeenLoaded() {
        assertNull(
            "re-running the effect must not overwrite what the reader is typing",
            restoredDraft("old text", alreadyLoaded = true)
        )
    }

    @Test
    fun blankDraftsRestoreNothing() {
        assertNull(restoredDraft("", alreadyLoaded = false))
        assertNull(restoredDraft("   ", alreadyLoaded = false))
        assertNull(restoredDraft(null, alreadyLoaded = false))
    }

    /** The block must actually run: a lambda literal around it was the bug. */
    @Test
    fun theRestoreIsNotWrappedInAnUncalledLambda() {
        val src = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/com/anindra/messages/ui/ChatScreen.kt") }
            .first { it.isFile }
            .readText()
        val start = src.indexOf("LaunchedEffect(convo) {")
        check(start >= 0) { "draft effect not found" }
        val body = src.substring(start, src.indexOf("\n    }", start))
        assertTrue(
            "the restore must call restoredDraft directly",
            body.contains("restoredDraft(convo?.draft, draftLoaded)") ||
                body.contains("restoredDraft(current?.draft, draftLoaded)")
        )
        assertTrue(
            "a bare block statement here is the never-invoked lambda bug",
            !Regex("""if \(convo != null && !draftLoaded\) \{\s*\{""").containsMatchIn(body)
        )
    }

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)
}
