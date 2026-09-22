package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmojiButtonSettingTest {

    @Test
    fun keyIsStable() {
        assertEquals("emoji_button_enabled", SettingsStore.KEY_EMOJI_BUTTON)
    }

    @Test
    fun defaultsOff() {
        assertFalse(SettingsStore.DEFAULTS_EMOJI_BUTTON)
    }
}
