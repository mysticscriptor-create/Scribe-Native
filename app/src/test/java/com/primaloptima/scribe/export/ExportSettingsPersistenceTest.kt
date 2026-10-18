package com.primaloptima.scribe.export

import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.ExportThemeMapper
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.ImageQuality
import com.primaloptima.scribe.util.export.MarginPreset
import com.primaloptima.scribe.util.export.PaperSize
import com.primaloptima.scribe.util.model.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsPersistenceTest {

    @Test
    fun testExportConfigDefaultValues() {
        val config = ExportConfig()

        assertEquals(ExportFormat.PDF, config.format)
        assertEquals(PaperSize.A4, config.paperSize)
        assertEquals(MarginPreset.STANDARD, config.marginPreset)
        assertEquals(FontChoice.INHERIT_THEME, config.fontChoice)
        assertEquals(11, config.fontSizePt)
        assertEquals(1.6f, config.lineSpacingMultiplier, 0.001f)
        assertTrue(config.includeCoverTitle)
        assertTrue(config.includeTableOfContents)
        assertEquals(ImageQuality.HIGH_RES, config.imageQuality)
        assertEquals("", config.authorName)
    }

    @Test
    fun testExportThemeMapperNeutralDefaults() {
        val darkThemeColors = ThemeColors(
            background = "#050505",
            surface = "#121212",
            text = "#E0E0E0",
            accent = "#FF6B6B"
        )

        // 1. Classical Serif typography -> strictly neutral publication colors
        val serifConfig = ExportConfig(fontChoice = FontChoice.CLASSIC_SERIF)
        val serifCss = ExportThemeMapper.generateExportCss(serifConfig, darkThemeColors)

        assertTrue("Background must be clean white", serifCss.contains("--scribe-bg: #FFFFFF;"))
        assertTrue("Text must be clean dark ink", serifCss.contains("--scribe-text: #1A1A1A;"))
        assertTrue("Primary color must be classic blue", serifCss.contains("--scribe-primary: #1A5276;"))
        assertFalse("Must never bleed dark background into document", serifCss.contains("#050505"))

        // 2. Inherit Theme -> inherits accent, but background and text stay strictly neutral
        val inheritConfig = ExportConfig(fontChoice = FontChoice.INHERIT_THEME)
        val inheritCss = ExportThemeMapper.generateExportCss(inheritConfig, darkThemeColors)

        assertTrue("Accent is inherited", inheritCss.contains("--scribe-primary: #FF6B6B;"))
        assertTrue("Background remains neutral white", inheritCss.contains("--scribe-bg: #FFFFFF;"))
        assertTrue("Text remains neutral ink", inheritCss.contains("--scribe-text: #1A1A1A;"))
    }

    @Test
    fun testDocxFontMapping() {
        assertEquals("Georgia", ExportThemeMapper.resolveDocxFontName(FontChoice.CLASSIC_SERIF))
        assertEquals("Calibri", ExportThemeMapper.resolveDocxFontName(FontChoice.MODERN_SANS))
        assertEquals("Courier New", ExportThemeMapper.resolveDocxFontName(FontChoice.MONOSPACE))
        assertEquals("Calibri", ExportThemeMapper.resolveDocxFontName(FontChoice.INHERIT_THEME))
    }

    @Test
    fun testPaperSizeAndMarginDimensions() {
        assertEquals(210f, PaperSize.A4.widthMm, 0.1f)
        assertEquals(297f, PaperSize.A4.heightMm, 0.1f)

        assertEquals(215.9f, PaperSize.LETTER.widthMm, 0.1f)
        assertEquals(279.4f, PaperSize.LETTER.heightMm, 0.1f)

        assertEquals(25.4f, MarginPreset.STANDARD.topMm, 0.1f)
        assertEquals(12.7f, MarginPreset.COMPACT.topMm, 0.1f)
    }
}
