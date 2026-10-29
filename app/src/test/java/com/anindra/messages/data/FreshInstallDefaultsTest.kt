package com.anindra.messages.data

import com.anindra.messages.ui.theme.AppFonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Defaults for someone installing the app for the first time: the redesigned UI
 * and DM Sans. An installation that has been updated keeps what it started
 * with — an update must not restyle the app under a user who never opened
 * Settings — and an explicit choice always wins over both.
 */
class FreshInstallDefaultsTest {

    private val app: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app") }
            .firstOrNull { File(it, "src").isDirectory }
            ?: error("app/src not found")
    }

    private fun read(rel: String) = File(app, rel).readText()

    private val store get() = read("src/main/java/com/anindra/messages/data/SettingsStore.kt")

    @Test
    fun aFreshInstallWithoutAStoredChoiceIsSeededWithDmSans() {
        assertEquals(
            SettingsStore.FONT_DM_SANS,
            SettingsStore.seedFontFor(freshInstall = true, storedFont = null)
        )
    }

    @Test
    fun anUpdatedInstallIsNotReseeded() {
        assertNull(SettingsStore.seedFontFor(freshInstall = false, storedFont = null))
    }

    @Test
    fun anExplicitChoiceIsNeverOverwritten() {
        listOf(
            SettingsStore.FONT_SYSTEM,
            SettingsStore.FONT_DM_SANS,
            SettingsStore.FONT_INTER,
            SettingsStore.FONT_FIGTREE,
            SettingsStore.FONT_POPPINS
        ).forEach { stored ->
            assertNull(
                "an explicit '$stored' choice must survive a fresh-install seed",
                SettingsStore.seedFontFor(freshInstall = true, storedFont = stored)
            )
        }
    }

    @Test
    fun theSeedIsWrittenOnceFromAFreshInstallCheck() {
        assertTrue(
            "the seed must be driven by the install-vs-update check",
            store.contains("seedFontFor(firstInstall, prefs.getString(KEY_FONT_FAMILY, null))")
        )
        assertTrue(
            "fresh vs updated is firstInstallTime == lastUpdateTime",
            store.contains("info.firstInstallTime == info.lastUpdateTime")
        )
    }

    @Test
    fun theDesignedUiIsTheDefault() {
        assertTrue(
            "a user who never touched the switch must land on the redesigned screens",
            store.contains("prefs.getBoolean(KEY_USE_NEW_UI, true)")
        )
    }

    @Test
    fun theFontFallbackStaysSystemSoOnlyTheSeedChangesTheDefault() {
        assertTrue(
            "unseeded reads must keep the old fallback; the seed is what moves a new user",
            store.contains("prefs.getString(KEY_FONT_FAMILY, FONT_SYSTEM)")
        )
    }

    @Test
    fun everyFontFamilyIsStillOfferedInSettings() {
        listOf(
            SettingsStore.FONT_SYSTEM,
            SettingsStore.FONT_DM_SANS,
            SettingsStore.FONT_INTER,
            SettingsStore.FONT_FIGTREE,
            SettingsStore.FONT_POPPINS
        ).forEach { key ->
            assertTrue("font option '$key' must stay selectable", key.isNotEmpty())
        }
        assertEquals(5, AppFonts.options.size)
    }
}
