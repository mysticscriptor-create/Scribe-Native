package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.data.NoteVersion
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribePill
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.viewmodel.EditorViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    editorVm: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as ScribeApp
    val accentColor = ScribeTheme.colors.interaction.primary
    val subtleText = ScribeTheme.colors.content.secondary

    val activeNoteIdState by app.dataStore.activeNoteIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val noteId = activeNoteIdState ?: ""

    var currentNoteContent by remember { mutableStateOf("") }
    val versionsFlow = remember(noteId) {
        app.database.noteVersionDao().observeVersions(noteId)
    }
    val versions by versionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(versions) {
        if (!hasLoaded) hasLoaded = true
    }

    LaunchedEffect(noteId) {
        if (noteId.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val n = app.database.noteDao().getById(noteId)
                if (n != null) {
                    currentNoteContent = n.content
                }
            }
        }
    }

    var selectedVersion by remember { mutableStateOf<NoteVersion?>(null) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current.toInt().coerceIn(1, 25)
    val hazeState = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = selectedVersion != null || showConfirmRestoreDialog
        LaunchedEffect(anyDialogOpen) {
            if (anyDialogOpen && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
                }
            } else if (!anyDialogOpen) {
                dialogCaptured = false
                dialogOneShotBitmap = null
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = "Version History",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        if (versions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (hasLoaded) {
                    Text(
                        "No saved versions for this note yet.",
                        color = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            ) {
                itemsIndexed(versions, key = { _, ver -> ver.timestamp }) { index, ver ->
                    val dateStr = remember(ver.timestamp) {
                        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
                    }
                    val deltaStr = remember(versions, index) {
                        val olderWords = versions.getOrNull(index + 1)?.wordCount ?: 0
                        val delta = ver.wordCount - olderWords
                        when {
                            index == versions.lastIndex -> null
                            delta > 0  -> "+$delta words"
                            delta < 0  -> "$delta words"
                            else       -> "no change"
                        }
                    }
                    val isManual = ver.type == NoteVersion.TYPE_MANUAL

                    ScribeCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = ScribeCardTokens.RadiusMedium,
                        onClick = { selectedVersion = ver }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(ScribeCardTokens.RadiusSmall))
                                    .background(accentColor.copy(alpha = 0.12f))
                                    .border(0.6.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(ScribeCardTokens.RadiusSmall)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isManual) Icons.Default.Bookmark else Icons.Default.History,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        dateStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isManual) {
                                        ScribePill(text = "Checkpoint", color = accentColor)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${ver.wordCount} words",
                                        fontSize = 12.sp,
                                        color = accentColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (deltaStr != null) {
                                        val successColor = ScribeTheme.colors.semantic.success
                                        val errorColor = ScribeTheme.colors.semantic.error
                                        val deltaColor = when {
                                            deltaStr.startsWith("+") -> successColor
                                            deltaStr.startsWith("-") -> errorColor
                                            else -> if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            deltaStr,
                                            fontSize = 12.sp,
                                            color = deltaColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    ver.content.take(100).replace("\n", " "),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
        selectedVersion?.let { ver ->
            val dateStr = remember(ver.timestamp) {
                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ver.timestamp))
            }
            val raisedSurface = ScribeTheme.colors.surfaces.surfaceRaised
            val borderSubtle = ScribeTheme.colors.borders.subtle

            FrostedDialog(
                onDismissRequest = { selectedVersion = null },
                title = {
                    Column {
                        Text("Version Preview", fontWeight = FontWeight.Bold)
                        Text(
                            dateStr,
                            fontSize = 12.sp,
                            color = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState())
                            .background(
                                if (raisedSurface != Color.Unspecified) raisedSurface else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(ScribeCardTokens.RadiusSmall)
                            )
                            .border(
                                0.7.dp,
                                if (borderSubtle != Color.Unspecified) borderSubtle else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                RoundedCornerShape(ScribeCardTokens.RadiusSmall)
                            )
                            .padding(12.dp)
                    ) {
                        val errorColor = ScribeTheme.colors.semantic.error
                        val successColor = ScribeTheme.colors.semantic.success
                        val diffAnnotated = remember(currentNoteContent, ver.content, errorColor, successColor) {
                            buildDiffAnnotatedString(currentNoteContent, ver.content, errorColor = errorColor, successColor = successColor)
                        }
                        Text(text = diffAnnotated, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showConfirmRestoreDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Restore this version")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedVersion = null }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showConfirmRestoreDialog && selectedVersion != null) {
            FrostedDialog(
                onDismissRequest = { showConfirmRestoreDialog = false },
                title = { Text("Confirm Restore") },
                text = { Text("Are you sure you want to replace current note content with this saved version?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val ver = selectedVersion!!
                            editorVm.restoreSnapshot(ver.content)
                            scope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "Version restored successfully", Toast.LENGTH_SHORT).show()
                                showConfirmRestoreDialog = false
                                selectedVersion = null
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestoreDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun buildDiffAnnotatedString(
    currentText: String,
    versionText: String,
    errorColor: Color = Color.Unspecified,
    successColor: Color = Color.Unspecified
) = buildAnnotatedString {
    val resolvedError = if (errorColor != Color.Unspecified) errorColor else Color(0xFFD32F2F)
    val resolvedSuccess = if (successColor != Color.Unspecified) successColor else Color(0xFF4CAF50)
    val currentLines = currentText.lines()
    val versionLines = versionText.lines()
    val oldSet = currentLines.toSet()
    val newSet = versionLines.toSet()

    for (line in versionLines) {
        if (!oldSet.contains(line)) {
            withStyle(
                style = SpanStyle(
                    background = resolvedSuccess.copy(alpha = 0.2f),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("+ $line\n")
            }
        } else {
            append("  $line\n")
        }
    }

    for (line in currentLines) {
        if (!newSet.contains(line)) {
            withStyle(
                style = SpanStyle(
                    background = resolvedError.copy(alpha = 0.2f),
                    textDecoration = TextDecoration.LineThrough,
                    color = resolvedError
                )
            ) {
                append("- $line\n")
            }
        }
    }
}
