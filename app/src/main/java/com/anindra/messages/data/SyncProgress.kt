package com.anindra.messages.data

/** Maps the phases of the initial system import onto 0..1 so the loading bar
 *  moves during the slow steps (provider read, conversation resolution) instead
 *  of appearing frozen and then jumping to done. */
object SyncProgress {
    /** Fraction of the bar covered by reading the provider cursor. */
    const val READ_END = 0.35f
    /** Fraction reached once every sender has a conversation row. */
    const val RESOLVE_END = 0.5f
    /** Fraction reached once every pending row is imported. */
    const val IMPORT_END = 0.95f

    fun read(done: Int, total: Int): Float =
        if (total <= 0) 0f else (READ_END * done / total).coerceIn(0f, READ_END)

    fun resolve(done: Int, total: Int): Float =
        if (total <= 0) READ_END
        else (READ_END + (RESOLVE_END - READ_END) * done / total).coerceIn(READ_END, RESOLVE_END)

    fun import(done: Int, pending: Int): Float =
        if (pending <= 0) RESOLVE_END
        else (RESOLVE_END + (IMPORT_END - RESOLVE_END) * done / pending).coerceIn(RESOLVE_END, IMPORT_END)
}
