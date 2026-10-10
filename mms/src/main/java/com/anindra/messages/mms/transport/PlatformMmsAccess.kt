package com.anindra.messages.mms.transport

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Read and write access to a PDU file for the platform MMS service.
 *
 * `sendMultimediaMessage` and `downloadMultimediaMessage` are called with a
 * `content://` URI served by this app's FileProvider, but the service runs in
 * another process and has to open that URI, so the grant has to be made
 * explicitly. The service lives in one of two packages depending on the
 * platform version, and a package that is not installed is not an error — the
 * other one may be the one serving.
 *
 * This is the only place that names those packages or makes that grant. The
 * app's own download path hands the platform a destination too, and two copies
 * of this decision is how one of them ends up granting the wrong thing.
 */
object PlatformMmsAccess {
    private val PLATFORM_PACKAGES = listOf("com.android.phone", "com.android.mms.service")

    private const val FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Grants [uri] to whichever platform package is installed.
     *
     * Returns whether at least one took it: a grant that silently failed for
     * every package is what produces a transfer that reports success and leaves
     * the file empty, and the caller has to be able to say so.
     */
    fun grant(context: Context, uri: Uri, write: Boolean): Boolean {
        val flags = if (write) FLAGS else Intent.FLAG_GRANT_READ_URI_PERMISSION
        var granted = false
        for (pkg in PLATFORM_PACKAGES) {
            try {
                context.grantUriPermission(pkg, uri, flags)
                granted = true
            } catch (_: Exception) {
                // Not installed here; the other package may serve.
            }
        }
        return granted
    }

    /**
     * Drops the grant this app made for [uri].
     *
     * The two-argument form drops every programmatic grant for the URI. It does
     * not touch a grant made through an Intent, and none of these are.
     */
    fun revoke(context: Context, uri: Uri) {
        context.revokeUriPermission(uri, FLAGS)
    }
}