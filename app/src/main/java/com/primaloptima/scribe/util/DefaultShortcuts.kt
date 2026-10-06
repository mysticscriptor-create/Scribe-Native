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

    // Snippets Categories
    const val CAT_CORRESPONDENCE = "correspondence"
    const val CAT_NARRATIVE = "narrative"
    const val CAT_NOTES = "notes"

    // Templates Categories
    const val CAT_TMPL_STRUCTURE = "structure"
    const val CAT_TMPL_CHARACTERS = "characters"
    const val CAT_TMPL_WORLDBUILDING = "worldbuilding"
    const val CAT_TMPL_DIALOGUE = "dialogue"

    // Legacy compatibility constant for existing data migration
    const val CAT_SYMBOLS = "symbols"

    /**
     * Auto-detects the concise icon/symbol representation from the user's inserted text
     * across all three shortcut types ("pair"/"wrap", "prefix", "insert").
     */
    fun autoDetectIcon(kind: String, payload: String, closing: String? = null): String {
        if (payload.isEmpty()) return ""

        fun formatToken(raw: String): String {
            if (raw.isEmpty()) return ""
            // If string is purely line breaks
            if (raw.all { it == '\n' || it == '\r' }) return "↵"
            // If string is purely spaces or tabs
            if (raw.all { it == ' ' || it == '\t' }) return "⇥"
            // Strip surrounding line breaks (e.g., "\n\n---\n\n" -> "---"), replace internal line breaks with ↵
            val strippedOuterNewlines = raw.trim('\n', '\r')
            val normalized = strippedOuterNewlines
                .replace("\r\n", "↵")
                .replace("\n", "↵")
                .replace("\r", "↵")
                .trim()
            return normalized.ifEmpty { "↵" }
        }

        return when (kind) {
            "pair", "wrap" -> {
                val openToken = formatToken(payload).take(3)
                val closeSource = if (!closing.isNullOrBlank()) closing else payload
                val closeToken = formatToken(closeSource).take(3)
                if (openToken.isEmpty()) "" else "$openToken $closeToken".trim()
            }
            "prefix" -> {
                formatToken(payload).take(5)
            }
            else -> { // "insert"
                formatToken(payload).take(5)
            }
        }
    }

    val all: List<ShortcutAction> = listOf(
        // ── 1. Dialogue & Monologue ──────────────────────────────────────────
        ShortcutAction(
            id = "quote_curly",
            label = "Curly double quotes",
            icon = "“ ”",
            kind = "pair",
            payload = "“",
            closing = "”",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "curly", "double", "dialogue", "speech")
        ),
        ShortcutAction(
            id = "quote_straight",
            label = "Straight double quotes",
            icon = "\" \"",
            kind = "pair",
            payload = "\"",
            closing = "\"",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "straight", "double", "normal", "speech")
        ),
        ShortcutAction(
            id = "quote_single_curly",
            label = "Curly single quotes",
            icon = "‘ ’",
            kind = "pair",
            payload = "‘",
            closing = "’",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "quotes", "single", "curly", "monologue", "thought", "apostrophe")
        ),
        ShortcutAction(
            id = "quote_single_straight",
            label = "Straight single quotes",
            icon = "' '",
            kind = "pair",
            payload = "'",
            closing = "'",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("quote", "single", "straight", "apostrophe")
        ),
        ShortcutAction(
            id = "quote_heavy_double",
            label = "Curly quotation marks",
            icon = "❝ ❞",
            kind = "pair",
            payload = "❝",
            closing = "❞",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("quote", "ornate", "heavy", "fancy", "dialogue")
        ),
        ShortcutAction(
            id = "quote_heavy_single",
            label = "Heavy single quotes",
            icon = "❛ ❜",
            kind = "pair",
            payload = "❛",
            closing = "❜",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("quote", "heavy", "single", "ornate")
        ),
        ShortcutAction(
            id = "quote_corner",
            label = "Japanese corner quotes",
            icon = "「 」",
            kind = "pair",
            payload = "「",
            closing = "」",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("corner", "japanese", "asian", "light novel", "dialogue", "bracket")
        ),
        ShortcutAction(
            id = "quote_corner_double",
            label = "Double corner brackets",
            icon = "『 』",
            kind = "pair",
            payload = "『",
            closing = "』",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("corner", "double", "japanese", "asian", "quote")
        ),
        ShortcutAction(
            id = "quote_guillemets",
            label = "French guillemets",
            icon = "« »",
            kind = "pair",
            payload = "«",
            closing = "»",
            category = CAT_DIALOGUE,
            isEnabled = false,
            keywords = listOf("guillemet", "french", "continental", "chevron", "quote")
        ),
        ShortcutAction(
            id = "dialogue_emdash",
            label = "Dialogue em dash",
            icon = "— ",
            kind = "insert",
            payload = "— ",
            category = CAT_DIALOGUE,
            isEnabled = true,
            keywords = listOf("em dash", "emdash", "dash", "dialogue", "speech", "starter")
        ),

        // ── 2. Brackets & Enclosures ─────────────────────────────────────────
        ShortcutAction(
            id = "paren",
            label = "Round parentheses",
            icon = "( )",
            kind = "pair",
            payload = "(",
            closing = ")",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("parenthesis", "parentheses", "round", "bracket", "enclosure")
        ),
        ShortcutAction(
            id = "bracket",
            label = "Square brackets",
            icon = "[ ]",
            kind = "pair",
            payload = "[",
            closing = "]",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("bracket", "square", "brackets", "enclosure")
        ),
        ShortcutAction(
            id = "brace",
            label = "Curly braces",
            icon = "{ }",
            kind = "pair",
            payload = "{",
            closing = "}",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("brace", "curly", "braces", "bracket")
        ),
        ShortcutAction(
            id = "sym_status_bracket",
            label = "Status window brackets",
            icon = "【 】",
            kind = "pair",
            payload = "【",
            closing = "】",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("status", "system", "litrpg", "lenticular", "black", "bracket", "game")
        ),
        ShortcutAction(
            id = "sym_lenticular",
            label = "Lenticular brackets",
            icon = "〔 〕",
            kind = "pair",
            payload = "〔",
            closing = "〕",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("tortoiseshell", "lenticular", "bracket", "asian", "japanese")
        ),
        ShortcutAction(
            id = "sym_white_square",
            label = "White square brackets",
            icon = "⟦ ⟧",
            kind = "pair",
            payload = "⟦",
            closing = "⟧",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("white square", "magic", "system", "math", "bracket", "double")
        ),
        ShortcutAction(
            id = "sym_angle_bracket",
            label = "Angle brackets",
            icon = "⟨ ⟩",
            kind = "pair",
            payload = "⟨",
            closing = "⟩",
            category = CAT_BRACKETS,
            isEnabled = true,
            keywords = listOf("angle", "skill", "spell", "bracket", "chevron")
        ),
        ShortcutAction(
            id = "sym_double_angle",
            label = "Double angle brackets",
            icon = "⟪ ⟫",
            kind = "pair",
            payload = "⟪",
            closing = "⟫",
            category = CAT_BRACKETS,
            isEnabled = false,
            keywords = listOf("double angle", "bracket", "system", "skill")
        ),
        ShortcutAction(
            id = "sym_floor",
            label = "Floor brackets",
            icon = "⌊ ⌋",
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
            label = "Em dash",
            icon = "—",
            kind = "insert",
            payload = "—",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("em dash", "emdash", "dash", "hyphen", "break", "pause")
        ),
        ShortcutAction(
            id = "endash",
            label = "En dash",
            icon = "–",
            kind = "insert",
            payload = "–",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("en dash", "endash", "dash", "range")
        ),
        ShortcutAction(
            id = "ellipsis",
            label = "Typographical ellipsis",
            icon = "…",
            kind = "insert",
            payload = "…",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("ellipsis", "dots", "pause", "trailing", "cadence")
        ),
        ShortcutAction(
            id = "semicolon",
            label = "Semicolon",
            icon = ";",
            kind = "insert",
            payload = ";",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("semicolon", "cadence", "punctuation")
        ),
        ShortcutAction(
            id = "colon",
            label = "Colon",
            icon = ":",
            kind = "insert",
            payload = ":",
            category = CAT_PUNCTUATION,
            isEnabled = true,
            keywords = listOf("colon", "punctuation")
        ),
        ShortcutAction(
            id = "interrobang",
            label = "Interrobang",
            icon = "‽",
            kind = "insert",
            payload = "‽",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("interrobang", "question", "exclamation", "curiosity", "surprise")
        ),
        ShortcutAction(
            id = "middledot",
            label = "Middle dot",
            icon = "·",
            kind = "insert",
            payload = "·",
            category = CAT_PUNCTUATION,
            isEnabled = false,
            keywords = listOf("middle dot", "bullet", "separator", "katakana")
        ),

        // ── 4. Scene Breaks & Ornaments ───────────────────────────────────────
        ShortcutAction(
            id = "hr",
            label = "Thematic break (---)",
            icon = "---",
            kind = "insert",
            payload = "\n\n---\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = true,
            keywords = listOf("horizontal rule", "scene break", "divider", "separator", "break", "thematic")
        ),
        ShortcutAction(
            id = "sym_asterism",
            label = "Asterism flourish",
            icon = "⁂",
            kind = "insert",
            payload = "\n\n⁂\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("asterism", "scene break", "flourish", "divider", "stars", "break")
        ),
        ShortcutAction(
            id = "sym_three_stars",
            label = "Three stars (* * *)",
            icon = "* * *",
            kind = "insert",
            payload = "\n\n* * *\n\n",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("stars", "scene break", "divider", "break", "dinkus")
        ),
        ShortcutAction(
            id = "sym_sparkle",
            label = "Magic sparkle",
            icon = "✦",
            kind = "insert",
            payload = "✦",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("sparkle", "star", "flourish", "magic", "ornament")
        ),
        ShortcutAction(
            id = "sym_black_star",
            label = "Black star",
            icon = "★",
            kind = "insert",
            payload = "★",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("star", "black star", "favorite", "dingbat", "rating")
        ),
        ShortcutAction(
            id = "sym_section",
            label = "Section symbol",
            icon = "§",
            kind = "insert",
            payload = "§",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("section", "legal", "clause", "divider", "symbol")
        ),
        ShortcutAction(
            id = "sym_fleuron",
            label = "Fleuron leaf",
            icon = "❦",
            kind = "insert",
            payload = "❦",
            category = CAT_SCENE_BREAKS,
            isEnabled = false,
            keywords = listOf("fleuron", "ivy", "leaf", "ornament", "flourish", "heart")
        ),

        // ── 5. Structure & Lists ─────────────────────────────────────────────
        ShortcutAction(
            id = "list",
            label = "Bullet list item",
            icon = "•",
            kind = "prefix",
            payload = "- ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("bullet", "list", "unordered", "item")
        ),
        ShortcutAction(
            id = "numlist",
            label = "Numbered list item",
            icon = "1.",
            kind = "prefix",
            payload = "1. ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("number", "numbered", "ordered", "list")
        ),
        ShortcutAction(
            id = "tasklist",
            label = "Task list checkbox",
            icon = "[✓]",
            kind = "prefix",
            payload = "- [ ] ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("task", "todo", "checkbox", "checklist", "done")
        ),
        ShortcutAction(
            id = "blockquote",
            label = "Blockquote indent",
            icon = ">",
            kind = "prefix",
            payload = "> ",
            category = CAT_STRUCTURE,
            isEnabled = true,
            keywords = listOf("blockquote", "quote", "indent", "callout", "note")
        ),
        ShortcutAction(
            id = "h1",
            label = "Heading 1 (#)",
            icon = "H1",
            kind = "prefix",
            payload = "# ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("h1", "heading", "header", "title", "chapter")
        ),
        ShortcutAction(
            id = "h2",
            label = "Heading 2 (##)",
            icon = "H2",
            kind = "prefix",
            payload = "## ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("h2", "heading", "header", "section", "subtitle")
        ),
        ShortcutAction(
            id = "tab",
            label = "Tab (4 spaces)",
            icon = "Tab",
            kind = "insert",
            payload = "    ",
            category = CAT_STRUCTURE,
            isEnabled = false,
            keywords = listOf("tab", "indent", "space", "whitespace")
        ),

        // ── 6. Text Styles & Markdown ─────────────────────────────────────────
        ShortcutAction(
            id = "bold",
            label = "Bold format",
            icon = "B",
            kind = "pair",
            payload = "**",
            closing = "**",
            category = CAT_FORMATTING,
            isEnabled = true,
            keywords = listOf("bold", "strong", "emphasis", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "italic",
            label = "Italic format",
            icon = "I",
            kind = "pair",
            payload = "*",
            closing = "*",
            category = CAT_FORMATTING,
            isEnabled = true,
            keywords = listOf("italic", "emphasis", "slant", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "strikethrough",
            label = "Strikethrough format",
            icon = "S",
            kind = "pair",
            payload = "~~",
            closing = "~~",
            category = CAT_FORMATTING,
            isEnabled = false,
            keywords = listOf("strikethrough", "strike", "cross", "formatting", "markdown")
        ),
        ShortcutAction(
            id = "code",
            label = "Inline code backticks",
            icon = "‹›",
            kind = "pair",
            payload = "`",
            closing = "`",
            category = CAT_FORMATTING,
            isEnabled = false,
            keywords = listOf("code", "inline", "monospace", "backtick", "markdown")
        ),

        // ── 7. Arrows & Direction ────────────────────────────────────────────
        ShortcutAction(
            id = "arrow_right",
            label = "Right arrow",
            icon = "→",
            kind = "insert",
            payload = "→",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "right", "direction", "pointer", "next", "flow")
        ),
        ShortcutAction(
            id = "arrow_left",
            label = "Left arrow",
            icon = "←",
            kind = "insert",
            payload = "←",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "left", "direction", "back", "previous")
        ),
        ShortcutAction(
            id = "arrow_both",
            label = "Bidirectional arrow",
            icon = "↔",
            kind = "insert",
            payload = "↔",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "both", "bidirectional", "horizontal")
        ),
        ShortcutAction(
            id = "arrow_double_right",
            label = "Double right arrow",
            icon = "⇒",
            kind = "insert",
            payload = "⇒",
            category = CAT_ARROWS,
            isEnabled = false,
            keywords = listOf("arrow", "implies", "double", "then", "result")
        ),

        // ── 8. Math, Logic & Units ───────────────────────────────────────────
        ShortcutAction(
            id = "math_plusminus",
            label = "Plus-minus sign",
            icon = "±",
            kind = "insert",
            payload = "±",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("plus minus", "tolerance", "approx", "math")
        ),
        ShortcutAction(
            id = "math_multiply",
            label = "Multiplication sign",
            icon = "×",
            kind = "insert",
            payload = "×",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("multiply", "times", "cross", "math", "by")
        ),
        ShortcutAction(
            id = "math_divide",
            label = "Division sign",
            icon = "÷",
            kind = "insert",
            payload = "÷",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("divide", "division", "math")
        ),
        ShortcutAction(
            id = "math_notequal",
            label = "Not equal to",
            icon = "≠",
            kind = "insert",
            payload = "≠",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("not equal", "different", "math", "logic")
        ),
        ShortcutAction(
            id = "math_approx",
            label = "Approximately equal",
            icon = "≈",
            kind = "insert",
            payload = "≈",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("approx", "approximately", "almost", "estimate", "math")
        ),
        ShortcutAction(
            id = "math_degree",
            label = "Degree symbol",
            icon = "°",
            kind = "insert",
            payload = "°",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("degree", "temperature", "angle", "celsius", "fahrenheit", "unit")
        ),
        ShortcutAction(
            id = "math_infinity",
            label = "Infinity symbol",
            icon = "∞",
            kind = "insert",
            payload = "∞",
            category = CAT_MATH,
            isEnabled = false,
            keywords = listOf("infinity", "infinite", "forever", "math")
        ),

        // ── 9. Currency & Common Symbols ─────────────────────────────────────
        ShortcutAction(
            id = "sym_dollar",
            label = "Dollar sign",
            icon = "$",
            kind = "insert",
            payload = "$",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("dollar", "usd", "currency", "money", "price")
        ),
        ShortcutAction(
            id = "sym_euro",
            label = "Euro sign",
            icon = "€",
            kind = "insert",
            payload = "€",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("euro", "eur", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_pound",
            label = "Pound sign",
            icon = "£",
            kind = "insert",
            payload = "£",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("pound", "gbp", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_yen",
            label = "Yen sign",
            icon = "¥",
            kind = "insert",
            payload = "¥",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("yen", "yuan", "jpy", "cny", "currency", "money")
        ),
        ShortcutAction(
            id = "sym_percent",
            label = "Percent sign",
            icon = "%",
            kind = "insert",
            payload = "%",
            category = CAT_CURRENCY,
            isEnabled = false,
            keywords = listOf("percent", "percentage", "rate", "symbol")
        )
    )

    val defaultSnippets: List<ShortcutAction> = listOf(
        ShortcutAction(
            id = "snip_sig_sincerely",
            label = "Sign-off: Sincerely",
            icon = "✍",
            kind = "insert",
            payload = "\n\nSincerely,\n",
            category = "correspondence",
            isEnabled = true,
            itemType = "snippet",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Formal letter closing with line breaks"
        ),
        ShortcutAction(
            id = "snip_pov_separator",
            label = "POV Switch",
            icon = "✦",
            kind = "insert",
            payload = "\n\n* * *\n\n",
            category = "narrative",
            isEnabled = true,
            itemType = "snippet",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Centered asterisks for point-of-view changes"
        ),
        ShortcutAction(
            id = "snip_dialogue_beat",
            label = "Action Beat",
            icon = "—",
            kind = "insert",
            payload = "—he paused, considering his words—",
            category = "narrative",
            isEnabled = true,
            itemType = "snippet",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Em-dash enclosed narrative pause"
        ),
        ShortcutAction(
            id = "snip_quick_note",
            label = "Writer's Note",
            icon = "✎",
            kind = "wrap",
            payload = "/* NOTE: ",
            closing = " */",
            category = "notes",
            isEnabled = true,
            itemType = "snippet",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Comment wrapper for drafting notes"
        )
    )

    val defaultTemplates: List<ShortcutAction> = listOf(
        ShortcutAction(
            id = "tmpl_scene_beats",
            label = "Scene Beats",
            icon = "📋",
            kind = "insert",
            payload = "## Scene Goal\n- Protagonist Want:\n- Obstacle / Conflict:\n- Outcome / Shift:\n\n### Narrative Beats\n1. Opening Hook:\n2. Rising Complication:\n3. Crisis / Turning Point:\n4. Climax / Revelation:\n5. Resolution & Cliffhanger:\n",
            category = "structure",
            isEnabled = true,
            itemType = "template",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "5-beat storytelling breakdown for scene planning"
        ),
        ShortcutAction(
            id = "tmpl_character_dossier",
            label = "Character Profile",
            icon = "👤",
            kind = "insert",
            payload = "### Character Profile\n- Name: \n- Role: \n- Core Motivation: \n- Fatal Flaw: \n- Distinctive Voice / Habit: \n- Secret: \n",
            category = "characters",
            isEnabled = true,
            itemType = "template",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Quick character creation sheet with motivation and voice"
        ),
        ShortcutAction(
            id = "tmpl_litrpg_status",
            label = "Status / Stat Window",
            icon = "📊",
            kind = "insert",
            payload = "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓\n┃ STATUS: [NAME]               ┃\n┃ Level: 1      Class: [None] ┃\n┃ HP: 100/100   MP: 50/50     ┃\n┃ STR: 10       DEX: 10       ┃\n┃ INT: 10       VIT: 10       ┃\n┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛\n",
            category = "worldbuilding",
            isEnabled = true,
            itemType = "template",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Formatted stat box for LitRPG and Progression Fantasy"
        ),
        ShortcutAction(
            id = "tmpl_dialogue_spar",
            label = "Dialogue Sparring Frame",
            icon = "💬",
            kind = "insert",
            payload = "“[Statement of assertion],” [Name] said.\n\n“[Subtextual challenge],” replied [Other Name].\n\n[Physical action beat demonstrating tension].\n\n“[The revelation or concession].”\n",
            category = "dialogue",
            isEnabled = true,
            itemType = "template",
            showInQuickActions = false,
            useCustomIcon = false,
            templateDescription = "Back-and-forth dialogue exchange with action beat"
        )
    )

    private val byIdMap: Map<String, ShortcutAction> by lazy {
        (all + defaultSnippets + defaultTemplates).associateBy { it.id }
    }

    fun findById(id: String): ShortcutAction? = byIdMap[id]
}

/**
 * Resolves the short icon/glyph for a shortcut (used in the 40dp icon badge and Writing Bar).
 * For snippets and templates without custom icons, returns badges like "[ Snip ]" or "[ Tmpl ]".
 */
fun ShortcutAction.resolvedIcon(): String {
    if (itemType == "snippet" && !useCustomIcon) {
        return "Snip"
    }
    if (itemType == "template" && !useCustomIcon) {
        return "Tmpl"
    }
    if (icon.isNotBlank()) return icon
    val def = DefaultShortcuts.findById(id)
    if (def != null && def.icon.isNotBlank() && payload == def.payload && closing == def.closing) {
        return def.icon
    }
    return DefaultShortcuts.autoDetectIcon(kind, payload, closing).ifBlank {
        when (itemType) {
            "snippet" -> "Snip"
            "template" -> "Tmpl"
            else -> "•"
        }
    }
}

/**
 * Resolves the human-friendly description label for a shortcut (used as the title in Shortcut Studio
 * and inside the LABEL field when editing).
 */
fun ShortcutAction.resolvedLabel(): String {
    val def = DefaultShortcuts.findById(id)
    // If this is a legacy persisted shortcut whose `label` still stores the raw icon glyph (e.g. "“ ”")
    if (def != null && (label.isBlank() || label.trim() == def.icon.trim())) {
        return def.label
    }
    return label
}
