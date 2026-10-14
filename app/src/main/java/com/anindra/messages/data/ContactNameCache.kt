package com.anindra.messages.data

/**
 * TTL cache for phone-number to contact-name lookups.
 *
 * Every name hit goes through the phone_lookup provider, so the conversation
 * list cannot query per row. Entries therefore expire, and [invalidate] drops
 * them the moment the Contacts provider reports a change, which is what lets a
 * renamed, added or deleted contact reach the list without waiting for the next
 * app resume.
 */
class ContactNameCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val name: String?, val at: Long)

    private val entries = HashMap<String, Entry>()

    /** The cached name for [address], or a fresh [resolve] when absent/expired. */
    @Synchronized
    fun getOrResolve(address: String, resolve: (String) -> String?): String? {
        val now = clock()
        entries[address]?.let { if (now - it.at < ttlMs) return it.name }
        val name = resolve(address)
        entries[address] = Entry(name, now)
        return name
    }

    /** Forgets every entry, so the next lookup re-reads the provider. */
    @Synchronized
    fun invalidate() {
        entries.clear()
    }

    companion object {
        /** How long a resolved name is trusted before it is re-read. */
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
    }
}
