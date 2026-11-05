package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #374 wiring: the inbox must route every undoable action through one LIFO
 * [UndoStack] and one snackbar. A `showSnackbar` call per swipe was the bug —
 * Material queues those FIFO, so the visible Undo belonged to the oldest swipe.
 */
class UndoSnackbarWiringTest {

    private val source: String by lazy {
        val main = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { File(it, "java").isDirectory }
            ?: error("app/src/main not found")
        File(main, "java/com/anindra/messages/ui/ConversationsScreen.kt").readText()
    }

    private fun occurrences(needle: String) =
        Regex(Regex.escape(needle)).findAll(source).count()

    @Test
    fun onlyOneSnackbarIsEverShown() {
        assertEquals(
            "every swipe must present through the undo stack, not its own snackbar",
            1,
            occurrences("showSnackbar(")
        )
    }

    @Test
    fun aNewerSwipeReplacesTheVisibleSnackbar() {
        assertTrue(
            "the presenter must cancel the snackbar it is replacing, or Material queues them FIFO",
            source.contains("cancelAndJoin()")
        )
        assertTrue(
            "the presenter must read the newest entry",
            source.contains("undoStack.peek()")
        )
    }

    @Test
    fun everySwipeActionIsUndoableThroughTheStack() {
        listOf(
            "moveToTrash",
            "archiveWithUndo",
            "markReadWithUndo",
            "pinWithUndo",
            "blockWithUndo",
            "trashSelection",
            "archiveSelection"
        ).forEach { funName ->
            val body = Regex("fun $funName\\([^)]*\\) \\{([\\s\\S]*?)\\n    \\}")
                .find(source)?.groupValues?.get(1)
                ?: error("$funName not found")
            assertTrue(
                "$funName must record its undo with showUndo",
                body.contains("showUndo(")
            )
        }
    }
}
