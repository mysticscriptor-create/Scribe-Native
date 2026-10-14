package com.primaloptima.scribe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.AppTheme
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.components.FullScreenImageViewer
import com.primaloptima.scribe.ui.ornaments.OrnamentRegistry
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.font.ScribeFontManager
import com.primaloptima.scribe.util.image.VaultImageManager
import java.text.NumberFormat
import java.util.Locale

private sealed interface ManuscriptBlock {
    data class Header(val level: Int, val text: String) : ManuscriptBlock
    data class Paragraph(val text: String) : ManuscriptBlock
    data class Blockquote(val text: String) : ManuscriptBlock
    data object SceneBreak : ManuscriptBlock
    data class ImageBlock(val alt: String, val uri: String) : ManuscriptBlock
}

/**
 * Formatted Manuscript Reader / Preview Screen.
 * Renders prose, typography tokens, extensible ornaments, and inline images according to the active theme.
 */
@Composable
fun ManuscriptPreviewSheet(
    note: Note,
    bookId: String?,
    activeTheme: AppTheme?,
    selectedOrnamentId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val numFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val wordCount = remember(note.content) { MarkdownUtil.countWords(note.content) }
    val readTimeMin = remember(wordCount) { (wordCount / 225).coerceAtLeast(1) }

    var lightboxImageUri by remember { mutableStateOf<String?>(null) }
    var lightboxImageTitle by remember { mutableStateOf("") }

    val themeFont = remember(activeTheme?.fontFamily, activeTheme?.documentFontWeight) {
        val fontKey = activeTheme?.fontFamily ?: "default"
        val weight = activeTheme?.documentFontWeight ?: 500
        ScribeFontManager.resolveFontFamily(context, fontKey, weight)
    }

    val titleFont = remember(activeTheme?.titleFontFamily, activeTheme?.fontFamily) {
        val fontKey = activeTheme?.titleFontFamily ?: activeTheme?.fontFamily ?: "default"
        val weight = activeTheme?.title1FontWeight ?: 600
        ScribeFontManager.resolveFontFamily(context, fontKey, weight)
    }

    val ornament = remember(selectedOrnamentId) {
        OrnamentRegistry.getById(selectedOrnamentId)
    }

    val parsedBlocks = remember(note.content) {
        parseManuscriptBlocks(note.content)
    }

    val bgModifier = if (activeTheme != null) {
        Modifier.background(activeTheme.backgroundColor)
    } else {
        Modifier.background(MaterialTheme.colorScheme.background)
    }

    val textColor = activeTheme?.textColor ?: MaterialTheme.colorScheme.onBackground
    val accentColor = ScribeTheme.colors.interaction.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .systemBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Bar ───────────────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    tonalElevation = 1.dp
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
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textColor
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = note.name.ifBlank { "Manuscript Preview" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${numFormat.format(wordCount)} words · $readTimeMin min read",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.65f)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = textColor.copy(alpha = 0.12f),
                    thickness = 0.5.dp
                )

                // ── Manuscript Body ───────────────────────────────────────
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = (activeTheme?.paddingHorizontal ?: 20).dp),
                        contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp)
                    ) {
                        // Title Header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = note.name.ifBlank { "Untitled" },
                                    fontSize = (activeTheme?.title1FontSize ?: 24).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = titleFont,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                                if (note.subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.subtitle,
                                        fontSize = (activeTheme?.title2FontSize ?: 16).sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = titleFont,
                                        color = textColor.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                ornament.Render(
                                    tint = textColor.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Blocks
                        items(parsedBlocks) { block ->
                            when (block) {
                                is ManuscriptBlock.Header -> {
                                    val (fontSize, topPadding) = when (block.level) {
                                        1 -> 22.sp to 24.dp
                                        2 -> 19.sp to 20.dp
                                        else -> 17.sp to 16.dp
                                    }
                                    Text(
                                        text = block.text,
                                        fontSize = fontSize,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = titleFont,
                                        color = textColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding, bottom = 8.dp)
                                    )
                                }

                                is ManuscriptBlock.Paragraph -> {
                                    val indentSpaces = " ".repeat(activeTheme?.firstLineIndent ?: 0)
                                    val paraSpacing = (activeTheme?.paragraphSpacing ?: 1) * 6
                                    Text(
                                        text = "$indentSpaces${block.text}",
                                        fontSize = (activeTheme?.fontSize ?: 17).sp,
                                        lineHeight = ((activeTheme?.fontSize ?: 17) * (activeTheme?.lineHeight ?: 1.4f)).sp,
                                        fontFamily = themeFont,
                                        color = textColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = paraSpacing.dp)
                                    )
                                }

                                is ManuscriptBlock.Blockquote -> {
                                    Surface(
                                        color = textColor.copy(alpha = 0.04f),
                                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 2.dp,
                                            color = accentColor.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = block.text,
                                            fontSize = ((activeTheme?.fontSize ?: 17) - 1).sp,
                                            fontStyle = FontStyle.Italic,
                                            fontFamily = themeFont,
                                            color = textColor.copy(alpha = 0.9f),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                is ManuscriptBlock.SceneBreak -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ornament.Render(
                                            tint = textColor.copy(alpha = 0.35f),
                                            modifier = Modifier
                                                .fillMaxWidth(0.5f)
                                                .height(14.dp)
                                        )
                                    }
                                }

                                is ManuscriptBlock.ImageBlock -> {
                                    val imageModel = remember(block.uri, bookId, note.id) {
                                        VaultImageManager.resolveModel(context, block.uri, bookId, note.id)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 300.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(
                                                    0.5.dp,
                                                    textColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    lightboxImageUri = block.uri
                                                    lightboxImageTitle = block.alt
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = imageModel,
                                                contentDescription = block.alt,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        if (block.alt.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = block.alt,
                                                fontSize = 12.sp,
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = themeFont,
                                                color = textColor.copy(alpha = 0.65f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Lightbox on Image Tap in Reader ─────────────────────────
            if (lightboxImageUri != null) {
                FullScreenImageViewer(
                    imageUri = lightboxImageUri!!,
                    title = lightboxImageTitle,
                    subtitle = note.name,
                    bookId = bookId,
                    noteId = note.id,
                    onDismiss = { lightboxImageUri = null }
                )
            }
        }
    }
}

private fun parseManuscriptBlocks(content: String): List<ManuscriptBlock> {
    val blocks = mutableListOf<ManuscriptBlock>()
    val lines = content.lines()
    val imageRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                i++
            }
            trimmed.startsWith("# ") -> {
                blocks.add(ManuscriptBlock.Header(1, trimmed.removePrefix("# ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks.add(ManuscriptBlock.Header(2, trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("### ") -> {
                blocks.add(ManuscriptBlock.Header(3, trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed == "***" || trimmed == "---" || trimmed == "* * *" || trimmed == "###" || trimmed == "___" -> {
                blocks.add(ManuscriptBlock.SceneBreak)
                i++
            }
            trimmed.startsWith("> ") -> {
                blocks.add(ManuscriptBlock.Blockquote(trimmed.removePrefix("> ").trim()))
                i++
            }
            trimmed.contains("![") && imageRegex.containsMatchIn(trimmed) -> {
                val match = imageRegex.find(trimmed)
                if (match != null) {
                    val alt = match.groupValues[1]
                    val uri = match.groupValues[2]
                    blocks.add(ManuscriptBlock.ImageBlock(alt, uri))
                }
                i++
            }
            else -> {
                blocks.add(ManuscriptBlock.Paragraph(trimmed))
                i++
            }
        }
    }
    return blocks
}
