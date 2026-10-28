package com.primaloptima.scribe.navigation

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.primaloptima.scribe.LocalThemeViewModel
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.ui.screens.ExportStudioScreen
import com.primaloptima.scribe.ui.screens.GuideScreen
import com.primaloptima.scribe.ui.screens.HistoryScreen
import com.primaloptima.scribe.ui.screens.SettingsScreen
import com.primaloptima.scribe.ui.screens.SheetsScreen
import com.primaloptima.scribe.ui.screens.ShortcutsScreen
import com.primaloptima.scribe.ui.screens.ThemeEditScreen
import com.primaloptima.scribe.ui.screens.ThemeListScreen
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Editor-specific auxiliary overlay host.
 *
 * Hosts auxiliary destinations (Settings, History, Themes, Shortcuts, Guide, Sheets, Export Studio)
 * directly in an overlay above the live MainEditorScreen.
 *
 * Key guarantees:
 * 1. The underlying MainEditorScreen, UnifiedCanvasLayout, and Sora CodeEditor instance
 *    remain continuously mounted in memory without disposal or recreation.
 * 2. Back navigation unwinds auxiliary screens (e.g. ThemeEdit -> ThemeList -> Settings -> Editor)
 *    and dismisses the overlay without popping the main Route.Editor route.
 * 3. Consumes all pointer input to strictly prevent touches from leaking through to the editor.
 * 4. Dismissal reveals the exact live viewport instantaneously with 0ms recreation or snapping.
 */
@Composable
fun EditorAuxiliaryOverlayHost(
    auxiliaryBackStack: SnapshotStateList<Route>,
    editorVm: EditorViewModel,
    shortcutsVm: ShortcutsViewModel,
    defaultBookId: String,
    defaultNoteId: String,
    onDismissOverlay: () -> Unit
) {
    val isOverlayActive = auxiliaryBackStack.isNotEmpty()
    val currentDestination = auxiliaryBackStack.lastOrNull()
    val context = LocalContext.current

    // Hide keyboard and clear soft input whenever an auxiliary destination is opened
    LaunchedEffect(isOverlayActive) {
        if (isOverlayActive) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val windowToken = (context as? Activity)?.window?.decorView?.windowToken
            windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
        }
    }

    // Intercept hardware and gesture back events while auxiliary overlay is active
    BackHandler(enabled = isOverlayActive) {
        if (auxiliaryBackStack.size > 1) {
            auxiliaryBackStack.removeLast()
        } else {
            auxiliaryBackStack.clear()
            onDismissOverlay()
        }
    }

    AnimatedVisibility(
        visible = isOverlayActive,
        enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(200, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } +
               fadeOut(tween(200, easing = LinearEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Strictly consume all raw pointer events so touches cannot reach the background editor
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            val destination = currentDestination
            if (destination != null) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                fadeOut(tween(100, easing = LinearEasing))
                    },
                    label = "EditorAuxiliaryTransition"
                ) { targetRoute ->
                    when (targetRoute) {
                        is Route.Settings -> {
                            SettingsScreen(
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                },
                                onOpenThemes = {
                                    if (auxiliaryBackStack.lastOrNull() !is Route.ThemeList) {
                                        auxiliaryBackStack.add(Route.ThemeList)
                                    }
                                }
                            )
                        }

                        is Route.ThemeList -> {
                            val themeVm: ThemeViewModel = viewModel()
                            CompositionLocalProvider(LocalThemeViewModel provides themeVm) {
                                ThemeListScreen(
                                    vm = themeVm,
                                    onBack = {
                                        if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                        else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                    },
                                    onEditTheme = { themeId ->
                                        auxiliaryBackStack.add(Route.ThemeEditArgs(themeId))
                                    }
                                )
                            }
                        }

                        is Route.ThemeEditArgs -> {
                            val themeVm = LocalThemeViewModel.current ?: viewModel()
                            ThemeEditScreen(
                                themeId = targetRoute.themeId,
                                vm = themeVm,
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                }
                            )
                        }

                        is Route.History -> {
                            HistoryScreen(
                                targetNoteId = targetRoute.noteId,
                                editorVm = editorVm,
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                }
                            )
                        }

                        is Route.Shortcuts -> {
                            ShortcutsScreen(
                                vm = shortcutsVm,
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                }
                            )
                        }

                        is Route.Guide -> {
                            GuideScreen(
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                }
                            )
                        }

                        is Route.Sheets -> {
                            val sheetsVm = viewModel<com.primaloptima.scribe.viewmodel.SheetsViewModel>()
                            SheetsScreen(
                                vm = sheetsVm,
                                onBack = {
                                    if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                    else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                },
                                openCreateOnLaunch = targetRoute.openCreate
                            )
                        }

                        is Route.ExportStudio -> {
                            val activeNote = editorVm.activeNote.value
                            val app = context.applicationContext as ScribeApp
                            val targetNoteId = targetRoute.noteId.ifEmpty { defaultNoteId }
                            val fullNote by produceState<com.primaloptima.scribe.data.Note?>(
                                initialValue = activeNote?.takeIf { it.id == targetNoteId },
                                key1 = targetNoteId
                            ) {
                                if (value == null) {
                                    value = withContext(Dispatchers.IO) {
                                        app.database.noteDao().getById(targetNoteId)
                                    }
                                }
                            }
                            val note = fullNote
                            if (note != null) {
                                ExportStudioScreen(
                                    note = note,
                                    bookId = targetRoute.bookId ?: defaultBookId,
                                    initialFormat = targetRoute.initialFormat ?: "pdf",
                                    onBack = {
                                        if (auxiliaryBackStack.size > 1) auxiliaryBackStack.removeLast()
                                        else { auxiliaryBackStack.clear(); onDismissOverlay() }
                                    }
                                )
                            }
                        }

                        else -> {
                            LaunchedEffect(targetRoute) {
                                if (auxiliaryBackStack.isNotEmpty()) auxiliaryBackStack.removeLast()
                                if (auxiliaryBackStack.isEmpty()) onDismissOverlay()
                            }
                        }
                    }
                }
            }
        }
    }
}
