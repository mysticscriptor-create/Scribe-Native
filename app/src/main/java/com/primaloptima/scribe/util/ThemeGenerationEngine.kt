package com.primaloptima.scribe.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.primaloptima.scribe.ui.theme.ContrastResolver
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ChromaticCharacter
import com.primaloptima.scribe.util.model.DarkLightBias
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ImagePaletteSource
import com.primaloptima.scribe.util.model.ImageUnderstanding
import com.primaloptima.scribe.util.model.PaletteDiversity
import com.primaloptima.scribe.util.model.TemperatureBias
import com.primaloptima.scribe.util.model.ThemeCanvasMode
import com.primaloptima.scribe.util.model.ThemeGenerationMetadata
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import com.primaloptima.scribe.util.model.ThemeSchema
import com.primaloptima.scribe.util.model.ThemeSourcePalette
import com.primaloptima.scribe.util.model.TonalCharacter
import com.primaloptima.scribe.util.model.VisualRole
import com.primaloptima.scribe.util.model.VisualThemePalette
import com.primaloptima.scribe.util.model.VisualPaletteReport
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
     * Phase 20.1 — Analyzes a list of hex color candidate strings directly into an [ImageUnderstanding].
     * Useful for programmatic palette testing and palette-based theme derivation.
     */
    fun analyzeArtworkPalette(hexCandidates: List<String>): ImageUnderstanding {
        val argbList = hexCandidates.map { parseHexToArgb(it) }
        val width = maxOf(1, argbList.size)
        return analyzePixels(argbList.toIntArray(), width, 1)
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

        // Check for fully transparent images
        val hasOpaquePixels = pixels.any { ((it ushr 24) and 0xFF) > 15 }
        if (!hasOpaquePixels) {
            return fallbackUnderstanding()
        }

        // 1. Ranked candidates via MCU QuantizerCelebi & Score (computed once)
        val quantizerResult = QuantizerCelebi.quantize(pixels, MAX_QUANTIZER_COLORS)
        val scored = Score.score(quantizerResult)
        val extracted = scored.ifEmpty { quantizerResult.keys.toList() }
        val accessibleCandidates = extracted.filter {
            val oklch = ContrastResolver.colorToOklch(it)
            oklch.l in 0.04..0.985
        }
        val rankedCandidates = if (accessibleCandidates.isNotEmpty()) {
            accessibleCandidates
        } else if (extracted.isNotEmpty()) {
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
    /**
     * Phase 20.1 — Generates the authoritative [VisualThemePalette] representing the
     * Visual Palette Distribution Layer.
     *
     * Maps extracted multi-color image sources to 10 visual interface roles:
     * - Visual Canvas (L0 app background)
     * - Visual Editor Surface (L1 writing/reading surface)
     * - Visual Chrome (L2 top app bar, bottom bar, primary drawers)
     * - Visual Secondary Chrome (tool trays, sunken split gutters)
     * - Visual Elevated Surface (L3 floating cards, workbench panes)
     * - Visual Primary Accent (FAB, primary triggers)
     * - Visual Secondary Accent (chips, secondary actions)
     * - Visual Tertiary Accent (monologue, auxiliary indicators)
     * - Visual Highlight (search matches, literary emphasis)
     * - Visual Neutral (structural boundaries, dividers)
     */
    fun generateVisualThemePalette(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: Int? = null,
        isDark: Boolean = true,
        canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): VisualThemePalette {
        // 1. Identify multi-color image palette sources
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

        val atmosphericSource = understanding.paletteSources.firstOrNull {
            it.visualRole == VisualRole.ATMOSPHERIC && it.colorArgb != primarySource.colorArgb
        } ?: understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.ATMOSPHERIC }
          ?: understanding.paletteSources.firstOrNull { it.colorArgb != primarySource.colorArgb }
          ?: primarySource

        val supportingSource = understanding.paletteSources.firstOrNull {
            it.colorArgb != primarySource.colorArgb && it.colorArgb != atmosphericSource.colorArgb &&
            (it.visualRole == VisualRole.SUPPORTING_ACCENT || circularHueDistance(it.hue, primarySource.hue) >= 24.0)
        } ?: understanding.paletteSources.firstOrNull {
            it.colorArgb != primarySource.colorArgb && it.colorArgb != atmosphericSource.colorArgb
        } ?: run {
            val secHue = (primarySource.hue + 36.0) % 360.0
            val oklch = ContrastResolver.Oklch(l = primarySource.tone, c = (primarySource.chroma * 0.85).coerceAtLeast(0.05), h = secHue)
            val cInt = ContrastResolver.oklchToColorInt(oklch)
            ImagePaletteSource(
                colorHex = String.format("#%06X", 0xFFFFFF and cInt),
                colorArgb = cInt,
                hue = secHue,
                chroma = oklch.c,
                tone = oklch.l,
                visualRole = VisualRole.SUPPORTING_ACCENT
            )
        }

        val tertiarySource = understanding.paletteSources.firstOrNull {
            it.colorArgb != primarySource.colorArgb && it.colorArgb != atmosphericSource.colorArgb &&
            it.colorArgb != supportingSource.colorArgb
        } ?: run {
            val tertHue = (primarySource.hue - 32.0 + 360.0) % 360.0
            val oklch = ContrastResolver.Oklch(l = primarySource.tone, c = (primarySource.chroma * 0.75).coerceAtLeast(0.04), h = tertHue)
            val cInt = ContrastResolver.oklchToColorInt(oklch)
            ImagePaletteSource(
                colorHex = String.format("#%06X", 0xFFFFFF and cInt),
                colorArgb = cInt,
                hue = tertHue,
                chroma = oklch.c,
                tone = oklch.l,
                visualRole = VisualRole.TERTIARY_ACCENT
            )
        }

        val highlightSource = understanding.paletteSources.firstOrNull {
            it.chroma >= 0.08 && ((it.hue in 35.0..85.0) || it.chroma > primarySource.chroma)
        } ?: understanding.paletteSources.maxByOrNull { it.chroma }
          ?: run {
              val hHue = (primarySource.hue + 60.0) % 360.0
              val oklch = ContrastResolver.Oklch(l = if (isDark) 0.78 else 0.55, c = 0.16, h = hHue)
              val cInt = ContrastResolver.oklchToColorInt(oklch)
              ImagePaletteSource(
                  colorHex = String.format("#%06X", 0xFFFFFF and cInt),
                  colorArgb = cInt,
                  hue = hHue,
                  chroma = 0.16,
                  tone = oklch.l,
                  visualRole = VisualRole.PRIMARY_ACCENT
              )
          }

        val isMonochromeArtwork = understanding.isMonochromatic || primarySource.chroma < 0.045

        // Influence scaling factors: subtle is restrained, balanced is distinct multi-color, strong is bold
        val influenceScale = when (influence) {
            ImageInfluence.SUBTLE -> 0.35
            ImageInfluence.BALANCED -> 1.00
            ImageInfluence.STRONG -> 1.70
        }
        val accentScale = when (influence) {
            ImageInfluence.SUBTLE -> 0.75
            ImageInfluence.BALANCED -> 1.00
            ImageInfluence.STRONG -> 1.35
        }

        val atmoHue = atmosphericSource.hue
        val atmoChroma = atmosphericSource.chroma.coerceIn(0.005, 0.25)
        val primaryHue = primarySource.hue
        val primaryChroma = primarySource.chroma.coerceIn(0.01, 0.28)
        val supportingHue = supportingSource.hue
        val supportingChroma = supportingSource.chroma.coerceIn(0.01, 0.25)
        val tertiaryHue = tertiarySource.hue
        val tertiaryChroma = tertiarySource.chroma.coerceIn(0.01, 0.22)
        val highlightHue = highlightSource.hue

        val isNative = canvasMode == ThemeCanvasMode.IMAGE_NATIVE
        val effectiveIsDark = when (canvasMode) {
            ThemeCanvasMode.DARK -> true
            ThemeCanvasMode.LIGHT -> false
            ThemeCanvasMode.IMAGE_NATIVE -> atmosphericSource.tone < 0.52
        }

        // 1. VISUAL CANVAS (L0) - App Background
        val canvasOklch = when (canvasMode) {
            ThemeCanvasMode.IMAGE_NATIVE -> {
                val nativeTone = atmosphericSource.tone
                when (recipe) {
                    ThemeGenerationRecipe.BALANCED -> {
                        ContrastResolver.Oklch(
                            l = nativeTone.coerceIn(0.20, 0.80),
                            c = (atmoChroma * 0.85 * influenceScale).coerceIn(0.035, 0.20),
                            h = atmoHue
                        )
                    }
                    ThemeGenerationRecipe.ATMOSPHERIC -> {
                        ContrastResolver.Oklch(
                            l = nativeTone.coerceIn(0.16, 0.84),
                            c = (atmoChroma * 1.15 * influenceScale).coerceIn(0.050, 0.26),
                            h = atmoHue
                        )
                    }
                    ThemeGenerationRecipe.INK -> {
                        if (nativeTone < 0.50) {
                            ContrastResolver.Oklch(
                                l = (nativeTone * 0.75).coerceIn(0.12, 0.35),
                                c = (atmoChroma * 0.25 * influenceScale).coerceIn(0.005, 0.04),
                                h = atmoHue
                            )
                        } else {
                            ContrastResolver.Oklch(
                                l = (0.85 + nativeTone * 0.10).coerceIn(0.85, 0.95),
                                c = (atmoChroma * 0.25 * influenceScale).coerceIn(0.005, 0.04),
                                h = atmoHue
                            )
                        }
                    }
                    ThemeGenerationRecipe.EXPRESSIVE -> {
                        ContrastResolver.Oklch(
                            l = nativeTone.coerceIn(0.22, 0.78),
                            c = (atmoChroma * 1.05 * influenceScale).coerceIn(0.055, 0.24),
                            h = atmoHue
                        )
                    }
                }
            }
            ThemeCanvasMode.DARK -> {
                when (recipe) {
                    ThemeGenerationRecipe.BALANCED -> {
                        ContrastResolver.Oklch(0.115, (atmoChroma * 0.25 * influenceScale).coerceIn(0.012, 0.045), atmoHue)
                    }
                    ThemeGenerationRecipe.ATMOSPHERIC -> {
                        ContrastResolver.Oklch(0.095, (atmoChroma * 0.60 * influenceScale).coerceIn(0.030, 0.095), atmoHue)
                    }
                    ThemeGenerationRecipe.INK -> {
                        ContrastResolver.Oklch(0.078, (0.005 * influenceScale).coerceIn(0.002, 0.010), atmoHue)
                    }
                    ThemeGenerationRecipe.EXPRESSIVE -> {
                        ContrastResolver.Oklch(0.125, (atmoChroma * 0.45 * influenceScale).coerceIn(0.025, 0.075), atmoHue)
                    }
                }
            }
            ThemeCanvasMode.LIGHT -> {
                when (recipe) {
                    ThemeGenerationRecipe.BALANCED -> {
                        ContrastResolver.Oklch(0.968, (atmoChroma * 0.22 * influenceScale).coerceIn(0.010, 0.040), atmoHue)
                    }
                    ThemeGenerationRecipe.ATMOSPHERIC -> {
                        ContrastResolver.Oklch(0.938, (atmoChroma * 0.50 * influenceScale).coerceIn(0.025, 0.085), atmoHue)
                    }
                    ThemeGenerationRecipe.INK -> {
                        ContrastResolver.Oklch(0.986, (0.004 * influenceScale).coerceIn(0.002, 0.008), atmoHue)
                    }
                    ThemeGenerationRecipe.EXPRESSIVE -> {
                        ContrastResolver.Oklch(0.952, (atmoChroma * 0.38 * influenceScale).coerceIn(0.020, 0.068), atmoHue)
                    }
                }
            }
        }
        val canvasInt = ContrastResolver.oklchToColorInt(canvasOklch)
        val canvasHex = String.format("#%06X", 0xFFFFFF and canvasInt)

        // 2. VISUAL EDITOR SURFACE (L1) - Reading and Prose writing surface
        val (baseEditorL, baseEditorC, baseEditorH) = if (isNative) {
            val step = if (effectiveIsDark) 0.05 else -0.05
            val nativeL = (canvasOklch.l + step).coerceIn(0.15, 0.90)
            val nativeC = (canvasOklch.c * 0.60).coerceAtLeast(0.015)
            Triple(nativeL, nativeC, canvasOklch.h)
        } else if (effectiveIsDark) {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> Triple(0.098, (atmoChroma * 0.12 * influenceScale).coerceIn(0.006, 0.022), atmoHue)
                ThemeGenerationRecipe.ATMOSPHERIC -> Triple(0.086, (atmoChroma * 0.35 * influenceScale).coerceIn(0.018, 0.050), atmoHue)
                ThemeGenerationRecipe.INK -> Triple(0.062, (0.002 * influenceScale).coerceAtMost(0.003), atmoHue)
                ThemeGenerationRecipe.EXPRESSIVE -> Triple(0.106, (atmoChroma * 0.18 * influenceScale).coerceIn(0.010, 0.035), atmoHue)
            }
        } else {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> Triple(0.984, (atmoChroma * 0.10 * influenceScale).coerceIn(0.005, 0.018), atmoHue)
                ThemeGenerationRecipe.ATMOSPHERIC -> Triple(0.958, (atmoChroma * 0.28 * influenceScale).coerceIn(0.015, 0.045), atmoHue)
                ThemeGenerationRecipe.INK -> Triple(0.992, (0.002 * influenceScale).coerceAtMost(0.003), atmoHue)
                ThemeGenerationRecipe.EXPRESSIVE -> Triple(0.976, (atmoChroma * 0.14 * influenceScale).coerceIn(0.008, 0.028), atmoHue)
            }
        }

        // Modulate Editor Surface noticeably by WritingCharacter
        val (finalEditorL, finalEditorC, finalEditorH) = when (writingCharacter) {
            WritingCharacter.NEUTRAL -> Triple(baseEditorL, baseEditorC, baseEditorH)
            WritingCharacter.WARM -> {
                val warmH = harmonizeHue(baseEditorH, 65.0, 0.75)
                val warmC = baseEditorC + (if (effectiveIsDark) 0.014 else 0.020)
                val warmL = if (effectiveIsDark) (baseEditorL + 0.007).coerceAtMost(0.12) else 0.975
                Triple(warmL, warmC, warmH)
            }
            WritingCharacter.COOL -> {
                val coolH = harmonizeHue(baseEditorH, 225.0, 0.75)
                val coolC = baseEditorC + (if (effectiveIsDark) 0.014 else 0.020)
                val coolL = if (effectiveIsDark) (baseEditorL - 0.004).coerceAtLeast(0.08) else 0.978
                Triple(coolL, coolC, coolH)
            }
            WritingCharacter.DRAMATIC -> {
                val dramL = if (effectiveIsDark) 0.055 else 0.996
                Triple(dramL, (baseEditorC * 0.3).coerceAtLeast(0.001), baseEditorH)
            }
        }
        val editorOklch = ContrastResolver.Oklch(finalEditorL, finalEditorC, finalEditorH)
        val editorInt = ContrastResolver.oklchToColorInt(editorOklch)
        val editorHex = String.format("#%06X", 0xFFFFFF and editorInt)

        // 3. VISUAL CHROME (L2) - Top App Bar, Bottom Bar, Primary Drawers
        val chromeOklch = if (isNative) {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    ContrastResolver.Oklch(
                        l = (supportingSource.tone).coerceIn(0.18, 0.82),
                        c = (supportingChroma * 0.85 * influenceScale).coerceIn(0.04, 0.22),
                        h = supportingHue
                    )
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    ContrastResolver.Oklch(
                        l = (atmosphericSource.tone * 0.90).coerceIn(0.15, 0.85),
                        c = (atmoChroma * 1.05 * influenceScale).coerceIn(0.05, 0.24),
                        h = atmoHue
                    )
                }
                ThemeGenerationRecipe.INK -> {
                    ContrastResolver.Oklch(
                        l = if (effectiveIsDark) 0.135 else 0.935,
                        c = (0.012 * influenceScale).coerceIn(0.003, 0.022),
                        h = atmoHue
                    )
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    ContrastResolver.Oklch(
                        l = (supportingSource.tone).coerceIn(0.18, 0.82),
                        c = (supportingChroma * 1.05 * influenceScale).coerceIn(0.06, 0.26),
                        h = supportingHue
                    )
                }
            }
        } else if (effectiveIsDark) {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    // Balanced chrome noticeably uses the secondary image color family
                    ContrastResolver.Oklch(0.155, (supportingChroma * 0.38 * influenceScale).coerceIn(0.025, 0.075), supportingHue)
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    // Atmospheric chrome immerses deeply in the atmospheric hue
                    ContrastResolver.Oklch(0.135, (atmoChroma * 0.55 * influenceScale).coerceIn(0.032, 0.095), atmoHue)
                }
                ThemeGenerationRecipe.INK -> {
                    // Ink chrome is deep, restrained slate/charcoal
                    ContrastResolver.Oklch(0.108, (0.010 * influenceScale).coerceIn(0.003, 0.016), primaryHue)
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    // Expressive chrome boldly uses the vibrant secondary color family
                    ContrastResolver.Oklch(0.165, (supportingChroma * 0.60 * influenceScale).coerceIn(0.045, 0.130), supportingHue)
                }
            }
        } else {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    ContrastResolver.Oklch(0.932, (supportingChroma * 0.30 * influenceScale).coerceIn(0.020, 0.065), supportingHue)
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    ContrastResolver.Oklch(0.915, (atmoChroma * 0.45 * influenceScale).coerceIn(0.025, 0.085), atmoHue)
                }
                ThemeGenerationRecipe.INK -> {
                    ContrastResolver.Oklch(0.950, (0.008 * influenceScale).coerceIn(0.002, 0.015), primaryHue)
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    ContrastResolver.Oklch(0.910, (supportingChroma * 0.50 * influenceScale).coerceIn(0.038, 0.115), supportingHue)
                }
            }
        }
        val chromeInt = ContrastResolver.oklchToColorInt(chromeOklch)
        val chromeHex = String.format("#%06X", 0xFFFFFF and chromeInt)

        // 4. VISUAL SECONDARY CHROME - Sunken Gutters, Split Rails, Trays
        val secChromeL = (canvasOklch.l + editorOklch.l) / 2.0
        val secChromeC = (canvasOklch.c + editorOklch.c) / 2.0
        val secChromeOklch = ContrastResolver.Oklch(secChromeL, secChromeC, canvasOklch.h)
        val secChromeInt = ContrastResolver.oklchToColorInt(secChromeOklch)
        val secChromeHex = String.format("#%06X", 0xFFFFFF and secChromeInt)

        // 5. VISUAL ELEVATED SURFACE (L3) - Cards, Floating Workbenches
        val cardOklch = if (isNative) {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    ContrastResolver.Oklch(
                        l = (tertiarySource.tone).coerceIn(0.20, 0.85),
                        c = (tertiaryChroma * 0.75 * influenceScale).coerceIn(0.035, 0.18),
                        h = tertiaryHue
                    )
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    val step = if (effectiveIsDark) 0.07 else -0.07
                    ContrastResolver.Oklch(
                        l = (canvasOklch.l + step).coerceIn(0.12, 0.90),
                        c = (atmoChroma * 0.90 * influenceScale).coerceIn(0.04, 0.20),
                        h = atmoHue
                    )
                }
                ThemeGenerationRecipe.INK -> {
                    ContrastResolver.Oklch(if (effectiveIsDark) 0.125 else 0.990, 0.005, atmoHue)
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    ContrastResolver.Oklch(
                        l = (tertiarySource.tone).coerceIn(0.20, 0.85),
                        c = (tertiaryChroma * 0.95 * influenceScale).coerceIn(0.05, 0.22),
                        h = tertiaryHue
                    )
                }
            }
        } else if (effectiveIsDark) {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    // Balanced cards pull distinctly from supporting/tertiary color family
                    ContrastResolver.Oklch(0.185, (tertiaryChroma * 0.30 * influenceScale).coerceIn(0.018, 0.055), tertiaryHue)
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    ContrastResolver.Oklch(0.165, (atmoChroma * 0.45 * influenceScale).coerceIn(0.025, 0.080), atmoHue)
                }
                ThemeGenerationRecipe.INK -> {
                    ContrastResolver.Oklch(0.130, (0.006 * influenceScale).coerceIn(0.002, 0.012), atmoHue)
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    // Expressive cards boldly pull from tertiary image color family
                    ContrastResolver.Oklch(0.190, (tertiaryChroma * 0.52 * influenceScale).coerceIn(0.035, 0.110), tertiaryHue)
                }
            }
        } else {
            when (recipe) {
                ThemeGenerationRecipe.BALANCED -> {
                    ContrastResolver.Oklch(0.985, (tertiaryChroma * 0.24 * influenceScale).coerceIn(0.014, 0.048), tertiaryHue)
                }
                ThemeGenerationRecipe.ATMOSPHERIC -> {
                    ContrastResolver.Oklch(0.965, (atmoChroma * 0.38 * influenceScale).coerceIn(0.020, 0.070), atmoHue)
                }
                ThemeGenerationRecipe.INK -> {
                    ContrastResolver.Oklch(0.995, (0.004 * influenceScale).coerceIn(0.001, 0.008), atmoHue)
                }
                ThemeGenerationRecipe.EXPRESSIVE -> {
                    ContrastResolver.Oklch(0.975, (tertiaryChroma * 0.42 * influenceScale).coerceIn(0.028, 0.095), tertiaryHue)
                }
            }
        }
        val cardInt = ContrastResolver.oklchToColorInt(cardOklch)
        val cardHex = String.format("#%06X", 0xFFFFFF and cardInt)

        // 6. VISUAL PRIMARY ACCENT - Primary action / FAB / active triggers
        val (accentL, accentC) = when (recipe) {
            ThemeGenerationRecipe.BALANCED -> Pair(if (effectiveIsDark) 0.72 else 0.45, (primaryChroma * accentScale).coerceIn(0.09, 0.23))
            ThemeGenerationRecipe.ATMOSPHERIC -> Pair(if (effectiveIsDark) 0.74 else 0.43, (primaryChroma * 1.15 * accentScale).coerceIn(0.10, 0.25))
            ThemeGenerationRecipe.INK -> Pair(if (effectiveIsDark) 0.70 else 0.46, (primaryChroma * 0.90 * accentScale).coerceIn(0.07, 0.20))
            ThemeGenerationRecipe.EXPRESSIVE -> Pair(if (effectiveIsDark) 0.76 else 0.42, (primaryChroma * 1.35 * accentScale).coerceIn(0.14, 0.28))
        }
        val resolvedPrimaryAccent = ContrastResolver.resolveContrast(
            background = Color(canvasInt),
            preferredForeground = Color(ContrastResolver.oklchToColorInt(ContrastResolver.Oklch(accentL, accentC, primaryHue))),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val primaryAccentHex = String.format("#%06X", 0xFFFFFF and resolvedPrimaryAccent.color.toArgb())

        // 7. VISUAL SECONDARY ACCENT - Secondary action / chips / tabs
        val secAccentHue = if (influence == ImageInfluence.SUBTLE || isMonochromeArtwork) {
            primaryHue
        } else {
            supportingHue
        }
        val secAccentC = if (influence == ImageInfluence.SUBTLE) {
            (accentC * 0.7).coerceIn(0.04, 0.12)
        } else {
            (supportingChroma * accentScale * 0.95).coerceIn(0.06, 0.22)
        }
        val resolvedSecondaryAccent = ContrastResolver.resolveContrast(
            background = Color(canvasInt),
            preferredForeground = Color(ContrastResolver.oklchToColorInt(ContrastResolver.Oklch(if (effectiveIsDark) 0.74 else 0.44, secAccentC, secAccentHue))),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val secondaryAccentHex = String.format("#%06X", 0xFFFFFF and resolvedSecondaryAccent.color.toArgb())

        // 8. VISUAL TERTIARY ACCENT - Auxiliary indicators / monologue
        val tertAccentHue = if (influence == ImageInfluence.SUBTLE || isMonochromeArtwork) {
            (primaryHue - 28.0 + 360.0) % 360.0
        } else {
            tertiaryHue
        }
        val tertAccentC = if (influence == ImageInfluence.SUBTLE) {
            (accentC * 0.5).coerceIn(0.03, 0.10)
        } else {
            (tertiaryChroma * accentScale * 0.85).coerceIn(0.05, 0.20)
        }
        val resolvedTertiaryAccent = ContrastResolver.resolveContrast(
            background = Color(canvasInt),
            preferredForeground = Color(ContrastResolver.oklchToColorInt(ContrastResolver.Oklch(if (effectiveIsDark) 0.76 else 0.46, tertAccentC, tertAccentHue))),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val tertiaryAccentHex = String.format("#%06X", 0xFFFFFF and resolvedTertiaryAccent.color.toArgb())

        // 9. VISUAL HIGHLIGHT - Search matches, literary annotations, emphasis
        val highlightC = when (recipe) {
            ThemeGenerationRecipe.INK -> 0.09
            ThemeGenerationRecipe.BALANCED -> 0.14
            ThemeGenerationRecipe.ATMOSPHERIC -> 0.16
            ThemeGenerationRecipe.EXPRESSIVE -> 0.20
        } * accentScale
        val resolvedHighlight = ContrastResolver.resolveContrast(
            background = Color(editorInt),
            preferredForeground = Color(ContrastResolver.oklchToColorInt(ContrastResolver.Oklch(if (effectiveIsDark) 0.80 else 0.52, highlightC.coerceIn(0.08, 0.22), highlightHue))),
            minRatio = 3.0,
            role = ContrastResolver.ContrastRole.UI_CONTROL
        )
        val highlightHex = String.format("#%06X", 0xFFFFFF and resolvedHighlight.color.toArgb())

        // 10. VISUAL NEUTRAL - Structural borders, dividers
        val neutralL = if (effectiveIsDark) (canvasOklch.l + 0.12).coerceAtMost(0.40) else (canvasOklch.l - 0.12).coerceAtLeast(0.80)
        val neutralC = (canvasOklch.c * 0.60).coerceIn(0.003, 0.015)
        val neutralOklch = ContrastResolver.Oklch(neutralL, neutralC, canvasOklch.h)
        val neutralInt = ContrastResolver.oklchToColorInt(neutralOklch)
        val neutralHex = String.format("#%06X", 0xFFFFFF and neutralInt)

        return VisualThemePalette(
            visualCanvas = canvasHex,
            visualEditorSurface = editorHex,
            visualElevatedSurface = cardHex,
            visualChrome = chromeHex,
            visualSecondaryChrome = secChromeHex,
            visualPrimaryAccent = primaryAccentHex,
            visualSecondaryAccent = secondaryAccentHex,
            visualTertiaryAccent = tertiaryAccentHex,
            visualHighlight = highlightHex,
            visualNeutral = neutralHex
        )
    }

    /**
     * Phase 20.1 — Generates a developer-facing visual palette distribution report.
     * Shows source colors, assigned visual roles, and token mapping.
     */
    fun generateVisualPaletteReport(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: Int? = null,
        isDark: Boolean = true,
        canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): VisualPaletteReport {
        val visualPalette = generateVisualThemePalette(
            understanding = understanding,
            recipe = recipe,
            candidateColor = candidateColor,
            isDark = isDark,
            canvasMode = canvasMode,
            influence = influence,
            writingCharacter = writingCharacter
        )

        val tokenMapping = linkedMapOf(
            "surfaces.background" to "VISUAL CANVAS (${visualPalette.visualCanvas})",
            "surfaces.surfaceLowest" to "VISUAL EDITOR SURFACE (${visualPalette.visualEditorSurface})",
            "surfaces.surface" to "VISUAL CHROME (${visualPalette.visualChrome})",
            "surfaces.surfaceRaised" to "VISUAL ELEVATED SURFACE (${visualPalette.visualElevatedSurface})",
            "interaction.primary" to "VISUAL PRIMARY ACCENT (${visualPalette.visualPrimaryAccent})",
            "interaction.secondary" to "VISUAL SECONDARY ACCENT (${visualPalette.visualSecondaryAccent})",
            "interaction.tertiary" to "VISUAL TERTIARY ACCENT (${visualPalette.visualTertiaryAccent})",
            "writing.highlight" to "VISUAL HIGHLIGHT (${visualPalette.visualHighlight})",
            "borders.normal" to "VISUAL NEUTRAL (${visualPalette.visualNeutral})",
            "semantic.status" to "PROTECTED (Semantic Statuses)"
        )

        return VisualPaletteReport(
            recipe = recipe,
            isDark = isDark,
            influence = influence,
            writingCharacter = writingCharacter,
            sourceColors = understanding.paletteSources,
            visualPalette = visualPalette,
            tokenDistribution = tokenMapping
        )
    }

    fun generateVisualPaletteReport(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: String,
        isDark: Boolean = true,
        canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): VisualPaletteReport = generateVisualPaletteReport(
        understanding = understanding,
        recipe = recipe,
        candidateColor = parseHexToArgb(candidateColor),
        isDark = isDark,
        canvasMode = canvasMode,
        influence = influence,
        writingCharacter = writingCharacter
    )

    fun generateSourcePalette(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: String,
        isDark: Boolean = true,
        canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): ThemeSourcePalette = generateSourcePalette(
        understanding = understanding,
        recipe = recipe,
        candidateColor = parseHexToArgb(candidateColor),
        isDark = isDark,
        canvasMode = canvasMode,
        influence = influence,
        writingCharacter = writingCharacter
    )

    fun generateSourcePalette(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe,
        candidateColor: Int? = null,
        isDark: Boolean = true,
        canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL
    ): ThemeSourcePalette {
        val visualPalette = generateVisualThemePalette(
            understanding = understanding,
            recipe = recipe,
            candidateColor = candidateColor,
            isDark = isDark,
            canvasMode = canvasMode,
            influence = influence,
            writingCharacter = writingCharacter
        )

        val atmosphericSource = understanding.paletteSources.firstOrNull { it.visualRole == VisualRole.ATMOSPHERIC }
            ?: understanding.paletteSources.firstOrNull()

        val editorInt = parseHexToArgb(visualPalette.visualEditorSurface)
        val primaryAccentInt = parseHexToArgb(visualPalette.visualPrimaryAccent)
        val primaryAccentOklch = ContrastResolver.colorToOklch(primaryAccentInt)

        val editorOklch = ContrastResolver.colorToOklch(editorInt)
        val effectiveIsDark = editorOklch.l < 0.52

        // Text target modulated by WritingCharacter and recipe
        val (textL, textC, textH) = when (writingCharacter) {
            WritingCharacter.NEUTRAL -> {
                val l = if (effectiveIsDark) 0.94 else 0.14
                Triple(l, 0.003, primaryAccentOklch.h)
            }
            WritingCharacter.WARM -> {
                val l = if (effectiveIsDark) 0.93 else 0.15
                Triple(l, 0.010, 65.0) // Gentle amber undertone
            }
            WritingCharacter.COOL -> {
                val l = if (effectiveIsDark) 0.93 else 0.15
                Triple(l, 0.010, 225.0) // Gentle slate undertone
            }
            WritingCharacter.DRAMATIC -> {
                val l = if (effectiveIsDark) 0.97 else 0.10 // Maximum stark contrast
                Triple(l, 0.002, primaryAccentOklch.h)
            }
        }

        val textOklch = ContrastResolver.Oklch(l = textL, c = textC, h = textH)
        val candidateTextInt = ContrastResolver.oklchToColorInt(textOklch)
        val resolvedText = ContrastResolver.resolveContrast(
            background = Color(editorInt),
            preferredForeground = Color(candidateTextInt),
            minRatio = 4.5,
            role = ContrastResolver.ContrastRole.NORMAL_TEXT
        )
        val textHex = String.format("#%06X", 0xFFFFFF and resolvedText.color.toArgb())

        return ThemeSourcePalette(
            background = visualPalette.visualCanvas,
            text = textHex,
            accent = visualPalette.visualPrimaryAccent,
            secondaryAccent = visualPalette.visualSecondaryAccent,
            tertiaryAccent = visualPalette.visualTertiaryAccent,
            atmosphericColor = atmosphericSource?.colorHex ?: visualPalette.visualCanvas,
            visualPalette = visualPalette
        )
    }

    fun parseHexToArgb(hex: String): Int {
        val clean = hex.removePrefix("#").trim()
        val fullHex = if (clean.length == 6) "FF$clean" else clean
        return fullHex.toLong(16).toInt()
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

    /**
     * Complete theme generation pipeline with provenance tracking:
     * ImageUnderstanding + Recipe + Options -> ThemeSourcePalette -> ThemeDefaults -> AppTheme with ThemeGenerationMetadata
     */
    fun createGeneratedTheme(
        name: String? = null,
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe = ThemeGenerationRecipe.BALANCED,
        candidateColor: Int? = null,
        isDark: Boolean,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
        relationshipMode: ThemeRelationshipMode = ThemeRelationshipMode.THEME_IMAGE,
        imageUri: String? = null
    ): AppTheme {
        val seedColor = candidateColor ?: understanding.rankedCandidates.firstOrNull() ?: 0xFF3B82F6.toInt()
        val seedHex = String.format("#%06X", 0xFFFFFF and seedColor)
        val sourcePalette = generateSourcePalette(
            understanding = understanding,
            recipe = recipe,
            candidateColor = seedColor,
            isDark = isDark,
            influence = influence,
            writingCharacter = writingCharacter
        )

        val metadata = ThemeGenerationMetadata(
            recipe = recipe,
            imageInfluence = influence,
            writingCharacter = writingCharacter,
            relationshipMode = relationshipMode,
            originalAtmosphereHex = sourcePalette.atmosphericColor,
            selectedCandidateHex = seedHex,
            secondaryAccentHex = sourcePalette.secondaryAccent,
            tertiaryAccentHex = sourcePalette.tertiaryAccent,
            highlightHex = sourcePalette.visualPalette?.visualHighlight,
            visualPalette = sourcePalette.visualPalette,
            sourceImageFingerprint = understanding.imageFingerprint,
            generationVersion = 3
        )

        val defaults = ThemeManager.generateThemeDefaults(
            sources = sourcePalette,
            isDark = isDark,
            metadata = metadata
        )

        val themeName = name ?: generateThemeName(understanding, recipe, seedColor, isDark)

        return AppTheme(
            id = "custom_generated_${System.currentTimeMillis()}",
            name = themeName,
            isDark = isDark,
            builtIn = false,
            schemaVersion = ThemeSchema.CURRENT_VERSION,
            generationMetadata = metadata,
            colors = defaults,
            backgroundImageUri = imageUri,
            bgMode = if (imageUri != null) "image" else "color",
            savedBgDominantColor = sourcePalette.atmosphericColor ?: understanding.dominantColors.firstOrNull()
        )
    }

    fun createGeneratedTheme(
        understanding: ImageUnderstanding,
        recipe: ThemeGenerationRecipe = ThemeGenerationRecipe.BALANCED,
        candidateColor: String,
        isDark: Boolean,
        influence: ImageInfluence = ImageInfluence.BALANCED,
        writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
        relationshipMode: ThemeRelationshipMode = ThemeRelationshipMode.THEME_IMAGE,
        imageUri: String? = null,
        name: String? = null
    ): AppTheme = createGeneratedTheme(
        name = name,
        understanding = understanding,
        recipe = recipe,
        candidateColor = parseHexToArgb(candidateColor),
        isDark = isDark,
        influence = influence,
        writingCharacter = writingCharacter,
        relationshipMode = relationshipMode,
        imageUri = imageUri
    )
}

