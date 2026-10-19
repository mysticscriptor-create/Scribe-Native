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
        return when (ornamentId) {
            "classic_diamond" -> """
                <svg width="160" height="20" viewBox="0 0 160 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="10" x2="68" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M80 3.5 L87 10 L80 16.5 L73 10 Z" fill="currentColor"/>
                  <circle cx="70" cy="10" r="1.3" fill="currentColor"/>
                  <circle cx="90" cy="10" r="1.3" fill="currentColor"/>
                  <line x1="92" y1="10" x2="140" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "fleuron" -> """
                <svg width="140" height="24" viewBox="0 0 140 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="55" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 4 C65 8, 62 14, 70 20 C78 14, 75 8, 70 4 Z" fill="currentColor"/>
                  <path d="M70 12 C64 12, 58 8, 62 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M70 12 C76 12, 82 8, 78 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <line x1="85" y1="12" x2="125" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "asterism" -> """
                <svg width="120" height="22" viewBox="0 0 120 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="45" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <text x="60" y="8" font-size="12" text-anchor="middle" fill="currentColor" font-family="serif">⁂</text>
                  <line x1="75" y1="11" x2="105" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "celtic_knot" -> """
                <svg width="150" height="22" viewBox="0 0 150 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="52" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <rect x="68" y="4" width="14" height="14" rx="2" transform="rotate(45 75 11)" stroke="currentColor" stroke-width="1.4" fill="none"/>
                  <circle cx="75" cy="11" r="4" stroke="currentColor" stroke-width="1" fill="none"/>
                  <line x1="98" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "minimal_rule" -> """
                <svg width="160" height="12" viewBox="0 0 160 12" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="6" x2="74" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                  <circle cx="80" cy="6" r="2" fill="currentColor"/>
                  <line x1="86" y1="6" x2="140" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "quatrefoil" -> """
                <svg width="150" height="24" viewBox="0 0 150 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="58" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <circle cx="75" cy="8" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="16" r="3.5" fill="currentColor"/>
                  <circle cx="71" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="79" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="12" r="1.5" fill="white"/>
                  <line x1="92" y1="12" x2="135" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            "laurel_branch" -> """
                <svg width="160" height="22" viewBox="0 0 160 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="55" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 11 C72 7, 78 7, 80 11 C78 15, 72 15, 70 11 Z" fill="currentColor"/>
                  <circle cx="65" cy="11" r="2" fill="currentColor"/>
                  <circle cx="85" cy="11" r="2" fill="currentColor"/>
                  <line x1="95" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()

            else -> "" // "none" or unknown
        }
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
