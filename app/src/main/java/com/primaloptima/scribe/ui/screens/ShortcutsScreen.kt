package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.ScribeSingleFab
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
import kotlinx.coroutines.withContext

data class CategoryMeta(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val CATEGORIES = listOf(
    CategoryMeta(
        id = DefaultShortcuts.CAT_DIALOGUE,
        title = "Dialogue & Monologue",
        subtitle = "Cursive curly, straight, East Asian, ornate quotes & em-dashes",
        icon = Icons.Default.ChatBubbleOutline
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_SYMBOLS,
        title = "Novel & LitRPG Symbols",
        subtitle = "Status window brackets, system frames, ellipsis & flourishes",
        icon = Icons.Default.AutoAwesome
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_PUNCTUATION,
        title = "Punctuation & Cadence",
        subtitle = "Em dashes, en dashes, parentheses, brackets & semicolons",
        icon = Icons.Default.FormatQuote
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_STRUCTURE,
        title = "Structure & Lists",
        subtitle = "Bullets, numbered sequences, task checklists, headings & dividers",
        icon = Icons.Default.FormatListNumbered
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_FORMATTING,
        title = "Formatting & Styles",
        subtitle = "Bold, italic, strikethrough & inline code markers",
        icon = Icons.Default.FormatBold
    ),
    CategoryMeta(
        id = DefaultShortcuts.CAT_CUSTOM,
        title = "Custom Shortcuts",
        subtitle = "Your own custom shortcuts, macros, snippets and character tags",
        icon = Icons.Default.BookmarkAdd
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    vm: ShortcutsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allShortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val disabledCategories by vm.disabledCategories.collectAsStateWithLifecycle()
    val activeBarShortcuts by vm.activeBarShortcuts.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var shortcutToEdit by remember { mutableStateOf<ShortcutAction?>(null) }
    var shortcutToDelete by remember { mutableStateOf<ShortcutAction?>(null) }

    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    val hazeState = LocalHazeState.current
    val subtleText = ScribeTheme.colors.content.secondary
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(showEditDialog) {
            if (showEditDialog && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (!showEditDialog) {
                dialogCaptured = false
                dialogOneShotBitmap = null
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            ScribeTopBar(
                title             = "Shortcut Studio",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions           = listOf(
                    ScribeBarAction(Icons.Default.Refresh, "Reset") {
                        vm.resetToDefaults()
                        Toast.makeText(context, "Shortcuts reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                )
            )
        },
        floatingActionButton = {
            ScribeSingleFab(
                icon = Icons.Default.Add,
                contentDescription = "New Custom Shortcut",
                onClick = {
                    shortcutToEdit = null
                    showEditDialog = true
                }
            )
        }
    ) { padding ->
        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            ) {
                // ── Section 1: Active Shortcut Bar Preview & Reordering Dock ─
                item(key = "active_dock_preview") {
                    ActiveDockCarousel(
                        activeShortcuts = activeBarShortcuts,
                        onMoveLeft = { idx -> vm.moveActiveShortcut(idx, idx - 1) },
                        onMoveRight = { idx -> vm.moveActiveShortcut(idx, idx + 1) },
                        onRemove = { shortcut -> vm.setShortcutEnabled(shortcut.id, false) }
                    )
                }

                // ── Section 2: Categorized Two-Column Shortcut Sections ──────
                CATEGORIES.forEach { categoryMeta ->
                    val isCategoryDisabled = disabledCategories.contains(categoryMeta.id)
                    val categoryShortcuts = allShortcuts.filter {
                        if (categoryMeta.id == DefaultShortcuts.CAT_CUSTOM) {
                            it.category == DefaultShortcuts.CAT_CUSTOM || it.category !in setOf(
                                DefaultShortcuts.CAT_DIALOGUE,
                                DefaultShortcuts.CAT_SYMBOLS,
                                DefaultShortcuts.CAT_PUNCTUATION,
                                DefaultShortcuts.CAT_STRUCTURE,
                                DefaultShortcuts.CAT_FORMATTING
                            )
                        } else {
                            it.category == categoryMeta.id
                        }
                    }

                    // Render category header
                    item(key = "header_${categoryMeta.id}") {
                        CategoryHeaderCard(
                            meta = categoryMeta,
                            itemCount = categoryShortcuts.size,
                            isDisabled = isCategoryDisabled,
                            onToggleCategory = { vm.toggleCategory(categoryMeta.id) }
                        )
                    }

                    // If category is not disabled and has items, render 2-column grid rows
                    if (!isCategoryDisabled && categoryShortcuts.isNotEmpty()) {
                        val chunked = categoryShortcuts.chunked(2)
                        items(chunked, key = { row -> "row_${categoryMeta.id}_${row.first().id}" }) { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ShortcutGridCard(
                                        shortcut = pair[0],
                                        onToggleActive = { vm.toggleShortcutEnabled(pair[0].id) },
                                        onEdit = {
                                            shortcutToEdit = pair[0]
                                            showEditDialog = true
                                        },
                                        onDelete = if (pair[0].category == DefaultShortcuts.CAT_CUSTOM) {
                                            { shortcutToDelete = pair[0] }
                                        } else null
                                    )
                                }
                                if (pair.size > 1) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ShortcutGridCard(
                                            shortcut = pair[1],
                                            onToggleActive = { vm.toggleShortcutEnabled(pair[1].id) },
                                            onEdit = {
                                                shortcutToEdit = pair[1]
                                                showEditDialog = true
                                            },
                                            onDelete = if (pair[1].category == DefaultShortcuts.CAT_CUSTOM) {
                                                { shortcutToDelete = pair[1] }
                                            } else null
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else if (!isCategoryDisabled && categoryShortcuts.isEmpty()) {
                        item(key = "empty_${categoryMeta.id}") {
                            Text(
                                text = "No custom shortcuts yet. Tap + to create one!",
                                style = MaterialTheme.typography.bodySmall,
                                color = subtleText,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // Edit / Create Dialog
            if (showEditDialog) {
                EditShortcutDialog(
                    existing = shortcutToEdit,
                    onDismiss = { showEditDialog = false },
                    onSave = { shortcut ->
                        if (shortcutToEdit == null) {
                            vm.add(shortcut)
                        } else {
                            vm.update(shortcut)
                        }
                        showEditDialog = false
                    }
                )
            }

            // Delete Dialog (Custom shortcuts only)
            shortcutToDelete?.let { shortcut ->
                FrostedDialog(
                    onDismissRequest = { shortcutToDelete = null },
                    title = { Text("Delete \"${shortcut.label}\"?") },
                    text = { Text("Are you sure you want to delete this custom shortcut?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.delete(shortcut.id)
                                shortcutToDelete = null
                            }
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { shortcutToDelete = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

/**
 * Top interactive dock showing the current shortcut bar order with live reordering & quick removal.
 */
@Composable
private fun ActiveDockCarousel(
    activeShortcuts: List<ShortcutAction>,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onRemove: (ShortcutAction) -> Unit
) {
    val accentColor = ScribeTheme.colors.interaction.primary

    Surface(
        shape = RoundedCornerShape(ScribeCardTokens.RadiusLarge),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            0.6.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Active Bar Dock (${activeShortcuts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Rearrange or Remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activeShortcuts.isEmpty()) {
                Text(
                    "No shortcuts currently active. Enable categories or individual items below to populate the bar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeShortcuts.forEachIndexed { index, shortcut ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.6.dp,
                                accentColor.copy(alpha = 0.3f)
                            ),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Move left
                                if (index > 0) {
                                    IconButton(
                                        onClick = { onMoveLeft(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Move Left",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = shortcut.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Move right
                                if (index < activeShortcuts.size - 1) {
                                    IconButton(
                                        onClick = { onMoveRight(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Move Right",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                // Quick eject from bar
                                IconButton(
                                    onClick = { onRemove(shortcut) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove from Bar",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
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

/**
 * Category header with icon, subtitle, item count, and master toggle switch.
 */
@Composable
private fun CategoryHeaderCard(
    meta: CategoryMeta,
    itemCount: Int,
    isDisabled: Boolean,
    onToggleCategory: () -> Unit
) {
    val accentColor = ScribeTheme.colors.interaction.primary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isDisabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDisabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            else accentColor.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = meta.icon,
                        contentDescription = null,
                        tint = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = meta.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = if (isDisabled) 0.05f else 0.15f)
                        ) {
                            Text(
                                text = "$itemCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isDisabled) "Category disabled in shortcut bar" else meta.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDisabled) 0.5f else 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = !isDisabled,
                onCheckedChange = { onToggleCategory() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * 2-Column Grid Card for displaying a shortcut with distinct preview badge, metadata, and status action.
 */
@Composable
private fun ShortcutGridCard(
    shortcut: ShortcutAction,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = ScribeTheme.colors.interaction.primary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (shortcut.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            0.6.dp,
            if (shortcut.isEnabled) accentColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        shadowElevation = if (shortcut.isEnabled) 1.5.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleActive() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Crisp Glyph / Symbol Preview Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (shortcut.isEnabled) accentColor.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortcut.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (shortcut.isEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Options Menu (Edit / Delete)
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    FrostedDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            // Label & Kind Description
            Column {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${shortcut.kind.replaceFirstChar { it.uppercase() }} • ${shortcut.payload.replace("\n", "↵")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Toggle Button
            Surface(
                onClick = onToggleActive,
                shape = CircleShape,
                color = if (shortcut.isEnabled) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    if (shortcut.isEnabled) accentColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().height(26.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = if (shortcut.isEnabled) "✓ In Bar" else "+ Add to Bar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (shortcut.isEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EditShortcutDialog(
    existing: ShortcutAction?,
    onDismiss: () -> Unit,
    onSave: (ShortcutAction) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "insert") }
    var payload by remember { mutableStateOf(existing?.payload ?: "") }
    var closing by remember { mutableStateOf(existing?.closing ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: DefaultShortcuts.CAT_CUSTOM) }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Shortcut" else "Edit Shortcut") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button Label") },
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

                Text("Action Type", style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kind == "pair", onClick = { kind = "pair" })
                    Text("Pair", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    RadioButton(selected = kind == "insert", onClick = { kind = "insert" })
                    Text("Insert", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    RadioButton(selected = kind == "prefix", onClick = { kind = "prefix" })
                    Text("Prefix", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    RadioButton(selected = kind == "wrap", onClick = { kind = "wrap" })
                    Text("Wrap", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank() && payload.isNotBlank()) {
                        onSave(
                            ShortcutAction(
                                id = existing?.id ?: (System.currentTimeMillis().toString() + Math.random().toString().takeLast(4)),
                                label = label.trim(),
                                kind = kind,
                                payload = payload,
                                closing = closing.ifBlank { null },
                                category = category,
                                isEnabled = existing?.isEnabled ?: true
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
