package com.primaloptima.scribe.navigation

import com.primaloptima.scribe.data.NoteDao
import com.primaloptima.scribe.data.NoteMetadata
import com.primaloptima.scribe.viewmodel.DocumentOpenCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Activity-scoped holder bridging note tap events to editor entrance.
 *
 * Responsibilities:
 * 1. Stage note metadata in memory at tap time so the editor entry renders frame 0 immediately.
 * 2. Eagerly prefetch the document body from Room on Dispatchers.IO into [DocumentOpenCache]
 *    during the 300ms navigation transition, so opening is an in-memory hit.
 */
object EditorDocumentSession {
    private val stagedMetadata = ConcurrentHashMap<String, NoteMetadata>()

    /**
     * Stage note metadata synchronously from the tap handler.
     */
    fun stage(noteId: String, metadata: NoteMetadata) {
        stagedMetadata[noteId] = metadata
    }

    /**
     * Consume staged metadata inside the editor nav entry.
     */
    fun consume(noteId: String): NoteMetadata? {
        return stagedMetadata.remove(noteId)
    }

    /**
     * Eagerly prefetch the note body on [Dispatchers.IO] into [DocumentOpenCache].
     * If already cached, this is a no-op.
     */
    fun prefetch(noteId: String, noteDao: NoteDao, scope: CoroutineScope) {
        if (DocumentOpenCache.get(noteId) != null) return
        scope.launch(Dispatchers.IO) {
            try {
                val note = noteDao.getById(noteId)
                if (note != null) {
                    DocumentOpenCache.put(note)
                }
            } catch (_: Throwable) {
                // Background prefetch error is non-fatal; editor loadNote will retry on main path
            }
        }
    }

    /**
     * Clears all staged metadata.
     */
    fun clear() {
        stagedMetadata.clear()
    }
}
