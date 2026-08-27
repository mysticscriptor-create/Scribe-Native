package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.engine.ProseAnalysisResult
import com.primaloptima.scribe.ui.components.workbench.Workbench
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.MinimizedBy
import com.primaloptima.scribe.util.model.OutlineEntry
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.PaneScope
import com.primaloptima.scribe.util.model.WorkbenchState
import io.github.rosemoe.sora.widget.CodeEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorRightPanel(
    rightPanelTab       : Int,
    onTabChange         : (Int) -> Unit,
    workbenchState      : WorkbenchState = WorkbenchState(),
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    books               : List<Book> = emptyList(),
    outline             : List<OutlineEntry>,
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
    proseAnalysis       : ProseAnalysisResult,
    soraEditorRef       : CodeEditor?,
    tabBarAtBottom      : Boolean,
    onToggleTabBarPos   : () -> Unit,
    onUpdatePane        : (paneId: String, transform: (PaneConfig) -> PaneConfig) -> Unit = { _, _ -> },
    onUpdateWorkbench   : (transform: (WorkbenchState) -> WorkbenchState) -> Unit = {},
    onAddPane           : (scope: PaneScope) -> Unit = {},
    onRemovePane        : (paneId: String) -> Unit = {},
    onDuplicatePane     : (paneId: String) -> Unit = {},
    onMinimizePane      : (paneId: String, by: MinimizedBy) -> Unit = { _, _ -> },
    onRestorePane       : (paneId: String) -> Unit = {},
    onPinNote           : (paneId: String, noteId: String) -> Unit = { _, _ -> },
    onUnpinNote         : (paneId: String, noteId: String) -> Unit = { _, _ -> },
    onReorderNote       : (paneId: String, fromIndex: Int, toIndex: Int) -> Unit = { _, _, _ -> },
    onCreateNote        : (paneId: String, title: String, content: String) -> Unit = { _, _, _ -> },
    onSaveNoteContent   : (noteId: String, content: String) -> Unit = { _, _ -> },
    onLoadNote          : (noteId: String) -> Unit = {},
    onClose             : () -> Unit,
    barBlurBitmap       : Bitmap?,
    hazeState           : dev.chrisbanes.haze.HazeState?,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val tabBarContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            PillTab(label = "Shelf",   selected = rightPanelTab == 0, onClick = { onTabChange(0) })
            PillTab(label = "Outline", selected = rightPanelTab == 1, onClick = { onTabChange(1) })
            PillTab(label = "Prose",   selected = rightPanelTab == 2, onClick = { onTabChange(2) })
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .frostedPanel(hazeState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                if (!tabBarAtBottom) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor             = Color.Transparent,
                            titleContentColor          = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary,
                            actionIconContentColor     = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.frostedBar(hazeState),
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                            }
                        },
                        title = { tabBarContent() },
                        actions = {
                            IconButton(onClick = onToggleTabBarPos) {
                                Icon(Icons.Default.VerticalAlignBottom, "Move tabs to bottom",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (rightPanelTab) {
                        0 -> Workbench(
                            workbenchState      = workbenchState,
                            allNotes            = allNotes,
                            worldEntries        = worldEntries,
                            books               = books,
                            activeTheme         = activeTheme,
                            activeNote          = activeNote,
                            onUpdatePane        = onUpdatePane,
                            onUpdateWorkbench   = onUpdateWorkbench,
                            onAddPane           = onAddPane,
                            onRemovePane        = onRemovePane,
                            onDuplicatePane     = onDuplicatePane,
                            onMinimizePane      = onMinimizePane,
                            onRestorePane       = onRestorePane,
                            onPinNote           = onPinNote,
                            onUnpinNote         = onUnpinNote,
                            onReorderNote       = onReorderNote,
                            onCreateNote        = onCreateNote,
                            onSaveNoteContent   = onSaveNoteContent,
                            onLoadNote          = onLoadNote,
                            hazeState           = hazeState,
                            snackbarHostState   = snackbarHostState,
                        )
                        1 -> OutlineView(
                            outline = outline,
                            soraEditorRef = soraEditorRef
                        )
                        2 -> ProseAnalysisView(
                            analysis = proseAnalysis,
                            onJumpToSentence = { line ->
                                soraEditorRef?.let { editor ->
                                    val l = line.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                                    editor.setSelection(l, 0)
                                }
                            }
                        )
                    }
                }

                if (tabBarAtBottom) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            tabBarContent()
                        }
                        IconButton(onClick = onToggleTabBarPos) {
                            Icon(Icons.Default.VerticalAlignTop, "Move tabs to top", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .zIndex(40f)
            )
        }
    }
}

// ── PillTab ───────────────────────────────────────────────────────────────────

@Composable
private fun PillTab(
    label   : String,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var textWidth by remember { mutableFloatStateOf(0f) }

    val animatedUnderlineWidth by animateFloatAsState(
        targetValue = if (selected) textWidth else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "PillTabUnderlineWidth"
    )

    val animatedFontSize by animateFloatAsState(
        targetValue = if (selected) 15f else 13f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "PillTabFontSize"
    )

    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .drawBehind {
                if (animatedUnderlineWidth > 0f) {
                    val strokeWidth = 2.dp.toPx()
                    val lineY = size.height - strokeWidth / 2
                    val startX = (size.width - animatedUnderlineWidth) / 2
                    val endX = startX + animatedUnderlineWidth
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, lineY),
                        end = Offset(endX, lineY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = animatedFontSize.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.onGloballyPositioned { layoutCoordinates ->
                textWidth = layoutCoordinates.size.width.toFloat()
            }
        )
    }
}
