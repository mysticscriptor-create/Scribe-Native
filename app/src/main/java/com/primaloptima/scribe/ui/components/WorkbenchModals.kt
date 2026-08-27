package com.primaloptima.scribe.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.util.model.PaneAccentColor
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.PaneScope
import com.primaloptima.scribe.util.model.toComposeColor

// ── 1. Choice Sheet (New Note / Add File / Add WorldSheet) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReferenceChoiceSheet(
    onDismiss: () -> Unit,
    onPickNewNote: () -> Unit,
    onPickAddFile: () -> Unit,
    onPickAddWorldSheet: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Add a Reference",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AddChoiceItem(
                title = "New Note",
                subtitle = "Write a quick reference note to pin here",
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                onClick = {
                    onDismiss()
                    onPickNewNote()
                }
            )

            AddChoiceItem(
                title = "Add File",
                subtitle = "Select existing notes from your books",
                icon = Icons.Default.Description,
                onClick = {
                    onDismiss()
                    onPickAddFile()
                }
            )

            AddChoiceItem(
                title = "Add WorldSheet",
                subtitle = "Pin character, location, or lore entries",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = {
                    onDismiss()
                    onPickAddWorldSheet()
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddChoiceItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ── 2. New Note Dialog ────────────────────────────────────────────────────────

@Composable
fun NewNoteDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "New Reference Note",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    placeholder = { Text("e.g. Character Sheet, Chapter Outline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (Markdown)") },
                    placeholder = { Text("Write content here...") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(title.ifBlank { "Untitled" }, content)
                            onDismiss()
                        }
                    ) {
                        Text("Create & Pin")
                    }
                }
            }
        }
    }
}

// ── 3. Add File Sheet (Notes Multi-Select) ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFileSheet(
    allNotes: List<Note>,
    currentBookId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (selectedIds: List<String>) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val bookIds = remember(allNotes) {
        listOf("ALL") + allNotes.map { it.bookId }.distinct()
    }
    var selectedBookFilter by remember { mutableStateOf(currentBookId ?: "ALL") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filteredNotes = remember(allNotes, searchQuery, selectedBookFilter) {
        allNotes.filter { note ->
            val matchBook = selectedBookFilter == "ALL" || note.bookId == selectedBookFilter
            val matchQuery = searchQuery.isBlank() ||
                    note.name.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true)
            matchBook && matchQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Notes as Reference",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${selectedIds.size} selected",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notes...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (bookIds.size > 2) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(bookIds) { bId ->
                        val isSelected = selectedBookFilter == bId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedBookFilter = bId },
                            label = { Text(if (bId == "ALL") "All Books" else bId.take(12)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filteredNotes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No notes found",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(filteredNotes, key = { it.id }) { note ->
                        val isChecked = selectedIds.contains(note.id)
                        Surface(
                            onClick = {
                                selectedIds = if (isChecked) selectedIds - note.id else selectedIds + note.id
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + note.id else selectedIds - note.id
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.content.isNotBlank()) {
                                        Text(
                                            text = note.content.take(80).replace('\n', ' '),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(selectedIds.toList())
                        onDismiss()
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Pin ${if (selectedIds.isNotEmpty()) "(${selectedIds.size})" else ""}")
                }
            }
        }
    }
}

// ── 4. Add WorldSheet Modal ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorldSheetModal(
    worldEntries: List<WorldEntry>,
    onDismiss: () -> Unit,
    onConfirm: (selectedIds: List<String>) -> Unit,
) {
    val categories = listOf("All", "character", "location", "faction", "item", "lore", "timeline")
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filteredEntries = remember(worldEntries, selectedCategory, searchQuery) {
        worldEntries.filter { entry ->
            val matchCat = selectedCategory == "All" || entry.type.equals(selectedCategory, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    entry.name.contains(searchQuery, ignoreCase = true) ||
                    entry.summary.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add WorldSheets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${selectedIds.size} selected",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search world sheets...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No world sheets found",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isChecked = selectedIds.contains(entry.id)
                        Surface(
                            onClick = {
                                selectedIds = if (isChecked) selectedIds - entry.id else selectedIds + entry.id
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedIds = if (it) selectedIds + entry.id else selectedIds - entry.id
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = entry.type.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = entry.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (entry.summary.isNotBlank()) {
                                        Text(
                                            text = entry.summary,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(selectedIds.toList())
                        onDismiss()
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Pin ${if (selectedIds.isNotEmpty()) "(${selectedIds.size})" else ""}")
                }
            }
        }
    }
}

// ── 5. Appearance Sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionAppearanceSheet(
    pane: PaneConfig,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onUpdatePane: (transform: (PaneConfig) -> PaneConfig) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Section Appearance",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Accent Colors
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = "ACCENT COLOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(PaneAccentColor.entries) { colorEnum ->
                            val isSelected = pane.accentColor == colorEnum
                            val composeColor = colorEnum.toComposeColor(isDark)

                            Surface(
                                onClick = {
                                    onUpdatePane { it.copy(accentColor = colorEnum) }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Canvas(modifier = Modifier.size(20.dp)) {
                                        if (colorEnum == PaneAccentColor.NONE) {
                                            drawCircle(
                                                color = Color.Gray,
                                                radius = size.minDimension / 2 - 1.dp.toPx(),
                                                style = Stroke(width = 1.5.dp.toPx())
                                            )
                                        } else {
                                            drawCircle(
                                                color = composeColor,
                                                radius = size.minDimension / 2
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = colorEnum.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Display Toggles
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DISPLAY OPTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Section Label",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Show shelf title chip",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = pane.showLabel,
                                    onCheckedChange = { v ->
                                        onUpdatePane { it.copy(showLabel = v) }
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Footer Pills",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Show bottom status & buttons",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = pane.showFooterPills,
                                    onCheckedChange = { v ->
                                        onUpdatePane { it.copy(showFooterPills = v) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 6. Scope Sheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScopeSheet(
    pane: PaneConfig,
    activeNote: Note?,
    onDismiss: () -> Unit,
    onUpdatePane: (transform: (PaneConfig) -> PaneConfig) -> Unit,
) {
    val bookTitle = activeNote?.bookId ?: "Current Book"
    val folderTitle = activeNote?.folderPath ?: "Current Folder"
    val fileTitle = activeNote?.name ?: "Current File"

    val currentScopeTitle = when (val s = pane.primaryScope) {
        is PaneScope.Global -> "Everywhere"
        is PaneScope.Book -> s.title.ifBlank { bookTitle }
        is PaneScope.Folder -> s.title.ifBlank { folderTitle }
        is PaneScope.File -> s.title.ifBlank { fileTitle }
    }

    val currentScopeType = when (pane.primaryScope) {
        is PaneScope.Global -> "app session"
        is PaneScope.Book -> "book"
        is PaneScope.Folder -> "folder"
        is PaneScope.File -> "file"
    }

    var showAddSecondary by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Section Scope",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This section is scoped to '$currentScopeTitle' and appears throughout this $currentScopeType.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "PRIMARY LOCATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Primary Scope Radio Group
            val scopeOptions: List<Pair<String, PaneScope>> = listOf(
                "Everywhere (Global)" to PaneScope.Global,
                "Book: $bookTitle" to PaneScope.Book(activeNote?.bookId ?: Note.DEFAULT_BOOK_ID, bookTitle),
                "Folder: $folderTitle" to PaneScope.Folder(activeNote?.folderPath ?: "/", folderTitle),
                "File: $fileTitle" to PaneScope.File(activeNote?.id ?: "", fileTitle),
            )

            scopeOptions.forEach { (label, scope) ->
                val isSelected = when (val current = pane.primaryScope) {
                    is PaneScope.Global -> scope is PaneScope.Global
                    is PaneScope.Book -> scope is PaneScope.Book && scope.bookId == current.bookId
                    is PaneScope.Folder -> scope is PaneScope.Folder && scope.folderPath == current.folderPath
                    is PaneScope.File -> scope is PaneScope.File && scope.fileId == current.fileId
                }

                Surface(
                    onClick = {
                        onUpdatePane { it.copy(primaryScope = scope) }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onUpdatePane { it.copy(primaryScope = scope) } }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Secondary Scopes
            Text(
                text = "SECONDARY LOCATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (pane.secondaryScopes.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(pane.secondaryScopes) { sScope ->
                        val chipLabel = when (sScope) {
                            is PaneScope.Global -> "Global"
                            is PaneScope.Book -> "Book: ${sScope.title}"
                            is PaneScope.Folder -> "Folder: ${sScope.title}"
                            is PaneScope.File -> "File: ${sScope.title}"
                        }
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(chipLabel, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            onUpdatePane { it.copy(secondaryScopes = it.secondaryScopes - sScope) }
                                        }
                                )
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = "No secondary locations added.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!showAddSecondary) {
                OutlinedButton(
                    onClick = { showAddSecondary = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add another location")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("Select a location to add:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(scopeOptions) { (label, scope) ->
                            SuggestionChip(
                                onClick = {
                                    if (!pane.secondaryScopes.contains(scope)) {
                                        onUpdatePane { it.copy(secondaryScopes = it.secondaryScopes + scope) }
                                    }
                                    showAddSecondary = false
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
