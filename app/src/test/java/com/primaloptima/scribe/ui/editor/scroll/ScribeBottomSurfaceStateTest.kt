package com.primaloptima.scribe.ui.editor.scroll

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating [ScribeBottomSurfaceState] logic, transition latching,
 * compact baseline clamping, and bottom surface delegation.
 */
class ScribeBottomSurfaceStateTest {

    @Test
    fun testInitialStateDefaults() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        assertEquals(280.dp, state.lastKeyboardHeightDp)
        assertFalse(state.isAwaitingKeyboardOpen)
        assertFalse(state.isBarTransitionLatched)
        assertFalse(state.isKeyboardVisible)
        assertEquals(0.dp, state.imeBottomDp)
        assertEquals(0.dp, state.docBottomNavInset)
        assertEquals(0.dp, state.imeAboveNavDp)
    }

    @Test
    fun testCompactHeightClamping() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.screenHeightDp = 800.dp
        // 60% of 800dp is 480dp. 280dp is between 160dp and 480dp -> 280dp
        assertEquals(280.dp, state.compactHeightDp)

        // Lower bound clamp (e.g., small baseline 100dp -> clamps to 160dp)
        state.lastKeyboardHeightDp = 100.dp
        assertEquals(160.dp, state.compactHeightDp)

        // Upper bound clamp (e.g. large baseline 500dp -> clamps to 480dp on 800dp screen)
        state.lastKeyboardHeightDp = 500.dp
        assertEquals(
            480f,
            state.compactHeightDp.value,
            0.001f
        )
    }

    @Test
    fun testTransitionLatchingLifecycle() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        assertFalse(state.isDrawerOrTransitionActive(isAccessoryDrawerActive = false))

        var callbackInvoked = false
        state.startAwaitingKeyboard(onLatched = { callbackInvoked = true })
        assertTrue(state.isAwaitingKeyboardOpen)
        assertTrue(state.isBarTransitionLatched)
        assertTrue(callbackInvoked)
        assertTrue(state.isDrawerOrTransitionActive(isAccessoryDrawerActive = false))
        assertTrue(state.curIsKeyboardVisible(isAccessoryDrawerActive = false))

        state.cancelAwaitingKeyboard()
        assertFalse(state.isAwaitingKeyboardOpen)
        assertFalse(state.isBarTransitionLatched)
        assertFalse(state.isDrawerOrTransitionActive(isAccessoryDrawerActive = false))
    }

    @Test
    fun testKeyboardEntranceStartedTriggersLatchWhenNotVisible() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.isKeyboardVisible = false
        state.isAwaitingKeyboardOpen = false

        state.onKeyboardEntranceStarted()
        assertTrue(state.isAwaitingKeyboardOpen)
        assertTrue(state.isBarTransitionLatched)
    }

    @Test
    fun testKeyboardEntranceStartedNoOpWhenAlreadyVisible() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.isKeyboardVisible = true
        state.isAwaitingKeyboardOpen = false
        state.isBarTransitionLatched = false

        state.onKeyboardEntranceStarted()
        assertFalse(state.isAwaitingKeyboardOpen)
        assertFalse(state.isBarTransitionLatched)
    }

    @Test
    fun testCurIsBottomSurfaceActiveWithShortcuts() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.isKeyboardVisible = false

        // No shortcuts active -> bottom surface inactive
        assertFalse(state.curIsBottomSurfaceActive(isAccessoryDrawerActive = false, hasShortcuts = false))

        // Keyboard visible and shortcuts present -> active
        state.isKeyboardVisible = true
        assertTrue(state.curIsBottomSurfaceActive(isAccessoryDrawerActive = false, hasShortcuts = true))
        assertFalse(state.curIsBottomSurfaceActive(isAccessoryDrawerActive = false, hasShortcuts = false))

        // Drawer active and shortcuts present -> active
        state.isKeyboardVisible = false
        assertTrue(state.curIsBottomSurfaceActive(isAccessoryDrawerActive = true, hasShortcuts = true))
    }

    @Test
    fun testCalculateEstimatedKeyboardHeightPx() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.densityValue = 2.0f

        // Bottom surface 280dp + shortcut bar 36dp = 316dp * 2.0 = 632px
        val heightWithShortcuts = state.calculateEstimatedKeyboardHeightPx(
            bottomSurfaceHeightDp = 280f,
            hasShortcuts = true
        )
        assertEquals(632f, heightWithShortcuts, 0.001f)

        // Bottom surface 280dp without shortcuts = 280dp * 2.0 = 560px
        val heightWithoutShortcuts = state.calculateEstimatedKeyboardHeightPx(
            bottomSurfaceHeightDp = 280f,
            hasShortcuts = false
        )
        assertEquals(560f, heightWithoutShortcuts, 0.001f)
    }

    @Test
    fun testDelegationToBottomSurfaceCoordinator() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.screenHeightDp = 800.dp
        state.screenHeightDpFloat = 800f
        state.isKeyboardVisible = true
        state.imeAboveNavDp = 270.dp

        // Standard keyboard locked to compactHeightDp (280dp)
        val effectiveHeight = state.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f
        )
        assertEquals(280.dp, effectiveHeight)

        // Keyboard settled when substantial (> 100dp) and visible
        assertTrue(state.isKeyboardSettled())

        // Dismissal is false when visible
        assertFalse(state.isKeyboardDismissing(isAccessoryDrawerActive = false))

        // When IME drops to dismissing state (!visible, imeAboveNav > 0)
        state.isKeyboardVisible = false
        state.imeAboveNavDp = 150.dp
        assertTrue(state.isKeyboardDismissing(isAccessoryDrawerActive = false))
    }

    // ── Phase 7: Accessory Drawer State & Viewport Tests ─────────────────

    @Test
    fun testDrawerInitialStateDefaults() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        assertEquals(null, state.activeAccessoryDrawer)
        assertFalse(state.isAccessoryDrawerActive)
        assertEquals(AccessoryDrawerState.COMPACT, state.drawerState)
        assertFalse(state.isDrawerDragging)
        assertEquals(280f, state.dragLiveHeightDp, 0.001f)
    }

    @Test
    fun testDrawerExpandedAndFullHeightCalculations() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.screenHeightDp = 800.dp
        state.screenHeightDpFloat = 800f

        // compactHeightDp = 280dp
        // expandedHeight = maxOf(280 + 60, (800 * 0.48 = 384).coerceIn(360, 480)) = 384dp
        assertEquals(384f, state.expandedHeightDp.value, 0.001f)

        // fullHeight = (800 * 0.72 = 576).coerceIn(480, 650) = 576dp
        assertEquals(576f, state.fullHeightDp.value, 0.001f)
    }

    @Test
    fun testDrawerSnapTargetStateFlingUp() {
        // Upward fling: vyDp < -300f
        // Below 95% of expanded (0.95 * 384 = 364.8) -> snaps to EXPANDED
        val targetUnderExpanded = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 300f,
            velocityDpPerSec = -400f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.EXPANDED, targetUnderExpanded)

        // At or above 95% of expanded -> snaps to FULL
        val targetAboveExpanded = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 370f,
            velocityDpPerSec = -400f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.FULL, targetAboveExpanded)
    }

    @Test
    fun testDrawerSnapTargetStateFlingDown() {
        // Downward fling: vyDp > 300f
        // Above 105% of expanded (1.05 * 384 = 403.2) -> snaps to EXPANDED
        val targetAboveExpanded = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 500f,
            velocityDpPerSec = 400f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.EXPANDED, targetAboveExpanded)

        // At or below 105% of expanded -> snaps to COMPACT
        val targetBelowExpanded = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 350f,
            velocityDpPerSec = 400f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.COMPACT, targetBelowExpanded)
    }

    @Test
    fun testDrawerSnapTargetStateNeutralNearest() {
        // Neutral fling (|vyDp| <= 300f): snaps to nearest height
        val nearestCompact = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 290f,
            velocityDpPerSec = 0f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.COMPACT, nearestCompact)

        val nearestExpanded = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 370f,
            velocityDpPerSec = 0f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.EXPANDED, nearestExpanded)

        val nearestFull = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = 520f,
            velocityDpPerSec = 0f,
            compactHeightDp = 280f,
            expandedHeightDp = 384f,
            fullHeightDp = 576f
        )
        assertEquals(AccessoryDrawerState.FULL, nearestFull)
    }

    @Test
    fun testDrawerStateCycling() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        assertEquals(AccessoryDrawerState.COMPACT, state.drawerState)

        val next1 = state.cycleDrawerState()
        assertEquals(AccessoryDrawerState.EXPANDED, next1)
        assertEquals(AccessoryDrawerState.EXPANDED, state.drawerState)

        val next2 = state.cycleDrawerState()
        assertEquals(AccessoryDrawerState.FULL, next2)
        assertEquals(AccessoryDrawerState.FULL, state.drawerState)

        val next3 = state.cycleDrawerState()
        assertEquals(AccessoryDrawerState.COMPACT, next3)
        assertEquals(AccessoryDrawerState.COMPACT, state.drawerState)
    }

    @Test
    fun testDrawerOpenCloseAndSwitchTransitions() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        assertFalse(state.isAccessoryDrawerActive)

        // Open snippets drawer
        state.openDrawer(AccessoryDrawerMode.SNIPPETS)
        assertEquals(AccessoryDrawerMode.SNIPPETS, state.activeAccessoryDrawer)
        assertEquals(AccessoryDrawerState.COMPACT, state.drawerState)
        assertTrue(state.isAccessoryDrawerActive)

        // Switch to templates drawer
        state.switchDrawer(AccessoryDrawerMode.TEMPLATES)
        assertEquals(AccessoryDrawerMode.TEMPLATES, state.activeAccessoryDrawer)
        assertTrue(state.isAccessoryDrawerActive)

        // Close drawer
        state.closeDrawer()
        assertEquals(null, state.activeAccessoryDrawer)
        assertEquals(AccessoryDrawerState.COMPACT, state.drawerState)
        assertFalse(state.isAccessoryDrawerActive)
    }

    @Test
    fun testDrawerDragLifecycle() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.screenHeightDp = 800.dp
        state.screenHeightDpFloat = 800f
        state.densityValue = 2.0f

        assertFalse(state.isDrawerDragging)
        state.onDrawerDragStart()
        assertTrue(state.isDrawerDragging)

        // Dragging upward by 40px at density 2.0 = 20dp upward delta
        // dragLiveHeightDp becomes 280 - (-20) = 300dp
        state.onDrawerDragDelta(deltaPx = -40f, densityVal = 2.0f)
        assertEquals(300f, state.dragLiveHeightDp, 0.001f)

        // Clamp to fullHeightDp (576dp) when dragging excessively upward
        state.onDrawerDragDelta(deltaPx = -1000f, densityVal = 2.0f)
        assertEquals(576f, state.dragLiveHeightDp, 0.001f)

        // Clamp to compactHeightDp (280dp) when dragging excessively downward
        state.onDrawerDragDelta(deltaPx = 2000f, densityVal = 2.0f)
        assertEquals(280f, state.dragLiveHeightDp, 0.001f)
    }

    @Test
    fun testEstimatedBottomSurfaceHeightDp() {
        val state = ScribeBottomSurfaceState(initialKeyboardHeightDp = 280.dp)
        state.screenHeightDp = 800.dp
        state.screenHeightDpFloat = 800f
        state.densityValue = 2.0f

        // 1. Keyboard closed, drawer closed -> retain compact keyboard baseline (280dp)
        assertFalse(state.isKeyboardVisible)
        assertFalse(state.isAccessoryDrawerActive)
        assertEquals(280.dp, state.estimatedBottomSurfaceHeightDp)
        // Notice visible spacer is 0dp when closed, while reservation remains 280dp
        assertEquals(0.dp, state.effectiveBottomSurfaceHeight)

        // 2. Keyboard dismissing (isKeyboardVisible = false, imeAboveNavDp > 0) -> retain at least compact baseline
        state.imeAboveNavDp = 150.dp
        assertEquals(280.dp, state.estimatedBottomSurfaceHeightDp)

        // 3. Standard keyboard visible -> retain compact baseline
        state.isKeyboardVisible = true
        state.imeAboveNavDp = 280.dp
        assertEquals(280.dp, state.estimatedBottomSurfaceHeightDp)

        // 4. Oversized keyboard / picker visible -> allow imeAboveNavDp to exceed compact baseline
        state.imeAboveNavDp = 350.dp
        assertEquals(350.dp, state.estimatedBottomSurfaceHeightDp)

        // 5. Accessory drawer active -> use actual drawer height (ignoring compact/ime)
        state.openDrawer(AccessoryDrawerMode.SNIPPETS)
        assertTrue(state.isAccessoryDrawerActive)
        assertEquals(state.effectiveBottomSurfaceHeight, state.estimatedBottomSurfaceHeightDp)

        // 6. Shortcut-bar addition verification in calculateEstimatedKeyboardHeightPx
        val withShortcutsPx = state.calculateEstimatedKeyboardHeightPx(
            bottomSurfaceHeightDp = 280f,
            hasShortcuts = true,
            densityVal = 2.0f
        )
        // (280dp + 36dp) * 2.0 = 632px
        assertEquals(632f, withShortcutsPx, 0.001f)

        val withoutShortcutsPx = state.calculateEstimatedKeyboardHeightPx(
            bottomSurfaceHeightDp = 280f,
            hasShortcuts = false,
            densityVal = 2.0f
        )
        // 280dp * 2.0 = 560px (no 36dp shortcut reservation)
        assertEquals(560f, withoutShortcutsPx, 0.001f)
    }
}
