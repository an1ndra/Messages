package com.anindra.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SimBadgeColorTest {
    private val source: String by lazy {
        val main = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main") }
            .firstOrNull { it.isDirectory }
            ?: error("app/src/main not found")
        File(main, "java/com/anindra/messages/ui/ChatScreen.kt").readText()
    }

    private val inputBar: String by lazy {
        val start = source.indexOf("private fun InputBar(")
        val end = source.indexOf("private fun SimPickerDialog(")
        assertTrue(start in 0 until end)
        source.substring(start, end)
    }

    @Test
    fun simIconStaysTintedWithOnSurfaceVariant() {
        assertTrue(inputBar.contains("tint = MaterialTheme.colorScheme.onSurfaceVariant"))
    }

    @Test
    fun simLabelUsesASurfaceColourForContrastOnTheIcon() {
        assertTrue(inputBar.contains("color = MaterialTheme.colorScheme.surface"))
    }

    @Test
    fun simLabelNeverHardcodesWhiteOrPrimaryContainer() {
        assertFalse(inputBar.contains("Color.White"))
        assertFalse(inputBar.contains("onPrimaryContainer"))
    }
}
