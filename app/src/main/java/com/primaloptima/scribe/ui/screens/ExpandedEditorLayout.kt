package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedPanel
import dev.chrisbanes.haze.HazeState
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch

/**
 * Expanded layout (Tablets, Foldables, Laptops):
 * - ModalNavigationDrawer wraps SupportingPaneScaffold
 * - Supports simultaneous side-by-side display of Editor + Supporting Pane (Right Panel)
 * - Zero animating layout jitter when returning from supporting pane
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExpandedEditorLayout(
    hazeState: HazeState,
    barBlurBitmap: Bitmap?,
    soraEditorRef: CodeEditor?,
    editorContent: @Composable (
        onNavClick: () -> Unit,
        onOpenRightPanel: () -> Unit,
        isLeftDrawerOpen: Boolean
    ) -> Unit,
    leftDrawerContent: @Composable (onClose: () -> Unit) -> Unit,
    rightPanelContent: @Composable (onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val navigator = rememberSupportingPaneScaffoldNavigator()

    val isSupportingPaneOpen =
        navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
                ModalDrawerSheet(
                    drawerContainerColor = Color.Transparent,
                    modifier = Modifier.frostedPanel(hazeState)
                ) {
                    leftDrawerContent {
                        scope.launch { drawerState.close() }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        SupportingPaneScaffold(
            value = navigator.scaffoldValue,
            directive = navigator.scaffoldDirective,
            mainPane = {
                AnimatedPane {
                    editorContent(
                        onNavClick = {
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close()
                                else drawerState.open()
                            }
                        },
                        onOpenRightPanel = {
                            scope.launch {
                                if (isSupportingPaneOpen) {
                                    if (navigator.canNavigateBack()) {
                                        navigator.navigateBack()
                                    }
                                } else {
                                    navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
                                }
                            }
                        },
                        isLeftDrawerOpen = drawerState.isOpen
                    )
                }
            },
            supportingPane = {
                AnimatedPane {
                    rightPanelContent {
                        scope.launch {
                            if (navigator.canNavigateBack()) {
                                navigator.navigateBack()
                            }
                        }
                    }
                }
            }
        )
    }
}
