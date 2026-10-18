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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxBlockElement
import com.primaloptima.scribe.util.export.DocxDocumentParser
import com.primaloptima.scribe.util.export.DocxRunModel
import com.primaloptima.scribe.util.export.DocxTableCellModel
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.MarkdownNode
import com.primaloptima.scribe.util.export.ParsedDocxDocument
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import com.primaloptima.scribe.util.export.TableAlignment
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Publication-grade Word Layout Simulator with True OpenXML Extraction.
 * When a compiled .docx artifact is present, extracts and renders paragraphs, headings,
 * tables, lists, and embedded images directly from word/document.xml, guaranteeing
 * true artifact preview parity. Falls back gracefully to AST rendering during compile.
 */
@Composable
fun DocxLayoutViewer(
    docxFile: File?,
    note: Note,
    config: ExportConfig,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF2B2D30)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Compiling OpenXML Word document…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDDDDDD)
                )
            }
        }
        return
    }

    if (errorMessage != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF2B2D30)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    // Inspect and parse compiled OpenXML package off the main thread
    var parsedDocx by remember { mutableStateOf<ParsedDocxDocument?>(null) }
    var packageMediaCount by remember { mutableIntStateOf(0) }
    var packageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(docxFile) {
        if (docxFile != null && docxFile.exists() && docxFile.length() > 0L) {
            withContext(Dispatchers.IO) {
                try {
                    ZipFile(docxFile).use { zip ->
                        val hasDoc = zip.getEntry("word/document.xml") != null
                        val hasTypes = zip.getEntry("[Content_Types].xml") != null
                        if (!hasDoc || !hasTypes) {
                            packageError = "Corrupt DOCX: missing word/document.xml or [Content_Types].xml"
                        } else {
                            val parsed = DocxDocumentParser.parseFromZip(zip)
                            parsedDocx = parsed
                            packageMediaCount = parsed.mediaEntries.size
                            packageError = null
                        }
                    }
                } catch (e: Exception) {
                    packageError = "DOCX inspection failed: ${e.localizedMessage}"
                }
            }
        } else {
            parsedDocx = null
        }
    }

    val doc = remember(note.content) { ScribeMarkdownAstParser.parse(note.content) }
    val fontFamily = when (config.fontChoice) {
        FontChoice.CLASSIC_SERIF -> FontFamily.Serif
        FontChoice.MODERN_SANS -> FontFamily.SansSerif
        FontChoice.MONOSPACE -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val fileKb = remember(docxFile) { docxFile?.let { it.length() / 1024L } ?: 0L }
    val bannerText = if (docxFile != null && packageError == null && parsedDocx != null) {
        val mediaText = if (packageMediaCount > 0) " · $packageMediaCount media parts" else ""
        "Word Layout Simulator · True OpenXML Artifact (${fileKb} KB)$mediaText · ${config.paperSize.label} · ${config.marginPreset.label}"
    } else if (docxFile != null && packageError == null) {
        "Word Layout Simulator · Verified OpenXML Package (${fileKb} KB) · ${config.paperSize.label}"
    } else if (packageError != null) {
        "Word Layout Simulator · Package Warning: $packageError"
    } else {
        "Word Layout Simulator · Simulated Pagination · ${config.paperSize.label} · ${config.marginPreset.label} · ${config.fontChoice.label}"
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
            // Truth in Preview & Package Integrity Banner
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f))
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
                            text = bannerText,
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
                        val activeDocx = parsedDocx
                        if (activeDocx != null) {
                            // ── Render from True Parsed OpenXML Document ────────────────
                            if (config.includeCoverTitle && activeDocx.elements.none { it is DocxBlockElement.Paragraph && it.style.equals("Title", ignoreCase = true) }) {
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

                            for (element in activeDocx.elements) {
                                when (element) {
                                    is DocxBlockElement.Paragraph -> {
                                        RenderDocxParagraph(element, fontFamily)
                                    }
                                    is DocxBlockElement.Table -> {
                                        RenderDocxTable(element, fontFamily)
                                    }
                                    is DocxBlockElement.DrawingImage -> {
                                        RenderDocxDrawing(element)
                                    }
                                }
                            }
                        } else {
                            // ── Fallback to AST Rendering during compilation ───────────
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
}

@Composable
private fun RenderDocxParagraph(
    para: DocxBlockElement.Paragraph,
    fontFamily: FontFamily
) {
    if (para.isThematicBreak) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = Color(0xFFDDDDDD)
        )
        return
    }

    if (para.style.equals("Title", ignoreCase = true)) {
        Text(
            text = buildDocxRunsAnnotatedString(para.runs, defaultBold = true),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )
        return
    }

    if (para.style.equals("Heading1", ignoreCase = true)) {
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = buildDocxRunsAnnotatedString(para.runs, defaultBold = true),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = fontFamily,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        return
    }

    if (para.style.equals("Heading2", ignoreCase = true)) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = buildDocxRunsAnnotatedString(para.runs, defaultBold = true),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = fontFamily,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        return
    }

    if (para.style.equals("Heading3", ignoreCase = true)) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildDocxRunsAnnotatedString(para.runs, defaultBold = true),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF222222),
            fontFamily = fontFamily,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        return
    }

    if (para.isBullet || para.isOrdered) {
        val glyphs = listOf("•", "◦", "▪")
        val bullet = if (para.isOrdered) "${para.ilvl + 1}." else glyphs[para.ilvl % 3]
        val indentDp = (12 + para.ilvl * 16).dp
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
                text = buildDocxRunsAnnotatedString(para.runs),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFF222222),
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    if (para.isBlockquote) {
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
                    Text(
                        text = buildDocxRunsAnnotatedString(para.runs, defaultItalic = true),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF555555),
                        fontFamily = fontFamily
                    )
                }
            }
        }
        return
    }

    if (para.isCodeBlock) {
        Surface(
            color = Color(0xFFF5F5F5),
            border = BorderStroke(1.dp, Color(0xFFBBBBBB)),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = buildDocxRunsAnnotatedString(para.runs),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF333333),
                modifier = Modifier.padding(8.dp)
            )
        }
        return
    }

    if (para.isEditorialCallout) {
        Surface(
            color = Color(0xFFF8F9FA),
            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = buildDocxRunsAnnotatedString(para.runs, defaultItalic = true),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF777777),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
        return
    }

    // Standard body paragraph
    val textAlign = if (para.isRtl) {
        TextAlign.End
    } else when (para.alignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }

    Text(
        text = buildDocxRunsAnnotatedString(para.runs),
        fontSize = 11.sp,
        lineHeight = 17.sp,
        color = Color(0xFF222222),
        fontFamily = fontFamily,
        textAlign = textAlign,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun RenderDocxTable(
    table: DocxBlockElement.Table,
    fontFamily: FontFamily
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, Color(0xFFCCCCCC))
    ) {
        for ((rowIdx, row) in table.rows.withIndex()) {
            val isHeaderRow = row.any { it.isHeader } || rowIdx == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isHeaderRow) Color(0xFFEEEEEE) else Color.Transparent)
                    .padding(6.dp)
            ) {
                for (cell in row) {
                    val textAlign = when (cell.alignment) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        for (p in cell.paragraphs) {
                            Text(
                                text = buildDocxRunsAnnotatedString(p.runs, defaultBold = isHeaderRow),
                                fontSize = 10.sp,
                                fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                textAlign = textAlign,
                                color = if (isHeaderRow) Color(0xFF111111) else Color(0xFF222222),
                                fontFamily = fontFamily,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                thickness = if (isHeaderRow) 1.dp else 0.5.dp,
                color = if (isHeaderRow) Color(0xFFCCCCCC) else Color(0xFFE8E8E8)
            )
        }
    }
}

@Composable
private fun RenderDocxDrawing(drawing: DocxBlockElement.DrawingImage) {
    if (drawing.imageBytes != null && drawing.imageBytes.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = drawing.imageBytes,
                    contentDescription = drawing.caption ?: "OpenXML Embedded Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!drawing.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = drawing.caption,
                    fontSize = 9.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun buildDocxRunsAnnotatedString(
    runs: List<DocxRunModel>,
    defaultBold: Boolean = false,
    defaultItalic: Boolean = false
) = buildAnnotatedString {
    for (r in runs) {
        val textColor = if (r.isHyperlink) {
            Color(0xFF0066CC)
        } else if (r.colorHex != null) {
            runCatching { Color(android.graphics.Color.parseColor("#${r.colorHex}")) }.getOrDefault(Color(0xFF222222))
        } else {
            Color.Unspecified
        }

        val font = if (r.fontName.equals("Courier New", ignoreCase = true)) FontFamily.Monospace else null
        val spanStyle = SpanStyle(
            fontWeight = if (r.isBold || defaultBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (r.isItalic || defaultItalic) FontStyle.Italic else FontStyle.Normal,
            color = textColor,
            fontFamily = font,
            textDecoration = if (r.isUnderline || r.isHyperlink) TextDecoration.Underline else TextDecoration.None
        )
        withStyle(spanStyle) {
            append(r.text)
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
