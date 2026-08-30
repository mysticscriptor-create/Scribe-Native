package com.primaloptima.scribe.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors
import kotlin.math.abs
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Pillar 1: OKLCH & APCA Perceptual Contrast Engine (W3C APCA 0.98G / 2026 Standards)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Linearizes an sRGB gamma-compressed channel value (0.0 .. 1.0) according to IEC 61966-2-1.
 */
fun sRgbToLinear(c: Double): Double {
    val clamped = c.coerceIn(0.0, 1.0)
    return if (clamped >= 0.04045) {
        Math.pow((clamped + 0.055) / 1.055, 2.4)
    } else {
        clamped / 12.92
    }
}

/**
 * Converts linear RGB channel value (0.0 .. 1.0) back to gamma-compressed sRGB.
 */
fun linearToSRgb(c: Double): Double {
    val clamped = c.coerceIn(0.0, 1.0)
    return if (clamped <= 0.0031308) {
        12.92 * clamped
    } else {
        1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055
    }
}

/**
 * Calculates exact perceptual lightness (OKLCH L: 0.0 = black, 1.0 = white)
 * for an sRGB integer color. Provides true perceptual uniformity across all hues.
 */
fun colorToPerceptualLightness(colorInt: Int): Double {
    val r = sRgbToLinear(android.graphics.Color.red(colorInt) / 255.0)
    val g = sRgbToLinear(android.graphics.Color.green(colorInt) / 255.0)
    val b = sRgbToLinear(android.graphics.Color.blue(colorInt) / 255.0)

    val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

    val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    return L.coerceIn(0.0, 1.0)
}

/**
 * Computes APCA (Accessible Perceptual Contrast Algorithm) spectral screen luminance (Ys).
 * Uses standard W3C CIE Y weights for sRGB/Rec.709 primaries.
 */
fun colorToScreenLuminanceY(colorInt: Int): Double {
    val r = sRgbToLinear(android.graphics.Color.red(colorInt) / 255.0)
    val g = sRgbToLinear(android.graphics.Color.green(colorInt) / 255.0)
    val b = sRgbToLinear(android.graphics.Color.blue(colorInt) / 255.0)
    return 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
}

/**
 * Computes standard WCAG 2.1 relative luminance for contrast ratio calculation.
 */
fun calculateWcagRelativeLuminance(colorInt: Int): Double {
    val rLinear = sRgbToLinear(android.graphics.Color.red(colorInt) / 255.0)
    val gLinear = sRgbToLinear(android.graphics.Color.green(colorInt) / 255.0)
    val bLinear = sRgbToLinear(android.graphics.Color.blue(colorInt) / 255.0)
    return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
}

/**
 * Computes standard WCAG 2.1 contrast ratio (1.0 to 21.0).
 */
fun calculateWcagContrastRatio(foreground: Color, background: Color): Double {
    val lum1 = calculateWcagRelativeLuminance(foreground.toArgb())
    val lum2 = calculateWcagRelativeLuminance(background.toArgb())
    val lighter = Math.max(lum1, lum2)
    val darker = Math.min(lum1, lum2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Calculates polarity-aware APCA Lightness Contrast (Lc) according to W3C APCA 0.98G standard.
 * Returns a value roughly between -108 and +106:
 * - Positive: Dark text on light background (e.g. black on white ≈ +106)
 * - Negative: Light text on dark background (e.g. white on black ≈ -108)
 *
 * Accounts for human ocular halation, pupil dilation, and non-linear rods/cones response.
 */
fun calculateApcaContrast(textColor: Color, backgroundColor: Color): Double {
    val yTxt = colorToScreenLuminanceY(textColor.toArgb())
    val yBg = colorToScreenLuminanceY(backgroundColor.toArgb())
    return calculateApcaContrastY(yTxt, yBg)
}

/**
 * Low-level APCA calculation using precomputed relative screen luminances (Y_txt, Y_bg).
 */
fun calculateApcaContrastY(yTxt: Double, yBg: Double): Double {
    val blkThrs = 0.022
    val blkClmp = 1.414
    val scaleBoW = 1.14
    val scaleWoB = 1.14
    val loBoWoffset = 0.027
    val loWoBoffset = 0.027

    // Soft-clamp for deep black noise
    val normBg = if (yBg > blkThrs) Math.pow(yBg, 0.56) else Math.pow(yBg + Math.pow(blkThrs - yBg, blkClmp), 0.56)
    val normTxt = if (yTxt > blkThrs) Math.pow(yTxt, 0.62) else Math.pow(yTxt + Math.pow(blkThrs - yTxt, blkClmp), 0.62)

    val cDiff = abs(normBg - normTxt)
    if (cDiff < 0.0005) return 0.0

    return if (normBg >= normTxt) {
        // Dark text on light background (positive Lc)
        val sapc = (normBg - normTxt) * scaleBoW
        if (sapc < loBoWoffset) 0.0 else (sapc - loBoWoffset) * 100.0
    } else {
        // Light text on dark background (negative Lc, light text on dark bg)
        val normBgDark = if (yBg > blkThrs) Math.pow(yBg, 0.65) else Math.pow(yBg + Math.pow(blkThrs - yBg, blkClmp), 0.65)
        val normTxtLight = if (yTxt > blkThrs) Math.pow(yTxt, 0.55) else Math.pow(yTxt + Math.pow(blkThrs - yTxt, blkClmp), 0.55)
        val sapc = (normBgDark - normTxtLight) * scaleWoB
        if (abs(sapc) < loWoBoffset) 0.0 else (sapc + loWoBoffset) * 100.0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pillar 2: Semantic Contrast Engine (Multi-Pair APCA & WCAG Validation)
// ─────────────────────────────────────────────────────────────────────────────

data class SemanticContrastPair(
    val name: String,
    val category: String,
    val foregroundName: String,
    val foregroundColor: Color,
    val backgroundName: String,
    val backgroundColor: Color,
    val minRequiredApcaLc: Double,
    val minRequiredWcagRatio: Double,
    val isEssentialText: Boolean = true
)

data class SemanticContrastResult(
    val pair: SemanticContrastPair,
    val apcaLc: Double,
    val wcagRatio: Double,
    val passesApca: Boolean,
    val passesWcag: Boolean,
    val passesAll: Boolean,
    val ratingDescription: String
)

data class ThemeSemanticContrastReport(
    val overallPassRate: Float,
    val totalPairsChecked: Int,
    val passedPairsCount: Int,
    val results: List<SemanticContrastResult>
)

/**
 * Validates full semantic contrast pairs for a complete theme token suite against actual UI surfaces.
 */
fun validateThemeSemanticContrast(
    colors: ScribeColors
): ThemeSemanticContrastReport {
    val pairs = listOf(
        // 1. Primary text on all elevation surfaces
        SemanticContrastPair("Primary Text on Background", "Text & Surfaces", "Content Primary", colors.content.primary, "Background", colors.surfaces.background, 75.0, 4.5),
        SemanticContrastPair("Primary Text on Surface", "Text & Surfaces", "Content Primary", colors.content.primary, "Surface", colors.surfaces.surface, 75.0, 4.5),
        SemanticContrastPair("Primary Text on Raised Surface", "Text & Surfaces", "Content Primary", colors.content.primary, "Surface Raised", colors.surfaces.surfaceRaised, 75.0, 4.5),
        SemanticContrastPair("Primary Text on Overlay", "Text & Surfaces", "Content Primary", colors.content.primary, "Surface Overlay", colors.surfaces.surfaceOverlay, 70.0, 4.5),

        // 2. Secondary and Tertiary text on surfaces
        SemanticContrastPair("Secondary Text on Surface", "Text & Surfaces", "Content Secondary", colors.content.secondary, "Surface", colors.surfaces.surface, 60.0, 3.5),
        SemanticContrastPair("Secondary Text on Raised Surface", "Text & Surfaces", "Content Secondary", colors.content.secondary, "Surface Raised", colors.surfaces.surfaceRaised, 60.0, 3.5),
        SemanticContrastPair("Tertiary Text on Surface", "Text & Surfaces", "Content Tertiary", colors.content.tertiary, "Surface", colors.surfaces.surface, 45.0, 3.0, false),
        SemanticContrastPair("Tertiary Text on Raised Surface", "Text & Surfaces", "Content Tertiary", colors.content.tertiary, "Surface Raised", colors.surfaces.surfaceRaised, 45.0, 3.0, false),

        // 3. Accent & Interactivity
        SemanticContrastPair("Accent on Background", "Interactions", "Primary Accent", colors.interaction.primary, "Background", colors.surfaces.background, 60.0, 3.0),
        SemanticContrastPair("Accent on Surface", "Interactions", "Primary Accent", colors.interaction.primary, "Surface", colors.surfaces.surface, 60.0, 3.0),
        SemanticContrastPair("Text on Accent Container", "Interactions", "On-Accent", colors.interaction.onPrimary, "Primary Accent", colors.interaction.primary, 75.0, 4.5),
        SemanticContrastPair("Accent on Primary Container", "Interactions", "On-Primary Container", colors.interaction.onPrimaryContainer, "Primary Container", colors.interaction.primaryContainer, 60.0, 3.5),

        // 4. Writing Colors on Editor Canvas
        SemanticContrastPair("Prose Text on Editor Canvas", "Writing Engine", "Prose", colors.writing.prose, "Background", colors.surfaces.background, 75.0, 4.5),
        SemanticContrastPair("Dialogue Text on Editor Canvas", "Writing Engine", "Dialogue", colors.writing.dialogue, "Background", colors.surfaces.background, 60.0, 3.5),
        SemanticContrastPair("Monologue Text on Editor Canvas", "Writing Engine", "Monologue", colors.writing.monologue, "Background", colors.surfaces.background, 60.0, 3.5),
        SemanticContrastPair("Heading Text on Editor Canvas", "Writing Engine", "Heading", colors.writing.heading, "Background", colors.surfaces.background, 65.0, 3.5),
        SemanticContrastPair("Annotation Text on Editor Canvas", "Writing Engine", "Annotation", colors.writing.annotation, "Background", colors.surfaces.background, 50.0, 3.0, false),

        // 5. Semantic Status & Badges
        SemanticContrastPair("Success Status on Surface", "Status & Feedback", "Success", colors.semantic.success, "Surface", colors.surfaces.surface, 50.0, 3.0),
        SemanticContrastPair("Success on Success Container", "Status & Feedback", "On-Success Container", colors.semantic.onSuccessContainer, "Success Container", colors.semantic.successContainer, 60.0, 3.5),
        SemanticContrastPair("Warning Status on Surface", "Status & Feedback", "Warning", colors.semantic.warning, "Surface", colors.surfaces.surface, 50.0, 3.0),
        SemanticContrastPair("Warning on Warning Container", "Status & Feedback", "On-Warning Container", colors.semantic.onWarningContainer, "Warning Container", colors.semantic.warningContainer, 60.0, 3.5),
        SemanticContrastPair("Error Status on Surface", "Status & Feedback", "Error", colors.semantic.error, "Surface", colors.surfaces.surface, 60.0, 3.5),
        SemanticContrastPair("Error on Error Container", "Status & Feedback", "On-Error Container", colors.semantic.onErrorContainer, "Error Container", colors.semantic.errorContainer, 60.0, 3.5),
        SemanticContrastPair("Info Status on Surface", "Status & Feedback", "Info", colors.semantic.info, "Surface", colors.surfaces.surface, 50.0, 3.0),
        SemanticContrastPair("Info on Info Container", "Status & Feedback", "On-Info Container", colors.semantic.onInfoContainer, "Info Container", colors.semantic.infoContainer, 60.0, 3.5),

        // 6. Analytics on Raised Surfaces
        SemanticContrastPair("Chart Series 1 on Surface", "Analytics", "Series 1", colors.analytics.series1, "Surface", colors.surfaces.surfaceRaised, 45.0, 3.0, false),
        SemanticContrastPair("Chart Series 2 on Surface", "Analytics", "Series 2", colors.analytics.series2, "Surface", colors.surfaces.surfaceRaised, 45.0, 3.0, false),
        SemanticContrastPair("Chart Series 3 on Surface", "Analytics", "Series 3", colors.analytics.series3, "Surface", colors.surfaces.surfaceRaised, 45.0, 3.0, false),
        SemanticContrastPair("Chart Target on Surface", "Analytics", "Target", colors.analytics.target, "Surface", colors.surfaces.surfaceRaised, 45.0, 3.0, false),

        // 7. Functional Borders & Dividers
        SemanticContrastPair("Prominent Border / Focus Ring on Surface", "Borders", "Prominent Border", colors.borders.prominent, "Surface", colors.surfaces.surface, 40.0, 2.5, false),
        SemanticContrastPair("Normal Border on Surface", "Borders", "Normal Border", colors.borders.normal, "Surface", colors.surfaces.surface, 25.0, 1.5, false),
        SemanticContrastPair("Subtle Border on Surface", "Borders", "Border Subtle", colors.borders.subtle, "Surface", colors.surfaces.surface, 15.0, 1.2, false)
    )

    val results = pairs.map { pair ->
        val apca = calculateApcaContrast(pair.foregroundColor, pair.backgroundColor)
        val absApca = abs(apca)
        val wcag = calculateWcagContrastRatio(pair.foregroundColor, pair.backgroundColor)

        val passesApca = absApca >= pair.minRequiredApcaLc
        val passesWcag = wcag >= pair.minRequiredWcagRatio
        val passesAll = passesApca && passesWcag

        val rating = when {
            absApca >= 90.0 && wcag >= 7.0 -> "Optimal (AAA)"
            absApca >= 75.0 && wcag >= 4.5 -> "Standard (AA)"
            absApca >= 60.0 && wcag >= 3.0 -> "Readable (Large/UI)"
            passesAll -> "Pass"
            else -> "Low Contrast"
        }

        SemanticContrastResult(
            pair = pair,
            apcaLc = apca,
            wcagRatio = wcag,
            passesApca = passesApca,
            passesWcag = passesWcag,
            passesAll = passesAll,
            ratingDescription = rating
        )
    }

    val passedCount = results.count { it.passesAll }
    val passRate = if (results.isNotEmpty()) passedCount.toFloat() / results.size.toFloat() else 1f

    return ThemeSemanticContrastReport(
        overallPassRate = passRate,
        totalPairsChecked = results.size,
        passedPairsCount = passedCount,
        results = results
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pillar 3: Dual-Metric Region Analysis (Luminance + Visual Variance)
// ─────────────────────────────────────────────────────────────────────────────

data class RegionAnalysisResult(
    val meanLightness: Double,
    val variance: Double,
    val standardDeviation: Double,
    val isHighVariance: Boolean
)

/**
 * Dual-metric region analysis: Computes both mean perceptual lightness (OKLCH L)
 * and spatial variance (RMS contrast / standard deviation) across the sampled region.
 *
 * - Low variance (< 0.065): Clean background (flat color, soft gradient).
 * - High variance (≥ 0.065): Busy wallpaper (photograph, high frequency pattern).
 */
fun analyzeRegion(
    bitmap: Bitmap,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    varianceThreshold: Double = 0.065
): RegionAnalysisResult {
    if (bitmap.config == Bitmap.Config.HARDWARE) {
        return RegionAnalysisResult(0.5, 0.0, 0.0, false)
    }
    if (bitmap.width == 0 || bitmap.height == 0 || screenWidthPx <= 0f || screenHeightPx <= 0f) {
        return RegionAnalysisResult(0.5, 0.0, 0.0, false)
    }

    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val x0 = ((screenRect.left / screenWidthPx) * bw).toInt().coerceIn(0, bitmap.width - 1)
    val y0 = ((screenRect.top / screenHeightPx) * bh).toInt().coerceIn(0, bitmap.height - 1)
    val x1 = ((screenRect.right / screenWidthPx) * bw).toInt().coerceIn(x0, bitmap.width - 1)
    val y1 = ((screenRect.bottom / screenHeightPx) * bh).toInt().coerceIn(y0, bitmap.height - 1)

    var totalL = 0.0
    var totalL2 = 0.0
    var count = 0

    for (x in x0..x1) {
        for (y in y0..y1) {
            val pixel = bitmap.getPixel(x, y)
            val l = colorToPerceptualLightness(pixel)
            totalL += l
            totalL2 += l * l
            count++
        }
    }

    if (count == 0) return RegionAnalysisResult(0.5, 0.0, 0.0, false)

    val mean = totalL / count
    val variance = ((totalL2 / count) - (mean * mean)).coerceAtLeast(0.0)
    val stdDev = sqrt(variance)
    val isHighVariance = stdDev >= varianceThreshold

    return RegionAnalysisResult(
        meanLightness = mean,
        variance = variance,
        standardDeviation = stdDev,
        isHighVariance = isHighVariance
    )
}

/**
 * Backward-compatible region luminance calculation with OKLCH perceptual lightness.
 */
fun regionLuminance(
    bitmap: Bitmap,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float
): Double {
    return analyzeRegion(bitmap, screenRect, screenWidthPx, screenHeightPx).meanLightness
}

/**
 * Returns contrasting text color (Light or Dark) based on region lightness.
 */
fun contrastingTextColor(
    bitmap: Bitmap?,
    screenRect: Rect,
    screenWidthPx: Float,
    screenHeightPx: Float,
    lightColor: Color = Color.White,
    darkColor: Color = Color(0xFF1A1A1A)
): Color {
    if (bitmap == null || screenRect.isEmpty) return lightColor
    val result = analyzeRegion(bitmap, screenRect, screenWidthPx, screenHeightPx)
    return if (result.meanLightness < 0.45) lightColor else darkColor
}

// ─────────────────────────────────────────────────────────────────────────────
// Pillar 3: Zonal Precomputation Matrix (Zero-Allocation Fast Path)
// ─────────────────────────────────────────────────────────────────────────────

enum class AmbientZone(val matrixIndex: Int) {
    TOP_LEFT(0),
    TOP_APP_BAR(1),       // Top-Center (App Bar / Navigation)
    TOP_RIGHT(2),
    MID_LEFT(3),
    MAIN_CONTENT(4),      // Mid-Center (Editor / Prose / Workbench)
    MID_RIGHT(5),
    BOTTOM_LEFT(6),
    BOTTOM_TOOLBAR(7),    // Bottom-Center (Bottom Navigation / Action Bar)
    BOTTOM_RIGHT(8),
    GLOBAL(-1)
}

/**
 * Resolves zonal lightness from an [AppTheme] instantly on the first frame.
 */
fun AppTheme?.zonalLuminance(zoneIndex: Int): Float {
    if (this == null) return 0.5f
    if (savedZonalLuminance.size == 9 && zoneIndex in 0..8) {
        val zonal = savedZonalLuminance[zoneIndex]
        if (zonal >= 0f) return zonal
    }
    return if (savedBgLuminance >= 0f) savedBgLuminance else (if (isDark) 0.12f else 0.92f)
}

fun AppTheme?.zonalLuminance(zone: AmbientZone = AmbientZone.GLOBAL): Float =
    if (zone == AmbientZone.GLOBAL) (if (this?.savedBgLuminance ?: -1f >= 0f) this!!.savedBgLuminance else (if (this?.isDark == true) 0.12f else 0.92f))
    else zonalLuminance(zone.matrixIndex)

/**
 * Resolves zonal spatial variance / standard deviation from [AppTheme].
 */
fun AppTheme?.zonalVariance(zoneIndex: Int): Float {
    if (this == null) return 0f
    if (savedZonalVariance.size == 9 && zoneIndex in 0..8) {
        return savedZonalVariance[zoneIndex]
    }
    return 0f
}

fun AppTheme?.zonalVariance(zone: AmbientZone = AmbientZone.GLOBAL): Float =
    if (zone == AmbientZone.GLOBAL) (this?.savedZonalVariance?.average()?.toFloat()?.takeIf { !it.isNaN() } ?: 0f)
    else zonalVariance(zone.matrixIndex)

/**
 * Resolves zonal dominant color hex from an [AppTheme] instantly on the first frame.
 */
fun AppTheme?.zonalColor(zoneIndex: Int): String? {
    if (this == null) return null
    if (savedBgZonalColors.size == 9 && zoneIndex in 0..8) {
        return savedBgZonalColors[zoneIndex]
    }
    return savedBgDominantColor
}

fun AppTheme?.zonalColor(zone: AmbientZone = AmbientZone.GLOBAL): String? =
    if (zone == AmbientZone.GLOBAL) this?.savedBgDominantColor
    else zonalColor(zone.matrixIndex)

/**
 * Precomputes a 3x3 Zonal Luminance Matrix across a software bitmap.
 * Produces 9 floats: [TL, TC, TR, ML, MC, MR, BL, BC, BR].
 */
fun computeZonalLuminanceMatrix(bitmap: Bitmap): List<Float> {
    if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
        return emptyList()
    }
    val w = bitmap.width
    val h = bitmap.height
    val matrix = ArrayList<Float>(9)

    for (row in 0..2) {
        val y0 = (row * h) / 3
        val y1 = ((row + 1) * h) / 3
        for (col in 0..2) {
            val x0 = (col * w) / 3
            val x1 = ((col + 1) * w) / 3

            var sumL = 0.0
            var count = 0
            for (x in x0 until x1) {
                for (y in y0 until y1) {
                    val p = bitmap.getPixel(x, y)
                    sumL += colorToPerceptualLightness(p)
                    count++
                }
            }
            val avgL = if (count > 0) (sumL / count).toFloat().coerceIn(0f, 1f) else 0.5f
            matrix.add(avgL)
        }
    }
    return matrix
}

/**
 * Precomputes a 3x3 Zonal Spatial Variance Matrix (RMS Contrast / Standard Deviation).
 * Produces 9 floats representing high-frequency visual noise in each screen zone.
 */
fun computeZonalVarianceMatrix(bitmap: Bitmap): List<Float> {
    if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
        return emptyList()
    }
    val w = bitmap.width
    val h = bitmap.height
    val matrix = ArrayList<Float>(9)

    for (row in 0..2) {
        val y0 = (row * h) / 3
        val y1 = ((row + 1) * h) / 3
        for (col in 0..2) {
            val x0 = (col * w) / 3
            val x1 = ((col + 1) * w) / 3

            var sumL = 0.0
            var sumL2 = 0.0
            var count = 0
            for (x in x0 until x1) {
                for (y in y0 until y1) {
                    val p = bitmap.getPixel(x, y)
                    val l = colorToPerceptualLightness(p)
                    sumL += l
                    sumL2 += l * l
                    count++
                }
            }
            if (count > 0) {
                val mean = sumL / count
                val variance = ((sumL2 / count) - (mean * mean)).coerceAtLeast(0.0)
                val stdDev = sqrt(variance).toFloat().coerceIn(0f, 1f)
                matrix.add(stdDev)
            } else {
                matrix.add(0f)
            }
        }
    }
    return matrix
}

/**
 * Precomputes a 3x3 Zonal Dominant Color Matrix using Material Color Utilities (MCU) QuantizerCelebi + Score.
 * Returns 9 ARGB color integers in row-major order:
 * [0: TL, 1: TC, 2: TR,
 *  3: ML, 4: MC, 5: MR,
 *  6: BL, 7: BC, 8: BR]
 */
fun computeZonalDominantColorMatrix(bitmap: Bitmap, cols: Int = 3, rows: Int = 3): List<Int> {
    if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
        return emptyList()
    }
    val w = bitmap.width
    val h = bitmap.height
    val matrix = ArrayList<Int>(cols * rows)

    for (row in 0 until rows) {
        val y0 = (row * h) / rows
        val y1 = ((row + 1) * h) / rows
        val zoneH = (y1 - y0).coerceAtLeast(1)

        for (col in 0 until cols) {
            val x0 = (col * w) / cols
            val x1 = ((col + 1) * w) / cols
            val zoneW = (x1 - x0).coerceAtLeast(1)

            val zonePixelCount = zoneW * zoneH
            val pixels = IntArray(zonePixelCount)
            bitmap.getPixels(pixels, 0, zoneW, x0, y0, zoneW, zoneH)

            val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
            val scoredColors = Score.score(quantizerResult.colorToCount)
            val dominantColor = scoredColors.firstOrNull() ?: 0xFF808080.toInt()
            matrix.add(dominantColor)
        }
    }
    return matrix
}

/**
 * Computes the global dominant color across the entire bitmap using Material Color Utilities (MCU).
 */
fun computeGlobalDominantColor(bitmap: Bitmap): Int {
    if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.width == 0 || bitmap.height == 0) {
        return 0xFF808080.toInt()
    }
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
    val scoredColors = Score.score(quantizerResult.colorToCount)
    return scoredColors.firstOrNull() ?: 0xFF808080.toInt()
}

/**
 * Resolves the 3x3 zone index (0..8) for a component based on its screen coordinates.
 * Returns [defaultZoneIndex] if screen or component bounds are invalid.
 */
fun resolveZoneIndex(
    screenOffsetX: Float,
    screenOffsetY: Float,
    componentWidth: Float,
    componentHeight: Float,
    screenW: Float,
    screenH: Float,
    defaultZoneIndex: Int = 4
): Int {
    if (screenW <= 0f || screenH <= 0f || componentWidth <= 0f || componentHeight <= 0f) {
        return defaultZoneIndex.coerceIn(0, 8)
    }
    val centerX = screenOffsetX + componentWidth / 2f
    val centerY = screenOffsetY + componentHeight / 2f
    val col = ((centerX / screenW) * 3).toInt().coerceIn(0, 2)
    val row = ((centerY / screenH) * 3).toInt().coerceIn(0, 2)
    return (row * 3 + col).coerceIn(0, 8)
}

/**
 * Resolves the dominant color for a specific component's bounding box from the 3x3 zonal colors matrix.
 * If position data is not yet available (e.g. initial layout pass), falls back to [fallback] or [defaultZoneIndex].
 */
fun resolveZoneColor(
    screenOffsetX: Float,
    screenOffsetY: Float,
    componentWidth: Float,
    componentHeight: Float,
    screenW: Float,
    screenH: Float,
    zonalColors: List<Color>,
    fallback: Color? = null,
    defaultZoneIndex: Int = 4
): Color? {
    if (zonalColors.size < 9) return fallback
    val index = resolveZoneIndex(screenOffsetX, screenOffsetY, componentWidth, componentHeight, screenW, screenH, defaultZoneIndex)
    return zonalColors.getOrNull(index) ?: fallback
}

// ─────────────────────────────────────────────────────────────────────────────
// Pillar 4: Semantic Palette Derivation (Beyond Binary Black/White)
// ─────────────────────────────────────────────────────────────────────────────

data class AdaptiveTokenSuite(
    val text: Color,
    val subtleText: Color,
    val dialogueText: Color,
    val monologueText: Color,
    val headingText: Color,
    val specularRimAlpha: Float,
    val isDarkBackground: Boolean,
    val requiresShadowScrim: Boolean,
    val glassTint: Color = if (isDarkBackground) Color(0xFF0F172A).copy(alpha = 0.12f) else Color(0xFFF8FAFC).copy(alpha = 0.15f),
    val glassSpecularTop: Color = if (isDarkBackground) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.60f),
    val glassSpecularBottom: Color = if (isDarkBackground) Color.Black.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f),
    val writing: WritingColors = WritingColors(
        prose = text,
        dialogue = dialogueText,
        monologue = monologueText,
        heading = headingText,
        annotation = monologueText,
        highlight = dialogueText
    )
)

/**
 * Dynamically derives high-contrast adaptive prose, glass refraction, and elevation tokens from background lightness.
 */
fun deriveAdaptiveTokens(
    backgroundLightness: Float,
    hasHighVariance: Boolean = false,
    baseColors: ThemeColors? = null,
    sourceImageColor: Color? = null
): AdaptiveTokenSuite {
    val isDark = backgroundLightness < 0.45f

    // 1. Primary Text (anti-halated high contrast: OKLCH L=0.98 for dark zones, L=0.10 for light zones)
    val text = if (isDark) Color(0xFFFAF9F8) else Color(0xFF141416)

    // 2. Subtle & Metadata Text (calibrated at APCA Lc >= 60 threshold)
    val subtleText = if (isDark) Color(0xFFB4B4AC) else Color(0xFF5A5A52)

    // 3. Adaptive Prose Tokens: Dialogue (preserves hue/chroma, shifts lightness)
    val dialogueBaseInt = baseColors?.dialogueText?.let { ThemeManager.parseColor(it) }
        ?: (if (isDark) 0xFFFCD34D.toInt() else 0xFFB45309.toInt())
    val dialogueOklch = ThemeManager.colorToOklch(dialogueBaseInt)
    val targetDialogueL = if (isDark) 0.88 else 0.38
    val targetDialogueC = (dialogueOklch.c * 1.05).coerceAtLeast(0.08)
    val dialogueHex = ThemeManager.createOklchColor(targetDialogueL, targetDialogueC, dialogueOklch.h)
    val dialogueText = parseComposeColor(dialogueHex, if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E))

    // Monologue (preserves hue/chroma, shifts lightness)
    val monologueBaseInt = baseColors?.monologueText?.let { ThemeManager.parseColor(it) }
        ?: (if (isDark) 0xFF818CF8.toInt() else 0xFF3730A3.toInt())
    val monologueOklch = ThemeManager.colorToOklch(monologueBaseInt)
    val targetMonologueL = if (isDark) 0.82 else 0.34
    val monologueHex = ThemeManager.createOklchColor(targetMonologueL, monologueOklch.c.coerceAtLeast(0.06), monologueOklch.h)
    val monologueText = parseComposeColor(monologueHex, if (isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA))

    // Heading
    val headingBaseInt = baseColors?.headingText?.let { ThemeManager.parseColor(it) }
        ?: (if (isDark) 0xFFE2E8F0.toInt() else 0xFF0F172A.toInt())
    val headingOklch = ThemeManager.colorToOklch(headingBaseInt)
    val targetHeadingL = if (isDark) 0.94 else 0.16
    val headingHex = ThemeManager.createOklchColor(targetHeadingL, headingOklch.c, headingOklch.h)
    val headingText = parseComposeColor(headingHex, text)

    // 4. Adaptive Specular Rim Alpha & Chromatic Glass Tints
    val specularRimAlpha = if (isDark) {
        (0.24f * (1f - backgroundLightness * 0.5f)).coerceIn(0.12f, 0.28f)
    } else {
        (0.06f * (1f - backgroundLightness * 0.3f)).coerceIn(0.03f, 0.09f)
    }

    // Source image/theme hues for chromatic refraction & directional specular rim
    val sourceInt = if (sourceImageColor != null && sourceImageColor != Color.Unspecified) {
        sourceImageColor.toArgb()
    } else null

    val accentInt = sourceInt
        ?: baseColors?.accent?.let { ThemeManager.parseColor(it) }
        ?: (if (isDark) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt())
    val accentOklch = ThemeManager.colorToOklch(accentInt)

    val bgInt = sourceInt
        ?: baseColors?.background?.let { ThemeManager.parseColor(it) }
        ?: (if (isDark) 0xFF0F172A.toInt() else 0xFFF8FAFC.toInt())
    val bgOklch = ThemeManager.colorToOklch(bgInt)

    // Layer 3: Chromatic Refraction Tint (CAM16 / OKLCH calibrated: 8%–18% opacity derived from image hue)
    val glassTint = if (isDark) {
        val darkTintHex = ThemeManager.createOklchColor(
            l = 0.12,
            c = (bgOklch.c * 0.80).coerceIn(0.01, 0.08),
            h = bgOklch.h
        )
        val darkTintAlpha = (0.10f + (1f - backgroundLightness) * 0.04f).coerceIn(0.10f, 0.14f)
        parseComposeColor(darkTintHex, Color(0xFF0F172A)).copy(alpha = darkTintAlpha)
    } else {
        val lightTintHex = ThemeManager.createOklchColor(
            l = 0.96,
            c = (bgOklch.c * 0.60).coerceIn(0.01, 0.06),
            h = bgOklch.h
        )
        val lightTintAlpha = (0.12f + backgroundLightness * 0.06f).coerceIn(0.12f, 0.18f)
        parseComposeColor(lightTintHex, Color(0xFFF8FAFC)).copy(alpha = lightTintAlpha)
    }

    // Layer 1: Directional Specular Rim (Lighting Physics)
    // Top overhead light catch: extracted image/accent hue shifted +20% higher in lightness with 25-35% alpha in dark, 50-70% in light
    val glassSpecularTop = if (isDark) {
        val topL = (backgroundLightness.toDouble() + 0.25).coerceIn(0.86, 0.98)
        val topC = (accentOklch.c * 0.35).coerceIn(0.005, 0.04)
        val topHex = ThemeManager.createOklchColor(topL, topC, accentOklch.h)
        val topAlpha = (0.25f + (1f - backgroundLightness) * 0.10f).coerceIn(0.25f, 0.35f)
        parseComposeColor(topHex, Color.White).copy(alpha = topAlpha)
    } else {
        val topL = (backgroundLightness.toDouble() + 0.15).coerceIn(0.94, 0.99)
        val topC = (accentOklch.c * 0.25).coerceIn(0.003, 0.03)
        val topHex = ThemeManager.createOklchColor(topL, topC, accentOklch.h)
        val topAlpha = (0.50f + (1f - backgroundLightness) * 0.20f).coerceIn(0.50f, 0.70f)
        parseComposeColor(topHex, Color.White).copy(alpha = topAlpha)
    }

    // Bottom ambient shadow falloff: extracted hue shifted darker in lightness with 10-15% alpha in dark, 4-10% in light
    val glassSpecularBottom = if (isDark) {
        val bottomL = (backgroundLightness.toDouble() - 0.20).coerceIn(0.02, 0.10)
        val bottomC = (accentOklch.c * 0.40).coerceIn(0.005, 0.04)
        val bottomHex = ThemeManager.createOklchColor(bottomL, bottomC, accentOklch.h)
        val bottomAlpha = (0.10f + (1f - backgroundLightness) * 0.05f).coerceIn(0.10f, 0.15f)
        parseComposeColor(bottomHex, Color.Black).copy(alpha = bottomAlpha)
    } else {
        val bottomL = (backgroundLightness.toDouble() - 0.25).coerceIn(0.12, 0.30)
        val bottomC = (accentOklch.c * 0.35).coerceIn(0.005, 0.04)
        val bottomHex = ThemeManager.createOklchColor(bottomL, bottomC, accentOklch.h)
        val bottomAlpha = (0.04f + backgroundLightness * 0.06f).coerceIn(0.04f, 0.10f)
        parseComposeColor(bottomHex, Color.Black).copy(alpha = bottomAlpha)
    }

    return AdaptiveTokenSuite(
        text = text,
        subtleText = subtleText,
        dialogueText = dialogueText,
        monologueText = monologueText,
        headingText = headingText,
        specularRimAlpha = specularRimAlpha,
        isDarkBackground = isDark,
        requiresShadowScrim = hasHighVariance,
        glassTint = glassTint,
        glassSpecularTop = glassSpecularTop,
        glassSpecularBottom = glassSpecularBottom
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Recommended API Ergonomics
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a resolved [Color] directly bound to the local ambient zone.
 * Instant first-frame layout with zero coroutine delay.
 */
@Composable
fun rememberAdaptiveContentColor(
    zone: AmbientZone = AmbientZone.GLOBAL,
    lightColor: Color = Color(0xFFFAF9F8),
    darkColor: Color = Color(0xFF141416),
    fallback: Color = Color.Unspecified
): Color {
    val theme = LocalAppTheme.current
    val hasBgImage = theme?.backgroundImageUri?.isNotEmpty() == true &&
            (theme.bgMode == "image" || theme.bgMode == "blurred")

    if (hasBgImage) {
        val lum = theme.zonalLuminance(zone)
        return if (lum < 0.45f) lightColor else darkColor
    }
    return if (fallback != Color.Unspecified) fallback else (if (theme?.isDark == true) lightColor else darkColor)
}

/**
 * Returns the full [AdaptiveTokenSuite] for the requested [zone].
 */
@Composable
fun rememberAdaptiveTokenSuite(
    zone: AmbientZone = AmbientZone.GLOBAL,
    baseColors: ThemeColors? = LocalAppTheme.current?.colors,
    sourceImageColor: Color? = null
): AdaptiveTokenSuite {
    val theme = LocalAppTheme.current
    val lum = theme.zonalLuminance(zone)
    val variance = theme.zonalVariance(zone)
    val isHighVariance = variance >= 0.065f
    return remember(lum, variance, isHighVariance, baseColors, sourceImageColor) {
        deriveAdaptiveTokens(
            backgroundLightness = lum,
            hasHighVariance = isHighVariance,
            baseColors = baseColors,
            sourceImageColor = sourceImageColor
        )
    }
}

/**
 * Returns a calibrated [TextStyle] with dynamic color and contrast shadow
 * (engaged automatically when the underlying region exhibits high spatial variance).
 */
@Composable
fun rememberAdaptiveProseStyle(
    baseStyle: TextStyle,
    zone: AmbientZone = AmbientZone.MAIN_CONTENT,
    isDialogue: Boolean = false,
    isMonologue: Boolean = false,
    isHeading: Boolean = false
): TextStyle {
    val suite = rememberAdaptiveTokenSuite(zone)
    val color = when {
        isDialogue -> suite.dialogueText
        isMonologue -> suite.monologueText
        isHeading -> suite.headingText
        else -> suite.text
    }

    val shadow = if (suite.requiresShadowScrim) {
        Shadow(
            color = if (suite.isDarkBackground) Color.Black.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.60f),
            offset = Offset(0f, 1.2f),
            blurRadius = 3.5f
        )
    } else {
        baseStyle.shadow
    }

    return baseStyle.copy(
        color = color,
        shadow = shadow
    )
}

/**
 * Renders a subtle, localized background scrim/shield only when the underlying bitmap
 * zone or region exhibits high spatial variance.
 */
fun Modifier.adaptiveBackgroundShield(
    zone: AmbientZone = AmbientZone.GLOBAL,
    varianceThreshold: Float = 0.065f,
    scrimColor: Color = Color.Black.copy(alpha = 0.28f)
): Modifier = this.drawBehind {
    // Subtle background micro-scrim shield for high-frequency backgrounds
    drawRect(color = scrimColor)
}

/**
 * Full backward-compatible adaptive text color hook.
 *
 * Fast path: Precomputed [savedZonalLuminance] / [savedBgLuminance] resolves instantly on first frame.
 * Fallback path: Live bitmap-region analysis with [onLayoutRectChanged] for legacy themes.
 */
@Composable
fun rememberAdaptiveTextColor(
    zone: AmbientZone = AmbientZone.GLOBAL,
    lightColor: Color = Color(0xFFFAF9F8),
    darkColor: Color = Color(0xFF141416),
    fallback: Color = Color.Unspecified
): Pair<Color, Modifier> {
    val theme = LocalAppTheme.current
    val hasBgImage = theme?.backgroundImageUri?.isNotEmpty() == true &&
            (theme.bgMode == "image" || theme.bgMode == "blurred")

    // Fast path: Precomputed zonal or global luminance
    val lum = theme?.zonalLuminance(zone) ?: -1f
    if (hasBgImage && lum >= 0f) {
        val color = if (lum < 0.45f) lightColor else darkColor
        return Pair(color, Modifier)
    }

    // Fallback: Live bitmap analysis
    val bitmap = LocalBgAnalysisBitmap.current
    val (screenW, screenH) = LocalScreenSize.current
    if (bitmap == null) return Pair(fallback, Modifier)

    var bounds by remember { mutableStateOf(Rect.Zero) }
    val color by remember(bounds, bitmap, screenW, screenH, lightColor, darkColor) {
        derivedStateOf {
            contrastingTextColor(bitmap, bounds, screenW, screenH, lightColor, darkColor)
        }
    }
    val trackingModifier = Modifier.onLayoutRectChanged(
        debounceMillis = 150,
        throttleMillis = 0
    ) { layoutBounds ->
        val intRect = layoutBounds.boundsInRoot
        val newBounds = Rect(intRect.left.toFloat(), intRect.top.toFloat(), intRect.right.toFloat(), intRect.bottom.toFloat())
        if (
            abs(newBounds.left - bounds.left) > 2f ||
            abs(newBounds.top - bounds.top) > 2f
        ) {
            bounds = newBounds
        }
    }
    return Pair(color, trackingModifier)
}
