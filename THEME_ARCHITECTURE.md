# Scribe Theme Architecture & Image Generation Engine

## Overview
Scribe's dynamic color and theme system generates editorial, accessible, and writer-centric themes from visual artwork without exposing technical color laboratories or creating unreadable "rainbow" or "muddy" user interfaces.

---

## 1. The Four Architectural Invariants
1. **Theme Identity is Stable**: A theme's canonical palette, semantic tokens, and contrast guarantees are independent of runtime presentations or device states.
2. **Quantization is Single-Pass**: Image analysis (quantization and perceptual scoring) executes exactly once per image fingerprint. Switching recipes, influence, polarity, or writing characters never re-quantizes.
3. **Semantic Roles are Protected**: Feedback indicators (`success`, `warning`, `error`), lexer syntax (`dialogueText`, `monologueText`), and analytics/worldbuilding categories are derived through independent perceptual rules and are never hijacked by arbitrary image pixels.
4. **Contrast & Accessibility are Guaranteed by Design**: All prose and UI elements satisfy WCAG 2.2 AA (>= 4.5:1 for body text, >= 3.0:1 for headings/UI) with APCA perceptual contrast verification and safe gamut mapping in OKLCH space.

---

## 2. Pipeline Stages

```
Image Bitmap / Pixels
       ↓ (Single Pass)
QuantizerCelebi + Score + OKLCH Sampling
       ↓
ImageUnderstanding (Tonal, Chromatic, Temperature, Diversity, Fingerprint)
       ↓
Multi-Source Palette Extraction (VisualRole: Primary, Atmospheric, Supporting, Tertiary)
       ↓
ThemeGenerationEngine (Recipe + Influence + WritingCharacter)
       ↓
ThemeSourcePalette
       ↓
ThemeManager.generateThemeDefaults (40+ Semantic Tokens, 5-Tier Surface Ramp)
       ↓
ContrastResolver & Collision Guard (WCAG 2.2 AA + APCA + Delta-E Separation)
       ↓
Resolved ThemeColors + ThemeColorOverrides → AppTheme
```

---

## 3. Image Understanding & Source Extraction
- **Perceptual Space**: All analysis and transformations occur in cylindrical **OKLCH** ($L$, $C$, $h$) to ensure uniform lightness and chroma scaling.
- **Visual Roles**:
  - `PRIMARY_ACCENT`: Chosen by combining frequency ranking with chromatic vibrancy.
  - `ATMOSPHERIC`: High-population neutral or low-chroma candidate that characterizes the ambient environment.
  - `SUPPORTING_ACCENT`: Extracted with a minimum circular hue distance of $\ge 28^\circ$ from the primary accent.
  - `TERTIARY_ACCENT`: Extracted with angular distance separation from both primary and supporting accents.
  - `NEUTRAL`: Candidates providing foundation anchor tones.

---

## 4. Theme Generation Recipes & Parameters
- **ThemeGenerationRecipe**:
  - `BALANCED`: Proportional equilibrium between primary image accent and legible reading canvas.
  - `ATMOSPHERIC`: Imbues surfaces with the photograph's ambient mood and soft tonal tint.
  - `INK`: Restrained, monochromatic background with razor-sharp editorial focus and minimalist accenting.
  - `EXPRESSIVE`: Heightened chromatic resonance and vibrant brand highlights.
- **ImageInfluence**:
  - `SUBTLE` (0.50x factor), `BALANCED` (1.00x factor), `STRONG` (1.55x factor).
- **WritingCharacter**:
  - `NEUTRAL`, `WARM`, `COOL`, `DRAMATIC` (subtle reading undertones).

---

## 5. Semantic Role Architecture & Collision Guard
- **Editorial & Prose Hierarchy**:
  - `text`: High-contrast body prose.
  - `headingText`: Prominent chapter and scene titles.
  - `dialogueText`: Spoken character dialogue (perceptually separated from body text and highlights).
  - `monologueText`: Internal character thoughts and stream of consciousness.
  - `specialHighlight`: Literary golden emphasis.
- **Status Indicators**:
  - `success` ($h \approx 142^\circ$), `warning` ($h \approx 85^\circ$), `error` ($h \approx 25^\circ$), `info` ($h \approx 230^\circ$).
  - When image primary accents land near status hues, `resolveSemanticCollisions` enforces an OKLab $\Delta E \ge 0.04$ separation.
- **5-Tier Surface Ramp**:
  - Dark Mode: $L0\ (\text{background}) < L1\ (\text{surfaceLowest}) < L2\ (\text{surface}) < L3\ (\text{surfaceRaised}) < L4\ (\text{surfaceOverlay})$.
  - Light Mode: Controlled relative elevation steps preserving contrast.

---

## 6. Presentation Relationship Modes
- `THEME_ONLY`: Generated theme identity only; zero image asset dependency.
- `THEME_IMAGE`: Generated theme + selected artwork pinned as background image.
- `THEME_GLASS`: Generated theme + artwork + frosted environmental blur with specular rim borders.

---

## 7. Persistence, Provenance & Migration
- Themes persist complete `ThemeGenerationMetadata` (recipe, influence, character, original atmosphere hex, source fingerprint).
- User manual overrides (`ThemeColorOverrides`) are preserved across polarity toggles and recipe re-evaluations.
- Reopening a theme or replacing an image never triggers silent re-generation.
