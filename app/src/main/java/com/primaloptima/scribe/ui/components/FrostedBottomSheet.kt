package com.primaloptima.scribe.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.ScribeShapeTokens
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.autoTextColor
import com.primaloptima.scribe.ui.theme.frostedPanel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
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
            .padding(vertical = ScribeTheme.spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = ScribeTheme.metrics.dragHandleWidth,
                    height = ScribeTheme.metrics.dragHandleHeight
                )
                .background(
                    color = color,
                    shape = ScribeShapeTokens.Handle
                )
        )
    }
}

val LocalFrostedSheetDismiss = compositionLocalOf<(() -> Unit)?> { null }

/**
 * An in-tree frosted bottom sheet that renders within the main activity window's RenderNode tree.
 * Unlike standard [androidx.compose.material3.ModalBottomSheet] which spawns a detached OS sub-window,
 * [FrostedBottomSheet] maintains seamless access to the root [HazeState] for real-time GPU blur.
 *
 * Supports fluid swipe-to-dismiss following the finger with velocity fling detection and spring settle.
 */
@Composable
fun FrostedBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    shape: Shape = ScribeShapeTokens.BottomSheet,
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    dragHandle: @Composable (() -> Unit)? = { FrostedSheetDragHandle() },
    onBackRequest: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val animOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableFloatStateOf(800f) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch {
            animOffsetY.animateTo(
                targetValue = sheetHeightPx,
                animationSpec = tween(durationMillis = 200)
            )
            isVisible = false
            onDismissRequest()
        }
    }

    // Settle logic when drag stops
    val settleSheet: suspend (Float) -> Unit = { velocity ->
        if (animOffsetY.value > 140f || velocity > 750f) {
            animOffsetY.animateTo(
                targetValue = sheetHeightPx,
                animationSpec = tween(durationMillis = 200)
            )
            isVisible = false
            onDismissRequest()
        } else {
            animOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    BackHandler(enabled = true) {
        if (onBackRequest != null) {
            onBackRequest()
        } else {
            dismissWithAnimation()
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (delta > 0 || animOffsetY.value > 0f) {
            coroutineScope.launch {
                animOffsetY.snapTo((animOffsetY.value + delta).coerceAtLeast(0f))
            }
        }
    }

    val nestedScrollConnection = remember(coroutineScope, sheetHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0 && animOffsetY.value > 0f) {
                    val consumed = delta.coerceAtLeast(-animOffsetY.value)
                    coroutineScope.launch { animOffsetY.snapTo(animOffsetY.value + consumed) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0 && available.y > 0) {
                    coroutineScope.launch { animOffsetY.snapTo(animOffsetY.value + delta) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (animOffsetY.value > 0f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (animOffsetY.value > 0f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val solidSurface = LocalSolidSurface.current
    val contentColor = autoTextColor(solidSurface)

    // Calculate dynamic scrim fade as sheet is dragged down
    val dragFraction = if (sheetHeightPx > 0f) (animOffsetY.value / sheetHeightPx).coerceIn(0f, 1f) else 0f
    val scrimAlpha = (1f - dragFraction) * 0.45f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim backdrop with interactive alpha tracking
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
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
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalFrostedSheetDismiss provides dismissWithAnimation
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            sheetHeightPx = coordinates.size.height.toFloat()
                        }
                        .graphicsLayer {
                            translationY = animOffsetY.value
                        }
                        .nestedScroll(nestedScrollConnection)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                coroutineScope.launch { settleSheet(velocity) }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    coroutineScope.launch { settleSheet(velocity) }
                                }
                            )
                    ) {
                        dragHandle?.invoke()
                    }
                    content()
                }
            }
        }
    }
}
