package com.primaloptima.scribe.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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

/**
 * Centralized design tokens for Scribe's shape and corner geometry.
 *
 * Scribe's corner language follows a disciplined physical hierarchy:
 * - [None] (0.dp): Full-bleed surfaces (top bars, bottom navigation rails, manuscript canvas).
 * - [ExtraSmall] (4.dp): Fine accents (status indicators, slider tracks, progress bars).
 * - [Small] (8.dp): Compact interactive controls (standard buttons, text input fields, nested cards).
 * - [Medium] (12.dp): Tactile interactive elements (action tiles, search bars, secondary cards).
 * - [Large] (16.dp): Prominent floating controls (standard FABs, section cards, drawer panels).
 * - [ExtraLarge] (20.dp): Primary content cards (book cards, hero project cards, speed dial containers).
 * - [Full] (CircleShape): Organic pill badges, circular action buttons, floating chips.
 */
object ScribeShapeTokens {
    // ── Base Geometric Scale ────────────────────────────────────────────────
    val None: Shape = RectangleShape
    val ExtraSmall: CornerBasedShape = RoundedCornerShape(4.dp)
    val Small: CornerBasedShape = RoundedCornerShape(8.dp)
    val Medium: CornerBasedShape = RoundedCornerShape(12.dp)
    val Large: CornerBasedShape = RoundedCornerShape(16.dp)
    val ExtraLarge: CornerBasedShape = RoundedCornerShape(20.dp)
    val Full: CornerBasedShape = CircleShape
    val Pill: CornerBasedShape = Full

    // ── Raw Radii Dimensions ────────────────────────────────────────────────
    val RadiusExtraSmall: Dp = 4.dp
    val RadiusSmall: Dp = 8.dp
    val RadiusMedium: Dp = 12.dp
    val RadiusLarge: Dp = 16.dp
    val RadiusExtraLarge: Dp = 20.dp

    // ── Semantic Component Radii / Shapes ───────────────────────────────────
    val Card: CornerBasedShape = ExtraLarge                                    // 20.dp - Hero & Primary Content Cards
    val CardMedium: CornerBasedShape = Large                                   // 16.dp - Secondary Cards, Strip Rows
    val CardSmall: CornerBasedShape = Medium                                   // 12.dp - Action Tiles, Compact Cards
    val CardNested: CornerBasedShape = Small                                   // 8.dp  - Inner grouping cards, icon boxes
    val Button: CornerBasedShape = Small                                       // 8.dp  - Standard Buttons
    val ButtonSmall: CornerBasedShape = RoundedCornerShape(6.dp)               // 6.dp  - Toolbar / compact buttons
    val Field: CornerBasedShape = Small                                        // 8.dp  - Text input fields
    val SearchBar: CornerBasedShape = Medium                                   // 12.dp - Search boxes
    val Chip: CornerBasedShape = Full                                          // Pill  - Filter chips, status badges
    val ChipRect: CornerBasedShape = Small                                     // 8.dp  - Rectangular tag chips
    val Menu: CornerBasedShape = RoundedCornerShape(14.dp)                     // 14.dp - Dropdown & context menus
    val Dialog: CornerBasedShape = RoundedCornerShape(24.dp)                   // 24.dp - Dialog surfaces
    val BottomSheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val Fab: CornerBasedShape = Large                                          // 16.dp - Squircle FAB
    val FabSmall: CornerBasedShape = Medium                                    // 12.dp - Small FAB
    val SpeedDial: CornerBasedShape = ExtraLarge                               // 20.dp - Speed dial menu card
    val Handle: CornerBasedShape = RoundedCornerShape(2.dp)                    // 2.dp  - Drag handles
    val Tag: CornerBasedShape = RoundedCornerShape(6.dp)                       // 6.dp  - Tag and entity badges
    val ActionCard: CornerBasedShape = CardSmall                               // 12.dp - Action tiles & secondary interactive cards
    val NavigationItem: CornerBasedShape = Small                               // 8.dp  - Navigation bar/rail items
    val FloatingPanel: CornerBasedShape = Menu                                 // 14.dp - Floating overlay windows & detached panels
    val Badge: CornerBasedShape = Tag                                          // 6.dp  - Status badges & indicators
    val ImageContainer: CornerBasedShape = CardSmall                           // 12.dp - Media previews & image frames
    val WorldEntityCard: CornerBasedShape = CardMedium                         // 16.dp - World building entity cards
    val ThemeEditorControl: CornerBasedShape = ButtonSmall                     // 6.dp  - Theme editor chips & micro controls
    val ThemeEditorSection: CornerBasedShape = Menu                            // 14.dp - Theme editor section containers
}

@Immutable
data class ScribeShapes(
    val none: Shape = ScribeShapeTokens.None,
    val extraSmall: CornerBasedShape = ScribeShapeTokens.ExtraSmall,
    val small: CornerBasedShape = ScribeShapeTokens.Small,
    val medium: CornerBasedShape = ScribeShapeTokens.Medium,
    val large: CornerBasedShape = ScribeShapeTokens.Large,
    val extraLarge: CornerBasedShape = ScribeShapeTokens.ExtraLarge,
    val full: CornerBasedShape = ScribeShapeTokens.Full,
    val pill: CornerBasedShape = ScribeShapeTokens.Pill,

    // Semantic Component Shape Aliases
    val card: CornerBasedShape = ScribeShapeTokens.Card,
    val cardMedium: CornerBasedShape = ScribeShapeTokens.CardMedium,
    val cardSmall: CornerBasedShape = ScribeShapeTokens.CardSmall,
    val cardNested: CornerBasedShape = ScribeShapeTokens.CardNested,
    val button: CornerBasedShape = ScribeShapeTokens.Button,
    val buttonSmall: CornerBasedShape = ScribeShapeTokens.ButtonSmall,
    val field: CornerBasedShape = ScribeShapeTokens.Field,
    val searchBar: CornerBasedShape = ScribeShapeTokens.SearchBar,
    val chip: CornerBasedShape = ScribeShapeTokens.Chip,
    val chipRect: CornerBasedShape = ScribeShapeTokens.ChipRect,
    val dialog: CornerBasedShape = ScribeShapeTokens.Dialog,
    val bottomSheet: CornerBasedShape = ScribeShapeTokens.BottomSheet,
    val menu: CornerBasedShape = ScribeShapeTokens.Menu,
    val fab: CornerBasedShape = ScribeShapeTokens.Fab,
    val fabSmall: CornerBasedShape = ScribeShapeTokens.FabSmall,
    val speedDial: CornerBasedShape = ScribeShapeTokens.SpeedDial,
    val handle: CornerBasedShape = ScribeShapeTokens.Handle,
    val tag: CornerBasedShape = ScribeShapeTokens.Tag,
    val actionCard: CornerBasedShape = ScribeShapeTokens.ActionCard,
    val navigationItem: CornerBasedShape = ScribeShapeTokens.NavigationItem,
    val floatingPanel: CornerBasedShape = ScribeShapeTokens.FloatingPanel,
    val badge: CornerBasedShape = ScribeShapeTokens.Badge,
    val imageContainer: CornerBasedShape = ScribeShapeTokens.ImageContainer,
    val worldEntityCard: CornerBasedShape = ScribeShapeTokens.WorldEntityCard,
    val themeEditorControl: CornerBasedShape = ScribeShapeTokens.ThemeEditorControl,
    val themeEditorSection: CornerBasedShape = ScribeShapeTokens.ThemeEditorSection
) {
    fun toMaterialShapes(): androidx.compose.material3.Shapes = androidx.compose.material3.Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge
    )
}

/** Returns a copy with only start corners (topStart, bottomStart) rounded. */
fun CornerBasedShape.startOnly(): CornerBasedShape =
    copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

/** Returns a copy with only end corners (topEnd, bottomEnd) rounded. */
fun CornerBasedShape.endOnly(): CornerBasedShape =
    copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp))

// ── Spacing Scale ───────────────────────────────────────────────────────────

/**
 * Centralized design tokens for Scribe's 4dp/8dp spacing system.
 *
 * Negative space hierarchy:
 * - [None] (0.dp): Zero padding / margin reset.
 * - [Hairline] (2.dp): Micro separation, badge-to-label offsets, hairline dividers.
 * - [Micro] (4.dp): Fine insets, pill padding, bar horizontal margins.
 * - [Compact] (8.dp): Related element grouping, icon-to-label spacing, tight list rows.
 * - [Medium] (12.dp): Card header vertical padding, tight card body padding, dense gaps.
 * - [Standard] (16.dp): Screen margins, standard card body padding, dialog insets.
 * - [Section] (24.dp): Vertical rhythm between major content cards/sections.
 * - [Large] (32.dp): Spacious separation, thematic groupings, empty-state tops.
 * - [Huge] (48.dp): Generous scroll bottoms (overscroll clearance), hero visual spacing.
 */
object ScribeSpacingTokens {
    val None: Dp = 0.dp
    val Hairline: Dp = 2.dp
    val Micro: Dp = 4.dp
    val Compact: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Standard: Dp = 16.dp
    val Section: Dp = 24.dp
    val Large: Dp = 32.dp
    val Huge: Dp = 48.dp
}

@Immutable
data class ScribeSpacing(
    // Canonical semantic scale
    val none: Dp = ScribeSpacingTokens.None,
    val hairline: Dp = ScribeSpacingTokens.Hairline,
    val micro: Dp = ScribeSpacingTokens.Micro,
    val compact: Dp = ScribeSpacingTokens.Compact,
    val medium: Dp = ScribeSpacingTokens.Medium,
    val standard: Dp = ScribeSpacingTokens.Standard,
    val section: Dp = ScribeSpacingTokens.Section,
    val large: Dp = ScribeSpacingTokens.Large,
    val huge: Dp = ScribeSpacingTokens.Huge,

    // Backward-compatible & scale aliases
    val xxs: Dp = hairline,
    val xs: Dp = micro,
    val sm: Dp = compact,
    val md: Dp = medium,
    val lg: Dp = standard,
    val xl: Dp = section,
    val xxl: Dp = large,
    val xxxl: Dp = huge,

    // Additional intuitive aliases
    val extraSmall: Dp = micro,
    val small: Dp = compact,
    val normal: Dp = standard,
    val extraLarge: Dp = large
)

// ── Metrics & Layout Dimensions ─────────────────────────────────────────────

/**
 * Centralized design tokens for physical component metrics, heights, touch bounds, and icon footprints.
 */
object ScribeMetricTokens {
    // ── Touch Target Boundaries ─────────────────────────────────────────────
    val TouchTargetMin: Dp = 48.dp         // WCAG / Material minimum interactive target
    val TouchTargetCompact: Dp = 40.dp     // Dense toolbar & compact card hit targets
    val TouchTargetMicro: Dp = 36.dp       // Dense secondary header controls with padding

    // ── Control & Bar Heights ───────────────────────────────────────────────
    val TopBarContentHeight: Dp = 48.dp    // Scribe top app bar content height
    val NavBarContentHeight: Dp = 52.dp    // Scribe bottom navigation bar content height
    val EditorBarContentHeight: Dp = 44.dp // Compact editor formatting bar height
    val FabSize: Dp = 56.dp                // Standard floating action button
    val FabSizeSmall: Dp = 40.dp           // Secondary / quick-action FAB
    val SpeedDialWidth: Dp = 210.dp        // Speed dial menu card width
    val SpeedDialItemHeight: Dp = 52.dp    // Speed dial menu row height
    val ChipHeight: Dp = 32.dp             // Standard filter / category chip
    val ChipHeightCompact: Dp = 28.dp      // Word counter pill / micro badge
    val FieldHeight: Dp = 56.dp            // Standard text input field
    val FieldHeightCompact: Dp = 48.dp     // Compact search bar / find & replace field
    val DragHandleWidth: Dp = 36.dp        // Bottom sheet tactile drag handle width
    val DragHandleHeight: Dp = 4.dp        // Bottom sheet drag handle thickness

    // ── Icon Footprints (Visual Scale) ───────────────────────────────────────
    val IconMicro: Dp = 12.dp              // Micro indicators & inline arrows
    val IconSmall: Dp = 14.dp              // Sub-label indicators, stat icons, pane controls
    val IconMedium: Dp = 18.dp             // Toolbar actions, section headers, badges
    val IconNormal: Dp = 20.dp             // Nav tab icons, secondary FAB icons
    val IconLarge: Dp = 22.dp              // Top bar navigation & action icons
    val IconDisplay: Dp = 24.dp            // Empty-state icons, primary visual icons
    val IconHero: Dp = 32.dp               // Hero illustrations

    // ── Semantic Screen & Container Insets ──────────────────────────────────
    val ScreenPadding: Dp = 16.dp          // Standard screen margin
    val ScreenPaddingWide: Dp = 24.dp      // Tablet / expanded screen margin
    val CardPadding: Dp = 16.dp            // Standard card inner padding
    val CardPaddingTight: Dp = 12.dp       // Dense list strip inner padding
    val CardPaddingSpacious: Dp = 20.dp    // Primary hero card padding
    val DialogPadding: Dp = 24.dp          // Modal dialog internal inset
    val SheetPadding: Dp = 20.dp           // Bottom sheet content inset

    // ── Strokes & Borders ───────────────────────────────────────────────────
    val BorderHairline: Dp = 0.7.dp        // Subtle frosted glass border stroke
    val BorderThin: Dp = 1.dp              // Standard container stroke
    val BorderThick: Dp = 2.dp             // Active selection / focus ring
    val AccentBarWidth: Dp = 3.5.dp        // Leading edge accent bar for cards/panes

    // ── Elevation & Blur ────────────────────────────────────────────────────
    val ElevationNone: Dp = 0.dp
    val ElevationLow: Dp = 2.dp
    val ElevationMedium: Dp = 4.dp
    val ElevationHigh: Dp = 8.dp
    val DefaultBlurRadius: Dp = 15.dp
}

@Immutable
data class ScribeMetrics(
    val spacing: ScribeSpacing = ScribeSpacing(),

    // Touch Targets
    val touchTargetMin: Dp = ScribeMetricTokens.TouchTargetMin,
    val touchTargetCompact: Dp = ScribeMetricTokens.TouchTargetCompact,
    val touchTargetMicro: Dp = ScribeMetricTokens.TouchTargetMicro,

    // Control & Bar Heights
    val topBarHeight: Dp = ScribeMetricTokens.TopBarContentHeight,
    val bottomBarHeight: Dp = ScribeMetricTokens.NavBarContentHeight,
    val editorBarHeight: Dp = ScribeMetricTokens.EditorBarContentHeight,
    val fabSize: Dp = ScribeMetricTokens.FabSize,
    val fabSizeSmall: Dp = ScribeMetricTokens.FabSizeSmall,
    val speedDialWidth: Dp = ScribeMetricTokens.SpeedDialWidth,
    val speedDialItemHeight: Dp = ScribeMetricTokens.SpeedDialItemHeight,
    val chipHeight: Dp = ScribeMetricTokens.ChipHeight,
    val chipHeightCompact: Dp = ScribeMetricTokens.ChipHeightCompact,
    val fieldHeight: Dp = ScribeMetricTokens.FieldHeight,
    val fieldHeightCompact: Dp = ScribeMetricTokens.FieldHeightCompact,
    val dragHandleWidth: Dp = ScribeMetricTokens.DragHandleWidth,
    val dragHandleHeight: Dp = ScribeMetricTokens.DragHandleHeight,

    // Icon Footprints
    val iconMicro: Dp = ScribeMetricTokens.IconMicro,
    val iconSmall: Dp = ScribeMetricTokens.IconSmall,
    val iconMedium: Dp = ScribeMetricTokens.IconMedium,
    val iconNormal: Dp = ScribeMetricTokens.IconNormal,
    val iconLarge: Dp = ScribeMetricTokens.IconLarge,
    val iconDisplay: Dp = ScribeMetricTokens.IconDisplay,
    val iconHero: Dp = ScribeMetricTokens.IconHero,

    // Semantic Insets
    val screenPadding: Dp = ScribeMetricTokens.ScreenPadding,
    val screenPaddingWide: Dp = ScribeMetricTokens.ScreenPaddingWide,
    val cardPadding: Dp = ScribeMetricTokens.CardPadding,
    val cardPaddingTight: Dp = ScribeMetricTokens.CardPaddingTight,
    val cardPaddingSpacious: Dp = ScribeMetricTokens.CardPaddingSpacious,
    val dialogPadding: Dp = ScribeMetricTokens.DialogPadding,
    val sheetPadding: Dp = ScribeMetricTokens.SheetPadding,

    // Strokes & Borders
    val borderHairline: Dp = ScribeMetricTokens.BorderHairline,
    val borderThin: Dp = ScribeMetricTokens.BorderThin,
    val borderThick: Dp = ScribeMetricTokens.BorderThick,
    val accentBarWidth: Dp = ScribeMetricTokens.AccentBarWidth,

    // Elevation & Blur
    val elevationNone: Dp = ScribeMetricTokens.ElevationNone,
    val elevationLow: Dp = ScribeMetricTokens.ElevationLow,
    val elevationMedium: Dp = ScribeMetricTokens.ElevationMedium,
    val elevationHigh: Dp = ScribeMetricTokens.ElevationHigh,
    val defaultBlurRadius: Dp = ScribeMetricTokens.DefaultBlurRadius,

    // Backward-compatible spacing fields on ScribeMetrics
    val spaceNone: Dp = spacing.none,
    val spaceExtraSmall: Dp = spacing.micro,
    val spaceSmall: Dp = spacing.compact,
    val spaceMedium: Dp = spacing.medium,
    val spaceNormal: Dp = spacing.standard,
    val spaceLarge: Dp = spacing.section,
    val spaceExtraLarge: Dp = spacing.large,
    val spaceHuge: Dp = spacing.huge
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
