package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * Clean Sora [EmptyLanguage] subclass for rich novel and prose writing.
 *
 * Provides smart symbol pairing and quote completion:
 *  1. Auto-pair    — typing ( inserts () and places cursor between them
 *  2. Skip-over    — typing ) when already before ) moves cursor forward
 *  3. Paired BS    — Backspace on open char deletes both when pair is empty
 *
 * Handled natively by Sora's [SymbolPairMatch] system with full typography support.
 */
class ScribeProseLanguage : EmptyLanguage() {

    private val pairs = SymbolPairMatch().apply {
        // ASCII pairs — String literals required for SymbolPair constructor
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        // Typographical curly quotes & guillemets
        putPair('\u201C', SymbolPairMatch.SymbolPair("\u201C", "\u201D"))  // “ ”
        putPair('\u2018', SymbolPairMatch.SymbolPair("\u2018", "\u2019"))  // ‘ ’
        putPair('\u00AB', SymbolPairMatch.SymbolPair("\u00AB", "\u00BB"))  // « »
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs
}
