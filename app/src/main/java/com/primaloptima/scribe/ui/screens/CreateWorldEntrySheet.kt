package com.primaloptima.scribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.viewmodel.SheetsViewModel

data class CategoryMeta(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

val CATEGORY_META = listOf(
    CategoryMeta("All",      "All",       Icons.Default.GridView,             Color(0xFF9E9E9E)),
    CategoryMeta("character","Characters", Icons.Default.Person,               Color(0xFF5C9EF0)),
    CategoryMeta("location", "Locations",  Icons.Default.Place,                Color(0xFF4CAF82)),
    CategoryMeta("faction",  "Factions",   Icons.Default.Group,                Color(0xFFE07B54)),
    CategoryMeta("item",     "Items",      Icons.Default.Category,             Color(0xFFB07FD4)),
    CategoryMeta("lore",     "Lore",       Icons.AutoMirrored.Filled.MenuBook, Color(0xFFD4A74A)),
    CategoryMeta("timeline", "Timeline",   Icons.Default.Timeline,             Color(0xFF4AB8D4)),
)

fun categoryMeta(key: String): CategoryMeta =
    CATEGORY_META.find { it.key.equals(key, ignoreCase = true) } ?: CATEGORY_META[0]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorldEntrySheet(
    selectedCategory: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val solidSurface = LocalSolidSurface.current

    var name by remember { mutableStateOf("") }
    var type by remember {
        mutableStateOf(if (selectedCategory == "All") "character" else selectedCategory)
    }
    val typeKeys = listOf("character", "location", "faction", "item", "lore", "timeline")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = solidSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
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
                    fontWeight = FontWeight.Bold
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else meta.color
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
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

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Sheet", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
