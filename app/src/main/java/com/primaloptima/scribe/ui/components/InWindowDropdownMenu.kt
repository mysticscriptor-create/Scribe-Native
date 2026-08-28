package com.primaloptima.scribe.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.autoTextColor
import com.primaloptima.scribe.ui.theme.frostedMenu
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Holds the state of currently active in-window dropdown menus.
 */
class InWindowMenuHostState {
    var activeMenu by mutableStateOf<InWindowMenuData?>(null)
        private set

    fun showMenu(menu: InWindowMenuData) {
        activeMenu = menu
    }

    fun dismissMenu(menuId: String? = null) {
        if (menuId == null || activeMenu?.id == menuId) {
            val dismissCallback = activeMenu?.onDismissRequest
            activeMenu = null
            dismissCallback?.invoke()
        }
    }
}

/**
 * Data bundle describing an in-window dropdown menu instance.
 */
data class InWindowMenuData(
    val id: String = UUID.randomUUID().toString(),
    val anchorBoundsInWindow: Rect,
    val offset: DpOffset = DpOffset.Zero,
    val shape: Shape = RoundedCornerShape(14.dp),
    val onDismissRequest: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit
)

/**
 * CompositionLocal providing access to the root in-window menu host.
 */
val LocalInWindowMenuHost = compositionLocalOf<InWindowMenuHostState?> { null }

/**
 * Root host overlay that renders anchored dropdown menus directly within the main Activity window's
 * Compose RenderNode tree.
 *
 * Benefits:
 * - On API 31+ (Android 12+): 100% Pure GPU Haze blur with zero popup sub-window isolation.
 * - On API < 31 (Android 7-11): Perfectly aligned StackBlur backdrop sampling with zero coordinate offsets.
 * - Non-image themes: High-contrast solid theme surfaces with zero transparency glitches.
 */
@Composable
fun InWindowMenuHost(
    hostState: InWindowMenuHostState,
    modifier: Modifier = Modifier
) {
    val activeMenu = hostState.activeMenu ?: return
    val hazeState = LocalHazeState.current
    val solidSurface = LocalSolidSurface.current
    val isDark = LocalAppTheme.current?.isDark == true
    val density = LocalDensity.current

    BackHandler(enabled = true) {
        hostState.dismissMenu()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val marginPx = with(density) { 8.dp.toPx() }
        val maxMenuHeightDp = (max(200f, screenHeightPx - marginPx * 4) / density.density).dp

        var menuSizePx by remember(activeMenu.id) { mutableStateOf(IntSize(0, 0)) }

        val anchor = activeMenu.anchorBoundsInWindow
        val offsetPxX = with(density) { activeMenu.offset.x.toPx() }
        val offsetPxY = with(density) { activeMenu.offset.y.toPx() }

        // Compute optimal horizontal placement & transform origin
        val isRightAligned = anchor.right > screenWidthPx / 2f
        val horizontalOrigin = if (isRightAligned) 1f else 0f
        val targetX = if (isRightAligned) {
            anchor.right - menuSizePx.width + offsetPxX
        } else {
            anchor.left + offsetPxX
        }
        val clampedX = max(marginPx, min(targetX, screenWidthPx - menuSizePx.width - marginPx))

        // Compute optimal vertical placement & transform origin
        val spaceBelow = screenHeightPx - anchor.bottom
        val spaceAbove = anchor.top
        val fitsBelow = (anchor.bottom + menuSizePx.height + offsetPxY) <= (screenHeightPx - marginPx)
        val fitsAbove = (anchor.top - menuSizePx.height - offsetPxY) >= marginPx

        val openBelow = fitsBelow || (spaceBelow >= spaceAbove && !fitsAbove)
        val verticalOrigin = if (openBelow) 0f else 1f
        val targetY = if (openBelow) {
            anchor.bottom + offsetPxY
        } else {
            anchor.top - menuSizePx.height - offsetPxY
        }
        val clampedY = max(marginPx, min(targetY, screenHeightPx - menuSizePx.height - marginPx))

        // Full-screen transparent touch dismiss layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeMenu.id) {
                    detectTapGestures {
                        hostState.dismissMenu(activeMenu.id)
                    }
                }
        )

        // Anchored Menu Container
        val transitionState = remember(activeMenu.id) {
            MutableTransitionState(false).apply { targetState = true }
        }

        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.85f,
                        transformOrigin = TransformOrigin(horizontalOrigin, verticalOrigin),
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                    ),
            exit = fadeOut(animationSpec = tween(90)) +
                    scaleOut(
                        targetScale = 0.88f,
                        transformOrigin = TransformOrigin(horizontalOrigin, verticalOrigin),
                        animationSpec = tween(90)
                    ),
            modifier = Modifier
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .onGloballyPositioned { coords ->
                    if (coords.size.width > 0 && coords.size.height > 0 && coords.size != menuSizePx) {
                        menuSizePx = coords.size
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 120.dp, max = 280.dp)
                    .heightIn(max = maxMenuHeightDp)
                    .frostedMenu(hazeState = hazeState, shape = activeMenu.shape, isDark = isDark)
                    .pointerInput(activeMenu.id) {
                        detectTapGestures { /* consume taps inside menu */ }
                    }
            ) {
                val contentColor = autoTextColor(solidSurface)
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(vertical = 6.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        activeMenu.content(this)
                    }
                }
            }
        }
    }
}

/**
 * In-window anchored dropdown menu with cross-API support.
 *
 * When [LocalInWindowMenuHost] is available (inside [ScribeTheme]), the menu is rendered
 * in-window at the root layer to guarantee access to the [HazeState] RenderNode tree on API 31+
 * and exact StackBlur coordinates on API < 31.
 */
@Composable
fun FrostedInWindowDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val menuHost = LocalInWindowMenuHost.current
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val menuId = remember { UUID.randomUUID().toString() }

    // Anchor position tracker placed alongside trigger button
    Spacer(
        modifier = Modifier
            .size(0.dp)
            .onGloballyPositioned { coords ->
                val parentCoords = coords.parentCoordinates
                if (parentCoords != null) {
                    val pos = parentCoords.positionInWindow()
                    val size = parentCoords.size
                    anchorBounds = Rect(
                        left = pos.x,
                        top = pos.y,
                        right = pos.x + size.width,
                        bottom = pos.y + size.height
                    )
                } else {
                    val pos = coords.positionInWindow()
                    anchorBounds = Rect(
                        left = pos.x,
                        top = pos.y,
                        right = pos.x,
                        bottom = pos.y
                    )
                }
            }
    )

    if (menuHost != null) {
        DisposableEffect(expanded, anchorBounds) {
            if (expanded && anchorBounds != null) {
                menuHost.showMenu(
                    InWindowMenuData(
                        id = menuId,
                        anchorBoundsInWindow = anchorBounds!!,
                        offset = offset,
                        shape = shape,
                        onDismissRequest = onDismissRequest,
                        content = content
                    )
                )
            } else if (!expanded) {
                menuHost.dismissMenu(menuId)
            }
            onDispose {
                menuHost.dismissMenu(menuId)
            }
        }
    } else {
        // Fallback to standard DropdownMenu if host is unavailable
        val hazeState = LocalHazeState.current
        val solidSurface = LocalSolidSurface.current
        val isDark = LocalAppTheme.current?.isDark == true

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.frostedMenu(hazeState = hazeState, shape = shape, isDark = isDark),
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            shape = shape,
            containerColor = Color.Transparent,
            content = {
                val contentColor = autoTextColor(solidSurface)
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    content()
                }
            }
        )
    }
}
