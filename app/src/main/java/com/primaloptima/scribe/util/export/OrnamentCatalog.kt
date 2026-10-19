package com.primaloptima.scribe.util.export

/**
 * Single Source of Truth (SSoT) for all manuscript ornament divider styles across Scribe.
 * Maps ornament identifiers to:
 * 1. Plain text / Unicode divider strings (used in TXT and DOCX)
 * 2. Resolution-independent SVG vector markup (used in HTML and EPUB)
 * 3. Human-readable display labels
 */
object OrnamentCatalog {

    data class OrnamentEntry(
        val id: String,
        val displayName: String,
        val textDivider: String,
        val svgMarkup: String
    )

    val entries: List<OrnamentEntry> = listOf(
        OrnamentEntry(
            id = "classic_diamond",
            displayName = "Classic Diamond",
            textDivider = "─── ❖ ───",
            svgMarkup = """
                <svg width="160" height="20" viewBox="0 0 160 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="10" x2="68" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M80 3.5 L87 10 L80 16.5 L73 10 Z" fill="currentColor"/>
                  <circle cx="70" cy="10" r="1.3" fill="currentColor"/>
                  <circle cx="90" cy="10" r="1.3" fill="currentColor"/>
                  <line x1="92" y1="10" x2="140" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "fleuron",
            displayName = "Fleuron",
            textDivider = "─── ❦ ───",
            svgMarkup = """
                <svg width="140" height="24" viewBox="0 0 140 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="55" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 4 C65 8, 62 14, 70 20 C78 14, 75 8, 70 4 Z" fill="currentColor"/>
                  <path d="M70 12 C64 12, 58 8, 62 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M70 12 C76 12, 82 8, 78 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <line x1="85" y1="12" x2="125" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "asterism",
            displayName = "Asterism",
            textDivider = "─── ⁂ ───",
            svgMarkup = """
                <svg width="120" height="22" viewBox="0 0 120 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="45" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <text x="60" y="8" font-size="12" text-anchor="middle" fill="currentColor" font-family="serif">⁂</text>
                  <line x1="75" y1="11" x2="105" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "celtic_knot",
            displayName = "Celtic Knot",
            textDivider = "─── ⌘ ───",
            svgMarkup = """
                <svg width="150" height="22" viewBox="0 0 150 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="52" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <rect x="68" y="4" width="14" height="14" rx="2" transform="rotate(45 75 11)" stroke="currentColor" stroke-width="1.4" fill="none"/>
                  <circle cx="75" cy="11" r="4" stroke="currentColor" stroke-width="1" fill="none"/>
                  <line x1="98" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "minimal_rule",
            displayName = "Minimal Rule",
            textDivider = "────── • ──────",
            svgMarkup = """
                <svg width="160" height="12" viewBox="0 0 160 12" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="6" x2="74" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                  <circle cx="80" cy="6" r="2" fill="currentColor"/>
                  <line x1="86" y1="6" x2="140" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "quatrefoil",
            displayName = "Quatrefoil",
            textDivider = "─── ✤ ───",
            svgMarkup = """
                <svg width="150" height="24" viewBox="0 0 150 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="58" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <circle cx="75" cy="8" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="16" r="3.5" fill="currentColor"/>
                  <circle cx="71" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="79" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="12" r="1.5" fill="white"/>
                  <line x1="92" y1="12" x2="135" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "laurel_branch",
            displayName = "Laurel Branch",
            textDivider = "─── ⸙ ───",
            svgMarkup = """
                <svg width="160" height="22" viewBox="0 0 160 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="55" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 11 C72 7, 78 7, 80 11 C78 15, 72 15, 70 11 Z" fill="currentColor"/>
                  <circle cx="65" cy="11" r="2" fill="currentColor"/>
                  <circle cx="85" cy="11" r="2" fill="currentColor"/>
                  <line x1="95" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "none",
            displayName = "None",
            textDivider = "",
            svgMarkup = ""
        )
    )

    private val mapById = entries.associateBy { it.id }

    fun getById(id: String): OrnamentEntry =
        mapById[id] ?: mapById["classic_diamond"]!!

    fun getTextDivider(id: String): String =
        getById(id).textDivider

    fun getSvgMarkup(id: String): String =
        getById(id).svgMarkup
}
