package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ThemeColorOverrides
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeGenerationMetadata
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import com.primaloptima.scribe.util.model.ThemeSchema
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import com.primaloptima.scribe.util.model.ThemeSourceType
import com.primaloptima.scribe.util.model.WritingCharacter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 Theme Schema + Versioning Test Suite
 *
 * Validates:
 * 1. Legacy v0 / unversioned migration to current schema
 * 2. Migration idempotency (f(f(x)) == f(x))
 * 3. Resilient deserialization with safe defaults for missing properties
 * 4. Tolerance of unknown JSON properties without deserialization failure
 * 5. Corrupted / invalid JSON safe recovery hierarchy
 * 6. Built-in themes compliance with schema invariants
 * 7. Full persistence roundtrip fidelity
 * 8. User color override survival across migration
 * 9. Forward compatibility for future schema versions
 * 10. Export / import fidelity
 */
class ThemeSchemaMigrationTest {

    // ── 1. Legacy Theme v0 Migration Test ─────────────────────────────────────
    @Test
    fun testLegacyThemeV0MigratesToCurrentVersion() {
        val legacyJson = """
            {
                "id": "custom_legacy_1",
                "name": "Old Dark",
                "isDark": true,
                "builtIn": false,
                "colors": {
                    "background": "#121214",
                    "text": "#F4F4F6",
                    "accent": "#E4E4E7"
                }
            }
        """.trimIndent()

        val decoded = AppJson.decodeFromString<AppTheme>(legacyJson)
        // Before migration, schemaVersion defaults to VERSION_LEGACY (0)
        assertEquals(ThemeSchema.VERSION_LEGACY, decoded.schemaVersion)

        // After migration:
        val migrated = ThemeManager.migrateTheme(decoded)
        assertEquals(ThemeSchema.CURRENT_VERSION, migrated.schemaVersion)
        assertEquals("custom_legacy_1", migrated.id)
        assertEquals("Old Dark", migrated.name)
        assertTrue(migrated.isDark)

        // Semantic tokens derived correctly
        assertTrue(migrated.colors.surfaceLowest.startsWith("#"))
        assertTrue(migrated.colors.surface.startsWith("#"))
        assertTrue(migrated.colors.surfaceRaised.startsWith("#"))
        assertTrue(migrated.colors.borderSubtle.startsWith("#"))
        assertTrue(migrated.colors.dialogueText.startsWith("#"))
        assertTrue(migrated.colors.analyticsPositive.startsWith("#"))
        assertTrue(migrated.colors.worldCharacter.startsWith("#"))
    }

    // ── 2. Migration Idempotency Test ─────────────────────────────────────────
    @Test
    fun testMigrationIsIdempotent() {
        val legacyTheme = AppTheme(
            id = "custom_test_id",
            name = "Test Theme",
            isDark = false,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_LEGACY,
            colors = ThemeColors(
                background = "#FAF8F5",
                text = "#1C211E",
                accent = "#234B39"
            )
        )

        val migratedOnce = ThemeManager.migrateTheme(legacyTheme)
        val migratedTwice = ThemeManager.migrateTheme(migratedOnce)

        assertEquals(migratedOnce, migratedTwice)
        assertEquals(migratedOnce.schemaVersion, migratedTwice.schemaVersion)
        assertEquals(migratedOnce.colors, migratedTwice.colors)
    }

    // ── 3. Missing Properties Receive Safe Defaults ───────────────────────────
    @Test
    fun testMissingPropertiesReceiveSafeDefaults() {
        val minimalJson = """
            {
                "id": "minimal_theme",
                "name": "Minimal"
            }
        """.trimIndent()

        val decoded = AppJson.decodeAppTheme(minimalJson)
        assertEquals("minimal_theme", decoded.id)
        assertEquals("Minimal", decoded.name)
        assertEquals(ThemeSchema.CURRENT_VERSION, decoded.schemaVersion)
        assertFalse(decoded.builtIn)
        assertNotNull(decoded.colors)
        assertTrue(decoded.colors.background.startsWith("#"))
        assertTrue(decoded.colors.text.startsWith("#"))
        assertTrue(decoded.colors.accent.startsWith("#"))
        assertTrue(decoded.fontSize in 10..48)
        assertTrue(decoded.lineHeight in 1.0f..3.0f)
    }

    // ── 4. Unknown Properties Are Ignored ─────────────────────────────────────
    @Test
    fun testUnknownPropertiesAreIgnored() {
        val futureJson = """
            {
                "id": "future_theme",
                "name": "Future Theme",
                "schemaVersion": 2,
                "unsupportedField": "futureValue",
                "nestedExperimental": { "key": 123 },
                "colors": {
                    "background": "#0B111A",
                    "text": "#EDF2F7",
                    "accent": "#70AEFB",
                    "quantumColor": "#123456"
                }
            }
        """.trimIndent()

        val decoded = AppJson.decodeAppTheme(futureJson)
        assertEquals("future_theme", decoded.id)
        assertEquals("Future Theme", decoded.name)
        assertEquals("#0B111A", decoded.colors.background)
        assertEquals("#EDF2F7", decoded.colors.text)
        assertEquals("#70AEFB", decoded.colors.accent)
    }

    // ── 5. Invalid JSON Recovers Safely ───────────────────────────────────────
    @Test
    fun testInvalidJsonRecoversSafely() {
        val corruptedJson = "not a valid json at all {[]}"
        val fallback = AppJson.decodeAppTheme(corruptedJson)
        assertNotNull(fallback)
        assertEquals(DefaultThemes.paper.id, fallback.id)

        // Resilient list recovery:
        val mixedListJson = """
            [
                { "id": "valid_1", "name": "Valid Theme 1" },
                { "malformed_json" },
                { "id": "valid_2", "name": "Valid Theme 2" }
            ]
        """.trimIndent()

        val recoveredList = AppJson.decodeAppThemes(mixedListJson)
        // Individual valid themes are preserved; corrupted elements are gracefully dropped
        assertEquals(2, recoveredList.size)
        assertEquals("valid_1", recoveredList[0].id)
        assertEquals("valid_2", recoveredList[1].id)
    }

    // ── 6. All Six Built-in Themes Pass Validation ────────────────────────────
    @Test
    fun testAllSixBuiltInThemesPassValidation() {
        val builtIns = DefaultThemes.builtInThemes
        assertEquals(6, builtIns.size)

        for (theme in builtIns) {
            assertTrue("${theme.name} must be builtIn", theme.builtIn)
            assertEquals("${theme.name} must be on current schema version", ThemeSchema.CURRENT_VERSION, theme.schemaVersion)

            // Validate invariants through sanitizeTheme
            val sanitized = ThemeManager.sanitizeTheme(theme)
            assertEquals(theme.id, sanitized.id)
            assertEquals(theme.colors.background, sanitized.colors.background)
            assertEquals(theme.colors.text, sanitized.colors.text)
            assertEquals(theme.colors.accent, sanitized.colors.accent)

            // Ensure valid hex colors
            assertTrue(ThemeManager.isValidHexColor(theme.colors.background))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.surfaceLowest))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.surface))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.surfaceRaised))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.surfaceOverlay))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.text))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.accent))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.border))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.borderSubtle))
            assertTrue(ThemeManager.isValidHexColor(theme.colors.borderProminent))
        }
    }

    // ── 7. Theme Persistence Roundtrip Preserves All Data ─────────────────────
    @Test
    fun testThemePersistenceRoundTripPreservesAllData() {
        val original = DefaultThemes.obsidian.copy(
            id = "roundtrip_test",
            name = "Roundtrip Test",
            builtIn = false,
            schemaVersion = ThemeSchema.CURRENT_VERSION,
            overrides = ThemeColorOverrides(
                headingText = "#FFE4B5",
                dialogueText = "#98FB98",
                surface = "#1F2430"
            ),
            fontSize = 20,
            lineHeight = 1.75f,
            letterSpacing = 0.2f,
            paragraphSpacing = 16,
            paddingHorizontal = 28,
            paddingVertical = 24,
            maxWidth = 800
        )

        val json = AppJson.encodeAppTheme(original)
        val restored = AppJson.decodeAppTheme(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.colors.background, restored.colors.background)
        assertEquals(original.colors.surface, restored.colors.surface)
        assertEquals(original.colors.headingText, restored.colors.headingText)
        assertEquals(original.colors.dialogueText, restored.colors.dialogueText)
        assertEquals(original.overrides, restored.overrides)
        assertEquals(original.fontSize, restored.fontSize)
        assertEquals(original.lineHeight, restored.lineHeight, 0.001f)
        assertEquals(original.letterSpacing, restored.letterSpacing, 0.001f)
        assertEquals(original.paragraphSpacing, restored.paragraphSpacing)
        assertEquals(original.paddingHorizontal, restored.paddingHorizontal)
        assertEquals(original.paddingVertical, restored.paddingVertical)
        assertEquals(original.maxWidth, restored.maxWidth)
    }

    // ── 8. Partial Color Overrides Survive Migration ──────────────────────────
    @Test
    fun testPartialColorOverridesSurviveMigration() {
        val legacyThemeWithOverrides = AppTheme(
            id = "custom_override_theme",
            name = "Custom With Overrides",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_LEGACY,
            colors = ThemeColors(
                background = "#141416",
                text = "#E6E6E6",
                accent = "#3B82F6",
                headingText = "#FFA500" // legacy custom field
            ),
            overrides = ThemeColorOverrides(
                headingText = "#FFA500",
                dialogueText = "#00FF7F"
            )
        )

        val migrated = ThemeManager.migrateTheme(legacyThemeWithOverrides)

        assertEquals(ThemeSchema.CURRENT_VERSION, migrated.schemaVersion)
        assertEquals("#FFA500", migrated.colors.headingText)
        assertEquals("#00FF7F", migrated.colors.dialogueText)
        assertNotNull(migrated.overrides)
        assertEquals("#FFA500", migrated.overrides?.headingText)
        assertEquals("#00FF7F", migrated.overrides?.dialogueText)
    }

    // ── 9. Future Schema Version Handled Gracefully ───────────────────────────
    @Test
    fun testFutureSchemaVersionHandledGracefully() {
        val futureTheme = DefaultThemes.focus.copy(
            id = "future_version_99",
            name = "Future Schema 99",
            schemaVersion = 99
        )

        val processed = ThemeManager.migrateTheme(futureTheme)
        assertEquals(99, processed.schemaVersion)
        assertEquals(futureTheme.id, processed.id)
        assertEquals(futureTheme.colors.background, processed.colors.background)
    }

    // ── 10. Export / Import Fidelity Test ─────────────────────────────────────
    @Test
    fun testExportImportFidelity() {
        val customTheme = AppTheme(
            id = "user_exported_1",
            name = "Author's Velvet",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.CURRENT_VERSION,
            colors = ThemeColors(
                background = "#1B101E",
                text = "#F5EFF7",
                accent = "#D477E6"
            ),
            fontFamily = "serif",
            fontSize = 18,
            lineHeight = 1.70f
        )

        val exportedJson = AppJson.encodeAppTheme(customTheme)
        val importedTheme = AppJson.decodeAppTheme(exportedJson)

        assertEquals(customTheme.id, importedTheme.id)
        assertEquals(customTheme.name, importedTheme.name)
        assertEquals(customTheme.colors.background, importedTheme.colors.background)
        assertEquals(customTheme.colors.text, importedTheme.colors.text)
        assertEquals(customTheme.colors.accent, importedTheme.colors.accent)
        assertEquals(customTheme.fontFamily, importedTheme.fontFamily)
        assertEquals(customTheme.fontSize, importedTheme.fontSize)
    }

    // ── 11. Schema v1 to v2 Migration Preserves Metadata ──────────────────────
    @Test
    fun testV1ToV2MigrationWithMetadataPreservation() {
        val metadata = ThemeGenerationMetadata(
            sourceType = ThemeSourceType.IMAGE_DYNAMIC,
            recipe = ThemeGenerationRecipe.ATMOSPHERIC,
            imageInfluence = ImageInfluence.STRONG,
            writingCharacter = WritingCharacter.WARM,
            relationshipMode = ThemeRelationshipMode.HARMONIC_SURFACE,
            originalAtmosphereHex = "#1E293B"
        )
        val v1Theme = AppTheme(
            id = "custom_v1_theme",
            name = "Misty Dawn",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_1,
            generationMetadata = metadata,
            colors = ThemeColors(
                background = "#121214",
                text = "#F4F4F6",
                accent = "#60A5FA"
            )
        )

        val migrated = ThemeManager.migrateTheme(v1Theme)
        assertEquals(ThemeSchema.VERSION_2, migrated.schemaVersion)
        assertNotNull(migrated.generationMetadata)
        assertEquals(ThemeGenerationRecipe.ATMOSPHERIC, migrated.generationMetadata?.recipe)
        assertEquals(ImageInfluence.STRONG, migrated.generationMetadata?.imageInfluence)
        assertEquals(WritingCharacter.WARM, migrated.generationMetadata?.writingCharacter)
        assertEquals(ThemeRelationshipMode.HARMONIC_SURFACE, migrated.generationMetadata?.relationshipMode)
        assertEquals("#1E293B", migrated.generationMetadata?.originalAtmosphereHex)
    }

    // ── 12. Schema v1 to v2 Synthesizes Missing Metadata ──────────────────────
    @Test
    fun testV1ToV2MigrationSynthesizesMissingMetadataFromLegacyTheme() {
        val v1ThemeWithoutMeta = AppTheme(
            id = "custom_v1_legacy_photo",
            name = "Wallpaper Theme",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_1,
            backgroundImageUri = "content://media/external/images/123",
            bgMode = "blurred",
            savedBgDominantColor = "#0F172A",
            colors = ThemeColors(
                background = "#0F172A",
                text = "#F8FAFC",
                accent = "#38BDF8"
            )
        )

        val migrated = ThemeManager.migrateTheme(v1ThemeWithoutMeta)
        assertEquals(ThemeSchema.VERSION_2, migrated.schemaVersion)
        val meta = migrated.generationMetadata
        assertNotNull(meta)
        assertEquals(ThemeRelationshipMode.HARMONIC_SURFACE, meta?.relationshipMode)
        assertEquals("#0F172A", meta?.originalAtmosphereHex)
        assertEquals(ThemeSourceType.IMAGE_DYNAMIC, meta?.sourceType)
    }

    // ── 13. Serialization Roundtrip with ThemeGenerationMetadata ──────────────
    @Test
    fun testSerializationRoundtripWithThemeGenerationMetadata() {
        val meta = ThemeGenerationMetadata(
            sourceType = ThemeSourceType.IMAGE_DYNAMIC,
            recipe = ThemeGenerationRecipe.EXPRESSIVE,
            imageInfluence = ImageInfluence.BALANCED,
            writingCharacter = WritingCharacter.DRAMATIC,
            relationshipMode = ThemeRelationshipMode.CONTRAST_PANEL,
            sourceFingerprint = "abcd1234efgh5678",
            selectedCandidateHex = "#EC4899",
            userSeedHex = "#F43F5E",
            originalAtmosphereHex = "#831843",
            generatedAtEpochMs = 1726000000000L
        )
        val theme = AppTheme(
            id = "theme_with_full_provenance",
            name = "Expressive Orchid",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_2,
            generationMetadata = meta,
            colors = ThemeColors(
                background = "#180D1E",
                text = "#FCE7F3",
                accent = "#EC4899"
            )
        )

        val json = AppJson.encodeAppTheme(theme)
        val decoded = AppJson.decodeAppTheme(json)

        assertEquals(ThemeSchema.VERSION_2, decoded.schemaVersion)
        val decodedMeta = decoded.generationMetadata
        assertNotNull(decodedMeta)
        assertEquals(meta.sourceType, decodedMeta?.sourceType)
        assertEquals(meta.recipe, decodedMeta?.recipe)
        assertEquals(meta.imageInfluence, decodedMeta?.imageInfluence)
        assertEquals(meta.writingCharacter, decodedMeta?.writingCharacter)
        assertEquals(meta.relationshipMode, decodedMeta?.relationshipMode)
        assertEquals(meta.sourceFingerprint, decodedMeta?.sourceFingerprint)
        assertEquals(meta.selectedCandidateHex, decodedMeta?.selectedCandidateHex)
        assertEquals(meta.userSeedHex, decodedMeta?.userSeedHex)
        assertEquals(meta.originalAtmosphereHex, decodedMeta?.originalAtmosphereHex)
        assertEquals(meta.generatedAtEpochMs, decodedMeta?.generatedAtEpochMs)
    }

    // ── 14. Foundation Color Editing Preserves Manual Semantic Overrides ───────
    @Test
    fun testEditingFoundationColorsPreservesManualOverrides() {
        val initialTheme = AppTheme(
            id = "custom_test_overrides",
            name = "Override Test",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_2,
            colors = ThemeColors(
                background = "#121214",
                text = "#F4F4F6",
                accent = "#3B82F6"
            ),
            overrides = ThemeColorOverrides(
                dialogueText = "#00FF7F",
                specialHighlight = "#FFD700"
            )
        )

        val updated = ThemeManager.updateFoundationColors(
            theme = initialTheme,
            newBg = "#1A1A24",
            newAccent = "#8B5CF6"
        )

        // Foundation colors changed
        assertEquals("#1A1A24", updated.colors.background)
        assertEquals("#8B5CF6", updated.colors.accent)
        // Manual overrides preserved and authoritative
        assertEquals("#00FF7F", updated.overrides?.dialogueText)
        assertEquals("#00FF7F", updated.colors.dialogueText)
        assertEquals("#FFD700", updated.overrides?.specialHighlight)
        assertEquals("#FFD700", updated.colors.specialHighlight)
    }

    // ── 15. Semantic Override Editing Updates Only Targeted Role ──────────────
    @Test
    fun testEditingSemanticOverrideUpdatesOnlyTargetedRole() {
        val theme = AppTheme(
            id = "custom_semantic_test",
            name = "Semantic Test",
            isDark = true,
            builtIn = false,
            schemaVersion = ThemeSchema.VERSION_2,
            colors = ThemeColors(
                background = "#121214",
                text = "#F4F4F6",
                accent = "#3B82F6"
            )
        )

        val updated = ThemeManager.updateSemanticOverride(theme, "focus", "#FF5722")
        assertEquals("#FF5722", updated.overrides?.focus)
        assertEquals("#FF5722", updated.colors.focus)
        // Other overrides remain unset
        assertEquals(null, updated.overrides?.dialogueText)
    }

    // ── 16. Changing Background Image Does Not Alter Theme Colors ─────────────
    @Test
    fun testChangingBackgroundImageDoesNotAlterThemeColors() {
        val theme = ThemeManager.resolveTheme(
            AppTheme(
                id = "custom_bg_change",
                name = "Stable Colors Theme",
                isDark = true,
                builtIn = false,
                schemaVersion = ThemeSchema.VERSION_2,
                colors = ThemeColors(
                    background = "#141416",
                    text = "#EDEDF0",
                    accent = "#10B981"
                )
            )
        )
        val initialColors = theme.colors

        val updated = ThemeManager.updateBackgroundImage(
            theme = theme,
            imageUri = "content://media/wallpapers/sunset.jpg",
            dominantColor = "#F97316"
        )

        assertEquals("content://media/wallpapers/sunset.jpg", updated.backgroundImageUri)
        assertEquals("#F97316", updated.savedBgDominantColor)
        // Theme semantic tokens and identity are strictly unchanged
        assertEquals(initialColors, updated.colors)
    }

    // ── 17. Theme Regeneration Continuity ─────────────────────────────────────
    @Test
    fun testThemeRegenerationPreservesRecipeIdentityAndManualOverrides() {
        val understanding = ThemeGenerationEngine.fallbackUnderstanding()
        val originalTheme = ThemeGenerationEngine.createGeneratedTheme(
            name = "Forest Haze",
            understanding = understanding,
            recipe = ThemeGenerationRecipe.ATMOSPHERIC,
            isDark = true,
            influence = ImageInfluence.STRONG,
            writingCharacter = WritingCharacter.WARM
        )

        // User sets a custom manual override
        val themeWithOverride = ThemeManager.updateSemanticOverride(originalTheme, "borderProminent", "#E11D48")
        assertEquals("#E11D48", themeWithOverride.colors.borderProminent)

        // Regenerate theme with a new candidate color
        val regenerated = ThemeManager.regenerateTheme(
            theme = themeWithOverride,
            newSources = ThemeSourcePalette(
                background = "#061A14",
                text = "#E6F4EA",
                accent = "#10B981",
                atmosphericColor = "#047857"
            ),
            preserveOverrides = true
        )

        // Provenance & Recipe preserved
        assertEquals(ThemeGenerationRecipe.ATMOSPHERIC, regenerated.generationMetadata?.recipe)
        assertEquals(ImageInfluence.STRONG, regenerated.generationMetadata?.imageInfluence)
        assertEquals(WritingCharacter.WARM, regenerated.generationMetadata?.writingCharacter)
        // Background and accent updated to new sources
        assertEquals("#061A14", regenerated.colors.background)
        assertEquals("#10B981", regenerated.colors.accent)
        // Manual override preserved
        assertEquals("#E11D48", regenerated.overrides?.borderProminent)
        assertEquals("#E11D48", regenerated.colors.borderProminent)
    }

    // ── 18. Material 3 ColorScheme Bridge ─────────────────────────────────────
    @Test
    fun testMaterialColorSchemeBridgeProducesValidScheme() {
        val theme = ThemeManager.resolveTheme(DefaultThemes.focus)
        val colorScheme = ThemeManager.toMaterialColorScheme(theme)

        // Core component roles exist and are not transparent
        assertNotEquals(0, colorScheme.primary.value.toLong())
        assertNotEquals(0, colorScheme.background.value.toLong())
        assertNotEquals(0, colorScheme.surface.value.toLong())
        assertNotEquals(0, colorScheme.surfaceContainer.value.toLong())
        assertNotEquals(0, colorScheme.surfaceContainerHighest.value.toLong())
        assertNotEquals(0, colorScheme.outline.value.toLong())
    }
}
