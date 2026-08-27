package com.primaloptima.scribe.ui.components.workbench

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
        targetValue = if (isDragging) accentColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(120),
        label = "SplitDividerBg"
    )

    val handleColor by animateColorAsState(
        targetValue = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(120),
        label = "SplitHandleColor"
    )

    val handleScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = tween(120),
        label = "SplitHandleScale"
    )

    Box(
        modifier = modifier.then(
            if (isHorizontal) {
                Modifier
                    .width(20.dp)
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
                    .height(20.dp)
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
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
        }

        // Sleek 32x4.dp horizontal handle bar (rotates 90 deg when horizontal split)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = if (isHorizontal) 90f else 0f
                    scaleX = handleScale
                    scaleY = handleScale
                }
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(handleColor)
        )
    }
}
