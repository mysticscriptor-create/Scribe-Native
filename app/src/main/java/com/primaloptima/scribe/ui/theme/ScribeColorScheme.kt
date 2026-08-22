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
        // Spoken dialogue color: Warm tone tinted towards accent or amber/gold contrast
        val dialogueColor = if (accent != text) {
            Color.argb(
                255,
                ((Color.red(text) * 0.4f) + (Color.red(accent) * 0.6f)).toInt().coerceIn(0, 255),
                ((Color.green(text) * 0.4f) + (Color.green(accent) * 0.6f)).toInt().coerceIn(0, 255),
                ((Color.blue(text) * 0.4f) + (Color.blue(accent) * 0.6f)).toInt().coerceIn(0, 255)
            )
        } else {
            Color.argb(255, 235, 175, 110)
        }
        setColor(LITERAL,                  dialogueColor)

        // Thought / Internal Monologue color: Soft subtle opacity of primary text
        val thoughtColor = withAlpha(text, 185)
        setColor(COMMENT,                  thoughtColor)

        // Headings / Scene Breaks: Accent highlight
        setColor(KEYWORD,                  accent)

        // ── Diagnostic Colors ──────────────────────────────────────────────────
        // Correct constant names for Sora 0.24.x:
        //   PROBLEM_WARNING (36) → passive voice / adverb phrases  (amber wave)
        //   PROBLEM_TYPO    (37) → filter words                     (purple wave)
        //   PROBLEM_ERROR   (35) → repeated words / hard adverbs    (blue wave)
        // DiagnosticRegion severity shorts (1=error, 2=warning, 3=typo) map to
        // these color constants automatically in EditorRenderer.
        setColor(PROBLEM_WARNING,          Color.argb(255, 245, 175,  45))  // Amber wave
        setColor(PROBLEM_TYPO,             Color.argb(255, 140, 120, 240))  // Purple wave
        setColor(PROBLEM_ERROR,            Color.argb(255,  80, 160, 240))  // Blue wave

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
