package com.anindra.messages.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SwipeThresholdTest {

    @Test
    fun commitFractionIsSmallButNonZero() {
        val fraction = SettingsLayout.SWIPE_COMMIT_FRACTION
        assertTrue("commit fraction must be positive", fraction > 0f)
        assertTrue(
            "commit fraction $fraction must stay below the library's hardcoded " +
                "half-row switch (issuetracker 471021165), or that switch decides the gesture",
            fraction < 0.5f
        )
    }

    @Test
    fun bothConversationScreensGateOnTheSharedFraction() {
        listOf(
            "java/com/anindra/messages/ui/ConversationsScreen.kt",
            "java/com/anindra/messages/ui/legacy/LegacyConversationsScreen.kt"
        ).forEach { relativePath ->
            val source = File(mainDir(), relativePath).readText()
            assertTrue(
                "$relativePath must gate its swipe on SettingsLayout.SWIPE_COMMIT_FRACTION",
                source.contains("SettingsLayout.SWIPE_COMMIT_FRACTION")
            )
        }
    }

    private fun mainDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main").isDirectory) dir = dir.parentFile
        return dir?.let { File(it, "src/main") } ?: error("src/main not found")
    }
}
