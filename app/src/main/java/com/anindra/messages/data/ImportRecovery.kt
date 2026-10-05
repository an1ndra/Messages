package com.anindra.messages.data

/**
 * What to do about the files a REPLACE import leaves behind if its process dies.
 *
 * The swap closes the live database, renames the verified temp file over it and
 * only then deletes the pre-import copy. A crash in that window leaves either
 * the temp or the pre-import copy on disk and no live database. This decides,
 * from what actually remains, how to get back to a complete database — in one
 * place, so the recovery path and the importer cannot disagree about the shape
 * of an interrupted run.
 */
object ImportRecovery {
    enum class Action { NONE, PROMOTE_TEMP, RESTORE_PRE_IMPORT }

    fun decide(
        liveDatabaseExists: Boolean,
        preImportExists: Boolean,
        tempIsValid: Boolean
    ): Action = when {
        liveDatabaseExists -> Action.NONE
        preImportExists -> Action.RESTORE_PRE_IMPORT
        tempIsValid -> Action.PROMOTE_TEMP
        else -> Action.NONE
    }
}
