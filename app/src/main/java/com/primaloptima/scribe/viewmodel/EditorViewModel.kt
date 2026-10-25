package com.primaloptima.scribe.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.encodeAppThemes
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.NoteMetadata
import com.primaloptima.scribe.data.toMetadata
import com.primaloptima.scribe.data.NoteVersion
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.DocumentOpenTrace
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.RecoveryManager
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.DocumentCursorState
import com.primaloptima.scribe.util.model.EditingTarget
import com.primaloptima.scribe.util.model.ExternalRoot
import com.primaloptima.scribe.util.model.FloatingWindow
import com.primaloptima.scribe.util.model.OutlineEntry
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.PaneScope
import com.primaloptima.scribe.util.model.MinimizedBy
import com.primaloptima.scribe.util.model.WorkbenchState
import com.primaloptima.scribe.util.model.WorkbenchLayout
import com.primaloptima.scribe.util.model.OutOfScopeBehavior
import com.primaloptima.scribe.util.model.specificity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.first
import com.primaloptima.scribe.engine.ProseAnalysisEngine
import com.primaloptima.scribe.engine.ProseAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DocumentLoadState {
    object Idle : DocumentLoadState()
    data class Loading(val noteId: String, val metadata: NoteMetadata? = null) : DocumentLoadState()
    data class Loaded(val note: Note) : DocumentLoadState()
    data class Error(val noteId: String, val message: String, val recoverable: Boolean = true, val localNote: Note? = null) : DocumentLoadState()
}

sealed class ExternalSyncStatus {
    object Synced : ExternalSyncStatus()
    data class SyncFailed(val uri: String, val message: String) : ExternalSyncStatus()
}

class EditorViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private companion object {
        const val KEY_LAST_WORD_COUNT    = "last_word_count"
        const val KEY_LAST_SNAPSHOT_WC   = "last_snapshot_word_count"
    }

    private val app = application as ScribeApp
    private val db = app.database
    private val dataStore = app.dataStore
    private val themeManager = app.themeManager

    // ── Generation counter & Load State ───────────────────────────────────────

    private val loadGeneration = java.util.concurrent.atomic.AtomicLong(0)
    val currentLoadGeneration: Long get() = loadGeneration.get()

    private val _loadState = MutableStateFlow<DocumentLoadState>(DocumentLoadState.Idle)
    val loadState: StateFlow<DocumentLoadState> = _loadState.asStateFlow()

    private val _externalSyncStatus = MutableStateFlow<ExternalSyncStatus>(ExternalSyncStatus.Synced)
    val externalSyncStatus: StateFlow<ExternalSyncStatus> = _externalSyncStatus.asStateFlow()

    // ── Date helpers ──────────────────────────────────────────────────────────

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun todayStr(): String = dateFmt.format(Date())

    // ── Active note ───────────────────────────────────────────────────────────

    private val _activeNote = MutableStateFlow<Note?>(null)
    val activeNote: StateFlow<Note?> = _activeNote.asStateFlow()

    private val _activeMetadata = MutableStateFlow<NoteMetadata?>(null)
    val activeMetadata: StateFlow<NoteMetadata?> = _activeMetadata.asStateFlow()

    private val _loadedWorkbenchNotes = MutableStateFlow<Map<String, Note>>(emptyMap())
    val loadedWorkbenchNotes: StateFlow<Map<String, Note>> = _loadedWorkbenchNotes.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineEntry>>(emptyList())
    val outline: StateFlow<List<OutlineEntry>> = _outline.asStateFlow()

    // ── Floating Windows & Pinned Slots ────────────────────────────────────────

    private val _floatingWindows = MutableStateFlow<List<FloatingWindow>>(emptyList())
    val floatingWindows: StateFlow<List<FloatingWindow>> = _floatingWindows.asStateFlow()

    private val _pinnedTopNotes = MutableStateFlow<List<String>>(emptyList())
    val pinnedTopNotes: StateFlow<List<String>> = _pinnedTopNotes.asStateFlow()

    private val _pinnedTopIndex = MutableStateFlow(0)
    val pinnedTopIndex: StateFlow<Int> = _pinnedTopIndex.asStateFlow()

    private val _pinnedBottomNotes = MutableStateFlow<List<String>>(emptyList())
    val pinnedBottomNotes: StateFlow<List<String>> = _pinnedBottomNotes.asStateFlow()

    private val _pinnedBottomIndex = MutableStateFlow(0)
    val pinnedBottomIndex: StateFlow<Int> = _pinnedBottomIndex.asStateFlow()

    // ── WorkbenchState ────────────────────────────────────────────────────────────

    private val _workbenchState = MutableStateFlow(WorkbenchState())
    val workbenchState: StateFlow<WorkbenchState> = _workbenchState.asStateFlow()

    private fun persistWorkbench() {
        viewModelScope.launch {
            dataStore.setWorkbenchStateJson(AppJson.encodeToString(_workbenchState.value))
        }
    }

    fun updatePane(id: String, transform: (PaneConfig) -> PaneConfig) {
        _workbenchState.value = _workbenchState.value.copy(
            panes = _workbenchState.value.panes.map { if (it.id == id) transform(it) else it }
        )
        persistWorkbench()
    }

    fun updateWorkbench(transform: (WorkbenchState) -> WorkbenchState) {
        _workbenchState.value = transform(_workbenchState.value)
        persistWorkbench()
    }

    // ── Session scope overrides ──────────────────────────────────────────────
    private val _sessionOverrides = MutableStateFlow<Set<String>>(emptySet())
    val sessionOverrides: StateFlow<Set<String>> = _sessionOverrides.asStateFlow()

    fun addSessionOverride(paneId: String) {
        _sessionOverrides.value = _sessionOverrides.value + paneId
    }

    fun removeSessionOverride(paneId: String) {
        _sessionOverrides.value = _sessionOverrides.value - paneId
    }

    fun clearSessionOverrides() {
        _sessionOverrides.value = emptySet()
    }

    fun isPaneInScope(pane: PaneConfig, note: Note?): Boolean {
        if (_sessionOverrides.value.contains(pane.id)) return true
        if (matchesScopeItem(pane.primaryScope, note)) return true
        return pane.secondaryScopes.any { matchesScopeItem(it, note) }
    }

    private fun matchesScopeItem(scope: PaneScope, note: Note?): Boolean {
        return when (scope) {
            is PaneScope.Global -> true
            is PaneScope.Book   -> note?.bookId == scope.id
            is PaneScope.Folder -> note?.folderPath == scope.id || (note != null && note.folderPath.startsWith(scope.id))
            is PaneScope.File   -> note?.id == scope.id
        }
    }

    fun resolveVisibleSections(): List<PaneConfig> {
        val state = _workbenchState.value
        val note = _activeNote.value
        val overrides = _sessionOverrides.value

        // Filter and categorize panes based on scope matching
        val candidatePanes = state.panes.filter { pane ->
            if (pane.isMinimized && pane.minimizedBy == MinimizedBy.USER) return@filter false
            val inScope = isPaneInScope(pane, note)
            if (inScope) {
                true
            } else {
                when (pane.outOfScopeBehavior) {
                    OutOfScopeBehavior.KEEP_VISIBLE -> true
                    OutOfScopeBehavior.HIDE -> false
                    OutOfScopeBehavior.MINIMIZE -> false
                    OutOfScopeBehavior.DEFAULT -> false
                }
            }
        }

        // Sort by primary scope specificity descending, preserving relative order
        val sorted = candidatePanes.sortedByDescending { it.primaryScope.specificity }
        return sorted.take(state.maxSlots)
    }

    fun setLayout(layout: WorkbenchLayout) {
        val slots = when (layout) {
            is WorkbenchLayout.Single -> 1
            is WorkbenchLayout.VerticalSplit, is WorkbenchLayout.HorizontalSplit -> 2
            is WorkbenchLayout.ThreePane -> 3
            is WorkbenchLayout.FourPane -> 4
        }
        val isHorizontal = layout is WorkbenchLayout.HorizontalSplit
        updateWorkbench {
            it.copy(
                layout = layout,
                maxSlots = slots,
                splitHorizontal = isHorizontal
            )
        }
    }

    fun updateSplitFraction(paneId: String, fraction: Float) {
        val clamped = fraction.coerceIn(0.15f, 0.85f)
        updatePane(paneId) { it.copy(splitFraction = clamped) }
    }

    fun addPane(scope: PaneScope) {
        val current = _workbenchState.value
        if (current.panes.count { !it.isMinimized } >= current.maxSlots) return
        val newPane = PaneConfig(
            id = "pane_${System.currentTimeMillis()}",
            primaryScope = scope
        )
        _workbenchState.value = current.copy(panes = current.panes + newPane)
        persistWorkbench()
    }

    fun removePane(id: String) {
        val current = _workbenchState.value
        _workbenchState.value = current.copy(panes = current.panes.filter { it.id != id })
        persistWorkbench()
    }

    fun duplicatePane(id: String) {
        val current = _workbenchState.value
        val source = current.panes.firstOrNull { it.id == id } ?: return
        val copy = source.copy(
            id = "pane_${System.currentTimeMillis()}",
            label = "${source.label} (Copy)"
        )
        _workbenchState.value = current.copy(panes = current.panes + copy)
        persistWorkbench()
    }

    fun minimizePane(id: String, by: MinimizedBy) {
        updatePane(id) { it.copy(isMinimized = true, minimizedBy = by) }
    }

    fun restorePane(id: String, replacingId: String? = null) {
        if (replacingId != null) {
            _workbenchState.value = _workbenchState.value.copy(
                panes = _workbenchState.value.panes.map { pane ->
                    when (pane.id) {
                        replacingId -> pane.copy(isMinimized = true, minimizedBy = MinimizedBy.USER)
                        id -> pane.copy(isMinimized = false, minimizedBy = null)
                        else -> pane
                    }
                }
            )
            persistWorkbench()
        } else {
            updatePane(id) { it.copy(isMinimized = false, minimizedBy = null) }
        }
    }

    fun reorderPinnedNote(paneId: String, fromIndex: Int, toIndex: Int) {
        updatePane(paneId) { pane ->
            val list = pane.pinnedNoteIds.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            pane.copy(pinnedNoteIds = list)
        }
    }

    fun unpinNote(paneId: String, noteId: String) {
        updatePane(paneId) { pane ->
            val list = pane.pinnedNoteIds.toMutableList()
            list.remove(noteId)
            val newIndex = pane.currentIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            pane.copy(pinnedNoteIds = list, currentIndex = newIndex)
        }
    }

    fun pinNoteToPane(paneId: String, noteId: String) {
        updatePane(paneId) { pane ->
            if (pane.pinnedNoteIds.contains(noteId)) return@updatePane pane
            val list = pane.pinnedNoteIds + noteId
            pane.copy(pinnedNoteIds = list, currentIndex = list.size - 1)
        }
    }

    fun pinMultipleNotesToPane(paneId: String, noteIds: List<String>) {
        updatePane(paneId) { pane ->
            val existing = pane.pinnedNoteIds.toSet()
            val toAdd = noteIds.filter { !existing.contains(it) }
            if (toAdd.isEmpty()) return@updatePane pane
            val list = pane.pinnedNoteIds + toAdd
            pane.copy(pinnedNoteIds = list, currentIndex = list.size - 1)
        }
    }

    fun ensureNotesLoaded(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = _loadedWorkbenchNotes.value
            val missing = noteIds.filter { !current.containsKey(it) }
            if (missing.isEmpty()) return@launch
            val fetched = db.noteDao().getByIds(missing)
            _loadedWorkbenchNotes.value = current + fetched.associateBy { it.id }
        }
    }

    fun loadNoteForWorkbench(noteId: String) {
        ensureNotesLoaded(setOf(noteId))
    }

    fun updateNoteContent(noteId: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = db.noteDao().getById(noteId) ?: return@launch
            if (note.externalUri != null) {
                try {
                    SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content)
                    _externalSyncStatus.value = ExternalSyncStatus.Synced
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "SAF write failed in updateNoteContent", e)
                    _externalSyncStatus.value = ExternalSyncStatus.SyncFailed(note.externalUri, e.localizedMessage ?: "Write failed")
                }
            }
            val newWords = MarkdownUtil.countWords(content)
            val now = System.currentTimeMillis()
            db.noteDao().updateContentAndWordCount(noteId, content, newWords, now)
            val updated = note.copy(content = content, wordCount = newWords, updatedAt = now)
            _loadedWorkbenchNotes.value = _loadedWorkbenchNotes.value + (noteId to updated)
            if (_activeNote.value?.id == noteId) {
                _activeNote.value = updated
                lastSavedContent = content
            }
        }
    }

    fun createNoteForPane(paneId: String, name: String, content: String, bookId: String = Note.DEFAULT_BOOK_ID) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val note = Note(
                id = id,
                name = name.ifBlank { "Untitled" },
                content = content,
                bookId = bookId,
                folderPath = "/",
                ext = "md",
                createdAt = now,
                updatedAt = now,
                wordCount = MarkdownUtil.countWords(content)
            )
            db.noteDao().insert(note)
            withContext(Dispatchers.Main) {
                pinNoteToPane(paneId, id)
            }
        }
    }

    // ── Companion panel UI prefs (persisted) ──────────────────────────────────
    val companionTabBarBottom: StateFlow<Boolean> = dataStore.companionTabBarBottomFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val companionSplitHorizontal: StateFlow<Boolean> = dataStore.companionSplitHorizontalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCompanionTabBarBottom(v: Boolean) {
        viewModelScope.launch { dataStore.setCompanionTabBarBottom(v) }
        updateWorkbench { it.copy(tabBarAtBottom = v) }
    }

    fun setCompanionSplitHorizontal(v: Boolean) {
        viewModelScope.launch { dataStore.setCompanionSplitHorizontal(v) }
        updateWorkbench { it.copy(splitHorizontal = v) }
    }

    // ── Pinned notes helper: save to DataStore ────────────────────────────────
    private fun persistPinnedTop() {
        viewModelScope.launch {
            dataStore.setPinnedTopJson(AppJson.encodeToString(_pinnedTopNotes.value))
        }
    }

    private fun persistPinnedBottom() {
        viewModelScope.launch {
            dataStore.setPinnedBottomJson(AppJson.encodeToString(_pinnedBottomNotes.value))
        }
    }

    @Deprecated("Use updatePane / pinNoteToPane instead")
    fun addPinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedTopNotes.value = list
            _pinnedTopIndex.value = list.size - 1
        } else {
            _pinnedTopIndex.value = list.indexOf(noteId)
        }
        persistPinnedTop()
    }

    @Deprecated("Use updatePane / unpinNote instead")
    fun removePinnedTop(noteId: String) {
        val list = _pinnedTopNotes.value.toMutableList()
        list.remove(noteId)
        _pinnedTopNotes.value = list
        val currIdx = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (list.isEmpty()) 0 else currIdx.coerceIn(0, list.size - 1)
        persistPinnedTop()
    }

    @Deprecated("Use updatePane instead")
    fun prevPinnedTop() {
        val list = _pinnedTopNotes.value
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    @Deprecated("Use updatePane instead")
    fun nextPinnedTop() {
        val list = _pinnedTopNotes.value
        if (list.size <= 1) return
        val curr = _pinnedTopIndex.value
        _pinnedTopIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    /** Swap top & bottom pinned slot contents. */
    @Deprecated("Use updateWorkbench instead")
    fun swapPinnedSlots() {
        val oldTop    = _pinnedTopNotes.value
        val oldBottom = _pinnedBottomNotes.value
        _pinnedTopNotes.value    = oldBottom
        _pinnedBottomNotes.value = oldTop
        _pinnedTopIndex.value    = 0
        _pinnedBottomIndex.value = 0
        persistPinnedTop()
        persistPinnedBottom()
    }

    @Deprecated("Use updatePane / pinNoteToPane instead")
    fun addPinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.toMutableList()
        if (!list.contains(noteId)) {
            list.add(noteId)
            _pinnedBottomNotes.value = list
            _pinnedBottomIndex.value = list.size - 1
        } else {
            _pinnedBottomIndex.value = list.indexOf(noteId)
        }
        persistPinnedBottom()
    }

    @Deprecated("Use updatePane / unpinNote instead")
    fun removePinnedBottom(noteId: String) {
        val list = _pinnedBottomNotes.value.toMutableList()
        list.remove(noteId)
        _pinnedBottomNotes.value = list
        val currIdx = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (list.isEmpty()) 0 else currIdx.coerceIn(0, list.size - 1)
        persistPinnedBottom()
    }

    @Deprecated("Use updatePane instead")
    fun prevPinnedBottom() {
        val list = _pinnedBottomNotes.value
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (curr > 0) curr - 1 else list.size - 1
    }

    @Deprecated("Use updatePane instead")
    fun nextPinnedBottom() {
        val list = _pinnedBottomNotes.value
        if (list.size <= 1) return
        val curr = _pinnedBottomIndex.value
        _pinnedBottomIndex.value = if (curr < list.size - 1) curr + 1 else 0
    }

    fun openFloatingWindow(noteId: String) {
        val current = _floatingWindows.value.toMutableList()
        if (current.none { it.noteId == noteId }) {
            current.add(
                FloatingWindow(
                    id = System.currentTimeMillis().toString(),
                    noteId = noteId,
                    x = 80f,
                    y = 120f,
                    width = 280,
                    height = 200,
                    zOrder = current.size
                )
            )
            _floatingWindows.value = current
        }
    }

    fun closeFloatingWindow(windowId: String) {
        _floatingWindows.value = _floatingWindows.value.filter { it.id != windowId }
    }

    fun toggleCollapseFloatingWindow(windowId: String) {
        _floatingWindows.value = _floatingWindows.value.map {
            if (it.id == windowId) it.copy(collapsed = !it.collapsed) else it
        }
    }

    fun moveFloatingWindow(windowId: String, x: Float, y: Float) {
        _floatingWindows.value = _floatingWindows.value.map {
            if (it.id == windowId) it.copy(x = x, y = y) else it
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _theme = MutableStateFlow<AppTheme?>(themeManager.activeTheme())
    val theme: StateFlow<AppTheme?> = _theme.asStateFlow()
    private var themeSaveJob: Job? = null

    fun updateActiveTheme(transform: (AppTheme) -> AppTheme) {
        val current = _theme.value ?: return
        val updated = if (current.builtIn) {
            val copy = current.copy(
                id = "custom_${System.currentTimeMillis()}",
                name = "${current.name} (Custom)",
                builtIn = false,
                schemaVersion = com.primaloptima.scribe.util.model.ThemeSchema.CURRENT_VERSION
            )
            transform(copy)
        } else {
            transform(current)
        }
        themeManager.saveCustomTheme(updated)
        themeManager.setActiveTheme(updated.id)
        _theme.value = updated
        themeSaveJob?.cancel()
        themeSaveJob = viewModelScope.launch {
            delay(400)
            dataStore.setActiveThemeId(updated.id)
            dataStore.setCustomThemesJson(AppJson.encodeAppThemes(themeManager.allCustomThemes()))
            dataStore.setEditorFontSize(updated.fontSize)
        }
    }

    // ── Word count ────────────────────────────────────────────────────────────

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _charCount = MutableStateFlow(0)
    val charCount: StateFlow<Int> = _charCount.asStateFlow()

    private val _proseAnalysis = MutableStateFlow(ProseAnalysisResult())
    val proseAnalysis: StateFlow<ProseAnalysisResult> = _proseAnalysis.asStateFlow()
    private var proseJob: Job? = null

    private val _readingTime = MutableStateFlow(0)
    val readingTime: StateFlow<Int> = _readingTime.asStateFlow()

    // ── Goal ──────────────────────────────────────────────────────────────────

    private val _goalProgress = MutableStateFlow(0f)
    val goalProgress: StateFlow<Float> = _goalProgress.asStateFlow()

    private val _goalReached = MutableStateFlow(false)
    val goalReached: StateFlow<Boolean> = _goalReached.asStateFlow()

    // Reactive DataStore-backed daily goal (kept — goal setting lives in DataStore).
    private val _dailyGoalState = dataStore.dailyGoalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 500)

    // ── Recovery ──────────────────────────────────────────────────────────────

    private val _recoveryAvailable = MutableStateFlow(false)
    val recoveryAvailable: StateFlow<Boolean> = _recoveryAvailable.asStateFlow()

    // ── SAF external folder ───────────────────────────────────────────────────

    private val _externalRoot = MutableStateFlow<ExternalRoot?>(null)
    val externalRoot: StateFlow<ExternalRoot?> = _externalRoot.asStateFlow()

    private val _externalLoading = MutableStateFlow(false)
    val externalLoading: StateFlow<Boolean> = _externalLoading.asStateFlow()

    // ── Autosave ──────────────────────────────────────────────────────────────

    private val saveMutex = Mutex()
    private var autosaveJob: Job? = null
    private var statsJob: Job? = null
    // Bug 4: debounce RecoveryManager writes (was firing on every keystroke).
    private var recoveryJob: Job? = null

    // In-memory text buffers for active session. Large text content must NEVER be
    // saved into SavedStateHandle as it exceeds the 1MB Android IPC Binder buffer limit.
    // Document content is already durably persisted in Room database & flat recovery files.
    private var lastSavedContent: String = ""
    private var pendingContent: String = ""
    private var isDirty: Boolean = false
    private var currentContentRevision: Long = 0L
    private var lastSavedRevision: Long = 0L

    // In-memory CPU caches to prevent duplicate analysis on unchanged documents
    private data class CachedStats(
        val contentHash: Int,
        val wordCount: Int,
        val charCount: Int,
        val readingTime: Int,
        val outline: List<OutlineEntry>
    )
    private val statsCache = java.util.concurrent.ConcurrentHashMap<String, CachedStats>()

    private data class CachedProse(
        val contentHash: Int,
        val result: ProseAnalysisResult
    )
    private val proseCache = java.util.concurrent.ConcurrentHashMap<String, CachedProse>()

    private var lastWordCount: Int
        get() = savedState[KEY_LAST_WORD_COUNT] ?: 0
        set(v) { savedState[KEY_LAST_WORD_COUNT] = v }

    /** Word count at the time of the last auto-snapshot — used for the min-words gate. */
    private var lastSnapshotWordCount: Int
        get() = savedState[KEY_LAST_SNAPSHOT_WC] ?: 0
        set(v) { savedState[KEY_LAST_SNAPSHOT_WC] = v }

    private val AUTOSAVE_DEBOUNCE_MS = 500L
    private val STATS_DEBOUNCE_MS    = 300L
    private val RECOVERY_DEBOUNCE_MS = 2000L

    // ── Zen / UI state ────────────────────────────────────────────────────────

    private val _zenMode = MutableStateFlow(false)
    val zenMode: StateFlow<Boolean> = _zenMode.asStateFlow()

    fun toggleZen() { _zenMode.value = !_zenMode.value }
    fun setZen(v: Boolean) { _zenMode.value = v }

    // Typewriter mode: reactive StateFlow instead of a synchronous prefs read
    val typewriterMode: StateFlow<Boolean> = dataStore.typewriterModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Restore cursor position on document open
    val restoreCursorOnOpen: StateFlow<Boolean> = dataStore.restoreCursorOnOpenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Select all scroll policy: KEEP_CURRENT_POSITION vs SCROLL_TO_SELECTION_END
    val selectAllScrollMode: StateFlow<String> = dataStore.selectAllScrollModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "KEEP_CURRENT_POSITION")

    suspend fun isRestoreCursorEnabled(): Boolean {
        return dataStore.restoreCursorOnOpenFlow.first()
    }

    fun setRestoreCursorOnOpen(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setRestoreCursorOnOpen(enabled)
        }
    }

    // Context B (in-memory, per session scroll offset captured across the entire editing session)
    data class SessionScroll(val noteId: String, val scrollD: Int, val scrollY: Int)

    private val sessionScrollMap = java.util.concurrent.ConcurrentHashMap<String, SessionScroll>()

    fun saveSessionScroll(noteId: String, scrollD: Int, scrollY: Int) {
        if (noteId.isNotEmpty()) {
            sessionScrollMap[noteId] = SessionScroll(noteId, scrollD, scrollY)
        }
    }

    fun captureSessionScroll(noteId: String, scrollD: Int, scrollY: Int) {
        saveSessionScroll(noteId, scrollD, scrollY)
    }

    fun hasSessionScroll(noteId: String): Boolean {
        return sessionScrollMap.containsKey(noteId)
    }

    fun getSessionScroll(noteId: String): SessionScroll? {
        return sessionScrollMap[noteId]
    }

    fun consumeSessionScroll(noteId: String): SessionScroll? {
        return sessionScrollMap[noteId]
    }

    fun clearSessionScroll(noteId: String? = null) {
        if (noteId != null) {
            sessionScrollMap.remove(noteId)
        } else {
            sessionScrollMap.clear()
        }
    }

    private val inMemoryCursorStates = java.util.concurrent.ConcurrentHashMap<String, DocumentCursorState>()

    fun getCursorStateInMemory(noteId: String): DocumentCursorState? = inMemoryCursorStates[noteId]

    fun saveCursorState(noteId: String, cursorState: DocumentCursorState) {
        inMemoryCursorStates[noteId] = cursorState
        val json = AppJson.encodeToString(cursorState)
        val cur = _activeNote.value
        if (cur?.id == noteId) {
            _activeNote.value = cur.copy(cursorStateJson = json)
        }
        // Save to SQLite on app-level scope so backstack pop / ViewModel clearance does not cancel the DB write
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                db.noteDao().updateCursorState(noteId, json)
            } catch (_: Throwable) {}
        }
    }

    suspend fun getOrFetchCursorState(noteId: String): DocumentCursorState? {
        inMemoryCursorStates[noteId]?.let { return it }
        val cur = _activeNote.value
        val json = if (cur?.id == noteId) cur.cursorStateJson else null
        if (json != null) {
            val parsed = try { AppJson.decodeFromString<DocumentCursorState>(json) } catch (_: Throwable) { null }
            if (parsed != null) return parsed
        }
        val fromDb = withContext(Dispatchers.IO) { db.noteDao().getCursorState(noteId) }
        if (fromDb != null) {
            val parsed = try { AppJson.decodeFromString<DocumentCursorState>(fromDb) } catch (_: Throwable) { null }
            if (parsed != null) {
                inMemoryCursorStates[noteId] = parsed
                return parsed
            }
        }
        return null
    }



    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            dataStore.activeThemeIdFlow.collectLatest { themeId ->
                _theme.value = themeManager.allThemes()
                    .firstOrNull { it.id == themeId }
                    ?: DefaultThemes.all.first()
            }
        }
        // Restore persisted pinned note IDs on startup
        viewModelScope.launch {
            dataStore.pinnedTopJsonFlow.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        val ids = AppJson.decodeFromString<List<String>>(json)
                        _pinnedTopNotes.value = ids
                        _pinnedTopIndex.value = if (ids.isEmpty()) 0 else _pinnedTopIndex.value.coerceIn(0, ids.size - 1)
                    } catch (_: Exception) { }
                }
            }
        }
        viewModelScope.launch {
            dataStore.pinnedBottomJsonFlow.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        val ids = AppJson.decodeFromString<List<String>>(json)
                        _pinnedBottomNotes.value = ids
                        _pinnedBottomIndex.value = if (ids.isEmpty()) 0 else _pinnedBottomIndex.value.coerceIn(0, ids.size - 1)
                    } catch (_: Exception) { }
                }
            }
        }
        viewModelScope.launch {
            dataStore.workbenchStateJsonFlow.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    try {
                        _workbenchState.value = AppJson.decodeFromString<WorkbenchState>(json)
                    } catch (_: Exception) { }
                } else {
                    // One-time migration from legacy keys
                    val topJson = dataStore.pinnedTopJsonFlow.first()
                    val bottomJson = dataStore.pinnedBottomJsonFlow.first()
                    val topNotes = if (!topJson.isNullOrBlank()) {
                        try { AppJson.decodeFromString<List<String>>(topJson) } catch (_: Exception) { emptyList() }
                    } else emptyList()
                    val bottomNotes = if (!bottomJson.isNullOrBlank()) {
                        try { AppJson.decodeFromString<List<String>>(bottomJson) } catch (_: Exception) { emptyList() }
                    } else emptyList()

                    val panes = mutableListOf<PaneConfig>()
                    if (topNotes.isNotEmpty()) {
                        panes.add(
                            PaneConfig(
                                id = "pane_top_migrated",
                                label = "Top Slot",
                                pinnedNoteIds = topNotes,
                                primaryScope = PaneScope.Global
                            )
                        )
                    }
                    if (bottomNotes.isNotEmpty()) {
                        panes.add(
                            PaneConfig(
                                id = "pane_bottom_migrated",
                                label = "Bottom Slot",
                                pinnedNoteIds = bottomNotes,
                                primaryScope = PaneScope.Global
                            )
                        )
                    }
                    if (panes.isEmpty()) {
                        panes.add(PaneConfig(id = "pane_default", label = "Section"))
                    }
                    val migrated = WorkbenchState(
                        panes = panes,
                        tabBarAtBottom = dataStore.companionTabBarBottomFlow.first(),
                        splitHorizontal = dataStore.companionSplitHorizontalFlow.first()
                    )
                    _workbenchState.value = migrated
                    persistWorkbench()
                }
            }
        }
        loadExternalRoot()
    }

    // ── Note loading ──────────────────────────────────────────────────────────

    /** The currently in-flight loadNote Job; cancelled by clearActiveNote() or new load request. */
    private var loadNoteJob: Job? = null

    /**
     * Preloads note metadata synchronously into memory so that the first composition
     * frame in MainEditorScreen has title, header, and book/folder information immediately,
     * while correctly identifying the state as Loading until the full body is loaded.
     */
    fun preloadNote(metadata: NoteMetadata) {
        if (_activeMetadata.value?.id == metadata.id && _loadState.value !is DocumentLoadState.Idle) return
        _loadState.value = DocumentLoadState.Loading(metadata.id, metadata)
        _activeMetadata.value = metadata
        lastWordCount = metadata.wordCount
        _wordCount.value = metadata.wordCount
        viewModelScope.launch { dataStore.setActiveNoteId(metadata.id) }
    }

    /**
     * Preloads an already fully loaded Note entity into memory.
     */
    fun preloadNote(note: Note) {
        if (_activeNote.value?.id == note.id && _loadState.value is DocumentLoadState.Loaded) return
        _loadState.value = DocumentLoadState.Loaded(note)
        _activeMetadata.value = note.toMetadata()
        _activeNote.value = note
        lastSavedContent = note.content
        pendingContent = note.content
        isDirty = false
        lastWordCount = note.wordCount
        _wordCount.value = note.wordCount
        lastSnapshotWordCount = lastWordCount
        scheduleAsyncStatsAndAnalysis(note.id, note.content, loadGeneration.get())
        viewModelScope.launch { dataStore.setActiveNoteId(note.id) }
    }

    fun loadNote(
        noteId: String,
        preloadedMetadata: NoteMetadata? = null,
        preloadedNote: Note? = null,
        outgoingText: String? = null
    ) {
        val currentLoaded = (_loadState.value as? DocumentLoadState.Loaded)?.note
        if (currentLoaded != null && currentLoaded.id == noteId && _activeNote.value?.id == noteId) {
            return
        }

        val requestId = loadGeneration.incrementAndGet()
        DocumentOpenTrace.start(noteId)

        // Capture outgoing note state
        val prevNote = _activeNote.value
        if (outgoingText != null && prevNote != null && outgoingText != pendingContent) {
            pendingContent = outgoingText
            isDirty = true
            currentContentRevision++
        }
        val prevDirty = isDirty
        val prevPending = pendingContent
        val prevRev = currentContentRevision

        if (_activeNote.value?.id != noteId) {
            sessionScrollMap.remove(noteId)
        }

        // Set loading state immediately so UI does not show previous document's contents
        val meta = preloadedMetadata ?: preloadedNote?.toMetadata()
        if (meta != null && meta.id == noteId) {
            preloadNote(meta)
        } else {
            _loadState.value = DocumentLoadState.Loading(noteId, null)
            _activeMetadata.value = null
        }
        _activeNote.value = null

        // Cancel obsolete background tasks
        loadNoteJob?.cancel()
        autosaveJob?.cancel()
        statsJob?.cancel()
        proseJob?.cancel()
        recoveryJob?.cancel()

        loadNoteJob = viewModelScope.launch {
            // Asynchronously wait for outgoing document local persistence to succeed before loading new document
            if (prevNote != null && prevNote.id != noteId && (prevDirty || prevPending != lastSavedContent)) {
                val persisted = withContext(Dispatchers.IO) {
                    saveMutex.withLock {
                        saveContentInternal(prevNote, prevPending, prevRev)
                    }
                }
                if (!persisted) {
                    val recovered = withContext(Dispatchers.IO) {
                        try {
                            RecoveryManager.get(getApplication(), prevNote.id) == prevPending
                        } catch (_: Exception) { false }
                    }
                    if (!recovered) {
                        _loadState.value = DocumentLoadState.Error(
                            noteId = noteId,
                            message = "Failed to save previous document changes. Please try again.",
                            recoverable = true
                        )
                        return@launch
                    }
                }
            }

            if (loadGeneration.get() != requestId || !coroutineContext.isActive) return@launch

            DocumentOpenTrace.mark(noteId, "metadata_lookup")
            val noteFromDb = withContext(Dispatchers.IO) { db.noteDao().getById(noteId) }
            DocumentOpenTrace.mark(noteId, "full_content_db_retrieval")

            if (loadGeneration.get() != requestId || !coroutineContext.isActive) return@launch

            val rawNote = noteFromDb ?: (if (preloadedNote != null && preloadedNote.id == noteId) preloadedNote else null)
            if (rawNote == null) {
                _loadState.value = DocumentLoadState.Error(
                    noteId = noteId,
                    message = "Document could not be found",
                    recoverable = false
                )
                return@launch
            }

            val inMem = inMemoryCursorStates[noteId]
            val noteWithCursor = if (inMem != null) {
                rawNote.copy(cursorStateJson = AppJson.encodeToString(inMem))
            } else rawNote

            // 4. SAF external file retrieval with explicit error protection
            val loadedNote = if (noteWithCursor.externalUri != null && !noteWithCursor.loaded) {
                DocumentOpenTrace.mark(noteId, "external_file_retrieval")
                try {
                    val content = SAFHelper.readFile(getApplication(), Uri.parse(noteWithCursor.externalUri))
                    val updated = noteWithCursor.copy(
                        content = content,
                        loaded = true,
                        wordCount = MarkdownUtil.countWords(content),
                        updatedAt = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.IO) { db.noteDao().update(updated) }
                    _externalSyncStatus.value = ExternalSyncStatus.Synced
                    updated
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "External file read failed for $noteId", e)
                    if (noteWithCursor.content.isEmpty()) {
                        // Failed to read external file and no local content exists
                        if (loadGeneration.get() == requestId && coroutineContext.isActive) {
                            _loadState.value = DocumentLoadState.Error(
                                noteId = noteId,
                                message = "Cannot read external file: ${e.localizedMessage ?: "File inaccessible"}",
                                recoverable = true,
                                localNote = noteWithCursor
                            )
                        }
                        return@launch
                    } else {
                        // A valid local copy already exists in the database
                        _externalSyncStatus.value = ExternalSyncStatus.SyncFailed(
                            noteWithCursor.externalUri,
                            e.localizedMessage ?: "External file currently inaccessible; displaying local cached copy"
                        )
                        noteWithCursor
                    }
                }
            } else noteWithCursor

            if (loadGeneration.get() != requestId || !coroutineContext.isActive) return@launch

            DocumentOpenTrace.mark(noteId, "content_preparation")
            _activeMetadata.value = loadedNote.toMetadata()
            _activeNote.value = loadedNote
            _loadState.value = DocumentLoadState.Loaded(loadedNote)
            lastSavedContent = loadedNote.content
            pendingContent = loadedNote.content
            isDirty = false
            currentContentRevision = 0L
            lastSavedRevision = 0L
            lastWordCount = loadedNote.wordCount
            _wordCount.value = loadedNote.wordCount
            lastSnapshotWordCount = lastWordCount

            checkRecovery(loadedNote, requestId)
            dataStore.setActiveNoteId(loadedNote.id)

            // 5. Asynchronous background calculations (off main thread, cached)
            scheduleAsyncStatsAndAnalysis(loadedNote.id, loadedNote.content, requestId)
        }
    }

    private fun scheduleAsyncStatsAndAnalysis(noteId: String, content: String, generation: Long) {
        val contentHash = content.hashCode()

        // Stats cache check
        val cachedS = statsCache[noteId]
        if (cachedS != null && cachedS.contentHash == contentHash) {
            _wordCount.value = cachedS.wordCount
            _charCount.value = cachedS.charCount
            _readingTime.value = cachedS.readingTime
            _outline.value = cachedS.outline
        } else {
            statsJob?.cancel()
            statsJob = viewModelScope.launch(Dispatchers.Default) {
                val wc = MarkdownUtil.countWords(content)
                val cc = MarkdownUtil.countChars(content)
                val rt = MarkdownUtil.readingTimeMinutes(content)
                val outline = MarkdownUtil.extractOutline(content)
                statsCache[noteId] = CachedStats(contentHash, wc, cc, rt, outline)
                withContext(Dispatchers.Main) {
                    if (loadGeneration.get() == generation && _activeNote.value?.id == noteId) {
                        _wordCount.value = wc
                        _charCount.value = cc
                        _readingTime.value = rt
                        _outline.value = outline
                    }
                }
            }
        }

        // Prose cache check
        val cachedP = proseCache[noteId]
        if (cachedP != null && cachedP.contentHash == contentHash) {
            _proseAnalysis.value = cachedP.result
        } else {
            proseJob?.cancel()
            proseJob = viewModelScope.launch(Dispatchers.Default) {
                val result = ProseAnalysisEngine.analyze(content)
                proseCache[noteId] = CachedProse(contentHash, result)
                withContext(Dispatchers.Main) {
                    if (loadGeneration.get() == generation && _activeNote.value?.id == noteId) {
                        _proseAnalysis.value = result
                        DocumentOpenTrace.mark(noteId, "background_stats_and_prose")
                        DocumentOpenTrace.finish(noteId, "words=${_wordCount.value}")
                    }
                }
            }
        }
    }

    fun clearActiveNote() {
        val note = _activeNote.value
        val dirty = isDirty
        val contentToSave = pendingContent
        val revToSave = currentContentRevision

        // If dirty, persist asynchronously on IO
        if (note != null && (dirty || contentToSave != lastSavedContent)) {
            viewModelScope.launch(Dispatchers.IO) {
                saveMutex.withLock {
                    saveContentInternal(note, contentToSave, revToSave)
                }
            }
        }

        _activeNote.value?.id?.let {
            sessionScrollMap.remove(it)
        }
        loadGeneration.incrementAndGet() // invalidate any pending load
        loadNoteJob?.cancel()
        loadNoteJob = null
        autosaveJob?.cancel()
        autosaveJob = null
        statsJob?.cancel()
        statsJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        _activeNote.value = null
        _activeMetadata.value = null
        _loadState.value = DocumentLoadState.Idle
        _outline.value = emptyList()
        _wordCount.value = 0
        _charCount.value = 0
        _readingTime.value = 0
        _proseAnalysis.value = ProseAnalysisResult()
        _goalProgress.value = 0f
        _recoveryAvailable.value = false
        lastSavedContent = ""
        pendingContent = ""
        isDirty = false
        currentContentRevision = 0L
        lastSavedRevision = 0L
        lastWordCount = 0
        lastSnapshotWordCount = 0
        viewModelScope.launch { dataStore.setActiveNoteId(null) }
    }

    /**
     * Suspendable save-and-leave: guarantees local persistence finishes before callback.
     */
    fun saveAndLeave(currentText: String? = null, onSaved: () -> Unit) {
        if (currentText != null && currentText != pendingContent) {
            onContentChanged(currentText)
        }
        val note = _activeNote.value
        val dirty = isDirty
        val content = pendingContent
        val rev = currentContentRevision
        if (note != null && (dirty || content != lastSavedContent)) {
            viewModelScope.launch(Dispatchers.IO) {
                val success = saveMutex.withLock {
                    saveContentInternal(note, content, rev)
                }
                val verifiedInRecovery = if (!success) {
                    try {
                        RecoveryManager.get(getApplication(), note.id) == content
                    } catch (_: Exception) { false }
                } else false

                withContext(Dispatchers.Main) {
                    if (success || verifiedInRecovery) {
                        clearActiveNote()
                        onSaved()
                    } else {
                        android.util.Log.e("EditorViewModel", "Cannot leave note: Room save and recovery verification both failed.")
                    }
                }
            }
        } else {
            clearActiveNote()
            onSaved()
        }
    }

    fun retryExternalSync() {
        val note = _activeNote.value ?: return
        val extUri = note.externalUri ?: return
        val content = pendingContent
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SAFHelper.writeFile(getApplication(), Uri.parse(extUri), content)
                _externalSyncStatus.value = ExternalSyncStatus.Synced
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "SAF retry write failed", e)
                _externalSyncStatus.value = ExternalSyncStatus.SyncFailed(extUri, e.localizedMessage ?: "Write failed")
            }
        }
    }

// ── Content change ────────────────────────────────────────────────────────

    /**
     * Requirement 3: Suppress Post-Indent Analysis Cascade.
     * Silently updates the note content in memory and writes to database in background
     * without triggering stats calculation (word count, reading time, outline) or prose analysis.
     */
    fun onIndentContentUpdated(content: String) {
        pendingContent = content
        lastSavedContent = content
        isDirty = false
        val note = _activeNote.value ?: return
        _activeNote.value = note.copy(content = content)
        viewModelScope.launch(Dispatchers.IO) {
            if (note.externalUri != null) {
                try {
                    SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content)
                    _externalSyncStatus.value = ExternalSyncStatus.Synced
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "SAF write failed in onIndentContentUpdated", e)
                    _externalSyncStatus.value = ExternalSyncStatus.SyncFailed(note.externalUri, e.localizedMessage ?: "Write failed")
                }
            }
            db.noteDao().updateContent(note.id, content, System.currentTimeMillis())
        }
    }

    fun onContentChanged(content: String, targetNoteId: String? = null) {
        val note = _activeNote.value ?: return
        if (targetNoteId != null && targetNoteId != note.id) return
        if (content == lastSavedContent && !isDirty) return

        pendingContent = content
        isDirty = true
        currentContentRevision++
        val rev = currentContentRevision

        // 1. Cancel and reschedule autosave (debounced at 500ms)
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            saveContent(content, rev)
        }

        // 2. Stats update — debounced separately on Default dispatcher (CPU work off main thread)
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            delay(STATS_DEBOUNCE_MS)
            val currentGen = loadGeneration.get()
            val wc = withContext(Dispatchers.Default) { MarkdownUtil.countWords(content) }
            val cc = withContext(Dispatchers.Default) { MarkdownUtil.countChars(content) }
            val rt = withContext(Dispatchers.Default) { MarkdownUtil.readingTimeMinutes(content) }
            val outline = withContext(Dispatchers.Default) { MarkdownUtil.extractOutline(content) }
            statsCache[note.id] = CachedStats(content.hashCode(), wc, cc, rt, outline)
            withContext(Dispatchers.Main) {
                if (loadGeneration.get() == currentGen && _activeNote.value?.id == note.id) {
                    _wordCount.value = wc
                    _charCount.value = cc
                    _readingTime.value = rt
                    _outline.value = outline
                }
            }
        }

        // 3. Prose analysis — debounced at 700ms on Default dispatcher (CPU-heavy NLP)
        proseJob?.cancel()
        proseJob = viewModelScope.launch {
            delay(700)
            val currentGen = loadGeneration.get()
            val result = withContext(Dispatchers.Default) {
                ProseAnalysisEngine.analyze(content)
            }
            proseCache[note.id] = CachedProse(content.hashCode(), result)
            withContext(Dispatchers.Main) {
                if (loadGeneration.get() == currentGen && _activeNote.value?.id == note.id) {
                    _proseAnalysis.value = result
                }
            }
        }

        // 4. Recovery write — debounced at 2s
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(RECOVERY_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                RecoveryManager.set(getApplication(), note.id, content)
            }
        }
    }

    /**
     * Persists pending content immediately without waiting for debounce.
     * Guaranteed to persist intentionally emptied content.
     */
    fun flushPendingContent(): kotlinx.coroutines.Job? {
        autosaveJob?.cancel()
        autosaveJob = null
        val note = _activeNote.value
        val content = pendingContent
        val rev = currentContentRevision
        if (note != null && (isDirty || content != lastSavedContent)) {
            return viewModelScope.launch(Dispatchers.IO) {
                saveMutex.withLock {
                    saveContentInternal(note, content, rev)
                }
            }
        }
        return null
    }

    /**
     * Asynchronous suspendable flush ensuring persistence completes before proceeding.
     */
    suspend fun flushPendingContentSuspend(targetNoteId: String? = null): Boolean {
        autosaveJob?.cancel()
        autosaveJob = null
        val note = _activeNote.value ?: return true
        if (targetNoteId != null && targetNoteId != note.id) return true
        val content = pendingContent
        val rev = currentContentRevision
        if (!isDirty && content == lastSavedContent) return true

        return withContext(Dispatchers.IO) {
            saveMutex.withLock {
                saveContentInternal(note, content, rev)
            }
        }
    }

    private suspend fun saveContent(content: String, revision: Long) {
        val note = _activeNote.value ?: return
        if (content == lastSavedContent && !isDirty) return

        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                saveContentInternal(note, content, revision)
            }
        }
    }

    private suspend fun saveContentInternal(note: Note, content: String, revision: Long): Boolean {
        val newWords = withContext(Dispatchers.Default) { MarkdownUtil.countWords(content) }
        val delta = newWords - lastWordCount
        lastWordCount = newWords

        var safSuccess = true
        if (note.externalUri != null) {
            try {
                SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content)
                _externalSyncStatus.value = ExternalSyncStatus.Synced
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "SAF write failed in saveContentInternal", e)
                _externalSyncStatus.value = ExternalSyncStatus.SyncFailed(note.externalUri, e.localizedMessage ?: "Write failed")
                safSuccess = false
            }
        }

        var roomSuccess = false
        try {
            val today = todayStr()
            db.writingLogDao().saveContentAndDelta(
                noteDao    = db.noteDao(),
                noteId     = note.id,
                content    = content,
                wordCount  = newWords,
                updatedAt  = System.currentTimeMillis(),
                date       = today,
                bookId     = note.bookId,
                folderPath = note.folderPath,
                delta      = delta
            )
            roomSuccess = true
            if (delta != 0) {
                updateGoalFromDb(today)
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorViewModel", "Room save failed", e)
            roomSuccess = false
        }

        if (roomSuccess) {
            if (_activeNote.value?.id == note.id) {
                _activeNote.value = note.copy(content = content, updatedAt = System.currentTimeMillis())
            }
            lastSavedContent = content
            if (revision >= currentContentRevision) {
                isDirty = false
            }
            lastSavedRevision = revision
        } else {
            // Keep content dirty and write to RecoveryManager immediately so user data is never lost
            isDirty = true
            RecoveryManager.set(getApplication(), note.id, content)
        }
        return roomSuccess
    }

    // ── Goal (DB-backed, replaces DataStore-backed updateGoal) ────────────────

    /**
     * Reads today's total from writing_log and updates goal progress.
     * Called after every save that produced a non-zero delta.
     * Must be called from a coroutine already on IO (inside withContext(Dispatchers.IO)).
     */
    private suspend fun updateGoalFromDb(today: String) {
        val goal  = _dailyGoalState.value.toFloat()
        val words = db.writingLogDao().getTodayWords(today).toFloat()
        _goalProgress.value = if (goal > 0) (words / goal).coerceIn(0f, 1f) else 0f
        _goalReached.value  = words >= goal
    }

    /**
     * Synchronous goal update used at note-load time (no writing_log read needed —
     * today's total is not yet meaningful until the user starts editing).
     */
    private fun updateGoalSync() {
        _goalProgress.value = 0f
        _goalReached.value  = false
    }

    /**
     * Called when the writer leaves a note (DisposableEffect onDispose).
     * Saves an "auto" snapshot only if auto-history is enabled and the net
     * word change since the last snapshot meets the minimum threshold.
     */
    fun saveVersionSnapshotOnLeave(content: String) {
        val note = _activeNote.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!dataStore.getAutoHistoryEnabled()) return@launch
            val currentWords = MarkdownUtil.countWords(content)
            val wordDelta = Math.abs(currentWords - lastSnapshotWordCount)
            if (wordDelta < dataStore.getAutoHistoryMinWords()) return@launch
            db.noteVersionDao().insert(
                NoteVersion(
                    noteId = note.id,
                    content = content,
                    wordCount = currentWords,
                    timestamp = System.currentTimeMillis(),
                    type = NoteVersion.TYPE_AUTO
                )
            )
            db.noteVersionDao().trimByType(note.id, NoteVersion.TYPE_AUTO, dataStore.getAutoHistorySlots())
            lastSnapshotWordCount = currentWords
        }
    }

    /**
     * Called when the writer taps the checkpoint (save) button in the toolbar.
     * Always saves regardless of word count, bypasses the min-words gate.
     */
    fun saveManualSnapshot(content: String) {
        val note = _activeNote.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!dataStore.getManualCheckpointsEnabled()) return@launch
            val words = MarkdownUtil.countWords(content)
            db.noteVersionDao().insert(
                NoteVersion(
                    noteId = note.id,
                    content = content,
                    wordCount = words,
                    timestamp = System.currentTimeMillis(),
                    type = NoteVersion.TYPE_MANUAL
                )
            )
            db.noteVersionDao().trimByType(note.id, NoteVersion.TYPE_MANUAL, dataStore.getManualCheckpointSlots())
            lastSnapshotWordCount = words
        }
    }

    fun flushContent(content: String) {
        autosaveJob?.cancel()
        val note = _activeNote.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (content != lastSavedContent) {
                if (note.externalUri != null) {
                    try { SAFHelper.writeFile(getApplication(), Uri.parse(note.externalUri), content) }
                    catch (_: Exception) {}
                }
                val wc = MarkdownUtil.countWords(content)
                // Fix (Bug 2): compute and record the delta so words written between
                // the last autosave and app-going-to-background are not silently lost
                // from writing_log (daily goal, charts, streaks).
                val delta = wc - lastWordCount
                val today = todayStr()
                db.writingLogDao().saveContentAndDelta(
                    noteDao    = db.noteDao(),
                    noteId     = note.id,
                    content    = content,
                    wordCount  = wc,
                    updatedAt  = System.currentTimeMillis(),
                    date       = today,
                    bookId     = note.bookId,
                    folderPath = note.folderPath,
                    delta      = delta
                )
                lastWordCount = wc
                lastSavedContent = content
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /** Synchronous stats update — called immediately when a note is loaded. */
    private fun updateStatsSync(content: String) {
        _wordCount.value = MarkdownUtil.countWords(content)
        _charCount.value = MarkdownUtil.countChars(content)
        _readingTime.value = MarkdownUtil.readingTimeMinutes(content)
        _outline.value = MarkdownUtil.extractOutline(content)
        updateGoalSync()
    }

    // ── Recovery ──────────────────────────────────────────────────────────────

    private fun checkRecovery(note: Note, requestId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = RecoveryManager.get(getApplication(), note.id) ?: return@launch
            if (loadGeneration.get() == requestId && saved != note.content) {
                withContext(Dispatchers.Main) {
                    if (loadGeneration.get() == requestId && _activeNote.value?.id == note.id) {
                        _recoveryAvailable.value = true
                    }
                }
            }
        }
    }

    suspend fun getRecoveryContent(): String? = withContext(Dispatchers.IO) {
        val noteId = _activeNote.value?.id ?: return@withContext null
        RecoveryManager.get(getApplication(), noteId)
    }

    fun dismissRecovery() {
        val noteId = _activeNote.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            RecoveryManager.clear(getApplication(), noteId)
        }
        _recoveryAvailable.value = false
    }

    // ── History ───────────────────────────────────────────────────────────────

    fun restoreSnapshot(content: String) {
        val note = _activeNote.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val restoredWc = MarkdownUtil.countWords(content)
            val previousWc = lastWordCount
            withContext(Dispatchers.IO) {
                val today = todayStr()
                val delta = restoredWc - previousWc
                // Atomically update notes and write_log so both reflect the restore.
                db.writingLogDao().saveContentAndDelta(
                    noteDao    = db.noteDao(),
                    noteId     = note.id,
                    content    = content,
                    wordCount  = restoredWc,
                    updatedAt  = System.currentTimeMillis(),
                    date       = today,
                    bookId     = note.bookId,
                    folderPath = note.folderPath,
                    delta      = delta
                )
                if (delta != 0) {
                    updateGoalFromDb(today)
                }
            }
            withContext(Dispatchers.Main) {
                _activeNote.value = note.copy(content = content, updatedAt = System.currentTimeMillis())
                lastSavedContent = content
                lastWordCount = restoredWc
                _wordCount.value = restoredWc
                scheduleAsyncStatsAndAnalysis(note.id, content, loadGeneration.get())
            }
        }
    }

    // ── SAF ───────────────────────────────────────────────────────────────────

    private fun loadExternalRoot() {
        viewModelScope.launch {
            val json = dataStore.getExternalRootJson() ?: return@launch
            try {
                val ext = AppJson.decodeFromString<ExternalRoot>(json)
                _externalRoot.value = ext
            } catch (_: Exception) {}
        }
    }

    fun disconnectExternalFolder() {
        _externalRoot.value = null
        viewModelScope.launch { dataStore.setExternalRootJson(null) }
    }

    override fun onCleared() {
        super.onCleared()
        val note = _activeNote.value
        val dirty = isDirty
        val content = pendingContent
        val rev = currentContentRevision
        if (note != null && (dirty || content != lastSavedContent)) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                saveMutex.withLock {
                    saveContentInternal(note, content, rev)
                }
            }
        }
    }
}
