package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportThemeMapper
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.PlainEmitter
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import com.primaloptima.scribe.util.export.ScribeMarkdownEngine
import com.primaloptima.scribe.util.image.ImageMimeUtil
import com.primaloptima.scribe.util.model.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlAndTextExportTest {

    @Test
    fun testHtmlDynamicLanguageAndDirection() {
        val urduContent = "یہ ایک اردو زبان کی دستاویز ہے جس کا متن دائیں سے بائیں چلے گا۔"
        val urduMeta = EpubExporter.detectLanguageAndDirection(urduContent)
        assertEquals("ur", urduMeta.primaryLanguage)
        assertEquals("rtl", urduMeta.documentDirection)

        val englishContent = "This is a clean literary manuscript written in English for standard publication."
        val englishMeta = EpubExporter.detectLanguageAndDirection(englishContent)
        assertEquals("en", englishMeta.primaryLanguage)
        assertEquals("ltr", englishMeta.documentDirection)
    }

    @Test
    fun testHtmlMissingAssetEditorialCallout() {
        val content = "![Ancient Castle Map](vault-img://missing_asset_123.jpg)"
        val html = ScribeMarkdownEngine.toHtml(content) { "MISSING_ASSET" }

        assertTrue("Missing asset must emit editorial callout class", html.contains("editorial-callout"))
        assertTrue("Callout must display the alt text as illustration note", html.contains("[Illustration: Ancient Castle Map]"))
        assertFalse("Should not leave raw vault URI", html.contains("vault-img://"))
    }

    @Test
    fun testHtmlPrintMediaCssRules() {
        val config = ExportConfig(fontChoice = FontChoice.CLASSIC_SERIF)
        val colors = ThemeColors(background = "#121212", text = "#FFFFFF") // Dark mode editor theme
        val css = ExportThemeMapper.generateExportCss(config, colors, isForPrintPdf = false)

        // Strict Theme Decoupling checks
        assertTrue("CSS should include @media print", css.contains("@media print"))
        assertTrue("CSS must contain white background for publication body", css.contains("--scribe-bg: #FFFFFF;"))
        assertTrue("CSS must contain dark ink for publication text", css.contains("--scribe-text: #1A1A1A;"))
        assertTrue("CSS must enforce page break avoidance", css.contains("page-break-after: avoid"))
        assertTrue("CSS must enforce inside page break avoidance", css.contains("page-break-inside: avoid"))
    }

    @Test
    fun testPlainTextCleanProse() {
        val markdown = """
            # Chapter 1: The #1 Champion

            This is **bold prose**, *italic thoughts*, and `inline code`.

            > A wise man once said that knowledge is power.

            - First expedition
            - Second expedition

            ![Expedition Banner](vault-img://map.png)
        """.trimIndent()

        val plain = ScribeMarkdownEngine.toPlainText(markdown)

        // Heading level 1 should be uppercase
        assertTrue("H1 heading should be capitalized", plain.contains("CHAPTER 1: THE #1 CHAMPION"))
        // Prose should be stripped of markdown tokens but keep literal #
        assertTrue("Prose should be stripped of syntax", plain.contains("This is bold prose, italic thoughts, and inline code."))
        // Blockquote should be indented, not prefixed with raw >
        assertTrue("Blockquote should be indented cleanly", plain.contains("  A wise man once said that knowledge is power."))
        // Lists should have - markers
        assertTrue("Lists should be formatted", plain.contains("- First expedition"))
        assertTrue("Lists should be formatted", plain.contains("- Second expedition"))
        // Image should be converted to clean editorial marker
        assertTrue("Image should become editorial marker", plain.contains("[Illustration: Expedition Banner]"))

        // Must not contain raw Markdown syntax
        assertFalse("No raw bold asterisks", plain.contains("**"))
        assertFalse("No raw code backticks", plain.contains("`"))
    }

    @Test
    fun testPlainTextUrduAndArabicUnicodeUtf8() {
        val urduText = "فلسفہ و ادب کی تاریخ میں یہ باب سنہری حروف سے لکھا جائے گا۔"
        val plain = ScribeMarkdownEngine.toPlainText(urduText)

        assertEquals("Plain text must preserve Urdu characters exactly", urduText, plain)

        val utf8Bytes = plain.toByteArray(Charsets.UTF_8)
        val decoded = String(utf8Bytes, Charsets.UTF_8)
        assertEquals("UTF-8 encoding must not alter Unicode glyphs", urduText, decoded)
    }

    @Test
    fun testPlainTextCoverTitlePrepend() {
        val note = Note(
            id = "test-title-note",
            name = "The Great Odyssey",
            content = "Once upon a time in a distant land..."
        )

        // 1. With cover title enabled: uppercase title prepended
        val configWithTitle = ExportConfig(includeCoverTitle = true)
        val plainWithTitle = ExportHelper.buildPlainText(note, configWithTitle)
        assertTrue("Must prepend uppercase title", plainWithTitle.startsWith("THE GREAT ODYSSEY\n\n"))
        assertTrue("Must contain body prose", plainWithTitle.contains("Once upon a time in a distant land..."))

        // 2. With cover title disabled: no title prepended
        val configNoTitle = ExportConfig(includeCoverTitle = false)
        val plainNoTitle = ExportHelper.buildPlainText(note, configNoTitle)
        assertFalse("Must not prepend uppercase title", plainNoTitle.startsWith("THE GREAT ODYSSEY"))
        assertTrue("Must start with body prose directly", plainNoTitle.startsWith("Once upon a time"))
    }

    @Test
    fun testImageMimeUtilMagicByteDetection() {
        // PNG Header (89 50 4E 47)
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val pngDetected = ImageMimeUtil.detect(pngBytes, "vault-img://uuid_without_ext")
        assertEquals("image/png", pngDetected.mimeType)
        assertEquals("png", pngDetected.extension)

        // JPEG Header (FF D8 FF)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val jpegDetected = ImageMimeUtil.detect(jpegBytes, "vault-img://uuid_without_ext")
        assertEquals("image/jpeg", jpegDetected.mimeType)
        assertEquals("jpg", jpegDetected.extension)

        // GIF Header (GIF8)
        val gifBytes = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte(), 0x39.toByte(), 0x61.toByte())
        val gifDetected = ImageMimeUtil.detect(gifBytes, "vault-img://uuid_without_ext")
        assertEquals("image/gif", gifDetected.mimeType)
        assertEquals("gif", gifDetected.extension)

        // WebP Header (RIFF....WEBP)
        val webpBytes = "RIFF1234WEBPVP8 ".toByteArray(Charsets.US_ASCII)
        val webpDetected = ImageMimeUtil.detect(webpBytes, "vault-img://uuid_without_ext")
        assertEquals("image/webp", webpDetected.mimeType)
        assertEquals("webp", webpDetected.extension)

        // SVG Header (<svg>)
        val svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"></svg>".toByteArray(Charsets.UTF_8)
        val svgDetected = ImageMimeUtil.detect(svgBytes, "vault-img://uuid_without_ext")
        assertEquals("image/svg+xml", svgDetected.mimeType)
        assertEquals("svg", svgDetected.extension)

        // Extension Fallback when bytes are null
        val fallbackPng = ImageMimeUtil.detect(null, "artwork.png")
        assertEquals("image/png", fallbackPng.mimeType)
        assertEquals("png", fallbackPng.extension)
    }
}
