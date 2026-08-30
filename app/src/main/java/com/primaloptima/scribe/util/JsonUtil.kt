package com.primaloptima.scribe.util

import kotlinx.serialization.json.Json

/**
 * App-wide Json instance for kotlinx.serialization.
 *
 * ignoreUnknownKeys = true  — Silently skips unknown fields (same as Gson's default).
 *                             Critical for AppTheme backward-compat: old JSON missing
 *                             `savedBgLuminance` will default to -1f cleanly.
 * encodeDefaults = true     — Fields with Kotlin default values are written to JSON.
 *                             Without this, optional fields like `closing = null`
 *                             would be omitted and lost on round-trip.
 * coerceInputValues = true  — If a non-null field receives `null` in JSON, use the
 *                             Kotlin default instead of throwing. Safe fallback.
 */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * Decodes an AppTheme with automatic centralized schema migration.
 */
fun Json.decodeAppTheme(string: String): AppTheme {
    val decoded = decodeFromString<AppTheme>(string)
    return ThemeManager.migrateTheme(decoded)
}

/**
 * Decodes a list of AppThemes with automatic centralized schema migration.
 */
fun Json.decodeAppThemes(string: String): List<AppTheme> {
    val decodedList = decodeFromString<List<AppTheme>>(string)
    return decodedList.map { ThemeManager.migrateTheme(it) }
}
