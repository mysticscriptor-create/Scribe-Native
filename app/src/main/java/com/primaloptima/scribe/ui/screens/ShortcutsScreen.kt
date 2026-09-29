package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.DefaultShortcuts
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ── Category Metadata ────────────────────────────────────────────────────────
data class CategoryMeta(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

val STUDIO_CATEGORIES: List<CategoryMeta> = listOf(
    CategoryMeta(
        id = DefaultShortcuts.CAT_DIALOGUE,
        title = "Dialogue & Monologue",
        subtitle = "Curly • straight • East Asian • em-dash starter",
        icon = Icons.Default.ChatBubbleOutline
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_BRACKETS,
        title = "Brackets & Enclosures",
        subtitle = "Standard • novel & LitRPG frames • angle brackets",
        icon = Icons.Default.DataArray
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_PUNCTUATION,
        title = "Punctuation & Cadence",
        subtitle = "Dashes • ellipsis • semicolon • interrobang",
        icon = Icons.Default.FormatQuote
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_SCENE_BREAKS,
        title = "Scene Breaks & Ornaments",
        subtitle = "Asterism • stars • dividers • thematic rule",
        icon = Icons.Default.AutoAwesome
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_STRUCTURE,
        title = "Structure & Lists",
        subtitle = "Bullets • numbers • checklists • blockquotes • headings",
        icon = Icons.Default.FormatListBulleted
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_FORMATTING,
        title = "Text Styles & Markdown",
        subtitle = "Bold • italic • strikethrough • inline code",
        icon = Icons.Default.FormatBold
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_ARROWS,
        title = "Arrows & Direction",
        subtitle = "Right • left • bidirectional • transition flows",
        icon = Icons.Default.East
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_MATH,
        title = "Math, Logic & Units",
        subtitle = "Arithmetic • comparisons • degrees • infinity",
        icon = Icons.Default.Calculate
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_CURRENCY,
        title = "Currency & Common Symbols",
        subtitle = "Dollar • Euro • Pound • Yen • percent",
        icon = Icons.Default.AttachMoney
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_CUSTOM,
        title = "Custom & Snippets",
        subtitle = "Personal writing phrases, templates and triggers",
        icon = Icons.Default.BookmarkBorder
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    vm: ShortcutsViewModel,
    onBack: () -> Unit
) {
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val activeBarShortcuts by vm.activeBarShortcuts.collectAsStateWithLifecycle()
    val disabledCategories by vm.disabledCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ── Search & Filter State ────────────────────────────────────────────────
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilterCategory by rememberSaveable { mutableStateOf<String?>("all") } // "all", "in_bar", or category ID

    // ── Edit/Reorder Mode State ──────────────────────────────────────────────
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    // ── Category Collapse State ──────────────────────────────────────────────
    // Initial load: keep first category expanded, rest collapsed
    var collapsedCategories by rememberSaveable {
        mutableStateOf(
            STUDIO_CATEGORIES.drop(1).map { it.id }.toSet()
        )
    }

    // ── Dialog States ────────────────────────────────────────────────────────
    var editingShortcut by remember { mutableStateOf<ShortcutAction?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ShortcutAction?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    // ── Haze & Snapshot for Frosted Glass ────────────────────────────────────
    val hazeState = LocalHazeState.current
    val view = LocalView.current
    var barBlurBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            withContext(Dispatchers.Default) {
                try {
                    val w = view.width.takeIf { it > 0 } ?: 1080
                    val h = view.height.takeIf { it > 0 } ?: 1920
                    val bmp = Bitmap.createBitmap(w / 4, h / 4, Bitmap.Config.ARGB_8888)
                    barBlurBitmap = BitmapBlur.blurBitmap(bmp, 20)
                } catch (_: Throwable) {}
            }
        }
    }

    val accentColor = ScribeTheme.colors.interaction.primary

    // ── Search & Filter Logic ────────────────────────────────────────────────
    val cleanQuery = searchQuery.trim().lowercase()

    val filteredShortcuts = remember(shortcuts, cleanQuery, selectedFilterCategory, activeBarShortcuts) {
        shortcuts.filter { action ->
            // Category / In-Bar filtering
            val matchesFilter = when (selectedFilterCategory) {
                "in_bar" -> action.isEnabled && action in activeBarShortcuts
                "all", null -> true
                else -> action.category == selectedFilterCategory
            }

            if (!matchesFilter) return@filter false

            // Query matching: label, payload, closing, kind, category, keywords
            if (cleanQuery.isBlank()) return@filter true

            action.label.lowercase().contains(cleanQuery) ||
                action.payload.lowercase().contains(cleanQuery) ||
                (action.closing?.lowercase()?.contains(cleanQuery) == true) ||
                action.kind.lowercase().contains(cleanQuery) ||
                action.category.lowercase().contains(cleanQuery) ||
                action.keywords.any { it.lowercase().contains(cleanQuery) }
        }
    }

    Scaffold(
        topBar = {
            ScribeTopBar(
                title = "Shortcut Studio",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actionsContent = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FrostedDropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reset to defaults") },
                                onClick = {
                                    showTopMenu = false
                                    showResetDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ScribeSingleFab(
                icon = Icons.Default.Add,
                contentDescription = "Create Custom Shortcut",
                onClick = { isCreatingNew = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. YOUR WRITING BAR (Hero Section) ───────────────────────────
            item(key = "hero_writing_bar") {
                WritingBarHeroSection(
                    activeShortcuts = activeBarShortcuts,
                    isEditMode = isEditMode,
                    onToggleEditMode = {
                        isEditMode = !isEditMode
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onRemoveShortcut = { shortcut ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setShortcutEnabled(shortcut.id, false)
                    },
                    onMoveShortcut = { from, to ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.moveActiveShortcut(from, to)
                    },
                    onScrollToLibrary = {
                        scope.launch {
                            listState.animateScrollToItem(1)
                        }
                    }
                )
            }

            // ── 2. SEARCH & DISCOVERY BAR ────────────────────────────────────
            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Search Bar
                    ShortcutSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )

                    // Horizontal Category Filter Row
                    ShortcutCategoryFilters(
                        selectedCategory = selectedFilterCategory,
                        activeBarCount = activeBarShortcuts.size,
                        onSelectCategory = { selectedFilterCategory = it }
                    )
                }
            }

            // ── 3. SHORTCUT LIBRARY SECTIONS ─────────────────────────────────
            // When user is searching or has selected a single filter, we dynamically group matching items
            val displayedCategories = if (selectedFilterCategory != null && selectedFilterCategory != "all" && selectedFilterCategory != "in_bar") {
                STUDIO_CATEGORIES.filter { it.id == selectedFilterCategory }
            } else {
                STUDIO_CATEGORIES
            }

            displayedCategories.forEach { catMeta ->
                val categoryShortcuts = filteredShortcuts.filter { it.category == catMeta.id }
                val allInThisCat = shortcuts.filter { it.category == catMeta.id }
                val isCatDisabled = disabledCategories.contains(catMeta.id)
                val activeInThisCat = allInThisCat.count { it.isEnabled && !isCatDisabled }
                val totalInThisCat = allInThisCat.size
                val isCollapsed = collapsedCategories.contains(catMeta.id) && cleanQuery.isBlank() && selectedFilterCategory == "all"

                if (categoryShortcuts.isNotEmpty() || (cleanQuery.isBlank() && selectedFilterCategory in listOf("all", catMeta.id))) {
                    item(key = "cat_header_${catMeta.id}") {
                        ShortcutCategoryAccordionHeader(
                            meta = catMeta,
                            activeCount = activeInThisCat,
                            totalCount = totalInThisCat,
                            isCategoryDisabled = isCatDisabled,
                            isCollapsed = isCollapsed,
                            onToggleCollapse = {
                                collapsedCategories = if (isCollapsed) {
                                    collapsedCategories - catMeta.id
                                } else {
                                    collapsedCategories + catMeta.id
                                }
                            },
                            onToggleCategoryEnabled = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleCategory(catMeta.id)
                            }
                        )
                    }

                    if (!isCollapsed) {
                        if (categoryShortcuts.isEmpty() && catMeta.id == DefaultShortcuts.CAT_CUSTOM) {
                            item(key = "empty_custom_notice") {
                                EmptyCustomCategoryNotice(
                                    onCreateClick = { isCreatingNew = true }
                                )
                            }
                        } else {
                            items(
                                items = categoryShortcuts,
                                key = { it.id }
                            ) { shortcut ->
                                val isShortcutActive = shortcut.isEnabled && !isCatDisabled
                                ShortcutCompactRow(
                                    shortcut = shortcut,
                                    isActive = isShortcutActive,
                                    isCategoryDisabled = isCatDisabled,
                                    onToggle = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        vm.toggleShortcutEnabled(shortcut.id)
                                    },
                                    onEdit = { editingShortcut = shortcut },
                                    onDelete = if (shortcut.category == DefaultShortcuts.CAT_CUSTOM) {
                                        { deleteCandidate = shortcut }
                                    } else null
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 54.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    // 1. Create / Edit Shortcut Dialog
    if (editingShortcut != null || isCreatingNew) {
        val target = editingShortcut
        CreateOrEditShortcutSheet(
            existing = target,
            onDismiss = {
                editingShortcut = null
                isCreatingNew = false
            },
            onSave = { updated ->
                if (target == null) {
                    vm.add(updated)
                    Toast.makeText(context, "Shortcut created", Toast.LENGTH_SHORT).show()
                } else {
                    vm.update(updated)
                    Toast.makeText(context, "Shortcut updated", Toast.LENGTH_SHORT).show()
                }
                editingShortcut = null
                isCreatingNew = false
            }
        )
    }

    // 2. Delete Confirmation Dialog
    deleteCandidate?.let { candidate ->
        FrostedDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Shortcut?") },
            text = { Text("Delete \"" + candidate.label + "\"? This custom shortcut cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(candidate.id)
                        deleteCandidate = null
                        Toast.makeText(context, "Shortcut deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Reset Confirmation Dialog
    if (showResetDialog) {
        FrostedDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset shortcuts?") },
            text = {
                Text("This restores all default writing shortcuts, their enabled states, custom order, and category settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        vm.resetToDefaults()
                        Toast.makeText(context, "Shortcuts reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Writing Bar Hero Section ──────────────────────────────────────────────────
@Composable
private fun WritingBarHeroSection(
    activeShortcuts: List<ShortcutAction>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onRemoveShortcut: (ShortcutAction) -> Unit,
    onMoveShortcut: (from: Int, to: Int) -> Unit,
    onScrollToLibrary: () -> Unit
) {
    val accentColor = ScribeTheme.colors.interaction.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            0.6.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title, Count & Edit/Done Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "YOUR WRITING BAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = accentColor
                    )
                    Text(
                        text = if (activeShortcuts.isNotEmpty()) "${activeShortcuts.size} shortcuts" else "0 shortcuts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeShortcuts.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onToggleEditMode,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isEditMode) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isEditMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isEditMode) "Done" else "Edit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Live Preview Surface
            if (activeShortcuts.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No shortcuts currently active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Add shortcuts from the library below to populate your writing bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onScrollToLibrary) {
                        Text("Browse shortcuts", color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fixed Undo & Redo Pills (Mirroring actual Editor bar)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(ScribeTheme.metrics.chipHeight)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo (Editor Action)",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(ScribeTheme.metrics.chipHeight)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo (Editor Action)",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Vertical Hairline Separator between fixed actions and user shortcuts
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        )

                        // Active Shortcuts
                        activeShortcuts.forEachIndexed { index, shortcut ->
                            if (!isEditMode) {
                                // Normal Mode: Authentic Bar Chip
                                ActiveBarChipItem(
                                    shortcut = shortcut,
                                    accentColor = accentColor
                                )
                            } else {
                                // Edit/Reorder Mode Chip
                                ReorderableBarChipItem(
                                    shortcut = shortcut,
                                    index = index,
                                    totalCount = activeShortcuts.size,
                                    accentColor = accentColor,
                                    onMove = { from, to -> onMoveShortcut(from, to) },
                                    onRemove = { onRemoveShortcut(shortcut) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Normal Mode Authentic Bar Chip Item ───────────────────────────────────────
@Composable
private fun ActiveBarChipItem(
    shortcut: ShortcutAction,
    accentColor: Color
) {
    val isDialogueOrSymbol = shortcut.category == DefaultShortcuts.CAT_DIALOGUE ||
        shortcut.category == DefaultShortcuts.CAT_BRACKETS ||
        shortcut.category == DefaultShortcuts.CAT_SCENE_BREAKS

    val chipBg = if (isDialogueOrSymbol) {
        accentColor.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }

    val contentColor = if (isDialogueOrSymbol) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = chipBg,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isDialogueOrSymbol) accentColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.height(ScribeTheme.metrics.chipHeight)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = ScribeTheme.spacing.medium, vertical = ScribeTheme.spacing.micro)
        ) {
            Text(
                text = shortcut.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

// ── Edit/Reorder Mode Bar Chip Item with Drag & Context Controls ───────────────
@Composable
private fun ReorderableBarChipItem(
    shortcut: ShortcutAction,
    index: Int,
    totalCount: Int,
    accentColor: Color,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragElevation"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dragScale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = animatedElevation,
        shadowElevation = animatedElevation,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDragging) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .scale(animatedScale)
            .height(34.dp)
            .zIndex(if (isDragging) 10f else 1f)
            .pointerInput(shortcut.id, index, totalCount) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        // Check if dragged enough to shift index
                        val threshold = 40.dp.toPx()
                        if (offsetX > threshold && index < totalCount - 1) {
                            onMove(index, index + 1)
                        } else if (offsetX < -threshold && index > 0) {
                            onMove(index, index - 1)
                        }
                        offsetX = 0f
                        isDragging = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-120f, 120f)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Drag affordance / Label
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.size(14.dp),
                tint = accentColor
            )

            Text(
                text = shortcut.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Contextual Menu Trigger (For accessibility, shift to start/end)
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Reorder options",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FrostedDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text("Move left") },
                            onClick = {
                                showMenu = false
                                onMove(index, index - 1)
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to beginning") },
                            onClick = {
                                showMenu = false
                                onMove(index, 0)
                            },
                            leadingIcon = { Icon(Icons.Default.FirstPage, contentDescription = null) }
                        )
                    }
                    if (index < totalCount - 1) {
                        DropdownMenuItem(
                            text = { Text("Move right") },
                            onClick = {
                                showMenu = false
                                onMove(index, index + 1)
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to end") },
                            onClick = {
                                showMenu = false
                                onMove(index, totalCount - 1)
                            },
                            leadingIcon = { Icon(Icons.Default.LastPage, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from bar", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            // Remove Button (✕)
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from writing bar",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Search Field ──────────────────────────────────────────────────────────────
@Composable
private fun ShortcutSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search shortcuts, quotes, brackets, symbols...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            focusedBorderColor = ScribeTheme.colors.interaction.primary
        ),
        singleLine = true
    )
}

// ── Category Filters Row ──────────────────────────────────────────────────────
@Composable
private fun ShortcutCategoryFilters(
    selectedCategory: String?,
    activeBarCount: Int,
    onSelectCategory: (String?) -> Unit
) {
    val accentColor = ScribeTheme.colors.interaction.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" Filter
        FilterChip(
            selected = selectedCategory == "all",
            onClick = { onSelectCategory("all") },
            label = { Text("All", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accentColor.copy(alpha = 0.15f),
                selectedLabelColor = accentColor
            )
        )

        // "In Bar" Filter
        FilterChip(
            selected = selectedCategory == "in_bar",
            onClick = { onSelectCategory("in_bar") },
            label = { Text("In Bar ($activeBarCount)", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accentColor.copy(alpha = 0.15f),
                selectedLabelColor = accentColor
            )
        )

        // Categories Filters
        STUDIO_CATEGORIES.forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat.id,
                onClick = { onSelectCategory(if (selectedCategory == cat.id) "all" else cat.id) },
                label = { Text(cat.title, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.15f),
                    selectedLabelColor = accentColor
                )
            )
        }
    }
}

// ── Category Accordion Header ─────────────────────────────────────────────────
@Composable
private fun ShortcutCategoryAccordionHeader(
    meta: CategoryMeta,
    activeCount: Int,
    totalCount: Int,
    isCategoryDisabled: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleCategoryEnabled: () -> Unit
) {
    val accentColor = ScribeTheme.colors.interaction.primary
    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(200),
        label = "chevronRot"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggleCollapse)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = meta.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (!isCategoryDisabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = meta.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (!isCategoryDisabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    // Active / Total Count Badge
                    Surface(
                        shape = CircleShape,
                        color = if (isCategoryDisabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        } else if (activeCount > 0) {
                            accentColor.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ) {
                        Text(
                            text = if (isCategoryDisabled) "Off" else "$activeCount / $totalCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCategoryDisabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else if (activeCount > 0) {
                                accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = meta.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Controls: Category Switch + Collapse Chevron
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Master Switch (Subtle scale for tight layouts)
            Switch(
                checked = !isCategoryDisabled,
                onCheckedChange = { onToggleCategoryEnabled() },
                modifier = Modifier.scale(0.75f)
            )

            // Chevron
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isCollapsed) "Expand category" else "Collapse category",
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Compact Shortcut Row ──────────────────────────────────────────────────────
@Composable
private fun ShortcutCompactRow(
    shortcut: ShortcutAction,
    isActive: Boolean,
    isCategoryDisabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accentColor = ScribeTheme.colors.interaction.primary
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Glyph Box + Label & Kind Description
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glyph Badge (Prominent visual reference)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) accentColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Texts
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCategoryDisabled) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${shortcut.kind.replaceFirstChar { it.uppercase() }} • ${shortcut.payload.replace("\n", "↵")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Trailing Controls: Three-dot menu + State Toggle Button (✓ / +)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three-dot options menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FrostedDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }

            // Compact State Control (✓ when active, + when inactive)
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (isActive) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isActive) accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "In Bar",
                            modifier = Modifier.size(16.dp),
                            tint = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to Bar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Empty Custom Category Notice ──────────────────────────────────────────────
@Composable
private fun EmptyCustomCategoryNotice(
    onCreateClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "No custom shortcuts yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Create a custom shortcut for a phrase, symbol or writing action you use often.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            TextButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create shortcut", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Create / Edit Shortcut Sheet / Dialog ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrEditShortcutSheet(
    existing: ShortcutAction?,
    onDismiss: () -> Unit,
    onSave: (ShortcutAction) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "insert") }
    var payload by remember { mutableStateOf(existing?.payload ?: "") }
    var closing by remember { mutableStateOf(existing?.closing ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: DefaultShortcuts.CAT_CUSTOM) }
    var keywordsText by remember { mutableStateOf(existing?.keywords?.joinToString(", ") ?: "") }
    val accentColor = ScribeTheme.colors.interaction.primary

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Shortcut" else "Edit Shortcut") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Interactive Live Preview Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val previewText = when (kind) {
                            "wrap" -> "$payload selected text $closing"
                            "pair" -> "$payload |cursor| $closing"
                            "prefix" -> "$payload line content"
                            else -> "text $payload text"
                        }
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button Label (e.g. “ ”, B, H1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Payload / Opening") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (kind == "wrap" || kind == "pair") {
                    OutlinedTextField(
                        value = closing,
                        onValueChange = { closing = it },
                        label = { Text("Closing Suffix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action Kind Selector
                Text("Action Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("pair", "insert", "prefix", "wrap").forEach { itemKind ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = kind == itemKind,
                                onClick = { kind = itemKind }
                            )
                            Text(
                                text = itemKind.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Search Keywords (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank() && payload.isNotBlank()) {
                        val parsedKeywords = keywordsText.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        onSave(
                            ShortcutAction(
                                id = existing?.id ?: (System.currentTimeMillis().toString() + Math.random().toString().takeLast(4)),
                                label = label.trim(),
                                kind = kind,
                                payload = payload,
                                closing = closing.ifBlank { null },
                                category = category,
                                isEnabled = existing?.isEnabled ?: true,
                                keywords = parsedKeywords
                            )
                        )
                    }
                }
            ) {
                Text("Save", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
