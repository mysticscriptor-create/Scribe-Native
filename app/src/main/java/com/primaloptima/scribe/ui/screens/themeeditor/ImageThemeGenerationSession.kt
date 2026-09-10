package com.primaloptima.scribe.ui.screens.themeeditor

import com.primaloptima.scribe.util.ThemeGenerationEngine
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ImageUnderstanding
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import com.primaloptima.scribe.util.model.WritingCharacter

/**
 * Phase 18.1: Dedicated Image Theme Generation Session State.
 *
 * Encapsulates temporary, creation-time inputs and algorithms used exclusively
 * while creating a new theme from an image.
 *
 * ARCHITECTURAL BOUNDARY:
 * - This session exists ONLY during the image creation workflow.
 * - Its state (candidate swatches, recipes, influence, character) is never persisted directly.
 * - When accepted, this session produces a standard, permanent [AppTheme] with ordinary
 *   foundation colors and presentation settings.
 * - Ordinary theme editing does NOT consume or retain this generation session.
 */
data class ImageThemeGenerationSession(
    val imageUri: String,
    val croppedUri: String? = null,
    val understanding: ImageUnderstanding,
    val selectedCandidate: Int? = null,
    val activeRecipe: ThemeGenerationRecipe = ThemeGenerationRecipe.BALANCED,
    val activeInfluence: ImageInfluence = ImageInfluence.BALANCED,
    val activeWritingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
    val isDark: Boolean = understanding.defaultDarkPolarity,
    val relationshipMode: ThemeRelationshipMode = ThemeRelationshipMode.THEME_IMAGE,
    val customName: String? = null
) {
    /**
     * Resolves the active seed candidate color (user-selected or first ranked candidate).
     */
    val effectiveCandidate: Int
        get() = selectedCandidate
            ?: understanding.rankedCandidates.firstOrNull()
            ?: 0xFF3B82F6.toInt()

    /**
     * Algorithmic [ThemeSourcePalette] derived deterministically from the generation inputs.
     */
    val sourcePalette: ThemeSourcePalette
        get() = ThemeGenerationEngine.generateSourcePalette(
            understanding = understanding,
            recipe = activeRecipe,
            candidateColor = effectiveCandidate,
            isDark = isDark,
            influence = activeInfluence,
            writingCharacter = activeWritingCharacter
        )

    /**
     * Resolved 40+ semantic [ThemeColors] derived via OKLCH from the generated source palette.
     */
    val resolvedColors: ThemeColors
        get() = ThemeManager.resolveThemeColors(
            sources = sourcePalette,
            overrides = null,
            isDark = isDark
        )

    /**
     * User-specified name or intelligent generated title derived from artwork character.
     */
    val effectiveThemeName: String
        get() = customName?.takeIf { it.isNotBlank() }
            ?: ThemeGenerationEngine.generateThemeName(
                understanding = understanding,
                recipe = activeRecipe,
                seedColor = effectiveCandidate,
                isDark = isDark
            )

    fun withCandidate(candidate: Int): ImageThemeGenerationSession =
        copy(selectedCandidate = candidate)

    fun withRecipe(recipe: ThemeGenerationRecipe): ImageThemeGenerationSession =
        copy(activeRecipe = recipe)

    fun withInfluence(influence: ImageInfluence): ImageThemeGenerationSession =
        copy(activeInfluence = influence)

    fun withWritingCharacter(character: WritingCharacter): ImageThemeGenerationSession =
        copy(activeWritingCharacter = character)

    fun withPolarity(isDark: Boolean): ImageThemeGenerationSession =
        copy(isDark = isDark)

    fun withRelationshipMode(mode: ThemeRelationshipMode): ImageThemeGenerationSession =
        copy(relationshipMode = mode)

    fun withThemeName(name: String): ImageThemeGenerationSession =
        copy(customName = name)

    fun withCroppedImage(uri: String): ImageThemeGenerationSession =
        copy(croppedUri = uri)

    /**
     * Converts the accepted generation session into a permanent [AppTheme].
     *
     * Once created:
     * - The generated colors become the theme's ordinary persisted semantic colors.
     * - Presentation settings (background image, glass) become standard theme properties.
     * - The generation session is completely terminated and garbage collected.
     */
    fun toAppTheme(id: String, baseTheme: AppTheme): AppTheme {
        val palette = sourcePalette
        val colors = resolvedColors
        val effectiveImage = croppedUri ?: imageUri

        val (bgMode, bgUri, frosted) = when (relationshipMode) {
            ThemeRelationshipMode.THEME_ONLY -> Triple("color", null, false)
            ThemeRelationshipMode.THEME_IMAGE -> Triple("image", effectiveImage, false)
            ThemeRelationshipMode.THEME_GLASS -> Triple("image", effectiveImage, true)
        }

        return baseTheme.copy(
            id = id,
            name = effectiveThemeName,
            isDark = isDark,
            builtIn = false,
            emoji = "🎨",
            colors = colors,
            overrides = null,
            bgMode = bgMode,
            backgroundImageUri = bgUri,
            backgroundImageOriginalUri = if (relationshipMode == ThemeRelationshipMode.THEME_ONLY) null else imageUri,
            frostedGlassEnabled = frosted,
            savedBgDominantColor = if (relationshipMode == ThemeRelationshipMode.THEME_ONLY) null else understanding.dominantColors.firstOrNull(),
            savedBgLuminance = if (relationshipMode == ThemeRelationshipMode.THEME_ONLY) -1f else understanding.averageLightness
        )
    }
}
