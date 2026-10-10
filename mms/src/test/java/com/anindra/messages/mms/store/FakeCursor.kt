package com.anindra.messages.mms.store

import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle

/**
 * A [Cursor] over a list of rows.
 *
 * SQLite is forgiving about types — a `TEXT` column reads back as an integer and
 * a `TEXT`-bound parameter compares against a number — so this coerces rather
 * than throwing, which is what the store's own reads rely on.
 */
class FakeCursor(
    private val columns: List<String>,
    rows: List<Map<String, Any?>>,
) : Cursor {

    private val rows = rows
    private var position = -1
    private var closed = false

    val current: Map<String, Any?> get() = rows[position]

    override fun getCount(): Int = rows.size

    override fun moveToNext(): Boolean = seek(position + 1)

    override fun moveToFirst(): Boolean = seek(0)

    override fun moveToLast(): Boolean = seek(rows.size - 1)

    override fun moveToPrevious(): Boolean = seek(position - 1)

    override fun moveToPosition(position: Int): Boolean = seek(position)

    override fun move(relative: Int): Boolean = seek(position + relative)

    private fun seek(target: Int): Boolean {
        position = target
        return target in rows.indices
    }

    override fun getPosition(): Int = position

    override fun isAfterLast(): Boolean = position >= rows.size

    override fun isBeforeFirst(): Boolean = position < 0

    override fun isFirst(): Boolean = position == 0

    override fun isLast(): Boolean = position == rows.size - 1

    override fun isClosed(): Boolean = closed

    override fun close() {
        closed = true
    }

    override fun getColumnCount(): Int = columns.size

    override fun getColumnNames(): Array<String> = columns.toTypedArray()

    override fun getColumnName(index: Int): String = columns[index]

    override fun getColumnIndex(name: String): Int = columns.indexOf(name)

    override fun getColumnIndexOrThrow(name: String): Int =
        getColumnIndex(name).takeIf { it >= 0 }
            ?: throw IllegalArgumentException("column $name does not exist")

    override fun isNull(index: Int): Boolean = current[columns[index]] == null

    override fun getString(index: Int): String? = current[columns[index]]?.toString()

    override fun getInt(index: Int): Int = when (val value = current[columns[index]]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    override fun getLong(index: Int): Long = when (val value = current[columns[index]]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    override fun getShort(index: Int): Short = getInt(index).toShort()

    override fun getDouble(index: Int): Double = getLong(index).toDouble()

    override fun getFloat(index: Int): Float = getLong(index).toFloat()

    override fun getBlob(index: Int): ByteArray = (current[columns[index]] as? String)?.toByteArray()
        ?: ByteArray(0)

    override fun getType(index: Int): Int = when (current[columns[index]]) {
        null -> Cursor.FIELD_TYPE_NULL
        is Number -> Cursor.FIELD_TYPE_INTEGER
        else -> Cursor.FIELD_TYPE_STRING
    }

    override fun getExtras(): Bundle? = null

    override fun respond(bundle: Bundle?): Bundle? = null

    override fun setExtras(bundle: Bundle?) = Unit

    override fun getNotificationUri(): Uri? = null

    override fun setNotificationUri(resolver: android.content.ContentResolver?, uri: Uri?) = Unit

    override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer) = Unit

    override fun registerContentObserver(observer: ContentObserver?) = Unit

    override fun unregisterContentObserver(observer: ContentObserver?) = Unit

    override fun registerDataSetObserver(observer: DataSetObserver?) = Unit

    override fun unregisterDataSetObserver(observer: DataSetObserver?) = Unit

    override fun deactivate() = Unit

    override fun requery(): Boolean = false

    override fun getWantsAllOnMoveCalls(): Boolean = false
}
