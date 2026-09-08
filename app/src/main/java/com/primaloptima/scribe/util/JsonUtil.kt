package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.AppTheme
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

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
 * Decodes an AppTheme with automatic centralized schema migration and safe fallback.
 */
fun Json.decodeAppTheme(string: String): AppTheme {
    if (string.isBlank()) return DefaultThemes.paper
    return try {
        val decoded = decodeFromString<AppTheme>(string)
        ThemeManager.migrateTheme(decoded)
    } catch (_: Exception) {
        DefaultThemes.paper
    }
}

/**
 * Decodes a list of AppThemes with automatic centralized schema migration.
 * Employs element-by-element recovery if top-level array parsing fails, ensuring
 * that a single corrupted theme does not cause data loss for other user themes.
 */
fun Json.decodeAppThemes(string: String): List<AppTheme> {
    if (string.isBlank() || string.trim() == "[]") return emptyList()
    return try {
        val decodedList = decodeFromString<List<AppTheme>>(string)
        decodedList.map { ThemeManager.migrateTheme(it) }
    } catch (_: Exception) {
        // Resilient fallback: decode individual JSON array elements
        try {
            val jsonArray = parseToJsonElement(string).jsonArray
            jsonArray.mapNotNull { element ->
                try {
                    val theme = decodeFromJsonElement<AppTheme>(element)
                    ThemeManager.migrateTheme(theme)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Encodes an AppTheme ensuring it is migrated to the current schema before serialization.
 */
fun Json.encodeAppTheme(theme: AppTheme): String {
    return encodeToString(ThemeManager.migrateTheme(theme))
}

/**
 * Encodes a list of AppThemes ensuring all themes are migrated to the current schema.
 */
fun Json.encodeAppThemes(themes: List<AppTheme>): String {
    val migrated = themes.map { ThemeManager.migrateTheme(it) }
    return encodeToString(migrated)
}
