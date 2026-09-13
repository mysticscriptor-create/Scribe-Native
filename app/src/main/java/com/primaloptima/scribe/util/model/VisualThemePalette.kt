package com.primaloptima.scribe.util.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Phase 20.1 — Visual Theme Palette (The Visual Palette Distribution Layer).
 *
 * Sits architecturally between the multi-color extracted image palette and
 * Scribe's canonical semantic tokens:
 *
 *   IMAGE
 *       ↓
 *   IMAGE UNDERSTANDING (multi-color extraction & analysis)
 *       ↓
 *   MULTI-COLOR IMAGE PALETTE (Source Colors: atmosphere, candidate, supporting, highlight)
 *       ↓
 *   VISUAL THEME PALETTE (Visual Theme Roles: canvas, editor, chrome, card, accents)
 *       ↓
 *   SCRIBE SEMANTIC TOKENS (ThemeColors / ScribeColors)
 *       ↓
 *   ACTUAL COMPONENTS (Editor, Top Bar, Bottom Bar, Cards, FAB, Dialogue, Headings)
 *
 * This layer describes WHERE image colors influence the interface physically,
 * rather than their functional/semantic status meaning.
 */
@Immutable
@Serializable
data class VisualThemePalette(
    val visualCanvas: String,           // Canvas / app background (L0)
    val visualEditorSurface: String,     // Editor surface / reading canvas (L1)
    val visualElevatedSurface: String,   // Elevated surfaces / cards / floating panes (L3)
    val visualChrome: String,            // Primary chrome: top app bar, bottom bar, primary drawers (L2)
    val visualSecondaryChrome: String,   // Secondary chrome: tool trays, sunken split gutters, tab strips
    val visualPrimaryAccent: String,     // Primary action / FAB / active triggers / carets
    val visualSecondaryAccent: String,   // Secondary action / chips / tabs / supporting icons
    val visualTertiaryAccent: String,    // Tertiary tools / auxiliary indicators / monologue
    val visualHighlight: String,         // Highlights / annotations / search matches / literary emphasis
    val visualNeutral: String            // Structural borders / dividers / quiet outlines
)

/**
 * Developer-facing visual palette distribution report model for validation and diagnostics.
 */
data class VisualPaletteReport(
    val recipe: ThemeGenerationRecipe,
    val isDark: Boolean,
    val influence: ImageInfluence,
    val writingCharacter: WritingCharacter,
    val sourceColors: List<ImagePaletteSource>,
    val visualPalette: VisualThemePalette,
    val tokenDistribution: Map<String, String>
) {
    fun toFormattedString(): String {
        val sb = StringBuilder()
        sb.appendLine("=== VISUAL PALETTE DISTRIBUTION REPORT ===")
        sb.appendLine("Recipe: ${recipe.label} | Polarity: ${if (isDark) "Dark" else "Light"} | Influence: ${influence.label} | Character: ${writingCharacter.label}")
        sb.appendLine("\n--- 1. SOURCE IMAGE COLORS ---")
        sourceColors.forEach {
            sb.appendLine("  • ${it.visualRole}: ${it.colorHex} (H=${it.hue.toInt()}°, C=${"%.3f".format(it.chroma)}, L=${"%.3f".format(it.tone)})")
        }
        sb.appendLine("\n--- 2. VISUAL THEME PALETTE ROLES ---")
        sb.appendLine("  • VISUAL CANVAS:            ${visualPalette.visualCanvas}")
        sb.appendLine("  • VISUAL EDITOR SURFACE:     ${visualPalette.visualEditorSurface}")
        sb.appendLine("  • VISUAL CHROME:            ${visualPalette.visualChrome}")
        sb.appendLine("  • VISUAL SECONDARY CHROME:  ${visualPalette.visualSecondaryChrome}")
        sb.appendLine("  • VISUAL ELEVATED SURFACE:  ${visualPalette.visualElevatedSurface}")
        sb.appendLine("  • VISUAL PRIMARY ACCENT:    ${visualPalette.visualPrimaryAccent}")
        sb.appendLine("  • VISUAL SECONDARY ACCENT:  ${visualPalette.visualSecondaryAccent}")
        sb.appendLine("  • VISUAL TERTIARY ACCENT:   ${visualPalette.visualTertiaryAccent}")
        sb.appendLine("  • VISUAL HIGHLIGHT:         ${visualPalette.visualHighlight}")
        sb.appendLine("  • VISUAL NEUTRAL:           ${visualPalette.visualNeutral}")
        sb.appendLine("\n--- 3. SCRIBE SEMANTIC TOKEN MAPPING ---")
        tokenDistribution.forEach { (token, sourceRole) ->
            sb.appendLine("  • $token -> $sourceRole")
        }
        return sb.toString()
    }
}
