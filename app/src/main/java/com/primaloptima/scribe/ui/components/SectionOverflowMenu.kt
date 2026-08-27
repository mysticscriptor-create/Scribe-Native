package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.localHasBgImage
import com.primaloptima.scribe.util.model.PaneAccentColor
import com.primaloptima.scribe.util.model.PaneConfig
import com.primaloptima.scribe.util.model.PaneScope
import com.primaloptima.scribe.util.model.toComposeColor

@Composable
fun SectionOverflowMenu(
    pane         : PaneConfig,
    isDark       : Boolean,
    onDismiss    : () -> Unit,
    onEditLabel  : () -> Unit,
    onAddRef     : () -> Unit,
    onReferences : () -> Unit,
    onDuplicate  : () -> Unit,
    onFocus      : () -> Unit,
    onAppearance : () -> Unit,
    onScope      : () -> Unit,
    onMinimize   : () -> Unit,
    hazeState    : dev.chrisbanes.haze.HazeState,
) {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val scopeTitle = when (val s = pane.primaryScope) {
        is PaneScope.Global -> "Everywhere"
        is PaneScope.Book -> s.title.ifBlank { "Book" }
        is PaneScope.Folder -> s.title.ifBlank { "Folder" }
        is PaneScope.File -> s.title.ifBlank { "File" }
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(-12, 36),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 210.dp, max = 270.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (hasBgImage) Modifier.frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
                    else Modifier.background(solidSurface, RoundedCornerShape(12.dp))
                ),
            shape = RoundedCornerShape(12.dp),
            color = if (hasBgImage) Color.Transparent else solidSurface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(
                0.8.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // 1. Section Label header (muted, 11sp, tappable -> onEditLabel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(),
                            onClick = onEditLabel
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = pane.label.ifBlank { "Section" }.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Label",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(12.dp)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // 2. Add a Reference
                OverflowMenuItem(
                    label = "Add a Reference",
                    icon = Icons.Default.Add,
                    onClick = onAddRef
                )

                // 3. References >
                OverflowMenuItem(
                    label = "References",
                    icon = Icons.Outlined.FormatListBulleted,
                    hasChevron = true,
                    onClick = onReferences
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // 4. Duplicate
                OverflowMenuItem(
                    label = "Duplicate",
                    icon = Icons.Default.ContentCopy,
                    onClick = onDuplicate
                )

                // 5. Focus
                OverflowMenuItem(
                    label = "Focus",
                    icon = Icons.Default.OpenInFull,
                    onClick = onFocus
                )

                // 6. Appearance > (with color dot)
                OverflowMenuItem(
                    label = "Appearance",
                    leadingCustomIcon = {
                        val accentComposeColor = pane.accentColor.toComposeColor(isDark)
                        Canvas(modifier = Modifier.size(16.dp)) {
                            if (pane.accentColor == PaneAccentColor.NONE) {
                                drawCircle(
                                    color = Color.Gray,
                                    radius = size.minDimension / 2 - 1.dp.toPx(),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            } else {
                                drawCircle(
                                    color = accentComposeColor,
                                    radius = size.minDimension / 2
                                )
                            }
                        }
                    },
                    hasChevron = true,
                    onClick = onAppearance
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // 7. Appears in: scope >
                OverflowMenuItem(
                    label = "Appears in: $scopeTitle",
                    icon = Icons.Default.Adjust,
                    hasChevron = true,
                    onClick = onScope
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )

                // 8. Minimize
                OverflowMenuItem(
                    label = "Minimize",
                    icon = Icons.Default.VerticalAlignBottom,
                    onClick = onMinimize
                )
            }
        }
    }
}

@Composable
private fun OverflowMenuItem(
    label: String,
    icon: ImageVector? = null,
    leadingCustomIcon: (@Composable () -> Unit)? = null,
    hasChevron: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingCustomIcon != null) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingCustomIcon()
            }
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
        }

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (hasChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
