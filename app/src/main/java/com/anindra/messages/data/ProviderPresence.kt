package com.anindra.messages.data

/**
 * The key a provider row is filed under when the local database checks which
 * provider rows are still live.
 *
 * SMS and MMS hand out ids from independent spaces, so the same number in each
 * means different rows. MMS ids are namespaced negative, so a local row is
 * only ever matched against its own transport's provider rows — never against
 * the other transport's overlapping ids.
 */
object ProviderPresence {
    fun key(transport: String, sysId: Long): Long =
        if (transport == MmsSupport.TRANSPORT_MMS) -sysId else sysId
}
