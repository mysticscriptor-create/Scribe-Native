package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FullScreenImageViewer
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.frostedChip
import com.primaloptima.scribe.ui.theme.frostedSearchBox
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.viewmodel.SheetsViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
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

// ── Category Pill Component ───────────────────────────────────────────────────

@Composable
private fun CategoryPill(
    key: String,
    label: String,
    icon: ImageVector,
    color: Color,
    count: Int,
    selected: Boolean,
    hazeState: HazeState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier
            .height(36.dp)
            .frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = selected)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (count > 0 && key != "more") "$label ($count)" else label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val focusManager = LocalFocusManager.current
    val allEntries by vm.allEntries.collectAsStateWithLifecycle()

    val categoryKeys = remember { listOf("All", "character", "location", "faction", "item", "lore", "timeline") }
    val pagerState = rememberPagerState(initialPage = 0) { categoryKeys.size }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(SheetsViewModel.SortOption.UPDATED_DESC) }
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }

    var showCreateSheet by remember { mutableStateOf(false) }
    var showTagFilterSheet by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Ensure keyboard does not open automatically on launch
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        if (openCreateOnLaunch) showCreateSheet = true
    }

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

    val hazeState = LocalHazeState.current

    // ── Multi-Stage Folding Animation Setup ───────────────────────────────────
    val totalTabsHeightDp = 120.dp // 36dp * 3 + 6dp * 2
    val density = LocalDensity.current
    val maxCollapsePx = with(density) { (totalTabsHeightDp + 8.dp).toPx() }
    val collapseOffset = remember { Animatable(0f) }

    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // delta < 0 means scrolling up (content moving up, collapsing tabs)
                if (delta < 0f && collapseOffset.value < maxCollapsePx) {
                    val newOffset = (collapseOffset.value - delta).coerceIn(0f, maxCollapsePx)
                    val consumedY = newOffset - collapseOffset.value
                    scope.launch { collapseOffset.snapTo(newOffset) }
                    return Offset(0f, -consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // delta > 0 means scrolling down (pulling down, expanding tabs)
                if (delta > 0f && collapseOffset.value > 0f) {
                    val newOffset = (collapseOffset.value - delta).coerceIn(0f, maxCollapsePx)
                    val consumedY = collapseOffset.value - newOffset
                    scope.launch { collapseOffset.snapTo(newOffset) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val currentProg = (collapseOffset.value / maxCollapsePx).coerceIn(0f, 1f)
                val snapPoints = listOf(0f, 0.333f, 0.666f, 1.0f)
                val targetProg = snapPoints.minByOrNull { kotlin.math.abs(it - currentProg) } ?: 0f
                val targetPx = targetProg * maxCollapsePx
                collapseOffset.animateTo(
                    targetValue = targetPx,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                return Velocity.Zero
            }
        }
    }

    val collapseProgress = (collapseOffset.value / maxCollapsePx).coerceIn(0f, 1f)
    val currentTabsContainerHeight = (totalTabsHeightDp * (1f - collapseProgress)).coerceAtLeast(0.dp)

    // FAB visibility: Smoothly minimize and disappear on slight scroll
    val isFabVisible by remember { derivedStateOf { collapseOffset.value < 12f } }

    val currentSelectedCategory = categoryKeys.getOrElse(pagerState.currentPage) { "All" }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
                    topBar = {
                        ScribeTopBar(
                            title = "World Building Sheets",
                            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                            onNavigationClick = onBack
                        )
                    },
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = isFabVisible,
                            enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                            exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                        ) {
                            ScribeSingleFab(
                                icon = Icons.Default.Add,
                                contentDescription = "Add Entry",
                                onClick = { showCreateSheet = true }
                            )
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // ── Compact Search & Sort & Filter Row ─────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Compact Marquee Search Box
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(LocalAccentColor.current),
                                decorationBox = { innerTextField ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .frostedSearchBox(hazeState, shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search sheets, attributes, backstory, tags, lore…",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    modifier = Modifier.basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        velocity = 28.dp
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Tag Filter Trigger Chip
                            val hasTagFilters = selectedTags.isNotEmpty()
                            FilterChip(
                                selected = hasTagFilters,
                                onClick = {
                                    focusManager.clearFocus()
                                    showTagFilterSheet = true
                                },
                                label = {
                                    Text(
                                        text = if (hasTagFilters) "Tags (${selectedTags.size})" else "Tags",
                                        fontSize = 12.sp,
                                        fontWeight = if (hasTagFilters) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (hasTagFilters) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.primary
                                    )
                                },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    selectedContainerColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = hasTagFilters)
                            )

                            // Sort Chip Button
                            Box {
                                val isSortSelected = selectedSort != SheetsViewModel.SortOption.UPDATED_DESC
                                FilterChip(
                                    selected = isSortSelected,
                                    onClick = {
                                        focusManager.clearFocus()
                                        sortMenuExpanded = true
                                    },
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
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Transparent,
                                        selectedContainerColor = Color.Transparent,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = isSortSelected)
                                )

                                FrostedDropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    SheetsViewModel.SortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        option.label,
                                                        fontWeight = if (selectedSort == option) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selectedSort == option) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                    )
                                                    if (selectedSort == option) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
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

                        // ── 3-Row Dynamic Fold Category Tabs ──────────────────────────
                        val row1 = listOf("All", "character", "location")
                        val row2 = listOf("faction", "item", "lore")
                        val stepHeightPx = with(density) { 42.dp.toPx() }

                        // Row 3 (Bottom) animation calculations
                        val p3 = (collapseProgress / 0.333f).coerceIn(0f, 1f)
                        val translationY3 = -p3 * stepHeightPx
                        val alpha3 = (1f - p3 * 1.25f).coerceIn(0f, 1f)
                        val scale3 = 1f - 0.08f * p3

                        // Row 2 (Middle) animation calculations
                        val p2 = if (collapseProgress <= 0.333f) 0f else ((collapseProgress - 0.333f) / 0.333f).coerceIn(0f, 1f)
                        val translationY2 = -p2 * stepHeightPx
                        val alpha2 = if (collapseProgress > 0.666f) 0f else (1f - p2 * 1.25f).coerceIn(0f, 1f)
                        val scale2 = 1f - 0.08f * p2

                        // Row 1 (Top) animation calculations
                        val p1 = if (collapseProgress <= 0.666f) 0f else ((collapseProgress - 0.666f) / 0.334f).coerceIn(0f, 1f)
                        val translationY1 = -p1 * stepHeightPx
                        val alpha1 = (1f - p1 * 1.25f).coerceIn(0f, 1f)
                        val scale1 = 1f - 0.08f * p1

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentTabsContainerHeight)
                                .clipToBounds()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Row 1 (Top Row - zIndex 3)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 0.dp)
                                    .zIndex(3f)
                                    .graphicsLayer {
                                        this.translationY = translationY1
                                        this.alpha = alpha1
                                        this.scaleX = scale1
                                        this.scaleY = scale1
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row1.forEach { key ->
                                    val meta = categoryMeta(key)
                                    val count = if (key == "All") allEntries.size else (countByType[key.lowercase()] ?: 0)
                                    val selected = categoryKeys.getOrNull(pagerState.currentPage) == key
                                    CategoryPill(
                                        key = key,
                                        label = meta.label,
                                        icon = meta.icon,
                                        color = meta.color,
                                        count = count,
                                        selected = selected,
                                        hazeState = hazeState,
                                        onClick = {
                                            focusManager.clearFocus()
                                            val targetIdx = categoryKeys.indexOf(key)
                                            if (targetIdx >= 0) {
                                                scope.launch { pagerState.animateScrollToPage(targetIdx) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Row 2 (Middle Row - zIndex 2)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 42.dp)
                                    .zIndex(2f)
                                    .graphicsLayer {
                                        this.translationY = translationY2
                                        this.alpha = alpha2
                                        this.scaleX = scale2
                                        this.scaleY = scale2
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row2.forEach { key ->
                                    val meta = categoryMeta(key)
                                    val count = countByType[key.lowercase()] ?: 0
                                    val selected = categoryKeys.getOrNull(pagerState.currentPage) == key
                                    CategoryPill(
                                        key = key,
                                        label = meta.label,
                                        icon = meta.icon,
                                        color = meta.color,
                                        count = count,
                                        selected = selected,
                                        hazeState = hazeState,
                                        onClick = {
                                            focusManager.clearFocus()
                                            val targetIdx = categoryKeys.indexOf(key)
                                            if (targetIdx >= 0) {
                                                scope.launch { pagerState.animateScrollToPage(targetIdx) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Row 3 (Bottom Row - zIndex 1)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = 84.dp)
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        this.translationY = translationY3
                                        this.alpha = alpha3
                                        this.scaleX = scale3
                                        this.scaleY = scale3
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Timeline Tab
                                val timelineMeta = categoryMeta("timeline")
                                val timelineCount = countByType["timeline"] ?: 0
                                val isTimelineSelected = categoryKeys.getOrNull(pagerState.currentPage) == "timeline"
                                CategoryPill(
                                    key = "timeline",
                                    label = timelineMeta.label,
                                    icon = timelineMeta.icon,
                                    color = timelineMeta.color,
                                    count = timelineCount,
                                    selected = isTimelineSelected,
                                    hazeState = hazeState,
                                    onClick = {
                                        focusManager.clearFocus()
                                        val targetIdx = categoryKeys.indexOf("timeline")
                                        if (targetIdx >= 0) {
                                            scope.launch { pagerState.animateScrollToPage(targetIdx) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                // More Tab (Action Tab)
                                CategoryPill(
                                    key = "more",
                                    label = "More",
                                    icon = Icons.Default.AutoAwesome,
                                    color = Color(0xFFA0AEC0),
                                    count = 0,
                                    selected = false,
                                    hazeState = hazeState,
                                    onClick = {
                                        focusManager.clearFocus()
                                        Toast.makeText(context, "More categories coming soon!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
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
                                            border = null,
                                            colors = InputChipDefaults.inputChipColors(
                                                containerColor = Color.Transparent,
                                                selectedContainerColor = Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.frostedChip(hazeState, shape = RoundedCornerShape(8.dp), isSelected = true)
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

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── Horizontal Pager (Swiping between categories only) ─────────────
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                        ) { page ->
                            val pageCategory = categoryKeys[page]

                            // Filter and Sort entries for this specific category page
                            val pageEntries = remember(
                                allEntries,
                                pageCategory,
                                searchQuery,
                                selectedTags,
                                selectedSort
                            ) {
                                val filtered = allEntries.filter { entry ->
                                    val matchesCategory = pageCategory == "All" ||
                                            entry.type.equals(pageCategory, ignoreCase = true)

                                    val matchesTags = if (selectedTags.isEmpty()) true else {
                                        try {
                                            val entryTags: List<String> = AppJson.decodeFromString(entry.tagsJson)
                                            selectedTags.all { reqTag -> entryTags.any { it.equals(reqTag, ignoreCase = true) } }
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }

                                    val matchesQuery = if (searchQuery.isBlank()) true else {
                                        entry.name.contains(searchQuery, ignoreCase = true) ||
                                                entry.summary.contains(searchQuery, ignoreCase = true) ||
                                                entry.tagsJson.contains(searchQuery, ignoreCase = true) ||
                                                entry.fieldsJson.contains(searchQuery, ignoreCase = true)
                                    }

                                    matchesCategory && matchesTags && matchesQuery
                                }

                                when (selectedSort) {
                                    SheetsViewModel.SortOption.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
                                    SheetsViewModel.SortOption.UPDATED_ASC -> filtered.sortedBy { it.updatedAt }
                                    SheetsViewModel.SortOption.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
                                    SheetsViewModel.SortOption.CREATED_ASC -> filtered.sortedBy { it.createdAt }
                                    SheetsViewModel.SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                                    SheetsViewModel.SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                                    SheetsViewModel.SortOption.TYPE -> filtered.sortedWith(compareBy({ it.type }, { it.name.lowercase() }))
                                }
                            }

                            if (pageEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (searchQuery.isNotBlank() || selectedTags.isNotEmpty()) Icons.Default.SearchOff
                                            else categoryMeta(pageCategory).icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(52.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank() || selectedTags.isNotEmpty())
                                                "No world sheets matching your filters."
                                            else
                                                "No ${categoryMeta(pageCategory).label.lowercase()} yet.
Tap + to create your first sheet.",
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(pageEntries, key = { it.id }) { entry ->
                                        WorldEntryCard(
                                            entry = entry,
                                            onClick = {
                                                focusManager.clearFocus()
                                                selectedEntryId = entry.id
                                                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.id) }
                                            },
                                            onImageClick = {
                                                focusManager.clearFocus()
                                                if (!entry.imageUri.isNullOrEmpty()) {
                                                    fullScreenImageUri = entry.imageUri
                                                    fullScreenImageTitle = entry.name
                                                } else {
                                                    selectedEntryId = entry.id
                                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.id) }
                                                }
                                            },
                                            onEdit = {
                                                focusManager.clearFocus()
                                                entryToEdit = entry
                                            },
                                            onDuplicate = {
                                                vm.duplicateEntry(entry.id)
                                                Toast.makeText(context, "Duplicated ${entry.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            onDelete = {
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
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a world sheet from the list", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    )

    // ── Create World Sheet Bottom Sheet ───────────────────────────────────────
    if (showCreateSheet) {
        CreateWorldEntrySheet(
            selectedCategory = currentSelectedCategory,
            onDismiss = { showCreateSheet = false },
            onConfirm = { name, type ->
                val newId = vm.createEntry(name, type)
                showCreateSheet = false
                selectedEntryId = newId
                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, newId) }
            }
        )
    }

    // ── Edit World Sheet Bottom Sheet ─────────────────────────────────────────
    entryToEdit?.let { entry ->
        EditWorldEntrySheet(
            entry = entry,
            onDismiss = { entryToEdit = null },
            onSave = { updatedEntry ->
                vm.updateEntry(updatedEntry)
                entryToEdit = null
            }
        )
    }

    // ── Tag Filter Modal Bottom Sheet ─────────────────────────────────────────
    if (showTagFilterSheet) {
        TagFilterBottomSheet(
            allTagsWithCount = allTagsWithCount,
            selectedTags = selectedTags,
            onDismiss = { showTagFilterSheet = false },
            onApply = { newSelected ->
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

                FrostedDropdownMenu(
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
