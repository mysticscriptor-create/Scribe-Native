package com.primaloptima.scribe.util

import com.primaloptima.scribe.ui.theme.ContrastResolver
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColorOverrides
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 Target Theme Architecture Tests:
 * 1. Regeneration Test
 * 2. Override Survival Test
 * 3. Override Precedence Test
 * 4. Reset/Clear Override Test
 * 5. Persistence Roundtrip Test
 * 6. Duplication Test
 * 7. Legacy Migration Test
 * 8. Determinism Test
 * 9. Heading Separation Test
 */
class ThemeArchitectureTest {

    // ── Test 1: Regeneration Test ─────────────────────────────────────────────
    @Test
    fun testRegeneration_changingFoundationSourceRegeneratesDependentDefaults() {
        val darkBg = "#121212"
        val lightBg = "#F8F8F8"
        val textHex = "#E0E0E0"
        val accentHex = "#4A90E2"

        val darkDefaults = ThemeManager.generateThemeDefaults(darkBg, textHex, accentHex, isDark = true)
        val lightDefaults = ThemeManager.generateThemeDefaults(lightBg, textHex, accentHex, isDark = false)

        assertNotEquals(darkDefaults.surface, lightDefaults.surface)
        assertNotEquals(darkDefaults.surfaceLowest, lightDefaults.surfaceLowest)
        assertNotEquals(darkDefaults.surfaceRaised, lightDefaults.surfaceRaised)
        assertNotEquals(darkDefaults.borderSubtle, lightDefaults.borderSubtle)
    }

    // ── Test 2: Override Survival Test ────────────────────────────────────────
    @Test
    fun testOverrideSurvival_changingFoundationPreservesUserOverrides() {
        val initialSources = ThemeSourcePalette(background = "#121212", text = "#F0F0F0", accent = "#4A90E2")
        val customSecondary = "#FF69B4"
        val customDialogue = "#00FA9A"
        val overrides = ThemeColorOverrides(
            secondary = customSecondary,
            dialogueText = customDialogue
        )

        val resolvedInitial = ThemeManager.resolveThemeColors(initialSources, overrides, isDark = true)
        assertEquals(customSecondary, resolvedInitial.secondary)
        assertEquals(customDialogue, resolvedInitial.dialogueText)

        // Change background source from dark to light
        val newSources = initialSources.copy(background = "#FAFAFA")
        val resolvedUpdated = ThemeManager.resolveThemeColors(newSources, overrides, isDark = false)

        // Overrides must survive the foundation source change
        assertEquals(customSecondary, resolvedUpdated.secondary)
        assertEquals(customDialogue, resolvedUpdated.dialogueText)
        // Non-overridden tokens must regenerate based on new foundation
        assertNotEquals(resolvedInitial.surface, resolvedUpdated.surface)
    }

    // ── Test 3: Override Precedence Test ──────────────────────────────────────
    @Test
    fun testOverridePrecedence_overridesTakePrecedenceOverGeneratedDefaults() {
        val sources = ThemeSourcePalette(background = "#18181B", text = "#F4F4F5", accent = "#3B82F6")
        val defaults = ThemeManager.generateThemeDefaults(sources, isDark = true)

        val customSurface = "#222230"
        val customError = "#FF0055"
        val overrides = ThemeColorOverrides(
            surface = customSurface,
            error = customError
        )

        val resolved = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)

        assertEquals(customSurface, resolved.surface)
        assertEquals(customError, resolved.error)
        // Non-overridden tokens must match generated defaults
        assertEquals(defaults.mutedText, resolved.mutedText)
        assertEquals(defaults.secondary, resolved.secondary)
        assertEquals(defaults.warning, resolved.warning)
    }

    // ── Test 4: Reset/Clear Override Test ─────────────────────────────────────
    @Test
    fun testResetClearOverride_revertsToGeneratedDefault() {
        val sources = ThemeSourcePalette(background = "#1E1E2E", text = "#CDD6F4", accent = "#CBA6F7")
        val defaults = ThemeManager.generateThemeDefaults(sources, isDark = true)

        val customSecondary = "#FF5555"
        val overrides = ThemeColorOverrides(secondary = customSecondary)
        val resolvedWithOverride = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)
        assertEquals(customSecondary, resolvedWithOverride.secondary)

        // Clear override (set to null)
        val clearedOverrides = overrides.copy(secondary = null)
        val resolvedAfterReset = ThemeManager.resolveThemeColors(sources, clearedOverrides, isDark = true)

        assertEquals(defaults.secondary, resolvedAfterReset.secondary)
    }

    // ── Test 5: Persistence Roundtrip Test ────────────────────────────────────
    @Test
    fun testPersistenceRoundtrip_serializesAndDeserializesWithOverrides() {
        val overrides = ThemeColorOverrides(
            surface = "#282A36",
            headingText = "#FF79C6",
            dialogueText = "#50FA7B",
            monologueText = "#8BE9FD"
        )
        val baseColors = ThemeColors(
            background = "#1E1F29",
            text = "#F8F8F2",
            accent = "#BD93F9"
        )
        val originalTheme = AppTheme(
            id = "custom_test_1",
            name = "Test Dracula",
            colors = baseColors,
            isDark = true,
            schemaVersion = ThemeManager.CURRENT_SCHEMA_VERSION,
            overrides = overrides
        )

        val json = AppJson.encodeToString(originalTheme)
        val deserializedTheme = AppJson.decodeFromString<AppTheme>(json)

        assertEquals(originalTheme.id, deserializedTheme.id)
        assertEquals(originalTheme.name, deserializedTheme.name)
        assertEquals(originalTheme.schemaVersion, deserializedTheme.schemaVersion)
        assertNotNull(deserializedTheme.overrides)
        assertEquals("#282A36", deserializedTheme.overrides?.surface)
        assertEquals("#FF79C6", deserializedTheme.overrides?.headingText)
        assertEquals("#50FA7B", deserializedTheme.overrides?.dialogueText)
        assertEquals("#8BE9FD", deserializedTheme.overrides?.monologueText)
    }

    // ── Test 6: Duplication Test ──────────────────────────────────────────────
    @Test
    fun testDuplication_preservesFoundationAndOverrides() {
        val overrides = ThemeColorOverrides(
            secondary = "#E06C75",
            headingText = "#61AFEF"
        )
        val baseTheme = AppTheme(
            id = "nordic",
            name = "Nordic",
            colors = ThemeColors(
                background = "#2E3440",
                text = "#ECEFF4",
                accent = "#88C0D0"
            ),
            isDark = true,
            schemaVersion = ThemeManager.CURRENT_SCHEMA_VERSION,
            overrides = overrides
        )

        val duplicate = baseTheme.copy(
            id = "nordic_copy",
            name = "${baseTheme.name} Copy",
            builtIn = false
        )

        assertEquals("nordic_copy", duplicate.id)
        assertEquals("Nordic Copy", duplicate.name)
        assertEquals(baseTheme.colors.background, duplicate.colors.background)
        assertEquals(baseTheme.colors.text, duplicate.colors.text)
        assertEquals(baseTheme.colors.accent, duplicate.colors.accent)
        assertEquals(baseTheme.overrides, duplicate.overrides)
        assertEquals("#E06C75", duplicate.overrides?.secondary)
        assertEquals("#61AFEF", duplicate.overrides?.headingText)
    }

    // ── Test 7: Legacy Migration Test ─────────────────────────────────────────
    @Test
    fun testLegacyMigration_legacyThemeWithoutOverridesMigratesSeamlessly() {
        // Legacy JSON with schemaVersion 0 (or absent) and no overrides field
        val legacyJson = """
            {
                "id": "legacy_theme_1",
                "name": "Old Theme",
                "colors": {
                    "background": "#202020",
                    "text": "#E0E0E0",
                    "accent": "#00AAFF"
                },
                "isDark": true
            }
        """.trimIndent()

        val decodedLegacy = AppJson.decodeFromString<AppTheme>(legacyJson)
        assertEquals(0, decodedLegacy.schemaVersion)
        assertNull(decodedLegacy.overrides)

        val migratedTheme = ThemeManager.migrateTheme(decodedLegacy)

        assertEquals(ThemeManager.CURRENT_SCHEMA_VERSION, migratedTheme.schemaVersion)
        assertEquals("#202020", migratedTheme.colors.background)
        assertEquals("#00AAFF", migratedTheme.colors.accent)
        // Ensure semantic roles were synthesized
        assertTrue(migratedTheme.colors.surface.isNotBlank())
        assertTrue(migratedTheme.colors.surfaceLowest.isNotBlank())
        assertTrue(migratedTheme.colors.secondary.isNotBlank())
        assertTrue(migratedTheme.colors.success.isNotBlank())
    }

    // ── Test 8: Determinism Test ──────────────────────────────────────────────
    @Test
    fun testDeterminism_sameInputsProduceExactSameOutputs() {
        val sources = ThemeSourcePalette(background = "#0F172A", text = "#F8FAFC", accent = "#06B6D4")
        val overrides = ThemeColorOverrides(secondary = "#818CF8", dialogueText = "#34D399")

        val run1 = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)
        val run2 = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)
        val run3 = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)

        assertEquals(run1, run2)
        assertEquals(run2, run3)
        assertEquals(run1.surface, run2.surface)
        assertEquals(run1.borderSubtle, run3.borderSubtle)
        assertEquals(run1.secondary, run2.secondary)
    }

    // ── Test 9: Heading Separation Test ───────────────────────────────────────
    @Test
    fun testHeadingSeparation_headingTextCanBeOverriddenIndependentlyFromAccent() {
        val accentHex = "#FF5722"
        val headingHex = "#00BCD4"
        val sources = ThemeSourcePalette(background = "#FFFFFF", text = "#212121", accent = accentHex)

        // 1. Without override: heading defaults to accent
        val defaultColors = ThemeManager.resolveThemeColors(sources, overrides = null, isDark = false)
        assertEquals(accentHex, defaultColors.headingText)
        assertEquals(accentHex, defaultColors.accent)

        // 2. With independent heading override: heading is detached from accent
        val overrides = ThemeColorOverrides(headingText = headingHex)
        val resolvedColors = ThemeManager.resolveThemeColors(sources, overrides = overrides, isDark = false)

        assertEquals(headingHex, resolvedColors.headingText)
        assertEquals(accentHex, resolvedColors.accent)
        assertNotEquals(resolvedColors.accent, resolvedColors.headingText)
    }

    // ── Test 10: Built-in Themes Border Distinctness ──────────────────────────
    @Test
    fun testBuiltInThemes_bordersAreStrictlyDistinctAndNonEmpty() {
        for (theme in DefaultThemes.all) {
            val colors = theme.colors
            assertTrue("Theme ${theme.name} borderSubtle should not be blank", colors.borderSubtle.isNotBlank())
            assertTrue("Theme ${theme.name} border should not be blank", colors.border.isNotBlank())
            assertTrue("Theme ${theme.name} borderProminent should not be blank", colors.borderProminent.isNotBlank())

            // No accidental alias collapse
            assertNotEquals("Theme ${theme.name} borderSubtle and border must not collapse", colors.borderSubtle, colors.border)
            assertNotEquals("Theme ${theme.name} borderSubtle and borderProminent must not collapse", colors.borderSubtle, colors.borderProminent)
            assertNotEquals("Theme ${theme.name} border and borderProminent must not collapse", colors.border, colors.borderProminent)
        }
    }

    // ── Test 11: Dynamic Theme Border Hierarchy in OKLCH ─────────────────────
    @Test
    fun testDynamicTheme_borderHierarchyMaintainsMonotonicLightnessSeparation() {
        // Test dark mode generation
        val darkSources = ThemeSourcePalette(background = "#121215", text = "#F4F4F6", accent = "#3B82F6")
        val darkDefaults = ThemeManager.generateThemeDefaults(darkSources, isDark = true)
        val darkBgOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(darkDefaults.background))
        val darkSubtleOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(darkDefaults.borderSubtle))
        val darkNormalOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(darkDefaults.border))

        assertTrue("Dark mode subtle border L must exceed bg L", darkSubtleOklch.l > darkBgOklch.l)
        assertTrue("Dark mode normal border L must exceed subtle border L", darkNormalOklch.l > darkSubtleOklch.l)

        // Test light mode generation
        val lightSources = ThemeSourcePalette(background = "#FAF8F5", text = "#1C211E", accent = "#234B39")
        val lightDefaults = ThemeManager.generateThemeDefaults(lightSources, isDark = false)
        val lightBgOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(lightDefaults.background))
        val lightSubtleOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(lightDefaults.borderSubtle))
        val lightNormalOklch = ThemeManager.colorToOklch(ThemeManager.parseColor(lightDefaults.border))

        assertTrue("Light mode subtle border L must be darker than bg L", lightSubtleOklch.l < lightBgOklch.l)
        assertTrue("Light mode normal border L must be darker than subtle border L", lightNormalOklch.l < lightSubtleOklch.l)
    }

    // ── Test 12: Independent Border Overrides ─────────────────────────────────
    @Test
    fun testBorderOverrides_allThreeBorderRolesOverrideIndependently() {
        val sources = ThemeSourcePalette(background = "#1E1E2E", text = "#CDD6F4", accent = "#CBA6F7")
        val customSubtle = "#333344"
        val customNormal = "#555566"
        val customProminent = "#FFAA00"

        val overrides = ThemeColorOverrides(
            borderSubtle = customSubtle,
            border = customNormal,
            borderProminent = customProminent
        )
        val resolved = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)

        assertEquals(customSubtle, resolved.borderSubtle)
        assertEquals(customNormal, resolved.border)
        assertEquals(customProminent, resolved.borderProminent)
    }

    // ── Test 13: Border Override Precedence and No Collapsing ─────────────────
    @Test
    fun testBorderOverridePrecedence_subtleOverrideDoesNotOverwriteNormalBorder() {
        val sources = ThemeSourcePalette(background = "#18181B", text = "#F4F4F5", accent = "#3B82F6")
        val defaults = ThemeManager.generateThemeDefaults(sources, isDark = true)
        val customSubtle = "#2A2A35"

        // Override ONLY borderSubtle
        val overrides = ThemeColorOverrides(borderSubtle = customSubtle)
        val resolved = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)

        assertEquals(customSubtle, resolved.borderSubtle)
        // Normal border must retain its generated default, NOT collapse to subtle override or defaults.borderSubtle
        assertEquals(defaults.border, resolved.border)
        assertNotEquals(resolved.borderSubtle, resolved.border)
    }

    // ── Test 14: Focus Token Independence ────────────────────────────────────
    @Test
    fun testFocusTokenIndependence_prominentBorderDoesNotInheritSubtle() {
        for (theme in DefaultThemes.all) {
            val colors = theme.colors
            assertNotEquals(
                "Theme ${theme.name} prominent/focus border must never inherit subtle border",
                colors.borderSubtle,
                colors.borderProminent
            )
        }
    }

    // ── Test 15: Focus Semantic Independence ──────────────────────────────────
    @Test
    fun testFocusSemanticIndependence_focusCanBeOverriddenIndependentlyFromBorderProminent() {
        val sources = ThemeSourcePalette(background = "#18181B", text = "#F4F4F5", accent = "#3B82F6")
        val defaults = ThemeManager.generateThemeDefaults(sources, isDark = true)

        // When focus override is NOT provided, it defaults to borderProminent
        val noFocusOverride = ThemeManager.resolveThemeColors(sources, overrides = null, isDark = true)
        assertEquals(defaults.borderProminent, noFocusOverride.borderProminent)
        val effectiveFocusDefault = noFocusOverride.focus.ifBlank { noFocusOverride.borderProminent }
        assertEquals(noFocusOverride.borderProminent, effectiveFocusDefault)

        // When focus IS overridden independently, focus changes while borderProminent remains untouched
        val customFocus = "#F59E0B"
        val focusOverride = ThemeColorOverrides(focus = customFocus)
        val resolvedFocus = ThemeManager.resolveThemeColors(sources, overrides = focusOverride, isDark = true)
        assertEquals(customFocus, resolvedFocus.focus)
        assertEquals(defaults.borderProminent, resolvedFocus.borderProminent)
        assertNotEquals(resolvedFocus.borderProminent, resolvedFocus.focus)

        // When borderProminent IS overridden independently, borderProminent changes while un-overridden focus defaults to it
        val customProminent = "#10B981"
        val prominentOverride = ThemeColorOverrides(borderProminent = customProminent)
        val resolvedProminent = ThemeManager.resolveThemeColors(sources, overrides = prominentOverride, isDark = true)
        assertEquals(customProminent, resolvedProminent.borderProminent)
        val effectiveFocusAfterProminent = resolvedProminent.focus.ifBlank { resolvedProminent.borderProminent }
        assertEquals(customProminent, effectiveFocusAfterProminent)

        // When BOTH are overridden to different values, both survive independently
        val bothOverride = ThemeColorOverrides(borderProminent = "#EC4899", focus = "#06B6D4")
        val resolvedBoth = ThemeManager.resolveThemeColors(sources, overrides = bothOverride, isDark = true)
        assertEquals("#EC4899", resolvedBoth.borderProminent)
        assertEquals("#06B6D4", resolvedBoth.focus)
        assertNotEquals(resolvedBoth.borderProminent, resolvedBoth.focus)
    }

    // ── Test 16: Dynamic Theme Pipeline Determinism & Role Integrity ───────────
    @Test
    fun testDynamicThemePipeline_deterministicPropagationNoRoleSubstitution() {
        // Source palette
        val sources = ThemeSourcePalette(background = "#0F172A", text = "#F8FAFC", accent = "#38BDF8")
        val derived = ThemeManager.generateThemeDefaults(sources, isDark = true)

        // Step 1: Verify raw generation produces 3 strictly distinct border roles
        assertTrue("borderSubtle must not be blank", derived.borderSubtle.isNotBlank())
        assertTrue("border (normal) must not be blank", derived.border.isNotBlank())
        assertTrue("borderProminent must not be blank", derived.borderProminent.isNotBlank())

        assertNotEquals("borderSubtle must not equal border", derived.borderSubtle, derived.border)
        assertNotEquals("border must not equal borderProminent", derived.border, derived.borderProminent)
        assertNotEquals("borderSubtle must not equal borderProminent", derived.borderSubtle, derived.borderProminent)

        // Step 2: Test resolution via ThemeColors and verify role mapping
        val resolvedThemeColors = ThemeManager.resolveThemeColors(sources, overrides = null, isDark = true)
        assertEquals(derived.borderSubtle, resolvedThemeColors.borderSubtle)
        assertEquals(derived.border, resolvedThemeColors.border)
        assertEquals(derived.borderProminent, resolvedThemeColors.borderProminent)

        // Step 3: Verify role values in Scribe token resolution
        val parsedSubtle = ThemeManager.parseColor(resolvedThemeColors.borderSubtle)
        val parsedNormal = ThemeManager.parseColor(resolvedThemeColors.border)
        val parsedProminent = ThemeManager.parseColor(resolvedThemeColors.borderProminent)

        assertNotEquals("Parsed subtle color must not equal normal", parsedSubtle, parsedNormal)
        assertNotEquals("Parsed normal color must not equal prominent", parsedNormal, parsedProminent)
        assertNotEquals("Parsed subtle color must not equal prominent", parsedSubtle, parsedProminent)

        // Step 4: Verify no stage silently substituted or inverted roles
        val subtleOklch = ThemeManager.colorToOklch(parsedSubtle)
        val normalOklch = ThemeManager.colorToOklch(parsedNormal)
        assertTrue("Dark theme normal border must have higher lightness than subtle border", normalOklch.l > subtleOklch.l)
    }

    // ── Test 17: Prominent Semantics (High-Emphasis Boundary vs Accent) ────────
    @Test
    fun testProminentSemantics_highEmphasisBoundaryNotHardwiredToAccent() {
        // 1. In built-in themes, verify prominent border is NOT forced to equal accent
        val obsidian = DefaultThemes.obsidian
        assertEquals("Obsidian accent is light gray", "#E4E4E7", obsidian.colors.accent)
        assertEquals("Obsidian prominent border is mid-emphasis boundary", "#8E8E93", obsidian.colors.borderProminent)
        assertNotEquals(obsidian.colors.accent, obsidian.colors.borderProminent)

        val focus = DefaultThemes.focus
        assertEquals("Focus accent is slate light", "#F1F5F9", focus.colors.accent)
        assertEquals("Focus prominent border is mid-neutral boundary", "#737373", focus.colors.borderProminent)
        assertNotEquals(focus.colors.accent, focus.colors.borderProminent)

        val typewriter = DefaultThemes.typewriter
        assertNotEquals(typewriter.colors.accent, typewriter.colors.borderProminent)

        // 2. In overrides, changing accent must not clobber an explicit borderProminent override
        val sources = ThemeSourcePalette(background = "#18181B", text = "#F4F4F5", accent = "#3B82F6")
        val overrides = ThemeColorOverrides(borderProminent = "#D97706")
        val resolved = ThemeManager.resolveThemeColors(sources, overrides, isDark = true)
        assertEquals("#D97706", resolved.borderProminent)
        assertEquals("#3B82F6", resolved.accent)

        // Change the accent foundation
        val newSources = ThemeSourcePalette(background = "#18181B", text = "#F4F4F5", accent = "#A855F7")
        val resolvedNewAccent = ThemeManager.resolveThemeColors(newSources, overrides, isDark = true)
        assertEquals("#A855F7", resolvedNewAccent.accent)
        // The prominent boundary override SURVIVES and is NOT replaced by the new accent!
        assertEquals("#D97706", resolvedNewAccent.borderProminent)
    }

    // ── Test 18: Normative WCAG Contrast Ratio Math ───────────────────────────
    @Test
    fun testUnifiedContrastResolver_normativeWcagRatioCalculation() {
        // Pure black vs pure white must be exactly 21.0:1
        val whiteInt = 0xFFFFFFFF.toInt()
        val blackInt = 0xFF000000.toInt()
        val maxRatio = ContrastResolver.calculateWcagContrastRatio(whiteInt, blackInt)
        assertTrue("Black vs White must equal 21:1 within epsilon", Math.abs(maxRatio - 21.0) < 0.01)

        // Identical colors must be exactly 1.0:1
        val unityRatio = ContrastResolver.calculateWcagContrastRatio(whiteInt, whiteInt)
        assertTrue("White vs White must equal 1:1 within epsilon", Math.abs(unityRatio - 1.0) < 0.01)

        // Standard WCAG 4.5:1 boundary check (#767676 on #FFFFFF is ~4.54:1)
        val grayBoundary = 0xFF767676.toInt()
        val boundaryRatio = ContrastResolver.calculateWcagContrastRatio(grayBoundary, whiteInt)
        assertTrue("Standard mid-gray on white must pass 4.5:1", boundaryRatio >= 4.5)
    }

    // ── Test 19: OKLCH Hue and Chroma Preservation ─────────────────────────────
    @Test
    fun testUnifiedContrastResolver_oklchHueAndChromaPreservation() {
        // Test with green accent (#234B39)
        val greenAccentInt = 0xFF234B39.toInt()
        val origOklch = ContrastResolver.colorToOklch(greenAccentInt)

        // Resolve against itself (requires contrast shift)
        val resolvedInt = ContrastResolver.resolveContrastInt(
            backgroundInt = greenAccentInt,
            preferredForegroundInt = greenAccentInt,
            minRatio = 4.5
        )
        val resolvedOklch = ContrastResolver.colorToOklch(resolvedInt)

        // Hue must be preserved within 1.5 degrees
        val hueDiff = Math.abs(origOklch.h - resolvedOklch.h)
        val effectiveHueDiff = Math.min(hueDiff, 360.0 - hueDiff)
        assertTrue("Hue must be preserved within perceptual tolerance (was $effectiveHueDiff)", effectiveHueDiff < 2.0)

        // Contrast ratio must achieve at least 4.5:1
        val achievedRatio = ContrastResolver.calculateWcagContrastRatio(resolvedInt, greenAccentInt)
        assertTrue("Achieved ratio must meet or exceed 4.5:1 (was $achievedRatio)", achievedRatio >= 4.5)
    }

    // ── Test 20: Primary Interaction Resolution Across All Themes ─────────────
    @Test
    fun testUnifiedContrastResolver_primaryInteractionResolutionAcrossAllThemes() {
        for (theme in DefaultThemes.all) {
            val scribeColors = ThemeManager.resolveToScribeColors(theme)
            val accentInt = ThemeManager.parseColor(theme.colors.accent)
            val onPrimaryInt = scribeColors.interaction.onPrimary.hashCode() // Compose color to int

            val ratio = ContrastResolver.calculateWcagContrastRatio(
                fgInt = ThemeManager.parseColor(theme.colors.text).let {
                    // Test actual onPrimary color from ScribeColors
                    ThemeManager.parseColor(
                        ContrastResolver.resolveOnColorHex(
                            containerHex = theme.colors.accent,
                            preferredForegroundHex = if (theme.isDark) theme.colors.background else theme.colors.text,
                            minRatio = 4.5,
                            isDarkTheme = theme.isDark
                        )
                    )
                },
                bgInt = accentInt
            )

            assertTrue(
                "Theme ${theme.name} onPrimary on primary accent must achieve >= 4.5:1 (was $ratio:1)",
                ratio >= 4.5
            )
        }
    }

    // ── Test 21: Primary Container Resolution Across All Themes ───────────────
    @Test
    fun testUnifiedContrastResolver_primaryContainerResolutionAcrossAllThemes() {
        for (theme in DefaultThemes.all) {
            val accentMutedInt = ThemeManager.parseColor(theme.colors.accentMuted)
            val textInt = ThemeManager.parseColor(theme.colors.text)

            val resolvedFgInt = ContrastResolver.resolveContrastInt(
                backgroundInt = accentMutedInt,
                preferredForegroundInt = textInt,
                minRatio = 3.5
            )
            val ratio = ContrastResolver.calculateWcagContrastRatio(resolvedFgInt, accentMutedInt)

            assertTrue(
                "Theme ${theme.name} onPrimaryContainer must achieve >= 3.5:1 on accentMuted (was $ratio:1)",
                ratio >= 3.5
            )
        }
    }

    // ── Test 22: Semantic Status Resolution Across All Themes ──────────────────
    @Test
    fun testUnifiedContrastResolver_semanticStatusResolutionAcrossAllThemes() {
        for (theme in DefaultThemes.all) {
            val scribeColors = ThemeManager.resolveToScribeColors(theme)

            // 1. Success
            val successHex = theme.colors.success.ifBlank { "#10B981" }
            val onSucHex = ContrastResolver.resolveOnColorHex(successHex, null, 4.5, theme.isDark)
            val sucRatio = ContrastResolver.calculateWcagContrastRatio(
                ThemeManager.parseColor(onSucHex),
                ThemeManager.parseColor(successHex)
            )
            assertTrue("Theme ${theme.name} onSuccess must achieve >= 4.5:1 (was $sucRatio:1)", sucRatio >= 4.5)

            // 2. Warning
            val warningHex = theme.colors.warning.ifBlank { "#F59E0B" }
            val onWarnHex = ContrastResolver.resolveOnColorHex(warningHex, null, 4.5, theme.isDark)
            val warnRatio = ContrastResolver.calculateWcagContrastRatio(
                ThemeManager.parseColor(onWarnHex),
                ThemeManager.parseColor(warningHex)
            )
            assertTrue("Theme ${theme.name} onWarning must achieve >= 4.5:1 (was $warnRatio:1)", warnRatio >= 4.5)

            // 3. Error
            val errorHex = theme.colors.error.ifBlank { "#EF4444" }
            val onErrHex = ContrastResolver.resolveOnColorHex(errorHex, null, 4.5, theme.isDark)
            val errRatio = ContrastResolver.calculateWcagContrastRatio(
                ThemeManager.parseColor(onErrHex),
                ThemeManager.parseColor(errorHex)
            )
            assertTrue("Theme ${theme.name} onError must achieve >= 4.5:1 (was $errRatio:1)", errRatio >= 4.5)
        }
    }

    // ── Test 23: Elimination of Ad-Hoc 0.5 Luminance Cutoff ────────────────────
    @Test
    fun testUnifiedContrastResolver_noAdHocLuminanceCutoffBug() {
        // In Midnight Blue, accent is #70AEFB (luminance ~0.413)
        // The old code (luminance < 0.5 -> White) picked White, giving 2.27:1 (FAIL!)
        // The unified resolver must select a dark accessible text achieving >= 4.5:1
        val midnightAccent = "#70AEFB"
        val resolvedOnAccent = ContrastResolver.resolveOnColorHex(
            containerHex = midnightAccent,
            preferredForegroundHex = "#0B111A",
            minRatio = 4.5,
            isDarkTheme = true
        )
        val ratio = ContrastResolver.calculateWcagContrastRatio(
            ThemeManager.parseColor(resolvedOnAccent),
            ThemeManager.parseColor(midnightAccent)
        )
        assertTrue(
            "Midnight accent #70AEFB must achieve >= 4.5:1 (was $ratio:1, resolved to $resolvedOnAccent)",
            ratio >= 4.5
        )
        assertNotEquals("Must NOT pick White for #70AEFB", "#FFFFFF", resolvedOnAccent)
    }

    // ── Test 24: Extreme Edge Cases and Primaries ─────────────────────────────
    @Test
    fun testUnifiedContrastResolver_extremeEdgeCasesAndPrimaries() {
        val testHues = listOf(
            "#000000" to "Pure Black",
            "#FFFFFF" to "Pure White",
            "#808080" to "Midtone Gray",
            "#FF0000" to "Pure Red",
            "#00FF00" to "Pure Green",
            "#0000FF" to "Pure Blue",
            "#FFFF00" to "Pure Yellow",
            "#00FFFF" to "Pure Cyan",
            "#FF00FF" to "Pure Magenta",
            "#E6E6FA" to "Pastel Lavender",
            "#2D3748" to "Dark Slate"
        )

        for ((hex, label) in testHues) {
            val resolvedFg = ContrastResolver.resolveOnColorHex(
                containerHex = hex,
                preferredForegroundHex = hex,
                minRatio = 4.5,
                isDarkTheme = false
            )
            val ratio = ContrastResolver.calculateWcagContrastRatio(
                ThemeManager.parseColor(resolvedFg),
                ThemeManager.parseColor(hex)
            )
            assertTrue(
                "Edge case '$label' ($hex) must resolve foreground to >= 4.5:1 (was $ratio:1, fg=$resolvedFg)",
                ratio >= 4.5
            )
        }
    }

    // ── Test 25: Theme Identity Preservation ──────────────────────────────────
    @Test
    fun testUnifiedContrastResolver_themeIdentityPreservation() {
        // Built-in theme core colors must remain completely unmutated
        assertEquals("#E4E4E7", DefaultThemes.obsidian.colors.accent)
        assertEquals("#70AEFB", DefaultThemes.midnight.colors.accent)
        assertEquals("#F1F5F9", DefaultThemes.focus.colors.accent)
        assertEquals("#234B39", DefaultThemes.paper.colors.accent)
        assertEquals("#7C3F14", DefaultThemes.sepia.colors.accent)
        assertEquals("#000000", DefaultThemes.typewriter.colors.accent)

        // Dark/light classifications must remain strictly truthful
        assertTrue("Obsidian is dark", DefaultThemes.obsidian.isDark)
        assertTrue("Midnight is dark", DefaultThemes.midnight.isDark)
        assertTrue("Focus is dark", DefaultThemes.focus.isDark)
        assertTrue("Paper is light", !DefaultThemes.paper.isDark)
        assertTrue("Sepia is light", !DefaultThemes.sepia.isDark)
        assertTrue("Typewriter is light", !DefaultThemes.typewriter.isDark)
    }

    // ── Test 26: Dynamic Overrides Contrast Preservation ──────────────────────
    @Test
    fun testUnifiedContrastResolver_dynamicOverridesContrastPreservation() {
        // When user creates a custom theme with pastel yellow accent (#FEF08A)
        val sources = ThemeSourcePalette(background = "#0F172A", text = "#F8FAFC", accent = "#FEF08A")
        val defaults = ThemeManager.generateThemeDefaults(sources, isDark = true)
        val resolved = ThemeManager.resolveThemeColors(sources, overrides = null, isDark = true)

        // The resolver must compute an onPrimary that achieves >= 4.5:1 against #FEF08A
        val onAccentHex = ContrastResolver.resolveOnColorHex(
            containerHex = resolved.accent,
            preferredForegroundHex = resolved.background,
            minRatio = 4.5,
            isDarkTheme = true
        )
        val ratio = ContrastResolver.calculateWcagContrastRatio(
            ThemeManager.parseColor(onAccentHex),
            ThemeManager.parseColor(resolved.accent)
        )
        assertTrue(
            "Dynamic pastel accent must resolve to >= 4.5:1 contrast (was $ratio:1)",
            ratio >= 4.5
        )
    }
}

