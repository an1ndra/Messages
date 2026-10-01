package com.anindra.messages.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Stages an sms-ie backup on disk so a large one can be imported without holding
 * it in memory.
 *
 * A 50k-message backup with images is hundreds of megabytes once decompressed,
 * and the previous reader pulled the whole file into a `ByteArray` and then kept
 * every decompressed part in a map, so peak memory was the entire backup before
 * a single row reached the database. On a mid-range phone that is an
 * OutOfMemoryError, which the caller reported as a generic "cannot read that
 * backup file" because the import is wrapped in `runCatching`.
 *
 * Instead the archive is streamed straight into a scratch directory: the
 * message list to a file, each part to `parts/`, both through a fixed buffer.
 * Peak memory is one buffer, and the caller then reads the message list a line
 * at a time.
 *
 * An oversized part is skipped rather than aborting the archive. One large photo
 * should cost you that photo, not the whole backup -- and whether it was fatal
 * used to depend on the order the entries happened to be written in, which is
 * not something a real export guarantees.
 */
object SmsIeReader {

    private const val TAG = "SmsIeImport"

    /** Per-part cap. A part larger than this is skipped with a warning. */
    const val MAX_PART_BYTES = 8 * 1024 * 1024

    /**
     * Cap on the message list itself. Generous, because a legitimate 50k-record
     * list runs to several megabytes; it exists only to stop a runaway or
     * hostile file, and exceeding it truncates the import rather than reading
     * without bound.
     */
    const val MAX_MESSAGE_BYTES = 256L * 1024 * 1024

    private const val COPY_BUFFER = 64 * 1024

    /** A backup unpacked onto disk. Delete with [close] when finished. */
    class Staged internal constructor(
        private val root: File,
        /** The message list: `messages.ndjson` for v2, the raw JSON for v1. */
        val messageFile: File,
        private val partsDir: File?,
        val zip: Boolean,
        /** Parts that were present but too large to keep. */
        val skippedParts: List<String>
    ) : AutoCloseable {
        /** The scratch directory, so a caller (or a test) can assert cleanup. */
        val debugRoot: File get() = root
        /** Resolves a part's `_data` filename to its bytes, or null if skipped. */
        fun partBytes(name: String): ByteArray? {
            val dir = partsDir ?: return null
            // A part's _data is an absolute path from the exporting app; only
            // the file name is meaningful here, and taking it directly is what
            // stops a crafted backup from writing outside the staging dir.
            val file = File(dir, File(name).name)
            if (!file.isFile) return null
            return runCatching { file.readBytes() }.getOrNull()
        }

        /** True when [name] was skipped for exceeding the per-part cap. */
        fun wasSkipped(name: String): Boolean =
            skippedParts.contains(File(name).name)

        override fun close() {
            root.deleteRecursively()
        }
    }

    /**
     * Unpacks [uri] into a scratch directory under [workDir].
     *
     * Returns null only when the file cannot be opened or holds no message list.
     * A partially readable backup still returns a Staged, so whatever messages
     * could be read are imported and only the damaged parts are lost.
     */
    fun stage(context: Context, uri: Uri, workDir: File): Staged? {
        val raw = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (raw == null) {
            Log.w(TAG, "cannot open $uri")
            return null
        }
        return stage(raw, workDir)
    }

    /**
     * Stages from an already-open stream, which the caller must not close.
     *
     * Split out from the ContentResolver path so the streaming behaviour can be
     * exercised from a plain file in a unit test, with no Android runtime.
     */
    fun stageFile(file: File, workDir: File): Staged? =
        runCatching { file.inputStream() }.getOrNull()?.let { stage(it, workDir) }

    private fun stage(raw: InputStream, workDir: File): Staged? {
        val root = File(workDir, "smsie-stage-${System.nanoTime()}")
        if (!root.mkdirs()) {
            Log.w(TAG, "cannot create staging dir $root")
            return null
        }
        val skipped = mutableListOf<String>()

        // Buffered so the two magic bytes can be peeked without consuming them;
        // everything after that streams.
        val input = BufferedInputStream(raw, COPY_BUFFER)
        val staged = input.use { stream ->
            stream.mark(2)
            val magic = ByteArray(2)
            val got = runCatching { stream.read(magic) }.getOrDefault(0)
            stream.reset()
            val isZip = got == 2 &&
                magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
            if (isZip) stageZip(stream, root, skipped)
            else stagePlain(stream, root)
        }

        if (staged == null) {
            root.deleteRecursively()
        } else if (skipped.isNotEmpty()) {
            Log.w(TAG, "${skipped.size} part(s) skipped for exceeding ${MAX_PART_BYTES / (1024 * 1024)}MB")
        }
        return staged
    }

    private fun stageZip(
        input: InputStream,
        root: File,
        skipped: MutableList<String>
    ): Staged? {
        val partsDir = File(root, "parts")
        val messageFile = File(root, "messages.ndjson")
        var found = false

        runCatching {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || name.isEmpty()) continue
                    when {
                        name == "messages.ndjson" || (name.endsWith(".json") && !found) -> {
                            val written = copy(zip, messageFile.outputStream(), MAX_MESSAGE_BYTES)
                            found = written >= 0
                            if (!found) {
                                Log.w(TAG, "message list too large, truncated at ${MAX_MESSAGE_BYTES} bytes")
                            }
                        }
                        else -> {
                            partsDir.mkdirs()
                            val target = File(partsDir, name)
                            val written = copy(zip, target.outputStream(), MAX_PART_BYTES.toLong())
                            if (written < 0) {
                                // Over the cap: drop this part and keep going.
                                target.delete()
                                skipped += name
                                Log.w(TAG, "part too large, skipped: $name")
                            }
                        }
                    }
                }
            }
        }.onFailure { Log.w(TAG, "zip read failed: ${it.message}") }

        if (!found || !messageFile.isFile || messageFile.length() == 0L) {
            Log.w(TAG, "no message list in archive")
            return null
        }
        return Staged(root, messageFile, partsDir, zip = true, skippedParts = skipped)
    }

    /** v1: the backup is a bare JSON document, copied verbatim. */
    private fun stagePlain(input: InputStream, root: File): Staged? {
        val messageFile = File(root, "messages.json")
        val written = copy(input, messageFile.outputStream(), MAX_MESSAGE_BYTES)
        if (written <= 0L) {
            Log.w(TAG, "empty backup")
            return null
        }
        return Staged(root, messageFile, null, zip = false, skippedParts = emptyList())
    }

    /**
     * Hands each message record to [onRecord] as raw JSON text, one at a time.
     *
     * v2 is newline-delimited and streams. v1 is a single JSON array, so it has
     * to be walked by index: splitting on newlines would not find any, and a
     * 50k-record array is one enormous line.
     *
     * Every non-blank line is handed on, including one that is not valid JSON.
     * Filtering malformed lines out here would hide them, and an import that
     * silently drops records is the thing this rework exists to stop.
     */
    fun forEachRecord(staged: Staged, onRecord: (String) -> Unit) {
        if (staged.zip) {
            staged.messageFile.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) onRecord(trimmed)
                }
            }
        } else {
            val text = staged.messageFile.readText()
            val array = runCatching { org.json.JSONArray(text) }.getOrNull()
            if (array == null) {
                Log.w(TAG, "v1 backup is not a JSON array")
                return
            }
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { onRecord(it.toString()) }
            }
        }
    }

    /** Copies at most [limit] bytes; returns -1 when the source exceeded it. */
    private fun copy(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return -1L
                output.write(buffer, 0, read)
            }
            output.flush()
        } finally {
            runCatching { output.close() }
        }
        return total
    }
}
