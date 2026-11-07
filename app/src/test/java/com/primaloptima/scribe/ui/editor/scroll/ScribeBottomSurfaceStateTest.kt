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
}
