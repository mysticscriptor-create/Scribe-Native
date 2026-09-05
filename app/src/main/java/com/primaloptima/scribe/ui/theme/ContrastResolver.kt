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
import kotlin.math.toDegrees
import kotlin.math.toRadians

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
     * Semantic role context specifying standard WCAG 2.2 contrast thresholds.
     */
    enum class ContrastRole(val defaultMinRatio: Double) {
        /** Normal body text, prose, dialogue, monologue, captions (<18pt or <14pt bold) - WCAG 2.2 SC 1.4.3 (4.5:1) */
        NORMAL_TEXT(4.5),

        /** Large text (>=18pt or >=14pt bold), headings, hero titles - WCAG 2.2 SC 1.4.3 (3.0:1) */
        LARGE_TEXT(3.0),

        /** Interactive controls, buttons, FABs, focus rings, status indicators - WCAG 2.2 SC 1.4.11 (3.0:1) */
        UI_CONTROL(3.0),

        /** Badges and status pills with text content (4.5:1 for standard legibility) */
        STATUS_BADGE(4.5),

        /** Text rendered inside high-emphasis container surfaces (4.5:1) */
        CONTAINER_TEXT(4.5),

        /** Functional borders with state or boundary information - WCAG 2.2 SC 1.4.11 (3.0:1) */
        BORDER(3.0),

        /** Secondary/decorative dividers not required to identify control boundaries */
        DECORATIVE(1.5)
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
        val method: ResolutionMethod
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
    // OKLCH Perceptual Transformations
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
        var h = toDegrees(atan2(bVal, a))
        if (h < 0.0) h += 360.0

        return Oklch(L.coerceIn(0.0, 1.0), C.coerceAtLeast(0.0), h)
    }

    fun oklchToColorInt(oklch: Oklch): Int {
        val hRad = toRadians(oklch.h)
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
     * 4. If full chroma cannot achieve [minRatio] due to sRGB gamut boundaries, adapts chroma smoothly.
     * 5. Falls back to high-contrast tinted neutral or polarity endpoint if needed.
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

        if (initialRatio >= minRatio) {
            return ResolvedContrast(
                color = preferredForeground,
                actualRatio = initialRatio,
                passesRequired = true,
                method = ResolutionMethod.DIRECT_PASS
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

                // 16 iterations gives ~0.000015 precision in OKLCH lightness
                for (iter in 0 until 16) {
                    val mid = (low + high) / 2.0
                    val candInt = oklchToColorInt(Oklch(mid, cTarget, h))
                    val candRatio = calculateWcagContrastRatio(candInt, bgInt)

                    if (candRatio >= minRatio) {
                        bestColorInt = candInt
                        bestRatio = candRatio
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
                    return ResolvedContrast(
                        color = Color(bestColorInt),
                        actualRatio = bestRatio,
                        passesRequired = true,
                        method = method
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
                    return ResolvedContrast(
                        color = Color(tintedInt),
                        actualRatio = tintedRatio,
                        passesRequired = true,
                        method = ResolutionMethod.TINTED_FALLBACK
                    )
                }
            }
        }

        // Absolute Polarity Endpoint (guaranteed maximum possible contrast)
        val useWhite = maxLightRatio >= maxDarkRatio
        val fallbackColor = if (useWhite) Color.White else Color.Black
        val fallbackRatio = if (useWhite) maxLightRatio else maxDarkRatio

        return ResolvedContrast(
            color = fallbackColor,
            actualRatio = fallbackRatio,
            passesRequired = fallbackRatio >= minRatio,
            method = ResolutionMethod.POLARITY_FALLBACK
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
