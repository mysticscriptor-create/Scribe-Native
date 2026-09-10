package com.primaloptima.scribe.util.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Phase 18 — Structured Image Understanding.
 *
 * Captures deterministic, measurable perceptual characteristics of an artwork or wallpaper:
 * - Ranked candidate colors derived via MCU QuantizerCelebi and Score.
 * - Tonal distribution and key (High-Key, Mid-Key, Low-Key).
 * - Chromatic character (Muted, Balanced, Vivid).
 * - Perceptual temperature bias (Warm, Cool, Neutral).
 * - Dark/light polarity bias.
 * - Palette diversity (Concentrated, Moderate, Diverse).
 * - Content fingerprint for deterministic session caching.
 */
@Immutable
@Serializable
data class ImageUnderstanding(
    val rankedCandidates: List<Int>,
    val dominantColors: List<String>,
    val averageLightness: Float,
    val tonalCharacter: TonalCharacter,
    val chromaticCharacter: ChromaticCharacter,
    val temperatureBias: TemperatureBias,
    val darkLightBias: DarkLightBias,
    val paletteDiversity: PaletteDiversity,
    val imageFingerprint: String? = null,
    val paletteSources: List<ImagePaletteSource> = emptyList(),
    val averageChroma: Float = 0f,
    val dominantHue: Double = 0.0,
    val isMonochromatic: Boolean = false,
    val isExtremeDark: Boolean = false,
    val isExtremeLight: Boolean = false
) {
    /**
     * Phase 20/Session 1: Intelligently inferred initial light/dark polarity.
     * DARK_BIASED or low-key images default to Dark mode.
     * LIGHT_BIASED or high-key images default to Light mode.
     */
    val defaultDarkPolarity: Boolean
        get() = darkLightBias == DarkLightBias.DARK_BIASED || averageLightness < 0.48f
}

@Serializable
enum class VisualRole {
    ATMOSPHERIC,
    PRIMARY_ACCENT,
    SUPPORTING_ACCENT,
    TERTIARY_ACCENT,
    NEUTRAL
}

@Immutable
@Serializable
data class ImagePaletteSource(
    val colorHex: String,
    val colorArgb: Int,
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    val population: Int = 1,
    val score: Double = 0.0,
    val visualRole: VisualRole = VisualRole.NEUTRAL
)

@Serializable
enum class TonalCharacter(val label: String, val description: String) {
    HIGH_KEY("High-Key", "Bright, airy overall luminance with prominent highlights"),
    MID_KEY("Mid-Key", "Balanced, harmonious mid-tone luminance distribution"),
    LOW_KEY("Low-Key", "Deep, shadow-rich mood with anchored dark tones")
}

@Serializable
enum class ChromaticCharacter(val label: String, val description: String) {
    MUTED("Muted", "Soft, restrained saturation spectrum suitable for quiet prose"),
    BALANCED("Balanced", "Natural, organic color saturation across primary elements"),
    VIVID("Vivid", "High-chroma, intense color presence with bold visual energy")
}

@Serializable
enum class TemperatureBias(val label: String, val description: String) {
    WARM("Warm", "Amber, ochre, terracotta, and golden undertones dominate"),
    COOL("Cool", "Slate, azure, pine, and indigo undertones dominate"),
    NEUTRAL("Neutral", "Achromatic or balanced chromatic temperature distribution")
}

@Serializable
enum class DarkLightBias(val label: String) {
    LIGHT_BIASED("Luminous"),
    DARK_BIASED("Shadowed"),
    BALANCED("Balanced")
}

@Serializable
enum class PaletteDiversity(val label: String, val description: String) {
    CONCENTRATED("Concentrated", "Tightly clustered monochromatic or analogous palette"),
    MODERATE("Moderate", "Harmonious adjacent hue spread with focused accents"),
    DIVERSE("Diverse", "Broad, multi-quadrant chromatic range across candidates")
}

/**
 * Phase 18 — Theme Generation Recipes (Interpretations).
 *
 * Four distinct, deterministic interpretive lenses for transforming the same [ImageUnderstanding]
 * into an authoritative [ThemeSourcePalette].
 */
@Serializable
enum class ThemeGenerationRecipe(
    val label: String,
    val description: String,
    val aestheticRole: String
) {
    BALANCED(
        label = "Balanced",
        description = "Natural, calm interpretation with restrained surfaces and image-informed accent",
        aestheticRole = "Calm surfaces, organic accent harmony"
    ),
    ATMOSPHERIC(
        label = "Atmospheric",
        description = "More of the image's mood enters the theme with immersive environmental tinting",
        aestheticRole = "Rich atmospheric surfaces, mood-infused accents"
    ),
    INK(
        label = "Ink",
        description = "Writing-first, restrained and high-contrast editorial clarity",
        aestheticRole = "Deep monochrome ink / crisp parchment, precise accent focus"
    ),
    EXPRESSIVE(
        label = "Expressive",
        description = "Stronger color and visual character with pronounced supporting relationships",
        aestheticRole = "Vibrant chromatic presence, bold editorial identity"
    )
}

/**
 * Human-readable control for how strongly the image modulates semantic surface & accent tokens.
 * NOT image opacity.
 */
@Serializable
enum class ImageInfluence(
    val label: String,
    val description: String
) {
    SUBTLE(
        label = "Subtle",
        description = "Primarily accent identity with minimal canvas tinting"
    ),
    BALANCED(
        label = "Balanced",
        description = "Balanced accent, supporting relationships, and surface harmony"
    ),
    STRONG(
        label = "Strong",
        description = "Pronounced palette character and richer environmental tinting"
    )
}

/**
 * Editorial prose character modulation prior to canonical token resolution.
 */
@Serializable
enum class WritingCharacter(
    val label: String,
    val description: String
) {
    NEUTRAL(
        label = "Neutral",
        description = "Crisp, uncolored editorial prose"
    ),
    WARM(
        label = "Warm",
        description = "Editorial prose with gentle warm parchment undertones"
    ),
    COOL(
        label = "Cool",
        description = "Editorial prose with subtle slate undertones"
    ),
    DRAMATIC(
        label = "Dramatic",
        description = "High-contrast editorial prose with heightened visual weight"
    )
}

/**
 * Relationship mode governing how the source artwork participates in the visual presentation.
 * Decoupled from semantic color calculation.
 */
enum class ThemeRelationshipMode(
    val label: String,
    val description: String
) {
    THEME_ONLY(
        label = "Theme Only",
        description = "Use generated theme colors on a solid canvas without displaying the artwork"
    ),
    THEME_IMAGE(
        label = "Theme + Image",
        description = "Display the artwork under the theme canvas"
    ),
    THEME_GLASS(
        label = "Theme + Glass",
        description = "Use generated colors with translucent frosted glassmorphism"
    )
}
