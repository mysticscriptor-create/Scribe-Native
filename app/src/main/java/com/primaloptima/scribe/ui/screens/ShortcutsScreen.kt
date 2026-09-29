package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeShapeTokens
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
    val iconGlyph: String? = null,
    val iconVector: ImageVector? = null,
    val filterLabel: String
)

val STUDIO_CATEGORIES: List<CategoryMeta> = listOf(
    CategoryMeta(
        id = DefaultShortcuts.CAT_DIALOGUE,
        title = "Dialogue & Monologue",
        subtitle = "Curly • straight • East Asian",
        iconVector = Icons.Default.ChatBubbleOutline,
        filterLabel = "Dialogue"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_PUNCTUATION,
        title = "Punctuation",
        subtitle = "Commas • periods • dashes • ellipsis",
        iconGlyph = "’",
        filterLabel = "Punctuation"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_BRACKETS,
        title = "Brackets & Parentheses",
        subtitle = "Parentheses • braces • brackets",
        iconGlyph = "( )",
        filterLabel = "Brackets"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_ARROWS,
        title = "Arrows & Symbols",
        subtitle = "Arrows • math • currency • misc",
        iconVector = Icons.Default.ArrowForward,
        filterLabel = "Symbols"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_FORMATTING,
        title = "Editing Shortcuts",
        subtitle = "Select • delete • format • more",
        iconVector = Icons.Default.AutoAwesome,
        filterLabel = "Editing"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_SCENE_BREAKS,
        title = "Scene Breaks & Ornaments",
        subtitle = "Asterism • stars • dividers • thematic rule",
        iconGlyph = "⁂",
        filterLabel = "Ornaments"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_STRUCTURE,
        title = "Structure & Lists",
        subtitle = "Bullets • numbers • checklists • blockquotes",
        iconVector = Icons.Default.FormatListBulleted,
        filterLabel = "Structure"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_MATH,
        title = "Math & Logic",
        subtitle = "Arithmetic • comparisons • degrees • infinity",
        iconGlyph = "±",
        filterLabel = "Math"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_CURRENCY,
        title = "Currency",
        subtitle = "Dollar • Euro • Pound • Yen • percent",
        iconGlyph = "$",
        filterLabel = "Currency"
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_CUSTOM,
        title = "Custom & Snippets",
        subtitle = "Personal writing phrases, templates and triggers",
        iconVector = Icons.Default.BookmarkBorder,
        filterLabel = "Custom"
    )
)

// Human-friendly title mapper for standard shortcut IDs to match reference UI
private fun getHumanFriendlyTitle(shortcut: ShortcutAction): String = when (shortcut.id) {
    "quote_curly" -> "Curly double quotes"
    "quote_straight" -> "Straight double quotes"
    "quote_single_curly" -> "Curly single quotes"
    "quote_single_straight" -> "Straight single quotes"
    "quote_heavy_double" -> "Curly quotation marks"
    "quote_heavy_single" -> "Heavy single quotes"
    "quote_corner" -> "Japanese corner quotes"
    "quote_corner_double" -> "Double corner brackets"
    "quote_guillemets" -> "French guillemets"
    "dialogue_emdash" -> "Dialogue em dash"
    "paren" -> "Round parentheses"
    "bracket" -> "Square brackets"
    "brace" -> "Curly braces"
    "sym_status_bracket" -> "Status window brackets"
    "sym_lenticular" -> "Lenticular brackets"
    "sym_white_square" -> "White square brackets"
    "sym_angle_bracket" -> "Angle brackets"
    "sym_double_angle" -> "Double angle brackets"
    "sym_floor" -> "Floor brackets"
    "emdash" -> "Em dash"
    "endash" -> "En dash"
    "ellipsis" -> "Typographical ellipsis"
    "semicolon" -> "Semicolon"
    "colon" -> "Colon"
    "interrobang" -> "Interrobang"
    "middledot" -> "Middle dot"
    "hr" -> "Thematic break (---)"
    "sym_asterism" -> "Asterism flourish"
    "sym_three_stars" -> "Three stars (* * *)"
    "sym_sparkle" -> "Magic sparkle"
    "sym_black_star" -> "Black star"
    "sym_section" -> "Section symbol"
    "sym_fleuron" -> "Fleuron leaf"
    "list" -> "Bullet list item"
    "numlist" -> "Numbered list item"
    "tasklist" -> "Task list checkbox"
    "blockquote" -> "Blockquote indent"
    "h1" -> "Heading 1 (#)"
    "h2" -> "Heading 2 (##)"
    "tab" -> "Tab (4 spaces)"
    "bold" -> "Bold format"
    "italic" -> "Italic format"
    "strikethrough" -> "Strikethrough format"
    "code" -> "Inline code backticks"
    "arrow_right" -> "Right arrow"
    "arrow_left" -> "Left arrow"
    "arrow_both" -> "Bidirectional arrow"
    "arrow_double_right" -> "Double right arrow"
    "math_plusminus" -> "Plus-minus sign"
    "math_multiply" -> "Multiplication sign"
    "math_divide" -> "Division sign"
    "math_notequal" -> "Not equal to"
    "math_approx" -> "Approximately equal"
    "math_degree" -> "Degree symbol"
    "math_infinity" -> "Infinity symbol"
    "sym_dollar" -> "Dollar sign"
    "sym_euro" -> "Euro sign"
    "sym_pound" -> "Pound sign"
    "sym_yen" -> "Yen sign"
    "sym_percent" -> "Percent sign"
    else -> shortcut.label
}

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

    // ── Semantic Color Tokens ────────────────────────────────────────────────
    val colors = ScribeTheme.colors
    val canvasBg = colors.surfaces.background
    val cardBg = colors.surfaces.surface
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val contentTertiary = colors.content.tertiary
    val accentPrimary = colors.interaction.primary
    val onAccent = colors.content.onAccent
    val glyphBoxBg = colors.surfaces.surfaceLowest

    // ── Search & Filter State ────────────────────────────────────────────────
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilterCategory by rememberSaveable { mutableStateOf<String?>("all") }

    // ── Edit/Reorder Mode State ──────────────────────────────────────────────
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    // ── Category Collapse State ──────────────────────────────────────────────
    // Match reference: Dialogue expanded, remaining categories collapsed
    var collapsedCategories by rememberSaveable {
        mutableStateOf(
            STUDIO_CATEGORIES.drop(1).map { it.id }.toSet()
        )
    }

    // ── Category Expanded Items Limit State (Show more (5) v) ────────────────
    var expandedLimits by rememberSaveable {
        mutableStateOf(mapOf<String, Int>())
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

    // ── Search & Filter Logic ────────────────────────────────────────────────
    val cleanQuery = searchQuery.trim().lowercase()

    val filteredShortcuts = remember(shortcuts, cleanQuery, selectedFilterCategory, activeBarShortcuts) {
        shortcuts.filter { action ->
            val matchesFilter = when (selectedFilterCategory) {
                "in_bar" -> action.isEnabled && action in activeBarShortcuts
                "all", null -> true
                else -> action.category == selectedFilterCategory
            }

            if (!matchesFilter) return@filter false

            if (cleanQuery.isBlank()) return@filter true

            action.label.lowercase().contains(cleanQuery) ||
                getHumanFriendlyTitle(action).lowercase().contains(cleanQuery) ||
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
                titleContent = { titleMod ->
                    Text(
                        text = "Shortcut Studio",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = contentPrimary,
                        modifier = titleMod
                    )
                },
                actionsContent = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = contentPrimary
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
                                        tint = accentPrimary
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Extended FAB using theme token interaction.primary
            Surface(
                onClick = { isCreatingNew = true },
                shape = CircleShape,
                color = accentPrimary,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Create",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = onAccent,
                            lineHeight = 15.sp
                        )
                        Text(
                            text = "Shortcut",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = onAccent,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        containerColor = canvasBg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

            // ── 2. SEARCH & PILL FILTERS ─────────────────────────────────────
            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Search Bar (Rounded pill shape)
                    ShortcutSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )

                    // Horizontal Filter Chips Row: [All] [Punctuation] [Dialogue] [Editing] [Symbols] ...
                    ShortcutCategoryFilters(
                        selectedCategory = selectedFilterCategory,
                        onSelectCategory = { selectedFilterCategory = it }
                    )
                }
            }

            // ── 3. SHORTCUT LIBRARY SECTIONS ─────────────────────────────────
            val displayedCategories = if (selectedFilterCategory != null && selectedFilterCategory != "all" && selectedFilterCategory != "in_bar") {
                STUDIO_CATEGORIES.filter { it.id == selectedFilterCategory }
            } else {
                STUDIO_CATEGORIES
            }

            displayedCategories.forEach { catMeta ->
                val categoryShortcuts = filteredShortcuts.filter { it.category == catMeta.id }
                val allInThisCat = shortcuts.filter { it.category == catMeta.id }
                val isCatDisabled = disabledCategories.contains(catMeta.id)
                val totalInThisCat = allInThisCat.size
                val isCollapsed = collapsedCategories.contains(catMeta.id) && cleanQuery.isBlank() && selectedFilterCategory == "all"

                if (categoryShortcuts.isNotEmpty() || (cleanQuery.isBlank() && selectedFilterCategory in listOf("all", catMeta.id))) {
                    item(key = "cat_card_${catMeta.id}") {
                        // Enclosed Elegant Card Container
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ScribeShapeTokens.CardMedium,
                            color = cardBg,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 14.dp)
                            ) {
                                // Accordion Header Row
                                ShortcutCategoryAccordionHeader(
                                    meta = catMeta,
                                    totalCount = totalInThisCat,
                                    isCategoryDisabled = isCatDisabled,
                                    isCollapsed = isCollapsed,
                                    onToggleCollapse = {
                                        collapsedCategories = if (isCollapsed) {
                                            collapsedCategories - catMeta.id
                                        } else {
                                            collapsedCategories + catMeta.id
                                        }
                                    }
                                )

                                // Expanded Shortcut List
                                if (!isCollapsed) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (categoryShortcuts.isEmpty() && catMeta.id == DefaultShortcuts.CAT_CUSTOM) {
                                        EmptyCustomCategoryNotice(
                                            onCreateClick = { isCreatingNew = true }
                                        )
                                    } else {
                                        val displayLimit = expandedLimits[catMeta.id] ?: 5
                                        val visibleShortcuts = if (cleanQuery.isNotBlank() || selectedFilterCategory != "all") {
                                            categoryShortcuts
                                        } else {
                                            categoryShortcuts.take(displayLimit)
                                        }

                                        visibleShortcuts.forEach { shortcut ->
                                            val isShortcutActive = shortcut.isEnabled && !isCatDisabled
                                            ShortcutCompactRow(
                                                shortcut = shortcut,
                                                isActive = isShortcutActive,
                                                onToggle = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    vm.toggleShortcutEnabled(shortcut.id)
                                                },
                                                onEdit = { editingShortcut = shortcut },
                                                onDelete = if (shortcut.category == DefaultShortcuts.CAT_CUSTOM) {
                                                    { deleteCandidate = shortcut }
                                                } else null
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        // "Show more (N) v" Expand Toggle if more items exist
                                        if (cleanQuery.isBlank() && selectedFilterCategory == "all" && categoryShortcuts.size > displayLimit) {
                                            val remaining = categoryShortcuts.size - displayLimit
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        expandedLimits = expandedLimits + (catMeta.id to (displayLimit + 10))
                                                    }
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Show more ($remaining)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = contentTertiary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = contentTertiary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacing for extended FAB
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
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
                    Text("Delete", color = colors.semantic.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                    Text("Reset", color = colors.semantic.error)
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
    val colors = ScribeTheme.colors
    val cardBg = colors.surfaces.surface
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val contentTertiary = colors.content.tertiary
    val accentPrimary = colors.interaction.primary
    val onAccent = colors.content.onAccent
    val glyphBoxBg = colors.surfaces.surfaceLowest
    val innerBarBg = colors.surfaces.surfaceLowest.copy(alpha = 0.65f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ScribeShapeTokens.CardMedium,
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Leading Rounded Icon + Title & Count + Trailing "Edit" Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(glyphBoxBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCompact,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentPrimary
                        )
                    }

                    Column {
                        Text(
                            text = "Your Writing Bar",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentPrimary
                        )
                        Text(
                            text = if (activeShortcuts.isNotEmpty()) "${activeShortcuts.size} shortcuts" else "0 shortcuts",
                            fontSize = 12.sp,
                            color = contentSecondary
                        )
                    }
                }

                if (activeShortcuts.isNotEmpty()) {
                    Surface(
                        onClick = onToggleEditMode,
                        shape = CircleShape,
                        color = if (isEditMode) accentPrimary else glyphBoxBg,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isEditMode) accentPrimary else subtleBorder
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isEditMode) onAccent else contentPrimary
                            )
                            Text(
                                text = if (isEditMode) "Done" else "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEditMode) onAccent else contentPrimary
                            )
                        }
                    }
                }
            }

            // Live Preview Enclosed Surface
            if (activeShortcuts.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "No shortcuts currently active",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentPrimary
                    )
                    TextButton(onClick = onScrollToLibrary) {
                        Text("Browse shortcuts", color = accentPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(
                    shape = ScribeShapeTokens.CardMedium,
                    color = innerBarBg,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Undo Pill (Circular)
                        Surface(
                            shape = CircleShape,
                            color = glyphBoxBg,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    modifier = Modifier.size(15.dp),
                                    tint = contentPrimary
                                )
                            }
                        }

                        // Redo Pill (Circular)
                        Surface(
                            shape = CircleShape,
                            color = glyphBoxBg,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    modifier = Modifier.size(15.dp),
                                    tint = contentPrimary
                                )
                            }
                        }

                        // Active Shortcuts
                        activeShortcuts.forEachIndexed { index, shortcut ->
                            if (!isEditMode) {
                                // Rounded pill chip
                                Surface(
                                    shape = CircleShape,
                                    color = glyphBoxBg,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = shortcut.label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = contentPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            } else {
                                // Reorderable Chip Item
                                ReorderableBarChipItem(
                                    shortcut = shortcut,
                                    index = index,
                                    totalCount = activeShortcuts.size,
                                    onMove = onMoveShortcut,
                                    onRemove = { onRemoveShortcut(shortcut) }
                                )
                            }
                        }

                        // Trailing overflow pill (...) & forward indicator (›)
                        Surface(
                            shape = CircleShape,
                            color = glyphBoxBg,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "More shortcuts",
                                    modifier = Modifier.size(16.dp),
                                    tint = contentPrimary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentTertiary
                        )
                    }
                }
            }
        }
    }
}

// ── Edit/Reorder Mode Bar Chip Item with Drag Gestures ────────────────────────
@Composable
private fun ReorderableBarChipItem(
    shortcut: ShortcutAction,
    index: Int,
    totalCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: () -> Unit
) {
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentTertiary = colors.content.tertiary
    val accentPrimary = colors.interaction.primary
    val glyphBoxBg = colors.surfaces.surfaceLowest
    val cardBg = colors.surfaces.surface

    var offsetX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 1.dp,
        label = "dragElevation"
    )

    Surface(
        shape = CircleShape,
        color = if (isDragging) cardBg else glyphBoxBg,
        tonalElevation = animatedElevation,
        shadowElevation = animatedElevation,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isDragging) accentPrimary else subtleBorder
        ),
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .height(34.dp)
            .zIndex(if (isDragging) 10f else 1f)
            .pointerInput(shortcut.id, index, totalCount) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
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
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.size(13.dp),
                tint = accentPrimary
            )

            Text(
                text = shortcut.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentPrimary
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(12.dp),
                    tint = contentTertiary
                )
            }
        }
    }
}

// ── Search Field (Pill Shape) ─────────────────────────────────────────────────
@Composable
private fun ShortcutSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val colors = ScribeTheme.colors
    val cardBg = colors.surfaces.surface
    val subtleBorder = colors.borders.subtle
    val accentPrimary = colors.interaction.primary
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        placeholder = {
            Text(
                "Search shortcuts...",
                fontSize = 14.sp,
                color = contentSecondary.copy(alpha = 0.85f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = contentPrimary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(16.dp),
                        tint = contentSecondary
                    )
                }
            }
        },
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = cardBg,
            focusedContainerColor = cardBg,
            unfocusedBorderColor = subtleBorder,
            focusedBorderColor = accentPrimary
        ),
        singleLine = true
    )
}

// ── Category Filters Row ──────────────────────────────────────────────────────
@Composable
private fun ShortcutCategoryFilters(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
) {
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val accentPrimary = colors.interaction.primary
    val onAccent = colors.content.onAccent
    val contentPrimary = colors.content.primary
    val chipUnselectedBg = colors.surfaces.surfaceLowest

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "All" Filter (Accent primary when selected)
        val isAllSelected = selectedCategory == "all"
        Surface(
            onClick = { onSelectCategory("all") },
            shape = CircleShape,
            color = if (isAllSelected) accentPrimary else chipUnselectedBg,
            border = androidx.compose.foundation.BorderStroke(
                0.8.dp,
                if (isAllSelected) accentPrimary else subtleBorder
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                Text(
                    text = "All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAllSelected) onAccent else contentPrimary
                )
            }
        }

        // Specific Taxonomy Filters matching Goal UI: Punctuation, Dialogue, Editing, Symbols...
        listOf(
            DefaultShortcuts.CAT_PUNCTUATION to "Punctuation",
            DefaultShortcuts.CAT_DIALOGUE to "Dialogue",
            DefaultShortcuts.CAT_FORMATTING to "Editing",
            DefaultShortcuts.CAT_ARROWS to "Symbols",
            DefaultShortcuts.CAT_BRACKETS to "Brackets",
            DefaultShortcuts.CAT_STRUCTURE to "Structure",
            DefaultShortcuts.CAT_CUSTOM to "Custom"
        ).forEach { (catId, label) ->
            val isSelected = selectedCategory == catId
            Surface(
                onClick = { onSelectCategory(if (isSelected) "all" else catId) },
                shape = CircleShape,
                color = if (isSelected) accentPrimary else chipUnselectedBg,
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isSelected) accentPrimary else subtleBorder
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) onAccent else contentPrimary
                    )
                }
            }
        }
    }
}

// ── Category Accordion Header ─────────────────────────────────────────────────
@Composable
private fun ShortcutCategoryAccordionHeader(
    meta: CategoryMeta,
    totalCount: Int,
    isCategoryDisabled: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit
) {
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val glyphBoxBg = colors.surfaces.surfaceLowest

    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 180f,
        animationSpec = tween(200),
        label = "chevronRot"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggleCollapse)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Box
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(glyphBoxBg),
                contentAlignment = Alignment.Center
            ) {
                if (meta.iconVector != null) {
                    Icon(
                        imageVector = meta.iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = contentPrimary
                    )
                } else if (meta.iconGlyph != null) {
                    Text(
                        text = meta.iconGlyph,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentPrimary
                    )
                }
            }

            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = meta.title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = if (!isCategoryDisabled) contentPrimary else contentSecondary.copy(alpha = 0.6f)
                    )

                    // Pill count badge (e.g., 10)
                    Surface(
                        shape = CircleShape,
                        color = glyphBoxBg,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder)
                    ) {
                        Text(
                            text = if (isCategoryDisabled) "Off" else "$totalCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = meta.subtitle,
                    fontSize = 12.sp,
                    color = contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Accordion Expand/Collapse Chevron (matching reference)
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isCollapsed) "Expand category" else "Collapse category",
            modifier = Modifier
                .size(22.dp)
                .rotate(chevronRotation),
            tint = contentPrimary
        )
    }
}

// ── Compact Shortcut Row ──────────────────────────────────────────────────────
@Composable
private fun ShortcutCompactRow(
    shortcut: ShortcutAction,
    isActive: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val accentPrimary = colors.interaction.primary
    val onAccent = colors.content.onAccent
    val glyphBoxBg = colors.surfaces.surfaceLowest

    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Glyph Box + Label & Subtitle
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glyph Badge (Prominent rounded box ~40dp)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(glyphBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = contentPrimary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            // Title & Subtitle (Matching reference e.g., "Curly double quotes", "Pair • “ ”")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getHumanFriendlyTitle(shortcut),
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = contentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val formatDetail = when (shortcut.kind) {
                    "pair", "wrap" -> {
                        val close = shortcut.closing?.ifBlank { null } ?: shortcut.payload
                        "${shortcut.payload} $close".trim()
                    }
                    else -> shortcut.payload.replace("\n", " ").trim()
                }
                val displayKind = if (shortcut.kind == "wrap") "Pair" else shortcut.kind.replaceFirstChar { it.uppercase() }
                Text(
                    text = "$displayKind • $formatDetail",
                    fontSize = 11.sp,
                    color = contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Trailing Controls: Three-dot menu + Circular Action Button (✓ / +)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Three-dot options menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(15.dp),
                        tint = contentSecondary
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
                            text = { Text("Delete", color = colors.semantic.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.semantic.error)
                            }
                        )
                    }
                }
            }

            // Circular Action Button: Solid accent circle with white checkmark when active, or light circle with dark plus sign when inactive
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (isActive) accentPrimary else glyphBoxBg,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (isActive) accentPrimary else subtleBorder
                ),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active in bar",
                            modifier = Modifier.size(15.dp),
                            tint = onAccent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to bar",
                            modifier = Modifier.size(15.dp),
                            tint = contentPrimary
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
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val glyphBoxBg = colors.surfaces.surfaceLowest

    Surface(
        shape = ScribeShapeTokens.CardSmall,
        color = glyphBoxBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder.copy(alpha = 0.4f)),
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
                color = contentPrimary
            )
            Text(
                text = "Create a custom shortcut for a phrase, symbol or writing action you use often.",
                style = MaterialTheme.typography.bodySmall,
                color = contentSecondary,
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

// ── Create / Edit Shortcut Preset Model ───────────────────────────────────────
private data class ShortcutPreset(
    val display: String,
    val label: String,
    val open: String,
    val close: String,
    val kind: String,
    val keywords: String,
    val category: String
)

// ── Create / Edit Shortcut Bottom Sheet ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrEditShortcutSheet(
    existing: ShortcutAction?,
    onDismiss: () -> Unit,
    onSave: (ShortcutAction) -> Unit
) {
    val colors = ScribeTheme.colors
    val accentPrimary = colors.interaction.primary
    val onAccent = colors.interaction.onPrimary
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val contentTertiary = colors.content.tertiary
    val surfaceLowest = colors.surfaces.surfaceLowest
    val surfaceRaised = colors.surfaces.surfaceRaised
    val surfaceContainer = colors.surfaces.surface
    val subtleBorder = colors.borders.subtle

    var label by remember { mutableStateOf(existing?.label ?: "") }
    var kind by remember {
        mutableStateOf(
            if (existing?.kind == "wrap") "pair" else (existing?.kind ?: "pair")
        )
    }
    var payload by remember { mutableStateOf(existing?.payload ?: "") }
    var closing by remember { mutableStateOf(existing?.closing ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: DefaultShortcuts.CAT_CUSTOM) }
    var keywordsText by remember { mutableStateOf(existing?.keywords?.joinToString(", ") ?: "") }
    var isHintExpanded by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val isValid = label.trim().isNotBlank() && payload.trim().isNotBlank()
    val isEditMode = existing != null

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = surfaceContainer,
        contentColor = contentPrimary,
        tonalElevation = 2.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(contentSecondary.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Header (Requirement 4: Title + Close Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditMode) "Edit Shortcut" else "Create Shortcut",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = contentSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = subtleBorder.copy(alpha = 0.5f)
            )

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Quick Start Presets (Requirement 14)
                if (!isEditMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "QUICK START",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                ShortcutPreset("“ ”", "Curly quotes", "“", "”", "pair", "quote, dialogue, curly", DefaultShortcuts.CAT_DIALOGUE),
                                ShortcutPreset("‘ ’", "Single quotes", "‘", "’", "pair", "quote, thought, monologue", DefaultShortcuts.CAT_DIALOGUE),
                                ShortcutPreset("( )", "Parentheses", "(", ")", "pair", "parentheses, parens, wrap", DefaultShortcuts.CAT_BRACKETS),
                                ShortcutPreset("[ ]", "Square brackets", "[", "]", "pair", "brackets, citation, square", DefaultShortcuts.CAT_BRACKETS),
                                ShortcutPreset("{ }", "Curly braces", "{", "}", "pair", "braces, code, curly", DefaultShortcuts.CAT_BRACKETS),
                                ShortcutPreset("** **", "Bold text", "**", "**", "pair", "bold, strong, formatting", DefaultShortcuts.CAT_FORMATTING),
                                ShortcutPreset("* *", "Italic text", "*", "*", "pair", "italic, emphasis, formatting", DefaultShortcuts.CAT_FORMATTING),
                                ShortcutPreset("…", "Ellipsis", "…", "", "insert", "ellipsis, dots, pause", DefaultShortcuts.CAT_PUNCTUATION),
                                ShortcutPreset("—", "Em dash", "—", "", "insert", "em dash, dash, cadence", DefaultShortcuts.CAT_PUNCTUATION),
                                ShortcutPreset("- [ ]", "Task item", "- [ ] ", "", "prefix", "task, todo, checklist", DefaultShortcuts.CAT_STRUCTURE),
                                ShortcutPreset("•", "Bullet list", "• ", "", "prefix", "bullet, list, item", DefaultShortcuts.CAT_STRUCTURE),
                                ShortcutPreset(">", "Blockquote", "> ", "", "prefix", "quote, callout, blockquote", DefaultShortcuts.CAT_STRUCTURE)
                            )
                            presets.forEach { preset ->
                                Surface(
                                    onClick = {
                                        label = preset.label
                                        payload = preset.open
                                        closing = preset.close
                                        kind = preset.kind
                                        keywordsText = preset.keywords
                                        category = preset.category
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = surfaceLowest,
                                    border = BorderStroke(1.dp, subtleBorder),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = preset.display,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = contentPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Live Preview (Requirement 9)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = surfaceLowest,
                    border = BorderStroke(1.dp, subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val previewText = when (kind) {
                            "pair", "wrap" -> {
                                val open = payload.ifBlank { "“" }
                                val close = closing.ifBlank { payload.ifBlank { "”" } }
                                "$open selected text $close"
                            }
                            "prefix" -> {
                                val p = payload.ifBlank { "• " }
                                "$p selected text"
                            }
                            else -> {
                                val ins = payload.ifBlank { "…" }
                                "selected text$ins"
                            }
                        }
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Type Selector (Requirement 5 - Compact equal-width segmented control, zero overflow at 320dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentSecondary,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(surfaceLowest, RoundedCornerShape(9.dp))
                            .border(BorderStroke(1.dp, subtleBorder), RoundedCornerShape(9.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val types = listOf(
                            "pair" to "Enclose",
                            "prefix" to "Prefix",
                            "insert" to "Insert"
                        )
                        types.forEach { (typeKey, typeTitle) ->
                            val isSelected = (kind == typeKey) || (typeKey == "pair" && kind == "wrap")
                            Surface(
                                onClick = { kind = typeKey },
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSelected) accentPrimary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = typeTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) onAccent else contentSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Compact 1-sentence explanation (Requirement 10)
                    Text(
                        text = when (kind) {
                            "pair", "wrap" -> "Wraps selected text or creates an opening/closing pair."
                            "prefix" -> "Inserts at the beginning of the line with smart continuation."
                            else -> "Inserts text directly at the cursor position."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        color = contentSecondary,
                        lineHeight = 15.sp
                    )
                }

                // Field: Label (Requirement 15, 16)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Curly quotes, Bold, Task", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentPrimary,
                        unfocusedBorderColor = subtleBorder,
                        focusedLabelColor = accentPrimary,
                        unfocusedLabelColor = contentSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Dynamic Field based on Type (Requirements 6, 7, 8)
                when (kind) {
                    "pair", "wrap" -> {
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text("Opening Delimiter", fontSize = 12.sp) },
                            placeholder = { Text("e.g. “, **, [, /*", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentPrimary,
                                unfocusedBorderColor = subtleBorder,
                                focusedLabelColor = accentPrimary,
                                unfocusedLabelColor = contentSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = closing,
                            onValueChange = { closing = it },
                            label = { Text("Closing Delimiter (optional)", fontSize = 12.sp) },
                            placeholder = { Text(if (payload.isNotBlank()) payload else "Mirrors opening", fontSize = 12.sp) },
                            supportingText = {
                                Text(
                                    "Leave blank to mirror opening (e.g. ** → **text**)",
                                    fontSize = 11.sp,
                                    color = contentSecondary
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentPrimary,
                                unfocusedBorderColor = subtleBorder,
                                focusedLabelColor = accentPrimary,
                                unfocusedLabelColor = contentSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "prefix" -> {
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text("Line Prefix", fontSize = 12.sp) },
                            placeholder = { Text("e.g. - [ ] , 1. , • , > ", fontSize = 12.sp) },
                            supportingText = {
                                Text(
                                    "Inserted at start of line. Smart continuation & indentation are handled automatically.",
                                    fontSize = 11.sp,
                                    color = contentSecondary
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentPrimary,
                                unfocusedBorderColor = subtleBorder,
                                focusedLabelColor = accentPrimary,
                                unfocusedLabelColor = contentSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> { // insert
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text("Text to insert", fontSize = 12.sp) },
                            placeholder = { Text("e.g. …, —, ©", fontSize = 12.sp) },
                            supportingText = {
                                Text(
                                    "Places text directly at the cursor position.",
                                    fontSize = 11.sp,
                                    color = contentSecondary
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentPrimary,
                                unfocusedBorderColor = subtleBorder,
                                focusedLabelColor = accentPrimary,
                                unfocusedLabelColor = contentSecondary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Secondary Keywords Field (Requirement 12)
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Keywords (optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. quote, dialogue, curly", fontSize = 12.sp) },
                    supportingText = {
                        Text(
                            "Used when searching Shortcut Studio.",
                            fontSize = 10.5.sp,
                            color = contentTertiary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentPrimary,
                        unfocusedBorderColor = subtleBorder,
                        focusedLabelColor = accentPrimary,
                        unfocusedLabelColor = contentSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector (Requirement 13)
                val categoryName = when (category) {
                    DefaultShortcuts.CAT_DIALOGUE -> "Dialogue & Monologue"
                    DefaultShortcuts.CAT_BRACKETS -> "Enclosures & Brackets"
                    DefaultShortcuts.CAT_PUNCTUATION -> "Punctuation & Cadence"
                    DefaultShortcuts.CAT_STRUCTURE -> "Structure & Lists"
                    DefaultShortcuts.CAT_FORMATTING -> "Typography & Formatting"
                    DefaultShortcuts.CAT_SCENE_BREAKS -> "Scene Breaks"
                    DefaultShortcuts.CAT_ARROWS -> "Arrows & Flow"
                    DefaultShortcuts.CAT_MATH -> "Math & Logic"
                    DefaultShortcuts.CAT_CURRENCY -> "Currency & Symbols"
                    else -> "Custom & Snippets"
                }

                Surface(
                    onClick = { showCategoryMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = surfaceLowest,
                    border = BorderStroke(1.dp, subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = contentSecondary
                                )
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select category",
                                tint = contentSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                            modifier = Modifier.background(surfaceRaised)
                        ) {
                            val allCategories = listOf(
                                DefaultShortcuts.CAT_CUSTOM to "Custom & Snippets",
                                DefaultShortcuts.CAT_DIALOGUE to "Dialogue & Monologue",
                                DefaultShortcuts.CAT_BRACKETS to "Enclosures & Brackets",
                                DefaultShortcuts.CAT_PUNCTUATION to "Punctuation & Cadence",
                                DefaultShortcuts.CAT_STRUCTURE to "Structure & Lists",
                                DefaultShortcuts.CAT_FORMATTING to "Typography & Formatting",
                                DefaultShortcuts.CAT_SCENE_BREAKS to "Scene Breaks",
                                DefaultShortcuts.CAT_ARROWS to "Arrows & Flow",
                                DefaultShortcuts.CAT_MATH to "Math & Logic",
                                DefaultShortcuts.CAT_CURRENCY to "Currency & Symbols"
                            )
                            allCategories.forEach { (catId, catLabel) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = catLabel,
                                            fontWeight = if (category == catId) FontWeight.Bold else FontWeight.Normal,
                                            color = if (category == catId) accentPrimary else contentPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        category = catId
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Compact Lightbulb Guide (Requirement 11)
                Surface(
                    onClick = { isHintExpanded = !isHintExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isHintExpanded) surfaceRaised else surfaceLowest,
                    border = BorderStroke(1.dp, subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "How shortcuts work",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentPrimary
                                )
                            }
                            Icon(
                                imageVector = if (isHintExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = contentSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isHintExpanded,
                            enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(100)) + shrinkVertically(tween(160))
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "• Enclose: Wraps selected text with opening + closing delimiters. Smart Enter moves smoothly outside the pair on the same line.",
                                    fontSize = 11.sp,
                                    color = contentSecondary,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    text = "• Prefix: Adds text at the line start. Smart Enter automatically continues sequence (1. → 2.) and double-Enter terminates list.",
                                    fontSize = 11.sp,
                                    color = contentSecondary,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    text = "• Insert: Places text or symbol directly at the cursor.",
                                    fontSize = 11.sp,
                                    color = contentSecondary,
                                    lineHeight = 15.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { isHintExpanded = false },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Got it", fontSize = 11.5.sp, color = accentPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pinned Bottom Action Bar (Requirements 17, 18)
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = subtleBorder.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Cancel", color = contentSecondary)
                }

                Button(
                    onClick = {
                        if (isValid) {
                            val parsedKeywords = keywordsText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            onSave(
                                ShortcutAction(
                                    id = existing?.id ?: (System.currentTimeMillis().toString() + Math.random().toString().takeLast(4)),
                                    label = label.trim(),
                                    kind = kind,
                                    payload = payload,
                                    closing = if (kind == "pair" || kind == "wrap") closing.ifBlank { null } else null,
                                    category = category,
                                    isEnabled = existing?.isEnabled ?: true,
                                    keywords = parsedKeywords
                                )
                            )
                        }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPrimary,
                        contentColor = onAccent,
                        disabledContainerColor = accentPrimary.copy(alpha = 0.35f),
                        disabledContentColor = onAccent.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
