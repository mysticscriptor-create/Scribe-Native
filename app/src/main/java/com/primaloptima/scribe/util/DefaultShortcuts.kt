package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.ShortcutAction

/**
 * Modernized taxonomy of built-in writing shortcuts.
 *
 * Categories:
 * - CAT_DIALOGUE: Dialogue & Monologue (Western quotes, Asian corner quotes, guillemets, em dash dialogue)
 * - CAT_BRACKETS: Brackets & Enclosures (Standard, Novel & LitRPG brackets, angle brackets)
 * - CAT_PUNCTUATION: Punctuation & Cadence (Em/en dash, ellipsis, semicolon, colon, interrobang)
 * - CAT_SCENE_BREAKS: Scene Breaks & Ornaments (Asterism, flourish, stars, dividers, hr)
 * - CAT_STRUCTURE: Structure & Lists (Bullets, numbers, tasks, blockquote, headers, tab)
 * - CAT_FORMATTING: Text Styles & Markdown (Bold, italic, strikethrough, code, highlight)
 * - CAT_ARROWS: Arrows & Direction (Right, left, bidirectional, transition arrows)
 * - CAT_MATH: Math, Logic & Units (Plus-minus, multiply, divide, not-equal, approx, degree)
 * - CAT_CURRENCY: Currency & Common Symbols (Dollar, Euro, Pound, Yen, Rupee, Won, percent)
 * - CAT_CUSTOM: Custom & Snippets (User-created)
 */
object DefaultShortcuts {
    const val CAT_DIALOGUE = "dialogue"
    const val CAT_BRACKETS = "brackets"
    const val CAT_PUNCTUATION = "punctuation"
    const val CAT_SCENE_BREAKS = "scene_breaks"
    const val CAT_STRUCTURE = "structure"
    const val CAT_FORMATTING = "formatting"
    const val CAT_ARROWS = "arrows"
    const val CAT_MATH = "math"
    const val CAT_CURRENCY = "currency"
    const val CAT_CUSTOM = "custom"

    // Legacy compatibility constant for existing data migration
    const val CAT_SYMBOLS = "symbols"

    val all: List<ShortcutAction> = listOf(
        // ── 1. Dialogue & Monologue ──────────────────────────────────────────
        ShortcutAction(
            id = "quote_curly",
            label = "“ ”",
            kind = "pair",
            payload = "“",
            closing = "”",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "curly", "double", "dialogue", "speech")
        ),
        ShortcutAction(
            id = "quote_straight",
            label = "" "",
            kind = "pair",
            payload = """,
            closing = """,
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "straight", "double", "normal", "speech")
        ),
        ShortcutAction(
            id = "quote_single_curly",
            label = "‘ ’",
            kind = "pair",
            payload = "‘",
            closing = "’",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "single", "curly", "monologue", "thought", "apostrophe")
        ),
        ShortcutAction(
            id = "quote_single_straight",
            label = "' '",
            kind = "pair",
            payload = "'",
            closing = "'",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("quote", "single", "straight", "apostrophe")
        ),
        ShortcutAction(
            id = "quote_heavy_double",
            label = "❝ ❞",
            kind = "pair",
            payload = "❝",
            closing = "❞",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "ornate", "heavy", "fancy", "dialogue")
        ),
        ShortcutAction(
            id = "quote_heavy_single",
            label = "❛ ❜",
            kind = "pair",
            payload = "❛",
            closing = "❜",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("quote", "heavy", "single", "ornate")
        ),
        ShortcutAction(
            id = "quote_corner",
            label = "「 」",
            kind = "pair",
            payload = "「",
            closing = "」",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("corner", "japanese", "asian", "light novel", "dialogue", "bracket")
        ),
        ShortcutAction(
            id = "quote_corner_double",
            label = "『 』",
            kind = "pair",
            payload = "『",
            closing = "』",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("corner", "double", "japanese", "asian", "quote")
        ),
        ShortcutAction(
            id = "quote_guillemets",
            label = "« »",
            kind = "pair",
            payload = "«",
            closing = "»",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("guillemet", "french", "continental", "chevron", "quote")
        ),
        ShortcutAction(
            id = "dialogue_emdash",
            label = "— ",
            kind = "insert",
            payload = "— ",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("em dash", "emdash", "dash", "dialogue", "speech", "starter")
        ),

        // ── 2. Brackets & Enclosures ─────────────────────────────────────────
        ShortcutAction(
            id = "paren",
            label = "( )",
            kind = "pair",
            payload = "(",
            closing = ")",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("parenthesis", "parentheses", "round", "bracket", "enclosure")
        ),
        ShortcutAction(
            id = "bracket",
            label = "[ ]",
            kind = "pair",
            payload = "[",
            closing = "]",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("bracket", "square", "brackets", "enclosure")
        ),
        ShortcutAction(
            id = "brace",
            label = "{ }",
            kind = "pair",
            payload = "{",
            closing = "}",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("brace", "curly", "braces", "bracket")
        ),
        ShortcutAction(
            id = "sym_status_bracket",
            label = "【 】",
            kind = "pair",
            payload = "【",
            closing = "】",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("status", "system", "litrpg", "lenticular", "black", "bracket", "game")
        ),
        ShortcutAction(
            id = "sym_lenticular",
            label = "〔 〕",
            kind = "pair",
            payload = "〔",
            closing = "〕",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("tortoiseshell", "lenticular", "bracket", "asian", "japanese")
        ),
        ShortcutAction(
            id = "sym_white_square",
            label = "⟦ ⟧",
            kind = "pair",
            payload = "⟦",
            closing = "⟧",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("white square", "magic", "system", "math", "bracket", "double")
        ),
        ShortcutAction(
            id = "sym_angle_bracket",
            label = "⟨ ⟩",
            kind = "pair",
            payload = "⟨",
            closing = "⟩",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("angle", "skill", "spell", "bracket", "chevron")
        ),
        ShortcutAction(
            id = "sym_double_angle",
            label = "⟪ ⟫",
            kind = "pair",
            payload = "⟪",
            closing = "⟫",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("double angle", "bracket", "system", "skill")
        ),
        ShortcutAction(
            id = "sym_floor",
            label = "⌊ ⌋",
            kind = "pair",
            payload = "⌊",
            closing = "⌋",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("floor", "corner", "bracket", "math")
        ),

        // ── 3. Punctuation & Cadence ─────────────────────────────────────────
        ShortcutAction(
            id = "emdash",
            label = "—",
            kind = "insert",
            payload = "—",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("em dash", "emdash", "dash", "hyphen", "break", "pause")
        ),
        ShortcutAction(
            id = "endash",
            label = "–",
            kind = "insert",
            payload = "–",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("en dash", "endash", "dash", "range")
        ),
        ShortcutAction(
            id = "ellipsis",
            label = "…",
            kind = "insert",
            payload = "…",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("ellipsis", "dots", "pause", "trailing", "cadence")
        ),
        ShortcutAction(
            id = "semicolon",
            label = ";",
            kind = "insert",
            payload = ";",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("semicolon", "cadence", "punctuation")
        ),
        ShortcutAction(
            id = "colon",
            label = ":",
            kind = "insert",
            payload = ":",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("colon", "punctuation")
        ),
        ShortcutAction(
            id = "interrobang",
            label = "‽",
            kind = "insert",
            payload = "‽",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("interrobang", "question", "exclamation", "curiosity", "surprise")
        ),
        ShortcutAction(
            id = "middledot",
            label = "·",
            kind = "insert",
            payload = "·",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("middle dot", "bullet", "separator", "katakana")
        ),

        // ── 4. Scene Breaks & Ornaments ───────────────────────────────────────
        ShortcutAction(
            id = "hr",
            label = "---",
            kind = "insert",
            payload = "\n\n---\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = true,
            keywords = listOf("horizontal rule", "scene break", "divider", "separator", "break", "thematic")
        ),
        ShortcutAction(
            id = "sym_asterism",
            label = "⁂",
            kind = "insert",
            payload = "\n\n⁂\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("asterism", "scene break", "flourish", "divider", "stars", "break")
        ),
        ShortcutAction(
            id = "sym_three_stars",
            label = "* * *",
            kind = "insert",
            payload = "\n\n* * *\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("stars", "scene break", "divider", "break", "dinkus")
        ),
        ShortcutAction(
            id = "sym_sparkle",
            label = "✦",
            kind = "insert",
            payload = "✦",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("sparkle", "star", "flourish", "magic", "ornament")
        ),
        ShortcutAction(
            id = "sym_black_star",
            label = "★",
            kind = "insert",
            payload = "★",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("star", "black star", "favorite", "dingbat", "rating")
        ),
        ShortcutAction(
            id = "sym_section",
            label = "§",
            kind = "insert",
            payload = "§",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("section", "legal", "clause", "divider", "symbol")
        ),
        ShortcutAction(
            id = "sym_fleuron",
            label = "❦",
            kind = "insert",
            payload = "❦",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("fleuron", "ivy", "leaf", "ornament", "flourish", "heart")
        ),

        // ── 5. Structure & Lists ─────────────────────────────────────────────
        ShortcutAction(
            id = "list",
            label = "•",
            kind = "prefix",
            payload = "- ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("bullet", "list", "unordered", "item")
        ),
        ShortcutAction(
            id = "numlist",
            label = "1.",
            kind = "prefix",
            payload = "1. ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("number", "numbered", "ordered", "list")
        ),
        ShortcutAction(
            id = "tasklist",
            label = "[✓]",
            kind = "prefix",
            payload = "- [ ] ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("task", "todo", "checkbox", "checklist", "done")
        ),
        ShortcutAction(
            id = "blockquote",
            label = ">",
            kind = "prefix",
            payload = "> ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("blockquote", "quote", "indent", "callout", "note")
        ),
        ShortcutAction(
            id = "h1",
            label = "H1",
            kind = "prefix",
            payload = "# ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("h1", "heading", "header", "title", "chapter")
        ),
        ShortcutAction(
            id = "h2",
            label = "H2",
            kind = "prefix",
            payload = "## ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("h2", "heading", "header", "section", "subtitle")
        ),
        ShortcutAction(
            id = "tab",
            label = "Tab",
            kind = "insert",
            payload = "    ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("tab", "indent", "space", "whitespace")
        ),

        // ── 6. Text Styles & Markdown ─────────────────────────────────────────
        ShortcutAction(
            id = "bold",
            label = "B",
            kind = "wrap",
            payload = "**",
            closing = "**",
            category = CAT_FORMATTING,
            isEnabled = true,
            keywords = listOf("bold", "strong", "emphasis", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "italic",
            label = "I",
            kind = "wrap",
            payload = "*",
            closing = "*",
            category = CAT_FORMATTING,
            isEnabled = true,
            keywords = listOf("italic", "emphasis", "slant", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "strikethrough",
            label = "S",
            kind = "wrap",
            payload = "~~",
            closing = "~~",
            category = CAT_FORMATTING,
            isEnabled = false,
            keywords = listOf("strikethrough", "strike", "cross", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "code",
            label = "‹›",
            kind = "wrap",
            payload = "`",
            closing = "`",
            category = CAT_FORMATTING,
            isEnabled = false,
            keywords = listOf("code", "inline", "monospace", "backtick", "markdown")
        ),

        // ── 7. Arrows & Direction ────────────────────────────────────────────
        ShortcutAction(
            id = "arrow_right",
            label = "→",
            kind = "insert",
            payload = "→",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "right", "direction", "pointer", "next", "flow")
        ),
        ShortcutAction(
            id = "arrow_left",
            label = "←",
            kind = "insert",
            payload = "←",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "left", "direction", "back", "previous")
        ),
        ShortcutAction(
            id = "arrow_both",
            label = "↔",
            kind = "insert",
            payload = "↔",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "both", "bidirectional", "horizontal")
        ),
        ShortcutAction(
            id = "arrow_double_right",
            label = "⇒",
            kind = "insert",
            payload = "⇒",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "implies", "double", "then", "result")
        ),

        // ── 8. Math, Logic & Units ───────────────────────────────────────────
        ShortcutAction(
            id = "math_plusminus",
            label = "±",
            kind = "insert",
            payload = "±",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("plus minus", "tolerance", "approx", "math")
        ),
        ShortcutAction(
            id = "math_multiply",
            label = "×",
            kind = "insert",
            payload = "×",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("multiply", "times", "cross", "math", "by")
        ),
        ShortcutAction(
            id = "math_divide",
            label = "÷",
            kind = "insert",
            payload = "÷",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("divide", "division", "math")
        ),
        ShortcutAction(
            id = "math_notequal",
            label = "≠",
            kind = "insert",
            payload = "≠",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("not equal", "different", "math", "logic")
        ),
        ShortcutAction(
            id = "math_approx",
            label = "≈",
            kind = "insert",
            payload = "≈",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("approx", "approximately", "almost", "estimate", "math")
        ),
        ShortcutAction(
            id = "math_degree",
            label = "°",
            kind = "insert",
            payload = "°",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("degree", "temperature", "angle", "celsius", "fahrenheit", "unit")
        ),
        ShortcutAction(
            id = "math_infinity",
            label = "∞",
            kind = "insert",
            payload = "∞",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("infinity", "infinite", "forever", "math")
        ),

        // ── 9. Currency & Common Symbols ─────────────────────────────────────
        ShortcutAction(
            id = "sym_dollar",
            label = "$",
            kind = "insert",
            payload = "$",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("dollar", "usd", "currency", "money", "price")
        ),
        ShortcutAction(
            id = "sym_euro",
            label = "€",
            kind = "insert",
            payload = "€",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("euro", "eur", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_pound",
            label = "£",
            kind = "insert",
            payload = "£",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("pound", "gbp", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_yen",
            label = "¥",
            kind = "insert",
            payload = "¥",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("yen", "yuan", "jpy", "cny", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_percent",
            label = "%",
            kind = "insert",
            payload = "%",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("percent", "percentage", "rate", "symbol")
        )
    )
}
