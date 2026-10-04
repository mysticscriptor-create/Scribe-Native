package com.primaloptima.scribe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ui.components.FrostedBottomSheet
import com.primaloptima.scribe.ui.components.LocalFrostedSheetDismiss
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeShapeTokens
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.DefaultShortcuts
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.util.resolvedIcon
import com.primaloptima.scribe.util.resolvedLabel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

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

// Human-friendly title mapper delegating to resolvedLabel()
private fun getHumanFriendlyTitle(shortcut: ShortcutAction): String = shortcut.resolvedLabel()

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
    var workingBarShortcuts by remember(activeBarShortcuts) {
        mutableStateOf(activeBarShortcuts.toList())
    }

    // Intercept Back while in Edit Mode to cleanly cancel and restore order
    BackHandler(enabled = isEditMode) {
        isEditMode = false
        workingBarShortcuts = activeBarShortcuts.toList()
    }

    // ── Smart Scrolling FAB Controller ───────────────────────────────────────
    val density = LocalDensity.current
    var isFabExtended by remember { mutableStateOf(true) }
    var isFabVisible by remember { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var previousFirstVisibleIndex by remember { mutableIntStateOf(0) }
    var accumulatedDownScroll by remember { mutableFloatStateOf(0f) }
    var accumulatedUpScroll by remember { mutableFloatStateOf(0f) }

    val thresholdContractPx = with(density) { 8.dp.toPx() }
    val thresholdHidePx = with(density) { 64.dp.toPx() }
    val thresholdShowPx = with(density) { 12.dp.toPx() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset <= 10) {
                    isFabExtended = true
                    isFabVisible = true
                    accumulatedDownScroll = 0f
                    accumulatedUpScroll = 0f
                } else {
                    val delta = if (currentIndex != previousFirstVisibleIndex) {
                        (currentIndex - previousFirstVisibleIndex) * 200 + (currentOffset - previousScrollOffset)
                    } else {
                        currentOffset - previousScrollOffset
                    }

                    if (delta > 2) { // Scrolling down
                        accumulatedUpScroll = 0f
                        accumulatedDownScroll += delta
                        if (accumulatedDownScroll > thresholdContractPx) {
                            isFabExtended = false
                        }
                        if (accumulatedDownScroll > thresholdHidePx) {
                            isFabVisible = false
                        }
                    } else if (delta < -2) { // Scrolling up
                        accumulatedDownScroll = 0f
                        accumulatedUpScroll += -delta
                        if (accumulatedUpScroll > thresholdShowPx) {
                            isFabVisible = true
                            if (accumulatedUpScroll > thresholdContractPx * 3) {
                                isFabExtended = true
                            }
                        }
                    }
                }
                previousFirstVisibleIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    // ── Elastic Stretch Overscroll Physics ───────────────────────────────────
    val rawOverscrollY = remember { Animatable(0f) }
    val maxRubberBandPx = with(density) { 54.dp.toPx() }
    val dampedOverscrollY = remember(rawOverscrollY.value, maxRubberBandPx) {
        val raw = rawOverscrollY.value
        if (abs(raw) < 0.5f) {
            0f
        } else {
            sign(raw) * maxRubberBandPx * (1f - exp(-abs(raw) / (maxRubberBandPx * 2.0f)))
        }
    }
    val rubberBandScrollConnection = remember(coroutineScope, maxRubberBandPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = rawOverscrollY.value
                if (abs(current) > 0.5f && available.y != 0f && sign(available.y) != sign(current)) {
                    val next = if (current > 0f) {
                        (current + available.y * 1.4f).coerceAtLeast(0f)
                    } else {
                        (current + available.y * 1.4f).coerceAtMost(0f)
                    }
                    coroutineScope.launch { rawOverscrollY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y != 0f && source == NestedScrollSource.UserInput) {
                    val next = (rawOverscrollY.value + available.y)
                        .coerceIn(-maxRubberBandPx * 3.5f, maxRubberBandPx * 3.5f)
                    coroutineScope.launch { rawOverscrollY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(rawOverscrollY.value) > 0.5f) {
                    coroutineScope.launch {
                        rawOverscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(rawOverscrollY.value) > 0.5f) {
                    coroutineScope.launch {
                        rawOverscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
                return available
            }
        }
    }

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

            action.resolvedLabel().lowercase().contains(cleanQuery) ||
                action.resolvedIcon().lowercase().contains(cleanQuery) ||
                action.payload.lowercase().contains(cleanQuery) ||
                (action.closing?.lowercase()?.contains(cleanQuery) == true) ||
                action.kind.lowercase().contains(cleanQuery) ||
                action.category.lowercase().contains(cleanQuery) ||
                action.keywords.any { it.lowercase().contains(cleanQuery) }
        }
    }
    val shortcutsByCategory = remember(filteredShortcuts) {
        filteredShortcuts.groupBy { it.category }
    }
    val allShortcutsByCategory = remember(shortcuts) {
        shortcuts.groupBy { it.category }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            ScribeTopBar(
                title = "Shortcut Studio",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = {
                    if (isEditMode) {
                        isEditMode = false
                        workingBarShortcuts = activeBarShortcuts.toList()
                    } else {
                        onBack()
                    }
                },
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
            AnimatedVisibility(
                visible = isFabVisible && !isEditMode,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(150))
            ) {
                Surface(
                    onClick = { isCreatingNew = true },
                    shape = CircleShape,
                    color = accentPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                )
                            )
                            .padding(
                                horizontal = if (isFabExtended) 18.dp else 16.dp,
                                vertical = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Shortcut",
                            tint = onAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        AnimatedVisibility(
                            visible = isFabExtended,
                            enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(),
                            exit = fadeOut(animationSpec = tween(140)) + shrinkHorizontally()
                        ) {
                            Text(
                                text = "Create Shortcut",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = onAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        containerColor = canvasBg,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { paddingValues ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(rubberBandScrollConnection)
                    .graphicsLayer { translationY = dampedOverscrollY },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── 1. YOUR WRITING BAR (Hero Section) ───────────────────────────
                item(key = "hero_writing_bar") {
                    WritingBarHeroSection(
                        activeShortcuts = activeBarShortcuts,
                        isEditMode = isEditMode,
                        workingShortcuts = workingBarShortcuts,
                        onStartEdit = {
                            workingBarShortcuts = activeBarShortcuts.toList()
                            isEditMode = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onCommitEdit = {
                            vm.commitActiveBarOrder(workingBarShortcuts.map { it.id })
                            isEditMode = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onCancelEdit = {
                            workingBarShortcuts = activeBarShortcuts.toList()
                            isEditMode = false
                        },
                        onRemoveShortcut = { shortcut ->
                            workingBarShortcuts = workingBarShortcuts.filter { it.id != shortcut.id }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onReorderShortcuts = { from, to ->
                            if (from in workingBarShortcuts.indices && to in workingBarShortcuts.indices && from != to) {
                                val updated = workingBarShortcuts.toMutableList()
                                val item = updated.removeAt(from)
                                updated.add(to, item)
                                workingBarShortcuts = updated
                            }
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
                    val categoryShortcuts = shortcutsByCategory[catMeta.id] ?: emptyList()
                    val allInThisCat = allShortcutsByCategory[catMeta.id] ?: emptyList()
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

                                    // Smooth Animated Expanded Shortcut List
                                    AnimatedVisibility(
                                        visible = !isCollapsed,
                                        enter = expandVertically(
                                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                                        ) + fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)),
                                        exit = shrinkVertically(
                                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                                    ) {
                                        Column {
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

                                                visibleShortcuts.forEachIndexed { index, shortcut ->
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
                                                    if (index < visibleShortcuts.lastIndex) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(start = 52.dp, end = 6.dp),
                                                            thickness = 0.5.dp,
                                                            color = subtleBorder.copy(alpha = 0.5f)
                                                        )
                                                    }
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
                }

                // Bottom spacing for extended FAB
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(84.dp))
                }
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
    }

    deleteCandidate?.let { candidate ->
        FrostedDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Shortcut?") },
            text = { Text("Delete \"" + candidate.resolvedLabel() + "\"? This custom shortcut cannot be undone.") },
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
    workingShortcuts: List<ShortcutAction>,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onRemoveShortcut: (ShortcutAction) -> Unit,
    onReorderShortcuts: (from: Int, to: Int) -> Unit,
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
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Leading Rounded Icon + Title & Count + Trailing Actions
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
                            .background(if (isEditMode) accentPrimary.copy(alpha = 0.12f) else glyphBoxBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Tune else Icons.Default.ViewCompact,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isEditMode) accentPrimary else contentPrimary
                        )
                    }
                    Column {
                        Text(
                            text = if (isEditMode) "Edit Writing Bar" else "Your Writing Bar",
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentPrimary
                        )
                        Text(
                            text = if (isEditMode) {
                                "Drag handle to reorder • Tap × to remove"
                            } else {
                                if (activeShortcuts.isNotEmpty()) "${activeShortcuts.size} shortcuts" else "0 shortcuts"
                            },
                            fontSize = 12.sp,
                            color = if (isEditMode) accentPrimary else contentSecondary
                        )
                    }
                }

                // Trailing Buttons
                if (!isEditMode) {
                    if (activeShortcuts.isNotEmpty()) {
                        Surface(
                            onClick = onStartEdit,
                            shape = CircleShape,
                            color = glyphBoxBg,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = contentPrimary
                                )
                                Text(
                                    text = "Edit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentPrimary
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(
                            onClick = onCancelEdit,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentSecondary
                            )
                        }
                        Surface(
                            onClick = onCommitEdit,
                            shape = CircleShape,
                            color = accentPrimary,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, accentPrimary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = onAccent
                                )
                                Text(
                                    text = "Done",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onAccent
                                )
                            }
                        }
                    }
                }
            }

            // Body: Compact Horizontal Bar vs Expanded Reorderable Studio
            if (!isEditMode) {
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
                            // Undo Pill
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
                            // Redo Pill
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
                            activeShortcuts.forEach { shortcut ->
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
                                            text = shortcut.resolvedIcon(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = contentPrimary,
                                            maxLines = 1
                                        )
                                    }
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
            } else {
                WritingBarEditCardContent(
                    workingShortcuts = workingShortcuts,
                    onRemoveShortcut = onRemoveShortcut,
                    onReorderShortcuts = onReorderShortcuts
                )
            }
        }
    }
}

// ── Reorderable Writing Bar Edit Card Content ─────────────────────────────────
@Composable
private fun WritingBarEditCardContent(
    workingShortcuts: List<ShortcutAction>,
    onRemoveShortcut: (ShortcutAction) -> Unit,
    onReorderShortcuts: (from: Int, to: Int) -> Unit
) {
    val colors = ScribeTheme.colors
    val subtleBorder = colors.borders.subtle
    val contentSecondary = colors.content.secondary
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }

    val innerBarBg = colors.surfaces.surfaceLowest.copy(alpha = 0.65f)

    if (workingShortcuts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No shortcuts in Writing Bar. Select shortcuts below to add them.",
                fontSize = 13.sp,
                color = contentSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Surface(
            shape = ScribeShapeTokens.CardMedium,
            color = innerBarBg,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, subtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    items = workingShortcuts,
                    key = { _, item -> item.id }
                ) { index, shortcut ->
                    val isDragging = draggingIndex == index

                    val itemModifier = if (isDragging) {
                        Modifier
                            .zIndex(10f)
                            .graphicsLayer {
                                translationY = dragOffsetY
                                shadowElevation = 8.dp.toPx()
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                    } else {
                        Modifier
                            .zIndex(1f)
                            .animateItem()
                    }

                    ReorderableWritingBarItemRow(
                        modifier = itemModifier,
                        shortcut = shortcut,
                        isDragging = isDragging,
                        onDragStart = {
                            draggingIndex = index
                            dragStartIndex = index
                            dragOffsetY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { dragAmountY ->
                            dragOffsetY += dragAmountY
                            val currIdx = draggingIndex ?: return@ReorderableWritingBarItemRow

                            // Edge autoscroll if near bounds
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val currentItemInfo = visibleItems.firstOrNull { it.index == currIdx }
                            if (currentItemInfo != null) {
                                val itemTop = currentItemInfo.offset + dragOffsetY
                                val itemBottom = itemTop + currentItemInfo.size
                                val viewportHeight = listState.layoutInfo.viewportSize.height
                                if (itemTop < 30f) {
                                    coroutineScope.launch { listState.scrollBy(-16f) }
                                } else if (itemBottom > viewportHeight - 30f) {
                                    coroutineScope.launch { listState.scrollBy(16f) }
                                }
                            }

                            // Dynamic slot crossing threshold (50% of row height)
                            if (dragOffsetY > rowHeightPx * 0.5f && currIdx < workingShortcuts.lastIndex) {
                                val target = currIdx + 1
                                onReorderShortcuts(currIdx, target)
                                draggingIndex = target
                                dragOffsetY -= rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -rowHeightPx * 0.5f && currIdx > 0) {
                                val target = currIdx - 1
                                onReorderShortcuts(currIdx, target)
                                draggingIndex = target
                                dragOffsetY += rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            draggingIndex = null
                            dragStartIndex = null
                            dragOffsetY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragCancel = {
                            val start = dragStartIndex
                            val curr = draggingIndex
                            if (start != null && curr != null && start != curr) {
                                onReorderShortcuts(curr, start)
                            }
                            draggingIndex = null
                            dragStartIndex = null
                            dragOffsetY = 0f
                        },
                        onRemove = { onRemoveShortcut(shortcut) }
                    )

                    if (index < workingShortcuts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp, end = 12.dp),
                            thickness = 0.5.dp,
                            color = subtleBorder.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

// ── Reorderable Writing Bar Item Row ──────────────────────────────────────────
@Composable
private fun ReorderableWritingBarItemRow(
    modifier: Modifier = Modifier,
    shortcut: ShortcutAction,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = ScribeTheme.colors
    val contentPrimary = colors.content.primary
    val contentSecondary = colors.content.secondary
    val contentTertiary = colors.content.tertiary
    val accentPrimary = colors.interaction.primary
    val cardBg = colors.surfaces.surface
    val badgeBg = colors.interaction.primaryContainer
    val badgeContent = colors.interaction.onPrimaryContainer

    val formatDetail = remember(shortcut.kind, shortcut.payload, shortcut.closing) {
        when (shortcut.kind) {
            "pair", "wrap" -> {
                val openClean = shortcut.payload.replace("
", "↵").replace("
", "↵").replace("", "↵")
                val closeRaw = shortcut.closing?.ifBlank { null } ?: shortcut.payload
                val closeClean = closeRaw.replace("
", "↵").replace("
", "↵").replace("", "↵")
                "$openClean $closeClean".trim()
            }
            else -> shortcut.payload.replace("
", "↵").replace("
", "↵").replace("", "↵").trim()
        }
    }
    val displayKind = remember(shortcut.kind) {
        if (shortcut.kind == "wrap") "Pair" else shortcut.kind.replaceFirstChar { it.uppercase() }
    }

    Surface(
        color = if (isDragging) cardBg else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle + Glyph Badge + Label
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle touch zone
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(shortcut.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        modifier = Modifier.size(20.dp),
                        tint = if (isDragging) accentPrimary else contentTertiary
                    )
                }

                // Typography semantic glyph badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortcut.resolvedIcon(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = badgeContent,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }

                // Label and kind
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shortcut.resolvedLabel(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = contentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$displayKind • $formatDetail",
                        fontSize = 11.sp,
                        color = contentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing Remove Button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from writing bar",
                    modifier = Modifier.size(16.dp),
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
        animationSpec = tween(260, easing = FastOutSlowInEasing),
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
    val badgeBg = colors.interaction.primaryContainer
    val badgeContent = colors.interaction.onPrimaryContainer
    var showMenu by remember { mutableStateOf(false) }

    val formatDetail = remember(shortcut.kind, shortcut.payload, shortcut.closing) {
        when (shortcut.kind) {
            "pair", "wrap" -> {
                val openClean = shortcut.payload.replace("\r\n", "↵").replace("\n", "↵").replace("\r", "↵")
                val closeRaw = shortcut.closing?.ifBlank { null } ?: shortcut.payload
                val closeClean = closeRaw.replace("\r\n", "↵").replace("\n", "↵").replace("\r", "↵")
                "$openClean $closeClean".trim()
            }
            else -> shortcut.payload.replace("\r\n", "↵").replace("\n", "↵").replace("\r", "↵").trim()
        }
    }
    val displayKind = remember(shortcut.kind) {
        if (shortcut.kind == "wrap") "Pair" else shortcut.kind.replaceFirstChar { it.uppercase() }
    }

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
            // Glyph Badge (Prominent rounded box ~40dp) — displays the shortcut symbol using semantic tokens
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.resolvedIcon(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = badgeContent,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            // Title & Subtitle (Matching reference e.g., "Curly double quotes", "Pair • “ ”")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.resolvedLabel(),
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = contentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = contentPrimary
                            )
                        }
                    )

                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            // Circular toggle button: Active (filled circle with checkmark) vs Inactive (subtle circle with plus)
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (isActive) accentPrimary else colors.surfaces.surfaceLowest,
                border = BorderStroke(
                    0.5.dp,
                    if (isActive) accentPrimary else subtleBorder
                ),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isActive) "Enabled" else "Disabled",
                        tint = if (isActive) onAccent else contentSecondary,
                        modifier = Modifier.size(15.dp)
                    )
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

// ── Format Preview Token (replaces line breaks with ↵ symbol so Preview height never changes) ──
private fun formatPreviewDisplay(raw: String, fallback: String): String {
    if (raw.isEmpty()) return fallback
    return raw
        .replace("\r\n", "↵")
        .replace("\n", "↵")
        .replace("\r", "↵")
}

// ── Animated Type Selector Option Card (120fps Spring Physics) ────────────────
@Composable
private fun AnimatedShortcutTypeCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val colors = ScribeTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.interaction.primaryContainer else colors.surfaces.surfaceRaised,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "typeCardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.interaction.primary else colors.borders.normal,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "typeCardBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) colors.interaction.primary else colors.content.secondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "typeCardIcon"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) colors.interaction.primary else colors.content.primary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "typeCardTitle"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.985f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "typeCardScale"
    )

    Surface(
        onClick = onClick,
        shape = ScribeShapeTokens.CardSmall,
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            iconContent(iconTint)
            Text(
                text = title,
                fontSize = 11.5.sp,
                lineHeight = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
                maxLines = 1
            )
        }
    }
}

// ── Compact Semantic Field Input ──────────────────────────────────────────────
@Composable
private fun ShortcutFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    textAlign: TextAlign = TextAlign.Start,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val colors = ScribeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor = colors.surfaces.surfaceRaised
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.interaction.primary else colors.borders.normal,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fieldBorderColor"
    )
    val borderWidth = if (isFocused) 1.2.dp else 1.dp
    val textColor = colors.content.primary
    val placeholderColor = colors.content.tertiary
    val fieldShape = ScribeShapeTokens.Field

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            color = textColor,
            fontWeight = FontWeight.Normal,
            textAlign = textAlign
        ),
        cursorBrush = SolidColor(colors.interaction.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .background(bgColor, fieldShape)
                    .border(borderWidth, borderColor, fieldShape)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            color = placeholderColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = textAlign
                        )
                    }
                    innerTextField()
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
        }
    )
}

// ── Inline Section Header with Optional Divider & Hint ────────────────────────
@Composable
private fun SheetSectionLabelRow(
    title: String,
    inlineHint: String? = null
) {
    val colors = ScribeTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.content.secondary,
            letterSpacing = 0.9.sp,
            maxLines = 1
        )
        if (!inlineHint.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(10.dp)
                    .background(colors.borders.normal)
            )
            Text(
                text = inlineHint,
                fontSize = 10.5.sp,
                color = colors.content.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Create / Edit Shortcut Bottom Sheet (Semantic & Compact Redesign) ─────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CreateOrEditShortcutSheet(
    existing: ShortcutAction?,
    onDismiss: () -> Unit,
    onSave: (ShortcutAction) -> Unit
) {
    // 2: Separate `label` (description in Shortcut Studio) from `icon` (actual shortcut symbol)
    var label by remember(existing) { mutableStateOf(existing?.resolvedLabel() ?: "") }
    var kind by remember(existing) {
        mutableStateOf(
            if (existing?.kind == "wrap") "pair" else (existing?.kind ?: "pair")
        )
    }
    var payload by remember(existing) { mutableStateOf(existing?.payload ?: "") }
    var closing by remember(existing) { mutableStateOf(existing?.closing ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: DefaultShortcuts.CAT_CUSTOM) }

    // 2: Auto-detect icon from user's inserted text across all three types ("pair", "prefix", "insert")
    val autoDetectedIcon = remember(kind, payload, closing) {
        DefaultShortcuts.autoDetectIcon(kind, payload, closing)
    }
    var iconText by remember(existing) {
        mutableStateOf(existing?.resolvedIcon() ?: "")
    }
    var isIconManuallyOverridden by remember(existing) {
        mutableStateOf(false)
    }

    // Keep iconText synchronized with autoDetectedIcon unless the user manually edited the ICON field
    LaunchedEffect(kind, payload, closing) {
        if (!isIconManuallyOverridden) {
            val existingDefaultMatches = existing != null &&
                existing.payload == payload &&
                (existing.closing ?: "") == closing &&
                (if (existing.kind == "wrap") "pair" else existing.kind) == kind
            iconText = if (existingDefaultMatches) {
                existing!!.resolvedIcon()
            } else {
                autoDetectedIcon
            }
        }
    }

    // 2G: Remove hardcoded keywords; only populate if editing an existing shortcut
    val initialKeywords = remember(existing) {
        existing?.keywords ?: emptyList()
    }
    val keywordsList = remember(existing) { mutableStateListOf<String>().apply { addAll(initialKeywords) } }
    var newKeywordText by remember(existing) { mutableStateOf("") }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val effectiveIcon = iconText.trim().ifBlank { autoDetectedIcon.ifBlank { "•" } }
    val isValid = label.trim().isNotBlank() && payload.isNotEmpty()
    val isEditMode = existing != null
    val isEnclose = (kind == "pair" || kind == "wrap")

    // 1: Semantic Color Tokens from ScribeTheme
    val colors = ScribeTheme.colors
    val metrics = ScribeTheme.metrics
    val sheetSurface = colors.surfaces.surface
    val surfaceLowest = colors.surfaces.surfaceLowest
    val cardBg = colors.surfaces.surfaceRaised
    val menuSurface = colors.surfaces.surfaceOverlay
    val selectedPillBg = colors.surfaces.surfaceSelected
    val textPrimary = colors.content.primary
    val textSecondary = colors.content.secondary
    val textTertiary = colors.content.tertiary
    val accentPrimary = colors.interaction.primary
    val accentContainer = colors.interaction.primaryContainer
    val onAccent = colors.content.onAccent
    val borderSubtle = colors.borders.subtle
    val borderNormal = colors.borders.normal
    val litBulbColor = colors.semantic.warning
    val litBulbContainer = colors.semantic.warningContainer
    val barShadowColor = colors.content.primary.copy(alpha = if (colors.isDark) 0.22f else 0.06f)

    // 1: Responsive layout detection (side-by-side fields in landscape or wide tablet screens)
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.screenWidthDp >= 520 ||
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 1 & 3: Only enable scrolling when the screen is too short / landscape so all components expand first
    val scrollState = rememberScrollState()
    val isScrollNeeded by remember {
        derivedStateOf { scrollState.maxValue > 0 }
    }
    val rawOverscrollY = remember { Animatable(0f) }
    val maxRubberBandPx = with(density) { 48.dp.toPx() }
    val dampedOverscrollY = remember(rawOverscrollY.value, maxRubberBandPx) {
        val raw = rawOverscrollY.value
        if (abs(raw) < 0.5f) {
            0f
        } else {
            sign(raw) * maxRubberBandPx * (1f - exp(-abs(raw) / (maxRubberBandPx * 2.0f)))
        }
    }

    val rubberBandScrollConnection = remember(coroutineScope, maxRubberBandPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = rawOverscrollY.value
                if (abs(current) > 0.5f && available.y != 0f && sign(available.y) != sign(current)) {
                    val next = if (current > 0f) {
                        (current + available.y * 1.4f).coerceAtLeast(0f)
                    } else {
                        (current + available.y * 1.4f).coerceAtMost(0f)
                    }
                    coroutineScope.launch { rawOverscrollY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y != 0f) {
                    if (source == NestedScrollSource.UserInput) {
                        val next = (rawOverscrollY.value + available.y)
                            .coerceIn(-maxRubberBandPx * 3.5f, maxRubberBandPx * 3.5f)
                        coroutineScope.launch { rawOverscrollY.snapTo(next) }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(rawOverscrollY.value) > 0.5f) {
                    coroutineScope.launch {
                        rawOverscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (abs(rawOverscrollY.value) > 0.5f) {
                    coroutineScope.launch {
                        rawOverscrollY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
                return available
            }
        }
    }

    val horizontalOverscrollBlocker = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }

    val keywordScrollState = rememberScrollState()
    val keywordFocusRequester = remember { FocusRequester() }
    val keywordInteractionSource = remember { MutableInteractionSource() }
    val isKeywordFocused by keywordInteractionSource.collectIsFocusedAsState()

    val commitKeyword: () -> Unit = {
        val cleaned = newKeywordText.replace("\n", "").trim()
        if (cleaned.isNotEmpty() && !keywordsList.contains(cleaned)) {
            keywordsList.add(cleaned)
        }
        newKeywordText = ""
        coroutineScope.launch {
            keywordScrollState.animateScrollTo(keywordScrollState.maxValue)
        }
    }

    FrostedBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        modifier = Modifier.wrapContentHeight()
    ) {
        val dismissSheet = LocalFrostedSheetDismiss.current ?: onDismiss

        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            // ── 2A & 2D: Compact Top Bar with Smaller Title & Subtle Semantic Elevation Shadow ──
            Surface(
                color = sheetSurface,
                shadowElevation = metrics.elevationLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Compact Drag Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp, bottom = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 3.5.dp)
                                .background(borderNormal, ScribeShapeTokens.Handle)
                        )
                    }

                    // Compact Title Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditMode) "Edit Shortcut" else "Create Shortcut",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Surface(
                            onClick = dismissSheet,
                            shape = CircleShape,
                            color = surfaceLowest,
                            border = BorderStroke(metrics.borderHairline, borderSubtle),
                            modifier = Modifier.size(25.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = textSecondary,
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = metrics.borderHairline,
                        color = borderSubtle.copy(alpha = 0.75f)
                    )
                }
            }

            // Middle Body: Expands to show all components; only scrolls if screen is too short / landscape
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isScrollNeeded) Modifier.nestedScroll(rubberBandScrollConnection) else Modifier
                        )
                        .verticalScroll(scrollState, enabled = isScrollNeeded)
                        .graphicsLayer {
                            translationY = if (isScrollNeeded) dampedOverscrollY else 0f
                        }
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // ── 3: Fixed-Height PREVIEW Card (Line Breaks Shown as ↵ Symbol) ──────────
                    Surface(
                        shape = ScribeShapeTokens.CardSmall,
                        color = surfaceLowest,
                        border = BorderStroke(1.dp, borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.align(Alignment.TopStart)
                            )

                            Surface(
                                shape = ScribeShapeTokens.Pill,
                                color = cardBg,
                                border = BorderStroke(1.dp, borderNormal),
                                shadowElevation = metrics.elevationLow,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .height(28.dp)
                                    .widthIn(max = 260.dp)
                            ) {
                                AnimatedContent(
                                    targetState = kind,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(150)) + scaleIn(
                                            initialScale = 0.94f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                        )) togetherWith (fadeOut(animationSpec = tween(100)) + scaleOut(
                                            targetScale = 0.94f,
                                            animationSpec = tween(100)
                                        ))
                                    },
                                    contentAlignment = Alignment.Center,
                                    label = "previewKindAnim"
                                ) { previewKind ->
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when (previewKind) {
                                            "pair", "wrap" -> {
                                                val open = formatPreviewDisplay(payload, "“")
                                                val closeFallback = if (payload.isNotEmpty()) formatPreviewDisplay(payload, "”") else "”"
                                                val close = if (closing.isNotEmpty()) formatPreviewDisplay(closing, closeFallback) else closeFallback
                                                Text(
                                                    text = open,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = accentPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 64.dp)
                                                )
                                                Text(
                                                    text = "selected text",
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                    color = textSecondary,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = close,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = accentPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 64.dp)
                                                )
                                            }
                                            "prefix" -> {
                                                val p = formatPreviewDisplay(payload, "• ")
                                                Text(
                                                    text = p,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = accentPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 90.dp)
                                                )
                                                Text(
                                                    text = "selected text",
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                    color = textSecondary,
                                                    maxLines = 1
                                                )
                                            }
                                            else -> {
                                                val ins = formatPreviewDisplay(payload, "…")
                                                Text(
                                                    text = "selected text",
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                    color = textSecondary,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = ins,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = accentPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 90.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 4 & 5: Animated TYPE Selector + Narrow Hint Card ─────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SheetSectionLabelRow(title = "TYPE")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedShortcutTypeCard(
                                title = "Enclose",
                                isSelected = isEnclose,
                                onClick = { kind = "pair" },
                                modifier = Modifier.weight(1f)
                            ) { tint ->
                                Text(
                                    text = "“ ”",
                                    fontSize = 13.5.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = tint
                                )
                            }

                            AnimatedShortcutTypeCard(
                                title = "Prefix",
                                isSelected = (kind == "prefix"),
                                onClick = { kind = "prefix" },
                                modifier = Modifier.weight(1f)
                            ) { tint ->
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            AnimatedShortcutTypeCard(
                                title = "Insert",
                                isSelected = (kind == "insert"),
                                onClick = { kind = "insert" },
                                modifier = Modifier.weight(1f)
                            ) { tint ->
                                Text(
                                    text = "I",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 13.5.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tint
                                )
                            }
                        }

                        // 5: Narrow Hint Card below Shortcut Types with tight border padding & lit bulb
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = surfaceLowest,
                            border = BorderStroke(0.5.dp, borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(litBulbContainer.copy(alpha = 0.65f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = litBulbColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                AnimatedContent(
                                    targetState = kind,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(100))
                                    },
                                    label = "narrowHintAnim",
                                    modifier = Modifier.weight(1f)
                                ) { currentKind ->
                                    Text(
                                        text = when (currentKind) {
                                            "pair", "wrap" -> "Wraps selected text in an opening/closing pair. Enter exits the pair automatically."
                                            "prefix" -> "Adds text at the start of the line. Enter continues sequence automatically."
                                            else -> "Inserts text directly at the cursor position."
                                        },
                                        fontSize = 11.sp,
                                        color = textSecondary,
                                        lineHeight = 13.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // ── 1 & 4: Smooth Animated Type Input Fields (First OPENING, then CLOSING) ──
                    AnimatedContent(
                        targetState = if (isEnclose) "pair" else kind,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(160)) + slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )) togetherWith (fadeOut(animationSpec = tween(110)) + slideOutVertically(
                                targetOffsetY = { -it / 6 },
                                animationSpec = tween(110)
                            ))
                        },
                        label = "typeFieldsTransition"
                    ) { targetKind ->
                        when (targetKind) {
                            "pair" -> {
                                val openingField: @Composable (Modifier) -> Unit = { mod ->
                                    Column(
                                        modifier = mod,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        SheetSectionLabelRow(title = "OPENING")

                                        ShortcutFieldInput(
                                            value = payload,
                                            onValueChange = { payload = it },
                                            placeholder = "e.g. “",
                                            trailingContent = {
                                                if (payload.isNotEmpty()) {
                                                    Surface(
                                                        onClick = { payload = "" },
                                                        shape = CircleShape,
                                                        color = surfaceLowest,
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Clear",
                                                                tint = textSecondary,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                val closingField: @Composable (Modifier) -> Unit = { mod ->
                                    Column(
                                        modifier = mod,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        SheetSectionLabelRow(
                                            title = "CLOSING",
                                            inlineHint = "Mirrors opening if blank"
                                        )

                                        ShortcutFieldInput(
                                            value = closing,
                                            onValueChange = { closing = it },
                                            placeholder = if (payload.isNotBlank()) payload else "Mirrors opening",
                                            trailingContent = {
                                                if (closing.isNotEmpty()) {
                                                    Surface(
                                                        onClick = { closing = "" },
                                                        shape = CircleShape,
                                                        color = surfaceLowest,
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Clear",
                                                                tint = textSecondary,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                if (isWideLayout) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        openingField(Modifier.weight(1f))
                                        closingField(Modifier.weight(1f))
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        openingField(Modifier.fillMaxWidth())
                                        closingField(Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            "prefix" -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    SheetSectionLabelRow(title = "PREFIX")

                                    ShortcutFieldInput(
                                        value = payload,
                                        onValueChange = { payload = it },
                                        placeholder = "e.g. - [ ] , 1. , • ",
                                        trailingContent = {
                                            if (payload.isNotEmpty()) {
                                                Surface(
                                                    onClick = { payload = "" },
                                                    shape = CircleShape,
                                                    color = surfaceLowest,
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear",
                                                            tint = textSecondary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            else -> { // insert
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    SheetSectionLabelRow(title = "TEXT TO INSERT")

                                    ShortcutFieldInput(
                                        value = payload,
                                        onValueChange = { payload = it },
                                        placeholder = "e.g. …, —, ©",
                                        trailingContent = {
                                            if (payload.isNotEmpty()) {
                                                Surface(
                                                    onClick = { payload = "" },
                                                    shape = CircleShape,
                                                    color = surfaceLowest,
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear",
                                                            tint = textSecondary,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── 1 & 2: LABEL (Description) & KEYWORDS (Side-by-Side on Wide/Landscape) ──
                    val labelFieldContent: @Composable (Modifier) -> Unit = { mod ->
                        Column(
                            modifier = mod,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SheetSectionLabelRow(
                                title = "LABEL",
                                inlineHint = "Description in studio"
                            )

                            ShortcutFieldInput(
                                value = label,
                                onValueChange = { if (it.length <= 30) label = it },
                                placeholder = "e.g. Curly double quotes",
                                trailingContent = {
                                    Text(
                                        text = "${label.length}/30",
                                        fontSize = 11.sp,
                                        color = textTertiary
                                    )
                                }
                            )
                        }
                    }

                    val keywordsFieldContent: @Composable (Modifier) -> Unit = { mod ->
                        Column(
                            modifier = mod,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SheetSectionLabelRow(
                                title = "KEYWORDS",
                                inlineHint = "Used when searching"
                            )

                            val kwBorderColor by animateColorAsState(
                                targetValue = if (isKeywordFocused) accentPrimary else borderNormal,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "kwBorderColor"
                            )
                            val kwBorderWidth = if (isKeywordFocused) 1.2.dp else 1.dp

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 38.dp)
                                    .background(cardBg, ScribeShapeTokens.Field)
                                    .border(kwBorderWidth, kwBorderColor, ScribeShapeTokens.Field)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        keywordFocusRequester.requestFocus()
                                        coroutineScope.launch {
                                            keywordScrollState.animateScrollTo(keywordScrollState.maxValue)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .nestedScroll(horizontalOverscrollBlocker)
                                    .horizontalScroll(keywordScrollState),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                keywordsList.forEachIndexed { index, kw ->
                                    Surface(
                                        shape = ScribeShapeTokens.Pill,
                                        color = selectedPillBg,
                                        border = BorderStroke(0.5.dp, borderSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = kw,
                                                fontSize = 12.sp,
                                                lineHeight = 15.sp,
                                                color = textPrimary,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove keyword",
                                                tint = textSecondary,
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable { keywordsList.removeAt(index) }
                                            )
                                        }
                                    }
                                }

                                BasicTextField(
                                    value = newKeywordText,
                                    onValueChange = { incoming ->
                                        if (incoming.contains("\n")) {
                                            val parts = incoming.split("\n")
                                            parts.dropLast(1).forEach { part ->
                                                val trimmed = part.trim()
                                                if (trimmed.isNotEmpty() && !keywordsList.contains(trimmed)) {
                                                    keywordsList.add(trimmed)
                                                }
                                            }
                                            newKeywordText = parts.last()
                                            coroutineScope.launch {
                                                keywordScrollState.animateScrollTo(keywordScrollState.maxValue)
                                            }
                                        } else {
                                            newKeywordText = incoming
                                        }
                                    },
                                    singleLine = true,
                                    interactionSource = keywordInteractionSource,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = textPrimary
                                    ),
                                    cursorBrush = SolidColor(accentPrimary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { commitKeyword() }
                                    ),
                                    modifier = Modifier
                                        .widthIn(min = 120.dp)
                                        .focusRequester(keywordFocusRequester)
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.Enter, Key.NumPadEnter -> {
                                                        commitKeyword()
                                                        true
                                                    }
                                                    Key.Backspace -> {
                                                        if (newKeywordText.isEmpty() && keywordsList.isNotEmpty()) {
                                                            keywordsList.removeAt(keywordsList.lastIndex)
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        },
                                    decorationBox = { inner ->
                                        Box(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (newKeywordText.isEmpty()) {
                                                Text(
                                                    text = if (keywordsList.isEmpty()) "Type keyword & press Enter…" else "Add more…",
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 16.sp,
                                                    color = textTertiary,
                                                    maxLines = 1
                                                )
                                            }
                                            inner()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            labelFieldContent(Modifier.weight(1f))
                            keywordsFieldContent(Modifier.weight(1f))
                        }
                    } else {
                        labelFieldContent(Modifier.fillMaxWidth())
                        keywordsFieldContent(Modifier.fillMaxWidth())
                    }

                    // ── 2: CATEGORY Selector + Auto-Detected ICON Field Side-by-Side ────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left: CATEGORY Selector
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SheetSectionLabelRow(title = "CATEGORY")

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
                                shape = ScribeShapeTokens.Field,
                                color = cardBg,
                                border = BorderStroke(1.dp, borderNormal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 38.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentContainer,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    val catMeta = STUDIO_CATEGORIES.find { it.id == category }
                                                    if (catMeta?.iconVector != null) {
                                                        Icon(
                                                            imageVector = catMeta.iconVector,
                                                            contentDescription = null,
                                                            tint = accentPrimary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    } else if (catMeta?.iconGlyph != null) {
                                                        Text(
                                                            text = catMeta.iconGlyph,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = accentPrimary
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.ChatBubbleOutline,
                                                            contentDescription = null,
                                                            tint = accentPrimary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = categoryName,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textPrimary,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Box(contentAlignment = Alignment.CenterEnd) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Select category",
                                                tint = textTertiary,
                                                modifier = Modifier.size(17.dp)
                                            )

                                            DropdownMenu(
                                                expanded = showCategoryMenu,
                                                onDismissRequest = { showCategoryMenu = false },
                                                offset = DpOffset(0.dp, 4.dp),
                                                shape = ScribeShapeTokens.Menu,
                                                containerColor = menuSurface,
                                                border = BorderStroke(1.dp, borderSubtle)
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
                                                    val isSelectedCat = category == catId
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = catLabel,
                                                                fontWeight = if (isSelectedCat) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelectedCat) accentPrimary else textPrimary,
                                                                fontSize = 13.sp
                                                            )
                                                        },
                                                        trailingIcon = if (isSelectedCat) {
                                                            {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = accentPrimary,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        } else null,
                                                        onClick = {
                                                            category = catId
                                                            showCategoryMenu = false
                                                        },
                                                        modifier = if (isSelectedCat) {
                                                            Modifier.background(selectedPillBg.copy(alpha = 0.55f))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Right: Auto-Detected ICON Field (shows actual shortcut symbol)
                        Column(
                            modifier = Modifier.width(114.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SheetSectionLabelRow(
                                title = "ICON",
                                inlineHint = if (!isIconManuallyOverridden) "Auto" else null
                            )

                            ShortcutFieldInput(
                                value = iconText,
                                onValueChange = { updated ->
                                    if (updated.length <= 8) {
                                        iconText = updated
                                        if (updated.isBlank()) {
                                            isIconManuallyOverridden = false
                                        } else {
                                            isIconManuallyOverridden = true
                                        }
                                    }
                                },
                                placeholder = autoDetectedIcon.ifBlank {
                                    when (kind) {
                                        "pair", "wrap" -> "“ ”"
                                        "prefix" -> "•"
                                        else -> "…"
                                    }
                                },
                                textAlign = TextAlign.Center,
                                trailingContent = if (isIconManuallyOverridden && iconText != autoDetectedIcon) {
                                    {
                                        Surface(
                                            onClick = {
                                                isIconManuallyOverridden = false
                                                iconText = autoDetectedIcon
                                            },
                                            shape = CircleShape,
                                            color = surfaceLowest,
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Reset to auto icon",
                                                    tint = textSecondary,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }

                // 2D: Subtle top bar downward shadow gradient using semantic token
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(barShadowColor, Color.Transparent)
                            )
                        )
                )

                // 2D: Subtle bottom bar upward shadow gradient using semantic token
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, barShadowColor)
                            )
                        )
                )
            }

            // ── 2C & 2D: Compact Bottom Action Bar with Subtle Semantic Elevation Shadow ──
            Surface(
                color = sheetSurface,
                shadowElevation = metrics.elevationMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = metrics.borderHairline,
                        color = borderSubtle.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = dismissSheet,
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (isValid) {
                                    val finalKeywords = keywordsList.toMutableList()
                                    val pendingKw = newKeywordText.trim()
                                    if (pendingKw.isNotEmpty() && !finalKeywords.contains(pendingKw)) {
                                        finalKeywords.add(pendingKw)
                                    }
                                    onSave(
                                        ShortcutAction(
                                            id = existing?.id ?: (System.currentTimeMillis().toString() + Math.random().toString().takeLast(4)),
                                            label = label.trim(),
                                            icon = effectiveIcon,
                                            kind = kind,
                                            payload = payload,
                                            closing = if (kind == "pair" || kind == "wrap") closing.ifBlank { null } else null,
                                            category = category,
                                            isEnabled = existing?.isEnabled ?: true,
                                            keywords = finalKeywords
                                        )
                                    )
                                }
                            },
                            enabled = isValid,
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentPrimary,
                                contentColor = onAccent,
                                disabledContainerColor = accentPrimary.copy(alpha = 0.35f),
                                disabledContentColor = onAccent.copy(alpha = 0.55f)
                            ),
                            shape = ScribeShapeTokens.Pill,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        }
    }
}
