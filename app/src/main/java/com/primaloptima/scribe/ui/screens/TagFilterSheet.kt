package com.primaloptima.scribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.components.FrostedBottomSheet
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedSearchBox

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterSheet(
    allTagsWithCount: Map<String, Int>,
    initiallySelectedTags: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    var selectedTags by remember { mutableStateOf(initiallySelectedTags) }
    var tagSearchQuery by remember { mutableStateOf("") }

    val filteredTags = remember(allTagsWithCount, tagSearchQuery) {
        if (tagSearchQuery.isBlank()) {
            allTagsWithCount.toList().sortedByDescending { it.second }
        } else {
            allTagsWithCount.toList().filter { (tag, _) ->
                tag.contains(tagSearchQuery, ignoreCase = true)
            }.sortedByDescending { it.second }
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filter by Tags",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedTags.isNotEmpty()) {
                    TextButton(onClick = { selectedTags = emptySet() }) {
                        Text("Clear All (${selectedTags.size})", fontSize = 13.sp)
                    }
                }
            }

            // Search input for tags
            if (allTagsWithCount.size > 8) {
                val hazeState = LocalHazeState.current
                OutlinedTextField(
                    value = tagSearchQuery,
                    onValueChange = { tagSearchQuery = it },
                    placeholder = { Text("Search tags…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (tagSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { tagSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().frostedSearchBox(hazeState, shape = RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Tags cloud
            if (allTagsWithCount.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tags added to any sheets yet.\nAdd tags when editing characters or locations!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else if (filteredTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tags matching \"$tagSearchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        filteredTags.forEach { (tag, count) ->
                            val isSelected = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                },
                                label = {
                                    Text(
                                        text = "$tag ($count)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // Apply Button
            Button(
                onClick = { onApply(selectedTags) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedTags.isEmpty()) "Show All Sheets"
                    else "Apply Filter (${selectedTags.size} selected)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
