package com.primaloptima.scribe.ui.editor.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

enum class OverlayOcclusionRole {
    TOP_BAR,
    BOTTOM_BAR,
    FLOATING_PILL,
    MODAL_BLOCKER
}

data class OverlayBoundsEntry(
    val key: String,
    val role: OverlayOcclusionRole,
    val windowRect: Rect
)

/**
 * Shared window-relative overlay occlusion registry across MainEditorScreen,
 * CompactEditorLayout, ExpandedEditorLayout, FloatingWindowOverlay, EditorImagePreviewCard,
 * and UnifiedCanvasLayout.
 *
 * Registrants report physical window coordinates via `coords.boundsInWindow()` and MUST
 * enforce `isVisible && coords.isAttached` plus key-scoped `DisposableEffect` cleanup so
 * hidden, detached, or replaced overlays never leave stale occlusion rects behind.
 */
class ScribeOverlayBoundsRegistry {

    private val lock = Any()
    private val entries = LinkedHashMap<String, OverlayBoundsEntry>()

    fun update(key: String, role: OverlayOcclusionRole, windowRect: Rect) {
        if (!windowRect.left.isFinite() ||
            !windowRect.top.isFinite() ||
            !windowRect.right.isFinite() ||
            !windowRect.bottom.isFinite()
        ) {
            return
        }
        synchronized(lock) {
            entries[key] = OverlayBoundsEntry(
                key = key,
                role = role,
                windowRect = windowRect
            )
        }
    }

    fun remove(key: String) {
        synchronized(lock) {
            entries.remove(key)
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    operator fun get(key: String): OverlayBoundsEntry? = synchronized(lock) {
        entries[key]
    }

    fun contains(key: String): Boolean = synchronized(lock) {
        entries.containsKey(key)
    }

    fun snapshot(): List<OverlayBoundsEntry> = synchronized(lock) {
        entries.values.toList()
    }

    fun hasActiveModalBlocker(): Boolean = synchronized(lock) {
        entries.values.any { it.role == OverlayOcclusionRole.MODAL_BLOCKER }
    }

    /**
     * Maps all active window-relative overlay entries into UnifiedCanvasLayout-local
     * coordinates (`yLocal = windowRect.y - canvasWindowTopPx`) and resolves the single
     * canonical [UnobstructedViewportBounds].
     */
    fun resolveUnobstructedViewportBounds(
        canvasWindowTopPx: Float,
        canvasHeightPx: Float,
        headerBottomLocal: Float,
        stickyBottomLocal: Float = 0f,
        windowVisibleBottomLocal: Float = canvasHeightPx,
        canvasWindowLeftPx: Float = 0f,
        canvasWidthPx: Float = 0f
    ): UnobstructedViewportBounds {
        val currentEntries = snapshot()
        val clampedHeight = canvasHeightPx.coerceAtLeast(0f)
        val clampedHeaderBottom = headerBottomLocal.coerceAtLeast(0f)
        val clampedStickyBottom = stickyBottomLocal.coerceAtLeast(0f)
        val clampedWindowBottom = if (windowVisibleBottomLocal.isFinite()) {
            windowVisibleBottomLocal.coerceIn(0f, clampedHeight)
        } else {
            clampedHeight
        }

        var maxTopBarBottomLocal = 0f
        var minBottomBarTopLocal = clampedHeight
        var hasModalBlocker = false
        val floatingPillRects = ArrayList<Rect>()

        for (entry in currentEntries) {
            val rect = entry.windowRect
            val horizontallyOverlapsCanvas = if (canvasWidthPx > 0f && rect.right > rect.left) {
                rect.right > canvasWindowLeftPx && rect.left < (canvasWindowLeftPx + canvasWidthPx)
            } else {
                true
            }
            if (!horizontallyOverlapsCanvas) {
                continue
            }

            val localTop = rect.top - canvasWindowTopPx
            val localBottom = rect.bottom - canvasWindowTopPx
            val localLeft = rect.left - canvasWindowLeftPx
            val localRight = rect.right - canvasWindowLeftPx

            when (entry.role) {
                OverlayOcclusionRole.TOP_BAR -> {
                    if (localBottom > 0f && localTop < clampedHeight) {
                        maxTopBarBottomLocal = maxOf(
                            maxTopBarBottomLocal,
                            localBottom.coerceIn(0f, clampedHeight)
                        )
                    }
                }
                OverlayOcclusionRole.BOTTOM_BAR -> {
                    if (localTop < clampedHeight && localBottom > 0f) {
                        minBottomBarTopLocal = minOf(
                            minBottomBarTopLocal,
                            localTop.coerceIn(0f, clampedHeight)
                        )
                    }
                }
                OverlayOcclusionRole.FLOATING_PILL -> {
                    floatingPillRects.add(
                        Rect(
                            left = localLeft,
                            top = localTop,
                            right = localRight,
                            bottom = localBottom
                        )
                    )
                }
                OverlayOcclusionRole.MODAL_BLOCKER -> {
                    hasModalBlocker = true
                }
            }
        }

        val visibleEditorTop = maxOf(
            0f,
            clampedHeaderBottom,
            clampedStickyBottom,
            maxTopBarBottomLocal
        )
        val visibleEditorBottom = minOf(
            clampedHeight,
            clampedWindowBottom,
            minBottomBarTopLocal
        )
        val usableHeight = (visibleEditorBottom - visibleEditorTop).coerceAtLeast(0f)
        val isValid = !hasModalBlocker &&
            clampedHeight > 0f &&
            visibleEditorTop.isFinite() &&
            visibleEditorBottom.isFinite() &&
            visibleEditorBottom > visibleEditorTop

        return UnobstructedViewportBounds(
            headerBottomLocal = clampedHeaderBottom,
            stickyBottomLocal = clampedStickyBottom,
            maxTopBarBottomLocal = maxTopBarBottomLocal,
            minBottomBarTopLocal = minBottomBarTopLocal,
            windowVisibleBottomLocal = clampedWindowBottom,
            visibleEditorTop = visibleEditorTop,
            visibleEditorBottom = visibleEditorBottom,
            usableHeight = usableHeight,
            hasModalBlocker = hasModalBlocker,
            isValid = isValid,
            floatingPillLocalRects = floatingPillRects
        )
    }
}

val LocalOverlayBoundsRegistry = staticCompositionLocalOf<ScribeOverlayBoundsRegistry?> { null }

@Composable
fun RegisterOverlayBoundsEffect(
    key: String,
    isVisible: Boolean,
    registry: ScribeOverlayBoundsRegistry? = LocalOverlayBoundsRegistry.current
) {
    if (registry == null) return
    DisposableEffect(registry, key, isVisible) {
        if (!isVisible) {
            registry.remove(key)
        }
        onDispose {
            registry.remove(key)
        }
    }
}

fun Modifier.reportOverlayBounds(
    key: String,
    role: OverlayOcclusionRole,
    isVisible: Boolean,
    registry: ScribeOverlayBoundsRegistry?
): Modifier {
    if (registry == null) return this
    return onGloballyPositioned { coords ->
        if (isVisible && coords.isAttached) {
            registry.update(key, role, coords.boundsInWindow())
        } else {
            registry.remove(key)
        }
    }
}
