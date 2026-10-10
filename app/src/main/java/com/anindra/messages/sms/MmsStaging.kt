package com.anindra.messages.sms

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.anindra.messages.mms.transport.PlatformMmsAccess
import java.io.File

/**
 * The file the platform MMS service downloads a PDU into.
 *
 * `downloadMultimediaMessage` is handed a destination and writes it from the
 * MMS service's own process, so the destination has to be a file this app's
 * FileProvider serves, with the grant given to the package hosting the service.
 * A `content://mms/<id>` message row is not such a destination: the platform
 * cannot open a provider message URI for writing, and every attempt fails with
 * `MMS_ERROR_IO_ERROR`.
 *
 * One place decides this, because the platform transport and the app's own
 * download path both hand the platform a destination and only one of them
 * getting it right is a bug that reappears on the other.
 */
internal object MmsStaging {
    private const val TAG = "MmsDownload"

    fun file(context: Context, name: String) = File(context.cacheDir, name)

    /** For [rowId]'s announcement row, a name that survives a process restart. */
    fun nameFor(rowId: Long) = "mms-recv-$rowId.dat"

    /**
     * A pre-created, granted, writable destination for the platform service.
     *
     * Pre-created because the provider has to be able to open the path before
     * anything writes to it, and granted because a grant that silently failed
     * produces exactly the "reported success but wrote no PDU" case, which is
     * why a refusal is logged rather than swallowed.
     */
    fun uriFor(context: Context, name: String, write: Boolean): Uri? {
        val file = file(context, name)
        if (!file.exists() && !runCatching { file.createNewFile() }.getOrDefault(false)) {
            MmsTrace.w(TAG, "staging file $name could not be created")
            return null
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrElse {
            MmsTrace.w(TAG, "staging file $name is outside the FileProvider paths", it)
            return null
        }
        if (!PlatformMmsAccess.grant(context, uri, write)) {
            MmsTrace.w(TAG, "no platform package could be granted $uri; nothing will be written to it")
        }
        return uri
    }

    /** The grant outlives the transfer that needed it unless it is dropped here. */
    fun release(context: Context, name: String) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file(context, name))
        }.getOrNull()
        if (uri != null) runCatching { PlatformMmsAccess.revoke(context, uri) }
            .onFailure { MmsTrace.w(TAG, "revoking $uri failed") }
        file(context, name).delete()
    }
}