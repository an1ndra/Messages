package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * New Chat must open with the search field focused and the keyboard up, so the
 * user can start typing immediately. This is a source-level wiring test: the
 * runtime focus/IME interaction is asserted by scripts/test-short-code-send.sh.
 */
class NewChatScreenFocusTest {

    private val main = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main") }
        .firstOrNull { it.isDirectory }
        ?: error("app/src/main not found")

    private val source = File(main, "java/com/anindra/messages/ui/NewChatScreen.kt").readText()

    @Test
    fun focusRequesterIsCreated() {
        assertTrue("FocusRequester not remembered", "remember { FocusRequester() }" in source)
    }

    @Test
    fun focusIsRequestedOnLaunch() {
        assertTrue("requestFocus not called in LaunchedEffect", "focusRequester.requestFocus()" in source)
        assertTrue("LaunchedEffect(Unit) missing", "LaunchedEffect(Unit)" in source)
    }

    @Test
    fun textFieldWiresTheFocusRequester() {
        assertTrue("TextField modifier does not use focusRequester", "Modifier.focusRequester(focusRequester)" in source)
    }
}
