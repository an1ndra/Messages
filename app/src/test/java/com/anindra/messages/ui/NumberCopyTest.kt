package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberCopyTest {
    @Test
    fun longPressWritesTheNumberBeforeShowingTheConfirmation() {
        val events = mutableListOf<String>()

        NumberCopy(
            write = { events += "write:$it" },
            notify = { events += "confirm" }
        )("+15557776661")

        assertEquals(listOf("write:+15557776661", "confirm"), events)
    }

    @Test
    fun theDetailsNumberIsTheLongPressTarget() {
        val source = source("ui/ContactDetailsScreen.kt")

        assertTrue(source.contains("NumberCopy("))
        assertTrue(source.contains("detectTapGestures(onLongPress = { copyNumber(address) })"))
        assertTrue(source.contains("onLongClick(label = context.getString(R.string.icon_copy))"))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/anindra/messages/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("could not locate $relative")
    }
}
