package com.anindra.messages.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A restore that dies mid-swap must come back to a complete database. These are
 * the only three shapes of leftover: a healthy live file, a pre-import copy, or
 * a valid staged temp file.
 */
class ImportRecoveryTest {

    @Test
    fun aHealthyLiveDatabaseIsLeftAlone() {
        assertEquals(
            ImportRecovery.Action.NONE,
            ImportRecovery.decide(liveDatabaseExists = true, preImportExists = true, tempIsValid = true)
        )
    }

    @Test
    fun aMissingLiveDatabaseFallsBackToThePreImportCopy() {
        assertEquals(
            ImportRecovery.Action.RESTORE_PRE_IMPORT,
            ImportRecovery.decide(liveDatabaseExists = false, preImportExists = true, tempIsValid = true)
        )
    }

    @Test
    fun aMissingLiveDatabasePromotesAValidStagedTemp() {
        assertEquals(
            ImportRecovery.Action.PROMOTE_TEMP,
            ImportRecovery.decide(liveDatabaseExists = false, preImportExists = false, tempIsValid = true)
        )
    }

    @Test
    fun anInvalidTempIsNotPromoted() {
        assertEquals(
            ImportRecovery.Action.NONE,
            ImportRecovery.decide(liveDatabaseExists = false, preImportExists = false, tempIsValid = false)
        )
    }
}
