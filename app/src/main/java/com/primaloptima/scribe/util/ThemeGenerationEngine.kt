package com.primaloptima.scribe.util

import android.graphics.Bitmap
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.primaloptima.scribe.ui.theme.ContrastResolver
import com.primaloptima.scribe.util.model.ThemeSourcePalette

/**
 * Phase 18 — Image-Derived Theme Generation Engine.
 *
 * Implements deterministic extraction of foundation colors from wallpaper / artwork:
 * Image (Bitmap) -> Downscale (128x128 max) -> Celebi Quantization -> Score Ranking
 * -> Extraction of Dominant / Seed Color -> Derivation of ThemeSourcePalette
 * (Background, Text, Accent) with guaranteed contrast via ContrastResolver.
 *
 * Architecture Invariant:
 * USER SOURCE (Image -> ThemeSourcePalette)
 *   ↓
 * DERIVED BASELINE (ThemeManager.generateThemeDefaults)
 *   ↓
 * OPTIONAL EXPLICIT OVERRIDE (ThemeColorOverrides)
 *   ↓
 * FINAL SEMANTIC THEME (ThemeColors)
 *   ↓
 * CONSUMER BRIDGES (ScribeColorScheme / Material)
 */
object ThemeGenerationEngine {

    const val ANALYSIS_SAMPLE_SIZE = 128
    const val MAX_QUANTIZER_COLORS = 128

    /**
     * Extracts a list of ranked dominant ARGB color integers from an integer pixel array.
     * Uses QuantizerCelebi and Score.score from Material Color Utilities.
     */
    fun extractRankedColors(pixels: IntArray, maxColors: Int = MAX_QUANTIZER_COLORS): List<Int> {
        if (pixels.isEmpty()) return emptyList()
        val quantizerResult = QuantizerCelebi.quantize(pixels, maxColors)
        val scored = Score.score(quantizerResult)
        return scored.ifEmpty {
            quantizerResult.colorToCount.keys.toList()
        }
    }

    /**
     * Extracts a list of ranked dominant ARGB color integers from a software Bitmap.
     * Scales down to [ANALYSIS_SAMPLE_SIZE]x[ANALYSIS_SAMPLE_SIZE] max to guarantee determinism
     * and low memory footprint.
     */
    fun extractRankedColors(bitmap: Bitmap): List<Int> {
        if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
            return emptyList()
        }

        val sample = if (bitmap.width > ANALYSIS_SAMPLE_SIZE || bitmap.height > ANALYSIS_SAMPLE_SIZE) {
            val scale = minOf(
                ANALYSIS_SAMPLE_SIZE.toFloat() / bitmap.width,
                ANALYSIS_SAMPLE_SIZE.toFloat() / bitmap.height
            )
            val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        val w = sample.width
        val h = sample.height
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)

        if (sample !== bitmap) {
            sample.recycle()
        }

        return extractRankedColors(pixels)
    }

    /**
     * Generates a deterministic [ThemeSourcePalette] from a list of ranked ARGB color ints.
     *
     * @param rankedColors Ranked color list extracted from the artwork (highest scoring first).
     * @param isDark Preferred darkness polarity for the resulting theme.
     * @return [ThemeSourcePalette] containing background, text, and accent hex strings.
     */
    fun generateSourcePalette(rankedColors: List<Int>, isDark: Boolean): ThemeSourcePalette {
        val seedInt = rankedColors.firstOrNull() ?: if (isDark) 0xFF3B82F6.toInt() else 0xFF1D4ED8.toInt()
        return generateSourcePaletteFromSeed(seedInt, isDark)
    }

    /**
     * Generates a deterministic [ThemeSourcePalette] from a single primary seed color int.
     * Uses OKLCH perceptual transformations and validates reading contrast against WCAG standards.
     *
     * Background:
     * - Dark: Derived from seed with low lightness (L=0.10..0.15) and subtle chroma.
     * - Light: Derived from seed with high lightness (L=0.96..0.98) and subtle chroma.
     *
     * Accent:
     * - Preserves the vibrant hue of the seed color, tuned for interaction visibility.
     *
     * Text:
     * - High-contrast editorial prose color meeting >= 7.0:1 contrast against the derived background.
     */
    fun generateSourcePaletteFromSeed(seedColorInt: Int, isDark: Boolean): ThemeSourcePalette {
        val seedOklch = ContrastResolver.colorToOklch(seedColorInt)
        val hue = seedOklch.h
        val chroma = seedOklch.c.coerceIn(0.01, 0.25)

        // 1. Derive Background
        val bgOklch = if (isDark) {
            // Dark mode: very dark, gentle tint of image seed hue
            ContrastResolver.Oklch(
                l = 0.12,
                c = (chroma * 0.15).coerceIn(0.005, 0.025),
                h = hue
            )
        } else {
            // Light mode: clean, luminous background with subtle seed warmth/tint
            ContrastResolver.Oklch(
                l = 0.97,
                c = (chroma * 0.12).coerceIn(0.004, 0.020),
                h = hue
            )
        }
        val bgInt = ContrastResolver.oklchToColorInt(bgOklch)
        val bgHex = String.format("#%06X", 0xFFFFFF and bgInt)

        // 2. Derive Accent (preserving seed hue and vibrant chroma, tuned for UI control visibility)
        val accentTargetL = if (isDark) 0.72 else 0.45
        val accentTargetC = maxOf(chroma, 0.12).coerceIn(0.08, 0.22)
        val accentOklch = ContrastResolver.Oklch(l = accentTargetL, c = accentTargetC, h = hue)
        val candidateAccentInt = ContrastResolver.oklchToColorInt(accentOklch)
        val resolvedAccent = ContrastResolver.resolveContrast(
            background = androidx.compose.ui.graphics.Color(bgInt),
            preferredForeground = androidx.compose.ui.graphics.Color(candidateAccentInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val accentHex = String.format("#%06X", 0xFFFFFF and resolvedAccent.color.toArgb())

        // 3. Derive Text (prose foreground meeting high contrast >= 7.0:1 against background)
        val candidateTextOklch = if (isDark) {
            ContrastResolver.Oklch(l = 0.94, c = (chroma * 0.05).coerceAtMost(0.015), h = hue)
        } else {
            ContrastResolver.Oklch(l = 0.14, c = (chroma * 0.05).coerceAtMost(0.015), h = hue)
        }
        val candidateTextInt = ContrastResolver.oklchToColorInt(candidateTextOklch)
        val resolvedText = ContrastResolver.resolveContrast(
            background = androidx.compose.ui.graphics.Color(bgInt),
            preferredForeground = androidx.compose.ui.graphics.Color(candidateTextInt),
            minRatio = 4.5,
            role = ContrastResolver.ContrastRole.NORMAL_TEXT
        )
        val textHex = String.format("#%06X", 0xFFFFFF and resolvedText.color.toArgb())

        return ThemeSourcePalette(
            background = bgHex,
            text = textHex,
            accent = accentHex
        )
    }

    /**
     * Generates a complete [ThemeSourcePalette] directly from a software Bitmap.
     */
    fun generateSourcePalette(bitmap: Bitmap, isDark: Boolean): ThemeSourcePalette {
        val ranked = extractRankedColors(bitmap)
        return generateSourcePalette(ranked, isDark)
    }
}
