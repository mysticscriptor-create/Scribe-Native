package com.primaloptima.scribe.ui.components.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.AddFileSheet
import com.primaloptima.scribe.ui.components.AddReferenceChoiceSheet
import com.primaloptima.scribe.ui.components.AddWorldSheetModal
import com.primaloptima.scribe.ui.components.NewNoteDialog
import com.primaloptima.scribe.ui.components.SectionAppearanceSheet
import com.primaloptima.scribe.ui.components.SectionOverflowMenu
import com.primaloptima.scribe.ui.components.SectionScopeSheet
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.localHasBgImage
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.PaneAccentColor
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.toComposeColor
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun WorkbenchCard(
    modifier            : Modifier = Modifier,
    pane                : PaneConfig,
    slotKey             : String,
    pinnedIds           : List<String>,
    pinnedIndex         : Int,
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    books               : List<Book> = emptyList(),
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
    accentColor         : Color = Color.Unspecified,
    removeMode          : Boolean = false,
    onRemoveClick       : (() -> Unit)? = null,
    onPrev              : () -> Unit,
    onNext              : () -> Unit,
    onSelectIndex       : (Int) -> Unit,
    onSwitch            : () -> Unit = {},
    onEdit              : (String) -> Unit = {},
    onRemove            : (String) -> Unit = {},
    onPick              : () -> Unit = {},
    onUpdatePane        : (transform: (PaneConfig) -> PaneConfig) -> Unit,
    onDuplicatePane     : () -> Unit,
    onMinimizePane      : () -> Unit,
    onFocusPane         : () -> Unit,
    onUnpinNote         : (noteId: String) -> Unit,
    onReorderNote       : (fromIndex: Int, toIndex: Int) -> Unit,
    onCreateNote        : (title: String, content: String) -> Unit,
    onPinNotes          : (selectedIds: List<String>) -> Unit,
    hazeState           : dev.chrisbanes.haze.HazeState? = LocalHazeState.current,
    isDetached          : Boolean = false,
    onStartDetachedDrag : ((note: Note, touchPos: Offset, size: androidx.compose.ui.geometry.Size, slotPosInRoot: Offset) -> Unit)? = null,
    onDetachedDrag      : ((dx: Float, dy: Float) -> Unit)? = null,
    onEndDetachedDrag   : (() -> Unit)? = null,
    onCancelDetachedDrag: (() -> Unit)? = null,
) {
    val currentId = pinnedIds.getOrNull(pinnedIndex)
    val currentNote = remember(currentId, allNotes, worldEntries) {
        allNotes.firstOrNull { it.id == currentId }
            ?: worldEntries.firstOrNull { it.id == currentId }?.let { w ->
                Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}")
            }
    }

    val pinnedNotesList = remember(pinnedIds, allNotes, worldEntries) {
        pinnedIds.mapNotNull { id ->
            allNotes.firstOrNull { it.id == id }
                ?: worldEntries.firstOrNull { it.id == id }?.let { w ->
                    Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
                }
        }
    }

    val isDark = isSystemInDarkTheme()
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()

    var slotSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var slotPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    // Section feature states
    var showOverflow by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf(false) }
    var labelInputText by remember(pane.label) { mutableStateOf(pane.label) }
    var showReferencesOverlay by remember { mutableStateOf(false) }
    var unpinCandidateNoteId by remember { mutableStateOf<String?>(null) }

    // Modals
    var showAddReferenceChoiceSheet by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showAddFileSheet by remember { mutableStateOf(false) }
    var showAddWorldSheetModal by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showScopeSheet by remember { mutableStateOf(false) }

    val paneAccentColor = pane.accentColor.toComposeColor(isDark)
    val hasAccentBar = pane.accentColor != PaneAccentColor.NONE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .graphicsLayer {
                alpha = if (isDetached) 0.35f else 1f
                scaleX = if (isDetached) 0.98f else 1f
                scaleY = if (isDetached) 0.98f else 1f
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!hasBgImage) Modifier.background(solidSurface, RoundedCornerShape(12.dp))
                else Modifier.frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .onGloballyPositioned { layoutCoordinates ->
                slotSize = androidx.compose.ui.geometry.Size(
                    layoutCoordinates.size.width.toFloat(),
                    layoutCoordinates.size.height.toFloat()
                )
                slotPositionInRoot = layoutCoordinates.positionInRoot()
            }
    ) {
        // Vertical accent bar on leading edge if pane accent color is set (3g)
        if (hasAccentBar) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(paneAccentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .drawBehind {
                    val alpha = if (isDark) 0.06f else 0.12f
                    drawRoundRect(
                        color = Color.White.copy(alpha = alpha),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
                    )
                }
        )

        if (currentNote == null) {
            Column(
                modifier            = Modifier.fillMaxSize().clickable(onClick = { showAddReferenceChoiceSheet = true }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Pin a reference note",
                             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Pin a reference note", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text("Tap to add notes or world sheets", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            val currentNoteState by rememberUpdatedState(currentNote)
            val currentSlotSize by rememberUpdatedState(slotSize)
            val currentSlotPosition by rememberUpdatedState(slotPositionInRoot)
            val currentOnStartDetachedDrag by rememberUpdatedState(onStartDetachedDrag)
            val currentOnDetachedDrag by rememberUpdatedState(onDetachedDrag)
            val currentOnEndDetachedDrag by rememberUpdatedState(onEndDetachedDrag)
            val currentOnCancelDetachedDrag by rememberUpdatedState(onCancelDetachedDrag)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(animationSpec = spring())
            ) {
                var headerDragging by remember { mutableStateOf(false) }

                // ── Flushed Header Section ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (headerDragging || isDetached) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .padding(start = if (hasAccentBar) 5.dp else 4.dp, end = 4.dp, top = 0.dp, bottom = 2.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val note = currentNoteState ?: return@detectDragGesturesAfterLongPress
                                    val onStart = currentOnStartDetachedDrag ?: return@detectDragGesturesAfterLongPress
                                    headerDragging = true
                                    onStart(note, offset, currentSlotSize, currentSlotPosition)
                                },
                                onDragEnd = {
                                    headerDragging = false
                                    currentOnEndDetachedDrag?.invoke()
                                },
                                onDragCancel = {
                                    headerDragging = false
                                    currentOnCancelDetachedDrag?.invoke()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDetachedDrag?.invoke(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Section Label Badge flushed with top-left corner
                            AnimatedVisibility(visible = pane.showLabel) {
                                val chipBg = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor.copy(alpha = 0.20f)
                                else MaterialTheme.colorScheme.primaryContainer
                                val chipText = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor
                                else MaterialTheme.colorScheme.onPrimaryContainer

                                if (editingLabel) {
                                    val focusRequester = remember { FocusRequester() }
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                    BasicTextField(
                                        value = labelInputText,
                                        onValueChange = { labelInputText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            color = chipText
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            editingLabel = false
                                            onUpdatePane { it.copy(label = labelInputText.ifBlank { "SECTION" }) }
                                        }),
                                        modifier = Modifier
                                            .focusRequester(focusRequester)
                                            .background(chipBg, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused && editingLabel) {
                                                    editingLabel = false
                                                    onUpdatePane { it.copy(label = labelInputText.ifBlank { "SECTION" }) }
                                                }
                                            }
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 4.dp),
                                        color = chipBg,
                                        onClick = {
                                            labelInputText = pane.label
                                            editingLabel = true
                                        }
                                    ) {
                                        Text(
                                            text = pane.label.ifBlank { "SECTION" }.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            color = chipText,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))

                            // Note Title - tapping opens reference switcher overlay
                            Text(
                                text = currentNote.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .clickable { showReferencesOverlay = true }
                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                            )
                        }

                        if (pinnedIds.size > 1) {
                            IconButton(onClick = onPrev, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous note", modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "${pinnedIndex + 1}/${pinnedIds.size}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(2.dp))
                            IconButton(onClick = onNext, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next note", modifier = Modifier.size(14.dp))
                            }
                        }

                        if (removeMode) {
                            IconButton(
                                onClick = { onRemoveClick?.invoke() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove Section",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(15.dp))
                            }

                            if (showOverflow) {
                                SectionOverflowMenu(
                                    pane = pane,
                                    isDark = isDark,
                                    onDismiss = { showOverflow = false },
                                    onEditLabel = {
                                        showOverflow = false
                                        labelInputText = pane.label
                                        editingLabel = true
                                    },
                                    onAddRef = {
                                        showOverflow = false
                                        showAddReferenceChoiceSheet = true
                                    },
                                    onReferences = {
                                        showOverflow = false
                                        showReferencesOverlay = true
                                    },
                                    onDuplicate = {
                                        showOverflow = false
                                        onDuplicatePane()
                                    },
                                    onFocus = {
                                        showOverflow = false
                                        onFocusPane()
                                    },
                                    onAppearance = {
                                        showOverflow = false
                                        showAppearanceSheet = true
                                    },
                                    onScope = {
                                        showOverflow = false
                                        showScopeSheet = true
                                    },
                                    onMinimize = {
                                        showOverflow = false
                                        onMinimizePane()
                                    },
                                    hazeState = hazeState
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // ── Note Content (Read-Only Sora CodeEditor) with Floating Pills Overlay ───
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        factory = { ctx ->
                            CodeEditor(ctx).apply {
                                isEditable             = false
                                isLineNumberEnabled    = false
                                isHighlightCurrentLine = false
                                isWordwrap             = true
                                setText(currentNote.content.ifBlank { "(Empty note content)" })
                                activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { editor ->
                            val incoming = currentNote.content.ifBlank { "(Empty note content)" }
                            if (editor.text.toString() != incoming) editor.setText(incoming)
                            activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                        }
                    )

                    // Floating Action Pills over the view (Bottom-End aligned)
                    AnimatedVisibility(
                        visible = pane.showFooterPills,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasBgImage) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                // Fullscreen Focus Pill
                                IconButton(
                                    onClick = onFocusPane,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInFull,
                                        contentDescription = "Focus note",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Add Reference Pill
                                IconButton(
                                    onClick = { showAddReferenceChoiceSheet = true },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add reference",
                                        modifier = Modifier.size(13.dp),
                                        tint = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Floating Meta Pill (Bottom-Start aligned)
                    AnimatedVisibility(
                        visible = pane.showFooterPills,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (hasBgImage) MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(9.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "Read-only",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── References Switcher Overlay Dialog ─────────────────────────────────────
    if (showReferencesOverlay) {
        AlertDialog(
            onDismissRequest = { showReferencesOverlay = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "References (${pinnedNotesList.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            showReferencesOverlay = false
                            showAddReferenceChoiceSheet = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                if (pinnedNotesList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No pinned references", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(pinnedNotesList, key = { _, n -> n.id }) { idx, noteItem ->
                            val isCurrent = idx == pinnedIndex
                            Surface(
                                onClick = {
                                    onSelectIndex(idx)
                                    showReferencesOverlay = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(18.dp)
                                    ) {
                                        if (idx > 0) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { onReorderNote(idx, idx - 1) }
                                            )
                                        }
                                        if (idx < pinnedNotesList.size - 1) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { onReorderNote(idx, idx + 1) }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = noteItem.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (noteItem.content.isNotBlank()) {
                                            Text(
                                                text = noteItem.content.take(60).replace('\n', ' '),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            showReferencesOverlay = false
                                            unpinCandidateNoteId = noteItem.id
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Unpin",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReferencesOverlay = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ── Unpin Confirmation Dialog (3c) ────────────────────────────────────────
    if (unpinCandidateNoteId != null) {
        val noteIdToUnpin = unpinCandidateNoteId!!
        AlertDialog(
            onDismissRequest = { unpinCandidateNoteId = null },
            title = { Text("Unpin this reference?") },
            text = { Text("It will be removed from this section. The original file won't be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnpinNote(noteIdToUnpin)
                        unpinCandidateNoteId = null
                    }
                ) {
                    Text("Unpin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { unpinCandidateNoteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Add Reference Choice Sheet (3d) ───────────────────────────────────────
    if (showAddReferenceChoiceSheet) {
        AddReferenceChoiceSheet(
            onDismiss = { showAddReferenceChoiceSheet = false },
            onPickNewNote = { showNewNoteDialog = true },
            onPickAddFile = { showAddFileSheet = true },
            onPickAddWorldSheet = { showAddWorldSheetModal = true }
        )
    }

    // ── New Note Dialog (3d) ──────────────────────────────────────────────────
    if (showNewNoteDialog) {
        NewNoteDialog(
            onDismiss = { showNewNoteDialog = false },
            onSave = { title, content -> onCreateNote(title, content) }
        )
    }

    // ── Add File Sheet (3d) ───────────────────────────────────────────────────
    if (showAddFileSheet) {
        AddFileSheet(
            allNotes = allNotes,
            currentBookId = activeNote?.bookId,
            books = books,
            onDismiss = { showAddFileSheet = false },
            onConfirm = { ids -> onPinNotes(ids) }
        )
    }

    // ── Add WorldSheet Modal (3d) ─────────────────────────────────────────────
    if (showAddWorldSheetModal) {
        AddWorldSheetModal(
            worldEntries = worldEntries,
            onDismiss = { showAddWorldSheetModal = false },
            onConfirm = { ids -> onPinNotes(ids) }
        )
    }

    // ── Section Appearance Sheet (3g) ─────────────────────────────────────────
    if (showAppearanceSheet) {
        SectionAppearanceSheet(
            pane = pane,
            isDark = isDark,
            onDismiss = { showAppearanceSheet = false },
            onUpdatePane = onUpdatePane
        )
    }

    // ── Section Scope Sheet (3h) ──────────────────────────────────────────────
    if (showScopeSheet) {
        SectionScopeSheet(
            pane = pane,
            activeNote = activeNote,
            onDismiss = { showScopeSheet = false },
            onUpdatePane = onUpdatePane
        )
    }
}
