package com.anindra.messages.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Whether this build can open the database already on the phone.
 *
 * The schema version only ever moves forward. Installing a build from an older
 * branch over one from a newer branch leaves a database the older code cannot
 * read, and `SQLiteOpenHelper` answers that with `SQLiteException: Can't
 * downgrade database from version 35 to 33` from a background worker — the
 * process dies before any screen exists, which reads as "the app does not open".
 *
 * The version is read read-only, before the helper is ever constructed, so the
 * app can explain the situation instead of crashing into it.
 */
object DatabaseCompatibility {

    private const val TAG = "DbCompatibility"

    /** `fileVersion` is 0 when there is no database yet (a fresh install). */
    data class State(val fileVersion: Int, val appVersion: Int) {
        val tooNew: Boolean get() = isTooNew(fileVersion, appVersion)
    }

    /** Split out so the rule is testable without a device. */
    fun isTooNew(fileVersion: Int, appVersion: Int): Boolean = fileVersion > appVersion

    fun check(context: Context, appVersion: Int = DB_VERSION): State {
        val file = context.getDatabasePath(DB_NAME)
        if (!file.exists()) return State(0, appVersion)
        val version = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version }
        }.getOrElse { error ->
            // A file that cannot be read at all is left to the helper, which
            // owns the "corrupt or unreadable" path.
            Log.d(TAG, "unreadable=${error.javaClass.simpleName}")
            0
        }
        Log.d(TAG, "file=$version app=$appVersion tooNew=${isTooNew(version, appVersion)}")
        return State(version, appVersion)
    }

    /** Where a newer database is parked when this build cannot read it. */
    fun backupName(fileVersion: Int): String = "messages.newer-v$fileVersion.db"

    /**
     * Moves a database written by a newer build out of the way, journals and
     * all, so this build can create its own instead of dying on the downgrade.
     *
     * Nothing is deleted: the file is kept beside the live database for the
     * newer build to be reinstalled over, and the notice remembers its name.
     */
    fun setAside(context: Context, state: State): String? {
        val live = context.getDatabasePath(DB_NAME)
        if (!live.exists()) return null
        val name = backupName(state.fileVersion)
        val moved = runCatching {
            // The journals belong to the same database. Moving the main file
            // alone would leave a -wal to be replayed onto the fresh one.
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                val from = File(live.path + suffix)
                if (!from.exists()) return@forEach
                val to = File(live.parentFile, name + suffix)
                to.delete()
                from.renameTo(to)
            }
            File(live.parentFile, name).exists()
        }.getOrDefault(false)
        Log.d(TAG, "setAside=$moved name=$name")
        if (!moved) return null
        prefs(context).edit()
            .putInt(KEY_VERSION, state.fileVersion)
            .putString(KEY_NAME, name)
            .putInt(KEY_APP, state.appVersion)
            .apply()
        return name
    }

    /** The parked-file notice, until the user has been told about it. */
    data class Notice(val fileVersion: Int, val appVersion: Int, val fileName: String)

    fun pendingNotice(context: Context): Notice? {
        val p = prefs(context)
        val version = p.getInt(KEY_VERSION, 0)
        val name = p.getString(KEY_NAME, null)
        if (version <= 0 || name.isNullOrBlank()) return null
        return Notice(version, p.getInt(KEY_APP, DB_VERSION), name)
    }

    fun clearNotice(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private const val PREFS = "db_compatibility"
    private const val KEY_VERSION = "set_aside_version"
    private const val KEY_APP = "set_aside_app_version"
    private const val KEY_NAME = "set_aside_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
