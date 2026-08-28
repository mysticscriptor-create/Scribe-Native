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
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.WorldImageUtil
import com.primaloptima.scribe.viewmodel.SheetsViewModel
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
    val solidSurface = LocalSolidSurface.current
    val meta = categoryMeta(entry.type)

    var showImageViewer by remember { mutableStateOf(false) }
    var isReorderingMode by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val fields: List<SheetsViewModel.Companion.Field> = remember(entry.fieldsJson) {
        try { AppJson.decodeFromString(entry.fieldsJson) } catch (_: Exception) { emptyList() }
    }
    val tags: List<String> = remember(entry.tagsJson) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    // Determine if the attached image is landscape (aspect ratio width > height)
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
        containerColor = solidSurface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = meta.color.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    tint = meta.color,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = meta.label.dropLast(1).ifEmpty { meta.label },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = meta.color
                                )
                            }
                        }

                        Text(
                            text = entry.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Toggle Reorder Attributes mode
                    if (fields.size > 1) {
                        IconButton(
                            onClick = { isReorderingMode = !isReorderingMode }
                        ) {
                            Icon(
                                if (isReorderingMode) Icons.Default.Check else Icons.Default.SwapVert,
                                contentDescription = "Reorder Attributes",
                                tint = if (isReorderingMode) meta.color else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Edit
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Sheet")
                    }

                    // Overflow Menu (Duplicate, Delete, Copy)
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
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
                            HorizontalDivider()
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = solidSurface
                )
            )
        }
    ) { innerPadding ->
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
                        // ── Landscape Image: Banner at Top Center ───────
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
                                        .background(Color.Black.copy(alpha = 0.65f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Expand Image",
                                        tint = Color.White,
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
                        // ── Portrait Image: Left Column ─────────────────
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
                                        .background(Color.Black.copy(alpha = 0.65f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Expand Image",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    // No image: Clean Header with Category Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(meta.color.copy(alpha = 0.15f))
                                .border(1.dp, meta.color.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = meta.icon,
                                contentDescription = null,
                                tint = meta.color,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            HeaderTitleAndMetadata(
                                entry = entry,
                                meta = meta,
                                formattedCreated = formattedCreated,
                                formattedUpdated = formattedUpdated
                            )
                        }
                    }
                }
            }

            // ── Summary Box (if present) ──────────────────────────────
            if (entry.summary.isNotBlank()) {
                item(key = "summary_section") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Overview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = meta.color
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.summary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ── Tags Strip (if present) ───────────────────────────────
            if (tags.isNotEmpty()) {
                item(key = "tags_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Tags",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = meta.color.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, meta.color.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "#$tag",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = meta.color,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Attributes Section Header ─────────────────────────────
            item(key = "attributes_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attributes & Lore",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isReorderingMode) {
                        TextButton(onClick = { isReorderingMode = false }) {
                            Text("Done Reordering", fontWeight = FontWeight.Bold)
                        }
                    } else if (fields.size > 1) {
                        TextButton(onClick = { isReorderingMode = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reorder", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Attributes List (with Comma-to-Pills formatting & Reordering) ─
            itemsIndexed(fields, key = { index, f -> "${f.label}_$index" }) { index, field ->
                DetailAttributeRow(
                    index = index,
                    totalCount = fields.size,
                    field = field,
                    accentColor = meta.color,
                    isReorderMode = isReorderingMode,
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = fields.toMutableList()
                            val temp = mutable[index]
                            mutable[index] = mutable[index - 1]
                            mutable[index - 1] = temp
                            onFieldsReordered(mutable)
                        }
                    },
                    onMoveDown = {
                        if (index < fields.size - 1) {
                            val mutable = fields.toMutableList()
                            val temp = mutable[index]
                            mutable[index] = mutable[index + 1]
                            mutable[index + 1] = temp
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
    Text(
        text = entry.name,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 28.sp
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Timestamps block
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Created: $formattedCreated",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Updated: $formattedUpdated",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
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
    // Comma pill detection: if value contains commas, parse into pill chips
    val pills = remember(field.value) {
        if (field.value.contains(",")) {
            field.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isReorderMode && field.value.isNotBlank()) { onCopyValue() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
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
                        color = MaterialTheme.colorScheme.outline
                    )
                } else if (pills.isNotEmpty()) {
                    // Auto-rendered comma pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    ) {
                        pills.forEach { pill ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = pill,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
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
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.3f)
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
                            tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.3f)
                        )
                    }
                }
            } else if (field.value.isNotBlank()) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
