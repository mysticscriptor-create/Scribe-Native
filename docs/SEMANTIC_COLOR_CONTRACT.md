# Scribe Semantic Color Contract & Architecture Guide (Phase 4)

## 1. Core Architectural Principle
```
SEMANTIC ROLE ≠ HARDWIRED IMPLEMENTATION ALIAS
```
Two semantic roles may resolve to the identical RGB value in a particular theme when visually appropriate. However, they remain conceptually and architecturally decoupled: changing or overriding one semantic role must never alter or cascade into another unrelated role.

---

## 2. Semantic Role Taxonomy

### A. Surface Roles (`SurfaceColors`)
- **`background`**: Base canvas for viewport background.
- **`surfaceLowest`**: Sunken wells, background cards, recessed gutters.
- **`surface`**: Standard elevated component cards, sheets, dialog bodies.
- **`surfaceRaised`**: Floating cards, dropdown panels, raised action sheets.
- **`surfaceOverlay`**: Highest modal sheets, popups, full-screen tool overlays.
- **`surfaceSelected`**: Selected states on containers, active list rows.
- **`surfacePressed`**: Tactile feedback color on direct pointer press.

### B. Content Roles (`ContentColors`)
- **`primary`**: High-contrast body copy, primary headings (WCAG AA >= 4.5:1, APCA Lc >= 75).
- **`secondary`**: Subtitles, secondary metadata, breadcrumbs (WCAG AA >= 4.5:1).
- **`tertiary`**: Timestamps, subtle caption text, auxiliary metadata (WCAG Large >= 3.0:1).
- **`disabled`**: Inactive labels, disabled controls.
- **`onAccent`**: Text and icons directly overlaid on `interaction.primary` containers.

### C. Interaction Roles (`InteractionColors`)
- **`primary`**: Primary active button backgrounds, tab indicators, key interaction landmarks.
- **`primaryContainer`**: Tinted secondary container backgrounds for primary actions.
- **`onPrimary`**: Accessible foreground contrast against `primary` surfaces.
- **`onPrimaryContainer`**: Foreground contrast against `primaryContainer` surfaces.
- **`secondary`**: Secondary actions, auxiliary buttons, secondary tags.
- **`tertiary`**: Tertiary actions, tertiary chip badges.
- **`selection`**: Text highlight selection bounding boxes.
- **`focus`**: Focused state keyboard focus rings, active accessibility borders.
- **`link`**: Clickable hyperlink text, URL spans (independent of accent or selection).

### D. Writing Roles (`WritingColors`)
- **`prose`**: Main editor narrative prose text and base body typography foundation.
- **`dialogue`**: Direct speech / quoted spoken dialogue highlighting ("...", “...”, «...», or em-dash lines).
- **`monologue`**: Internal thoughts and reflection highlighting (*asterisks* or ‘typographic single quotes’).
- **`heading`**: Scene headings, chapter titles, markdown markers (#, ##, scene-breaks ***).
- **`annotation`**: Editorial margin notes, reviewer comments, callouts, and inline editorial critique flags.
- **`highlight`**: Search hit matches, literary emphasis markers, and active search result spans.

#### Canonical Writing Role Distinctions & Boundaries:
1. **`writing.highlight` vs `interaction.selection`**:
   - `writing.highlight`: Represents passive document state (e.g. search query hits, literary text markers). Mapped to Sora `MATCHED_TEXT_BACKGROUND` at subtle alpha (~51% / 130). Text underneath maintains its canonical syntax foreground color.
   - `interaction.selection`: Represents active user pointer interaction state. Mapped to Sora `SELECTED_TEXT_BACKGROUND` at prominent alpha (~63% / 160). When overlapping a search highlight, user selection visually dominates due to higher opacity and active interaction state priority.
2. **`writing.annotation` vs `semantic.warning`**:
   - `writing.annotation`: Represents author editorial notes, manuscript comments, and inlay hints. It conveys literary metadata rather than system failure or danger. Mapped to Sora `TEXT_INLAY_HINT_FOREGROUND` / `TEXT_INLAY_HINT_BACKGROUND` (purple/violet hues).
   - `semantic.warning`: Represents non-blocking system diagnostics, low storage warnings, and conflict notifications. Mapped to Sora diagnostic wavy underline `PROBLEM_WARNING` (amber hue). Never substitute warning tokens for authorial annotations.
3. **`writing.heading` vs Application/Navigation Headings (`content.primary`)**:
   - `writing.heading`: Dedicated to manuscript content headings (e.g. "Chapter I", Markdown `# Scene Title`, and scene-break dividers). It reflects literary styling within the canvas and editor lexer (`KEYWORD`).
   - `content.primary`: High-contrast structural application typography used in app bars, drawer navigation headers, modal dialog titles, and card headers. Application headings must never change when an author customizes their manuscript heading color.
4. **Writing Roles vs Analytics Roles (`AnalyticsColors`)**:
   - Writing roles (`prose`, `dialogue`, `monologue`, `heading`, `annotation`, `highlight`) govern reading calm, manuscript aesthetics, and creative text rendering inside the editor.
   - Analytics roles (`positive`, `neutral`, `negative`, `series1`, `series2`, `series3`, `target`, `warning`) govern charts, writing session metrics, wordcount trends, and progress gauges. Modifying writing colors must never impact chart series identity or velocity indicators.

### E. Semantic Status Roles (`SemanticStatusColors`)
- **`success` / `onSuccess` / `successContainer` / `onSuccessContainer`**: System operation success, completed saves, cloud sync success.
- **`warning` / `onWarning` / `warningContainer` / `onWarningContainer`**: System warnings, storage low, conflict notifications.
- **`error` / `onError` / `errorContainer` / `onErrorContainer`**: Form validation errors, network failures, deletion alerts.
- **`info` / `onInfo` / `infoContainer` / `onInfoContainer`**: Informational alerts, tooltips, guidance badges.

### F. Analytics & Metrics Roles (`AnalyticsColors`)
- **`positive`**: Upward trend, wordcount delta surplus (+N words).
- **`neutral`**: Flat trend, baseline target metric, neutral word count delta (0 words).
- **`negative`**: Downward trend, deficit, wordcount delta reduction (-N words).
- **`series1`**: Primary data chart series, primary word map bar.
- **`series2`**: Secondary data chart series, comparison chart line.
- **`series3`**: Tertiary data chart series, cumulative background area.
- **`target`**: Benchmark goal line, top rank indicator, milestone marker.
- **`warning`**: Writing streak fire icon, pacing deficit warning, deadline proximity warning.

### G. Worldbuilding Entity Roles (`WorldEntityColors`)
- **`character`**: Character codex badges, POV markers, dialogue attribution.
- **`location`**: Setting and geography entity badges.
- **`faction`**: Political factions, guilds, organizations.
- **`item`**: Inventory items, artifacts, weapons, key objects.
- **`lore`**: World history, mythology, world rules, magic systems.
- **`event`**: Timelines, historical events, chronological milestones.
- **`relationship`**: Character ties, social graphs, alignment connections.

### H. Border Hierarchy Roles (`BorderColors`)
- **`subtle`**: Low-contrast internal dividers, nested card outlines, table gridlines.
- **`normal`**: Standard card borders, text field outlines, sheet headers.
- **`prominent`**: Active card selections, key structural dividers, modal boundaries.

---

## 3. Derivation, Overrides & Precedence

1. **Source Foundation**: User or default defines `ThemeSourcePalette(background, text, accent)`.
2. **Default Derivation**: `ThemeManager.generateThemeDefaults()` calculates OKLCH-derived defaults for all 35+ semantic roles across all categories.
3. **Override Layering**: If a user customizes a specific role (e.g. `ThemeColorOverrides(analyticsSeries2 = "#06B6D4")`), `ThemeManager.resolveThemeColors()` layers the override onto the specific role while preserving all other calculated defaults.
4. **Contrast Guard**: `ThemeManager.validateSemanticContrast()` checks and adjusts any overridden or derived tokens via `ContrastResolver` using APCA / WCAG binary-search OKLCH adjustments to ensure accessibility compliance.
5. **UI & Editor Dispatch**:
   - Compose views consume tokens via `ScribeTheme.colors.*`.
   - Sora editor consumes tokens via `ScribeColorScheme`, truthfully translating prose syntax, diagnostic waves (`PROBLEM_WARNING`, `PROBLEM_TYPO`, `PROBLEM_ERROR`), and search matches (`MATCHED_TEXT_BACKGROUND`).
