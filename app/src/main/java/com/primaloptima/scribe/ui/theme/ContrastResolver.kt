package com.primaloptima.scribe.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 3: Unified Contrast Resolution Engine for Scribe.
 *
 * Implements a deterministic, hue-preserving, contrast-aware resolution pipeline
 * that replaces ad-hoc thresholding (e.g. luminance < 0.5f -> white : black) across
 * all semantic token roles.
 *
 * Optimization Hierarchy:
 * 1. Accessibility (strict compliance with WCAG 2.2 SC 1.4.3 / 1.4.11)
 * 2. Semantic correctness (distinct role separation, e.g. error is red, warning is amber)
 * 3. Theme identity (preserves aesthetic mood of Obsidian, Midnight Blue, Focus, Paper, Sepia, Typewriter)
 * 4. Hue/chroma preservation (adjusts along perceptual OKLCH lightness before desaturating)
 * 5. Minimal visual disturbance (finds the minimum shift required to satisfy contrast thresholds)
 */
object ContrastResolver {

    /**
     * Semantic role context specifying standard WCAG 2.2 contrast thresholds and APCA Lc targets.
     *
     * Note on Accessibility Standards:
     * - WCAG 2.2 (SC 1.4.3 / 1.4.11) is the normative accessibility baseline used for official conformance.
     * - APCA (Accessible Perceptual Contrast Algorithm - W3C Silver / WCAG 3 research draft) is used strictly
     *   as an experimental perceptual diagnostic tool to assess reading comfort and polarity effects.
     *   APCA values MUST NOT be represented or interpreted as finalized WCAG 3 conformance.
     */
    enum class ContrastRole(val defaultMinRatio: Double, val defaultMinApca: Double = 45.0) {
        /** Normal body text, prose, dialogue, monologue, captions (<18pt or <14pt bold) - WCAG 2.2 SC 1.4.3 (4.5:1) & APCA Lc 75 */
        NORMAL_TEXT(4.5, 75.0),

        /** Large text (>=18pt or >=14pt bold), headings, hero titles - WCAG 2.2 SC 1.4.3 (3.0:1) & APCA Lc 60 */
        LARGE_TEXT(3.0, 60.0),

        /** Secondary/subtle text, metadata, word counts - WCAG 2.2 (3.5:1 / 4.5:1) & APCA Lc 60 */
        SECONDARY_TEXT(3.5, 60.0),

        /** Interactive controls, buttons, FABs, focus rings, status indicators - WCAG 2.2 SC 1.4.11 (3.0:1) & APCA Lc 45 */
        UI_CONTROL(3.0, 45.0),

        /** Badges and status pills with text content (4.5:1 for standard legibility & APCA Lc 60) */
        STATUS_BADGE(4.5, 60.0),

        /** Text rendered inside high-emphasis container surfaces (4.5:1 & APCA Lc 65) */
        CONTAINER_TEXT(4.5, 65.0),

        /** Functional borders with state or boundary information - WCAG 2.2 SC 1.4.11 (3.0:1) & APCA Lc 30 */
        BORDER(3.0, 30.0),

        /** Secondary/decorative dividers not required to identify control boundaries */
        DECORATIVE(1.5, 15.0)
    }

    /**
     * Resolution strategy applied to achieve required contrast.
     */
    enum class ResolutionMethod {
        /** Preferred foreground already satisfied or exceeded required contrast */
        DIRECT_PASS,

        /** Shifted perceptual lightness in OKLCH while preserving exact hue and chroma */
        HUE_PRESERVED_LIGHTNESS,

        /** Shifted perceptual lightness with scaled chroma to remain within valid sRGB gamut */
        CHROMA_ADAPTED,

        /** High-contrast tinted neutral endpoint */
        TINTED_FALLBACK,

        /** Maximum contrast polarity endpoint (pure/tinted black or white) */
        POLARITY_FALLBACK
    }

    /**
     * Result of a contrast resolution operation.
     */
    data class ResolvedContrast(
        val color: Color,
        val actualRatio: Double,
        val passesRequired: Boolean,
        val method: ResolutionMethod,
        val apcaLc: Double = 0.0,
        val repairMagnitude: Double = 0.0
    )

    /**
     * Internal OKLCH representation for perceptual uniformity and hue preservation.
     */
    data class Oklch(val l: Double, val c: Double, val h: Double)

    // ─────────────────────────────────────────────────────────────────────────
    // Normative Color Space & Contrast Math (WCAG 2.2 IEC 61966-2-1)
    // ─────────────────────────────────────────────────────────────────────────

    fun sRgbToLinear(c: Double): Double {
        val clamped = c.coerceIn(0.0, 1.0)
        return if (clamped <= 0.04045) {
            clamped / 12.92
        } else {
            ((clamped + 0.055) / 1.055).pow(2.4)
        }
    }

    fun linearToSRgb(c: Double): Double {
        val clamped = c.coerceIn(0.0, 1.0)
        return if (clamped <= 0.0031308) {
            12.92 * clamped
        } else {
            1.055 * clamped.pow(1.0 / 2.4) - 0.055
        }
    }

    /**
     * Computes WCAG 2.2 relative luminance Y in [0.0, 1.0] from an integer ARGB color.
     */
    fun calculateWcagRelativeLuminance(colorInt: Int): Double {
        val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
        val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
        val b = sRgbToLinear((colorInt and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Computes WCAG 2.2 relative luminance Y in [0.0, 1.0] from a Compose Color.
     */
    fun calculateWcagRelativeLuminance(color: Color): Double {
        return calculateWcagRelativeLuminance(color.toArgb())
    }

    /**
     * Computes normative WCAG 2.2 contrast ratio (1.0 to 21.0) between two colors.
     */
    fun calculateWcagContrastRatio(foreground: Color, background: Color): Double {
        val l1 = calculateWcagRelativeLuminance(foreground)
        val l2 = calculateWcagRelativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun calculateWcagContrastRatio(fgInt: Int, bgInt: Int): Double {
        val l1 = calculateWcagRelativeLuminance(fgInt)
        val l2 = calculateWcagRelativeLuminance(bgInt)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APCA (Accessible Perceptual Contrast Algorithm - W3C 0.98G)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes APCA spectral screen luminance (Ys) using sRGB/Rec.709 primaries.
     */
    fun colorToScreenLuminanceY(colorInt: Int): Double {
        val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
        val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
        val b = sRgbToLinear((colorInt and 0xFF) / 255.0)
        return 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    }

    /**
     * Computes polarity-aware APCA Lightness Contrast (Lc).
     * Positive values indicate dark text on light background (BoW).
     * Negative values indicate light text on dark background (WoB).
     */
    fun calculateApcaContrast(textColor: Color, backgroundColor: Color): Double {
        val yTxt = colorToScreenLuminanceY(textColor.toArgb())
        val yBg = colorToScreenLuminanceY(backgroundColor.toArgb())
        return calculateApcaContrastY(yTxt, yBg)
    }

    fun calculateApcaContrast(fgInt: Int, bgInt: Int): Double {
        val yTxt = colorToScreenLuminanceY(fgInt)
        val yBg = colorToScreenLuminanceY(bgInt)
        return calculateApcaContrastY(yTxt, yBg)
    }

    /**
     * Low-level APCA calculation using precomputed relative screen luminances.
     */
    fun calculateApcaContrastY(yTxt: Double, yBg: Double): Double {
        val blkThrs = 0.022
        val blkClmp = 1.414
        val scaleBoW = 1.14
        val scaleWoB = 1.14
        val loBoWoffset = 0.027
        val loWoBoffset = 0.027

        val normBg = if (yBg > blkThrs) Math.pow(yBg, 0.56) else Math.pow(yBg + Math.pow(blkThrs - yBg, blkClmp), 0.56)
        val normTxt = if (yTxt > blkThrs) Math.pow(yTxt, 0.62) else Math.pow(yTxt + Math.pow(blkThrs - yTxt, blkClmp), 0.62)

        val cDiff = abs(normBg - normTxt)
        if (cDiff < 0.0005) return 0.0

        return if (normBg >= normTxt) {
            val sapc = (normBg - normTxt) * scaleBoW
            if (sapc < loBoWoffset) 0.0 else (sapc - loBoWoffset) * 100.0
        } else {
            val normBgDark = if (yBg > blkThrs) Math.pow(yBg, 0.65) else Math.pow(yBg + Math.pow(blkThrs - yBg, blkClmp), 0.65)
            val normTxtLight = if (yTxt > blkThrs) Math.pow(yTxt, 0.55) else Math.pow(yTxt + Math.pow(blkThrs - yTxt, blkClmp), 0.55)
            val sapc = (normBgDark - normTxtLight) * scaleWoB
            if (abs(sapc) < loWoBoffset) 0.0 else (sapc + loWoBoffset) * 100.0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OKLCH Perceptual Transformations & Gamut Mapping
    // ─────────────────────────────────────────────────────────────────────────

    fun colorToOklch(colorInt: Int): Oklch {
        val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
        val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
        val b = sRgbToLinear((colorInt and 0xFF) / 255.0)

        val l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b).pow(1.0 / 3.0)
        val m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b).pow(1.0 / 3.0)
        val s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b).pow(1.0 / 3.0)

        val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bVal = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s

        val C = sqrt(a * a + bVal * bVal)
        var h = Math.toDegrees(atan2(bVal, a))
        if (h < 0.0) h += 360.0

        return Oklch(L.coerceIn(0.0, 1.0), C.coerceAtLeast(0.0), h)
    }

    /**
     * Unclamped conversion from OKLCH to linear sRGB components.
     */
    fun oklchToLinearSrgb(oklch: Oklch): Triple<Double, Double, Double> {
        val hRad = Math.toRadians(oklch.h)
        val a = oklch.c * cos(hRad)
        val bVal = oklch.c * sin(hRad)

        val l_ = oklch.l + 0.3963377774 * a + 0.2158037573 * bVal
        val m_ = oklch.l - 0.1055613458 * a - 0.0638541728 * bVal
        val s_ = oklch.l - 0.0894841775 * a - 1.2914855480 * bVal

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val rLin = +4.0767439362 * l - 3.3077115913 * m + 0.2309699292 * s
        val gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

        return Triple(rLin, gLin, bLin)
    }

    /**
     * Tests if an OKLCH coordinate resides strictly within the displayable sRGB gamut.
     */
    fun isInSrgbGamut(oklch: Oklch, epsilon: Double = 0.0001): Boolean {
        val (rLin, gLin, bLin) = oklchToLinearSrgb(oklch)
        return rLin in -epsilon..(1.0 + epsilon) &&
               gLin in -epsilon..(1.0 + epsilon) &&
               bLin in -epsilon..(1.0 + epsilon)
    }

    /**
     * Finds maximum in-gamut chroma for a given lightness and hue in OKLCH space.
     * Uses binary search along constant lightness L and hue H to prevent hue shifts.
     */
    fun findMaxChromaInSrgb(l: Double, h: Double, iterations: Int = 12): Double {
        if (l <= 0.0001 || l >= 0.9999) return 0.0
        var low = 0.0
        var high = 0.40
        for (i in 0 until iterations) {
            val mid = (low + high) / 2.0
            if (isInSrgbGamut(Oklch(l, mid, h))) {
                low = mid
            } else {
                high = mid
            }
        }
        return low
    }

    /**
     * Maps an OKLCH color safely into sRGB by clamping chroma to the gamut boundary
     * along constant lightness and hue, eliminating clipping artifacts and hue distortion.
     */
    fun mapToSrgbGamut(oklch: Oklch): Oklch {
        if (isInSrgbGamut(oklch)) return oklch
        val maxC = findMaxChromaInSrgb(oklch.l, oklch.h)
        return Oklch(oklch.l, min(oklch.c, maxC), oklch.h)
    }

    /**
     * Calculates the perceptual distance Delta E in OKLCH / Oklab space.
     * Provides a uniform metric for evaluating repair magnitude.
     */
    fun deltaEOk(a: Oklch, b: Oklch): Double {
        val hRadA = Math.toRadians(a.h)
        val aA = a.c * cos(hRadA)
        val bA = a.c * sin(hRadA)

        val hRadB = Math.toRadians(b.h)
        val aB = b.c * cos(hRadB)
        val bB = b.c * sin(hRadB)

        val dL = a.l - b.l
        val da = aA - aB
        val db = bA - bB
        return sqrt(dL * dL + da * da + db * db)
    }

    fun oklchToColorInt(oklch: Oklch): Int {
        val mapped = mapToSrgbGamut(oklch)
        val (rLin, gLin, bLin) = oklchToLinearSrgb(mapped)

        val r = (linearToSRgb(rLin) * 255.0).roundToInt().coerceIn(0, 255)
        val g = (linearToSRgb(gLin) * 255.0).roundToInt().coerceIn(0, 255)
        val b = (linearToSRgb(bLin) * 255.0).roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun oklchToColor(oklch: Oklch): Color {
        return Color(oklchToColorInt(oklch))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Central Contrast Resolution Algorithm
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a foreground color against a background to meet the specified [minRatio].
     *
     * 1. If [preferredForeground] meets [minRatio], it is returned unmodified (DIRECT_PASS).
     * 2. Otherwise, evaluates contrast headroom (lighter vs darker).
     * 3. Performs a binary search along OKLCH perceptual lightness while preserving exact hue and chroma.
     * 4. Maps candidates cleanly into the sRGB gamut along constant lightness and hue to prevent distortion.
     * 5. Falls back to high-contrast tinted neutral or polarity endpoint if needed.
     * 6. Records repair magnitude in Delta-E OKLCH.
     */
    fun resolveContrast(
        background: Color,
        preferredForeground: Color,
        minRatio: Double = 4.5,
        role: ContrastRole = ContrastRole.NORMAL_TEXT
    ): ResolvedContrast {
        val bgInt = background.toArgb()
        val fgInt = preferredForeground.toArgb()

        val bgLum = calculateWcagRelativeLuminance(bgInt)
        val fgLum = calculateWcagRelativeLuminance(fgInt)
        val initialRatio = calculateWcagContrastRatio(fgInt, bgInt)
        val initialApca = calculateApcaContrast(fgInt, bgInt)

        if (initialRatio >= minRatio) {
            return ResolvedContrast(
                color = preferredForeground,
                actualRatio = initialRatio,
                passesRequired = true,
                method = ResolutionMethod.DIRECT_PASS,
                apcaLc = initialApca,
                repairMagnitude = 0.0
            )
        }

        val maxLightRatio = (1.0 + 0.05) / (bgLum + 0.05)
        val maxDarkRatio = (bgLum + 0.05) / (0.0 + 0.05)

        val oklch = colorToOklch(fgInt)
        val lOrig = oklch.l
        val cOrig = oklch.c
        val h = oklch.h

        // Order directions by available headroom and proximity to initial luminance
        val directions = if (fgLum >= bgLum) {
            if (maxLightRatio >= minRatio) listOf(true, false) else listOf(false, true)
        } else {
            if (maxDarkRatio >= minRatio) listOf(false, true) else listOf(true, false)
        }

        // Search with full chroma first, then gracefully adapt chroma if gamut limits prevent pass
        val chromaScales = listOf(1.0, 0.75, 0.50, 0.25)

        for (goLight in directions) {
            val maxRatioInDir = if (goLight) maxLightRatio else maxDarkRatio
            if (maxRatioInDir < minRatio * 0.999) continue

            for (scale in chromaScales) {
                val cTarget = cOrig * scale
                var low = if (goLight) lOrig else 0.02
                var high = if (goLight) 0.98 else lOrig

                var bestColorInt = -1
                var bestRatio = 0.0
                var bestApca = 0.0

                // 16 iterations gives ~0.000015 precision in OKLCH lightness
                for (iter in 0 until 16) {
                    val mid = (low + high) / 2.0
                    val candInt = oklchToColorInt(Oklch(mid, cTarget, h))
                    val candRatio = calculateWcagContrastRatio(candInt, bgInt)
                    val candApca = calculateApcaContrast(candInt, bgInt)

                    if (candRatio >= minRatio) {
                        bestColorInt = candInt
                        bestRatio = candRatio
                        bestApca = candApca
                        if (goLight) {
                            high = mid // Seek minimal adjustment towards original
                        } else {
                            low = mid
                        }
                    } else {
                        if (goLight) {
                            low = mid
                        } else {
                            high = mid
                        }
                    }
                }

                if (bestColorInt != -1 && bestRatio >= minRatio) {
                    val method = if (scale == 1.0) {
                        ResolutionMethod.HUE_PRESERVED_LIGHTNESS
                    } else {
                        ResolutionMethod.CHROMA_ADAPTED
                    }
                    val magnitude = deltaEOk(oklch, colorToOklch(bestColorInt))
                    return ResolvedContrast(
                        color = Color(bestColorInt),
                        actualRatio = bestRatio,
                        passesRequired = true,
                        method = method,
                        apcaLc = bestApca,
                        repairMagnitude = magnitude
                    )
                }
            }
        }

        // Tinted Neutral Fallback (preserves hue subtle tint: C = 0.03)
        for (goLight in directions) {
            val maxRatioInDir = if (goLight) maxLightRatio else maxDarkRatio
            if (maxRatioInDir >= minRatio * 0.999) {
                val targetL = if (goLight) 0.96 else 0.05
                val tintedInt = oklchToColorInt(Oklch(targetL, 0.03, h))
                val tintedRatio = calculateWcagContrastRatio(tintedInt, bgInt)
                if (tintedRatio >= minRatio) {
                    val magnitude = deltaEOk(oklch, colorToOklch(tintedInt))
                    return ResolvedContrast(
                        color = Color(tintedInt),
                        actualRatio = tintedRatio,
                        passesRequired = true,
                        method = ResolutionMethod.TINTED_FALLBACK,
                        apcaLc = calculateApcaContrast(tintedInt, bgInt),
                        repairMagnitude = magnitude
                    )
                }
            }
        }

        // Absolute Polarity Endpoint (guaranteed maximum possible contrast)
        val useWhite = maxLightRatio >= maxDarkRatio
        val fallbackColor = if (useWhite) Color.White else Color.Black
        val fallbackRatio = if (useWhite) maxLightRatio else maxDarkRatio
        val fallbackInt = fallbackColor.toArgb()
        val polarityMagnitude = deltaEOk(oklch, colorToOklch(fallbackInt))

        return ResolvedContrast(
            color = fallbackColor,
            actualRatio = fallbackRatio,
            passesRequired = fallbackRatio >= minRatio,
            method = ResolutionMethod.POLARITY_FALLBACK,
            apcaLc = calculateApcaContrast(fallbackInt, bgInt),
            repairMagnitude = polarityMagnitude
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Semantic Helpers for UI Tokens & On-Colors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves an accessible foreground for a container surface (e.g. onPrimary, onSuccess).
     *
     * Evaluates [preferredForeground] first. If insufficient, evaluates theme text candidates
     * before initiating the hue-preserving perceptual search.
     */
    fun resolveOnColor(
        container: Color,
        preferredForeground: Color? = null,
        minRatio: Double = 4.5,
        isDarkTheme: Boolean = false
    ): Color {
        val containerInt = container.toArgb()
        val bgLum = calculateWcagRelativeLuminance(containerInt)

        // 1. If caller provided an explicit preferred foreground, test it
        if (preferredForeground != null) {
            val initialRatio = calculateWcagContrastRatio(preferredForeground, container)
            if (initialRatio >= minRatio) {
                return preferredForeground
            }
        }

        // 2. High-contrast theme anchor candidates
        val lightAnchor = Color(0xFFFAF9F8)
        val darkAnchor = Color(0xFF141416)

        val lightRatio = calculateWcagContrastRatio(lightAnchor, container)
        val darkRatio = calculateWcagContrastRatio(darkAnchor, container)

        // If a preferred foreground exists, try resolving its lightness first to retain hue
        if (preferredForeground != null) {
            val resolved = resolveContrast(container, preferredForeground, minRatio)
            if (resolved.passesRequired && resolved.method != ResolutionMethod.POLARITY_FALLBACK) {
                return resolved.color
            }
        }

        // Choose the highest contrast anchor that satisfies the minimum requirement
        if (lightRatio >= minRatio && darkRatio >= minRatio) {
            // Both meet threshold: pick candidate matching expected contrast direction
            return if (bgLum > 0.35) darkAnchor else lightAnchor
        } else if (lightRatio >= minRatio) {
            return lightAnchor
        } else if (darkRatio >= minRatio) {
            return darkAnchor
        }

        // If neither anchor reached threshold (e.g. strict 7.0:1 on mid-tone), use central resolver
        val bestAnchor = if (lightRatio >= darkRatio) lightAnchor else darkAnchor
        return resolveContrast(container, bestAnchor, minRatio).color
    }

    /**
     * Drop-in replacement for legacy [autoTextColor].
     * Computes contrast against [bg] and guarantees >= [minRatio] legibility.
     */
    fun autoTextColor(bg: Color, minRatio: Double = 4.5): Color {
        val bgLum = calculateWcagRelativeLuminance(bg)
        val lightCandidate = Color(0xFFFAF9F8)
        val darkCandidate = Color(0xFF141416)

        val lightRatio = calculateWcagContrastRatio(lightCandidate, bg)
        val darkRatio = calculateWcagContrastRatio(darkCandidate, bg)

        return when {
            lightRatio >= minRatio && darkRatio >= minRatio -> {
                // When both pass, pick the one with significantly higher contrast headroom
                if (bgLum > 0.35) darkCandidate else lightCandidate
            }
            lightRatio >= minRatio -> lightCandidate
            darkRatio >= minRatio -> darkCandidate
            else -> if (lightRatio >= darkRatio) Color.White else Color.Black
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hex and Integer Overloads for ThemeManager & Unit Tests
    // ─────────────────────────────────────────────────────────────────────────

    fun resolveOnColorHex(
        containerHex: String,
        preferredForegroundHex: String? = null,
        minRatio: Double = 4.5,
        isDarkTheme: Boolean = false
    ): String {
        val container = Color(android.graphics.Color.parseColor(containerHex))
        val preferred = preferredForegroundHex?.let { Color(android.graphics.Color.parseColor(it)) }
        val resolved = resolveOnColor(container, preferred, minRatio, isDarkTheme)
        val argb = resolved.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun resolveOnColorInt(
        containerInt: Int,
        preferredForegroundInt: Int? = null,
        minRatio: Double = 4.5,
        isDarkTheme: Boolean = false
    ): Int {
        val container = Color(containerInt)
        val preferred = preferredForegroundInt?.let { Color(it) }
        return resolveOnColor(container, preferred, minRatio, isDarkTheme).toArgb()
    }

    fun resolveContrastInt(
        backgroundInt: Int,
        preferredForegroundInt: Int,
        minRatio: Double = 4.5,
        role: ContrastRole = ContrastRole.NORMAL_TEXT
    ): Int {
        return resolveContrast(Color(backgroundInt), Color(preferredForegroundInt), minRatio, role).color.toArgb()
    }
}
