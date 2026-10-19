package com.primaloptima.scribe.util.export

import android.content.Context
import android.util.Base64
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.export.pdf.PdfEngine
import com.primaloptima.scribe.util.export.pdf.PrintedPdfDocumentEngine
import com.primaloptima.scribe.util.image.ImageMimeUtil
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.File

/**
 * Handles HTML publication compilation and standards-compliant PDF generation using
 * Scribe's AST Markdown engine, print-media CSS layouts, and [PrintedPdfDocumentEngine].
 */
object HtmlPdfExporter {

    /**
     * Builds a self-contained HTML document with embedded styles and Base64 images
     * using the unified [ScribeMarkdownEngine] AST pipeline.
     */
    fun buildHtmlDocument(
        context: Context?,
        note: Note,
        config: ExportConfig,
        isForPrintPdf: Boolean = false
    ): String {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val header = ManuscriptTitleHeader.from(note, config.ornamentId)
        val titleText = header.toTocLabel(fallback = note.name.ifBlank { "Untitled Manuscript" })
        val titleEscaped = MarkdownUtil.escapeHtml(titleText)
        val css = ExportThemeMapper.generateExportCss(config, themeColors, isForPrintPdf = isForPrintPdf)
        val langMeta = EpubExporter.detectLanguageAndDirection(note.content)

        val enableDropCaps = config.dropCapStyle != DropCapStyle.DISABLED && langMeta.documentDirection != "rtl"

        val bodyHtml = ScribeMarkdownEngine.toHtml(
            markdown = note.content,
            docDirection = langMeta.documentDirection,
            enableDropCaps = enableDropCaps
        ) { src ->
            resolveImageToBase64(context, src, note.bookId, note.id) ?: "MISSING_ASSET"
        }

        val coverTitleHtml = if (config.includeCoverTitle && header.isPresent) {
            val kickerHtml = if (header.hasPrimary) {
                """<p class="title-kicker">${MarkdownUtil.escapeHtml(header.primaryTitle)}</p>"""
            } else ""
            val mainTitleHtml = if (header.hasSecondary) {
                """<h1 class="document-title">${MarkdownUtil.escapeHtml(header.secondaryTitle)}</h1>"""
            } else if (!header.hasPrimary) {
                """<h1 class="document-title">$titleEscaped</h1>"""
            } else ""
            val ornamentSvg = if (config.ornamentId != "none") {
                val svg = ExportThemeMapper.renderOrnamentSvg(config.ornamentId)
                if (svg.isNotBlank()) """<div class="manuscript-ornament" aria-hidden="true">$svg</div>""" else ""
            } else ""

            """
              <header class="manuscript-header">
                $kickerHtml
                $mainTitleHtml
                $ornamentSvg
              </header>
            """.trimIndent()
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="${langMeta.primaryLanguage}" dir="${langMeta.documentDirection}">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>$titleEscaped</title>
              <style>
            $css
              </style>
            </head>
            <body>
              $coverTitleHtml
              $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    fun resolveImageToBase64(
        context: Context?,
        uri: String,
        bookId: String?,
        noteId: String
    ): String? {
        val bytes = if (context != null) VaultImageManager.getImageBytes(context, uri, bookId, noteId) else null
        if (bytes == null) return null
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mime = ImageMimeUtil.detect(bytes, uri).mimeType
        return "data:$mime;base64,$base64"
    }

    /**
     * Headless background generation of a genuine print-ready PDF file into [outputFile]
     * using the legal, public [PrintedPdfDocumentEngine].
     */
    fun renderPdfToFile(
        context: Context,
        note: Note,
        config: ExportConfig,
        outputFile: File,
        engine: PdfEngine = PrintedPdfDocumentEngine,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        engine.renderPdfToFile(context, note, config, outputFile, onComplete)
    }
}
