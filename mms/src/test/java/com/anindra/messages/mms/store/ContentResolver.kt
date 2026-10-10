package android.content

import android.database.Cursor
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * The `android.content.ContentValues` the unit tests run against.
 *
 * The compile-time `android.jar` version silently drops every `put`, which would
 * make every write assertion here vacuous. Same class name, same package, real
 * behaviour.
 */
class ContentValues(@Suppress("UNUSED_PARAMETER") initialCapacity: Int = 8) {

    private val values = LinkedHashMap<String, Any?>()

    // Return types match the platform's, because they are part of the method
    // descriptor the store was compiled against.
    fun put(key: String, value: String?) {
        values[key] = value
    }

    fun put(key: String, value: Int?) {
        values[key] = value
    }

    fun put(key: String, value: Long?) {
        values[key] = value
    }

    fun put(key: String, value: Boolean?) {
        values[key] = value
    }

    fun putNull(key: String) {
        values[key] = null
    }

    fun get(key: String): Any? = values[key]
    fun getAsString(key: String): String? = values[key] as? String
    fun getAsInteger(key: String): Int? = (values[key] as? Number)?.toInt()
    fun getAsLong(key: String): Long? = (values[key] as? Number)?.toLong()
    fun containsKey(key: String): Boolean = values.containsKey(key)
    fun keySet(): Set<String> = values.keys.toSet()
    fun size(): Int = values.size
    fun clear() = values.clear()

    /** The insert a provider would see, as an immutable copy. */
    fun snapshot(): Map<String, Any?> = LinkedHashMap(values)

    override fun toString(): String = values.toString()
}

/**
 * The `android.content.ContentResolver` the unit tests run against.
 *
 * Every accessor here is `final` on the platform class and routes through a real
 * provider, so there is no way to intercept one from a test without replacing the
 * class outright. Only the calls the store makes are declared, which also stops
 * the store reaching for anything the double cannot answer.
 */
abstract class ContentResolver {
    abstract fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor?

    abstract fun insert(uri: Uri, values: ContentValues?): Uri?

    abstract fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int

    abstract fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int

    abstract fun openInputStream(uri: Uri): InputStream?

    abstract fun openOutputStream(uri: Uri): OutputStream?
}
