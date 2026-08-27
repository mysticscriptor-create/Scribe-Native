package com.primaloptima.scribe.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.autoTextColor
import com.primaloptima.scribe.ui.theme.frostedPanel
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * Standard tactile drag handle for [FrostedBottomSheet].
 */
@Composable
fun FrostedSheetDragHandle(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * An in-tree frosted bottom sheet that renders within the main activity window's RenderNode tree.
 * Unlike standard [androidx.compose.material3.ModalBottomSheet] which spawns a detached OS sub-window,
 * [FrostedBottomSheet] maintains seamless access to the root [HazeState] for real-time GPU blur.
 */
@Composable
fun FrostedBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    dragHandle: @Composable (() -> Unit)? = { FrostedSheetDragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissWithAnimation: () -> Unit = {
        isVisible = false
    }

    // Dismiss trigger after slideOut completes
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            kotlinx.coroutines.delay(220)
            onDismissRequest()
        }
    }

    BackHandler(enabled = true) {
        dismissWithAnimation()
    }

    val solidSurface = LocalSolidSurface.current
    val contentColor = autoTextColor(solidSurface)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim backdrop
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        dismissWithAnimation()
                    }
            )
        }

        // Sheet Surface
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 200)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            val draggableState = rememberDraggableState { delta ->
                if (delta > 0 || offsetY > 0) {
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                }
            }

            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = offsetY
                        }
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (offsetY > 180f || velocity > 800f) {
                                    dismissWithAnimation()
                                } else {
                                    offsetY = 0f
                                }
                            }
                        )
                        .frostedPanel(
                            hazeState = hazeState,
                            shape = shape,
                            isDark = isDark
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks */ }
                        .imePadding()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    dragHandle?.invoke()
                    content()
                }
            }
        }
    }
}
