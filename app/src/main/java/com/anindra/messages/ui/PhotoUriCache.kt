package com.anindra.messages.ui

/**
 * Caches the contact photo URI resolved for a phone number, including the
 * "no photo" result (stored as an empty string). Without the negative entry,
 * contacts without a photo re-run the ContactsContract `phone_lookup` query on
 * every avatar composition, which is the main source of the visible delay.
 */
class PhotoUriCache(private val max: Int = 1024) {
    private val map = LinkedHashMap<String, String>(16, 0.75f, true)

    fun get(key: String): String? = map[key]

    fun put(key: String, uri: String?) {
        map[key] = uri ?: ""
        while (map.size > max) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }
}
