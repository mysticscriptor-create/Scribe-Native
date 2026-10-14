package com.primaloptima.scribe.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal dialog for inserting images into the Scribe editor.
 * Supports:
 *   1. Local Device Images (imported & downsampled into internal vault storage)
 *   2. Web URL Images (direct Markdown links)
 */
@Composable
fun InsertImageDialog(
    bookId: String?,
    noteId: String,
    onDismiss: () -> Unit,
    onInsertMarkdown: (markdownTag: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Local Photo, 1: Web Link
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var webUrl by remember { mutableStateOf("") }
    var altDescription by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            errorMessage = null
        }
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isImporting,
            dismissOnClickOutside = !isImporting
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Header ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ScribeTheme.colors.interaction.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = ScribeTheme.colors.interaction.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "Insert Image",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { if (!isImporting) onDismiss() },
                        enabled = !isImporting
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Tab Switcher: Local vs Web ───────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = {},
                    divider = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Device Photo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                        modifier = Modifier
                            .padding(3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Web URL", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                        modifier = Modifier
                            .padding(3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Tab 0: Local Image Picker ────────────────────────────
                if (selectedTab == 0) {
                    if (selectedUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(Color.Black.copy(alpha = 0.04f))
                        ) {
                            AsyncImage(
                                model = selectedUri,
                                contentDescription = "Selected Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black.copy(alpha = 0.65f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = "Image will be optimized and saved to note vault.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = ScribeTheme.colors.interaction.primary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(ScribeTheme.colors.interaction.primary.copy(alpha = 0.05f))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Pick Image",
                                    tint = ScribeTheme.colors.interaction.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Tap to choose photo from device",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ScribeTheme.colors.interaction.primary
                                )
                            }
                        }
                    }
                }

                // ── Tab 1: Web URL ───────────────────────────────────────
                if (selectedTab == 1) {
                    OutlinedTextField(
                        value = webUrl,
                        onValueChange = { webUrl = it; errorMessage = null },
                        label = { Text("Image Web URL") },
                        placeholder = { Text("https://example.com/illustration.jpg") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (webUrl.isNotBlank() && (webUrl.startsWith("http://") || webUrl.startsWith("https://"))) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.04f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = webUrl.trim(),
                                contentDescription = "Web Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Alt Description / Caption ────────────────────────────
                OutlinedTextField(
                    value = altDescription,
                    onValueChange = { altDescription = it },
                    label = { Text("Caption / Alt Description (Optional)") },
                    placeholder = { Text("e.g. Map of the Northern Citadel") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action Buttons ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (!isImporting) onDismiss() },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    val canInsert = (selectedTab == 0 && selectedUri != null) ||
                            (selectedTab == 1 && webUrl.trim().startsWith("http"))

                    Button(
                        onClick = {
                            if (selectedTab == 0) {
                                val uri = selectedUri ?: return@Button
                                isImporting = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val (_, vaultUri) = VaultImageManager.importLocalImage(
                                            context = context,
                                            sourceUri = uri,
                                            bookId = bookId,
                                            noteId = noteId
                                        )
                                        val caption = altDescription.trim().ifBlank { "Image" }
                                        val tag = "![$caption]($vaultUri)"
                                        withContext(Dispatchers.Main) {
                                            isImporting = false
                                            onInsertMarkdown(tag)
                                            onDismiss()
                                        }
                                    } catch (e: Throwable) {
                                        withContext(Dispatchers.Main) {
                                            isImporting = false
                                            errorMessage = e.message ?: "Failed to import image"
                                        }
                                    }
                                }
                            } else {
                                val url = webUrl.trim()
                                if (url.isBlank()) return@Button
                                val caption = altDescription.trim().ifBlank { "Image" }
                                val tag = "![$caption]($url)"
                                onInsertMarkdown(tag)
                                onDismiss()
                            }
                        },
                        enabled = canInsert && !isImporting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScribeTheme.colors.interaction.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Insert")
                        }
                    }
                }
            }
        }
    }
}
