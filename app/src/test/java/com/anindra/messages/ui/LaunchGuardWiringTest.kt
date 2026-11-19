package com.anindra.messages.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray opens other apps: the camera, the photo picker, the document picker,
 * the contact picker. A device without one of those throws
 * `ActivityNotFoundException` from `launch()` itself, and an unguarded launch
 * crashed the chat the moment the Camera tile was tapped on an AVD whose camera
 * app had been removed.
 */
class LaunchGuardWiringTest {

    private val chat: String by lazy { locate("ui/ChatScreen.kt") }

    @Test
    fun everyExternalPickerIsLaunchedThroughTheGuard() {
        for (launcher in listOf("cameraLauncher", "galleryLauncher", "audioLauncher",
                                "contactLauncher")) {
            val call = Regex(
                "launchOrExplain\\([^)]*\\)\\s*\\{[\\s\\S]{0,200}?$launcher\\.launch\\("
            )
            assertTrue(
                "$launcher must be launched inside launchOrExplain, or a device without " +
                    "that app takes the chat down",
                call.containsMatchIn(chat)
            )
        }
    }

    @Test
    fun theGuardCatchesTheMissingActivity() {
        assertTrue(
            "the guard must catch the missing-app exception",
            chat.contains("catch (_: android.content.ActivityNotFoundException)")
        )
    }

    @Test
    fun eachMissingAppExplainsItself() {
        for (message in listOf("chat_camera_unavailable", "chat_gallery_unavailable",
                               "chat_contact_unavailable")) {
            assertTrue(
                "$message must be shown when the app is missing",
                chat.contains("R.string.$message")
            )
        }
    }

    private fun locate(relative: String): String {
        val app = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
        return File(app, "src/main/java/com/anindra/messages/$relative").readText()
    }
}
