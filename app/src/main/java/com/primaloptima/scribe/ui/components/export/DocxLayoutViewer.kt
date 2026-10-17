package com.primaloptima.scribe.ui.components.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportThemeMapper
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.MarkdownNode
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import com.primaloptima.scribe.util.export.TableAlignment
import com.primaloptima.scribe.util.image.VaultImageManager

/**
 * Word Document Layout Simulator.
 * Renders an authentic paginated Word canvas complete with margins, headers,
 * typography styling, multi-level lists, tables, and images.
 */
@Composable
fun DocxLayoutViewer(
    note: Note,
    config: ExportConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val doc = remember(note.content) { ScribeMarkdownAstParser.parse(note.content) }
    val fontFamily = when (config.fontChoice) {
        FontChoice.CLASSIC_SERIF -> FontFamily.Serif
        FontChoice.MODERN_SANS -> FontFamily.SansSerif
        FontChoice.MONOSPACE -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30)) // Desk mat backdrop
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Truth in Preview Banner
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Manuscript Word Layout Preview · ${config.paperSize.label} · ${config.marginPreset.label} · ${config.fontChoice.label}",
                            color = Color(0xFFDDDDDD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            item {
                // Word Page Sheet
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .shadow(elevation = 10.dp, shape = RoundedCornerShape(2.dp)),
                    color = Color.White,
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        // Title / Cover
                        if (config.includeCoverTitle) {
                            Text(
                                text = note.name.ifBlank { "Untitled Manuscript" },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = fontFamily,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )
                        }

                        // Render AST Nodes
                        for (node in doc.children) {
                            when (node) {
                                is MarkdownNode.BlockNode.Heading -> {
                                    val (fontSize, weight, spaceTop) = when (node.level) {
                                        1 -> Triple(20.sp, FontWeight.Bold, 18.dp)
                                        2 -> Triple(16.sp, FontWeight.SemiBold, 14.dp)
                                        else -> Triple(14.sp, FontWeight.Medium, 10.dp)
                                    }
                                    Spacer(modifier = Modifier.height(spaceTop))
                                    Text(
                                        text = buildRunsAnnotatedString(node.runs, defaultBold = true),
                                        fontSize = fontSize,
                                        fontWeight = weight,
                                        color = if (node.level <= 2) MaterialTheme.colorScheme.primary else Color(0xFF222222),
                                        fontFamily = fontFamily,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                is MarkdownNode.BlockNode.Paragraph -> {
                                    Text(
                                        text = buildRunsAnnotatedString(node.runs),
                                        fontSize = 11.sp,
                                        lineHeight = 17.sp,
                                        color = Color(0xFF222222),
                                        fontFamily = fontFamily,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    )
                                }

                                is MarkdownNode.BlockNode.ListBlock -> {
                                    val glyphs = listOf("•", "◦", "▪")
                                    var numCounter = node.startNumber
                                    for (item in node.items) {
                                        val bullet = if (node.isOrdered) "${numCounter++}." else glyphs[item.level % 3]
                                        val indentDp = (12 + item.level * 16).dp
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = indentDp, bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = bullet,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF444444),
                                                modifier = Modifier.width(20.dp)
                                            )
                                            Text(
                                                text = buildRunsAnnotatedString(item.runs),
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                                color = Color(0xFF222222),
                                                fontFamily = fontFamily,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                is MarkdownNode.BlockNode.Blockquote -> {
                                    Surface(
                                        color = Color(0xFFF9F9F9),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .heightIn(min = 36.dp)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                for (child in node.children) {
                                                    if (child is MarkdownNode.BlockNode.Paragraph) {
                                                        Text(
                                                            text = buildRunsAnnotatedString(child.runs, defaultItalic = true),
                                                            fontSize = 11.sp,
                                                            fontStyle = FontStyle.Italic,
                                                            color = Color(0xFF555555),
                                                            fontFamily = fontFamily
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is MarkdownNode.BlockNode.ThematicBreak -> {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        thickness = 1.dp,
                                        color = Color(0xFFDDDDDD)
                                    )
                                }

                                is MarkdownNode.BlockNode.Table -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .border(1.dp, Color(0xFFCCCCCC))
                                    ) {
                                        if (node.headers.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFEEEEEE))
                                                    .padding(6.dp)
                                            ) {
                                                for ((idx, h) in node.headers.withIndex()) {
                                                    val align = node.alignments.getOrElse(idx) { TableAlignment.DEFAULT }
                                                    val textAlign = when (align) {
                                                        TableAlignment.CENTER -> TextAlign.Center
                                                        TableAlignment.RIGHT -> TextAlign.End
                                                        else -> TextAlign.Start
                                                    }
                                                    Text(
                                                        text = buildRunsAnnotatedString(h.runs, defaultBold = true),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = textAlign,
                                                        color = Color(0xFF111111),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(thickness = 1.dp, color = Color(0xFFCCCCCC))
                                        }
                                        for (row in node.rows) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp)
                                            ) {
                                                for ((idx, cell) in row.withIndex()) {
                                                    val align = node.alignments.getOrElse(idx) { TableAlignment.DEFAULT }
                                                    val textAlign = when (align) {
                                                        TableAlignment.CENTER -> TextAlign.Center
                                                        TableAlignment.RIGHT -> TextAlign.End
                                                        else -> TextAlign.Start
                                                    }
                                                    Text(
                                                        text = buildRunsAnnotatedString(cell.runs),
                                                        fontSize = 10.sp,
                                                        textAlign = textAlign,
                                                        color = Color(0xFF222222),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE8E8E8))
                                        }
                                    }
                                }

                                is MarkdownNode.BlockNode.ImageBlock -> {
                                    val imageModel = remember(node.uri, note.bookId, note.id) {
                                        VaultImageManager.resolveModel(context, node.uri, note.bookId, note.id)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .heightIn(max = 220.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = imageModel,
                                                contentDescription = node.caption ?: node.alt,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        val captionText = node.caption ?: node.alt
                                        if (captionText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = captionText,
                                                fontSize = 9.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = Color(0xFF777777),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildRunsAnnotatedString(
    runs: List<MarkdownNode.InlineRun>,
    defaultBold: Boolean = false,
    defaultItalic: Boolean = false
) = buildAnnotatedString {
    for (r in runs) {
        if (r.isHardBreak) {
            append("\n")
            continue
        }
        val spanStyle = SpanStyle(
            fontWeight = if (r.isBold || defaultBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (r.isItalic || defaultItalic) FontStyle.Italic else FontStyle.Normal,
            color = if (r.linkUrl != null) Color(0xFF0066CC) else Color.Unspecified,
            fontFamily = if (r.isCode) FontFamily.Monospace else null,
            background = if (r.isCode) Color(0xFFEEEEEE) else Color.Transparent
        )
        withStyle(spanStyle) {
            append(r.text)
        }
    }
}
