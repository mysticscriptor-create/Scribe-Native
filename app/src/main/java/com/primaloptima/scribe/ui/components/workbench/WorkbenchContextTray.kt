package com.primaloptima.scribe.ui.components.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun WorkbenchContextTray(
    trayExpanded   : Boolean,
    onToggleExpand : () -> Unit,
    waitingCount   : Int,
    removeMode     : Boolean,
    onToggleRemove : () -> Unit,
    onAddSection   : () -> Unit,
    onOpenSettings : () -> Unit,
    modifier       : Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand / collapse handle
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (trayExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (trayExpanded) "Collapse Context Tray" else "Expand Context Tray",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                // Waiting badge indicator if any waiting panes
                if (waitingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$waitingCount waiting",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Remove Mode Toggle
                IconButton(
                    onClick = onToggleRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Toggle Remove Mode",
                        tint = if (removeMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Add Section Button
                IconButton(
                    onClick = onAddSection,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Section",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Workbench Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Workbench Settings",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded Tray Content
            AnimatedVisibility(visible = trayExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Workbench Controls",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddSection,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Section", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Settings", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
