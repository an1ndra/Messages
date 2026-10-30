package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #347 review: "switch in chat details does not follow the style of other
 * switches in settings (badly maintained colors)".
 *
 * Google Messages draws a switch as a light track with a dark thumb, which is
 * exactly what Material's default pair resolves to in this scheme (track =
 * primary, thumb = onPrimary). The details screen overrode it with the inverse,
 * so the same control looked different on two screens.
 */
class SwitchStyleTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    @Test
    fun theDetailsSwitchesUseTheSameColoursAsSettings() {
        val details = read("src/main/java/com/anindra/messages/ui/ContactDetailsScreen.kt")
        assertTrue(
            "no per-screen switch colours: they made the control look foreign",
            !details.contains("SwitchDefaults.colors")
        )
    }

    @Test
    fun settingsRowsStickToTheDefaultPair() {
        val settings = read("src/main/java/com/anindra/messages/ui/SettingsScreen.kt")
        assertTrue(
            "settings rows must not override the switch either",
            !settings.contains("SwitchDefaults.colors")
        )
    }

    @Test
    fun theDetailsScreenStillHasItsSwitches() {
        val details = read("src/main/java/com/anindra/messages/ui/ContactDetailsScreen.kt")
        assertTrue("the notification switch stays", details.contains("checked = notifState"))
        assertTrue("and the bubble one", details.contains("checked = bubbleEnabled"))
    }
}
