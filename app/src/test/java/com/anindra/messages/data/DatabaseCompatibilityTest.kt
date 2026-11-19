package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A database written by a newer build cannot be read by an older one: SQLite
 * answers "Can't downgrade database from version 35 to 33" and the process dies
 * before any screen exists. The rule itself is small, so it is pinned here.
 */
class DatabaseCompatibilityTest {

    @Test
    fun aDatabaseFromANewerBuildIsRefused() {
        assertTrue(DatabaseCompatibility.isTooNew(fileVersion = 35, appVersion = 33))
        assertTrue(DatabaseCompatibility.isTooNew(fileVersion = 34, appVersion = 33))
    }

    @Test
    fun theSameVersionIsFine() {
        assertFalse(DatabaseCompatibility.isTooNew(fileVersion = 33, appVersion = 33))
    }

    @Test
    fun anOlderDatabaseIsUpgradedNotRefused() {
        // Upgrades are the normal path: onUpgrade has migrations for these.
        assertFalse(DatabaseCompatibility.isTooNew(fileVersion = 32, appVersion = 33))
        assertFalse(DatabaseCompatibility.isTooNew(fileVersion = 4, appVersion = 33))
    }

    @Test
    fun aFreshInstallIsNotRefused() {
        // No database file yet reads as version 0.
        assertFalse(DatabaseCompatibility.isTooNew(fileVersion = 0, appVersion = 33))
    }

    @Test
    fun aNewerDatabaseIsParkedUnderAVersionedName() {
        // The name carries the version so two newer builds cannot overwrite
        // each other's file, and so the dialog can say exactly where it went.
        assertEquals(
            "messages.newer-v35.db",
            DatabaseCompatibility.backupName(35)
        )
        assertEquals(
            "messages.newer-v40.db",
            DatabaseCompatibility.backupName(40)
        )
    }

    @Test
    fun theStateCarriesTheVerdictAndBothNumbers() {
        val state = DatabaseCompatibility.State(fileVersion = 35, appVersion = 33)

        assertTrue(state.tooNew)
        // Both are shown to the user, so the order matters: this build first.
        assertTrue(state.appVersion < state.fileVersion)
    }
}
