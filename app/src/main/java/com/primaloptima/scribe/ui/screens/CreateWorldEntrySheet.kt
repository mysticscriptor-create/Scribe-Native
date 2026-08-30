package com.primaloptima.scribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.components.FrostedBottomSheet
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.frostedChip
import com.primaloptima.scribe.viewmodel.SheetsViewModel

@Composable
@ReadOnlyComposable
fun categoryColor(key: String): Color {
    val world = ScribeTheme.colors.world
    val subtle = ScribeTheme.colors.content.tertiary
    val fallbackSubtle = if (subtle != Color.Unspecified) subtle else MaterialTheme.colorScheme.onSurfaceVariant
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

val CATEGORY_META = listOf(
    CategoryMeta("All",      "All",       Icons.Default.GridView),
    CategoryMeta("character","Characters", Icons.Default.Person),
    CategoryMeta("location", "Locations",  Icons.Default.Place),
    CategoryMeta("faction",  "Factions",   Icons.Default.Group),
    CategoryMeta("item",     "Items",      Icons.Default.Category),
    CategoryMeta("lore",     "Lore",       Icons.AutoMirrored.Filled.MenuBook),
    CategoryMeta("timeline", "Timeline",   Icons.Default.Timeline),
)

fun categoryMeta(key: String): CategoryMeta =
    CATEGORY_META.find { it.key.equals(key, ignoreCase = true) } ?: CATEGORY_META[0]

@Composable
fun CreateWorldEntrySheet(
    selectedCategory: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember {
        mutableStateOf(if (selectedCategory == "All") "character" else selectedCategory)
    }
    val typeKeys = listOf("character", "location", "faction", "item", "lore", "timeline")
    val hazeState = LocalHazeState.current
    val accentColor = ScribeTheme.colors.interaction.primary
    val subtleText = ScribeTheme.colors.content.tertiary
    val surfaceRaised = ScribeTheme.colors.surfaces.surfaceRaised
    val borderSubtle = ScribeTheme.colors.borders.subtle
    val resolvedSubtle = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    FrostedBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New World Sheet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Category selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select Category",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = resolvedSubtle
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(typeKeys, key = { it }) { key ->
                        val meta = categoryMeta(key)
                        val selected = type == key
                        FilterChip(
                            selected = selected,
                            onClick = { type = key },
                            label = {
                                Text(
                                    text = meta.label.dropLast(1).ifEmpty { meta.label },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else meta.color
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = selected)
                        )
                    }
                }
            }

            // Name field
            val defaultPlaceholder = when (type) {
                "character" -> "e.g. Elena Ravenwood, Lord Kaelen"
                "location"  -> "e.g. The Whispering Forest, Citadel of Dawn"
                "faction"   -> "e.g. Order of the Silver Flame, Night Guild"
                "item"      -> "e.g. Amulet of Shadow, Dragonforged Blade"
                "lore"      -> "e.g. The Great Sundering, Myth of the Starborn"
                "timeline"  -> "e.g. Battle of Red Mountain (Year 402)"
                else        -> "Name or Title"
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name / Title") },
                placeholder = { Text(defaultPlaceholder, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Template preview box
            val previewFields = when (type) {
                "character" -> SheetsViewModel.CHARACTER_FIELDS
                "location"  -> SheetsViewModel.LOCATION_FIELDS
                "faction"   -> SheetsViewModel.FACTION_FIELDS
                "item"      -> SheetsViewModel.ITEM_FIELDS
                "lore"      -> SheetsViewModel.LORE_FIELDS
                "timeline"  -> SheetsViewModel.TIMELINE_FIELDS
                else        -> SheetsViewModel.GENERAL_FIELDS
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (surfaceRaised != Color.Unspecified) surfaceRaised else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        0.7.dp,
                        if (borderSubtle != Color.Unspecified) borderSubtle else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = categoryMeta(type).color,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Included Attributes (${previewFields.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = categoryMeta(type).color
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = previewFields.joinToString(" · ") { it.label },
                        fontSize = 12.sp,
                        color = resolvedSubtle,
                        lineHeight = 18.sp
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(name, type) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Create Sheet", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
