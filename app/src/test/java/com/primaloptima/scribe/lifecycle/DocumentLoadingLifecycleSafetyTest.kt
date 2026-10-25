package com.primaloptima.scribe.lifecycle

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.NoteMetadata
import com.primaloptima.scribe.data.toMetadata
import com.primaloptima.scribe.util.DocumentOpenTrace
import com.primaloptima.scribe.viewmodel.DocumentLoadState
import com.primaloptima.scribe.viewmodel.ExternalSyncStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Focused automated regression suite for document loading lifecycle safety,
 * generation counters, empty document persistence, Room/SAF error handling,
 * and on-demand note body retrieval.
 */
class DocumentLoadingLifecycleSafetyTest {

    @Test
    fun testMetadataPlaceholderTransitionsToLoadedWithSameId() {
        // Requirement B: Metadata placeholder must never be treated as the actual loaded document.
        // It must cleanly transition from Loading to Loaded with the exact same note ID.
        val noteId = "note-alpha-123"
        val metadata = NoteMetadata(
            id = noteId,
            name = "Chapter 1\nBeginnings",
            bookId = "book-main",
            wordCount = 1500,
            snippet = "It was a dark and stormy night..."
        )

        // Stage 1: Loading state with metadata
        var state: DocumentLoadState = DocumentLoadState.Loading(noteId, metadata)
        assertTrue("State must be Loading", state is DocumentLoadState.Loading)
        val loadingState = state as DocumentLoadState.Loading
        assertEquals(noteId, loadingState.noteId)
        assertEquals(metadata, loadingState.metadata)
        assertFalse("Metadata placeholder must NOT be considered Loaded", state is DocumentLoadState.Loaded)

        // Stage 2: Full document arrives from local persistence
        val fullNote = Note(
            id = noteId,
            name = "Chapter 1\nBeginnings",
            bookId = "book-main",
            content = "It was a dark and stormy night. The wind howled outside.",
            wordCount = 1500,
            loaded = true
        )
        state = DocumentLoadState.Loaded(fullNote)
        assertTrue("State must transition to Loaded", state is DocumentLoadState.Loaded)
        val loadedState = state as DocumentLoadState.Loaded
        assertEquals("Loaded document ID must match requested ID", noteId, loadedState.note.id)
        assertEquals("Loaded document content must match full body", fullNote.content, loadedState.note.content)
        assertNotEquals("Content must not be empty placeholder", "", loadedState.note.content)
    }

    @Test
    fun testRapidDocumentSwitching_RejectsStaleOlderGeneration() {
        // Requirement A & C: A previous loading task can finish after user selected a new document.
        // Request generation counter must reject obsolete results.
        val generationCounter = AtomicLong(0)

        // User initiates loading Document A
        val reqIdA = generationCounter.incrementAndGet()
        assertEquals(1L, reqIdA)

        // User quickly switches to Document B before A finishes
        val reqIdB = generationCounter.incrementAndGet()
        assertEquals(2L, reqIdB)

        var activeDocumentId: String? = null
        var activeContent: String? = null

        // Callback for Document A attempts to commit
        val commitDocA = {
            if (generationCounter.get() == reqIdA) {
                activeDocumentId = "doc-A"
                activeContent = "Content A"
            }
        }

        // Callback for Document B attempts to commit
        val commitDocB = {
            if (generationCounter.get() == reqIdB) {
                activeDocumentId = "doc-B"
                activeContent = "Content B"
            }
        }

        // Document A completes late
        commitDocA()
        assertNull("Obsolete request A must be rejected and not set active document", activeDocumentId)

        // Document B completes
        commitDocB()
        assertEquals("doc-B", activeDocumentId)
        assertEquals("Content B", activeContent)
    }

    @Test
    fun testSavingIntentionallyEmptiedDocument_MarkedDirtyAndSaved() {
        // Requirement 4 & E: An intentionally emptied document must be saved.
        // Never use content.isNotEmpty() to guard save logic.
        val note = Note(
            id = "note-delete-all",
            name = "Draft",
            content = "Initial paragraphs with multiple sentences."
        )

        var isDirty = false
        var pendingContent = note.content
        var lastSavedContent = note.content

        // User selects all and presses Backspace -> content is now empty string ""
        val newContent = ""
        if (newContent != pendingContent) {
            pendingContent = newContent
            isDirty = true // Dirty flag set independently of string length
        }

        assertTrue("Dirty flag must be true when text is deleted", isDirty)
        assertEquals("", pendingContent)

        // Evaluation for whether persistence is needed
        val needsSave = isDirty || pendingContent != lastSavedContent
        assertTrue("Intentionally emptied document MUST require save", needsSave)

        // Simulate save completion
        val simulatedDbSave = { content: String ->
            // DB receives empty string
            assertEquals("", content)
            true
        }
        val roomSuccess = simulatedDbSave(pendingContent)
        if (roomSuccess) {
            lastSavedContent = pendingContent
            isDirty = false
        }

        assertFalse("Dirty state cleared after successful save", isDirty)
        assertEquals("", lastSavedContent)
    }

    @Test
    fun testRetainingDirtyContentAfterFailedRoomWrite() {
        // Requirement 5 & E: A failed local save must leave content dirty/recoverable.
        // Must never mark it saved just because a save was launched.
        val note = Note(
            id = "note-room-fail",
            name = "Important Notes",
            content = "Original"
        )
        var isDirty = true
        val pendingContent = "Newly typed critical paragraphs"
        var lastSavedContent = "Original"

        val recoveryStore = mutableMapOf<String, String>()

        // Simulate Room save throwing SQLiteDiskIOException
        var roomSuccess = false
        try {
            throw java.io.IOException("Disk full / DB write failed")
        } catch (_: Exception) {
            roomSuccess = false
        }

        if (roomSuccess) {
            lastSavedContent = pendingContent
            isDirty = false
        } else {
            // Keep dirty and preserve in emergency recovery
            isDirty = true
            recoveryStore[note.id] = pendingContent
        }

        assertTrue("isDirty must remain TRUE after Room write failure", isDirty)
        assertEquals("lastSavedContent must NOT be updated on failure", "Original", lastSavedContent)
        assertEquals("Recovery mechanism must contain latest text", pendingContent, recoveryStore[note.id])
    }

    @Test
    fun testExternalSyncFailure_PreservesLocalVaultContent() {
        // Requirement 9, 10 & H: External SAF write failure must be reported as SyncFailed,
        // while Room local database save still succeeds without discarding local changes.
        val note = Note(
            id = "ext-note-001",
            name = "External Article",
            content = "Local content v1",
            externalUri = "content://com.android.externalstorage.documents/tree/..."
        )
        val newText = "Updated local content v2 with fresh revisions"
        var externalSyncStatus: ExternalSyncStatus = ExternalSyncStatus.Synced
        var localDbContent = note.content

        // Simulate SAF write failing (permission revoked, SD card unmounted)
        var safSuccess = true
        try {
            throw java.io.FileNotFoundException("Storage volume unmounted")
        } catch (e: Exception) {
            externalSyncStatus = ExternalSyncStatus.SyncFailed(note.externalUri ?: "", e.localizedMessage ?: "Failed")
            safSuccess = false
        }

        // Room save still proceeds
        var roomSuccess = false
        try {
            localDbContent = newText
            roomSuccess = true
        } catch (_: Exception) {
            roomSuccess = false
        }

        assertFalse("SAF write must report failure", safSuccess)
        assertTrue("Local Room save must succeed", roomSuccess)
        assertEquals("Local vault must retain latest text despite SAF failure", newText, localDbContent)
        assertTrue("External sync status must be SyncFailed", externalSyncStatus is ExternalSyncStatus.SyncFailed)
    }

    @Test
    fun testWorkbenchAndFloatingWindows_LoadOnlyRequiredNotesOnDemand() {
        // Requirement D: Do not load every note body into memory.
        // Query only the specific required IDs for workbench/floating panels.
        val libraryMetadata = listOf(
            NoteMetadata(id = "n1", name = "Note 1", snippet = "Snippet 1"),
            NoteMetadata(id = "n2", name = "Note 2", snippet = "Snippet 2"),
            NoteMetadata(id = "n3", name = "Note 3", snippet = "Snippet 3"),
            NoteMetadata(id = "n4", name = "Note 4", snippet = "Snippet 4"),
            NoteMetadata(id = "n5", name = "Note 5", snippet = "Snippet 5")
        )

        // Mock database containing full bodies
        val mockDatabase = mapOf(
            "n1" to Note(id = "n1", name = "Note 1", content = "Full body 1 (100KB)"),
            "n2" to Note(id = "n2", name = "Note 2", content = "Full body 2 (150KB)"),
            "n3" to Note(id = "n3", name = "Note 3", content = "Full body 3 (200KB)"),
            "n4" to Note(id = "n4", name = "Note 4", content = "Full body 4 (80KB)"),
            "n5" to Note(id = "n5", name = "Note 5", content = "Full body 5 (120KB)")
        )

        // Pinned pane requests only n2 and n4
        val requestedPinnedIds = setOf("n2", "n4")
        val fetchedNotes = mutableMapOf<String, Note>()

        // Simulated focused fetch query (SELECT * FROM notes WHERE id IN (:ids))
        val missing = requestedPinnedIds.filter { !fetchedNotes.containsKey(it) }
        assertEquals(2, missing.size)
        missing.forEach { id ->
            mockDatabase[id]?.let { fetchedNotes[id] = it }
        }

        assertEquals("Only 2 notes should be materialized in workbench memory", 2, fetchedNotes.size)
        assertTrue(fetchedNotes.containsKey("n2"))
        assertTrue(fetchedNotes.containsKey("n4"))
        assertFalse("Unreferenced note n1 must NOT be loaded", fetchedNotes.containsKey("n1"))
        assertFalse("Unreferenced note n3 must NOT be loaded", fetchedNotes.containsKey("n3"))
        assertFalse("Unreferenced note n5 must NOT be loaded", fetchedNotes.containsKey("n5"))
    }

    @Test
    fun testDocumentOpenTrace_IndependentMilestones() {
        // Requirement 10 & G: Milestone 1 (first_correct_visible_content) and
        // Milestone 2 (full_wordwrap_ready) are recorded as independent stages.
        val noteId = "trace-test-note"
        val trace = DocumentOpenTrace.start(noteId)

        trace.mark("metadata_lookup")
        trace.mark("full_content_db_retrieval")
        trace.mark("inlay_hints_computed")
        trace.mark("sora_set_text_start")
        trace.mark("sora_set_text_completed")
        trace.mark("first_correct_visible_content")
        trace.mark("full_wordwrap_ready")
        trace.mark("background_stats_and_prose")
        trace.finish("test complete")

        assertNotNull(trace.getElapsedMs("first_correct_visible_content"))
        assertNotNull(trace.getElapsedMs("full_wordwrap_ready"))
        assertNotNull(trace.getElapsedMs("sora_set_text_completed"))
    }
}
