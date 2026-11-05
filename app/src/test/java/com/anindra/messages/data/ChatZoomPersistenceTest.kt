package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * #348: the chat zoom is stored per conversation, so the key has to be unique per
 * conversation and stable across builds — the value is written on every pinch and
 * read when the chat is reopened.
 */
class ChatZoomPersistenceTest {

    @Test
    fun eachConversationGetsItsOwnKey() {
        assertEquals("chat_zoom_7", SettingsStore.chatZoomKey(7))
        assertNotEquals(SettingsStore.chatZoomKey(7), SettingsStore.chatZoomKey(70))
        assertNotEquals(SettingsStore.chatZoomKey(1), SettingsStore.chatZoomKey(2))
    }

    @Test
    fun theKeyDoesNotCollideWithAnotherSetting() {
        val others = listOf(
            SettingsStore.KEY_THEME,
            SettingsStore.KEY_HIDE_LINKS,
            SettingsStore.KEY_FONT_FAMILY,
            SettingsStore.KEY_PERIODIC_BACKUP,
            SettingsStore.KEY_BLOCKED_KEYWORDS
        )
        others.forEach {
            assertNotEquals(it, SettingsStore.chatZoomKey(1))
        }
    }
}
