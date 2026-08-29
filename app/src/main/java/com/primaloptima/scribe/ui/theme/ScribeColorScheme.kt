package com.primaloptima.scribe.ui.theme

import android.graphics.Color
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme

/**
 * Maps Scribe's [AppTheme] onto Sora 0.24.6's [EditorColorScheme] token set.
 *
 * Extends [EditorColorScheme] by overriding [applyDefault], which is called by
 * the parent constructor. Sora calls applyDefault() to populate the color map;
 * we call super first to ensure all IDs have safe fallbacks, then override the
 * ones we care about. Assign a fresh instance to [CodeEditor.colorScheme] via
 * [editor.colorScheme = ScribeColorScheme(theme)] — Sora redraws automatically.
 *
 * Tokens covered:
 *  - Background / gutter (hidden)
 *  - Normal text + selected-text foreground
 *  - Current-line highlight (subtle, ~7% opacity of text colour)
 *  - Selection background + handles + insert cursor line
 *  - Novel / Prose Lexer Tokens (LITERAL → dialogue, COMMENT → thoughts, KEYWORD → headings)
 *  - Diagnostic underline colors (PROBLEM_WARNING / PROBLEM_TYPO / PROBLEM_ERROR)
 *  - Inlay hint foreground / background
 *  - Matched-bracket highlight
 *  - Scroll bar thumb/track
 *
 * API notes for Sora 0.24.6:
 *  - Diagnostic color constants are PROBLEM_ERROR (35), PROBLEM_WARNING (36),
 *    PROBLEM_TYPO (37). The names DIAGNOSTIC_WARNING / DIAGNOSTIC_INFO /
 *    DIAGNOSTIC_ERROR do NOT exist and will cause a compile error.
 *  - TEXT_SELECTED (30) exists and controls selected-text foreground.
 *    Pass 0 (fully transparent) to leave selected text color unchanged.
 *  - SELECTION_INSERT (7) is the cursor bar (caret) color — valid.
 *  - TEXT_INLAY_HINT_FOREGROUND (50) and TEXT_INLAY_HINT_BACKGROUND (49)
 *    control inline hint badges.
 *  - HIGHLIGHTED_DELIMITERS_UNDERLINE (40) exists in 0.24.x — included here.
 *  - SEARCH_RESULT_BACKGROUND is not a valid constant; search highlights are
 *    handled by MATCHED_TEXT_BACKGROUND (29).
 */
class ScribeColorScheme(private var theme: AppTheme? = null) : EditorColorScheme(theme?.isDark ?: false) {

    init {
        theme?.let { applyTheme(it) }
    }

    override fun applyDefault() {
        // Always populate parent defaults first so every color ID is valid.
        super.applyDefault()

        // During super constructor call, properties of this class are not yet initialized (theme is null in bytecode)
        val currentTheme = theme ?: return
        applyTheme(currentTheme)
    }

    fun applyTheme(appTheme: AppTheme) {
        this.theme = appTheme
        val bg     = parse(appTheme.colors.background)
        val text   = parse(appTheme.colors.text)
        val accent = parse(appTheme.colors.accent)
        val sel    = parse(appTheme.colors.selection)

        // ── Background ────────────────────────────────────────────────────────
        setColor(WHOLE_BACKGROUND,         bg)
        setColor(LINE_NUMBER_BACKGROUND,   bg)
        setColor(LINE_NUMBER,              bg)   // gutter text invisible
        setColor(LINE_DIVIDER,             bg)

        // ── Text ──────────────────────────────────────────────────────────────
        setColor(TEXT_NORMAL,              text)
        // TEXT_SELECTED (30): 0 = no change (Sora keeps selected text same color
        // as TEXT_NORMAL). Set to 0 so selection highlight comes from background.
        setColor(TEXT_SELECTED,            0)

        // ── Novel / Prose Lexer Tokens ─────────────────────────────────────────
        // Dialogue / Direct speech highlighting from calibrated theme token
        val dialogueColor = parse(appTheme.colors.dialogueText)
        setColor(LITERAL,                  dialogueColor)

        // Thought / Internal Monologue from calibrated theme token
        val thoughtColor = parse(appTheme.colors.monologueText)
        setColor(COMMENT,                  thoughtColor)

        // Headings / Scene Breaks: Heading highlight token
        val headingColor = parse(appTheme.colors.headingText)
        setColor(KEYWORD,                  headingColor)

        // ── Diagnostic Colors ──────────────────────────────────────────────────
        // High-contrast, calibrated chromatic wave markers for prose diagnostics
        //   PROBLEM_WARNING (36) → passive voice / adverb phrases  (warning / amber)
        //   PROBLEM_TYPO    (37) → filter words                     (secondary / violet)
        //   PROBLEM_ERROR   (35) → repeated words / hard adverbs    (error or tertiary / blue)
        val warningColor = if (appTheme.colors.warning.isNotEmpty()) {
            parse(appTheme.colors.warning)
        } else if (appTheme.isDark) {
            Color.argb(255, 251, 191, 36)
        } else {
            Color.argb(255, 217, 119, 6)
        }

        val typoColor = if (appTheme.colors.secondary.isNotEmpty() && appTheme.colors.secondary != appTheme.colors.accent) {
            parse(appTheme.colors.secondary)
        } else if (appTheme.isDark) {
            Color.argb(255, 167, 139, 250)
        } else {
            Color.argb(255, 124, 58, 237)
        }

        val errorColor = if (appTheme.colors.error.isNotEmpty()) {
            parse(appTheme.colors.error)
        } else if (appTheme.colors.tertiary.isNotEmpty() && appTheme.colors.tertiary != appTheme.colors.accent) {
            parse(appTheme.colors.tertiary)
        } else if (appTheme.isDark) {
            Color.argb(255, 96, 165, 250)
        } else {
            Color.argb(255, 37, 99, 235)
        }

        setColor(PROBLEM_WARNING, warningColor)
        setColor(PROBLEM_TYPO, typoColor)
        setColor(PROBLEM_ERROR, errorColor)

        // ── Inlay Hints ───────────────────────────────────────────────────────
        // Scene word-count badges and POV tags rendered by ProseInlayHintProvider.
        setColor(TEXT_INLAY_HINT_FOREGROUND, withAlpha(text,   140))
        setColor(TEXT_INLAY_HINT_BACKGROUND, withAlpha(accent,  30))

        // ── Current line (very subtle) ────────────────────────────────────────
        val currentLineTint = Color.argb(18,
            Color.red(text), Color.green(text), Color.blue(text))
        setColor(CURRENT_LINE,             currentLineTint)

        // ── Selection ─────────────────────────────────────────────────────────
        setColor(SELECTED_TEXT_BACKGROUND, withAlpha(sel, 160))
        setColor(SELECTION_HANDLE,         accent)
        setColor(SELECTION_INSERT,         accent)  // cursor / caret bar colour

        // ── Matched bracket ───────────────────────────────────────────────────
        setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, text)
        setColor(HIGHLIGHTED_DELIMITERS_BACKGROUND, withAlpha(accent, 60))
        setColor(HIGHLIGHTED_DELIMITERS_UNDERLINE,  withAlpha(accent, 80))

        // ── Scroll indicators (keep neutral) ──────────────────────────────────
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
