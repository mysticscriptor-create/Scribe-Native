package com.primaloptima.scribe.ui.theme

import android.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme

/**
 * Maps Scribe's [AppTheme] and [ScribeColors] onto Sora 0.24.6's [EditorColorScheme] token set.
 *
 * Extends [EditorColorScheme] by overriding [applyDefault], which is called by
 * the parent constructor. Sora calls applyDefault() to populate the color map;
 * we call super first to ensure all IDs have safe fallbacks, then override the
 * ones we care about. Assign a fresh instance to [CodeEditor.colorScheme] via
 * [editor.colorScheme = ScribeColorScheme(theme)] — Sora redraws automatically.
 *
 * Phase 4 Semantic Decoupling:
 *  - MATCHED_TEXT_BACKGROUND (29) is explicitly mapped to the writing highlight token
 *  - Diagnostic wavy markers are bound truthfully:
 *      PROBLEM_WARNING (36) → semantic.warning / theme.warning
 *      PROBLEM_TYPO    (37) → semantic.info / theme.info
 *      PROBLEM_ERROR   (35) → semantic.error / theme.error
 *  - Resolves themes via [ThemeManager.resolveTheme] before applying to ensure override layering
 */
class ScribeColorScheme(private var theme: AppTheme? = null) : EditorColorScheme(theme?.isDark ?: false) {

    init {
        theme?.let { applyTheme(it) }
    }

    override fun applyDefault() {
        super.applyDefault()
        val currentTheme = theme ?: return
        applyTheme(currentTheme)
    }

    fun applyTheme(appTheme: AppTheme) {
        this.theme = appTheme
        val resolved = ThemeManager.resolveTheme(appTheme)
        val bg     = parse(resolved.colors.background)
        val text   = parse(resolved.colors.text)
        val accent = parse(resolved.colors.accent)
        val sel    = parse(resolved.colors.selection)

        // ── Background ────────────────────────────────────────────────────────
        setColor(WHOLE_BACKGROUND,         bg)
        setColor(LINE_NUMBER_BACKGROUND,   bg)
        setColor(LINE_NUMBER,              bg)   // gutter text invisible
        setColor(LINE_DIVIDER,             bg)

        // ── Text ──────────────────────────────────────────────────────────────
        setColor(TEXT_NORMAL,              text)
        setColor(TEXT_SELECTED,            0)

        // ── Novel / Prose Lexer Tokens ─────────────────────────────────────────
        val dialogueColor = parse(resolved.colors.dialogueText)
        setColor(LITERAL,                  dialogueColor)

        val thoughtColor = parse(resolved.colors.monologueText)
        setColor(COMMENT,                  thoughtColor)

        val headingColor = parse(resolved.colors.headingText)
        setColor(KEYWORD,                  headingColor)

        // ── Search & Highlight Tokens (Phase 4 Decoupling) ────────────────────
        val highlightColor = if (resolved.colors.specialHighlight.isNotEmpty()) {
            parse(resolved.colors.specialHighlight)
        } else if (resolved.isDark) {
            Color.argb(255, 231, 184, 90)
        } else {
            Color.argb(255, 180, 83, 9)
        }
        // Sora 0.24.x uses MATCHED_TEXT_BACKGROUND (29) for search matches
        setColor(MATCHED_TEXT_BACKGROUND, withAlpha(highlightColor, 130))

        // ── Diagnostic Colors ──────────────────────────────────────────────────
        // Truthful semantic mapping:
        //   PROBLEM_WARNING (36) → warning feedback (amber)
        //   PROBLEM_TYPO    (37) → informational guidance (cyan / info)
        //   PROBLEM_ERROR   (35) → critical syntax/grammar failures (red / error)
        val warningColor = if (resolved.colors.warning.isNotEmpty()) {
            parse(resolved.colors.warning)
        } else if (resolved.isDark) {
            Color.argb(255, 251, 191, 36)
        } else {
            Color.argb(255, 217, 119, 6)
        }

        val typoColor = if (resolved.colors.info.isNotEmpty()) {
            parse(resolved.colors.info)
        } else if (resolved.isDark) {
            Color.argb(255, 56, 189, 248)
        } else {
            Color.argb(255, 2, 132, 199)
        }

        val errorColor = if (resolved.colors.error.isNotEmpty()) {
            parse(resolved.colors.error)
        } else if (resolved.isDark) {
            Color.argb(255, 248, 113, 113)
        } else {
            Color.argb(255, 197, 34, 31)
        }

        setColor(PROBLEM_WARNING, warningColor)
        setColor(PROBLEM_TYPO, typoColor)
        setColor(PROBLEM_ERROR, errorColor)

        // ── Inlay Hints ───────────────────────────────────────────────────────
        setColor(TEXT_INLAY_HINT_FOREGROUND, withAlpha(text,   140))
        setColor(TEXT_INLAY_HINT_BACKGROUND, withAlpha(accent,  30))

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

    private fun parse(hex: String): Int = ThemeManager.parseColor(hex)

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )
}
