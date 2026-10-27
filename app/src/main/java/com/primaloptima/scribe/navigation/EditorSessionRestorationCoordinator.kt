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
 * @param sessionKey The unique navigation session key (Route.Editor)
 * @param noteId Document ID associated with this snapshot
 * @param scrollD Physical canvas displacement of the header (0..headerHeight)
 * @param editorOffsetY Internal vertical scroll offset of the Sora CodeEditor
 * @param totalLogicalScroll Canonical 1-DOF combined displacement: scrollD + editorOffsetY
 * @param generation Restoration token / generation at time of capture
 * @param timestamp Time of capture
 */
data class ViewportSnapshot(
    val sessionKey: Route.Editor,
    val noteId: String,
    val scrollD: Int,
    val editorOffsetY: Int,
    val totalLogicalScroll: Int = scrollD + editorOffsetY,
    val generation: Long = 0L,
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
     * Executes viewport restoration with explicit result callback.
     * Returns APPLIED if scroll was actually mutated, DEFERRED if view/layout not ready,
     * or CANCELLED if generation/session is invalid.
     */
    fun restoreViewport(
        snapshot: ViewportSnapshot,
        generation: Long,
        onResult: (RestorationResult) -> Unit
    ) {
        restoreViewport(snapshot, generation) {
            onResult(RestorationResult.APPLIED)
        }
    }

    /**
     * Backwards compatibility overload.
     */
    fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
        restoreViewport(snapshot, generation) { result ->
            if (result == RestorationResult.APPLIED) {
                onComplete()
            }
        }
    }

    /**
     * Overload for backwards compatibility.
     */
    fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit) {
        restoreViewport(snapshot, -1L, onComplete)
    }

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
 * Result of a viewport restoration attempt.
 */
enum class RestorationResult {
    APPLIED,
    DEFERRED,
    CANCELLED
}

/**
 * Restoration lifecycle states for a Navigation 3 editor session.
 */
enum class RestorationState {
    IDLE,
    PENDING,
    SCHEDULED,
    APPLIED,
    CANCELLED
}

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
 * 7. Enforces token/generation-based cancellation so asynchronous callbacks for older
 *    requests or swapped documents can never mutate the current viewport.
 */
class EditorSessionRestorationCoordinator {

    // Snapshots indexed by unique session identity (Route.Editor)
    private val sessionSnapshots = ConcurrentHashMap<Route.Editor, ViewportSnapshot>()
    private val clientMap = ConcurrentHashMap<Route.Editor, EditorSessionClient>()
    private val restorationGenerations = ConcurrentHashMap<Route.Editor, Long>()
    private val sessionStates = ConcurrentHashMap<Route.Editor, RestorationState>()
    private val pendingRestorationSessions = ConcurrentHashMap.newKeySet<Route.Editor>()
    private val globalGenerationCounter = AtomicLong(0)

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

        // Any screen in trailing that is NOT a list pane (Route.Book or Route.Home) covers the window or detail pane.
        val hasCoveringScreen = trailing.any { it !is Route.Book && it !is Route.Home }
        return !hasCoveringScreen
    }

    /**
     * Central observation callback invoked when the Navigation 3 backStack changes.
     */
    fun onNavigationBackStackChanged(backStack: List<NavKey>, isCompact: Boolean) {
        val editorsInStack = backStack.filterIsInstance<Route.Editor>()
        val currentEditorKeys = editorsInStack.toSet()

        // Clean up sessions that were genuinely removed/popped from backStack across all collections
        val allTracked = (sessionSnapshots.keys + sessionStates.keys + pendingRestorationSessions + clientMap.keys + restorationGenerations.keys).toSet()
        for (tracked in allTracked) {
            if (!currentEditorKeys.contains(tracked)) {
                invalidateSession(tracked)
            }
        }

        if (editorsInStack.isEmpty()) {
            activeEditorKey = null
            activeNoteId = null
            isCurrentlyCovered = false
            sessionSnapshots.clear()
            sessionStates.clear()
            pendingRestorationSessions.clear()
            restorationGenerations.clear()
            clientMap.clear()
            return
        }

        val topEditor = editorsInStack.last()
        val isVisible = isEditorVisibleInBackStack(topEditor, backStack, isCompact)

        val prevActiveKey = activeEditorKey
        val prevCovered = isCurrentlyCovered

        if (prevActiveKey != null && prevActiveKey != topEditor) {
            // Active session switched to another editor in stack.
            // If prevActiveKey is still in the backStack, it is retained underneath.
            // Do NOT discard its snapshot! Capture and suspend it.
            if (currentEditorKeys.contains(prevActiveKey)) {
                captureCurrentViewport(prevActiveKey)
                sessionStates[prevActiveKey] = RestorationState.PENDING
                pendingRestorationSessions.add(prevActiveKey)
            }

            activeEditorKey = topEditor
            activeNoteId = topEditor.noteId
            isCurrentlyCovered = !isVisible

            if (isVisible) {
                if (sessionStates[topEditor] == RestorationState.PENDING || pendingRestorationSessions.contains(topEditor)) {
                    if (!isOverlayCovering && !isAppPaused) {
                        requestCoordinatedRestoration(topEditor)
                    }
                }
            } else {
                captureCurrentViewport(topEditor)
                sessionStates[topEditor] = RestorationState.PENDING
                pendingRestorationSessions.add(topEditor)
            }
            return
        }

        activeEditorKey = topEditor
        activeNoteId = topEditor.noteId

        if (!isVisible && !prevCovered) {
            // Transition: Active -> Covered (user pushed auxiliary screen or new destination)
            isCurrentlyCovered = true
            captureCurrentViewport(topEditor)
            sessionStates[topEditor] = RestorationState.PENDING
            pendingRestorationSessions.add(topEditor)
        } else if (isVisible && prevCovered) {
            // Transition: Covered -> Active (user popped auxiliary screen and returned to editor)
            isCurrentlyCovered = false
            if (!isOverlayCovering && !isAppPaused) {
                if (sessionStates[topEditor] == RestorationState.PENDING || pendingRestorationSessions.contains(topEditor)) {
                    requestCoordinatedRestoration(topEditor)
                }
            }
        }
    }

    /**
     * Registers an active MainEditorScreen instance.
     *
     * Note: Ordinary initial registration of a client does NOT trigger restoration.
     * Restoration occurs only if this session was marked as pending restoration
     * (e.g. view reattachment after detachment while retained, or resuming).
     */
    fun registerClient(client: EditorSessionClient) {
        clientMap[client.sessionKey] = client

        // If the session has a pending restoration requirement (e.g. view recreated while retained)
        // and is currently active and uncovered, execute restoration.
        if (activeEditorKey == client.sessionKey && !isCurrentlyCovered && !isOverlayCovering && !isAppPaused) {
            if (sessionStates[client.sessionKey] == RestorationState.PENDING || pendingRestorationSessions.contains(client.sessionKey)) {
                requestCoordinatedRestoration(client.sessionKey)
            }
        }
    }

    /**
     * Unregisters an editor instance when disposed.
     * If the session is retained in the backStack and has a snapshot,
     * record that its recreated view will need reattachment restoration.
     * Only removes if the registered client is this exact client instance.
     */
    fun unregisterClient(client: EditorSessionClient) {
        val removed = clientMap.remove(client.sessionKey, client)
        if (removed && sessionSnapshots.containsKey(client.sessionKey)) {
            val currentState = sessionStates[client.sessionKey]
            if (currentState == RestorationState.SCHEDULED || currentState == RestorationState.PENDING || pendingRestorationSessions.contains(client.sessionKey)) {
                sessionStates[client.sessionKey] = RestorationState.PENDING
                pendingRestorationSessions.add(client.sessionKey)
            }
        }
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
            sessionStates[editorKey] = RestorationState.PENDING
            pendingRestorationSessions.add(editorKey)
        } else if (!isCovered && wasCovered) {
            if (!isCurrentlyCovered && !isAppPaused) {
                if (sessionStates[editorKey] == RestorationState.PENDING || pendingRestorationSessions.contains(editorKey)) {
                    requestCoordinatedRestoration(editorKey)
                }
            }
        }
    }

    /**
     * Called when an overlay is dismissed specifically because navigation to an auxiliary screen
     * (Settings, History, Shortcuts, Guide, Themes, Export Studio) has been initiated.
     *
     * This ensures:
     * 1. The live viewport snapshot is captured before navigating away.
     * 2. Pending restoration requirement is explicitly recorded for when navigation returns.
     * 3. The overlay covering flag is cleared immediately (isOverlayCovering = false).
     * 4. The coordinator marks that the editor is transitioning/covered (isCurrentlyCovered = true)
     *    so that no premature restoration is triggered against the old navigation state.
     */
    fun onOverlayDismissedForNavigation(editorKey: Route.Editor) {
        if (activeEditorKey != editorKey) return
        captureCurrentViewport(editorKey)
        sessionStates[editorKey] = RestorationState.PENDING
        pendingRestorationSessions.add(editorKey)
        isOverlayCovering = false
        isCurrentlyCovered = true
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
                    sessionStates[currentKey] = RestorationState.PENDING
                    pendingRestorationSessions.add(currentKey)
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                if (isAppPaused) {
                    isAppPaused = false
                    if (!isCurrentlyCovered && !isOverlayCovering) {
                        if (sessionStates[currentKey] == RestorationState.PENDING || pendingRestorationSessions.contains(currentKey)) {
                            requestCoordinatedRestoration(currentKey)
                        }
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
            sessionSnapshots[editorKey] = snapshot
            return snapshot
        }
        return sessionSnapshots[editorKey]
    }

    /**
     * Directly updates the in-memory snapshot for a session/document. Cheap and in-memory only.
     */
    fun updateViewportSnapshot(sessionKey: Route.Editor, noteId: String, scrollD: Int, editorOffsetY: Int) {
        sessionSnapshots[sessionKey] = ViewportSnapshot(
            sessionKey = sessionKey,
            noteId = noteId,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY
        )
    }

    fun getSnapshot(editorKey: Route.Editor): ViewportSnapshot? = sessionSnapshots[editorKey]

    fun getSnapshot(noteId: String): ViewportSnapshot? {
        val active = activeEditorKey
        if (active != null && active.noteId == noteId) {
            val s = sessionSnapshots[active]
            if (s != null) return s
        }
        return sessionSnapshots.entries.firstOrNull { it.key.noteId == noteId }?.value
    }

    fun hasSnapshot(editorKey: Route.Editor): Boolean = sessionSnapshots.containsKey(editorKey)

    fun hasSnapshot(noteId: String): Boolean = sessionSnapshots.keys.any { it.noteId == noteId }

    fun hasPendingRestoration(editorKey: Route.Editor): Boolean {
        val state = sessionStates[editorKey]
        return state == RestorationState.PENDING || state == RestorationState.SCHEDULED || pendingRestorationSessions.contains(editorKey)
    }

    /**
     * Determines whether an editor session is a returning/retained session
     * (which must restore its captured viewport snapshot unconditionally)
     * versus a genuinely fresh editor open.
     */
    fun isRetainedSession(editorKey: Route.Editor): Boolean {
        if (!sessionSnapshots.containsKey(editorKey)) return false
        val state = sessionStates[editorKey]
        return (state != null && state != RestorationState.IDLE) || pendingRestorationSessions.contains(editorKey)
    }

    fun getRestorationState(editorKey: Route.Editor): RestorationState? = sessionStates[editorKey]

    /**
     * Requests exactly ONE coordinated viewport restoration for this session.
     * Generates a new unique generation token and dispatches to the client.
     * Returns true if client accepted the request, false otherwise.
     */
    fun requestCoordinatedRestoration(editorKey: Route.Editor): Boolean {
        val snapshot = sessionSnapshots[editorKey] ?: return false
        val client = clientMap[editorKey] ?: return false

        if (sessionStates[editorKey] == RestorationState.SCHEDULED) {
            // Already scheduled; avoid duplicate concurrent dispatch
            return true
        }

        val gen = globalGenerationCounter.incrementAndGet()
        activeRestorationGeneration = gen
        restorationGenerations[editorKey] = gen
        sessionStates[editorKey] = RestorationState.SCHEDULED

        client.restoreViewport(snapshot, gen) {
            if (restorationGenerations[editorKey] == gen) {
                sessionStates[editorKey] = RestorationState.APPLIED
                pendingRestorationSessions.remove(editorKey)
            }
        }
        return true
    }

    /**
     * Validates whether a deferred restoration request is still valid immediately
     * before applying scroll offsets.
     */
    fun isRestorationValid(sessionKey: Route.Editor, noteId: String, generation: Long): Boolean {
        if (activeEditorKey != sessionKey) return false
        if (activeNoteId != noteId) return false
        if (sessionKey.noteId != noteId) return false
        if (isCurrentlyCovered || isOverlayCovering || isAppPaused) return false
        if (!sessionSnapshots.containsKey(sessionKey)) return false
        if (generation >= 0) {
            val currentGen = restorationGenerations[sessionKey]
            if (currentGen == null || currentGen != generation) return false
        }
        return true
    }

    /**
     * Checks if the restoration generation is still valid and active.
     */
    fun isGenerationActive(generation: Long): Boolean = activeRestorationGeneration == generation

    /**
     * Checks if an editor key is currently active and uncovered.
     */
    fun isEditorActive(editorKey: Route.Editor): Boolean {
        return activeEditorKey == editorKey && !isCurrentlyCovered && !isOverlayCovering && !isAppPaused
    }

    /**
     * Invalidates a session and cancels any pending work.
     */
    fun invalidateSession(editorKey: Route.Editor) {
        globalGenerationCounter.incrementAndGet()
        restorationGenerations.remove(editorKey)
        sessionSnapshots.remove(editorKey)
        sessionStates.remove(editorKey)
        pendingRestorationSessions.remove(editorKey)
        clientMap.remove(editorKey)
        if (activeEditorKey == editorKey) {
            activeEditorKey = null
            activeNoteId = null
        }
    }

    /**
     * Invalidates a document snapshot when a note is deleted or genuinely changed.
     */
    fun invalidateNote(noteId: String) {
        globalGenerationCounter.incrementAndGet()
        val toRemove = sessionSnapshots.keys.filter { it.noteId == noteId }
        for (key in toRemove) {
            invalidateSession(key)
        }
        if (activeNoteId == noteId) {
            activeNoteId = null
            activeEditorKey = null
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
