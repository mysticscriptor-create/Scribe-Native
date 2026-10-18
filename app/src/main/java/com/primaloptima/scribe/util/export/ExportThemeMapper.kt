package com.primaloptima.scribe.util.export

import android.content.Context
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Maps Scribe's active theme colors and export configurations to CSS styles and DOCX formatting tokens.
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
        val primaryColorHex = themeColors.accent.ifBlank { "#1A5276" }
        val textColorHex = if (isForPrintPdf || isForEpub) "#1A1A1A" else themeColors.text.ifBlank { "#222222" }
        val bgColorHex = if (isForPrintPdf || isForEpub) "#FFFFFF" else themeColors.background.ifBlank { "#FFFFFF" }
        val borderColorHex = themeColors.borderSubtle.ifBlank { "#DDDDDD" }

        val printPageRule = if (isForPrintPdf) """
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
        """ else ""

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
        p {
            margin-top: 0;
            margin-bottom: 0.8em;
            text-align: justify;
            hyphens: auto;
        }
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
