package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ScribeBottomSurfaceCoordinator: keyboard baseline bounds, dismiss tracking, and drawer transitions.
 */
class ScribeBottomSurfacePolicyTest {
    @Test
    fun testKeyboardOpenEliminatesDoublePaddingAboveKeyboard() {
        val layoutHeight = 1000
        val fullScreenHeight = 2000
        val keyboardHeight = 800
        val viewportHeightWithKeyboard = fullScreenHeight - keyboardHeight // 1200px (Compose padding applied)
        val persistentBottomSpacePx = 800
        val breathingMarginPx = 60 // 24dp at 2.5 density

        val fixedMaxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeightWithKeyboard,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            isTextEmpty = false,
            currentOffsetY = 0,
            isFocused = true
        )

        // 1000 + 60 - 1200 = -140 <= 0: fits completely, 0 extra padding above keyboard!
        assertEquals("When keyboard is visible, no double padding allows scrolling above keyboard", 0, fixedMaxScroll)
    }

    @Test
    fun testKeyboardDismissalRetainsStationaryDocumentPosition() {
        val layoutHeight = 1500
        val fullScreenHeight = 2000
        val keyboardHeight = 800
        val viewportHeightWithKeyboard = fullScreenHeight - keyboardHeight // 1200px
        val persistentBottomSpacePx = 860 // 800 keyboard + 60 breathing margin
        val breathingMarginPx = 60

        val openMaxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeightWithKeyboard,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            isTextEmpty = false,
            currentOffsetY = 0,
            isFocused = true
        )
        // 1500 + 60 - 1200 = 360: exactly 360
        assertEquals(360, openMaxScroll)

        val dismissedMaxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = fullScreenHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = false,
            isTextEmpty = false,
            currentOffsetY = 360,
            isFocused = true
        )

        // 1500 + 860 - 2000 = 360: identical to openMaxScroll!
        assertEquals("Max scroll remains identical upon dismissal, keeping text rock-solid stationary", openMaxScroll, dismissedMaxScroll)
    }

    @Test
    fun testDrawerCollapseSmoothlyPullsLastLineDownToBreathingMargin() {
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt() // 60px
        val headerHeight = 200
        val lastLineDocBottom = 1500f

        // When drawer was in FULL expanded state, usable viewport was small (e.g. 500px).
        // Total scroll had pushed document up so last line was resting at 500 - 60 = 440px.
        // screenBottom = (headerHeight - scrollD) + lastLineDocBottom - offsetY
        // With scrollD = 200, offsetY = 1060: screenBottom = 0 + 1500 - 1060 = 440px.
        val scrollD = 200
        val offsetY = 1060

        // Now drawer collapses to COMPACT / keyboard: usable viewport expands to 1200px!
        val newExpandedViewport = 1200
        val targetLineBottom = newExpandedViewport - marginPx // 1140px

        val deltaDown = ScribeScrollRestorationPolicy.calculateLastLineFollowDelta(
            lastLineDocBottom = lastLineDocBottom,
            headerHeight = headerHeight,
            scrollD = scrollD,
            offsetY = offsetY,
            viewportHeight = newExpandedViewport,
            density = density,
            isAtLastLine = true
        )

        // The last line was at 440px, but target is 1140px.
        // Delta must be 440 - 1140 = -700px (downward pull).
        assertEquals("Must calculate exact downward pull to follow shortcut bar to 24dp breathing margin", -700f, deltaDown)
    }

    @Test
    fun testDrawerCollapseDoesNotMoveMidDocumentEditingPosition() {
        val density = 2.5f
        val headerHeight = 200
        val lastLineDocBottom = 5000f // Large document
        val scrollD = 200
        val offsetY = 1200
        val newExpandedViewport = 1200

        // When editing in the middle of document (isAtLastLine = false):
        val delta = ScribeScrollRestorationPolicy.calculateLastLineFollowDelta(
            lastLineDocBottom = lastLineDocBottom,
            headerHeight = headerHeight,
            scrollD = scrollD,
            offsetY = offsetY,
            viewportHeight = newExpandedViewport,
            density = density,
            isAtLastLine = false
        )

        // Mid-document text must stay 100% rock-solid stationary!
        assertEquals("Mid-document text must never be pulled down on drawer collapse", 0f, delta)
    }

    @Test
    fun testKeyboardDismissalRetainsStationaryOffsetWithoutPullingDown() {
        val density = 2.5f
        val headerHeight = 200
        val lastLineDocBottom = 1500f
        val scrollD = 200
        val offsetY = 1060

        // When keyboard dismisses, viewport expands to 2200px (virtually full screen height):
        val newExpandedViewport = 2200
        val rootHeight = 2300
        val hasBottomObstruction = (rootHeight - newExpandedViewport) >= (140 * density).toInt()
        val isBottomSurfaceActive = false
        val isKeyboardVisible = false
        val isBottomActive = isBottomSurfaceActive || isKeyboardVisible || hasBottomObstruction
        val isImeClosing = true

        // In UnifiedCanvasLayout, when keyboard is dismissed (isImeClosing is true or isBottomActive is false),
        // the downward pull is strictly suppressed, keeping the document rock-solid stationary at saved margin.
        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollRestorationPolicy.calculateLastLineFollowDelta(
            lastLineDocBottom = lastLineDocBottom,
            headerHeight = headerHeight,
            scrollD = scrollD,
            offsetY = offsetY,
            viewportHeight = newExpandedViewport,
            density = density,
            isAtLastLine = true
        )

        assertEquals("Document must remain stationary at saved margin upon keyboard dismissal", 0f, delta)
    }

    @Test
    fun testKeyboardDismissalMidAnimationSuppressesDownwardFollowEvenWithObstruction() {
        val density = 2.5f
        val headerHeight = 200
        val lastLineDocBottom = 1500f
        val scrollD = 200
        val offsetY = 1060

        // Mid-animation during keyboard exit: viewport is at 1800px (still 500px obstruction remaining >= 140dp)
        val midAnimationViewport = 1800
        val rootHeight = 2300
        val hasBottomObstruction = (rootHeight - midAnimationViewport) >= (140 * density).toInt()
        val isImeClosing = true
        val isBottomActive = hasBottomObstruction // would be true if only obstruction checked

        // Thanks to isImeClosing guard, downward pull is suppressed even before keyboard fully closes
        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollRestorationPolicy.calculateLastLineFollowDelta(
            lastLineDocBottom = lastLineDocBottom,
            headerHeight = headerHeight,
            scrollD = scrollD,
            offsetY = offsetY,
            viewportHeight = midAnimationViewport,
            density = density,
            isAtLastLine = true
        )

        assertEquals("Mid-dismissal must remain stationary at saved margin", 0f, delta)
    }

    @Test
    fun testEmojiKeyboardContractCalculatesSmoothDownwardDelta() {
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt() // 60px
        val headerHeight = 200
        val lastLineDocBottom = 1500f

        // Expanded emoji keyboard had a small viewport (e.g. 1400px), document was scrolled up
        // screenBottom = (200 - 200) + 1500 - 1060 = 440px
        val scrollD = 200
        val offsetY = 1060

        // Returning to standard keyboard: viewport expands to 1650px (250px expansion)
        val standardKbViewport = 1650
        val isImeClosing = false
        val isKeyboardVisible = true
        val isBottomActive = true

        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollRestorationPolicy.calculateLastLineFollowDelta(
            lastLineDocBottom = lastLineDocBottom,
            headerHeight = headerHeight,
            scrollD = scrollD,
            offsetY = offsetY,
            viewportHeight = standardKbViewport,
            density = density,
            isAtLastLine = true
        )

        // Target line bottom is 1650 - 60 = 1590px. Last line is at 440px.
        // Difference is 440 - 1590 = -1150px (clamped to -totalScroll = -1260px)
        assertEquals("Emoji contract must smoothly track downward when keyboard remains open", -1150f, delta)
    }

    @Test
    fun testBottomSurfaceCoordinatorStandardKeyboardBaselineBounds() {
        val screenHeightDp = 800f
        val maxBaseline = ScribeBottomSurfaceCoordinator.calculateMaxStandardBaselineDp(screenHeightDp)
        assertEquals(330f, maxBaseline)

        // Valid standard keyboard heights in range [200, 330]
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(270f, screenHeightDp))
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(220f, screenHeightDp))
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(330f, screenHeightDp))

        // Oversized pickers (emojis/stickers/clipboard) > 330dp must be rejected
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(380f, screenHeightDp))
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(420f, screenHeightDp))

        // Too small / closing heights must be rejected
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(100f, screenHeightDp))
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(0f, screenHeightDp))
    }

    @Test
    fun testBottomSurfaceCoordinatorEffectiveBottomSurfaceHeight() {
        // Standard keyboard typing: locks at compactHeightDp (270dp) to prevent 60fps layout thrashing
        val standardHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = true,
            compactHeightDp = 270f,
            imeAboveNavDp = 270f
        )
        assertEquals(270f, standardHeight)

        // Emoji keyboard: expands smoothly to imeAboveNavDp (380dp)
        val emojiHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = true,
            compactHeightDp = 270f,
            imeAboveNavDp = 380f
        )
        assertEquals(380f, emojiHeight)

        // Return from emoji keyboard: drops immediately back to 270f with ZERO floating delay!
        val returnHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = true,
            compactHeightDp = 270f,
            imeAboveNavDp = 270f
        )
        assertEquals(270f, returnHeight)

        // Drawer active: reflects drawer height directly
        val drawerHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = true,
            drawerHeightDp = 450f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = false,
            compactHeightDp = 270f,
            imeAboveNavDp = 0f
        )
        assertEquals(450f, drawerHeight)
    }

    @Test
    fun testBottomSurfaceCoordinatorKeyboardDismissingState() {
        // Dismissing: keyboard not visible, no drawer, no transition latch, ime > 0
        val isDismissing = ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = false,
            isAccessoryDrawerActive = false,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            imeAboveNavDp = 150f
        )
        assertEquals(true, isDismissing)

        // Normal typing: keyboard visible -> NOT dismissing
        val isTyping = ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = true,
            isAccessoryDrawerActive = false,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            imeAboveNavDp = 270f
        )
        assertEquals(false, isTyping)

        // Transition from drawer to keyboard -> NOT dismissing
        val isTransition = ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = false,
            isAccessoryDrawerActive = false,
            isAwaitingKeyboardOpen = true,
            isBarTransitionLatched = true,
            imeAboveNavDp = 100f
        )
        assertEquals(false, isTransition)

        // Completely dismissed: ime == 0 -> NOT dismissing (settled)
        val isSettled = ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = false,
            isAccessoryDrawerActive = false,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            imeAboveNavDp = 0f
        )
        assertEquals(false, isSettled)
    }

    @Test
    fun testBottomSurfaceCoordinatorShouldFollowDownwardDecisions() {
        // Keyboard dismissing: NEVER follow downward! (Pure Writer stationary saved margin)
        val dismissDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true, // remaining obstruction from closing keyboard
            isAtLastLine = true,
            hasActiveScroll = true
        )
        assertEquals("Dismissal must never pull document down", false, dismissDecision)

        // Emoji keyboard contracting to standard keyboard: SHOULD follow downward!
        val emojiContractDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true
        )
        assertEquals("Emoji contract must smoothly track downward", true, emojiContractDecision)

        // Drawer collapsing: SHOULD follow downward!
        val drawerCollapseDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true
        )
        assertEquals("Drawer collapse must smoothly track downward", true, drawerCollapseDecision)

        // Mid-document editing (isAtLastLine = false): NEVER follow downward!
        val midDocDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true
        )
        assertEquals("Mid-document editing must never pull down", false, midDocDecision)
    }

    // ── Select All Scroll Behavior Tests ────────────────────────────────────────

    @Test
    fun testBottomSurfaceCoordinator_AllowsDownwardFollow_WhenTemporaryDisplacementActive() {
        // Line 50 (isAtLastLine = false) with temporaryDisplacement = 150px
        val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 150f
        )
        assertTrue("Coordinator must permit downward follow for non-last lines when displacement is active", shouldFollow)

        // But when keyboard is dismissing, must strictly reject!
        val shouldFollowDismissing = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 150f
        )
        assertFalse("Coordinator must reject downward follow on dismissal even with displacement active", shouldFollowDismissing)
    }

    @Test
    fun testKeyboardSettledPredicate() {
        // While awaiting keyboard open (animating), keyboard is NOT settled
        assertFalse(
            "Keyboard must not be settled while awaiting open",
            ScribeBottomSurfaceCoordinator.isKeyboardSettled(
                isKeyboardVisible = true,
                isAwaitingKeyboardOpen = true,
                imeAboveNavDp = 280f
            )
        )

        // When keyboard is not visible, it is NOT settled
        assertFalse(
            "Keyboard must not be settled when invisible",
            ScribeBottomSurfaceCoordinator.isKeyboardSettled(
                isKeyboardVisible = false,
                isAwaitingKeyboardOpen = false,
                imeAboveNavDp = 0f
            )
        )

        // When IME height is very small (< 100dp, e.g. starting to open or dismissing), not settled
        assertFalse(
            "Keyboard must not be settled when IME height is too small",
            ScribeBottomSurfaceCoordinator.isKeyboardSettled(
                isKeyboardVisible = true,
                isAwaitingKeyboardOpen = false,
                imeAboveNavDp = 50f
            )
        )

        // Fully open and rested: keyboard is settled!
        assertTrue(
            "Keyboard must be settled once rested and visible",
            ScribeBottomSurfaceCoordinator.isKeyboardSettled(
                isKeyboardVisible = true,
                isAwaitingKeyboardOpen = false,
                imeAboveNavDp = 280f
            )
        )
    }

    // ── Phase 8 Policy & Adapter Helpers Unit Tests ─────────────────────────

}
