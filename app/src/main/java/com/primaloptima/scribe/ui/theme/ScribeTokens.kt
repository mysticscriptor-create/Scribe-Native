package com.primaloptima.scribe.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 1. SURFACES ─────────────────────────────────────────────────────────────
/**
 * Surface elevation tokens establishing the physical visual hierarchy.
 *
 * Contract:
 * - L0 [background]: Foundation canvas layer (75-85% neutral foundation).
 * - L1 [surfaceLowest]: Recessed split gutters, sunken panels, tool trays.
 * - L2 [surface]: Base windows, top app bars, primary navigation drawers.
 * - L3 [surfaceRaised]: Floating workbench cards, elevated dialogs, floating sheets.
 * - L4 [surfaceOverlay]: Popovers, dropdown menus, context menus, modal alerts.
 * - [surfaceSelected]: Active navigation tab fills, selected list item backgrounds.
 * - [surfacePressed]: Immediate touch ripple & active pressed state tint.
 *
 * Source: Derived mathematically in OKLCH space from `background` foundation source.
 * Consumers: Card backgrounds, modal containers, navigation rails, app bars.
 * Anti-Pattern: Never use surface tokens as text/icon foregrounds or border fills.
 */
@Immutable
data class SurfaceColors(
    val background: Color,      // L0: Canvas base background (75-85% neutral foundation)
    val surfaceLowest: Color,   // L1: Recessed gutters, split rails, sunken panels
    val surface: Color,         // L2: Base windows, top app bars, primary drawers
    val surfaceRaised: Color,   // L3: Floating cards, workbench cards, elevated panes
    val surfaceOverlay: Color,  // L4: Popovers, floating action menus, modal dialogs
    val surfaceSelected: Color, // Selected list items, active navigation tab fills
    val surfacePressed: Color   // Pressed/activated state tint
)

// ── 2. CONTENT ──────────────────────────────────────────────────────────────
/**
 * Typography and iconography content hierarchy tokens.
 *
 * Contract:
 * - [primary]: Main readable prose, high-emphasis text, primary titles (10-15% neutral).
 * - [secondary]: Supporting readable text, subtitles, metadata, secondary captions.
 * - [tertiary]: Low-priority metadata, inactive hints, subtle counters, timestamps.
 * - [disabled]: Genuinely disabled, unavailable text and inactive states.
 * - [onAccent]: High-contrast readable foreground rendered directly on primary accent surfaces.
 *
 * Source: Derived from `text` foundation source with controlled lightness and chroma curves.
 * Consumers: Text composables, iconography, status badges.
 * Anti-Pattern: Never use content.tertiary as a generic replacement for secondary labels.
 */
@Immutable
data class ContentColors(
    val primary: Color,         // Primary body prose, high-emphasis text & headings (10-15% neutral)
    val secondary: Color,       // Subtitles, metadata, secondary captions
    val tertiary: Color,        // Inactive hints, timestamps, subtle counters
    val disabled: Color,        // Disabled text & placeholder states
    val onAccent: Color         // Content rendered directly on primary accent surfaces (high-contrast white/black)
)

// ── 3. INTERACTION ──────────────────────────────────────────────────────────
/**
 * Interactive affordances, triggers, and state feedback tokens.
 *
 * Contract:
 * - [primary]: Primary interactive brand action/accent (FAB, primary buttons, active toggles).
 * - [primaryContainer]: Tonal container/fill supporting primary interaction.
 * - [onPrimary]: Readable foreground on primary interactive color.
 * - [onPrimaryContainer]: Readable foreground on primary container fill.
 * - [secondary]: Secondary interactive action (secondary buttons, chips, filters).
 * - [tertiary]: Tertiary interactive accent (special tool highlights, tertiary controls).
 * - [selection]: Text selection highlight & multi-item selection bounding box.
 * - [focus]: Keyboard navigation focus rings & high-contrast accessibility outlines.
 * - [link]: Hyperlinks, cross-document references, citation jumps.
 *
 * Source: Derived from `accent` foundation source in OKLCH perceptual space.
 * Consumers: Buttons, FABs, chips, checkboxes, switches, focus indicators.
 * Anti-Pattern: Never use interaction.primary as a universal background or decorative border.
 */
@Immutable
data class InteractionColors(
    val primary: Color,             // Primary interactive brand action/accent (5-8% accent)
    val primaryContainer: Color,    // Container background for primary actions
    val onPrimary: Color,           // Foreground on primary interactive color
    val onPrimaryContainer: Color,  // Foreground on primary container
    val secondary: Color,           // Secondary interactive accent
    val tertiary: Color,            // Tertiary interactive accent
    val selection: Color,           // Text selection highlight
    val focus: Color,               // Focus rings & keyboard navigation outlines
    val link: Color                 // Hyperlinks & cross-references
)

// ── 4. SEMANTIC STATUS ──────────────────────────────────────────────────────
/**
 * System status, feedback messages, and diagnostic severity indicators.
 *
 * Contract:
 * - [success]: Completed actions, confirmed save, sync success, positive health.
 * - [warning]: Non-blocking cautions, unsaved edits, rate limits, attention required.
 * - [error]: Critical failures, validation errors, destructive actions, offline alerts.
 * - [info]: Neutral guidance, system hints, informational banners.
 * - (*Container, on*): Paired background fills and high-contrast foregrounds.
 *
 * Source: Perceptually tuned status hues in OKLCH space, contrast-validated against surfaces.
 * Consumers: Snackbars, diagnostic pills, inline validation banners, toast alerts.
 * Anti-Pattern: Never use semantic status tokens for world entity types or editor highlighting.
 */
@Immutable
data class SemanticStatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

// ── 5. WRITING & PROSE LEXER ────────────────────────────────────────────────
/**
 * Editor syntax highlighting and creative writing structural tokens.
 *
 * Contract:
 * - [prose]: Primary narrative text & base paragraph foundation.
 * - [dialogue]: Spoken dialogue highlighting ("...", “...”).
 * - [monologue]: Internal monologue / thought text (‘...’).
 * - [heading]: Chapter / Scene title highlighting.
 * - [annotation]: Margin notes, inline editorial comments, critique flags.
 * - [highlight]: User search results & literary emphasis spans.
 *
 * Source: Editorial theme overrides or harmonic OKLCH derivatives.
 * Consumers: Sora CodeEditor syntax lexer, outline tree, manuscript view.
 * Anti-Pattern: Never force every writing role to have loud colors; preserve reading calm.
 */
@Immutable
data class WritingColors(
    val prose: Color,       // Primary narrative text / base prose foundation
    val dialogue: Color,    // Spoken dialogue highlighting ("...", “...”)
    val monologue: Color,   // Internal monologue / thought text (‘...’)
    val heading: Color,     // Chapter / Scene title highlighting
    val annotation: Color,  // Margin notes, inline editorial comments
    val highlight: Color    // User search & literary emphasis highlight
)

// ── 6. DATA & ANALYTICS ─────────────────────────────────────────────────────
/**
 * Data visualization, manuscript metrics, and pacing analysis tokens.
 *
 * Contract:
 * - [positive]: Metric positive trend, goal reached, velocity gain.
 * - [neutral]: Baseline metric, running average, unchanged pace.
 * - [negative]: Metric deficit, goal behind, velocity decline.
 * - [series1]: Chart series 1 identity (primary metric: e.g. daily word count).
 * - [series2]: Chart series 2 identity (secondary metric: e.g. reading time).
 * - [series3]: Chart series 3 identity (tertiary metric: e.g. vocabulary density).
 * - [target]: Target reference benchmark line, goal threshold marker.
 * - [warning]: Pacing / scene length imbalance alert indicator.
 *
 * Source: Independent analytical palette harmonized with theme foundations.
 * Consumers: Recharts/D3 stats, progress bars, pacing charts, streak meters.
 * Anti-Pattern: Never confuse trend status (positive/negative) with series identity (series1).
 */
@Immutable
data class AnalyticsColors(
    val positive: Color,    // Metric positive trend, goal achieved
    val neutral: Color,     // Baseline metric / average
    val negative: Color,    // Metric warning / decline
    val series1: Color,     // Chart series 1 (Primary metric)
    val series2: Color,     // Chart series 2 (Secondary metric)
    val series3: Color,     // Chart series 3 (Tertiary metric)
    val target: Color,      // Goal target line / gauge indicator
    val warning: Color      // Pacing / density flag warning
)

// ── 7. BORDERS ──────────────────────────────────────────────────────────────
/**
 * Structural boundaries, separators, and active focus boundaries.
 *
 * Contract:
 * - [subtle]: 1px quiet structural dividers, table rules, subtle card outlines (outlineVariant).
 * - [normal]: Standard input borders, card boundaries, container outlines (outline).
 * - [prominent]: Active focus rings, selected card keylines, high-emphasis borders.
 *
 * Source: Derived from background & accent in OKLCH space with explicit contrast deltas.
 * Consumers: Divider components, Card borders, OutlinedTextField borders, focus rings.
 * Anti-Pattern: Never map borders.subtle to an accent or prominent border color.
 */
@Immutable
data class BorderColors(
    val subtle: Color,      // 1px structural dividing lines & card outlines (outlineVariant)
    val normal: Color,      // Standard input borders & container boundaries (outline)
    val prominent: Color    // Active focus rings, keyline accents
)

// ── 8. WORLD / ENTITY TYPES ─────────────────────────────────────────────────
/**
 * Worldbuilding lore entity categories and manuscript index identities.
 *
 * Contract:
 * - [character]: Character entity tags, dialogue attribution avatars, dramatis personae.
 * - [location]: Setting & location entities, world map pins, atmospheric backdrops.
 * - [faction]: Factions, clans, guilds, political organizations.
 * - [item]: Physical artifacts, magic items, weapons, inventory props.
 * - [lore]: Lore documents, historical chronicles, magic systems, universe rules.
 * - [event]: Historical timeline events, plot milestones, narrative beats (NOT system errors!).
 * - [relationship]: Character dynamics, entity linkages, alliance arcs (NOT muted text!).
 *
 * Source: Independent harmonic entity palette derived to maintain distinct identities.
 * Consumers: World sheets, entity pill badges, timeline nodes, graph view connectors.
 * Anti-Pattern: Never alias world.event to error or world.relationship to mutedText.
 */
@Immutable
data class WorldEntityColors(
    val character: Color,   // Character entities & dialogue attribution
    val location: Color,    // Setting / Location entities
    val faction: Color,     // Factions, groups, organizations
    val item: Color,        // Items, artifacts, inventory
    val lore: Color,        // Lore documents, world rules
    val event: Color,       // Timeline events, plot milestones
    val relationship: Color // Entity connections & character arcs
)

// ── ScribeColors Master Token Object ─────────────────────────────────────────
@Immutable
data class ScribeColors(
    val surfaces: SurfaceColors,
    val content: ContentColors,
    val interaction: InteractionColors,
    val semantic: SemanticStatusColors,
    val writing: WritingColors,
    val analytics: AnalyticsColors,
    val borders: BorderColors,
    val world: WorldEntityColors,
    val isDark: Boolean
)

// ── Shapes ──────────────────────────────────────────────────────────────────
@Immutable
data class ScribeShapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(20.dp),
    val full: CornerBasedShape = RoundedCornerShape(50),

    // Semantic Component Shape Aliases
    val card: CornerBasedShape = RoundedCornerShape(12.dp),
    val cardNested: CornerBasedShape = RoundedCornerShape(8.dp),
    val button: CornerBasedShape = RoundedCornerShape(8.dp),
    val field: CornerBasedShape = RoundedCornerShape(8.dp),
    val chip: CornerBasedShape = RoundedCornerShape(50),
    val dialog: CornerBasedShape = RoundedCornerShape(16.dp),
    val bottomSheet: CornerBasedShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
    val fab: CornerBasedShape = RoundedCornerShape(16.dp)
)

// ── Spacing Scale ───────────────────────────────────────────────────────────
@Immutable
data class ScribeSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val huge: Dp = 48.dp
)

// ── Metrics & Spacing ───────────────────────────────────────────────────────
@Immutable
data class ScribeMetrics(
    val spacing: ScribeSpacing = ScribeSpacing(),
    val spaceNone: Dp = 0.dp,
    val spaceExtraSmall: Dp = 4.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 12.dp,
    val spaceNormal: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
    val spaceExtraLarge: Dp = 32.dp,
    val spaceHuge: Dp = 48.dp,

    // Border Widths
    val borderHairline: Dp = 1.dp,
    val borderThick: Dp = 2.dp,

    // Elevation & Blur
    val elevationNone: Dp = 0.dp,
    val elevationLow: Dp = 2.dp,
    val elevationMedium: Dp = 4.dp,
    val elevationHigh: Dp = 8.dp,
    val defaultBlurRadius: Dp = 15.dp
)

// ── Typography ──────────────────────────────────────────────────────────────
@Immutable
data class ScribeAppTypography(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    val bodySecondary: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    ),
    val statValue: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    val statLabel: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

@Immutable
data class ScribeEditorTypography(
    val prose: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    val dialogue: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    val monologue: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    val heading: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    val fontFamily: FontFamily = FontFamily.Default,
    val fontSize: Int = 17,
    val lineHeight: Float = 1.68f,
    val letterSpacing: Float = 0f,
    val paragraphSpacing: Int = 14,
    val textAlignment: String = "left"
)

@Immutable
data class ScribeTypography(
    // App UI semantic typography (cards, dialogs, drawers, stats, worldbuilding)
    val app: ScribeAppTypography = ScribeAppTypography(),
    // Editor typography (user-controlled settings for writing canvas)
    val editor: ScribeEditorTypography = ScribeEditorTypography(),

    // Direct semantic convenience accessors
    val display: TextStyle = app.display,
    val headline: TextStyle = app.headline,
    val title: TextStyle = app.title,
    val sectionTitle: TextStyle = app.sectionTitle,
    val body: TextStyle = app.body,
    val bodySecondary: TextStyle = app.bodySecondary,
    val label: TextStyle = app.label,
    val caption: TextStyle = app.caption,
    val statValue: TextStyle = app.statValue,
    val statLabel: TextStyle = app.statLabel,

    // Editor writing text styles
    val prose: TextStyle = editor.prose,
    val dialogue: TextStyle = editor.dialogue,
    val monologue: TextStyle = editor.monologue,
    val heading: TextStyle = editor.heading,

    // Compatibility accessors
    val displayLarge: TextStyle = app.display,
    val displayMedium: TextStyle = app.headline,
    val titleLarge: TextStyle = app.title,
    val titleMedium: TextStyle = app.sectionTitle,
    val titleSmall: TextStyle = app.label,
    val bodyLarge: TextStyle = app.body,
    val bodyMedium: TextStyle = app.bodySecondary,
    val bodySmall: TextStyle = app.caption,
    val labelLarge: TextStyle = app.label,
    val labelMedium: TextStyle = app.caption,
    val labelSmall: TextStyle = app.caption
)

// ── Composition Locals ───────────────────────────────────────────────────────
val LocalScribeColors = staticCompositionLocalOf<ScribeColors> {
    error("No ScribeColors provided! Ensure ScribeComposeTheme wraps the hierarchy.")
}

val LocalScribeShapes = staticCompositionLocalOf<ScribeShapes> {
    ScribeShapes()
}

val LocalScribeTypography = staticCompositionLocalOf<ScribeTypography> {
    ScribeTypography()
}

val LocalScribeMetrics = staticCompositionLocalOf<ScribeMetrics> {
    ScribeMetrics()
}

val LocalScribeSpacing = staticCompositionLocalOf<ScribeSpacing> {
    ScribeSpacing()
}

// ── Central ScribeTheme Accessor Object ──────────────────────────────────────
object ScribeTheme {
    val colors: ScribeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScribeColors.current

    val shapes: ScribeShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalScribeShapes.current

    val typography: ScribeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalScribeTypography.current

    val metrics: ScribeMetrics
        @Composable
        @ReadOnlyComposable
        get() = LocalScribeMetrics.current

    val spacing: ScribeSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalScribeSpacing.current
}
