package com.anindra.messages.mms.store

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log

/**
 * Whether the installed provider has the optional `sub_id` column.
 *
 * `sub_id` only exists on a device that carries more than one subscription, so a
 * provider without it rejects the whole insert rather than ignoring the unknown
 * key. The answer cannot change while the process lives, and re-proving it on
 * every write is a query per message, so it is asked once.
 */
internal class ProviderProbe(private val probe: () -> Boolean) {

    private val cached: Boolean by lazy { probe() }

    fun supportsSubscriptionId(): Boolean = cached

    companion object {
        private const val COLUMN_SUB_ID = "sub_id"

        fun forResolver(resolver: ContentResolver, messagesUri: Uri): ProviderProbe =
            ProviderProbe {
                try {
                    resolver.query(messagesUri, arrayOf(COLUMN_SUB_ID), null, null, null)
                        ?.use { true } ?: false
                } catch (e: SQLiteException) {
                    Log.w("MmsStore", "provider has no sub_id column: ${e.message}")
                    false
                }
            }
    }
}
