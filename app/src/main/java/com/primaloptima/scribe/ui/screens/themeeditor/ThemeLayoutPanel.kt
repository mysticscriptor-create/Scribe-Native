package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Inspector panel for Layout and Formatting in the Theme Editor.
 */
@Composable
fun ThemeLayoutPanel(
    textAlignment: String,
    themeScope: String,
    onTextAlignmentChange: (String) -> Unit,
    onThemeScopeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Text Alignment Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Text Alignment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val alignOptions = listOf(
                        "left" to Icons.AutoMirrored.Filled.FormatAlignLeft,
                        "justified" to Icons.Default.FormatAlignJustify,
                        "center" to Icons.Default.FormatAlignCenter
                    )
                    alignOptions.forEach { (key, icon) ->
                        OutlinedIconToggleButton(
                            checked = textAlignment == key,
                            onCheckedChange = { onTextAlignmentChange(key) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(icon, contentDescription = key, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // Theme Scope Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Theme Scope",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Text(
                    text = "Choose whether this theme applies exclusively to the distraction-free editor canvas or styles the entire application shell.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val scopes = listOf("editor_only" to "Editor Only", "whole_app" to "Whole App")
                    scopes.forEachIndexed { index, (scopeKey, label) ->
                        SegmentedButton(
                            selected = themeScope == scopeKey,
                            onClick = { onThemeScopeChange(scopeKey) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}
