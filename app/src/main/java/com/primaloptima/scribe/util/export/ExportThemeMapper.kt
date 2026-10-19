package com.primaloptima.scribe.util.export

import android.content.Context
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Maps Scribe's active theme colors and export configurations to CSS styles,
 * vector SVG ornaments, and DOCX formatting tokens.
 */
object ExportThemeMapper {

    /**
     * Resolves the effective ThemeColors, either from the active theme or a sensible default.
     */
    fun resolveThemeColors(context: Context?): ThemeColors {
        if (context == null) return ThemeColors()
        return try {
            val tm = ThemeManager(context)
            tm.activeTheme().colors
        } catch (_: Throwable) {
            ThemeColors()
        }
    }

    /**
     * Cleans a hex color string for OpenXML (e.g. "#1A5276" -> "1A5276").
     */
    fun cleanHexForDocx(hex: String, fallback: String = "222222"): String {
        val clean = hex.trim().removePrefix("#")
        return if (clean.length == 6 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            clean.uppercase()
        } else {
            fallback
        }
    }

    /**
     * Resolves the primary font family for CSS rendering.
     */
    fun resolveCssFontFamily(fontChoice: FontChoice): String {
        return when (fontChoice) {
            FontChoice.INHERIT_THEME -> "Georgia, 'Times New Roman', serif"
            FontChoice.CLASSIC_SERIF -> "Georgia, 'Times New Roman', Cambria, serif"
            FontChoice.MODERN_SANS -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
            FontChoice.MONOSPACE -> "'JetBrains Mono', 'Roboto Mono', 'Courier New', monospace"
        }
    }

    /**
     * Resolves font name for OpenXML DOCX.
     */
    fun resolveDocxFontName(fontChoice: FontChoice): String {
        return fontChoice.docxFontName
    }

    /**
     * Returns resolution-independent SVG markup for a given manuscript ornament id.
     * Matches the vector paths defined in Scribe's compose OrnamentRegistry.
     */
    fun renderOrnamentSvg(ornamentId: String): String {
        return OrnamentCatalog.getSvgMarkup(ornamentId)
    }

    /**
     * Generates standard publication CSS for HTML, PDF, and EPUB formats.
     */
    fun generateExportCss(
        config: ExportConfig,
        themeColors: ThemeColors,
        isForPrintPdf: Boolean = false,
        isForEpub: Boolean = false
    ): String {
        val fontFamily = resolveCssFontFamily(config.fontChoice)
        val fontSizePt = config.fontSizePt
        val lineSpacing = config.lineSpacingMultiplier

        // Strict Theme Decoupling:
        // Publication documents default to neutral, professional print standards:
        // White page #FFFFFF, dark ink #1A1A1A, neutral border #CCCCCC.
        // Accent color uses #1A5276 (classic editorial blue) unless fontChoice is explicitly INHERIT_THEME.
        val primaryColorHex = if (config.fontChoice == FontChoice.INHERIT_THEME) {
            themeColors.accent.ifBlank { "#1A5276" }
        } else {
            "#1A5276"
        }
        val textColorHex = "#1A1A1A"
        val bgColorHex = "#FFFFFF"
        val borderColorHex = "#CCCCCC"

        val printPageRule = """
        @page {
            size: ${config.paperSize.cssSize};
            margin: ${config.marginPreset.cssMargin};
            @bottom-right {
                content: counter(page);
                font-family: $fontFamily;
                font-size: 9pt;
                color: #777777;
            }
        }
        @media print {
            body {
                padding: 0 !important;
                margin: 0 !important;
                background-color: #FFFFFF !important;
                color: #1A1A1A !important;
            }
            h1, h2, h3, h4, h5, h6 {
                page-break-after: avoid;
                break-after: avoid;
            }
            table, figure, pre, blockquote {
                page-break-inside: avoid;
                break-inside: avoid;
            }
        }
        """.trimIndent()

        val dropCapCss = if (config.dropCapStyle != DropCapStyle.DISABLED) {
            """
        .has-dropcap::first-letter {
            font-family: $fontFamily;
            font-weight: 700;
            color: var(--scribe-primary);
            float: left;
            font-size: 3.2em;
            line-height: 0.82;
            margin-top: 0.08em;
            margin-right: 0.12em;
            margin-bottom: -0.05em;
        }
        @supports (initial-letter: 3) or (-webkit-initial-letter: 3) {
            .has-dropcap::first-letter {
                -webkit-initial-letter: 3;
                initial-letter: 3;
                float: none;
                margin: 0 0.15em 0 0;
            }
        }
        [dir="rtl"] .has-dropcap::first-letter {
            float: none !important;
            font-size: inherit !important;
            font-weight: inherit !important;
            color: inherit !important;
            margin: 0 !important;
            initial-letter: normal !important;
        }
            """.trimIndent()
        } else {
            ""
        }

        val paragraphCss = if (config.firstLineIndent) {
            """
        p {
            margin-top: 0;
            margin-bottom: 0;
            text-indent: 1.5em;
            text-align: justify;
            hyphens: auto;
        }
        .manuscript-header + p,
        .manuscript-ornament + p,
        h1 + p, h2 + p, h3 + p, h4 + p, h5 + p, h6 + p,
        p:first-of-type,
        p.has-dropcap {
            text-indent: 0;
        }
            """.trimIndent()
        } else {
            """
        p {
            margin-top: 0;
            margin-bottom: 0.8em;
            text-align: justify;
            hyphens: auto;
        }
            """.trimIndent()
        }

        return """
        $printPageRule
        :root {
            --scribe-primary: $primaryColorHex;
            --scribe-text: $textColorHex;
            --scribe-bg: $bgColorHex;
            --scribe-border: $borderColorHex;
        }
        body {
            font-family: $fontFamily;
            font-size: ${fontSizePt}pt;
            line-height: $lineSpacing;
            color: var(--scribe-text);
            background-color: var(--scribe-bg);
            margin: 0;
            padding: ${if (isForPrintPdf) "0" else "24px"};
            -webkit-font-smoothing: antialiased;
            text-rendering: optimizeLegibility;
        }
        .manuscript-header {
            text-align: center;
            margin: ${if (isForPrintPdf) "1em 0 1.5em 0" else "2em 0 2.5em 0"};
            page-break-after: avoid;
            break-after: avoid;
        }
        .title-kicker {
            font-family: $fontFamily;
            font-size: ${fontSizePt * 1.15}pt;
            font-weight: 600;
            letter-spacing: 0.15em;
            text-transform: uppercase;
            color: var(--scribe-primary);
            margin: 0 0 0.4em 0;
            text-align: center;
        }
        .document-title {
            font-family: $fontFamily;
            font-size: ${fontSizePt * 1.9}pt;
            font-weight: 700;
            line-height: 1.25;
            color: var(--scribe-text);
            margin: 0 0 0.6em 0;
            text-align: center;
        }
        .manuscript-ornament {
            display: flex;
            justify-content: center;
            align-items: center;
            margin: 1.2em auto 1.8em auto;
            color: var(--scribe-primary);
            opacity: 0.65;
            page-break-after: avoid;
            break-after: avoid;
        }
$dropCapCss
        h1, h2, h3, h4, h5, h6 {
            color: var(--scribe-primary);
            font-family: $fontFamily;
            font-weight: 700;
            line-height: 1.25;
            page-break-after: avoid;
            break-after: avoid;
        }
        h1 {
            font-size: ${fontSizePt * 1.8}pt;
            margin-top: 1.5em;
            margin-bottom: 0.6em;
            text-align: center;
        }
        h2 {
            font-size: ${fontSizePt * 1.4}pt;
            margin-top: 1.4em;
            margin-bottom: 0.5em;
            border-bottom: 1px solid var(--scribe-border);
            padding-bottom: 4px;
        }
        h3 {
            font-size: ${fontSizePt * 1.2}pt;
            margin-top: 1.2em;
            margin-bottom: 0.4em;
        }
        $paragraphCss
        blockquote {
            margin: 1.2em 0;
            padding: 0.6em 1.2em;
            border-left: 3.5px solid var(--scribe-primary);
            background-color: rgba(0, 0, 0, 0.03);
            font-style: italic;
            color: inherit;
        }
        ul, ol {
            margin: 0.8em 0;
            padding-left: 1.8em;
        }
        li {
            margin-bottom: 0.3em;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin: 1.5em 0;
            font-size: ${fontSizePt * 0.95}pt;
            page-break-inside: avoid;
        }
        th, td {
            border: 1px solid var(--scribe-border);
            padding: 8px 12px;
            text-align: left;
        }
        th {
            background-color: rgba(0, 0, 0, 0.05);
            font-weight: bold;
            color: var(--scribe-primary);
        }
        tr:nth-child(even) td {
            background-color: rgba(0, 0, 0, 0.02);
        }
        hr {
            border: 0;
            height: 1px;
            background: var(--scribe-border);
            margin: 2em 0;
        }
        pre, code {
            font-family: 'JetBrains Mono', 'Roboto Mono', 'Courier New', monospace;
            background-color: rgba(0, 0, 0, 0.04);
            border-radius: 4px;
        }
        code {
            font-size: 0.9em;
            padding: 2px 4px;
        }
        pre {
            padding: 12px;
            overflow-x: auto;
            line-height: 1.4;
            margin: 1em 0;
            page-break-inside: avoid;
        }
        pre code {
            padding: 0;
            background: transparent;
        }
        figure {
            margin: 2em 0;
            text-align: center;
            page-break-inside: avoid;
        }
        figure img {
            max-width: 100%;
            height: auto;
            border-radius: 6px;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
        }
        figcaption {
            margin-top: 8px;
            font-size: ${fontSizePt * 0.85}pt;
            font-style: italic;
            color: #666666;
            text-align: center;
        }
        .page-break {
            page-break-after: always;
            break-after: page;
        }
        /* RTL and Bidirectional Support */
        [dir="rtl"] {
            direction: rtl;
            text-align: right;
            unicode-bidi: embed;
        }
        [dir="ltr"] {
            direction: ltr;
            text-align: left;
            unicode-bidi: embed;
        }
        blockquote[dir="rtl"] {
            border-left: none;
            border-right: 3.5px solid var(--scribe-primary);
        }
        .editorial-callout {
            border: 1px dashed var(--scribe-border, #999999);
            padding: 14px;
            margin: 20px 0;
            text-align: center;
            font-style: italic;
            color: #666666;
            background-color: rgba(0, 0, 0, 0.02);
            border-radius: 4px;
        }
        """.trimIndent()
    }
}
