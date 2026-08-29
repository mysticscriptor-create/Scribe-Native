package com.primaloptima.scribe.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FullScreenImageViewer
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeContentCard
import com.primaloptima.scribe.ui.components.ScribePill
import com.primaloptima.scribe.ui.components.ScribeSectionLabel
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalAccentColor
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSubtleTextColor
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.WorldImageUtil
import com.primaloptima.scribe.viewmodel.SheetsViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.serialization.decodeFromString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorldEntryDetailScreen(
    entry: WorldEntry,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onFieldsReordered: (List<SheetsViewModel.Companion.Field>) -> Unit
) {
    val context = LocalContext.current
    val meta = categoryMeta(entry.type)
    val hazeState = LocalHazeState.current
    val accentColor = LocalAccentColor.current
    val subtleText = LocalSubtleTextColor.current
    val resolvedSubtle = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    var showImageViewer by remember { mutableStateOf(false) }
    var isReorderingMode by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val fields: List<SheetsViewModel.Companion.Field> = remember(entry.fieldsJson) {
        try { AppJson.decodeFromString(entry.fieldsJson) } catch (_: Exception) { emptyList() }
    }

    val tags: List<String> = remember(entry.tagsJson) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    val isLandscape = remember(entry.imageUri) {
        WorldImageUtil.isLandscapeImage(context, entry.imageUri)
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val shortDateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val formattedCreated = remember(entry.createdAt) {
        if (entry.createdAt > 0) dateFormatter.format(Date(entry.createdAt))
        else "Initial creation"
    }

    val formattedUpdated = remember(entry.updatedAt) {
        val now = System.currentTimeMillis()
        val diff = now - entry.updatedAt
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> shortDateFormatter.format(Date(entry.updatedAt))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = entry.name,
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions           = listOfNotNull(
                    if (fields.size > 1) {
                        ScribeBarAction(
                            icon = if (isReorderingMode) Icons.Default.Check else Icons.Default.SwapVert,
                            contentDescription = "Reorder Attributes",
                            onClick = { isReorderingMode = !isReorderingMode }
                        )
                    } else null,
                    ScribeBarAction(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit Sheet",
                        onClick = onEdit
                    ),
                    ScribeBarAction(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        onClick = { menuExpanded = true }
                    )
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Adaptive Header (Portrait Left vs Landscape Top Center) ─
                item(key = "header_section") {
                    if (!entry.imageUri.isNullOrEmpty()) {
                        if (isLandscape) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.5.dp, meta.color.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                        .clickable { showImageViewer = true }
                                ) {
                                    AsyncImage(
                                        model = entry.imageUri,
                                        contentDescription = entry.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Fullscreen,
                                            contentDescription = "Expand Image",
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                HeaderTitleAndMetadata(
                                    entry = entry,
                                    meta = meta,
                                    formattedCreated = formattedCreated,
                                    formattedUpdated = formattedUpdated
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.5.dp, meta.color.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                        .clickable { showImageViewer = true }
                                ) {
                                    AsyncImage(
                                        model = entry.imageUri,
                                        contentDescription = entry.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Fullscreen,
                                            contentDescription = "Expand Image",
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HeaderTitleAndMetadata(
                                        entry = entry,
                                        meta = meta,
                                        formattedCreated = formattedCreated,
                                        formattedUpdated = formattedUpdated
                                    )
                                }
                            }
                        }
                    } else {
                        HeaderTitleAndMetadata(
                            entry = entry,
                            meta = meta,
                            formattedCreated = formattedCreated,
                            formattedUpdated = formattedUpdated
                        )
                    }
                }

                // ── Summary Section ──────────────────────────────────────────
                if (entry.summary.isNotBlank()) {
                    item(key = "summary_section") {
                        ScribeContentCard(
                            title = "Summary",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = entry.summary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // ── Tags Section ──────────────────────────────────────────────
                if (tags.isNotEmpty()) {
                    item(key = "tags_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScribeSectionLabel(text = "Tags")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tags.forEach { tag ->
                                    ScribePill(
                                        text = "#$tag",
                                        color = meta.color
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Attributes Section ────────────────────────────────────────
                if (fields.isNotEmpty()) {
                    item(key = "attributes_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScribeSectionLabel(text = "Attributes (${fields.size})")
                            AnimatedVisibility(
                                visible = isReorderingMode,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Text(
                                    text = "Tap arrows to reorder",
                                    fontSize = 11.sp,
                                    color = meta.color,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    itemsIndexed(fields, key = { index, field -> "${field.label}_$index" }) { index, field ->
                        DetailAttributeRow(
                            index = index,
                            totalCount = fields.size,
                            field = field,
                            accentColor = meta.color,
                            isReorderMode = isReorderingMode,
                            onMoveUp = {
                                if (index > 0) {
                                    val mutable = fields.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index - 1, item)
                                    onFieldsReordered(mutable)
                                }
                            },
                            onMoveDown = {
                                if (index < fields.size - 1) {
                                    val mutable = fields.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(index + 1, item)
                                    onFieldsReordered(mutable)
                                }
                            },
                            onCopyValue = {
                                if (field.value.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(field.label, field.value))
                                    Toast.makeText(context, "Copied ${field.label}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Dropdown Menu
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp)
            ) {
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
                    DropdownMenuItem(
                        text = { Text("Copy Summary") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = buildString {
                                appendLine("${entry.name} (${meta.label})")
                                if (entry.summary.isNotBlank()) appendLine(entry.summary)
                                fields.filter { it.value.isNotBlank() }.forEach {
                                    appendLine("${it.label}: ${it.value}")
                                }
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText(entry.name, text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    // Full Screen Image Viewer Modal
    if (showImageViewer && !entry.imageUri.isNullOrEmpty()) {
        FullScreenImageViewer(
            imageUri = entry.imageUri,
            title = entry.name,
            subtitle = meta.label.dropLast(1).ifEmpty { meta.label },
            onDismiss = { showImageViewer = false }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        FrostedDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete World Sheet?") },
            text = { Text("Are you sure you want to delete \"${entry.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeaderTitleAndMetadata(
    entry: WorldEntry,
    meta: CategoryMeta,
    formattedCreated: String,
    formattedUpdated: String
) {
    val subtle = LocalSubtleTextColor.current
    val resolvedSubtle = if (subtle != Color.Unspecified) subtle else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        ScribePill(
            text = meta.label.dropLast(1).ifEmpty { meta.label },
            color = meta.color
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = entry.name,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 28.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = resolvedSubtle,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Created: $formattedCreated",
                fontSize = 11.sp,
                color = resolvedSubtle
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Update,
                contentDescription = null,
                tint = resolvedSubtle,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Updated: $formattedUpdated",
                fontSize = 11.sp,
                color = resolvedSubtle
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailAttributeRow(
    index: Int,
    totalCount: Int,
    field: SheetsViewModel.Companion.Field,
    accentColor: Color,
    isReorderMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCopyValue: () -> Unit
) {
    val pills = remember(field.value) {
        if (field.value.contains(",")) {
            field.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }
    val subtle = LocalSubtleTextColor.current
    val resolvedSubtle = if (subtle != Color.Unspecified) subtle else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    ScribeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReorderMode && field.value.isNotBlank()) { onCopyValue() },
        cornerRadius = ScribeCardTokens.RadiusMedium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = field.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (field.value.isBlank()) {
                    Text(
                        text = "—",
                        fontSize = 13.sp,
                        color = resolvedSubtle
                    )
                } else if (pills.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        pills.forEach { pill ->
                            ScribePill(
                                text = pill,
                                color = accentColor
                            )
                        }
                    }
                } else {
                    Text(
                        text = field.value,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            if (isReorderMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            modifier = Modifier.size(16.dp),
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface else resolvedSubtle.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            modifier = Modifier.size(16.dp),
                            tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurface else resolvedSubtle.copy(alpha = 0.35f)
                        )
                    }
                }
            } else if (field.value.isNotBlank()) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = resolvedSubtle.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
