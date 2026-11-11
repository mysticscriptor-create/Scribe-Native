package com.primaloptima.scribe.viewmodel

import com.primaloptima.scribe.data.Note

/**
 * Holds the last few documents that were opened, fully materialised.
 *
 * A hit means the editor can publish Loaded on the tap frame with no I/O at all:
 * no query, no mapping, no parsing.
 */
object DocumentOpenCache {
    private const val MAX_ENTRIES = 4
    private val lock = Any()

    private val entries = object : LinkedHashMap<String, Note>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Note>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(noteId: String): Note? = synchronized(lock) { entries[noteId] }

    fun put(note: Note) = synchronized(lock) { entries[note.id] = note }

    /** Call from every path that changes a stored document and has no fresh Note to put. */
    fun invalidate(noteId: String) = synchronized(lock) { entries.remove(noteId) }

    fun clear() = synchronized(lock) { entries.clear() }
}
