package com.primaloptima.scribe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.primaloptima.scribe.navigation.EditorAuxiliaryOverlayHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.metadata
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.core.layout.WindowWidthSizeClass
import com.primaloptima.scribe.navigation.EditorSessionRestorationCoordinator
import com.primaloptima.scribe.navigation.LocalEditorRestorationCoordinator
import com.primaloptima.scribe.navigation.Route
import com.primaloptima.scribe.ui.screens.BookScreen
import com.primaloptima.scribe.ui.screens.ExportStudioScreen
import com.primaloptima.scribe.ui.screens.GuideScreen
import com.primaloptima.scribe.ui.screens.HistoryScreen
import com.primaloptima.scribe.ui.screens.HomeScreen
import com.primaloptima.scribe.ui.screens.MainEditorScreen
import com.primaloptima.scribe.ui.screens.SettingsScreen
import com.primaloptima.scribe.ui.screens.SheetsScreen
import com.primaloptima.scribe.ui.screens.ShortcutsScreen
import com.primaloptima.scribe.ui.screens.ThemeEditScreen
import com.primaloptima.scribe.ui.screens.ThemeListScreen
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.BooksViewModel
import com.primaloptima.scribe.viewmodel.DashboardViewModel
import com.primaloptima.scribe.viewmodel.HomeShellViewModel
import com.primaloptima.scribe.viewmodel.NotesViewModel
import com.primaloptima.scribe.viewmodel.NoteListViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import com.primaloptima.scribe.viewmodel.StatsViewModel
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import kotlinx.coroutines.runBlocking

// CompositionLocal so HistoryScreen can access the EditorViewModel that is
// scoped to the Editor entry, without a fragile getBackStackEntry() call
// (Bug 6 fix — eliminates the IllegalArgumentException crash).
val LocalEditorViewModel = compositionLocalOf<EditorViewModel?> { null }

// Phase 1: exposes the SharedTransitionLayout scope to any composable that needs
// sharedElement/sharedBounds without threading it through every call site.
// Null outside the SharedTransitionLayout (e.g. in @Preview composables).
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

// CompositionLocal to share one ThemeViewModel between ThemeList and ThemeEdit,
// replacing the Nav2 sub-graph / getBackStackEntry(Route.ThemeFlow) pattern.
val LocalThemeViewModel = compositionLocalOf<ThemeViewModel?> { null }

class ScribeActivity : ComponentActivity() {

    // Activity-scoped ViewModels.
    // Home tab ViewModels are Activity-scoped so each tab keeps its state while
    // the Home shell remains on the navigation back stack.
    private val homeShellVm: HomeShellViewModel by viewModels()
    private val dashboardVm: DashboardViewModel by viewModels()
    private val booksVm: BooksViewModel by viewModels()
    private val notesVm: NotesViewModel by viewModels()
    private val statsVm: StatsViewModel by viewModels()
    // shortcutsVm: shared between the Editor toolbar and ShortcutsScreen.
    private val shortcutsVm: ShortcutsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // ← MUST be first

        // Read the active theme synchronously so the correct splash style is applied
        // before installSplashScreen() reads it. runBlocking is safe here because
        // it runs before setContent on a cold start, and DataStore returns from an
        // in-memory snapshot after the first emission.
        val activeThemeId = runBlocking {
            (application as ScribeApp).dataStore.getActiveThemeId()
        }
        setTheme(splashStyleFor(activeThemeId))

        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Hold splash until the DB emits its first result.
        splashScreen.setKeepOnScreenCondition { booksVm.books.value == null }

        // Fade out instead of snapping away.
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view
                .animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { provider.remove() }
                .start()
        }

        setContent {
            // ScribeComposeTheme ALREADY contains the wallpaper AsyncImage,
            // hazeSource modifier, and all CompositionLocalProviders
            // (LocalHazeState, LocalAppTheme, etc.) inside ScribeTheme.kt.
            // Do NOT add another Box + AsyncImage wrapper here.
            ScribeComposeTheme {
                ScribeNavigation()
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3AdaptiveApi::class)
    @Composable
    private fun ScribeNavigation() {
        val backStack = rememberNavBackStack(Route.Home)
        val restorationCoordinator = remember { EditorSessionRestorationCoordinator() }

        val adaptiveInfo = currentWindowAdaptiveInfo()
        val isCompact = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

        val currentBackStackList = backStack.toList()

        // Centrally observe backStack transitions and notify the restoration coordinator
        LaunchedEffect(currentBackStackList, isCompact) {
            restorationCoordinator.onNavigationBackStackChanged(currentBackStackList, isCompact)
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                restorationCoordinator.onActivityLifecycleEvent(event)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // Holds the EditorViewModel from whichever Editor entry is currently active.
        // Updated inside entry<Route.Editor> so History can receive it directly.
        // CompositionLocal alone cannot bridge separate Nav3 entries (different
        // composition scopes), so we hoist the reference here instead.
        var activeEditorVm by remember { mutableStateOf<EditorViewModel?>(null) }

        // Phase 3: two-pane strategy for tablets/foldables. On phones it is a no-op.
        val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

        // Phase 2: hazeSource centralised here so screens don't need it individually.
        val hazeState = com.primaloptima.scribe.ui.theme.LocalHazeState.current

        SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalEditorRestorationCoordinator provides restorationCoordinator
        ) {
        NavDisplay(
            backStack = backStack,
            onBack    = { backStack.removeLastOrNull() },
            // Phase 1: enables sharedElement/sharedBounds across entries.
            sharedTransitionScope = this,
            // Phase 2: DialogSceneStrategy enables future nav-level dialog routes.
            // FrostedDialog stays hand-rolled (separate window breaks Haze pipeline).
            // Phase 3: listDetailStrategy = two-pane on tablets, no-op on phones.
            sceneStrategies = remember(listDetailStrategy) {
                listOf(DialogSceneStrategy(), listDetailStrategy)
            },
            sceneDecoratorStrategies = remember(hazeState) {
                if (hazeState != null)
                    listOf(com.primaloptima.scribe.navigation.HazeSourceDecoratorStrategy<NavKey>(hazeState))
                else emptyList()
            },
            entryDecorators = listOf(
                // Must be first: wraps each entry in a SaveableStateProvider so that
                // rememberSaveable (e.g. loadedNoteId in MainEditorScreen) survives
                // back-stack changes. Without this, rememberSaveable resets on every
                // navigation even when the ViewModel is retained.
                // Import: androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
                rememberSaveableStateHolderNavEntryDecorator(),
                // Scopes each ViewModel to its NavEntry lifetime — cleared on pop.
                // rememberSceneSetupNavEntryDecorator() is built into NavDisplay
                // as of Nav3 1.1.x — do NOT add it manually (duplicate/crash risk).
                rememberViewModelStoreNavEntryDecorator()
            ),
            // Global default: fade-through.
            // Old screen fades out quickly (90ms), new screen fades in after a brief
            // pause (delayMillis = 90ms), giving the feel of turning a page rather than
            // two screens crossing. Book uses shared-element bounds instead; Editor uses
            // horizontal slide on pop only; modal screens override with vertical slide.
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                ) togetherWith
                    fadeOut(animationSpec = tween(90, easing = LinearEasing))
            },
            popTransitionSpec = {
                fadeIn(
                    animationSpec = tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                ) togetherWith
                    fadeOut(animationSpec = tween(90, easing = LinearEasing))
            },
            entryProvider = entryProvider {

                // ── Home ─────────────────────────────────────────────────────
                entry<Route.Home> {
                    HomeScreen(
                        shellVm            = homeShellVm,
                        dashboardVm        = dashboardVm,
                        booksVm            = booksVm,
                        notesVm            = notesVm,
                        statsVm            = statsVm,
                        onOpenBook         = { book ->
                            backStack.add(Route.Book(book.id))
                        },
                        onOpenNote         = { noteId, bookId ->
                            backStack.add(Route.Editor(bookId, noteId))
                        },
                        onOpenSettings     = {
                            if (backStack.lastOrNull() !is Route.Settings)
                                backStack.add(Route.Settings)
                        },
                        onOpenSheets       = {
                            if (backStack.lastOrNull() !is Route.Sheets)
                                backStack.add(Route.Sheets())
                        },
                        onOpenSheetsCreate = {
                            backStack.add(Route.Sheets(openCreate = true))
                        },
                        onOpenThemes       = {
                            if (backStack.lastOrNull() !is Route.ThemeList)
                                backStack.add(Route.ThemeList)
                        }
                    )
                }

                // ── Book ─────────────────────────────────────────────────────
                // Push: lightweight fade — the sharedElement (cover) and sharedBounds (title)
                // already wired in HomeScreen/BookScreen are the visual hero. Running a heavy
                // slide alongside shared elements causes jank; fade costs almost nothing and
                // lets the shared elements breathe.
                // Pop: slide the BookScreen *down* off the screen. Sliding the departing screen
                // is cheap (it's already composed); the Home screen beneath it never rebuilds.
                entry<Route.Book>(
                    metadata = ListDetailSceneStrategy.listPane() + metadata {
                        put(NavDisplay.TransitionKey) {
                            // Fade in: the sharedElement (cover) + sharedBounds (title) are the
                            // visual hero. A heavy slide alongside them causes dropped frames.
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(90, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            // Slide the BookScreen down on back. Home is already composed
                            // under it so this costs nothing extra.
                            fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(150, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(150, easing = LinearEasing)))
                        }
                    }
                ) { key ->
                    val bookVm: BookViewModel = viewModel()
                    androidx.compose.runtime.remember(key.bookId) { bookVm.init(key.bookId); true }
                    BookScreen(
                        vm          = bookVm,
                        dashboardVm = dashboardVm,
                        onBack      = { backStack.removeLastOrNull() },
                        onOpenNote  = { noteId ->
                            backStack.add(Route.Editor(key.bookId, noteId))
                        }
                    )
                }

                // ── Editor ───────────────────────────────────────────────────
                // Push: fade only. The Editor is the heaviest screen (ScribeEditText +
                // note content). Composing it while sliding causes dropped frames on notes
                // with large text — this is what makes it feel like a snap. Fade is nearly
                // free on the GPU even while composition is in progress.
                // Pop: slide the Editor screen out to the right. BookScreen beneath it is
                // already composed, so the slide costs nothing extra and gives a clear
                // spatial "going back" signal.
                entry<Route.Editor>(
                    metadata = ListDetailSceneStrategy.detailPane() + metadata {
                        put(NavDisplay.TransitionKey) {
                            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(90, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it }
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                                slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it }
                        }
                    }
                ) { key ->
                    val editorVm:   EditorViewModel   = viewModel()
                    val bookVm:     BookViewModel     = viewModel()
                    val noteListVm: NoteListViewModel = viewModel()
                    remember(key.bookId) { bookVm.init(key.bookId); true }
                    // Preload note metadata synchronously into editorVm so frame 0 has title immediately
                    remember(key.noteId) {
                        val cached = bookVm.notes.value.firstOrNull { it.id == key.noteId }
                            ?: noteListVm.notes.value.firstOrNull { it.id == key.noteId }
                        if (cached != null) {
                            editorVm.preloadNote(cached)
                        }
                        true
                    }
                    // Keep the hoisted reference current so History can receive it.
                    activeEditorVm = editorVm

                    // Editor-specific auxiliary overlay navigation stack
                    val auxiliaryBackStack = remember { mutableStateListOf<Route>() }
                    val isAuxiliaryActive = auxiliaryBackStack.isNotEmpty()

                    val isEditorActive = remember(currentBackStackList, isCompact, key, isAuxiliaryActive) {
                        !isAuxiliaryActive && restorationCoordinator.isEditorVisibleInBackStack(key, currentBackStackList, isCompact)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalEditorViewModel provides editorVm) {
                            MainEditorScreen(
                                editorVm        = editorVm,
                                bookVm          = bookVm,
                                noteListVm      = noteListVm,
                                shortcutsVm     = shortcutsVm,
                                initialNoteId   = key.noteId,
                                isScreenActive  = isEditorActive,
                                sessionKey      = key,
                                onBack          = {
                                    activeEditorVm?.clearSessionScroll(key.noteId)
                                    restorationCoordinator.invalidateSession(key)
                                    editorVm.saveAndLeave {
                                        backStack.removeLastOrNull()
                                    }
                                },
                                onOpenHistory   = { targetNoteId ->
                                    editorVm.flushPendingContent()
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.History)
                                        auxiliaryBackStack.add(Route.History(targetNoteId))
                                },
                                onOpenShortcuts = {
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.Shortcuts)
                                        auxiliaryBackStack.add(Route.Shortcuts)
                                },
                                onOpenGuide     = {
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.Guide)
                                        auxiliaryBackStack.add(Route.Guide)
                                },
                                onOpenThemes    = {
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.ThemeList)
                                        auxiliaryBackStack.add(Route.ThemeList)
                                },
                                onOpenSettings  = {
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.Settings)
                                        auxiliaryBackStack.add(Route.Settings)
                                },
                                onOpenSheets    = {
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.Sheets)
                                        auxiliaryBackStack.add(Route.Sheets())
                                },
                                onOpenExportStudio = { format ->
                                    val currentNote = editorVm.activeNote.value
                                    restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = true)
                                    if (auxiliaryBackStack.lastOrNull() !is Route.ExportStudio) {
                                        auxiliaryBackStack.add(
                                            Route.ExportStudio(
                                                bookId = key.bookId,
                                                noteId = currentNote?.id ?: key.noteId,
                                                initialFormat = format
                                            )
                                        )
                                    }
                                }
                            )
                        }

                        EditorAuxiliaryOverlayHost(
                            auxiliaryBackStack = auxiliaryBackStack,
                            editorVm           = editorVm,
                            shortcutsVm        = shortcutsVm,
                            defaultBookId      = key.bookId,
                            defaultNoteId      = key.noteId,
                            onDismissOverlay   = {
                                restorationCoordinator.onAuxiliaryOverlayVisibilityChanged(key, isCovered = false)
                            }
                        )
                    }
                }

                // ── Settings ─────────────────────────────────────────────────
                // Slide up from bottom — communicates "floating above" rather than
                // "replacing". These are auxiliary screens, not peer destinations.
                entry<Route.Settings>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) {
                    SettingsScreen(
                        onBack       = { backStack.removeLastOrNull() },
                        onOpenThemes = {
                            if (backStack.lastOrNull() !is Route.ThemeList)
                                backStack.add(Route.ThemeList)
                        }
                    )
                }

                // ── Theme List ────────────────────────────────────────────────
                // ThemeViewModel is scoped to this entry and provided via
                // CompositionLocal so ThemeEdit can share the exact same instance
                // without any back-stack lookup (replaces Nav2 sub-graph pattern).
                entry<Route.ThemeList> {
                    val themeVm: ThemeViewModel = viewModel()
                    CompositionLocalProvider(LocalThemeViewModel provides themeVm) {
                        ThemeListScreen(
                            vm          = themeVm,
                            onBack      = { backStack.removeLastOrNull() },
                            onEditTheme = { themeId ->
                                backStack.add(Route.ThemeEditArgs(themeId))
                            }
                        )
                    }
                }

                // ── Theme Edit ────────────────────────────────────────────────
                // Retrieves the ThemeViewModel provided by the ThemeList entry above.
                // Falls back to a fresh viewModel() only as a safety net for the
                // edge case where ThemeEdit is somehow reached without ThemeList in
                // the composition tree.
                entry<Route.ThemeEditArgs> { key ->
                    val themeVm = LocalThemeViewModel.current ?: viewModel()
                    ThemeEditScreen(
                        themeId = key.themeId,
                        vm      = themeVm,
                        onBack  = { backStack.removeLastOrNull() }
                    )
                }

                // ── Sheets ────────────────────────────────────────────────────
                // Slide up — auxiliary screen floating above the current context.
                entry<Route.Sheets>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) { key ->
                    val sheetsVm = viewModel<com.primaloptima.scribe.viewmodel.SheetsViewModel>()
                    SheetsScreen(
                        vm                 = sheetsVm,
                        onBack             = { backStack.removeLastOrNull() },
                        openCreateOnLaunch = key.openCreate
                    )
                }

                // ── History ───────────────────────────────────────────────────
                // Uses the hoisted activeEditorVm reference set inside entry<Route.Editor>.
                // CompositionLocal cannot cross Nav3 entry boundaries (separate composition
                // scopes), so we capture the ViewModel at the ScribeNavigation level instead.
                // If History is reached without an active Editor (e.g. unusual process
                // restoration), we pop back rather than crashing.
                // Slide up — auxiliary screen floating above the editor.
                entry<Route.History>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) { key ->
                    val editorVm = activeEditorVm
                    if (editorVm == null) {
                        LaunchedEffect(Unit) { backStack.removeLastOrNull() }
                        return@entry
                    }
                    HistoryScreen(
                        targetNoteId = key.noteId,
                        editorVm     = editorVm,
                        onBack       = { backStack.removeLastOrNull() }
                    )
                }

                // ── Guide ─────────────────────────────────────────────────────
                // Slide up — auxiliary screen floating above the editor.
                entry<Route.Guide>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) {
                    GuideScreen(onBack = { backStack.removeLastOrNull() })
                }

                // ── Shortcuts ──────────────────────────────────────────────────
                // Slide up — auxiliary screen floating above the editor.
                entry<Route.Shortcuts>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) {
                    ShortcutsScreen(
                        vm     = shortcutsVm,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                // ── Export Studio ──────────────────────────────────────────────
                // Full-screen publishing workspace floating above the editor.
                entry<Route.ExportStudio>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it }
                                + fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                        put(NavDisplay.PredictivePopTransitionKey) { _ ->
                            fadeIn(tween(150, easing = FastOutSlowInEasing)) togetherWith
                                (slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it }
                                    + fadeOut(tween(200, easing = LinearEasing)))
                        }
                    }
                ) { key ->
                    val editorVm = activeEditorVm
                    val activeNote = editorVm?.activeNote?.value
                    val context = LocalContext.current
                    val app = context.applicationContext as ScribeApp
                    val fullNote by produceState<com.primaloptima.scribe.data.Note?>(
                        initialValue = activeNote?.takeIf { it.id == key.noteId },
                        key1 = key.noteId
                    ) {
                        if (value == null) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                app.database.noteDao().getById(key.noteId)
                            }
                        }
                    }

                    val note = fullNote
                    if (note == null) {
                        return@entry
                    }

                    ExportStudioScreen(
                        note = note,
                        bookId = key.bookId,
                        initialFormat = key.initialFormat ?: "pdf",
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                // ── Crash ─────────────────────────────────────────────────────
                // CrashScreen is launched via a separate CrashActivity in the
                // manifest, not via navigation — it stays as-is and needs no entry here.
            }
        )
        } // end CompositionLocalProvider(LocalSharedTransitionScope)
        } // end SharedTransitionLayout
    }

    // Deep links: Scribe currently declares no URL scheme deep links in
    // AndroidManifest.xml (only MAIN/LAUNCHER intent filter), so onNewIntent
    // only handles task re-entry which the system manages automatically.
    // The Nav2 navController.handleDeepLink(intent) call is removed — Nav3
    // does not use NavController and has no equivalent; the backStack is
    // initialised at composition time and handles cold-start state correctly.
    // If you add URL deep links in the future, parse the intent here and push
    // the appropriate Route onto backStack.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    /**
     * Maps a saved theme ID to the matching pre-defined splash style.
     * Each style lives in res/values/scribe_splash_dynamic_theme.xml (API < 31)
     * and res/values-v31/scribe_splash_dynamic_theme.xml (API 31+ adds icon color).
     */
    private fun splashStyleFor(themeId: String): Int = when (themeId) {
        "obsidian"   -> R.style.Theme_Scribe_Splash_Obsidian
        "midnight"   -> R.style.Theme_Scribe_Splash_Midnight
        "focus"      -> R.style.Theme_Scribe_Splash_Focus
        "paper"      -> R.style.Theme_Scribe_Splash_Paper
        "sepia"      -> R.style.Theme_Scribe_Splash_Sepia
        "typewriter" -> R.style.Theme_Scribe_Splash_Typewriter
        else         -> R.style.Theme_Scribe_Splash_Custom
    }
}
