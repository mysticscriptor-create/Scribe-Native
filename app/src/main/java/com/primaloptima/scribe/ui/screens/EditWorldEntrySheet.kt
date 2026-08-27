package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FrostedBottomSheet
import com.primaloptima.scribe.ui.components.FullScreenImageViewer
import com.primaloptima.scribe.ui.components.ImageCropperDialog
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.WorldImageUtil
import com.primaloptima.scribe.viewmodel.SheetsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWorldEntrySheet(
    entry: WorldEntry,
    onDismiss: () -> Unit,
    onSave: (WorldEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = categoryMeta(entry.type)

    val initialFields: List<SheetsViewModel.Companion.Field> = remember(entry) {
        try { AppJson.decodeFromString(entry.fieldsJson) } catch (_: Exception) { emptyList() }
    }
    val initialTags: List<String> = remember(entry) {
        try { AppJson.decodeFromString(entry.tagsJson) } catch (_: Exception) { emptyList() }
    }

    var name by remember { mutableStateOf(entry.name) }
    var summary by remember { mutableStateOf(entry.summary) }
    var imageUri by remember { mutableStateOf(entry.imageUri) }
    var fields by remember { mutableStateOf(initialFields) }
    var tags by remember { mutableStateOf(initialTags) }
    var newTagInput by remember { mutableStateOf("") }

    // Cropper & Image viewer state
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showImageViewer by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    // Helper to add tag from text (splits on comma)
    val addTagsFromInput: (String) -> Unit = { raw ->
        val splits = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (splits.isNotEmpty()) {
            val updated = (tags + splits).distinct()
            tags = updated
            newTagInput = ""
        }
    }

    FrostedBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // ── Top Navigation & Actions Bar ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edit ${meta.label.dropLast(1).ifEmpty { meta.label }}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        onSave(
                            entry.copy(
                                name = name.trim().ifEmpty { entry.name },
                                summary = summary.trim(),
                                imageUri = imageUri,
                                fieldsJson = AppJson.encodeToString(fields),
                                tagsJson = AppJson.encodeToString(tags),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            // ── Scrollable Body ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ── Image Section (with Crop & Adjust) ────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!imageUri.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, meta.color, RoundedCornerShape(12.dp))
                                    .clickable { showImageViewer = true }
                            ) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Expand",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(meta.color.copy(alpha = 0.15f))
                                    .border(1.dp, meta.color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    tint = meta.color,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (!imageUri.isNullOrEmpty()) "Illustration / Portrait" else "No image attached",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (imageUri.isNullOrEmpty()) "Select Photo" else "Change", fontSize = 12.sp)
                                }

                                if (!imageUri.isNullOrEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val uri = Uri.parse(imageUri)
                                            pendingCropUri = uri
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = "Crop", modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Crop", fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = { imageUri = null },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove photo",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Name & Overview ──────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Summary / Logline / Brief Overview") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // ── Tags Section (Comma Auto-tagging) ────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = meta.color, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tags & Keywords", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text("Separate with commas", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }

                        // Tags cloud
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tags.forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove tag",
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { tags = tags - tag }
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        // Input row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { input ->
                                    if (input.endsWith(",")) {
                                        addTagsFromInput(input.removeSuffix(","))
                                    } else {
                                        newTagInput = input
                                    }
                                },
                                placeholder = { Text("Add tag (press comma to complete)…", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            IconButton(
                                onClick = { addTagsFromInput(newTagInput) },
                                enabled = newTagInput.trim().isNotEmpty()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add tag", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ── Attributes / Fields Section (with Drag/Reorder & Comma Pills) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("World Attributes & Details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Drag or use arrows to reorder. Commas create pills.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    FilledTonalButton(
                        onClick = {
                            fields = fields + SheetsViewModel.Companion.Field("New Attribute", "")
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Field", fontSize = 12.sp)
                    }
                }

                // Fields list with reordering
                fields.forEachIndexed { index, field ->
                    AttributeEditCard(
                        index = index,
                        totalCount = fields.size,
                        field = field,
                        accentColor = meta.color,
                        onLabelChange = { newLabel ->
                            val list = fields.toMutableList()
                            list[index] = field.copy(label = newLabel)
                            fields = list
                        },
                        onValueChange = { newVal ->
                            val list = fields.toMutableList()
                            list[index] = field.copy(value = newVal)
                            fields = list
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val list = fields.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                fields = list
                            }
                        },
                        onMoveDown = {
                            if (index < fields.size - 1) {
                                val list = fields.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                fields = list
                            }
                        },
                        onDelete = {
                            val list = fields.toMutableList()
                            list.removeAt(index)
                            fields = list
                        }
                    )
                }
            }
        }
    }

    // Interactive Image Cropper Modal
    pendingCropUri?.let { uri ->
        ImageCropperDialog(
            sourceUri = uri,
            onDismiss = { pendingCropUri = null },
            onCropFinished = { croppedBmp ->
                scope.launch {
                    val savedUri = WorldImageUtil.saveCroppedBitmap(context, entry.id, croppedBmp)
                    if (savedUri.isNotBlank()) {
                        imageUri = savedUri
                    }
                    pendingCropUri = null
                }
            }
        )
    }

    // Full-screen Image Viewer
    if (showImageViewer && !imageUri.isNullOrEmpty()) {
        FullScreenImageViewer(
            imageUri = imageUri!!,
            title = name,
            subtitle = meta.label.dropLast(1).ifEmpty { meta.label },
            onDismiss = { showImageViewer = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeEditCard(
    index: Int,
    totalCount: Int,
    field: SheetsViewModel.Companion.Field,
    accentColor: Color,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    // Check if value has comma-separated items for pills preview
    val commaPills = remember(field.value) {
        if (field.value.contains(",")) {
            field.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Label input + Reorder buttons + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = field.label,
                    onValueChange = onLabelChange,
                    label = { Text("Label", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )

                // Move Up
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        modifier = Modifier.size(16.dp),
                        tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                // Move Down
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        modifier = Modifier.size(16.dp),
                        tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.4f)
                    )
                }

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Remove field",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Value text area
            OutlinedTextField(
                value = field.value,
                onValueChange = onValueChange,
                label = { Text("Value (use commas to auto-format pills)", fontSize = 11.sp) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            // Dynamic Pills Preview if comma separated
            if (commaPills.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Pills preview: ",
                        fontSize = 10.sp,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        commaPills.forEach { pill ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(pill, fontSize = 10.sp) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
