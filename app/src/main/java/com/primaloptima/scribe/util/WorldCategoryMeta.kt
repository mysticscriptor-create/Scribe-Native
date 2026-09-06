package com.primaloptima.scribe.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Phase 6: Canonical World Entity Semantic Token Resolver & Redundant Identifiers.
 *
 * Enforces the canonical pipeline:
 *   World entity meaning -> ScribeTheme.colors.world.* -> Worldbuilding UI
 *
 * Adheres strictly to WCAG 2.2 SC 1.4.1 (non-color redundancy) by pairing each semantic
 * category token with a canonical human-readable label and vector icon.
 */
@Composable
@ReadOnlyComposable
fun categoryColor(key: String): Color {
    val world = ScribeTheme.colors.world
    val fallbackSubtle = ScribeTheme.colors.content.secondary
    return when (key.lowercase()) {
        "character" -> world.character
        "location" -> world.location
        "faction" -> world.faction
        "item" -> world.item
        "lore" -> world.lore
        "timeline", "event" -> world.event
        "relationship" -> world.relationship
        else -> fallbackSubtle
    }
}

@Immutable
data class CategoryMeta(
    val key: String,
    val label: String,
    val icon: ImageVector
) {
    val color: Color
        @Composable
        @ReadOnlyComposable
        get() = categoryColor(key)
}

val CANONICAL_WORLD_CATEGORIES: List<CategoryMeta> = listOf(
    CategoryMeta("All", "All", Icons.Default.GridView),
    CategoryMeta("character", "Characters", Icons.Default.Person),
    CategoryMeta("location", "Locations", Icons.Default.Place),
    CategoryMeta("faction", "Factions", Icons.Default.Group),
    CategoryMeta("item", "Items", Icons.Default.Category),
    CategoryMeta("lore", "Lore", Icons.AutoMirrored.Filled.MenuBook),
    CategoryMeta("timeline", "Timeline", Icons.Default.Timeline),
    CategoryMeta("relationship", "Relationships", Icons.Default.Link)
)

val CATEGORY_META: List<CategoryMeta> = CANONICAL_WORLD_CATEGORIES

fun categoryMeta(key: String): CategoryMeta {
    val lower = key.lowercase()
    return CANONICAL_WORLD_CATEGORIES.find { it.key.equals(lower, ignoreCase = true) }
        ?: if (lower == "event") {
            CANONICAL_WORLD_CATEGORIES.find { it.key == "timeline" } ?: CANONICAL_WORLD_CATEGORIES[0]
        } else {
            CategoryMeta(key, key.replaceFirstChar { it.uppercase() }, Icons.Default.Category)
        }
}
