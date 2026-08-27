package com.primaloptima.scribe.ui.components.workbench

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun WorkbenchSplitDivider(
    isHorizontal: Boolean,
    onDrag      : (Float) -> Unit,
    onSwap      : () -> Unit,
    accentColor : Color,
    hazeState   : dev.chrisbanes.haze.HazeState? = null,
    modifier    : Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val dividerBgColor by animateColorAsState(
        targetValue = if (isDragging) accentColor.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(120),
        label = "SplitDividerBg"
    )

    Box(
        modifier = modifier.then(
            if (isHorizontal) {
                Modifier
                    .width(28.dp)
                    .fillMaxHeight()
                    .background(dividerBgColor)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd   = { isDragging = false },
                            onDragCancel= { isDragging = false },
                            onDrag      = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onSwap() })
                    }
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(dividerBgColor)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd   = { isDragging = false },
                            onDragCancel= { isDragging = false },
                            onDrag      = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onSwap() })
                    }
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        // 0.5dp Hairline running through the divider center
        if (isHorizontal) {
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        }

        // Center pill with RoundedCornerShape(50)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = if (isHorizontal) {
                Modifier.width(28.dp).height(36.dp)
            } else {
                Modifier.width(48.dp).height(28.dp)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Swap split",
                    modifier = Modifier.size(16.dp),
                    tint = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
