package com.primaloptima.scribe.util

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
}
