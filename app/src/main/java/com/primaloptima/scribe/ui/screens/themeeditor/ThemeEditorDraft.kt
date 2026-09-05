package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.runtime.Immutable
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColorOverrides
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeSourcePalette

/**
 * High-level category tabs for the decomposed Theme Editor.
 */
enum class ThemeEditorCategory(val title: String) {
    COLORS("Colors"),
    TYPOGRAPHY("Typography"),
    LAYOUT("Layout"),
    ATMOSPHERE("Atmosphere")
}

/**
 * Identifies which color token is currently targeted by the Color Picker dialog.
 */
enum class ColorPickerTarget {
    // Foundation Sources
    BACKGROUND,
    TEXT,
    ACCENT,

    // Writing Overrides
    HEADING_TEXT,
    DIALOGUE_TEXT,
    MONOLOGUE_TEXT,
    SPECIAL_HIGHLIGHT,
    ANNOTATION,

    // Supporting Accent Overrides
    SECONDARY,
    TERTIARY,

    // Surface Overrides
    SURFACE
}

/**
 * Authoritative draft state representation for the Theme Editor session.
 * Encapsulates the editing lifecycle: Persisted Theme -> Editor Draft -> Resolve -> Preview -> Save.
 */
@Immutable
data class ThemeEditorDraft(
    val id: String,
    val builtIn: Boolean,
    val isDark: Boolean,
    val name: String,
    val emoji: String,

    // Foundation Sources (Authoritative driving inputs)
    val bgHex: String,
    val textHex: String,
    val accentHex: String,

    // Explicit User Overrides (null means token resolves to generated default)
    val overrides: ThemeColorOverrides? = null,

    // Typography
    val fontFamily: String,
    val fontSize: Float,
    val lineHeight: Float,
    val paragraphSpacing: Float,
    val sideMargins: Float,

    // Layout
    val textAlignment: String,
    val themeScope: String,

    // Atmosphere & Background
    val bgMode: String,
    val bgUri: String?,
    val bgOriginalUri: String?,
    val bgOpacity: Float,
    val blurIntensity: Float,
    val frostedGlassEnabled: Boolean,
    val frostedTintEnabled: Boolean,
    val frostedBlurRadius: Float,

    // Image Background Analysis
    val bgLuminance: Float,
    val zonalLuminanceMatrix: List<Float> = emptyList(),
    val zonalVarianceMatrix: List<Float> = emptyList(),
    val bgDominantColor: String? = null,
    val zonalColorsMatrix: List<String> = emptyList(),
    val luminanceFieldMatrix: List<Float> = emptyList()
) {
    /**
     * Resolves the canonical active ThemeColors by layering overrides onto generated defaults.
     */
    fun resolveColors(): ThemeColors {
        return ThemeManager.resolveThemeColors(
            sources = ThemeSourcePalette(
                background = bgHex,
                text = textHex,
                accent = accentHex
            ),
            overrides = overrides,
            isDark = ThemeManager.isDarkColor(bgHex)
        )
    }

    /**
     * Generates pure algorithmic defaults for comparison and reset previews.
     */
    fun generateDefaults(): ThemeColors {
        return ThemeManager.generateThemeDefaults(
            sources = ThemeSourcePalette(
                background = bgHex,
                text = textHex,
                accent = accentHex
            ),
            isDark = ThemeManager.isDarkColor(bgHex)
        )
    }

    /**
     * Checks whether a specific semantic color token is currently overridden.
     */
    fun isOverridden(target: ColorPickerTarget): Boolean {
        if (overrides == null) return false
        return when (target) {
            ColorPickerTarget.HEADING_TEXT -> overrides.headingText != null
            ColorPickerTarget.DIALOGUE_TEXT -> overrides.dialogueText != null
            ColorPickerTarget.MONOLOGUE_TEXT -> overrides.monologueText != null
            ColorPickerTarget.SPECIAL_HIGHLIGHT -> overrides.specialHighlight != null
            ColorPickerTarget.ANNOTATION -> overrides.annotation != null
            ColorPickerTarget.SECONDARY -> overrides.secondary != null
            ColorPickerTarget.TERTIARY -> overrides.tertiary != null
            ColorPickerTarget.SURFACE -> overrides.surface != null
            else -> false
        }
    }

    /**
     * Sets an explicit override for the given semantic target.
     */
    fun withOverride(target: ColorPickerTarget, hex: String): ThemeEditorDraft {
        val current = overrides ?: ThemeColorOverrides()
        val updatedOverrides = when (target) {
            ColorPickerTarget.HEADING_TEXT -> current.copy(headingText = hex)
            ColorPickerTarget.DIALOGUE_TEXT -> current.copy(dialogueText = hex)
            ColorPickerTarget.MONOLOGUE_TEXT -> current.copy(monologueText = hex)
            ColorPickerTarget.SPECIAL_HIGHLIGHT -> current.copy(specialHighlight = hex)
            ColorPickerTarget.ANNOTATION -> current.copy(annotation = hex)
            ColorPickerTarget.SECONDARY -> current.copy(secondary = hex)
            ColorPickerTarget.TERTIARY -> current.copy(tertiary = hex)
            ColorPickerTarget.SURFACE -> current.copy(surface = hex)
            else -> current
        }
        return copy(overrides = updatedOverrides)
    }

    /**
     * Clears an override for the given semantic target, returning it to the generated default.
     */
    fun withClearedOverride(target: ColorPickerTarget): ThemeEditorDraft {
        if (overrides == null) return this
        val updated = when (target) {
            ColorPickerTarget.HEADING_TEXT -> overrides.copy(headingText = null)
            ColorPickerTarget.DIALOGUE_TEXT -> overrides.copy(dialogueText = null)
            ColorPickerTarget.MONOLOGUE_TEXT -> overrides.copy(monologueText = null)
            ColorPickerTarget.SPECIAL_HIGHLIGHT -> overrides.copy(specialHighlight = null)
            ColorPickerTarget.ANNOTATION -> overrides.copy(annotation = null)
            ColorPickerTarget.SECONDARY -> overrides.copy(secondary = null)
            ColorPickerTarget.TERTIARY -> overrides.copy(tertiary = null)
            ColorPickerTarget.SURFACE -> overrides.copy(surface = null)
            else -> overrides
        }
        return copy(overrides = if (updated.isEmpty()) null else updated)
    }

    /**
     * Converts the editor draft into a persisted AppTheme entity upon user save.
     */
    fun toAppTheme(base: AppTheme): AppTheme {
        val resolved = resolveColors()
        return base.copy(
            name = name,
            isDark = ThemeManager.isDarkColor(bgHex),
            emoji = emoji,
            colors = resolved,
            overrides = if (overrides?.isEmpty() == true) null else overrides,
            fontFamily = fontFamily,
            fontSize = fontSize.toInt(),
            lineHeight = lineHeight,
            paragraphSpacing = paragraphSpacing.toInt(),
            paddingHorizontal = sideMargins.toInt(),
            textAlignment = textAlignment,
            themeScope = themeScope,
            bgMode = bgMode,
            backgroundImageUri = bgUri,
            backgroundImageOriginalUri = bgOriginalUri,
            backgroundImageOpacity = bgOpacity,
            blurIntensity = blurIntensity,
            frostedGlassEnabled = frostedGlassEnabled,
            frostedTintEnabled = frostedTintEnabled,
            frostedBlurRadius = frostedBlurRadius,
            savedBgLuminance = bgLuminance,
            savedZonalLuminance = zonalLuminanceMatrix,
            savedZonalVariance = zonalVarianceMatrix,
            savedBgDominantColor = bgDominantColor,
            savedBgZonalColors = zonalColorsMatrix,
            savedBgLuminanceField = luminanceFieldMatrix
        )
    }

    companion object {
        fun fromAppTheme(theme: AppTheme): ThemeEditorDraft {
            return ThemeEditorDraft(
                id = theme.id,
                builtIn = theme.builtIn,
                isDark = theme.isDark,
                name = theme.name,
                emoji = theme.emoji ?: "🖊️",
                bgHex = theme.colors.background,
                textHex = theme.colors.text,
                accentHex = theme.colors.accent,
                overrides = theme.overrides,
                fontFamily = theme.fontFamily,
                fontSize = theme.fontSize.toFloat(),
                lineHeight = theme.lineHeight,
                paragraphSpacing = theme.paragraphSpacing.toFloat(),
                sideMargins = theme.paddingHorizontal.toFloat(),
                textAlignment = theme.textAlignment,
                themeScope = theme.themeScope,
                bgMode = theme.bgMode,
                bgUri = theme.backgroundImageUri,
                bgOriginalUri = theme.backgroundImageOriginalUri,
                bgOpacity = theme.backgroundImageOpacity ?: 0.35f,
                blurIntensity = theme.blurIntensity,
                frostedGlassEnabled = theme.frostedGlassEnabled,
                frostedTintEnabled = theme.frostedTintEnabled,
                frostedBlurRadius = theme.frostedBlurRadius,
                bgLuminance = theme.savedBgLuminance,
                zonalLuminanceMatrix = theme.savedZonalLuminance,
                zonalVarianceMatrix = theme.savedZonalVariance,
                bgDominantColor = theme.savedBgDominantColor,
                zonalColorsMatrix = theme.savedBgZonalColors,
                luminanceFieldMatrix = theme.savedBgLuminanceField
            )
        }
    }
}
