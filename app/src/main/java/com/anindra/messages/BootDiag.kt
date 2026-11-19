package com.anindra.messages

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Temporary startup trace for the blank-inbox report. Every step of the launch
 * path appends one line to logcat and to files/boot-diag.txt, so the reason a
 * sync does not run is visible without guessing.
 */
object BootDiag {
    private const val TAG = "BootDiag"

    fun log(context: Context, message: String) {
        Log.i(TAG, message)
        runCatching {
            File(context.filesDir, "boot-diag.txt")
                .appendText("${System.currentTimeMillis()} $message\n")
        }
    }
}
