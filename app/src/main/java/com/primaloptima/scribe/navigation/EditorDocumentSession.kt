package com.primaloptima.scribe.navigation

import android.content.Context
import android.net.Uri
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.NoteMetadata
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.RecoveryManager
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.viewmodel.DocumentOpenCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

/**
 * Activity-scoped holder bridging note tap events to editor entrance.
 *
 * Responsibilities:
 * 1. Stage note metadata in memory at tap time so the editor entry renders frame 0 immediately.
 * 2. Eagerly prefetch the document body from Room/SAF on Dispatchers.IO into [DocumentOpenCache]
 *    during navigation, deduplicating with [EditorViewModel].
 * 3. Enforce identical full materialization rules as [EditorViewModel], including SAF and RecoveryManager.
 * 4. Cancel in-flight prefetch on cache invalidation or document mutation to prevent stale writes.
 */
object EditorDocumentSession {
    private val stagedMetadata = ConcurrentHashMap<String, NoteMetadata>()
    private val inFlightLoads = ConcurrentHashMap<String, Deferred<Note?>>()
    private val inFlightJobs = ConcurrentHashMap<String, Job>()

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
     * Peek staged metadata without consuming (for checking matching metadata).
     */
    fun peek(noteId: String): NoteMetadata? {
        return stagedMetadata[noteId]
    }

    /**
     * Returns any in-flight prefetch load for [noteId] to deduplicate with EditorViewModel.
     */
    fun getInFlightLoad(noteId: String): Deferred<Note?>? {
        return inFlightLoads[noteId]
    }

    /**
     * Cancels any in-flight prefetch for [noteId] and removes it.
     */
    fun cancelPrefetch(noteId: String) {
        inFlightJobs.remove(noteId)?.cancel()
        inFlightLoads.remove(noteId)?.cancel()
    }

    /**
     * Eagerly prefetch and fully materialize the note on [Dispatchers.IO] into [DocumentOpenCache].
     * Uses identical materialization rules: SAF reading and RecoveryManager fallback.
     * Never caches an unresolved external document.
     */
    fun prefetch(noteId: String, context: Context, scope: CoroutineScope) {
        if (DocumentOpenCache.get(noteId) != null) return
        if (inFlightLoads.containsKey(noteId)) return

        val appContext = context.applicationContext as? ScribeApp ?: ScribeApp.instance
        val deferred = scope.async(Dispatchers.IO) {
            try {
                val materialized = materializeNote(appContext, noteId)
                if (materialized != null && coroutineContext.isActive) {
                    DocumentOpenCache.put(materialized)
                }
                materialized
            } catch (_: Throwable) {
                null
            } finally {
                inFlightLoads.remove(noteId)
                inFlightJobs.remove(noteId)
            }
        }

        inFlightLoads[noteId] = deferred
        inFlightJobs[noteId] = deferred
    }

    /**
     * Fully materializes a note using identical rules to EditorViewModel.
     * Never returns an unresolved external note as fully loaded.
     */
    suspend fun materializeNote(context: Context, noteId: String): Note? {
        val app = context.applicationContext as? ScribeApp ?: ScribeApp.instance
        val rawNote = app.database.noteDao().getById(noteId) ?: return null

        // SAF external file retrieval
        val loadedNote = if (rawNote.externalUri != null && !rawNote.loaded) {
            try {
                val content = SAFHelper.readFile(app, Uri.parse(rawNote.externalUri))
                val updated = rawNote.copy(
                    content = content,
                    loaded = true,
                    wordCount = MarkdownUtil.countWords(content),
                    updatedAt = System.currentTimeMillis()
                )
                app.database.noteDao().update(updated)
                updated
            } catch (e: Exception) {
                // If external file read failed and content is empty, cannot materialize
                if (rawNote.content.isEmpty()) {
                    return null
                }
                rawNote
            }
        } else {
            rawNote
        }

        // Never cache an unresolved external document as fully loaded
        if (loadedNote.externalUri != null && !loadedNote.loaded) {
            return null
        }

        // RecoveryManager fallback
        val recoveryContent = try {
            RecoveryManager.get(app, noteId)
        } catch (_: Exception) {
            null
        }

        val effectiveContent = if (loadedNote.content.isEmpty() && !recoveryContent.isNullOrEmpty()) {
            recoveryContent
        } else {
            loadedNote.content
        }

        return if (effectiveContent != loadedNote.content) {
            val safeWc = MarkdownUtil.countWords(effectiveContent)
            loadedNote.copy(content = effectiveContent, wordCount = safeWc)
        } else {
            loadedNote
        }
    }

    /**
     * Clears all staged metadata and cancels in-flight prefetch tasks.
     */
    fun clear() {
        stagedMetadata.clear()
        inFlightJobs.values.forEach { it.cancel() }
        inFlightJobs.clear()
        inFlightLoads.clear()
    }
}
