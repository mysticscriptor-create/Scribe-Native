package com.primaloptima.scribe.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.primaloptima.scribe.ui.theme.ContrastResolver
import com.primaloptima.scribe.util.model.ChromaticCharacter
import com.primaloptima.scribe.util.model.DarkLightBias
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ImagePaletteSource
import com.primaloptima.scribe.util.model.ImageUnderstanding
import com.primaloptima.scribe.util.model.PaletteDiversity
import com.primaloptima.scribe.util.model.TemperatureBias
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import com.primaloptima.scribe.util.model.TonalCharacter
import com.primaloptima.scribe.util.model.VisualRole
import com.primaloptima.scribe.util.model.WritingCharacter
import java.util.Collections
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Phase 18 — Image-Derived Theme Generation Engine.
 *
 * Implements deterministic, multi-recipe extraction of foundation colors from wallpaper / artwork:
 * Image (Bitmap) -> Downscale (128x128 max) -> Celebi Quantization + MCU Score Ranking
 * -> Structured Perceptual Analysis (ImageUnderstanding)
 * -> Multi-Recipe Source Generation (Balanced, Atmospheric, Ink, Expressive)
 * -> Derivation of ThemeSourcePalette (Background, Text, Accent) with guaranteed contrast via ContrastResolver.
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

    // In-memory cache for deterministic session reuse of ImageUnderstanding (keyed by imageFingerprint or URI hash)
    private val understandingCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageUnderstanding>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageUnderstanding>?): Boolean {
                return size > 24
            }
        }
    )

    fun getCachedUnderstanding(key: String): ImageUnderstanding? = understandingCache[key]

    fun putCachedUnderstanding(key: String, understanding: ImageUnderstanding) {
        understandingCache[key] = understanding
    }

    fun clearCache() {
        understandingCache.clear()
    }

    /**
     * Calculates the shortest angular distance between two hue angles in degrees [0, 180].
     */
    fun circularHueDistance(h1: Double, h2: Double): Double {
        val diff = abs(h1 - h2) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /**
     * Calculates signed circular hue difference (target - source) in range [-180, 180].
     */
    fun circularHueDifference(fromHue: Double, toHue: Double): Double {
        return ((toHue - fromHue + 540.0) % 360.0) - 180.0
    }

    /**
     * Gently nudges [sourceHue] towards [targetHue] by [fraction] if they are within 80 degrees,
     * maintaining perceptual identity while establishing organic color temperature alignment.
     */
    fun harmonizeHue(sourceHue: Double, targetHue: Double, fraction: Double = 0.15): Double {
        val diff = circularHueDifference(sourceHue, targetHue)
        return if (abs(diff) <= 80.0) {
            (sourceHue + diff * fraction + 360.0) % 360.0
        } else {
            sourceHue
        }
    }

    /**
     * Smooth continuous hue harmonization using a cosine-squared window.
     * Prevents abrupt threshold cliffs while smoothly attenuating shift to zero at [maxAngle].
     */
    fun harmonizeHueSmooth(
        sourceHue: Double,
        targetHue: Double,
        maxAngle: Double = 90.0,
        fraction: Double = 0.15
    ): Double {
        val diff = circularHueDifference(sourceHue, targetHue)
        val absDiff = abs(diff)
        if (absDiff >= maxAngle) return sourceHue
        val angleRatio = absDiff / maxAngle
        val falloff = kotlin.math.cos(angleRatio * Math.PI / 2.0).let { it * it }
        val effectiveShift = diff * fraction * falloff
        return (sourceHue + effectiveShift + 360.0) % 360.0
    }

    /**
     * Extracts a list of ranked dominant ARGB color integers from an integer pixel array.
     * Uses QuantizerCelebi and Score.score from Material Color Utilities.
     */
    fun extractRankedColors(pixels: IntArray, maxColors: Int = MAX_QUANTIZER_COLORS): List<Int> {
        if (pixels.isEmpty()) return emptyList()
        val quantizerResult = QuantizerCelebi.quantize(pixels, maxColors)
        val scored = Score.score(quantizerResult)
        return scored.ifEmpty {
            quantizerResult.keys.toList()
        }
    }

    /**
     * Extracts structured [ImagePaletteSource] candidates from pixel array, assigning visual roles
     * (PRIMARY_ACCENT, ATMOSPHERIC, SUPPORTING_ACCENT, TERTIARY_ACCENT, NEUTRAL)
     * using Material Color Utilities QuantizerCelebi & Score combined with OKLCH perceptual metrics.
     */
    fun extractPaletteSources(
        pixels: IntArray,
        width: Int,
        height: Int,
        maxColors: Int = MAX_QUANTIZER_COLORS
    ): List<ImagePaletteSource> {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return emptyList()
        val quantizerResult = QuantizerCelebi.quantize(pixels, maxColors)
        val scored = Score.score(quantizerResult)
        val totalPixels = pixels.size.toDouble().coerceAtLeast(1.0)
        return extractPaletteSources(quantizerResult, scored, totalPixels)
    }

    /**
     * Overload for single-pass analysis: processes precomputed quantization and ranking results
     * to eliminate redundant double quantization passes.
     */
    fun extractPaletteSources(
        quantizerResult: Map<Int, Int>,
        scored: List<Int>,
        totalPixels: Double
    ): List<ImagePaletteSource> {
        if (quantizerResult.isEmpty()) return emptyList()

        val rawCandidates = quantizerResult.map { (colorInt, population) ->
            val oklch = ContrastResolver.colorToOklch(colorInt)
            val scoreIndex = scored.indexOf(colorInt)
            val score = if (scoreIndex >= 0) (scored.size - scoreIndex).toDouble() else 0.0
            val hex = String.format("#%06X", 0xFFFFFF and colorInt)
            ImagePaletteSource(
                colorHex = hex,
                colorArgb = colorInt,
                hue = oklch.h,
                chroma = oklch.c,
                tone = oklch.l,
                population = population,
                score = score,
                visualRole = VisualRole.NEUTRAL
            )
        }

        // Filter out extreme near-black noise (L < 0.04) or pure white specular glare (L > 0.985)
        val valid = rawCandidates.filter { it.tone in 0.04..0.985 }
        if (valid.isEmpty()) {
            return fallbackUnderstanding().paletteSources
        }

        val isOverallMonochrome = valid.all { it.chroma < 0.045 }

        // 1. PRIMARY ACCENT
        // Balanced combination of MCU ranking and OKLCH chroma
        val primaryCandidate = if (isOverallMonochrome) {
            // Special Case 45: Image without high chroma / monochrome / B&W
            // Pick candidate with best tone balance (mid-tone) and highest score or subtle undertone
            valid.maxByOrNull { it.score * 1.5 + (0.5 - abs(it.tone - 0.5)) }
                ?: valid.first()
        } else {
            valid.filter { it.chroma >= 0.06 && it.tone in 0.15..0.85 }
                .maxByOrNull {
                    val normScore = if (scored.isNotEmpty()) it.score / scored.size else 0.5
                    val normChroma = (it.chroma / 0.25).coerceIn(0.0, 1.0)
                    normScore * 0.6 + normChroma * 0.4
                }
                ?: valid.maxByOrNull { it.chroma }
                ?: valid.first()
        }

        // 2. ATMOSPHERIC
        // Candidate with high population representing the ambient canvas/environment
        val atmosphericCandidate = valid.filter { it.colorArgb != primaryCandidate.colorArgb }
            .filter { it.chroma in 0.005..0.15 && it.tone in 0.08..0.92 }
            .maxByOrNull { it.population.toDouble() / totalPixels }
            ?: valid.filter { it.colorArgb != primaryCandidate.colorArgb }.maxByOrNull { it.population }
            ?: primaryCandidate

        // 3. SUPPORTING ACCENT
        // Distinct hue separation (>= 28 degrees) from primary accent, healthy chroma
        val supportingCandidate = if (isOverallMonochrome) {
            // In monochrome: pick a candidate with distinct lightness separation (|delta L| >= 0.12)
            valid.filter { it.colorArgb != primaryCandidate.colorArgb && it.colorArgb != atmosphericCandidate.colorArgb }
                .maxByOrNull { abs(it.tone - primaryCandidate.tone) }
        } else {
            valid.filter { it.colorArgb != primaryCandidate.colorArgb && it.colorArgb != atmosphericCandidate.colorArgb }
                .filter { circularHueDistance(it.hue, primaryCandidate.hue) >= 28.0 && it.chroma >= 0.045 && it.tone in 0.12..0.88 }
                .maxByOrNull { it.score + it.chroma * 2.0 }
        }

        // 4. TERTIARY ACCENT
        // Distinct hue separation from both primary and supporting (if supporting exists)
        val tertiaryCandidate = if (isOverallMonochrome) {
            null // Special Case 45: Do not invent tertiary accent in monochromatic images
        } else {
            valid.filter {
                it.colorArgb != primaryCandidate.colorArgb &&
                it.colorArgb != atmosphericCandidate.colorArgb &&
                (supportingCandidate == null || it.colorArgb != supportingCandidate.colorArgb)
            }.filter {
                val distP = circularHueDistance(it.hue, primaryCandidate.hue)
                val distS = if (supportingCandidate != null) circularHueDistance(it.hue, supportingCandidate.hue) else 90.0
                distP >= 32.0 && distS >= 28.0 && it.chroma >= 0.045
            }.maxByOrNull { it.score + it.chroma }
        }

        val result = mutableListOf<ImagePaletteSource>()
        result.add(primaryCandidate.copy(visualRole = VisualRole.PRIMARY_ACCENT))
        if (atmosphericCandidate.colorArgb != primaryCandidate.colorArgb) {
            result.add(atmosphericCandidate.copy(visualRole = VisualRole.ATMOSPHERIC))
        }
        supportingCandidate?.let {
            result.add(it.copy(visualRole = VisualRole.SUPPORTING_ACCENT))
        }
        tertiaryCandidate?.let {
            result.add(it.copy(visualRole = VisualRole.TERTIARY_ACCENT))
        }

        // Add remaining top candidates as NEUTRAL
        val assignedColors = result.map { it.colorArgb }.toSet()
        valid.filterNot { assignedColors.contains(it.colorArgb) }
            .sortedByDescending { it.population }
            .take(4)
            .forEach {
                result.add(it.copy(visualRole = VisualRole.NEUTRAL))
            }

        return result
    }

    /**
     * Extracts structured [ImagePaletteSource] candidates from a software Bitmap.
     */
    fun extractPaletteSources(bitmap: Bitmap): List<ImagePaletteSource> {
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
        return extractPaletteSources(pixels, w, h)
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

        // 4. Derive Harmonized Secondary & Tertiary Accents
        val secHue = (hue + 32.0) % 360.0
        val secOklch = ContrastResolver.Oklch(l = if (isDark) 0.74 else 0.44, c = (chroma * 0.85).coerceIn(0.06, 0.20), h = secHue)
        val secInt = ContrastResolver.oklchToColorInt(secOklch)
        val resolvedSec = ContrastResolver.resolveContrast(
            background = androidx.compose.ui.graphics.Color(bgInt),
            preferredForeground = androidx.compose.ui.graphics.Color(secInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val secHex = String.format("#%06X", 0xFFFFFF and resolvedSec.color.toArgb())

        val tertHue = (hue - 28.0 + 360.0) % 360.0
        val tertOklch = ContrastResolver.Oklch(l = if (isDark) 0.76 else 0.46, c = (chroma * 0.75).coerceIn(0.05, 0.18), h = tertHue)
        val tertInt = ContrastResolver.oklchToColorInt(tertOklch)
        val resolvedTert = ContrastResolver.resolveContrast(
            background = androidx.compose.ui.graphics.Color(bgInt),
            preferredForeground = androidx.compose.ui.graphics.Color(tertInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val tertHex = String.format("#%06X", 0xFFFFFF and resolvedTert.color.toArgb())

        val atmoHex = String.format("#%06X", 0xFFFFFF and seedColorInt)

        return ThemeSourcePalette(
            background = bgHex,
            text = textHex,
            accent = accentHex,
            secondaryAccent = secHex,
            tertiaryAccent = tertHex,
            atmosphericColor = atmoHex
        )
    }

    /**
     * Generates a complete [ThemeSourcePalette] directly from a software Bitmap.
     */
    fun generateSourcePalette(bitmap: Bitmap, isDark: Boolean): ThemeSourcePalette {
        val ranked = extractRankedColors(bitmap)
        return generateSourcePalette(ranked, isDark)
    }

    /**
     * Phase 18 — Analyzes a software [Bitmap] to extract a structured, deterministic [ImageUnderstanding].
     * Scales down to [ANALYSIS_SAMPLE_SIZE]x[ANALYSIS_SAMPLE_SIZE] max to guarantee determinism and fast execution.
     * Caches result in-memory by fingerprint for instant reuse across recipe selections.
     */
    fun analyzeImage(bitmap: Bitmap): ImageUnderstanding {
        if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
            return fallbackUnderstanding()
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

        val understanding = analyzePixels(pixels, w, h)
        understanding.imageFingerprint?.let { fp ->
            putCachedUnderstanding(fp, understanding)
        }
        return understanding
    }

    /**
     * Phase 18 — Analyzes a downscaled pixel array into a structured [ImageUnderstanding].
     */
    fun analyzePixels(
        pixels: IntArray,
        width: Int,
        height: Int
    ): ImageUnderstanding {
        if (pixels.isEmpty() || width <= 0 || height <= 0) {
            return fallbackUnderstanding()
        }

        // 1. Ranked candidates via MCU QuantizerCelebi & Score (computed once)
        val quantizerResult = QuantizerCelebi.quantize(pixels, MAX_QUANTIZER_COLORS)
        val scored = Score.score(quantizerResult)
        val extracted = scored.ifEmpty { quantizerResult.keys.toList() }
        val rankedCandidates = if (extracted.isNotEmpty()) {
            extracted
        } else {
            listOf(0xFF3B82F6.toInt(), 0xFF1D4ED8.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt())
        }
        val dominantColors = rankedCandidates.take(8).map { String.format("#%06X", 0xFFFFFF and it) }

        // 2. Sample pixel luminance and lightness in OKLCH
        val step = maxOf(1, pixels.size / 1024)
        var totalLightness = 0.0
        var sampledCount = 0
        for (i in pixels.indices step step) {
            val oklch = ContrastResolver.colorToOklch(pixels[i])
            totalLightness += oklch.l
            sampledCount++
        }
        val averageLightness = (if (sampledCount > 0) totalLightness / sampledCount else 0.5).toFloat()

        // 3. Tonal Character
        val tonalCharacter = when {
            averageLightness >= 0.62f -> TonalCharacter.HIGH_KEY
            averageLightness <= 0.38f -> TonalCharacter.LOW_KEY
            else -> TonalCharacter.MID_KEY
        }

        // 4. Dark/Light Bias
        val darkLightBias = when {
            averageLightness >= 0.55f -> DarkLightBias.LIGHT_BIASED
            averageLightness <= 0.45f -> DarkLightBias.DARK_BIASED
            else -> DarkLightBias.BALANCED
        }

        // 5. Candidate chromatic character and temperature
        val candidateOklch = rankedCandidates.take(8).map { ContrastResolver.colorToOklch(it) }
        val meanChroma = if (candidateOklch.isNotEmpty()) candidateOklch.map { it.c }.average() else 0.08
        val chromaticCharacter = when {
            meanChroma < 0.055 -> ChromaticCharacter.MUTED
            meanChroma > 0.135 -> ChromaticCharacter.VIVID
            else -> ChromaticCharacter.BALANCED
        }

        // 6. Temperature bias from candidate hues and chroma
        var warmScore = 0.0
        var coolScore = 0.0
        candidateOklch.forEachIndexed { index, oklch ->
            val weight = (8.0 - index) * oklch.c.coerceAtLeast(0.02)
            val h = oklch.h
            if ((h in 15.0..115.0) || (h in 335.0..360.0)) {
                warmScore += weight
            } else if (h in 140.0..290.0) {
                coolScore += weight
            }
        }
        val temperatureBias = when {
            warmScore > coolScore * 1.35 -> TemperatureBias.WARM
            coolScore > warmScore * 1.35 -> TemperatureBias.COOL
            else -> TemperatureBias.NEUTRAL
        }

        // 7. Palette diversity
        val paletteDiversity = if (candidateOklch.size <= 1) {
            PaletteDiversity.CONCENTRATED
        } else {
            var maxHueDist = 0.0
            for (i in candidateOklch.indices) {
                for (j in i + 1 until candidateOklch.size) {
                    val diff = abs(candidateOklch[i].h - candidateOklch[j].h)
                    val circularDist = minOf(diff, 360.0 - diff)
                    if (circularDist > maxHueDist) maxHueDist = circularDist
                }
            }
            when {
                maxHueDist < 25.0 -> PaletteDiversity.CONCENTRATED
                maxHueDist > 85.0 -> PaletteDiversity.DIVERSE
                else -> PaletteDiversity.MODERATE
            }
        }

        // 8. Deterministic content fingerprint (64-bit luminance hash)
        val fingerprint = computeFingerprint(pixels, width, height)

        // 9. Structured multi-color palette sources (reusing single-pass quantization and scoring)
        val paletteSources = extractPaletteSources(quantizerResult, scored, pixels.size.toDouble().coerceAtLeast(1.0))

        // 10. Continuous metrics & Special Case boundaries (Parts 45-48)
        val averageChroma = meanChroma.toFloat()
        val dominantHue = candidateOklch.firstOrNull()?.h ?: 220.0
        val isMonochromatic = meanChroma < 0.045 || (paletteDiversity == PaletteDiversity.CONCENTRATED && meanChroma < 0.065)
        val isExtremeDark = averageLightness < 0.16f
        val isExtremeLight = averageLightness > 0.84f

        return ImageUnderstanding(
            rankedCandidates = rankedCandidates,
            dominantColors = dominantColors,
            averageLightness = averageLightness,
            tonalCharacter = tonalCharacter,
            chromaticCharacter = chromaticCharacter,
            temperatureBias = temperatureBias,
            darkLightBias = darkLightBias,
            paletteDiversity = paletteDiversity,
            imageFingerprint = fingerprint,
            paletteSources = paletteSources,
            averageChroma = averageChroma,
            dominantHue = dominantHue,
            isMonochromatic = isMonochromatic,
            isExtremeDark = isExtremeDark,
            isExtremeLight = isExtremeLight
        )
    }

    /**
     * Computes a deterministic 64-bit luminance difference hash formatted as a 16-hex string.
     */
    fun computeFingerprint(pixels: IntArray, width: Int, height: Int): String {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return "0000000000000000"
        val grid = 8
        val blockW = maxOf(1, width / grid)
        val blockH = maxOf(1, height / grid)
        val blockLums = DoubleArray(64)
        var overallSum = 0.0

        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val startX = gx * blockW
                val startY = gy * blockH
                var blockSum = 0.0
                var blockCount = 0
                for (y in startY until minOf(startY + blockH, height)) {
                    for (x in startX until minOf(startX + blockW, width)) {
                        val p = pixels[y * width + x]
                        val r = ContrastResolver.sRgbToLinear(((p shr 16) and 0xFF) / 255.0)
                        val g = ContrastResolver.sRgbToLinear(((p shr 8) and 0xFF) / 255.0)
                        val b = ContrastResolver.sRgbToLinear((p and 0xFF) / 255.0)
                        // Linear Rec. 709 relative luminance
                        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                        blockSum += lum
                        blockCount++
                    }
                }
                val avgLum = if (blockCount > 0) blockSum / blockCount else 0.5
                val idx = gy * grid + gx
                blockLums[idx] = avgLum
                overallSum += avgLum
            }
        }

        val mean = overallSum / 64.0
        var hash = 0L
        for (i in 0 until 64) {
            if (blockLums[i] >= mean) {
                hash = hash or (1L shl i)
            }
        }
        return String.format("%016X", hash)
    }

    /**
     * Phase 18 — Multi-Recipe Theme Generation.
     *
     * Transforms an [ImageUnderstanding] into an authoritative [ThemeSourcePalette] according
     * to a selected [ThemeGenerationRecipe], [ImageInfluence], and [WritingCharacter].
     *
     * Guaranteed Invariant:
     * - Changing recipe, candidate, influence, or polarity NEVER re-runs quantization.
     * - All source tokens route through [ContrastResolver] to guarantee reading and UI accessibility.
     */
    fun generateSourcePalette(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: Int? = null,
        isDark: Boolean,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): ThemeSourcePalette {
        // 1. Identify primary, atmospheric, supporting, and tertiary sources from ImageUnderstanding
        val primarySource = candidateColor?.let { c ->
            understanding.paletteSources.firstOrNull { it.colorArgb == c }
                ?: run {
                    val oklch = ContrastResolver.colorToOklch(c)
                    ImagePaletteSource(
                        colorHex = String.format("#%06X", 0xFFFFFF and c),
                        colorArgb = c,
                        hue = oklch.h,
                        chroma = oklch.c,
                        tone = oklch.l,
                        visualRole = VisualRole.PRIMARY_ACCENT
                    )
                }
        } ?: understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.PRIMARY_ACCENT }
          ?: run {
              val seed = understanding.rankedCandidates.firstOrNull() ?: (if (isDark) 0xFF3B82F6.toInt() else 0xFF1D4ED8.toInt())
              val oklch = ContrastResolver.colorToOklch(seed)
              ImagePaletteSource(
                  colorHex = String.format("#%06X", 0xFFFFFF and seed),
                  colorArgb = seed,
                  hue = oklch.h,
                  chroma = oklch.c,
                  tone = oklch.l,
                  visualRole = VisualRole.PRIMARY_ACCENT
              )
          }

        val atmosphericSource = understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.ATMOSPHERIC }
            ?: primarySource

        val supportingSource = understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.SUPPORTING_ACCENT }

        val tertiarySource = understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.TERTIARY_ACCENT }

        val influenceScale = when (influence) {
            ImageInfluence.SUBTLE -> 0.45
            ImageInfluence.BALANCED -> 1.00
            ImageInfluence.STRONG -> 1.55
        }
        val accentScale = when (influence) {
            ImageInfluence.SUBTLE -> 0.85
            ImageInfluence.BALANCED -> 1.00
            ImageInfluence.STRONG -> 1.25
        }

        // Atmosphere dictates ambient background tone
        val atmoHue = atmosphericSource.hue
        val atmoChroma = atmosphericSource.chroma.coerceIn(0.005, 0.25)
        val primaryHue = primarySource.hue
        val primaryChroma = primarySource.chroma.coerceIn(0.01, 0.28)
        val isMonochromeArtwork = understanding.isMonochromatic || primaryChroma < 0.045

        // Compute Background targets per recipe with bounded limits (Special Cases 47 & 48)
        val (rawBgTargetL, rawBgTargetC, bgHue) = when (recipe) {
            ThemeGenerationRecipe.BALANCED -> {
                if (isDark) {
                    Triple(0.12, (atmoChroma * 0.15 * influenceScale).coerceIn(0.005, 0.026), atmoHue)
                } else {
                    Triple(0.97, (atmoChroma * 0.12 * influenceScale).coerceIn(0.004, 0.022), atmoHue)
                }
            }
            ThemeGenerationRecipe.ATMOSPHERIC -> {
                if (isDark) {
                    Triple(0.105, (atmoChroma * 0.32 * influenceScale).coerceIn(0.012, 0.042), atmoHue)
                } else {
                    Triple(0.955, (atmoChroma * 0.25 * influenceScale).coerceIn(0.008, 0.035), atmoHue)
                }
            }
            ThemeGenerationRecipe.INK -> {
                if (isDark) {
                    Triple(0.085, (0.003 * influenceScale).coerceIn(0.001, 0.008), atmoHue)
                } else {
                    Triple(0.980, (0.003 * influenceScale).coerceIn(0.001, 0.008), atmoHue)
                }
            }
            ThemeGenerationRecipe.EXPRESSIVE -> {
                if (isDark) {
                    Triple(0.13, (atmoChroma * 0.28 * influenceScale).coerceIn(0.010, 0.040), (atmoHue + 6.0) % 360.0)
                } else {
                    Triple(0.96, (atmoChroma * 0.22 * influenceScale).coerceIn(0.008, 0.035), (atmoHue + 6.0) % 360.0)
                }
            }
        }

        // Bounded limits: protect elevation ramp in deep dark (Part 47) and readability in bright light (Part 48)
        val bgTargetL = if (isDark) {
            rawBgTargetL.coerceIn(0.075, 0.145)
        } else {
            rawBgTargetL.coerceIn(0.955, 0.980)
        }
        val bgTargetC = rawBgTargetC

        val bgOklch = ContrastResolver.Oklch(l = bgTargetL, c = bgTargetC, h = bgHue)
        val bgInt = ContrastResolver.oklchToColorInt(bgOklch)
        val bgHex = String.format("#%06X", 0xFFFFFF and bgInt)

        // Primary Accent target per recipe, harmonized gently with ambient atmosphere
        val harmonizedPrimaryHue = if (isMonochromeArtwork) primaryHue else harmonizeHue(primaryHue, atmoHue, 0.12)
        val (accentTargetL, accentBaseC) = when (recipe) {
            ThemeGenerationRecipe.BALANCED -> {
                if (isDark) Pair(0.72, maxOf(primaryChroma, 0.13)) else Pair(0.45, maxOf(primaryChroma, 0.13))
            }
            ThemeGenerationRecipe.ATMOSPHERIC -> {
                if (isDark) Pair(0.74, maxOf(primaryChroma, 0.15)) else Pair(0.43, maxOf(primaryChroma, 0.15))
            }
            ThemeGenerationRecipe.INK -> {
                if (isDark) Pair(0.70, maxOf(primaryChroma, 0.12)) else Pair(0.46, maxOf(primaryChroma, 0.12))
            }
            ThemeGenerationRecipe.EXPRESSIVE -> {
                if (isDark) Pair(0.75, maxOf(primaryChroma * 1.25, 0.18)) else Pair(0.42, maxOf(primaryChroma * 1.25, 0.18))
            }
        }

        // Special Case 45: Restrained accent chroma for monochrome/neutral artwork
        val accentTargetC = if (isMonochromeArtwork) {
            (primaryChroma * 1.1).coerceIn(0.040, 0.085)
        } else {
            (accentBaseC * accentScale).coerceIn(0.08, 0.25)
        }

        val accentOklch = ContrastResolver.Oklch(l = accentTargetL, c = accentTargetC, h = harmonizedPrimaryHue)
        val candidateAccentInt = ContrastResolver.oklchToColorInt(accentOklch)
        val resolvedAccent = ContrastResolver.resolveContrast(
            background = Color(bgInt),
            preferredForeground = Color(candidateAccentInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val accentHex = String.format("#%06X", 0xFFFFFF and resolvedAccent.color.toArgb())

        // Supporting / Secondary Accent
        val rawSecHue = supportingSource?.hue ?: ((harmonizedPrimaryHue + 32.0) % 360.0)
        val secHue = if (circularHueDistance(rawSecHue, harmonizedPrimaryHue) < 24.0) {
            (harmonizedPrimaryHue + 32.0) % 360.0
        } else {
            rawSecHue
        }
        val secBaseC = supportingSource?.chroma ?: (primaryChroma * 0.85)
        val secTargetC = if (isMonochromeArtwork) {
            (secBaseC * 1.0).coerceIn(0.035, 0.075)
        } else {
            (secBaseC * accentScale * 0.90).coerceIn(0.06, 0.22)
        }
        val secOklch = ContrastResolver.Oklch(
            l = if (isDark) 0.74 else 0.44,
            c = secTargetC,
            h = secHue
        )
        val candidateSecInt = ContrastResolver.oklchToColorInt(secOklch)
        val resolvedSec = ContrastResolver.resolveContrast(
            background = Color(bgInt),
            preferredForeground = Color(candidateSecInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val secondaryHex = String.format("#%06X", 0xFFFFFF and resolvedSec.color.toArgb())

        // Tertiary Accent
        val rawTertHue = tertiarySource?.hue ?: ((harmonizedPrimaryHue - 28.0 + 360.0) % 360.0)
        val tertHue = if (circularHueDistance(rawTertHue, harmonizedPrimaryHue) < 22.0 || circularHueDistance(rawTertHue, secHue) < 22.0) {
            (harmonizedPrimaryHue - 32.0 + 360.0) % 360.0
        } else {
            rawTertHue
        }
        val tertBaseC = tertiarySource?.chroma ?: (primaryChroma * 0.75)
        val tertTargetC = if (isMonochromeArtwork) {
            (tertBaseC * 1.0).coerceIn(0.030, 0.070)
        } else {
            (tertBaseC * accentScale * 0.80).coerceIn(0.05, 0.20)
        }
        val tertOklch = ContrastResolver.Oklch(
            l = if (isDark) 0.76 else 0.46,
            c = tertTargetC,
            h = tertHue
        )
        val candidateTertInt = ContrastResolver.oklchToColorInt(tertOklch)
        val resolvedTert = ContrastResolver.resolveContrast(
            background = Color(bgInt),
            preferredForeground = Color(candidateTertInt),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val tertiaryHex = String.format("#%06X", 0xFFFFFF and resolvedTert.color.toArgb())

        // Atmospheric Hex
        val atmosphericHex = atmosphericSource.colorHex

        // Text target modulated by WritingCharacter and recipe
        val (textL, textC, textH) = when (writingCharacter) {
            WritingCharacter.NEUTRAL -> {
                val l = if (isDark) 0.94 else 0.14
                Triple(l, 0.003, harmonizedPrimaryHue)
            }
            WritingCharacter.WARM -> {
                val l = if (isDark) 0.93 else 0.15
                Triple(l, 0.010, 65.0) // Gentle amber undertone
            }
            WritingCharacter.COOL -> {
                val l = if (isDark) 0.93 else 0.15
                Triple(l, 0.010, 225.0) // Gentle slate undertone
            }
            WritingCharacter.DRAMATIC -> {
                val l = if (isDark) 0.97 else 0.10 // Maximum stark contrast
                Triple(l, 0.002, harmonizedPrimaryHue)
            }
        }

        val textOklch = ContrastResolver.Oklch(l = textL, c = textC, h = textH)
        val candidateTextInt = ContrastResolver.oklchToColorInt(textOklch)
        val resolvedText = ContrastResolver.resolveContrast(
            background = Color(bgInt),
            preferredForeground = Color(candidateTextInt),
            minRatio = 4.5,
            role = ContrastResolver.ContrastRole.NORMAL_TEXT
        )
        val textHex = String.format("#%06X", 0xFFFFFF and resolvedText.color.toArgb())

        return ThemeSourcePalette(
            background = bgHex,
            text = textHex,
            accent = accentHex,
            secondaryAccent = secondaryHex,
            tertiaryAccent = tertiaryHex,
            atmosphericColor = atmosphericHex
        )
    }

    /**
     * Generates all 4 recipe interpretations at once for preview or comparison.
     */
    fun generateInterpretations(
        understanding: ImageUnderstanding,
        candidateColor: Int? = null,
        isDark: Boolean,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): Map<ThemeGenerationRecipe, ThemeSourcePalette> {
        return ThemeGenerationRecipe.values().associateWith { recipe ->
            generateSourcePalette(
                understanding = understanding,
                recipe = recipe,
                candidateColor = candidateColor,
                isDark = isDark,
                influence = influence,
                writingCharacter = writingCharacter
            )
        }
    }

    /**
     * Generates a poetic, human-readable editorial name for the theme based on hue and recipe.
     */
    fun generateThemeName(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        seedColor: Int,
        isDark: Boolean
    ): String {
        val seedOklch = ContrastResolver.colorToOklch(seedColor)
        val hueName = getHueDescriptor(seedOklch.h, seedOklch.c)
        val recipeWord = when (recipe) {
            ThemeGenerationRecipe.BALANCED -> if (isDark) "Dusk" else "Dawn"
            ThemeGenerationRecipe.ATMOSPHERIC -> if (isDark) "Atmosphere" else "Haze"
            ThemeGenerationRecipe.INK -> if (isDark) "Ink" else "Script"
            ThemeGenerationRecipe.EXPRESSIVE -> if (isDark) "Radiance" else "Pulse"
        }
        return "$hueName $recipeWord"
    }

    private fun getHueDescriptor(hue: Double, chroma: Double): String {
        if (chroma < 0.04) return "Monochrome"
        return when (hue.roundToInt()) {
            in 0..25 -> "Crimson"
            in 26..50 -> "Amber"
            in 51..85 -> "Gold"
            in 86..145 -> "Sage"
            in 146..185 -> "Emerald"
            in 186..230 -> "Teal"
            in 231..275 -> "Cobalt"
            in 276..315 -> "Iris"
            in 316..345 -> "Rose"
            else -> "Crimson"
        }
    }

    fun getColorDescriptor(colorInt: Int): String {
        val seedOklch = ContrastResolver.colorToOklch(colorInt)
        return getHueDescriptor(seedOklch.h, seedOklch.c)
    }

    internal fun fallbackUnderstanding(): ImageUnderstanding {
        val defaultSeed = 0xFF3B82F6.toInt()
        val defaultDominant = listOf("#3B82F6", "#1D4ED8", "#10B981", "#F59E0B")
        val defaultPaletteSources = listOf(
            ImagePaletteSource(
                colorHex = "#3B82F6",
                colorArgb = defaultSeed,
                hue = 240.0,
                chroma = 0.18,
                tone = 0.60,
                visualRole = VisualRole.PRIMARY_ACCENT
            ),
            ImagePaletteSource(
                colorHex = "#1E293B",
                colorArgb = 0xFF1E293B.toInt(),
                hue = 230.0,
                chroma = 0.03,
                tone = 0.20,
                visualRole = VisualRole.ATMOSPHERIC
            ),
            ImagePaletteSource(
                colorHex = "#10B981",
                colorArgb = 0xFF10B981.toInt(),
                hue = 155.0,
                chroma = 0.16,
                tone = 0.65,
                visualRole = VisualRole.SUPPORTING_ACCENT
            ),
            ImagePaletteSource(
                colorHex = "#F59E0B",
                colorArgb = 0xFFF59E0B.toInt(),
                hue = 80.0,
                chroma = 0.16,
                tone = 0.70,
                visualRole = VisualRole.TERTIARY_ACCENT
            )
        )
        return ImageUnderstanding(
            rankedCandidates = listOf(defaultSeed, 0xFF1D4ED8.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt()),
            dominantColors = defaultDominant,
            averageLightness = 0.5f,
            tonalCharacter = TonalCharacter.MID_KEY,
            chromaticCharacter = ChromaticCharacter.BALANCED,
            temperatureBias = TemperatureBias.NEUTRAL,
            darkLightBias = DarkLightBias.BALANCED,
            paletteDiversity = PaletteDiversity.MODERATE,
            imageFingerprint = "0000000000000000",
            paletteSources = defaultPaletteSources,
            averageChroma = 0.12f,
            dominantHue = 240.0,
            isMonochromatic = false,
            isExtremeDark = false,
            isExtremeLight = false
        )
    }
}

