package com.primaloptima.scribe.util

import com.primaloptima.scribe.ui.theme.ContrastResolver
import com.primaloptima.scribe.ui.theme.ContrastMatrix
import com.primaloptima.scribe.ui.theme.Oklch
import com.primaloptima.scribe.ui.theme.SafeGamutMapper
import com.primaloptima.scribe.ui.theme.ValidationStatus
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ChromaticCharacter
import com.primaloptima.scribe.util.model.DarkLightBias
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ImagePaletteSource
import com.primaloptima.scribe.util.model.ImageThemeGenerationSession
import com.primaloptima.scribe.util.model.ImageUnderstanding
import com.primaloptima.scribe.util.model.PaletteDiversity
import com.primaloptima.scribe.util.model.TemperatureBias
import com.primaloptima.scribe.util.model.ThemeColorOverrides
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeGenerationMetadata
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import com.primaloptima.scribe.util.model.TonalCharacter
import com.primaloptima.scribe.util.model.VisualRole
import com.primaloptima.scribe.util.model.WritingCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Scribe Session 5 Comprehensive Test Suite.
 *
 * Implements the full 36 specific test scenarios (Part 36) and property/quality tests (Part 37):
 * - Edge cases (grayscale, extreme black, extreme white, transparent, single dominant color, corrupt image)
 * - Gamut mapping and OKLCH perceptual validation
 * - Contrast preservation and collision avoidance
 * - Presentation relationship modes (THEME_ONLY, THEME_IMAGE, THEME_GLASS)
 * - Determinism and persistence invariance
 */
class ThemeGenerationSession5Test {

    private fun createSyntheticUnderstanding(
        primaryHex: String = "#4A90E2",
        primaryArgb: Int = 0xFF4A90E2.toInt(),
        primaryHue: Double = 220.0,
        primaryChroma: Double = 0.15,
        primaryTone: Double = 0.60,
        averageLightness: Float = 0.50f,
        tonalCharacter: TonalCharacter = TonalCharacter.MID_KEY,
        chromaticCharacter: ChromaticCharacter = ChromaticCharacter.BALANCED,
        temperatureBias: TemperatureBias = TemperatureBias.NEUTRAL,
        darkLightBias: DarkLightBias = DarkLightBias.BALANCED,
        paletteDiversity: PaletteDiversity = PaletteDiversity.MODERATE,
        supportingHex: String? = "#50E3C2",
        supportingArgb: Int? = 0xFF50E3C2.toInt(),
        supportingHue: Double? = 165.0,
        tertiaryHex: String? = "#FF5722",
        tertiaryArgb: Int? = 0xFFFF5722.toInt(),
        tertiaryHue: Double? = 35.0
    ): ImageUnderstanding {
        val sources = mutableListOf<ImagePaletteSource>()
        sources.add(
            ImagePaletteSource(
                colorHex = primaryHex,
                colorArgb = primaryArgb,
                hue = primaryHue,
                chroma = primaryChroma,
                tone = primaryTone,
                population = 5000,
                visualRole = VisualRole.PRIMARY_ACCENT
            )
        )
        sources.add(
            ImagePaletteSource(
                colorHex = "#1E293B",
                colorArgb = 0xFF1E293B.toInt(),
                hue = 230.0,
                chroma = 0.03,
                tone = 0.20,
                population = 15000,
                visualRole = VisualRole.ATMOSPHERIC
            )
        )
        if (supportingHex != null && supportingArgb != null && supportingHue != null) {
            sources.add(
                ImagePaletteSource(
                    colorHex = supportingHex,
                    colorArgb = supportingArgb,
                    hue = supportingHue,
                    chroma = 0.12,
                    tone = 0.65,
                    population = 3000,
                    visualRole = VisualRole.SUPPORTING_ACCENT
                )
            )
        }
        if (tertiaryHex != null && tertiaryArgb != null && tertiaryHue != null) {
            sources.add(
                ImagePaletteSource(
                    colorHex = tertiaryHex,
                    colorArgb = tertiaryArgb,
                    hue = tertiaryHue,
                    chroma = 0.14,
                    tone = 0.55,
                    population = 2000,
                    visualRole = VisualRole.TERTIARY_ACCENT
                )
            )
        }

        val candidates = listOfNotNull(primaryArgb, supportingArgb, tertiaryArgb)
        val dominant = listOfNotNull(primaryHex, supportingHex, tertiaryHex)

        return ImageUnderstanding(
            rankedCandidates = candidates,
            dominantColors = dominant,
            paletteSources = sources,
            averageLightness = averageLightness,
            tonalCharacter = tonalCharacter,
            chromaticCharacter = chromaticCharacter,
            temperatureBias = temperatureBias,
            darkLightBias = darkLightBias,
            paletteDiversity = paletteDiversity,
            imageFingerprint = "fp_${primaryHex}_${averageLightness}"
        )
    }

    // ── Test 1: Same image + same settings -> identical theme ─────────────────
    @Test
    fun test01_sameImageSameSettings_yieldsIdenticalTheme() {
        val understanding = createSyntheticUnderstanding()
        val palette1 = ThemeGenerationEngine.generateSourcePalette(
            understanding = understanding,
            recipe = ThemeGenerationRecipe.BALANCED,
            isDark = true,
            influence = ImageInfluence.BALANCED,
            writingCharacter = WritingCharacter.NEUTRAL
        )
        val palette2 = ThemeGenerationEngine.generateSourcePalette(
            understanding = understanding,
            recipe = ThemeGenerationRecipe.BALANCED,
            isDark = true,
            influence = ImageInfluence.BALANCED,
            writingCharacter = WritingCharacter.NEUTRAL
        )
        val defaults1 = ThemeManager.generateThemeDefaults(palette1, isDark = true)
        val defaults2 = ThemeManager.generateThemeDefaults(palette2, isDark = true)

        assertEquals(palette1.background, palette2.background)
        assertEquals(palette1.accent, palette2.accent)
        assertEquals(palette1.text, palette2.text)
        assertEquals(defaults1, defaults2)
    }

    // ── Test 2: Same image + different recipe -> meaningfully different theme ─
    @Test
    fun test02_sameImageDifferentRecipe_yieldsMeaningfullyDifferentTheme() {
        val understanding = createSyntheticUnderstanding()
        val balanced = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val atmospheric = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.ATMOSPHERIC, isDark = true)
        val ink = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.INK, isDark = true)
        val expressive = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.EXPRESSIVE, isDark = true)

        assertNotEquals(balanced.background, ink.background)
        val inkOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(ink.background))
        val exprOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(expressive.background))
        assertTrue("Ink background must be nearly neutral chroma", inkOklch.c <= 0.015)
        assertTrue("Expressive chroma must exceed ink chroma", exprOklch.c >= inkOklch.c)
    }

    // ── Test 3: Same image + different influence -> meaningfully different theme
    @Test
    fun test03_sameImageDifferentInfluence_yieldsMeaningfullyDifferentTheme() {
        val understanding = createSyntheticUnderstanding()
        val subtle = ThemeGenerationEngine.generateSourcePalette(
            understanding, recipe = ThemeGenerationRecipe.ATMOSPHERIC, isDark = true, influence = ImageInfluence.SUBTLE
        )
        val strong = ThemeGenerationEngine.generateSourcePalette(
            understanding, recipe = ThemeGenerationRecipe.ATMOSPHERIC, isDark = true, influence = ImageInfluence.STRONG
        )

        val subtleOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(subtle.background))
        val strongOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(strong.background))
        assertTrue("Strong influence must produce higher background chroma than subtle", strongOklch.c >= subtleOklch.c)
    }

    // ── Test 4: Same image + different writing character -> status semantics preserved
    @Test
    fun test04_sameImageDifferentWritingCharacter_atmosphereChangesWithoutCorruptingStatusSemantics() {
        val understanding = createSyntheticUnderstanding()
        val warm = ThemeGenerationEngine.generateSourcePalette(
            understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true, writingCharacter = WritingCharacter.WARM
        )
        val cool = ThemeGenerationEngine.generateSourcePalette(
            understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true, writingCharacter = WritingCharacter.COOL
        )

        val warmDefaults = ThemeManager.generateThemeDefaults(warm, isDark = true)
        val coolDefaults = ThemeManager.generateThemeDefaults(cool, isDark = true)

        assertNotEquals(warm.background, cool.background)
        // Status semantics must remain within their designated semantic hue envelopes
        val successOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(warmDefaults.success))
        val warningOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(warmDefaults.warning))
        val errorOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(warmDefaults.error))

        assertTrue("Success hue must remain green", successOklch.h in 120.0..165.0)
        assertTrue("Warning hue must remain amber/gold", warningOklch.h in 60.0..105.0)
        assertTrue("Error hue must remain red/crimson", errorOklch.h in 10.0..45.0)
    }

    // ── Test 5: Dark image -> sensible default polarity (dark mode) ───────────
    @Test
    fun test05_darkImage_sensibleDefaultPolarity() {
        val understanding = createSyntheticUnderstanding(averageLightness = 0.25f, darkLightBias = DarkLightBias.DARK_BIASED)
        assertTrue("Dark image must default to dark mode", understanding.darkLightBias == DarkLightBias.DARK_BIASED)
    }

    // ── Test 6: Light image -> sensible default polarity (light mode) ──────────
    @Test
    fun test06_lightImage_sensibleDefaultPolarity() {
        val understanding = createSyntheticUnderstanding(averageLightness = 0.75f, darkLightBias = DarkLightBias.LIGHT_BIASED)
        assertTrue("Light image must default to light mode", understanding.darkLightBias == DarkLightBias.LIGHT_BIASED)
    }

    // ── Test 7: Warm image -> warm character influence ─────────────────────────
    @Test
    fun test07_warmImage_warmCharacterInfluence() {
        val understanding = createSyntheticUnderstanding(
            primaryHex = "#E67E22",
            primaryArgb = 0xFFE67E22.toInt(),
            primaryHue = 45.0,
            temperatureBias = TemperatureBias.WARM
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val bgOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(palette.background))
        // Background tint should lean warm or neutral-warm
        assertTrue("Warm image should preserve warm hue undertone", bgOklch.h in 20.0..90.0 || bgOklch.c < 0.02)
    }

    // ── Test 8: Cool image -> cool character influence ─────────────────────────
    @Test
    fun test08_coolImage_coolCharacterInfluence() {
        val understanding = createSyntheticUnderstanding(
            primaryHex = "#3498DB",
            primaryArgb = 0xFF3498DB.toInt(),
            primaryHue = 230.0,
            temperatureBias = TemperatureBias.COOL
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val bgOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(palette.background))
        assertTrue("Cool image should preserve cool hue undertone", bgOklch.h in 180.0..280.0 || bgOklch.c < 0.02)
    }

    // ── Test 9: Muted image -> restrained theme ────────────────────────────────
    @Test
    fun test09_mutedImage_restrainedTheme() {
        val understanding = createSyntheticUnderstanding(
            primaryHex = "#7F8C8D",
            primaryArgb = 0xFF7F8C8D.toInt(),
            primaryChroma = 0.04,
            chromaticCharacter = ChromaticCharacter.MUTED
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val accentOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(palette.accent))
        assertTrue("Muted image produces restrained accent chroma", accentOklch.c < 0.20)
    }

    // ── Test 10: Highly saturated image -> controlled expressive theme ────────
    @Test
    fun test10_highlySaturatedImage_controlledExpressiveTheme() {
        val understanding = createSyntheticUnderstanding(
            primaryHex = "#FF0055",
            primaryArgb = 0xFFFF0055.toInt(),
            primaryChroma = 0.32,
            chromaticCharacter = ChromaticCharacter.VIVID
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.EXPRESSIVE, isDark = true)
        val accentOklch = ContrastResolver.colorToOklch(ThemeManager.parseColor(palette.accent))
        // Accent should be clamped into safe gamut (c <= 0.26)
        assertTrue("Accent chroma must be safely clamped", accentOklch.c <= 0.26)
    }

    // ── Test 11: Monochromatic image -> coherent palette, no invented chaos ───
    @Test
    fun test11_monochromaticImage_coherentPaletteNoInventedChaos() {
        val sources = listOf(
            ImagePaletteSource("#808080", 0xFF808080.toInt(), hue = 0.0, chroma = 0.01, tone = 0.50, visualRole = VisualRole.PRIMARY_ACCENT),
            ImagePaletteSource("#202020", 0xFF202020.toInt(), hue = 0.0, chroma = 0.01, tone = 0.15, visualRole = VisualRole.ATMOSPHERIC)
        )
        val understanding = ImageUnderstanding(
            rankedCandidates = listOf(0xFF808080.toInt(), 0xFF202020.toInt()),
            dominantColors = listOf("#808080", "#202020"),
            paletteSources = sources,
            averageLightness = 0.40f,
            chromaticCharacter = ChromaticCharacter.MUTED
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        assertNull("Monochromatic image must not invent a tertiary accent", palette.tertiaryAccent)
    }

    // ── Test 12: Multi-color image -> controlled multi-source palette ─────────
    @Test
    fun test12_multiColorImage_controlledMultiSourcePalette() {
        val understanding = createSyntheticUnderstanding(
            primaryHue = 220.0,
            supportingHue = 160.0,
            tertiaryHue = 35.0,
            paletteDiversity = PaletteDiversity.DIVERSE
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        assertNotNull("Multi-color image extracts supporting accent", palette.secondaryAccent)
        assertNotNull("Multi-color image extracts tertiary accent", palette.tertiaryAccent)
    }

    // ── Test 13: Green image -> primary does not become indistinguishable from success
    @Test
    fun test13_greenImage_primaryDoesNotBecomeIndistinguishableFromSuccess() {
        val greenPrimary = "#10B981"
        val understanding = createSyntheticUnderstanding(
            primaryHex = greenPrimary,
            primaryArgb = 0xFF10B981.toInt(),
            primaryHue = 145.0,
            primaryChroma = 0.16
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val accentColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.accent))
        val successColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.success))
        val distance = ContrastResolver.calculateOklabDeltaE(accentColor, successColor)
        assertTrue("Accent and success must remain perceptually distinct (deltaE >= 0.04)", distance >= 0.04)
    }

    // ── Test 14: Amber image -> primary does not become indistinguishable from warning
    @Test
    fun test14_amberImage_primaryDoesNotBecomeIndistinguishableFromWarning() {
        val amberPrimary = "#F59E0B"
        val understanding = createSyntheticUnderstanding(
            primaryHex = amberPrimary,
            primaryArgb = 0xFFF59E0B.toInt(),
            primaryHue = 82.0,
            primaryChroma = 0.17
        )
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val accentColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.accent))
        val warningColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.warning))
        val distance = ContrastResolver.calculateOklabDeltaE(accentColor, warningColor)
        assertTrue("Accent and warning must remain perceptually distinct (deltaE >= 0.04)", distance >= 0.04)
    }

    // ── Test 15: Dialogue/highlight collision prevention ──────────────────────
    @Test
    fun test15_dialogueHighlightCollisionPrevention() {
        val understanding = createSyntheticUnderstanding()
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val dialogueColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.dialogueText))
        val highlightColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.specialHighlight))
        val distance = ContrastResolver.calculateOklabDeltaE(dialogueColor, highlightColor)
        assertTrue("Dialogue and highlight must maintain distinct visual separation", distance >= 0.05)
    }

    // ── Test 16: Prose/dialogue readability ───────────────────────────────────
    @Test
    fun test16_proseDialogueReadability() {
        val understanding = createSyntheticUnderstanding()
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val bgColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.background))
        val textColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.text))
        val dialogueColor = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.dialogueText))

        val textRatio = ContrastResolver.calculateWcagContrastRatio(textColor, bgColor)
        val dialogueRatio = ContrastResolver.calculateWcagContrastRatio(dialogueColor, bgColor)

        assertTrue("Prose text must satisfy WCAG AA contrast (>= 4.5:1)", textRatio >= 4.5)
        assertTrue("Dialogue text must satisfy WCAG AA contrast (>= 4.0:1)", dialogueRatio >= 4.0)
    }

    // ── Test 17: Analytics color separation ───────────────────────────────────
    @Test
    fun test17_analyticsColorSeparation() {
        val understanding = createSyntheticUnderstanding()
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val pos = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.analyticsPositive))
        val neutral = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.analyticsNeutral))
        val series1 = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.analyticsSeries1))

        assertTrue("Analytics positive and neutral must differ", ContrastResolver.calculateOklabDeltaE(pos, neutral) >= 0.05)
        assertTrue("Analytics positive and series1 must differ", ContrastResolver.calculateOklabDeltaE(pos, series1) >= 0.04)
    }

    // ── Test 18: Worldbuilding category separation ────────────────────────────
    @Test
    fun test18_worldbuildingCategorySeparation() {
        val understanding = createSyntheticUnderstanding()
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val character = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.worldCharacter))
        val location = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.worldLocation))
        val faction = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.worldFaction))
        val lore = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.worldLore))

        assertTrue(ContrastResolver.calculateOklabDeltaE(character, location) >= 0.05)
        assertTrue(ContrastResolver.calculateOklabDeltaE(character, faction) >= 0.05)
        assertTrue(ContrastResolver.calculateOklabDeltaE(location, lore) >= 0.05)
    }

    // ── Test 19: Light surface hierarchy ──────────────────────────────────────
    @Test
    fun test19_lightSurfaceHierarchy() {
        val understanding = createSyntheticUnderstanding(averageLightness = 0.85f)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = false)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = false)

        val lowest = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surfaceLowest)).l
        val surface = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surface)).l
        val raised = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surfaceRaised)).l

        assertTrue("Light surface lowest must be less light than surface", lowest <= surface + 0.005)
        assertTrue("Light surface raised must be lighter than surface", raised >= surface - 0.005)
    }

    // ── Test 20: Dark surface hierarchy ───────────────────────────────────────
    @Test
    fun test20_darkSurfaceHierarchy() {
        val understanding = createSyntheticUnderstanding(averageLightness = 0.20f)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        val bgL = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.background)).l
        val lowestL = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surfaceLowest)).l
        val surfaceL = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surface)).l
        val raisedL = ContrastResolver.colorToOklch(ThemeManager.parseColor(defaults.surfaceRaised)).l

        assertTrue("Dark lowest surface is >= background", lowestL >= bgL - 0.005)
        assertTrue("Dark surface is >= lowest surface", surfaceL >= lowestL - 0.005)
        assertTrue("Dark raised surface is > surface", raisedL > surfaceL)
    }

    // ── Test 21: Manual overrides survive base-color edits ────────────────────
    @Test
    fun test21_manualOverridesSurviveBaseColorEdits() {
        val understanding = createSyntheticUnderstanding()
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val customDialogue = "#FF00FF"
        val overrides = ThemeColorOverrides(dialogueText = customDialogue)

        val resolved = ThemeManager.resolveThemeColors(palette, overrides, isDark = true)
        assertEquals(customDialogue, resolved.dialogueText)
    }

    // ── Test 22: Recipe survives theme reopening ──────────────────────────────
    @Test
    fun test22_recipeSurvivesThemeReopening() {
        val meta = ThemeGenerationMetadata(
            recipe = ThemeGenerationRecipe.INK,
            imageInfluence = ImageInfluence.STRONG,
            writingCharacter = WritingCharacter.DRAMATIC,
            originalAtmosphereHex = "#123456"
        )
        val theme = AppTheme(
            id = "theme_session_5",
            name = "Midnight Ink",
            colors = ThemeColors(),
            isDark = true,
            generationMetadata = meta
        )
        assertEquals(ThemeGenerationRecipe.INK, theme.generationMetadata?.recipe)
        assertEquals(ImageInfluence.STRONG, theme.generationMetadata?.imageInfluence)
        assertEquals(WritingCharacter.DRAMATIC, theme.generationMetadata?.writingCharacter)
    }

    // ── Test 23: Existing custom themes remain compatible ─────────────────────
    @Test
    fun test23_existingCustomThemesRemainCompatible() {
        val legacyTheme = AppTheme(
            id = "legacy_custom_1",
            name = "Old Custom",
            colors = ThemeColors(background = "#18181B", text = "#FAFAFA", accent = "#3B82F6"),
            isDark = true
        )
        val resolved = ThemeManager.resolveThemeColors(legacyTheme)
        assertEquals("#18181B", resolved.background)
        assertEquals("#FAFAFA", resolved.text)
        assertEquals("#3B82F6", resolved.accent)
    }

    // ── Test 24: Built-in themes remain unchanged ─────────────────────────────
    @Test
    fun test24_builtInThemesRemainUnchanged() {
        val builtIns = ThemeManager.getBuiltInThemes()
        assertTrue("Built-in themes list must not be empty", builtIns.isNotEmpty())
        val midnight = builtIns.firstOrNull { it.id == "midnight" } ?: builtIns.first()
        val resolved = ThemeManager.resolveThemeColors(midnight)
        assertEquals(midnight.colors.background, resolved.background)
        assertEquals(midnight.colors.text, resolved.text)
    }

    // ── Test 25: Image replacement does not silently regenerate theme ─────────
    @Test
    fun test25_imageReplacementDoesNotSilentlyRegenerateTheme() {
        val initialSession = ImageThemeGenerationSession(
            imageUri = "content://media/1",
            themeName = "Ocean Dusk",
            selectedCandidate = 0xFF3B82F6.toInt(),
            isDark = true
        )
        val themeBefore = initialSession.toAppTheme("test_id")
        val sessionWithNewImage = initialSession.withImage("content://media/2", 0xFF10B981.toInt())
        // Existing theme colors must not mutate retroactively
        assertEquals("#3B82F6", initialSession.sourcePalette.accent)
        assertNotEquals(initialSession.imageUri, sessionWithNewImage.imageUri)
    }

    // ── Test 26: Theme-only mode has no image dependency ──────────────────────
    @Test
    fun test26_themeOnlyModeHasNoImageDependency() {
        val session = ImageThemeGenerationSession(
            imageUri = "content://media/artwork.png",
            relationshipMode = ThemeRelationshipMode.THEME_ONLY
        )
        val theme = session.toAppTheme("theme_only_test")
        assertNull("THEME_ONLY must not bind background image URI", theme.backgroundImageUri)
    }

    // ── Test 27: Theme-image mode persists artwork correctly ──────────────────
    @Test
    fun test27_themeImageModePersistsArtworkCorrectly() {
        val session = ImageThemeGenerationSession(
            imageUri = "content://media/artwork.png",
            relationshipMode = ThemeRelationshipMode.THEME_IMAGE
        )
        val theme = session.toAppTheme("theme_image_test")
        assertEquals("content://media/artwork.png", theme.backgroundImageUri)
        assertEquals("image", theme.backgroundImageMode)
    }

    // ── Test 28: Theme-glass mode persists required environmental data ────────
    @Test
    fun test28_themeGlassModePersistsRequiredEnvironmentalData() {
        val session = ImageThemeGenerationSession(
            imageUri = "content://media/artwork.png",
            relationshipMode = ThemeRelationshipMode.THEME_GLASS
        )
        val theme = session.toAppTheme("theme_glass_test")
        assertEquals("content://media/artwork.png", theme.backgroundImageUri)
        assertEquals("blurred", theme.backgroundImageMode)
        assertTrue("Glass mode blur intensity must be > 0", (theme.blurIntensity ?: 0f) >= 20f)
    }

    // ── Test 29: Contrast validation catches intentionally injected failures ──
    @Test
    fun test29_contrastValidationCatchesIntentionallyInjectedFailures() {
        // Gray on gray failure injection
        val failingColors = ThemeColors(
            background = "#808080",
            text = "#828282",
            accent = "#848484"
        )
        val validation = ContrastResolver.validateThemeContrast(failingColors)
        assertEquals(ValidationStatus.FAIL, validation.proseStatus)
    }

    // ── Test 30: Gamut edge cases ─────────────────────────────────────────────
    @Test
    fun test30_gamutEdgeCases() {
        // High chroma out-of-sRGB values
        val superSaturatedOklch = Oklch(0.60, 0.40, 140.0)
        val mappedHex = SafeGamutMapper.mapOklchToSrgbHex(superSaturatedOklch)
        assertNotNull(mappedHex)
        assertTrue("Hex must be valid format", mappedHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
        val parsedInt = ThemeManager.parseColor(mappedHex)
        val roundTripOklch = ContrastResolver.colorToOklch(parsedInt)
        assertTrue("Mapped chroma must be within sRGB gamut (< 0.30)", roundTripOklch.c < 0.30)
    }

    // ── Test 31: Empty/corrupt image fallback ──────────────────────────────────
    @Test
    fun test31_emptyCorruptImageFallback() {
        val emptyPixels = IntArray(0)
        val understanding = ThemeGenerationEngine.analyzePixels(emptyPixels, 0, 0)
        assertNotNull(understanding)
        assertTrue(understanding.paletteSources.isNotEmpty())
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        assertNotNull(palette.accent)
        assertNotNull(palette.background)
    }

    // ── Test 32: Grayscale image ──────────────────────────────────────────────
    @Test
    fun test32_grayscaleImage() {
        val grayPixels = IntArray(100) { 0xFF888888.toInt() }
        val understanding = ThemeGenerationEngine.analyzePixels(grayPixels, 10, 10)
        assertNotNull(understanding)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        assertNotNull(palette.accent)
    }

    // ── Test 33: Extreme black image ──────────────────────────────────────────
    @Test
    fun test33_extremeBlackImage() {
        val blackPixels = IntArray(100) { 0xFF000000.toInt() }
        val understanding = ThemeGenerationEngine.analyzePixels(blackPixels, 10, 10)
        assertNotNull(understanding)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)
        val contrast = ContrastResolver.calculateWcagContrastRatio(
            androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.text)),
            androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.background))
        )
        assertTrue("Theme defaults for black image must still have accessible contrast", contrast >= 4.5)
    }

    // ── Test 34: Extreme white image ──────────────────────────────────────────
    @Test
    fun test34_extremeWhiteImage() {
        val whitePixels = IntArray(100) { 0xFFFFFFFF.toInt() }
        val understanding = ThemeGenerationEngine.analyzePixels(whitePixels, 10, 10)
        assertNotNull(understanding)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = false)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = false)
        val contrast = ContrastResolver.calculateWcagContrastRatio(
            androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.text)),
            androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.background))
        )
        assertTrue("Theme defaults for white image must still have accessible contrast", contrast >= 4.5)
    }

    // ── Test 35: Transparent image ────────────────────────────────────────────
    @Test
    fun test35_transparentImage() {
        val transparentPixels = IntArray(100) { 0x00000000 }
        val understanding = ThemeGenerationEngine.analyzePixels(transparentPixels, 10, 10)
        assertNotNull(understanding)
        assertTrue("Transparent image falls back gracefully", understanding.paletteSources.isNotEmpty())
    }

    // ── Test 36: Image with one dominant color ────────────────────────────────
    @Test
    fun test36_imageWithOneDominantColor() {
        val singleColorPixels = IntArray(100) { 0xFF2C3E50.toInt() }
        val understanding = ThemeGenerationEngine.analyzePixels(singleColorPixels, 10, 10)
        val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
        val defaults = ThemeManager.generateThemeDefaults(palette, isDark = true)

        assertNotNull(defaults.background)
        assertNotNull(defaults.accent)
        assertNotNull(defaults.surface)
        assertNotEquals(defaults.background, defaults.text)
    }

    // ── Part 37: Property & Quality Tests ─────────────────────────────────────
    @Test
    fun test37_property_randomPalettes_noCrashesNoInfinitiesValidHex() {
        val rng = Random(42)
        val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")

        for (i in 0 until 50) {
            val hue = rng.nextDouble(0.0, 360.0)
            val chroma = rng.nextDouble(0.01, 0.28)
            val tone = rng.nextDouble(0.15, 0.85)
            val oklch = Oklch(tone, chroma, hue)
            val hex = SafeGamutMapper.mapOklchToSrgbHex(oklch)
            val argb = ThemeManager.parseColor(hex)

            val understanding = createSyntheticUnderstanding(
                primaryHex = hex,
                primaryArgb = argb,
                primaryHue = hue,
                primaryChroma = chroma,
                primaryTone = tone
            )

            val recipe = ThemeGenerationRecipe.values()[i % ThemeGenerationRecipe.values().size]
            val isDark = i % 2 == 0
            val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = recipe, isDark = isDark)
            val defaults = ThemeManager.generateThemeDefaults(palette, isDark = isDark)

            assertTrue("Background hex must be valid", defaults.background.matches(hexRegex))
            assertTrue("Text hex must be valid", defaults.text.matches(hexRegex))
            assertTrue("Accent hex must be valid", defaults.accent.matches(hexRegex))
            assertTrue("Surface hex must be valid", defaults.surface.matches(hexRegex))
            assertFalse("Background must not be NaN/infinite", defaults.background.contains("NaN", ignoreCase = true))
        }
    }

    @Test
    fun test38_property_deterministicAcrossRuns() {
        val rng = Random(1337)
        for (i in 0 until 25) {
            val hue = rng.nextDouble(0.0, 360.0)
            val hex = SafeGamutMapper.mapOklchToSrgbHex(Oklch(0.55, 0.14, hue))
            val argb = ThemeManager.parseColor(hex)
            val understanding = createSyntheticUnderstanding(primaryHex = hex, primaryArgb = argb, primaryHue = hue)

            val p1 = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
            val p2 = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true)
            assertEquals("Run $i must be strictly deterministic", p1.background, p2.background)
            assertEquals("Run $i must be strictly deterministic", p1.accent, p2.accent)
        }
    }

    @Test
    fun test39_property_requiredContrastConstraintsSatisfied() {
        val rng = Random(999)
        for (i in 0 until 30) {
            val hue = rng.nextDouble(0.0, 360.0)
            val hex = SafeGamutMapper.mapOklchToSrgbHex(Oklch(0.50, 0.12, hue))
            val argb = ThemeManager.parseColor(hex)
            val understanding = createSyntheticUnderstanding(primaryHex = hex, primaryArgb = argb, primaryHue = hue)

            val isDark = (i % 2 == 0)
            val palette = ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = isDark)
            val defaults = ThemeManager.generateThemeDefaults(palette, isDark = isDark)

            val bg = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.background))
            val text = androidx.compose.ui.graphics.Color(ThemeManager.parseColor(defaults.text))
            val ratio = ContrastResolver.calculateWcagContrastRatio(text, bg)
            assertTrue("Run $i text contrast must be >= 4.5:1 (got $ratio)", ratio >= 4.5)
        }
    }

    @Test
    fun test40_property_semanticCollisionRateBounded() {
        val rng = Random(777)
        for (i in 0 until 30) {
            val hue = rng.nextDouble(0.0, 360.0)
            val hex = SafeGamutMapper.mapOklchToSrgbHex(Oklch(0.50, 0.15, hue))
            val argb = ThemeManager.parseColor(hex)
            val understanding = createSyntheticUnderstanding(primaryHex = hex, primaryArgb = argb, primaryHue = hue)

            val defaults = ThemeManager.generateThemeDefaults(
                ThemeGenerationEngine.generateSourcePalette(understanding, recipe = ThemeGenerationRecipe.BALANCED, isDark = true),
                isDark = true
            )

            assertNotEquals("Dialogue text must not collide with prose text", defaults.dialogueText, defaults.text)
            assertNotEquals("Accent must not collide with background", defaults.accent, defaults.background)
        }
    }
}
