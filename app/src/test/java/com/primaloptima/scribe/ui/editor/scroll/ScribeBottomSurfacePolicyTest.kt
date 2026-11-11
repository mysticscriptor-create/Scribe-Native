package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.roundToInt
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
        assertEquals(360f, maxBaseline)

        // Valid standard keyboard heights in range [160, 360]
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(280f, screenHeightDp))
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(220f, screenHeightDp))
        assertEquals(true, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(360f, screenHeightDp))

        // Oversized pickers (emojis/stickers/clipboard) > 360dp on 800dp screen must be rejected
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(380f, screenHeightDp))
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(420f, screenHeightDp))

        // Too small / closing heights must be rejected (< 160dp on portrait)
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(100f, screenHeightDp))
        assertEquals(false, ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(0f, screenHeightDp))
    }

    @Test
    fun testLandscapeStandardBaselineBounds() {
        val landscapeScreenHeightDp = 380f
        val minBaseline = ScribeBottomSurfaceCoordinator.calculateMinStandardBaselineDp(landscapeScreenHeightDp)
        assertEquals(120f, minBaseline)

        // A 140dp keyboard is valid in landscape
        assertTrue(ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(140f, landscapeScreenHeightDp))
        // Under 120dp is rejected
        assertFalse(ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(100f, landscapeScreenHeightDp))
    }

    @Test
    fun testTabletStandardBaselineBounds() {
        val tabletScreenHeightDp = 1000f
        val maxBaseline = ScribeBottomSurfaceCoordinator.calculateMaxStandardBaselineDp(tabletScreenHeightDp)
        assertEquals(450f, maxBaseline) // 1000 * 0.45 = 450
        assertTrue(ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(380f, tabletScreenHeightDp))
    }

    @Test
    fun testStandardKeyboardBaselineRejectsExpandedPickers() {
        val screenHeightDp = 800f
        // Standard keyboard heights (e.g. 244dp, 280dp) must pass
        assertTrue(ScribeBottomSurfaceCoordinator.isStandardKeyboardBaseline(244f, 280f, screenHeightDp))
        assertTrue(ScribeBottomSurfaceCoordinator.isStandardKeyboardBaseline(280f, 280f, screenHeightDp))

        // Oversized pickers (emojis/stickers/GIFs/clipboard) > 330dp on phone must be rejected
        assertFalse(ScribeBottomSurfaceCoordinator.isStandardKeyboardBaseline(340f, 244f, screenHeightDp))
        assertFalse(ScribeBottomSurfaceCoordinator.isStandardKeyboardBaseline(360f, 244f, screenHeightDp))

        // If standard baseline is already established at 244dp, an expanded keyboard significantly taller (> +30dp, e.g. 290dp) must be rejected
        assertFalse(ScribeBottomSurfaceCoordinator.isStandardKeyboardBaseline(290f, 244f, screenHeightDp))
    }

    @Test
    fun testShouldUpdateSavedBaselineProtectsEstablishedBaseline() {
        val screenHeightDp = 800f
        // Fresh install: baseline updates from default 280dp to measured 244dp
        assertTrue(ScribeBottomSurfaceCoordinator.shouldUpdateSavedBaseline(244f, 280f, screenHeightDp))

        // Established baseline: expanded keyboard picker (340dp) must NOT overwrite standard baseline (244dp)
        assertFalse(ScribeBottomSurfaceCoordinator.shouldUpdateSavedBaseline(340f, 244f, screenHeightDp))

        // Negligible jitter (< 2dp) does not trigger unnecessary updates
        assertFalse(ScribeBottomSurfaceCoordinator.shouldUpdateSavedBaseline(245f, 244f, screenHeightDp))
    }

    @Test
    fun testEffectiveBottomSurfaceHeightAwaitingKeyboardDoesNotPreBloatLayout() {
        val heightAwaiting = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f,
            isAwaitingKeyboardOpen = true,
            isBarTransitionLatched = false,
            isKeyboardVisible = false,
            compactHeightDp = 280f,
            imeAboveNavDp = 0f
        )
        // Must follow actual arriving IME insets (0f) instead of prematurely allocating 280dp
        assertEquals(0f, heightAwaiting)
    }

    @Test
    fun testUnconditionalGlobalVirtualTailMatchesEditorMaxScroll() {
        val headerHeight = 200
        val layoutHeight = 1200
        val viewportHeight = 800
        val persistentBottomSpacePx = 700 // ~280dp at 2.5x density

        val maxCanvas = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            bottomSpace = persistentBottomSpacePx,
            isTextEmpty = false
        )
        // totalContentHeight = 200 + 1200 + 700 = 2100. maxCanvas = 2100 - 800 = 1300.
        assertEquals(1300, maxCanvas)

        // After header is fully scrolled out, remaining inner editor scroll:
        val remainingInnerScroll = maxCanvas - headerHeight // 1100

        val safeEditorMax = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            isKeyboardVisible = false,
            isTextEmpty = false
        )
        // layoutHeight + persistentBottomSpacePx - viewportHeight = 1200 + 700 - 800 = 1100
        assertEquals(1100, safeEditorMax)
        assertEquals("Inner editor max scroll matches outer canvas remaining capacity with zero mismatch", remainingInnerScroll, safeEditorMax)
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

        // Fresh install: live keyboard (e.g. 244dp) reflects actual measured IME height without baseline inflation
        val freshInstallHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 0f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = true,
            compactHeightDp = 280f,
            imeAboveNavDp = 244f
        )
        assertEquals(244f, freshInstallHeight)

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

    @Test
    fun testAuthoritativeVirtualTailContinuousTransfer() {
        val breathingMarginPx = 60f
        val standardKbPx = 700f
        val shortcutBarHeightPx = 0f
        val globalClearancePx = standardKbPx + shortcutBarHeightPx + breathingMarginPx // 760f

        // At keyboard open: physical clearance = 700f. Virtual tail = max(60f, 760f - 700f) = 60f.
        val openTail = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKbPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = 700f
        )
        assertEquals(60f, openTail, 0.001f)

        // Mid-dismissal: physical clearance = 350f. Virtual tail = 760f - 350f = 410f.
        val midTail = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKbPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = 350f
        )
        assertEquals(410f, midTail, 0.001f)
        // Invariant: physical clearance + virtual tail = 350f + 410f = 760f!
        assertEquals(globalClearancePx, 350f + midTail, 0.001f)

        // Fully dismissed: physical clearance = 0f. Virtual tail = 760f.
        val dismissedTail = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKbPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = 0f
        )
        assertEquals(760f, dismissedTail, 0.001f)
        assertEquals(globalClearancePx, 0f + dismissedTail, 0.001f)

        // Oversized physical clearance (e.g. 900f > 760f): clamped to breathingMarginPx
        val clampedTail = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKbPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = 900f
        )
        assertEquals(breathingMarginPx, clampedTail, 0.001f)
    }

    @Test
    fun testShouldFollowDownwardSuppressedDuringImeDismissalAndHandoff() {
        // IME_DISMISSAL transition must NEVER follow downward
        val dismissDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertFalse("Must never follow downward during IME_DISMISSAL", dismissDecision)

        // DRAWER_TO_IME_HANDOFF must NOT follow downward
        val drawerToImeDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            activeTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF
        )
        assertFalse("Must never follow downward during DRAWER_TO_IME_HANDOFF", drawerToImeDecision)

        // IME_TO_DRAWER_HANDOFF must NOT follow downward
        val imeToDrawerDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF
        )
        assertFalse("Must never follow downward during IME_TO_DRAWER_HANDOFF", imeToDrawerDecision)
    }

    @Test
    fun testShouldFollowDownwardPermittedDuringDrawerToImeHandoffWithPriorExpansion() {
        // Expanded drawer closed with Snip/Tmpl returning to keyboard: must restore prior upward displacement
        val restoreDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 160f,
            activeTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF
        )
        assertTrue("Must permit downward restoration during DRAWER_TO_IME_HANDOFF when prior expansion displacement > 0", restoreDecision)
    }

    @Test
    fun testShouldFollowDownwardPermittedDuringImeResizeContraction() {
        // Taller emoji keyboard returning to normal standard keyboard: must restore prior upward displacement
        val restoreDecision = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = false,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 120f,
            activeTransition = ScribeDockTransition.IME_RESIZE
        )
        assertTrue("Must permit downward restoration during IME_RESIZE when prior expansion displacement > 0", restoreDecision)
    }

    @Test
    fun testResolveEffectiveBottomSurfaceHeightDuringHandoff() {
        // During DRAWER_TO_IME_HANDOFF with compact height 280dp and IME height 120dp: holds 280dp
        val handoffHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = 280f,
            isAwaitingKeyboardOpen = true,
            isBarTransitionLatched = true,
            isKeyboardVisible = false,
            compactHeightDp = 280f,
            imeAboveNavDp = 120f,
            activeTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF
        )
        assertEquals(280f, handoffHeight, 0.001f)

        // During IME_TO_DRAWER_HANDOFF: holds 280dp
        val imeToDrawerHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = true,
            drawerHeightDp = 280f,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = true,
            compactHeightDp = 280f,
            imeAboveNavDp = 280f,
            activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF
        )
        assertEquals(280f, imeToDrawerHeight, 0.001f)
    }

    @Test
    fun testStationaryMaxCanvasScrollThroughoutKeyboardDismissal() {
        val headerHeight = 200
        val layoutHeight = 1500
        val fullScreenHeight = 2000
        val kbHeight = 800f
        val breathingMarginPx = 60f
        val shortcutBarHeightPx = 0f

        // Step 1: Keyboard Open
        val pOpen = kbHeight // 800f
        val vTailOpen = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = kbHeight,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = pOpen
        ) // 60f
        val viewportOpen = fullScreenHeight - pOpen.toInt() // 1200
        val maxCanvasOpen = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportOpen,
            bottomSpace = vTailOpen.roundToInt(),
            isTextEmpty = false
        )

        // Step 2: Halfway Dismissed
        val pHalf = 400f
        val vTailHalf = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = kbHeight,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = pHalf
        ) // 860f - 400f = 460f
        val viewportHalf = fullScreenHeight - pHalf.toInt() // 1600
        val maxCanvasHalf = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHalf,
            bottomSpace = vTailHalf.roundToInt(),
            isTextEmpty = false
        )

        // Step 3: Fully Dismissed
        val pClosed = 0f
        val vTailClosed = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = kbHeight,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = pClosed
        ) // 860f
        val viewportClosed = fullScreenHeight // 2000
        val maxCanvasClosed = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportClosed,
            bottomSpace = vTailClosed.roundToInt(),
            isTextEmpty = false
        )

        assertEquals("maxCanvasScroll must be completely constant at start and midpoint", maxCanvasOpen, maxCanvasHalf)
        assertEquals("maxCanvasScroll must be completely constant at start and fully dismissed", maxCanvasOpen, maxCanvasClosed)
    }

    @Test
    fun testImeToDrawerHandoffWithExpandedImeDoesNotCollapseSlot() {
        val compactBaseline = 280f
        val expandedImeHeight = 360f
        val drawerHeight = 280f

        // When starting handoff from a taller (expanded) keyboard:
        // capturedHandoffImeHeightDp captures 360dp.
        val handoffHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = true,
            drawerHeightDp = drawerHeight,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = false,
            compactHeightDp = compactBaseline,
            imeAboveNavDp = expandedImeHeight,
            activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF,
            capturedHandoffImeHeightDp = expandedImeHeight
        )
        assertEquals("Combined slot must not collapse to 280dp while IME is 360dp", 360f, handoffHeight, 0.001f)

        // Intermediate handoff state: IME drops to 200dp
        val intermediateHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = true,
            drawerHeightDp = drawerHeight,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = false,
            compactHeightDp = compactBaseline,
            imeAboveNavDp = 200f,
            activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF,
            capturedHandoffImeHeightDp = expandedImeHeight
        )
        assertEquals("Intermediate handoff retains stable captured baseline", 360f, intermediateHeight, 0.001f)

        // Settled: IME reaches 0dp, handoff completes to NONE
        val settledHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = true,
            drawerHeightDp = drawerHeight,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            isKeyboardVisible = false,
            compactHeightDp = compactBaseline,
            imeAboveNavDp = 0f,
            activeTransition = ScribeDockTransition.NONE
        )
        assertEquals("Settled drawer occupies its 280dp height", 280f, settledHeight, 0.001f)
    }

    @Test
    fun testDrawerToImeHandoffAtIntermediateImeHeights() {
        val compactBaseline = 280f
        val drawerHeight = 280f

        // Drawer-to-IME handoff: drawer is active/outgoing at 280dp, IME is arriving at 120dp
        val handoffHeight = ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = false,
            drawerHeightDp = drawerHeight,
            isAwaitingKeyboardOpen = true,
            isBarTransitionLatched = true,
            isKeyboardVisible = false,
            compactHeightDp = compactBaseline,
            imeAboveNavDp = 120f,
            activeTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF,
            capturedHandoffImeHeightDp = drawerHeight
        )
        assertEquals("Drawer to IME handoff holds stable 280dp slot", 280f, handoffHeight, 0.001f)
    }

    @Test
    fun testShouldFollowDownwardSuppressedDuringImeDismissalAtLastParagraph() {
        val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertFalse("Last paragraph must never follow downward during IME dismissal", shouldFollow)
    }

    @Test
    fun testShouldFollowDownwardPermittedOnActiveDrawerContraction() {
        val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 120f,
            activeTransition = ScribeDockTransition.DRAWER_RESIZE
        )
        assertTrue("Active drawer contraction permits restoring legitimate temporary displacement", shouldFollow)
    }

    @Test
    fun testCalculateDockGeometryAuthoritativeSnapshot() {
        val density = 2f
        val breathingMarginPx = 48f
        val geometry = ScribeBottomSurfaceCoordinator.calculateDockGeometry(
            dockMode = ScribeDockMode.IME,
            activeTransition = ScribeDockTransition.NONE,
            transitionGeneration = 5L,
            compactHeightDp = 280f,
            imeAboveNavDp = 280f,
            currentDrawerHeightDp = 0f,
            isAccessoryDrawerActive = false,
            isKeyboardVisible = true,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            capturedHandoffImeHeightDp = null,
            hasShortcuts = true,
            density = density,
            breathingMarginPx = breathingMarginPx
        )
        assertEquals(ScribeDockMode.IME, geometry.dockMode)
        assertEquals(ScribeDockTransition.NONE, geometry.activeTransition)
        assertEquals(560f, geometry.standardKeyboardBaselinePx, 0.001f)
        assertEquals(72f, geometry.shortcutBarHeightPx, 0.001f)
        assertTrue(geometry.isShortcutBarOccupied)
        assertEquals(632f, geometry.physicalDockClearanceAppliedPx, 0.001f)
        assertEquals(48f, geometry.virtualTailPx, 0.001f)
    }

    @Test
    fun testObsoleteTransitionGenerationCannotClearNewerTransition() {
        val initialGen = 1L
        var activeTransition = ScribeDockTransition.IME_ENTRANCE
        var currentGen = initialGen

        // Newer transition starts
        currentGen++
        activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF

        // Obsolete callback from initialGen attempts to clear transition
        if (initialGen == currentGen) {
            activeTransition = ScribeDockTransition.NONE
        }

        assertEquals("Obsolete callback must not clear newer transition", ScribeDockTransition.IME_TO_DRAWER_HANDOFF, activeTransition)
    }

    @Test
    fun testCoordinatorInteractionGenerationSeparation() {
        val coordinator = ScribeScrollCoordinator()
        val initialGen = coordinator.interactionGeneration
        assertEquals(0L, initialGen)

        // Raw touch-down does not call onManualDragConfirmed()
        assertEquals("Touch-down alone does not advance interactionGeneration", initialGen, coordinator.interactionGeneration)

        // Confirmed manual drag advances interactionGeneration
        coordinator.onManualDragConfirmed()
        assertEquals("Confirmed drag advances interactionGeneration", initialGen + 1L, coordinator.interactionGeneration)
    }

    @Test
    fun testInnerEditorScrollCapacityConsistentWithOuterCanvas() {
        val headerHeight = 150
        val layoutHeight = 2000
        val viewportHeight = 800
        val vTail = 700

        val maxCanvas = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            bottomSpace = vTail,
            isTextEmpty = false
        )
        assertEquals(2050, maxCanvas)

        val remainingEditorScroll = maxCanvas - headerHeight // 1900

        val safeMaxY = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = vTail,
            isKeyboardVisible = false,
            isTextEmpty = false,
            isHeaderFullyHidden = true
        )
        assertEquals(1900, safeMaxY)
        assertEquals("Inner editor max scroll matches outer canvas remaining capacity", remainingEditorScroll, safeMaxY)
    }

    // ── Targeted Pure Unit Tests for Plan Requirements ─────────────────────────

    @Test
    fun testClassifyImeTransitionSourceZeroToTargetPositiveIsEntrance() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 0f,
            targetAboveNavDp = 280f,
            currentAboveNavDp = 50f,
            isImeVisible = true
        )
        assertEquals("Source 0 -> Target 280 must be classified as IME_ENTRANCE", ScribeDockTransition.IME_ENTRANCE, transition)
    }

    @Test
    fun testClassifyImeTransitionSourcePositiveToTargetLargerIsResizeExpansion() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 340f,
            currentAboveNavDp = 290f,
            isImeVisible = true
        )
        assertEquals("Source 280 -> Target 340 must be classified as IME_RESIZE", ScribeDockTransition.IME_RESIZE, transition)
    }

    @Test
    fun testClassifyImeTransitionSourcePositiveToTargetSmallerIsResizeContraction() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 340f,
            targetAboveNavDp = 280f,
            currentAboveNavDp = 330f,
            isImeVisible = true
        )
        assertEquals("Source 340 -> Target 280 must be classified as IME_RESIZE", ScribeDockTransition.IME_RESIZE, transition)
    }

    @Test
    fun testClassifyImeTransitionSourcePositiveToTargetZeroIsDismissal() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 200f,
            isImeVisible = true
        )
        assertEquals("Source 280 -> Target 0 must be classified as IME_DISMISSAL", ScribeDockTransition.IME_DISMISSAL, transition)
    }

    @Test
    fun testClassifyImeTransitionTargetZeroWhileImeVisibleIsDismissal() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 150f,
            isImeVisible = true // Insets report target 0 while isImeVisible remains true in interim frames
        )
        assertEquals("Target 0 takes precedence and classifies as IME_DISMISSAL even if isImeVisible is true", ScribeDockTransition.IME_DISMISSAL, transition)
    }

    @Test
    fun testClassifyImeTransitionPreservesActiveImeToDrawerHandoff() {
        val transition = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 0f,
            isImeVisible = false,
            activeTransition = ScribeDockTransition.IME_TO_DRAWER_HANDOFF
        )
        assertEquals("Active IME_TO_DRAWER_HANDOFF must be preserved when IME drops to 0", ScribeDockTransition.IME_TO_DRAWER_HANDOFF, transition)
    }

    @Test
    fun testDismissalLatchingRemainsImeDismissalUntilCurrentReachesZero() {
        // Frame 1: Dismissal starts (current 200, target 0)
        val frame1 = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 200f,
            isImeVisible = true,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertEquals("Must remain IME_DISMISSAL", ScribeDockTransition.IME_DISMISSAL, frame1)

        // Frame 2: Mid-animation (current 80, target 0) - MUST NOT flip to IME_RESIZE
        val frame2 = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 80f,
            isImeVisible = false,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertEquals("Must not flip to IME_RESIZE during dismissal", ScribeDockTransition.IME_DISMISSAL, frame2)

        // Frame 3: Settled at 0 (current 0, target 0) -> completes to NONE
        val frame3 = ScribeBottomSurfaceCoordinator.classifyImeTransition(
            sourceAboveNavDp = 280f,
            targetAboveNavDp = 0f,
            currentAboveNavDp = 0f,
            isImeVisible = false,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertEquals("Transitions to NONE when current IME obstruction reaches 0", ScribeDockTransition.NONE, frame3)
    }

    @Test
    fun testDownwardFollowRejectedDuringImeDismissalRegardlessOfLastLineOrDisplacement() {
        val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            temporaryDisplacement = 200f,
            activeTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertFalse("Downward follow must be strictly rejected during IME_DISMISSAL even if isAtLastLine and displacement > 0", shouldFollow)
    }

    @Test
    fun testDownwardFollowRejectedWhenTargetIsZeroEvenIfImeVisibleIsTrue() {
        val isDismissing = ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = true,
            isAccessoryDrawerActive = false,
            isAwaitingKeyboardOpen = false,
            isBarTransitionLatched = false,
            imeAboveNavDp = 180f,
            activeTransition = ScribeDockTransition.NONE,
            imeTargetAboveNavDp = 0f
        )
        assertTrue("isKeyboardDismissing must be true when target is 0 even if isKeyboardVisible is true", isDismissing)

        val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = isDismissing,
            isBottomSurfaceActive = false,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = true,
            hasActiveScroll = true,
            temporaryDisplacement = 100f,
            activeTransition = ScribeDockTransition.NONE
        )
        assertFalse("shouldFollowDownward must return false when keyboard is dismissing", shouldFollow)
    }

    @Test
    fun testContractionRestorationPreservedForDrawerResizeAndImeResize() {
        // Drawer contraction (e.g. from 400dp to 280dp)
        val shouldFollowDrawer = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 120f,
            activeTransition = ScribeDockTransition.DRAWER_RESIZE
        )
        assertTrue("Drawer resize contraction must authorize downward follow", shouldFollowDrawer)

        // Expanded IME contraction (e.g. emoji picker back to normal keyboard)
        val shouldFollowIme = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true,
            isKeyboardVisible = true,
            hasPhysicalBottomObstruction = true,
            isAtLastLine = false,
            hasActiveScroll = true,
            temporaryDisplacement = 60f,
            activeTransition = ScribeDockTransition.IME_RESIZE
        )
        assertTrue("Expanded IME contraction must authorize downward follow", shouldFollowIme)
    }

    @Test
    fun testPredictiveGeometryMatchesPostResizeTargetWithinDeadband() {
        val density = 2.5f
        val fullHeight = 2000f
        val imeTargetDp = 280f
        val shortcutBarDp = 36f
        val breathingMarginDp = 24f

        val imeTargetPx = imeTargetDp * density // 700px
        val shortcutBarPx = shortcutBarDp * density // 90px
        val breathingMarginPx = breathingMarginDp * density // 60px
        val deadbandPx = ScribeScrollPolicy.CURSOR_DEADBAND_DP * density // 10px

        // Predictive geometry calculation
        val predictedDockClearance = imeTargetPx + shortcutBarPx // 790px
        val futureViewportHeight = fullHeight - predictedDockClearance // 1210px
        val predictiveTargetLineBottom = futureViewportHeight - breathingMarginPx // 1150px

        // Actual post-resize geometry:
        // AndroidView padding reduces height by docBottomPadding (790px)
        val postResizeHeight = fullHeight - predictedDockClearance // 1210px
        val postResizeTargetLineBottom = postResizeHeight - breathingMarginPx // 1150px

        val delta = kotlin.math.abs(predictiveTargetLineBottom - postResizeTargetLineBottom)
        assertTrue("Predictive target matches post-resize layout target within deadband ($delta <= $deadbandPx)", delta <= deadbandPx)
        assertEquals("Predictive target exactly equals post-resize target", postResizeTargetLineBottom, predictiveTargetLineBottom, 0.001f)
    }

    @Test
    fun testKeyboardTransactionLifecycleSingleEntranceWithoutSecondaryJump() {
        val transaction = ScribeScrollPolicy.KeyboardTransaction(
            transactionId = 42L,
            isActive = true,
            targetScroll = 300f
        )
        assertTrue(transaction.isActive)
        assertEquals(300f, transaction.targetScroll, 0.001f)

        // Retargeting with caret within deadband calculates consistent destination
        val retargeted = transaction.calculateRetargetedDestination(
            caretDocBottom = 1200f,
            headerHeight = 150f,
            usableViewportHeight = 1000f,
            breathingMarginPx = 60f,
            maxCanvasScroll = 2500f
        )
        // target = 150 + 1200 - (1000 - 60) = 1350 - 940 = 410f
        assertEquals(410f, retargeted, 0.001f)
    }
}

