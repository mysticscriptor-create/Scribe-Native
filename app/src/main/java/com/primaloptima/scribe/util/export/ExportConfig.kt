package com.primaloptima.scribe.util.export

/**
 * Modern typed configuration models for Scribe's multi-format export pipeline.
 */

enum class ExportFormat(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    PDF("pdf", "PDF", ".pdf", "application/pdf", "Print-ready document with high-resolution typography & illustrations"),
    EPUB("epub", "EPUB", ".epub", "application/epub+zip", "Standard reflowable EPUB 3.0 eBook with bundled styles, TOC & illustrations"),
    DOCX("docx", "DOCX", ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Microsoft Word document with embedded OpenXML styles, lists & tables"),
    HTML("html", "HTML", ".html", "text/html", "Standalone web page with inline self-contained illustrations & responsive CSS"),
    MD("md", "Markdown", ".md", "text/markdown", "Clean Markdown manuscript source text (packaged with images as ZIP when images are present)"),
    TXT("txt", "Plain Text", ".txt", "text/plain", "Clean UTF-8 text format with editorial illustration notes");

    companion object {
        fun fromId(id: String): ExportFormat =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PDF
    }
}

enum class PaperSize(
    val label: String,
    val widthMm: Float,
    val heightMm: Float,
    val cssSize: String,
    val dxaWidth: Long,
    val dxaHeight: Long
) {
    A4("ISO A4", 210f, 297f, "210mm 297mm", 11906L, 16838L),
    LETTER("US Letter", 215.9f, 279.4f, "8.5in 11in", 12240L, 15840L)
}

enum class MarginPreset(
    val label: String,
    val topMm: Float,
    val rightMm: Float,
    val bottomMm: Float,
    val leftMm: Float,
    val cssMargin: String,
    val dxaTop: Long,
    val dxaRight: Long,
    val dxaBottom: Long,
    val dxaLeft: Long
) {
    STANDARD("Standard (1 inch)", 25.4f, 25.4f, 25.4f, 25.4f, "25.4mm", 1440L, 1440L, 1440L, 1440L),
    EDITORIAL("Editorial Book (Gutter)", 22f, 30f, 22f, 20f, "22mm 30mm 22mm 20mm", 1247L, 1700L, 1247L, 1134L),
    COMPACT("Compact (0.5 inch)", 12.7f, 12.7f, 12.7f, 12.7f, "12.7mm", 720L, 720L, 720L, 720L)
}

enum class FontChoice(
    val label: String,
    val cssFontFamily: String,
    val docxFontName: String
) {
    INHERIT_THEME("Inherit Editor Theme", "inherit", "Calibri"),
    CLASSIC_SERIF("Classic Literary Serif", "Georgia, 'Times New Roman', Cambria, serif", "Georgia"),
    MODERN_SANS("Modern Editorial Sans", "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif", "Calibri"),
    MONOSPACE("Technical Monospace", "'JetBrains Mono', 'Roboto Mono', 'Courier New', monospace", "Courier New")
}

enum class ImageQuality(
    val label: String,
    val maxDimension: Int,
    val compressionQuality: Int
) {
    HIGH_RES("Print Quality (High)", 2400, 95),
    COMPRESSED("Web Standard (Fast)", 1400, 80)
}

/**
 * Complete immutable configuration state driving export generation and previews.
 */
data class ExportConfig(
    val format: ExportFormat = ExportFormat.PDF,
    val paperSize: PaperSize = PaperSize.A4,
    val marginPreset: MarginPreset = MarginPreset.STANDARD,
    val fontChoice: FontChoice = FontChoice.INHERIT_THEME,
    val fontSizePt: Int = 11,
    val lineSpacingMultiplier: Float = 1.6f,
    val includeCoverTitle: Boolean = true,
    val includeTableOfContents: Boolean = true,
    val imageQuality: ImageQuality = ImageQuality.HIGH_RES
)
