package com.primaloptima.scribe.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import com.primaloptima.scribe.ui.components.ScribeScrollPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Immutable snapshot of the editor's visible document viewport.
 *
 * @param sessionKey The unique navigation session key (typically Route.Editor)
 * @param noteId Document ID associated with this snapshot
 * @param scrollD Physical canvas displacement of the header (0..headerHeight)
 * @param editorOffsetY Internal vertical scroll offset of the Sora CodeEditor
 * @param totalLogicalScroll Canonical 1-DOF combined displacement: scrollD + editorOffsetY
 * @param timestamp Time of capture
 */
data class ViewportSnapshot(
    val sessionKey: Any,
    val noteId: String,
    val scrollD: Int,
    val editorOffsetY: Int,
    val totalLogicalScroll: Int = scrollD + editorOffsetY,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Interface through which an active MainEditorScreen session communicates
 * with the restoration coordinator.
 */
interface EditorSessionClient {
    val sessionKey: Route.Editor
    val noteId: String

    /**
     * Captures the current visible viewport displacement from canvas & editor.
     * Must account for in-progress flings/scrolling.
     */
    fun captureCurrentViewport(): ViewportSnapshot?

    /**
     * Executes viewport restoration with the specified snapshot.
     * Must only apply when canvas and editor layout (and wordwrap) are ready.
     */
    fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit)

    /**
     * Checks if both canvas and editor layout are settled and ready for scroll measurement.
     */
    fun isLayoutReady(): Boolean
}

/**
 * CompositionLocal providing access to the EditorSessionRestorationCoordinator.
 */
val LocalEditorRestorationCoordinator = compositionLocalOf<EditorSessionRestorationCoordinator?> { null }

/**
 * Architectural coordinator for Editor Viewport Restoration across Navigation 3 screens.
 *
 * Responsibilities:
 * 1. Centrally observes the Navigation 3 backStack at the navigation host.
 * 2. Automatically detects when an editor session becomes covered by ANY new destination
 *    (present or future) and captures its live viewport state.
 * 3. Automatically detects when that same editor session becomes active again and requests
 *    exactly ONE coordinated restoration.
 * 4. Accommodates adaptive multi-pane layouts (ListDetailSceneStrategy) so an editor
 *    is not falsely marked covered if it remains visible in the detail pane.
 * 5. Coordinates editor-owned overlays/sheets and Activity lifecycle transitions through
 *    the exact same single-flight restoration path.
 * 6. Ensures session isolation: changing documents or editor sessions immediately invalidates
 *    pending work and prevents stale snapshots from being applied.
 */
class EditorSessionRestorationCoordinator {

    private val sessionSnapshots = ConcurrentHashMap<String, ViewportSnapshot>()
    private val clientMap = ConcurrentHashMap<Route.Editor, EditorSessionClient>()
    private val restorationGeneration = AtomicLong(0)

    @Volatile
    private var activeEditorKey: Route.Editor? = null

    @Volatile
    private var activeNoteId: String? = null

    @Volatile
    private var isCurrentlyCovered: Boolean = false

    @Volatile
    private var isOverlayCovering: Boolean = false

    @Volatile
    private var isAppPaused: Boolean = false

    @Volatile
    private var activeRestorationGeneration: Long = -1L

    /**
     * Evaluates whether a given editor route is visible in the current Navigation 3 backStack.
     *
     * Pure logic function:
     * - In compact mode (phone): Visible iff it is the last item on the backStack.
     * - In expanded mode (two-pane tablet): Visible iff it is in the backStack, no subsequent
     *   Route.Editor exists, and no non-listPane entry (e.g. Settings, History, Guide, Shortcuts,
     *   Sheets, ExportStudio, or any future fullscreen/modal destination) is pushed on top of it.
     */
    fun isEditorVisibleInBackStack(
        editorKey: Route.Editor,
        backStack: List<NavKey>,
        isCompact: Boolean
    ): Boolean {
        if (backStack.isEmpty()) return false
        val index = backStack.lastIndexOf(editorKey)
        if (index == -1) return false

        if (isCompact) {
            return index == backStack.lastIndex
        }

        // Expanded / Two-Pane Mode:
        // The editor occupies the detail pane.
        val trailing = backStack.subList(index + 1, backStack.size)
        if (trailing.isEmpty()) return true

        // If another Route.Editor is pushed on top, this editor is covered.
        if (trailing.any { it is Route.Editor }) return false

        // Any screen in trailing that is NOT a list pane (Route.Book) covers the window or detail pane.
        // Future destinations without ListDetailSceneStrategy.listPane() will correctly cover the editor.
        val hasCoveringScreen = trailing.any { it !is Route.Book }
        return !hasCoveringScreen
    }

    /**
     * Central observation callback invoked when the Navigation 3 backStack changes.
     */
    fun onNavigationBackStackChanged(backStack: List<NavKey>, isCompact: Boolean) {
        val editorsInStack = backStack.filterIsInstance<Route.Editor>()

        if (editorsInStack.isEmpty()) {
            // No editor in stack. If one was previously active, it has been popped.
            val prevKey = activeEditorKey
            if (prevKey != null) {
                invalidateSession(prevKey)
            }
            activeEditorKey = null
            activeNoteId = null
            isCurrentlyCovered = false
            return
        }

        val topEditor = editorsInStack.last()
        val isVisible = isEditorVisibleInBackStack(topEditor, backStack, isCompact)

        val prevActiveKey = activeEditorKey
        val prevCovered = isCurrentlyCovered

        if (prevActiveKey != null && prevActiveKey != topEditor) {
            // Active session genuinely changed (different note/editor)
            // Invalidate pending work for previous session
            invalidateSession(prevActiveKey)
            activeEditorKey = topEditor
            activeNoteId = topEditor.noteId
            isCurrentlyCovered = !isVisible

            if (!isVisible) {
                // Suspended immediately upon arrival
                captureCurrentViewport(topEditor)
            }
            return
        }

        activeEditorKey = topEditor
        activeNoteId = topEditor.noteId

        if (!isVisible && !prevCovered) {
            // Transition: Active -> Covered (e.g. user pushed Settings, Guide, History, Shortcuts, etc.)
            isCurrentlyCovered = true
            captureCurrentViewport(topEditor)
        } else if (isVisible && prevCovered) {
            // Transition: Covered -> Active (e.g. user popped auxiliary screen and returned to editor)
            isCurrentlyCovered = false
            if (!isOverlayCovering && !isAppPaused) {
                requestCoordinatedRestoration(topEditor)
            }
        }
    }

    /**
     * Registers an active MainEditorScreen instance.
     */
    fun registerClient(client: EditorSessionClient) {
        clientMap[client.sessionKey] = client
        // If there's an existing snapshot and the session is active and not covered,
        // trigger restoration (e.g. if the view was recreated while returning).
        if (activeEditorKey == client.sessionKey && !isCurrentlyCovered && !isOverlayCovering && !isAppPaused) {
            if (hasSnapshot(client.noteId)) {
                requestCoordinatedRestoration(client.sessionKey)
            }
        }
    }

    /**
     * Unregisters an editor instance when disposed.
     */
    fun unregisterClient(client: EditorSessionClient) {
        clientMap.remove(client.sessionKey)
    }

    /**
     * Coordinates editor-owned overlays, dialogs, and bottom sheets.
     * Reuses the exact same restoration pipeline so overlays do not create independent scroll paths.
     */
    fun onOverlayVisibilityChanged(editorKey: Route.Editor, isCovered: Boolean) {
        if (activeEditorKey != editorKey) return
        val wasCovered = isOverlayCovering
        isOverlayCovering = isCovered

        if (isCovered && !wasCovered) {
            captureCurrentViewport(editorKey)
        } else if (!isCovered && wasCovered) {
            if (!isCurrentlyCovered && !isAppPaused) {
                requestCoordinatedRestoration(editorKey)
            }
        }
    }

    /**
     * Coordinates Activity lifecycle events.
     */
    fun onActivityLifecycleEvent(event: Lifecycle.Event) {
        val currentKey = activeEditorKey ?: return
        when (event) {
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                isAppPaused = true
                if (!isCurrentlyCovered && !isOverlayCovering) {
                    captureCurrentViewport(currentKey)
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                if (isAppPaused) {
                    isAppPaused = false
                    if (!isCurrentlyCovered && !isOverlayCovering) {
                        requestCoordinatedRestoration(currentKey)
                    }
                }
            }
            else -> {}
        }
    }

    /**
     * Captures and stores the latest viewport snapshot for the specified session.
     */
    fun captureCurrentViewport(editorKey: Route.Editor): ViewportSnapshot? {
        val client = clientMap[editorKey]
        val snapshot = client?.captureCurrentViewport()
        if (snapshot != null) {
            sessionSnapshots[snapshot.noteId] = snapshot
            return snapshot
        }
        return null
    }

    /**
     * Directly updates the in-memory snapshot for a document. Cheap and in-memory only.
     */
    fun updateViewportSnapshot(sessionKey: Any, noteId: String, scrollD: Int, editorOffsetY: Int) {
        sessionSnapshots[noteId] = ViewportSnapshot(
            sessionKey = sessionKey,
            noteId = noteId,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY
        )
    }

    fun getSnapshot(noteId: String): ViewportSnapshot? = sessionSnapshots[noteId]

    fun hasSnapshot(noteId: String): Boolean = sessionSnapshots.containsKey(noteId)

    /**
     * Requests exactly ONE coordinated viewport restoration for this session.
     */
    fun requestCoordinatedRestoration(editorKey: Route.Editor) {
        val noteId = editorKey.noteId
        val snapshot = sessionSnapshots[noteId] ?: return
        val gen = restorationGeneration.incrementAndGet()
        activeRestorationGeneration = gen

        val client = clientMap[editorKey]
        if (client != null) {
            client.restoreViewport(snapshot) {
                // Completed
            }
        }
    }

    /**
     * Checks if the restoration generation is still valid and active.
     */
    fun isGenerationActive(generation: Long): Boolean = activeRestorationGeneration == generation

    /**
     * Checks if an editor key is currently active and uncovered.
     */
    fun isEditorActive(editorKey: Route.Editor): Boolean {
        return activeEditorKey == editorKey && !isCurrentlyCovered && !isOverlayCovering
    }

    /**
     * Invalidates a session and cancels any pending work.
     */
    fun invalidateSession(editorKey: Route.Editor) {
        restorationGeneration.incrementAndGet()
        sessionSnapshots.remove(editorKey.noteId)
        if (activeEditorKey == editorKey) {
            activeEditorKey = null
            activeNoteId = null
        }
    }

    /**
     * Invalidates a document snapshot when a note is deleted or genuinely changed.
     */
    fun invalidateNote(noteId: String) {
        restorationGeneration.incrementAndGet()
        sessionSnapshots.remove(noteId)
        if (activeNoteId == noteId) {
            activeNoteId = null
        }
    }

    /**
     * Authoritative helper to calculate logical scroll split respecting the 1-DOF invariant.
     */
    companion object {
        fun splitLogicalScroll(
            headerHeight: Int,
            totalLogicalScroll: Int,
            maxCanvasScroll: Int
        ): ScribeScrollPolicy.ReconciledScrollState {
            return ScribeScrollPolicy.reconcileScrollState(
                headerHeight = headerHeight,
                scrollD = 0,
                editorOffsetY = totalLogicalScroll,
                maxCanvasScroll = maxCanvasScroll
            )
        }
    }
}
