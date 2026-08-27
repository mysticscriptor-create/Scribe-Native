package com.primaloptima.scribe.ui.components.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.toComposeColor

enum class EdgeSide { LEFT, RIGHT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeTabRail(
    minimizedPanes: List<PaneConfig>,
    side: EdgeSide,
    isDark: Boolean,
    onTap: (PaneConfig) -> Unit,
    onLongPress: (PaneConfig) -> Unit,
    onOpenGroup: (List<PaneConfig>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (minimizedPanes.isEmpty()) return

    val maxIndividualTabs = 3
    val showGroup = minimizedPanes.size > maxIndividualTabs
    val individualTabs = if (showGroup) minimizedPanes.take(maxIndividualTabs - 1) else minimizedPanes
    val groupedTabs = if (showGroup) minimizedPanes.drop(maxIndividualTabs - 1) else emptyList()

    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = if (side == EdgeSide.LEFT) Alignment.Start else Alignment.End
    ) {
        individualTabs.forEach { pane ->
            EdgeTabItem(
                pane = pane,
                side = side,
                isDark = isDark,
                onTap = { onTap(pane) },
                onLongPress = { onLongPress(pane) }
            )
        }

        if (showGroup && groupedTabs.isNotEmpty()) {
            val groupAccent = groupedTabs.firstOrNull()?.accentColor?.toComposeColor(isDark)
                ?.takeOrElse { MaterialTheme.colorScheme.primary } ?: MaterialTheme.colorScheme.primary

            val shape = if (side == EdgeSide.LEFT) {
                RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
            }

            Surface(
                onClick = { onOpenGroup(groupedTabs) },
                shape = shape,
                color = groupAccent.copy(alpha = if (isDark) 0.22f else 0.15f),
                border = BorderStroke(1.dp, groupAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .width(26.dp)
                    .height(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${groupedTabs.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = groupAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EdgeTabItem(
    pane: PaneConfig,
    side: EdgeSide,
    isDark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = pane.accentColor.toComposeColor(isDark).takeOrElse { MaterialTheme.colorScheme.primary }
    val shape = if (side == EdgeSide.LEFT) {
        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    }

    Surface(
        modifier = modifier
            .width(26.dp)
            .height(84.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = shape,
        color = accent.copy(alpha = if (isDark) 0.25f else 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pip / dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.height(4.dp))
                // Vertical text rotation
                Text(
                    text = pane.label.take(8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (side == EdgeSide.LEFT) -90f else 90f
                    }
                )
            }
        }
    }
}
