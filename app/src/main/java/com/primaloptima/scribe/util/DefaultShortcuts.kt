package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.ShortcutAction

object DefaultShortcuts {

    const val CAT_DIALOGUE = "dialogue"
    const val CAT_SYMBOLS = "symbols"
    const val CAT_PUNCTUATION = "punctuation"
    const val CAT_STRUCTURE = "structure"
    const val CAT_FORMATTING = "formatting"
    const val CAT_CUSTOM = "custom"

    val all: List<ShortcutAction> = listOf(
        // ── 1. Dialogue & Monologues (Item 6 & 7) ────────────────────────────
        // Typographical Curly Quotes
        ShortcutAction(
            id = "quote_curly",
            label = "“ ”",
            kind = "pair",
            payload = "\u201C",
            closing = "\u201D",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),
        // Straight / Normal Quotes
        ShortcutAction(
            id = "quote_straight",
            label = "\" \"",
            kind = "pair",
            payload = "\"",
            closing = "\"",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),
        // Typographical Single Curly (Monologues / Thoughts)
        ShortcutAction(
            id = "quote_single_curly",
            label = "‘ ’",
            kind = "pair",
            payload = "\u2018",
            closing = "\u2019",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),
        // Straight Single Quotes
        ShortcutAction(
            id = "quote_single_straight",
            label = "' '",
            kind = "pair",
            payload = "'",
            closing = "'",
            category = CAT_DIALOGUE,
            isEnabled = false
        ),
        // Heavy / Ornate Double Quotes
        ShortcutAction(
            id = "quote_heavy_double",
            label = "❝ ❞",
            kind = "pair",
            payload = "❝",
            closing = "❞",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),
        // Heavy / Ornate Single Quotes
        ShortcutAction(
            id = "quote_heavy_single",
            label = "❛ ❜",
            kind = "pair",
            payload = "❛",
            closing = "❜",
            category = CAT_DIALOGUE,
            isEnabled = false
        ),
        // Light Novel / East Asian Corner Brackets
        ShortcutAction(
            id = "quote_corner",
            label = "「 」",
            kind = "pair",
            payload = "「",
            closing = "」",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),
        // Double Corner Brackets
        ShortcutAction(
            id = "quote_corner_double",
            label = "『 』",
            kind = "pair",
            payload = "『",
            closing = "』",
            category = CAT_DIALOGUE,
            isEnabled = false
        ),
        // French Guillemets
        ShortcutAction(
            id = "quote_guillemets",
            label = "« »",
            kind = "pair",
            payload = "«",
            closing = "»",
            category = CAT_DIALOGUE,
            isEnabled = false
        ),
        // Em-dash dialogue starter
        ShortcutAction(
            id = "dialogue_emdash",
            label = "— ",
            kind = "insert",
            payload = "— ",
            category = CAT_DIALOGUE,
            isEnabled = true
        ),

        // ── 2. Novel & LitRPG Symbols (Item 7) ───────────────────────────────
        // Status Window / System Brackets
        ShortcutAction(
            id = "sym_status_bracket",
            label = "【 】",
            kind = "pair",
            payload = "【",
            closing = "】",
            category = CAT_SYMBOLS,
            isEnabled = true
        ),
        // Lenticular Brackets
        ShortcutAction(
            id = "sym_lenticular",
            label = "〔 〕",
            kind = "pair",
            payload = "〔",
            closing = "〕",
            category = CAT_SYMBOLS,
            isEnabled = false
        ),
        // White Square Brackets (Mathematical / Magic System)
        ShortcutAction(
            id = "sym_white_square",
            label = "⟦ ⟧",
            kind = "pair",
            payload = "⟦",
            closing = "⟧",
            category = CAT_SYMBOLS,
            isEnabled = false
        ),
        // Angle Brackets (Spells / Skills)
        ShortcutAction(
            id = "sym_angle_bracket",
            label = "⟨ ⟩",
            kind = "pair",
            payload = "⟨",
            closing = "⟩",
            category = CAT_SYMBOLS,
            isEnabled = true
        ),
        // Typographical Ellipsis
        ShortcutAction(
            id = "ellipsis",
            label = "…",
            kind = "insert",
            payload = "…",
            category = CAT_SYMBOLS,
            isEnabled = true
        ),
        // Section Break Flourish / Asterism
        ShortcutAction(
            id = "sym_asterism",
            label = "⁂",
            kind = "insert",
            payload = "\n\n⁂\n\n",
            category = CAT_SYMBOLS,
            isEnabled = false
        ),
        // Sparkle / Magic Star
        ShortcutAction(
            id = "sym_sparkle",
            label = "✦",
            kind = "insert",
            payload = "✦",
            category = CAT_SYMBOLS,
            isEnabled = false
        ),
        // Section Symbol
        ShortcutAction(
            id = "sym_section",
            label = "§",
            kind = "insert",
            payload = "§",
            category = CAT_SYMBOLS,
            isEnabled = false
        ),

        // ── 3. Punctuation & Cadence ─────────────────────────────────────────
        ShortcutAction(
            id = "emdash",
            label = "—",
            kind = "insert",
            payload = "—",
            category = CAT_PUNCTUATION,
            isEnabled = true
        ),
        ShortcutAction(
            id = "endash",
            label = "–",
            kind = "insert",
            payload = "–",
            category = CAT_PUNCTUATION,
            isEnabled = false
        ),
        ShortcutAction(
            id = "paren",
            label = "( )",
            kind = "pair",
            payload = "(",
            closing = ")",
            category = CAT_PUNCTUATION,
            isEnabled = true
        ),
        ShortcutAction(
            id = "bracket",
            label = "[ ]",
            kind = "pair",
            payload = "[",
            closing = "]",
            category = CAT_PUNCTUATION,
            isEnabled = true
        ),
        ShortcutAction(
            id = "brace",
            label = "{ }",
            kind = "pair",
            payload = "{",
            closing = "}",
            category = CAT_PUNCTUATION,
            isEnabled = false
        ),
        ShortcutAction(
            id = "semicolon",
            label = ";",
            kind = "insert",
            payload = ";",
            category = CAT_PUNCTUATION,
            isEnabled = true
        ),
        ShortcutAction(
            id = "colon",
            label = ":",
            kind = "insert",
            payload = ":",
            category = CAT_PUNCTUATION,
            isEnabled = true
        ),

        // ── 4. Structure & Lists ─────────────────────────────────────────────
        ShortcutAction(
            id = "list",
            label = "•",
            kind = "prefix",
            payload = "- ",
            category = CAT_STRUCTURE,
            isEnabled = true
        ),
        ShortcutAction(
            id = "numlist",
            label = "1.",
            kind = "prefix",
            payload = "1. ",
            category = CAT_STRUCTURE,
            isEnabled = true
        ),
        ShortcutAction(
            id = "tasklist",
            label = "[✓]",
            kind = "prefix",
            payload = "- [ ] ",
            category = CAT_STRUCTURE,
            isEnabled = true
        ),
        ShortcutAction(
            id = "blockquote",
            label = ">",
            kind = "prefix",
            payload = "> ",
            category = CAT_STRUCTURE,
            isEnabled = true
        ),
        ShortcutAction(
            id = "hr",
            label = "---",
            kind = "insert",
            payload = "\n\n---\n\n",
            category = CAT_STRUCTURE,
            isEnabled = true
        ),
        ShortcutAction(
            id = "h1",
            label = "H1",
            kind = "prefix",
            payload = "# ",
            category = CAT_STRUCTURE,
            isEnabled = false
        ),
        ShortcutAction(
            id = "h2",
            label = "H2",
            kind = "prefix",
            payload = "## ",
            category = CAT_STRUCTURE,
            isEnabled = false
        ),
        ShortcutAction(
            id = "tab",
            label = "Tab",
            kind = "insert",
            payload = "    ",
            category = CAT_STRUCTURE,
            isEnabled = false
        ),

        // ── 5. Formatting & Styles ───────────────────────────────────────────
        ShortcutAction(
            id = "bold",
            label = "B",
            kind = "wrap",
            payload = "**",
            closing = "**",
            category = CAT_FORMATTING,
            isEnabled = true
        ),
        ShortcutAction(
            id = "italic",
            label = "I",
            kind = "wrap",
            payload = "*",
            closing = "*",
            category = CAT_FORMATTING,
            isEnabled = true
        ),
        ShortcutAction(
            id = "strikethrough",
            label = "S",
            kind = "wrap",
            payload = "~~",
            closing = "~~",
            category = CAT_FORMATTING,
            isEnabled = false
        ),
        ShortcutAction(
            id = "code",
            label = "‹›",
            kind = "wrap",
            payload = "`",
            closing = "`",
            category = CAT_FORMATTING,
            isEnabled = false
        )
    )
}
