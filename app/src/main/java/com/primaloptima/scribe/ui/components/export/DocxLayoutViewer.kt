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
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
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
import com.primaloptima.scribe.util.export.MarkdownAstParser
import com.primaloptima.scribe.util.image.VaultImageManager

/**
 * Word Document Layout Simulator.
 * Renders an authentic paginated Word canvas complete with margins, headers,
 * typography styling, lists, tables, and images.
 */
@Composable
fun DocxLayoutViewer(
    note: Note,
    config: ExportConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ast = remember(note.content) { MarkdownAstParser.parse(note.content) }
    val themeColors = remember { ExportThemeMapper.resolveThemeColors(context) }
    val fontName = remember(config.fontChoice) { ExportThemeMapper.resolveDocxFontName(config.fontChoice) }

    val fontFamily = when (config.fontChoice) {
        com.primaloptima.scribe.util.export.FontChoice.CLASSIC_SERIF -> FontFamily.Serif
        com.primaloptima.scribe.util.export.FontChoice.MODERN_SANS -> FontFamily.SansSerif
        com.primaloptima.scribe.util.export.FontChoice.MONOSPACE -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30)) // Desk mat backdrop
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                        for (node in ast.nodes) {
                            when (node) {
                                is MarkdownAstParser.AstNode.HeadingNode -> {
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

                                is MarkdownAstParser.AstNode.ParagraphNode -> {
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

                                is MarkdownAstParser.AstNode.ListNode -> {
                                    for ((idx, item) in node.items.withIndex()) {
                                        val bullet = if (node.isOrdered) "${idx + 1}." else "•"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, bottom = 4.dp)
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

                                is MarkdownAstParser.AstNode.BlockquoteNode -> {
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
                                                    if (child is MarkdownAstParser.AstNode.ParagraphNode) {
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

                                is MarkdownAstParser.AstNode.DividerNode -> {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        thickness = 1.dp,
                                        color = Color(0xFFDDDDDD)
                                    )
                                }

                                is MarkdownAstParser.AstNode.TableNode -> {
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
                                                for (h in node.headers) {
                                                    Text(
                                                        text = buildRunsAnnotatedString(h, defaultBold = true),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
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
                                                for (cell in row) {
                                                    Text(
                                                        text = buildRunsAnnotatedString(cell),
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF222222),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE8E8E8))
                                        }
                                    }
                                }

                                is MarkdownAstParser.AstNode.ImageNode -> {
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
                                                contentDescription = node.caption,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (node.caption.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = node.caption,
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
    runs: List<MarkdownAstParser.InlineRun>,
    defaultBold: Boolean = false,
    defaultItalic: Boolean = false
) = buildAnnotatedString {
    for (r in runs) {
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
