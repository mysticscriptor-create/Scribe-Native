package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.WorldEntry
import kotlinx.coroutines.launch
import com.primaloptima.scribe.ui.components.FullScreenImageViewer
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.WorldImageUtil
import com.primaloptima.scribe.viewmodel.SheetsViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Time-ago helper ───────────────────────────────────────────────────────────

private fun timeAgo(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }
}

// ── Main Sheets Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SheetsScreen(
    vm: SheetsViewModel,
    onBack: () -> Unit,
    openCreateOnLaunch: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allEntries by vm.allEntries.collectAsStateWithLifecycle()

    val categoryKeys = listOf("All", "character", "location", "faction", "item", "lore", "timeline")
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(SheetsViewModel.SortOption.UPDATED_DESC) }
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }

    var showCreateSheet by remember { mutableStateOf(false) }
    var showTagFilterSheet by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (openCreateOnLaunch) showCreateSheet = true }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    var entryToEdit by remember { mutableStateOf<WorldEntry?>(null) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }
    var fullScreenImageTitle by remember { mutableStateOf("") }

    // Predictive / Android Hardware Back Handler for Adaptive Scaffold
    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    // Extract all unique tags with usage count across all sheets
    val allTagsWithCount = remember(allEntries) {
        val map = mutableMapOf<String, Int>()
        allEntries.forEach { entry ->
            try {
                val tags: List<String> = AppJson.decodeFromString(entry.tagsJson)
                tags.forEach { tag ->
                    if (tag.isNotBlank()) {
                        map[tag] = (map[tag] ?: 0) + 1
                    }
                }
            } catch (_: Exception) {}
        }
        map
    }

    // Count per category for chip badges
    val countByType = remember(allEntries) {
        allEntries.groupingBy { it.type.lowercase() }.eachCount()
    }

    // Filter and Sort entries
    val filteredAndSortedEntries = remember(
        allEntries,
        selectedCategory,
        searchQuery,
        selectedTags,
        selectedSort
    ) {
        val filtered = allEntries.filter { entry ->
            // Category filter
            val matchesCategory = selectedCategory == "All" ||
                    entry.type.equals(selectedCategory, ignoreCase = true)

            // Tags filter
            val matchesTags = if (selectedTags.isEmpty()) true else {
                try {
                    val entryTags: List<String> = AppJson.decodeFromString(entry.tagsJson)
                    selectedTags.all { reqTag -> entryTags.any { it.equals(reqTag, ignoreCase = true) } }
                } catch (_: Exception) {
                    false
                }
            }

            // Search query filter (matches name, summary, tags, and field values)
            val matchesQuery = if (searchQuery.isBlank()) true else {
                entry.name.contains(searchQuery, ignoreCase = true) ||
                entry.summary.contains(searchQuery, ignoreCase = true) ||
                entry.tagsJson.contains(searchQuery, ignoreCase = true) ||
                entry.fieldsJson.contains(searchQuery, ignoreCase = true)
            }

            matchesCategory && matchesTags && matchesQuery
        }

        // Apply Sorting
        when (selectedSort) {
            SheetsViewModel.SortOption.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
            SheetsViewModel.SortOption.UPDATED_ASC  -> filtered.sortedBy { it.updatedAt }
            SheetsViewModel.SortOption.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
            SheetsViewModel.SortOption.CREATED_ASC  -> filtered.sortedBy { it.createdAt }
            SheetsViewModel.SortOption.NAME_ASC     -> filtered.sortedBy { it.name.lowercase() }
            SheetsViewModel.SortOption.NAME_DESC    -> filtered.sortedByDescending { it.name.lowercase() }
            SheetsViewModel.SortOption.TYPE         -> filtered.sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
        }
    }

    val hazeState = LocalHazeState.current

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            ScribeTopBar(
                title             = "World Building Sheets",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        },
        floatingActionButton = {
            ScribeSingleFab(
                icon               = Icons.Default.Add,
                contentDescription = "Add Entry",
                onClick            = { showCreateSheet = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search & Sort Row ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = { Text("Search sheets, attributes, tags…", fontSize = 13.sp) },
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                // Sort Chip Button
                Box {
                    FilterChip(
                        selected = selectedSort != SheetsViewModel.SortOption.UPDATED_DESC,
                        onClick = { sortMenuExpanded = true },
                        label = {
                            Text(
                                text = selectedSort.shortLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SheetsViewModel.SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (selectedSort == option) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedSort == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (selectedSort == option) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Spacer(modifier = Modifier.size(24.dp))
                                    }
                                },
                                onClick = {
                                    selectedSort = option
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Category & Tags Filter Chips Row ──────────────────────────────
            LazyRow(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tag Drawer Trigger Chip
                item(key = "tag_filter_trigger") {
                    val hasTagFilters = selectedTags.isNotEmpty()
                    FilterChip(
                        selected = hasTagFilters,
                        onClick = { showTagFilterSheet = true },
                        label = {
                            Text(
                                text = if (hasTagFilters) "Tags (${selectedTags.size})" else "Tags",
                                fontWeight = if (hasTagFilters) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (hasTagFilters) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Category chips
                items(categoryKeys, key = { it }) { key ->
                    val meta  = categoryMeta(key)
                    val count = if (key == "All") allEntries.size
                                else countByType[key] ?: 0
                    val selected = selectedCategory == key
                    FilterChip(
                        selected = selected,
                        onClick  = { selectedCategory = key },
                        label    = {
                            Text(
                                if (count > 0) "${meta.label} ($count)"
                                else meta.label
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector        = meta.icon,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                       else meta.color
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ── Active Tag Filter Strip (if tags selected) ────────────────────
            if (selectedTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text("#$tag", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { selectedTags = selectedTags - tag }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        TextButton(
                            onClick = { selectedTags = emptySet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Clear all", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── World Entries List or Empty State ─────────────────────────────
            if (filteredAndSortedEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            imageVector        = if (searchQuery.isNotBlank() || selectedTags.isNotEmpty()) Icons.Default.SearchOff
                                                 else categoryMeta(selectedCategory).icon,
                            contentDescription = null,
                            modifier           = Modifier.size(52.dp),
                            tint               = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text  = if (searchQuery.isNotBlank() || selectedTags.isNotEmpty())
                                        "No world sheets matching your filters."
                                    else
                                        "No ${categoryMeta(selectedCategory).label.lowercase()} yet.\nTap + to create your first sheet.",
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier
                        .fillMaxSize()
                        .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                ) {
                    items(filteredAndSortedEntries, key = { it.id }) { entry ->
                        WorldEntryCard(
                            entry       = entry,
                            onClick     = {
                                selectedEntryId = entry.id
                                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.id) }
                            },
                            onImageClick = {
                                if (!entry.imageUri.isNullOrEmpty()) {
                                    fullScreenImageUri = entry.imageUri
                                    fullScreenImageTitle = entry.name
                                } else {
                                    selectedEntryId = entry.id
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.id) }
                                }
                            },
                            onEdit      = { entryToEdit   = entry },
                            onDuplicate = {
                                vm.duplicateEntry(entry.id)
                                Toast.makeText(context, "Duplicated ${entry.name}", Toast.LENGTH_SHORT).show()
                            },
                            onDelete    = {
                                vm.deleteEntry(entry.id)
                                if (selectedEntryId == entry.id) {
                                    selectedEntryId = null
                                }
                                Toast.makeText(context, "Deleted sheet", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
},
detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                val currentEntry = allEntries.find { it.id == selectedEntryId }
                if (currentEntry != null) {
                    WorldEntryDetailScreen(
                        entry = currentEntry,
                        onBack = {
                            scope.launch {
                                if (navigator.canNavigateBack()) {
                                    navigator.navigateBack()
                                } else {
                                    selectedEntryId = null
                                }
                            }
                        },
                        onEdit = {
                            entryToEdit = currentEntry
                        },
                        onDuplicate = {
                            vm.duplicateEntry(currentEntry.id)
                            Toast.makeText(context, "Duplicated ${currentEntry.name}", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            vm.deleteEntry(currentEntry.id)
                            scope.launch {
                                if (navigator.canNavigateBack()) {
                                    navigator.navigateBack()
                                } else {
                                    selectedEntryId = null
                                }
                            }
                            Toast.makeText(context, "Deleted sheet", Toast.LENGTH_SHORT).show()
                        },
                        onFieldsReordered = { updatedFields ->
                            vm.updateEntryFields(currentEntry, updatedFields)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select a sheet to view details",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    )

    // ── Create World Sheet Bottom Sheet ───────────────────────────────────────
    if (showCreateSheet) {
        CreateWorldEntrySheet(
            selectedCategory = selectedCategory,
            onDismiss        = { showCreateSheet = false },
            onConfirm        = { name, type ->
                vm.createEntry(type, name) { created ->
                    showCreateSheet = false
                    entryToEdit      = created
                }
            }
        )
    }

    // ── Edit World Sheet Bottom Sheet ─────────────────────────────────────────
    entryToEdit?.let { editTarget ->
        EditWorldEntrySheet(
            entry     = editTarget,
            onDismiss = { entryToEdit = null },
            onSave    = { updated ->
                vm.updateEntry(updated)
                entryToEdit = null
                if (selectedEntryId == updated.id) {
                    selectedEntryId = updated.id
                }
                Toast.makeText(context, "Saved changes", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ── Tag Filter Sheet Drawer ───────────────────────────────────────────────
    if (showTagFilterSheet) {
        TagFilterSheet(
            allTagsWithCount     = allTagsWithCount,
            initiallySelectedTags = selectedTags,
            onDismiss            = { showTagFilterSheet = false },
            onApply              = { newSelected ->
                selectedTags = newSelected
                showTagFilterSheet = false
            }
        )
    }

    // ── Full-Screen Image Viewer Modal ────────────────────────────────────────
    fullScreenImageUri?.let { uri ->
        FullScreenImageViewer(
            imageUri = uri,
            title = fullScreenImageTitle,
            onDismiss = { fullScreenImageUri = null }
        )
    }
}

// ── World Entry Card Component ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldEntryCard(
    entry: WorldEntry,
    onClick: () -> Unit,
    onImageClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val meta = categoryMeta(entry.type)
    val tags: List<String> = remember(entry.tagsJson) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    ScribeCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Fixed Image / Icon Thumbnail on the left
            if (!entry.imageUri.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, meta.color.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        .clickable { onImageClick() }
                ) {
                    AsyncImage(
                        model = entry.imageUri,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(meta.color.copy(alpha = 0.15f))
                        .border(1.dp, meta.color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = null,
                        tint = meta.color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Body info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(meta.color)
                    )
                    Text(
                        text = meta.label.dropLast(1).ifEmpty { meta.label }.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = meta.color
                    )
                    Text(
                        text = "· ${timeAgo(entry.updatedAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = entry.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (entry.summary.isNotBlank()) {
                    Text(
                        text = entry.summary,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }

                // Tags preview pills
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        tags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = meta.color.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, meta.color.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 10.sp,
                                    color = meta.color,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }

            // More Options Dropdown
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Sheet") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
