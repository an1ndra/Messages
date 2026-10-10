package com.anindra.messages.mms.net

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.telephony.SubscriptionManager
import java.util.Collections

/** Resolves the MMS APN for a subscription, or null when the carrier has none. */
interface ApnResolver {
    fun resolve(subscriptionId: Int): ApnProfile?
}

/**
 * One carriers-table row, narrowed to the columns [ApnProfile] needs.
 *
 * The platform cursor is not mockable under plain JUnit, so selection and
 * normalisation are written against this instead of against `Cursor`.
 */
data class ApnRow(
    val apn: String?,
    val mmsc: String?,
    val proxy: String?,
    val port: Int?,
    val type: String?,
)

/**
 * Picks the MMS row out of a carrier's APN list.
 *
 * Rows are matched on the `type` column rather than on the presence of an MMSC
 * because a data-only row frequently carries one; the carrier decides which
 * APN carries MMS, and that decision is in `type`.
 */
object ApnRowSelector {

    fun isMmsType(type: String?): Boolean {
        if (type.isNullOrBlank()) return false
        return type.split(',').any { entry ->
            val value = entry.trim()
            value.equals(TYPE_MMS, ignoreCase = true) || value == TYPE_ANY
        }
    }

    fun select(rows: List<ApnRow>): ApnProfile? =
        rows.firstOrNull { isMmsType(it.type) }
            ?.let { ApnProfile(apnName = it.apn, mmscUrl = it.mmsc, mmsProxy = it.proxy, mmsPort = it.port, type = it.type) }

    const val TYPE_MMS = "mms"
    const val TYPE_ANY = "*"
}

/**
 * Caches resolved APNs per subscription.
 *
 * The carriers table is re-read on every send in the reference implementation.
 * A subscription's APN only changes when the SIM or the carrier configuration
 * does, so [invalidate] is the only thing that should ever force a re-read.
 */
class CachingApnResolver(
    private val load: (Int) -> ApnProfile?,
    private val defaultSubscriptionId: () -> Int,
) : ApnResolver {
    // HashMap rather than ConcurrentHashMap: a missing APN has to be cached too,
    // or every attempt re-queries the table to learn the same nothing.
    private val cache: MutableMap<Int, ApnProfile?> =
        Collections.synchronizedMap(HashMap<Int, ApnProfile?>())

    override fun resolve(subscriptionId: Int): ApnProfile? {
        val key = keyFor(subscriptionId)
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }
        val loaded = load(key)
        synchronized(cache) { cache[key] = loaded }
        return loaded
    }

    /** Drops one subscription's entry, or all of them when [subscriptionId] is null. */
    fun invalidate(subscriptionId: Int? = null) {
        synchronized(cache) {
            if (subscriptionId == null) cache.clear() else cache.remove(keyFor(subscriptionId))
        }
    }

    private fun keyFor(subscriptionId: Int): Int =
        if (subscriptionId > 0) subscriptionId else defaultSubscriptionId()
}

/** Reads the carriers table, one subscription at a time. */
class TelephonyApnResolver(
    private val contentResolver: ContentResolver,
    private val defaultSubscriptionId: () -> Int = { SubscriptionManager.getDefaultSmsSubscriptionId() },
) : ApnResolver {
    private val caching = CachingApnResolver(::load, defaultSubscriptionId)

    override fun resolve(subscriptionId: Int): ApnProfile? = caching.resolve(subscriptionId)

    fun invalidate(subscriptionId: Int? = null) = caching.invalidate(subscriptionId)

    private fun load(subscriptionId: Int): ApnProfile? = ApnRowSelector.select(queryRows(subscriptionId))

    private fun queryRows(subscriptionId: Int): List<ApnRow> {
        if (subscriptionId <= 0) return emptyList()
        // READ_PHONE_STATE can be revoked between a check and the query, so the
        // SecurityException is caught here rather than treated as "no APN".
        val cursor = try {
            contentResolver.query(Uri.parse("$CARRIERS_URI/subId/$subscriptionId"), PROJECTION, null, null, null)
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()
        return cursor.use(::readRows)
    }

    private fun readRows(cursor: Cursor): List<ApnRow> {
        val apnIndex = cursor.getColumnIndex(APN)
        val mmscIndex = cursor.getColumnIndex(MMSC)
        val proxyIndex = cursor.getColumnIndex(MMSPROXY)
        val portIndex = cursor.getColumnIndex(MMSPORT)
        val typeIndex = cursor.getColumnIndex(TYPE)
        val rows = ArrayList<ApnRow>(cursor.count)
        while (cursor.moveToNext()) {
            rows += ApnRow(
                apn = apnIndex.takeIf { it >= 0 }?.let(cursor::getString),
                mmsc = mmscIndex.takeIf { it >= 0 }?.let(cursor::getString),
                proxy = proxyIndex.takeIf { it >= 0 }?.let(cursor::getString),
                port = portIndex.takeIf { it >= 0 }?.let(cursor::getInt),
                type = typeIndex.takeIf { it >= 0 }?.let(cursor::getString),
            )
        }
        return rows
    }

    private companion object {
        const val CARRIERS_URI = "content://telephony/carriers"
        const val APN = "apn"
        const val MMSC = "mmsc"
        const val MMSPROXY = "mmsproxy"
        const val MMSPORT = "mmsport"
        const val TYPE = "type"

        val PROJECTION = arrayOf(APN, MMSC, MMSPROXY, MMSPORT, TYPE)
    }
}