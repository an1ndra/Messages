package com.anindra.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The record gesture is not discoverable from its icon, so a one-time card
 * explains it. It must be shown only until it has been dismissed — a card that
 * comes back every time is worse than no card.
 */
class VoiceGuideTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private val chat get() = File(
        app, "src/main/java/com/anindra/messages/ui/ChatScreen.kt"
    ).readText()

    private val settings get() = File(
        app, "src/main/java/com/anindra/messages/data/SettingsStore.kt"
    ).readText()

    @Test
    fun theSeenFlagIsStableAndDefaultsToUnseen() {
        assertEquals("voice_guide_seen", com.anindra.messages.data.SettingsStore.KEY_VOICE_GUIDE_SEEN)
        assertTrue(
            "a fresh install has not seen the guide",
            settings.contains("getBoolean(KEY_VOICE_GUIDE_SEEN, false)")
        )
    }

    @Test
    fun theChatShowsTheGuideOnlyWhileItIsUnseen() {
        assertTrue(
            "the composer must ask the flag before showing the card",
            chat.contains("if (hasMicrophone && !vm.settings.voiceGuideSeen && !guideDismissed) {")
        )
        assertTrue(
            "dismissing it must record that it has been seen",
            chat.contains("vm.settings.voiceGuideSeen = true") && chat.contains("guideDismissed = true")
        )
    }

    @Test
    fun aDeviceWithoutAMicrophoneNeverSeesTheGuide() {
        assertTrue(
            "the card explains a control the device does not have, so it is gated",
            chat.contains("if (hasMicrophone && !vm.settings.voiceGuideSeen && !guideDismissed) {")
        )
    }
}
