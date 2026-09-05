package com.primaloptima.scribe.ui.theme

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme

/**
 * Maps Scribe's canonical [ScribeColors] (and [AppTheme] via [ThemeManager.resolveToScribeColors])
 * onto Sora 0.24.6's [EditorColorScheme] token set.
 *
 * Architecture (Phase 5 — Canonical Writing Semantic Bridge):
 *   Writing meaning
 *       ↓
 *   Scribe Writing semantic tokens (ScribeColors.writing)
 *       ↓
 *   canonical adapter (ScribeColorScheme)
 *       ↓
 *   editor / lexer / Sora
 *
 * Canonical Token Mappings:
 *   - Prose: TEXT_NORMAL → writing.prose
 *   - Dialogue: LITERAL → writing.dialogue
 *   - Monologue / Internal Thought: COMMENT → writing.monologue
 *   - Heading / Section Titles: KEYWORD → writing.heading
 *   - Annotation / Inlay Hints: TEXT_INLAY_HINT_FOREGROUND → writing.annotation
 *                               TEXT_INLAY_HINT_BACKGROUND → writing.annotation (subtle alpha)
 *   - Search / Match Highlight: MATCHED_TEXT_BACKGROUND → writing.highlight (with alpha)
 *   - Diagnostics (Decoupled Semantic Status):
 *       PROBLEM_WARNING → semantic.warning
 *       PROBLEM_TYPO    → semantic.info
 *       PROBLEM_ERROR   → semantic.error
 *   - Canvas & Gutter:
 *       WHOLE_BACKGROUND, LINE_NUMBER_BACKGROUND, LINE_NUMBER, LINE_DIVIDER → surfaces.background
 *   - Selection & Caret:
 *       SELECTED_TEXT_BACKGROUND → interaction.selection
 *       SELECTION_HANDLE, SELECTION_INSERT → interaction.primary
 */
class ScribeColorScheme(
    private var scribeColors: ScribeColors? = null,
    isDark: Boolean = scribeColors?.isDark ?: false
) : EditorColorScheme(isDark) {

    private var appTheme: AppTheme? = null

    constructor(theme: AppTheme) : this(
        scribeColors = ThemeManager.resolveToScribeColors(theme),
        isDark = theme.isDark
    ) {
        this.appTheme = theme
    }

    init {
        scribeColors?.let { applyScribeColors(it) }
    }

    override fun applyDefault() {
        super.applyDefault()
        scribeColors?.let { applyScribeColors(it) }
            ?: appTheme?.let { applyTheme(it) }
    }

    fun applyTheme(appTheme: AppTheme) {
        this.appTheme = appTheme
        val resolved = ThemeManager.resolveToScribeColors(appTheme)
        applyScribeColors(resolved)
    }

    fun applyScribeColors(colors: ScribeColors) {
        this.scribeColors = colors

        val bg      = colors.surfaces.background.toArgb()
        val text    = colors.writing.prose.toArgb()
        val accent  = colors.interaction.primary.toArgb()
        val sel     = colors.interaction.selection.toArgb()

        // ── Background & Gutter ───────────────────────────────────────────────
        setColor(WHOLE_BACKGROUND,         bg)
        setColor(LINE_NUMBER_BACKGROUND,   bg)
        setColor(LINE_NUMBER,              bg)   // gutter text invisible
        setColor(LINE_DIVIDER,             bg)

        // ── Text & Base Prose ─────────────────────────────────────────────────
        setColor(TEXT_NORMAL,              text)
        setColor(TEXT_SELECTED,            0)

        // ── Novel / Prose Lexer Tokens (Truthful Canonical Mapping) ───────────
        val dialogueColor = colors.writing.dialogue.toArgb()
        setColor(LITERAL,                  dialogueColor)

        val thoughtColor = colors.writing.monologue.toArgb()
        setColor(COMMENT,                  thoughtColor)

        val headingColor = colors.writing.heading.toArgb()
        setColor(KEYWORD,                  headingColor)

        // ── Search & Highlight Tokens (Canonical Bridge) ──────────────────────
        val highlightColor = colors.writing.highlight.toArgb()
        // Sora 0.24.x uses MATCHED_TEXT_BACKGROUND (29) for search matches
        setColor(MATCHED_TEXT_BACKGROUND, withAlpha(highlightColor, 130))

        // ── Diagnostic Colors (Decoupled Semantic Status) ─────────────────────
        // Truthful semantic mapping:
        //   PROBLEM_WARNING (36) → semantic.warning feedback (amber)
        //   PROBLEM_TYPO    (37) → semantic.info informational guidance (cyan)
        //   PROBLEM_ERROR   (35) → semantic.error critical syntax/grammar failures (red)
        val warningColor = colors.semantic.warning.toArgb()
        val typoColor    = colors.semantic.info.toArgb()
        val errorColor   = colors.semantic.error.toArgb()

        setColor(PROBLEM_WARNING, warningColor)
        setColor(PROBLEM_TYPO, typoColor)
        setColor(PROBLEM_ERROR, errorColor)

        // ── Inlay Hints & Annotations (Canonical Bridge) ──────────────────────
        val annotationColor = colors.writing.annotation.toArgb()
        setColor(TEXT_INLAY_HINT_FOREGROUND, annotationColor)
        setColor(TEXT_INLAY_HINT_BACKGROUND, withAlpha(annotationColor, 35))

        // ── Current line (subtle) ─────────────────────────────────────────────
        val currentLineTint = Color.argb(18,
            Color.red(text), Color.green(text), Color.blue(text))
        setColor(CURRENT_LINE,             currentLineTint)

        // ── Selection ─────────────────────────────────────────────────────────
        setColor(SELECTED_TEXT_BACKGROUND, withAlpha(sel, 160))
        setColor(SELECTION_HANDLE,         accent)
        setColor(SELECTION_INSERT,         accent)

        // ── Matched bracket ───────────────────────────────────────────────────
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, text)
        setColor(HIGHLIGHTED_DELIMITERS_BACKGROUND, withAlpha(accent, 60))
        setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE,  withAlpha(accent, 80))

        // ── Scroll indicators ─────────────────────────────────────────────────
        setColor(SCROLL_BAR_THUMB,         withAlpha(text, 60))
        setColor(SCROLL_BAR_THUMB_PRESSED, withAlpha(text, 120))
        setColor(SCROLL_BAR_TRACK,         withAlpha(text, 20))
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
