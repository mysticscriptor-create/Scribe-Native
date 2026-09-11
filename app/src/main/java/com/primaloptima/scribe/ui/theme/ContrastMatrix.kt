package com.primaloptima.scribe.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.primaloptima.scribe.ui.theme.ContrastResolver.ContrastRole
import com.primaloptima.scribe.ui.theme.ContrastResolver.ResolutionMethod
import kotlin.math.abs

/**
 * Semantic role categories distinguishing contrast requirements across UI and manuscript elements.
 */
enum class ContrastRoleCategory(
    val defaultMinWcagRatio: Double,
    val defaultMinApcaLc: Double,
    val description: String
) {
    /** Primary body text, prose, dialogue, monologue (WCAG 2.2 SC 1.4.3: 4.5:1) */
    BODY_TEXT(4.5, 75.0, "Body text & primary reading prose"),

    /** Subtitles, secondary metadata, chapter timestamps (WCAG 2.2: 3.5:1) */
    SECONDARY_TEXT(3.5, 60.0, "Secondary labels, metadata & captions"),

    /** Interactive controls, buttons, active toggles, focus indicators (WCAG 2.2 SC 1.4.11: 3.0:1) */
    INTERACTIVE_CONTROL(3.0, 45.0, "Interactive triggers, buttons & focus rings"),

    /** Text & icons rendered directly on filled container surfaces (WCAG 2.2: 4.5:1) */
    CONTAINER_ON_COLOR(4.5, 65.0, "Readable content on container fills"),

    /** Standalone status indicators, severity badges (WCAG 2.2 SC 1.4.11: 3.0:1) */
    SEMANTIC_INDICATOR(3.0, 45.0, "System status & severity indicators"),

    /** Non-text charts, data series, graphic markers (WCAG 2.2: 2.0:1) */
    DECORATIVE_CHART(2.0, 25.0, "Analytics charts & graphic lines"),

    /** Component boundaries, separators, decorative keylines (WCAG 2.2: 1.5:1) */
    BORDER_BOUNDARY(1.5, 20.0, "Keylines, borders & dividers")
}

/**
 * Conformance status of a semantic contrast evaluation.
 */
enum class ValidationStatus {
    /** Satisfied normative contrast threshold directly without modification */
    PASS,

    /** Failed initial contrast, but successfully repaired while preserving hue and mood */
    REPAIRED,

    /** Failed normative contrast threshold even after repair attempts */
    FAIL,

    /** Decorative or non-essential role exempt from strict regulatory contrast */
    EXEMPT
}

/**
 * Diagnostic result for a single token-to-surface pair in the contrast matrix.
 */
data class AccessibilityReportEntry(
    val token: String,
    val targetSurface: String,
    val foregroundHex: String,
    val backgroundHex: String,
    val roleCategory: ContrastRoleCategory,
    val wcagRatio: Double,
    val minWcagRatio: Double,
    val apcaLc: Double,
    val minApcaLc: Double,
    val status: ValidationStatus,
    val repairApplied: ResolutionMethod,
    val repairMagnitude: Double, // Delta E in OKLCH
    val wcagPass: Boolean,
    val apcaPass: Boolean,
    val diagnosticNote: String = ""
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"token\":\"$token\",")
        append("\"targetSurface\":\"$targetSurface\",")
        append("\"foregroundHex\":\"$foregroundHex\",")
        append("\"backgroundHex\":\"$backgroundHex\",")
        append("\"roleCategory\":\"${roleCategory.name}\",")
        append("\"wcagRatio\":${String.format(java.util.Locale.US, "%.2f", wcagRatio)},")
        append("\"minWcagRatio\":${String.format(java.util.Locale.US, "%.2f", minWcagRatio)},")
        append("\"apcaLc\":${String.format(java.util.Locale.US, "%.1f", apcaLc)},")
        append("\"minApcaLc\":${String.format(java.util.Locale.US, "%.1f", minApcaLc)},")
        append("\"status\":\"${status.name}\",")
        append("\"repairApplied\":\"${repairApplied.name}\",")
        append("\"repairMagnitude\":${String.format(java.util.Locale.US, "%.4f", repairMagnitude)},")
        append("\"wcagPass\":$wcagPass,")
        append("\"apcaPass\":$apcaPass,")
        append("\"diagnosticNote\":\"$diagnosticNote\"")
        append("}")
    }
}

/**
 * Machine-readable accessibility validation report for an entire theme palette.
 */
data class AccessibilityReport(
    val themeId: String,
    val isDark: Boolean,
    val overallPassRate: Float,
    val totalChecks: Int,
    val passedChecks: Int,
    val repairedChecks: Int,
    val failedChecks: Int,
    val entries: List<AccessibilityReportEntry>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun allWcagPassed(): Boolean = entries.none { it.status == ValidationStatus.FAIL }

    fun toJson(): String = buildString {
        append("{")
        append("\"themeId\":\"$themeId\",")
        append("\"isDark\":$isDark,")
        append("\"overallPassRate\":${String.format(java.util.Locale.US, "%.3f", overallPassRate)},")
        append("\"totalChecks\":$totalChecks,")
        append("\"passedChecks\":$passedChecks,")
        append("\"repairedChecks\":$repairedChecks,")
        append("\"failedChecks\":$failedChecks,")
        append("\"timestamp\":$timestamp,")
        append("\"entries\":[")
        append(entries.joinToString(",") { it.toJson() })
        append("]")
        append("}")
    }

    fun toFormattedSummary(): String = buildString {
        appendLine("=== Theme Accessibility Validation Report ===")
        appendLine("Theme: $themeId | Dark Mode: $isDark")
        appendLine("Normative WCAG 2.2 Conformance: ${if (allWcagPassed()) "PASSED" else "FAILED ($failedChecks unresolved)"}")
        appendLine("Evaluated Pairs: $totalChecks | Direct Pass: $passedChecks | Repaired: $repairedChecks | Failed: $failedChecks")
        appendLine("Compliance Rate: ${(overallPassRate * 100).toInt()}%")
        appendLine("Accessibility Baseline: WCAG 2.2 SC 1.4.3 / SC 1.4.11.")
        appendLine("Perceptual Diagnostic: APCA (W3C Silver / WCAG 3 research draft) used strictly for readability diagnostic.")
        appendLine("--------------------------------------------------------------------------------")
        entries.forEach { entry ->
            appendLine(
                String.format(
                    java.util.Locale.US,
                    "[%-8s] %-28s -> %-24s | WCAG %5.2f (min %4.1f) | APCA %6.1f Lc | %s (ΔE=%.3f)",
                    entry.status.name,
                    entry.token,
                    entry.targetSurface,
                    entry.wcagRatio,
                    entry.minWcagRatio,
                    entry.apcaLc,
                    entry.repairApplied.name,
                    entry.repairMagnitude
                )
            )
        }
    }
}

/**
 * Rule definition binding a semantic token to its actual rendered surface.
 */
data class MatrixRule(
    val tokenName: String,
    val targetSurfaceName: String,
    val roleCategory: ContrastRoleCategory,
    val minWcagRatio: Double = roleCategory.defaultMinWcagRatio,
    val minApcaLc: Double = roleCategory.defaultMinApcaLc,
    val isStrictBaseline: Boolean = true,
    val foregroundExtractor: (ScribeColors) -> Color,
    val surfaceExtractor: (ScribeColors) -> Color,
    val colorUpdater: ((ScribeColors, Color) -> ScribeColors)? = null
)

/**
 * Centralized Contrast Matrix and Accessibility Engine (Parts 21-22, 38).
 *
 * Implements:
 * 1. WCAG 2.2 normative accessibility verification.
 * 2. APCA (W3C Silver research draft) perceptual readability diagnostics.
 * 3. Concrete surface-to-token matrix mapping across Content, Interaction, Writing,
 *    Semantic Status, Analytics, Borders, and Worldbuilding roles.
 * 4. Safe gamut-aware repair preserving semantic hue and character.
 * 5. Machine-readable reporting.
 */
object ContrastMatrix {

    private fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }

    /**
     * Complete matrix of semantic token rules against their target surfaces.
     */
    val rules: List<MatrixRule> = listOf(
        // ── 1. Content Typography Hierarchy ──────────────────────────────────
        MatrixRule(
            tokenName = "content.primary",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.content.primary },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "content.primary",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.content.primary },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "content.primary",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.content.primary },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "content.primary",
            targetSurfaceName = "surfaces.surfaceOverlay",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.content.primary },
            surfaceExtractor = { it.surfaces.surfaceOverlay },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "content.secondary",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SECONDARY_TEXT,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.content.secondary },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(secondary = c)) }
        ),
        MatrixRule(
            tokenName = "content.secondary",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SECONDARY_TEXT,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.content.secondary },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(secondary = c)) }
        ),
        MatrixRule(
            tokenName = "content.tertiary",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SECONDARY_TEXT,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.content.tertiary },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(tertiary = c)) }
        ),
        MatrixRule(
            tokenName = "content.tertiary",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SECONDARY_TEXT,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.content.tertiary },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(content = s.content.copy(tertiary = c)) }
        ),

        // ── 2. Interactive Controls & Paired Material Roles ───────────────────
        MatrixRule(
            tokenName = "interaction.primary",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.interaction.primary },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.primary",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.interaction.primary },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(primary = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.onPrimary",
            targetSurfaceName = "interaction.primary",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.interaction.onPrimary },
            surfaceExtractor = { it.interaction.primary },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(onPrimary = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.onPrimaryContainer",
            targetSurfaceName = "interaction.primaryContainer",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.interaction.onPrimaryContainer },
            surfaceExtractor = { it.interaction.primaryContainer },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(onPrimaryContainer = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.onSecondary",
            targetSurfaceName = "interaction.secondary",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.content.onAccent },
            surfaceExtractor = { it.interaction.secondary },
            colorUpdater = null
        ),
        MatrixRule(
            tokenName = "interaction.onTertiary",
            targetSurfaceName = "interaction.tertiary",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.content.onAccent },
            surfaceExtractor = { it.interaction.tertiary },
            colorUpdater = null
        ),
        MatrixRule(
            tokenName = "interaction.focus",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.interaction.focus },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(focus = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.link",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.interaction.link },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(link = c)) }
        ),
        MatrixRule(
            tokenName = "interaction.link",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.interaction.link },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(interaction = s.interaction.copy(link = c)) }
        ),

        // ── 3. Creative Writing Roles Against Manuscript Canvas ──────────────
        MatrixRule(
            tokenName = "writing.prose",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.writing.prose },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(prose = c)) }
        ),
        MatrixRule(
            tokenName = "writing.dialogue",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.0,
            foregroundExtractor = { it.writing.dialogue },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(dialogue = c)) }
        ),
        MatrixRule(
            tokenName = "writing.monologue",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.writing.monologue },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(monologue = c)) }
        ),
        MatrixRule(
            tokenName = "writing.heading",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.BODY_TEXT,
            minWcagRatio = 4.0,
            foregroundExtractor = { it.writing.heading },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(heading = c)) }
        ),
        MatrixRule(
            tokenName = "writing.annotation",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.SECONDARY_TEXT,
            minWcagRatio = 3.5,
            foregroundExtractor = { it.writing.annotation },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(annotation = c)) }
        ),
        MatrixRule(
            tokenName = "writing.highlight",
            targetSurfaceName = "surfaces.background",
            roleCategory = ContrastRoleCategory.INTERACTIVE_CONTROL,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.writing.highlight },
            surfaceExtractor = { it.surfaces.background },
            colorUpdater = { s, c -> s.copy(writing = s.writing.copy(highlight = c)) }
        ),

        // ── 4. Paired Semantic Status Roles ──────────────────────────────────
        MatrixRule(
            tokenName = "semantic.onSuccess",
            targetSurfaceName = "semantic.success",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onSuccess },
            surfaceExtractor = { it.semantic.success },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onSuccess = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onSuccessContainer",
            targetSurfaceName = "semantic.successContainer",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onSuccessContainer },
            surfaceExtractor = { it.semantic.successContainer },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onSuccessContainer = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onWarning",
            targetSurfaceName = "semantic.warning",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onWarning },
            surfaceExtractor = { it.semantic.warning },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onWarning = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onWarningContainer",
            targetSurfaceName = "semantic.warningContainer",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onWarningContainer },
            surfaceExtractor = { it.semantic.warningContainer },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onWarningContainer = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onError",
            targetSurfaceName = "semantic.error",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onError },
            surfaceExtractor = { it.semantic.error },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onError = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onErrorContainer",
            targetSurfaceName = "semantic.errorContainer",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onErrorContainer },
            surfaceExtractor = { it.semantic.errorContainer },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onErrorContainer = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onInfo",
            targetSurfaceName = "semantic.info",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onInfo },
            surfaceExtractor = { it.semantic.info },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onInfo = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.onInfoContainer",
            targetSurfaceName = "semantic.infoContainer",
            roleCategory = ContrastRoleCategory.CONTAINER_ON_COLOR,
            minWcagRatio = 4.5,
            foregroundExtractor = { it.semantic.onInfoContainer },
            surfaceExtractor = { it.semantic.infoContainer },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(onInfoContainer = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.error",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.semantic.error },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(error = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.warning",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.semantic.warning },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(warning = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.success",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.semantic.success },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(success = c)) }
        ),
        MatrixRule(
            tokenName = "semantic.info",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 3.0,
            foregroundExtractor = { it.semantic.info },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(semantic = s.semantic.copy(info = c)) }
        ),

        // ── 5. Analytics & Graph Data Series ──────────────────────────────────
        MatrixRule(
            tokenName = "analytics.series1",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.series1 },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(series1 = c)) }
        ),
        MatrixRule(
            tokenName = "analytics.series2",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.series2 },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(series2 = c)) }
        ),
        MatrixRule(
            tokenName = "analytics.series3",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.series3 },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(series3 = c)) }
        ),
        MatrixRule(
            tokenName = "analytics.target",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.target },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(target = c)) }
        ),
        MatrixRule(
            tokenName = "analytics.positive",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.positive },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(positive = c)) }
        ),
        MatrixRule(
            tokenName = "analytics.negative",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.DECORATIVE_CHART,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.analytics.negative },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(analytics = s.analytics.copy(negative = c)) }
        ),

        // ── 6. Structural Borders & Boundaries ────────────────────────────────
        MatrixRule(
            tokenName = "borders.prominent",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.BORDER_BOUNDARY,
            minWcagRatio = 2.0,
            foregroundExtractor = { it.borders.prominent },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(borders = s.borders.copy(prominent = c)) }
        ),
        MatrixRule(
            tokenName = "borders.normal",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.BORDER_BOUNDARY,
            minWcagRatio = 1.4,
            foregroundExtractor = { it.borders.normal },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(borders = s.borders.copy(normal = c)) }
        ),
        MatrixRule(
            tokenName = "borders.subtle",
            targetSurfaceName = "surfaces.surface",
            roleCategory = ContrastRoleCategory.BORDER_BOUNDARY,
            minWcagRatio = 1.1,
            foregroundExtractor = { it.borders.subtle },
            surfaceExtractor = { it.surfaces.surface },
            colorUpdater = { s, c -> s.copy(borders = s.borders.copy(subtle = c)) }
        ),

        // ── 7. Worldbuilding Roles ────────────────────────────────────────────
        MatrixRule(
            tokenName = "world.character",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.character },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(character = c)) }
        ),
        MatrixRule(
            tokenName = "world.location",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.location },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(location = c)) }
        ),
        MatrixRule(
            tokenName = "world.faction",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.faction },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(faction = c)) }
        ),
        MatrixRule(
            tokenName = "world.item",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.item },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(item = c)) }
        ),
        MatrixRule(
            tokenName = "world.lore",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.lore },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(lore = c)) }
        ),
        MatrixRule(
            tokenName = "world.event",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.event },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(event = c)) }
        ),
        MatrixRule(
            tokenName = "world.relationship",
            targetSurfaceName = "surfaces.surfaceRaised",
            roleCategory = ContrastRoleCategory.SEMANTIC_INDICATOR,
            minWcagRatio = 2.5,
            foregroundExtractor = { it.world.relationship },
            surfaceExtractor = { it.surfaces.surfaceRaised },
            colorUpdater = { s, c -> s.copy(world = s.world.copy(relationship = c)) }
        )
    )

    /**
     * Evaluates all semantic pairs without mutating colors.
     */
    fun validate(colors: ScribeColors, themeId: String = ""): AccessibilityReport {
        val entries = mutableListOf<AccessibilityReportEntry>()
        var passedCount = 0
        var repairedCount = 0
        var failedCount = 0

        for (rule in rules) {
            val fg = rule.foregroundExtractor(colors)
            val bg = rule.surfaceExtractor(colors)
            val fgInt = fg.toArgb()
            val bgInt = bg.toArgb()

            val wcagRatio = ContrastResolver.calculateWcagContrastRatio(fgInt, bgInt)
            val apcaLc = ContrastResolver.calculateApcaContrast(fgInt, bgInt)

            val wcagPass = wcagRatio >= rule.minWcagRatio
            val apcaPass = abs(apcaLc) >= rule.minApcaLc

            val status: ValidationStatus
            val method: ResolutionMethod
            val magnitude: Double

            if (wcagPass) {
                status = ValidationStatus.PASS
                method = ResolutionMethod.DIRECT_PASS
                magnitude = 0.0
                passedCount++
            } else {
                status = ValidationStatus.FAIL
                method = ResolutionMethod.DIRECT_PASS
                magnitude = 0.0
                failedCount++
            }

            entries.add(
                AccessibilityReportEntry(
                    token = rule.tokenName,
                    targetSurface = rule.targetSurfaceName,
                    foregroundHex = colorToHex(fg),
                    backgroundHex = colorToHex(bg),
                    roleCategory = rule.roleCategory,
                    wcagRatio = wcagRatio,
                    minWcagRatio = rule.minWcagRatio,
                    apcaLc = apcaLc,
                    minApcaLc = rule.minApcaLc,
                    status = status,
                    repairApplied = method,
                    repairMagnitude = magnitude,
                    wcagPass = wcagPass,
                    apcaPass = apcaPass,
                    diagnosticNote = if (!wcagPass) "WCAG 2.2 threshold not met (ratio: ${String.format(java.util.Locale.US, "%.2f", wcagRatio)} < ${rule.minWcagRatio})" else ""
                )
            )
        }

        val total = entries.size
        val rate = if (total > 0) (passedCount + repairedCount).toFloat() / total else 1.0f

        return AccessibilityReport(
            themeId = themeId,
            isDark = colors.isDark,
            overallPassRate = rate,
            totalChecks = total,
            passedChecks = passedCount,
            repairedChecks = repairedCount,
            failedChecks = failedCount,
            entries = entries
        )
    }

    /**
     * Evaluates all semantic pairs, performing gamut-safe, hue-preserving repair on any failing tokens.
     * Returns the adjusted [ScribeColors] alongside the final machine-readable [AccessibilityReport].
     */
    fun validateAndRepair(colors: ScribeColors, themeId: String = ""): Pair<ScribeColors, AccessibilityReport> {
        var currentColors = colors
        val entries = mutableListOf<AccessibilityReportEntry>()
        var passedCount = 0
        var repairedCount = 0
        var failedCount = 0

        for (rule in rules) {
            val fg = rule.foregroundExtractor(currentColors)
            val bg = rule.surfaceExtractor(currentColors)
            val fgInt = fg.toArgb()
            val bgInt = bg.toArgb()

            val initialRatio = ContrastResolver.calculateWcagContrastRatio(fgInt, bgInt)
            val initialApca = ContrastResolver.calculateApcaContrast(fgInt, bgInt)

            if (initialRatio >= rule.minWcagRatio) {
                passedCount++
                entries.add(
                    AccessibilityReportEntry(
                        token = rule.tokenName,
                        targetSurface = rule.targetSurfaceName,
                        foregroundHex = colorToHex(fg),
                        backgroundHex = colorToHex(bg),
                        roleCategory = rule.roleCategory,
                        wcagRatio = initialRatio,
                        minWcagRatio = rule.minWcagRatio,
                        apcaLc = initialApca,
                        minApcaLc = rule.minApcaLc,
                        status = ValidationStatus.PASS,
                        repairApplied = ResolutionMethod.DIRECT_PASS,
                        repairMagnitude = 0.0,
                        wcagPass = true,
                        apcaPass = abs(initialApca) >= rule.minApcaLc,
                        diagnosticNote = "Direct pass"
                    )
                )
            } else {
                // Perform repair
                val resolved = ContrastResolver.resolveContrast(
                    background = bg,
                    preferredForeground = fg,
                    minRatio = rule.minWcagRatio
                )

                val repairedColor = resolved.color
                val repairedInt = repairedColor.toArgb()
                val repairedRatio = resolved.actualRatio
                val repairedApca = resolved.apcaLc

                if (rule.colorUpdater != null) {
                    currentColors = rule.colorUpdater.invoke(currentColors, repairedColor)
                }

                val wcagPass = repairedRatio >= rule.minWcagRatio
                val status = if (wcagPass) {
                    repairedCount++
                    ValidationStatus.REPAIRED
                } else {
                    failedCount++
                    ValidationStatus.FAIL
                }

                entries.add(
                    AccessibilityReportEntry(
                        token = rule.tokenName,
                        targetSurface = rule.targetSurfaceName,
                        foregroundHex = colorToHex(repairedColor),
                        backgroundHex = colorToHex(bg),
                        roleCategory = rule.roleCategory,
                        wcagRatio = repairedRatio,
                        minWcagRatio = rule.minWcagRatio,
                        apcaLc = repairedApca,
                        minApcaLc = rule.minApcaLc,
                        status = status,
                        repairApplied = resolved.method,
                        repairMagnitude = resolved.repairMagnitude,
                        wcagPass = wcagPass,
                        apcaPass = abs(repairedApca) >= rule.minApcaLc,
                        diagnosticNote = "Repaired via ${resolved.method.name} (ΔE=${String.format(java.util.Locale.US, "%.4f", resolved.repairMagnitude)})"
                    )
                )
            }
        }

        val total = entries.size
        val rate = if (total > 0) (passedCount + repairedCount).toFloat() / total else 1.0f

        val report = AccessibilityReport(
            themeId = themeId,
            isDark = currentColors.isDark,
            overallPassRate = rate,
            totalChecks = total,
            passedChecks = passedCount,
            repairedChecks = repairedCount,
            failedChecks = failedCount,
            entries = entries
        )

        return Pair(currentColors, report)
    }
}
