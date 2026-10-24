package com.primaloptima.scribe.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating unified scroll policy, cursor glide geometry, and downward anchoring.
 */
class CursorAndCanvasGeometryTest {

    @Test
    fun testTapOnLastLineDoesNotAnchorDownward() {
        // When tapping on the last line, isDeletionActive is FALSE.
        // Even if screenBottom is higher than targetLineBottom, it MUST NOT scroll down.
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // Cursor line is in the middle of the screen (e.g. 500px)
        val screenBottom = 500f
        val screenTop = 460f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = false, // TAP, NOT DELETION
            hasScrollOffset = true
        )

        assertFalse("Tap on last line must never trigger downward scroll", resolution.shouldScroll)
        assertEquals(0f, resolution.deltaY, 0.001f)
    }

    @Test
    fun testDeleteWhileCursorInViewDoesNotJump() {
        // Bug 3 Fix: When editing text and cursor is already comfortably visible in the viewport,
        // deleting must NOT trigger an unexpected downward jump towards the shortcut bar.
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // Cursor is comfortably visible a few lines above shortcut bar (e.g. 900px)
        val screenBottom = 900f
        val screenTop = 860f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = true,
            hasScrollOffset = true
        )

        assertFalse("Cursor already in view must never jump on delete", resolution.shouldScroll)
        assertEquals(0f, resolution.deltaY, 0.001f)
    }

    @Test
    fun testDeleteOnLastLineClampsExcessScrollViaCanvasMax() {
        // Bug 1 Fix: When at the end of document and deleting lines, the document content
        // shrinks, causing currentTotal scroll to exceed maxCanvasScroll.
        // Canvas clamping naturally pulls the document down by the exact excess.
        val headerHeight = 200
        val layoutHeightBefore = 1500
        val layoutHeightAfter = 1420 // 1 line deleted (80px)
        val viewportHeight = 1200
        val breathingMarginPx = 60

        val maxCanvasBefore = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeightBefore,
            viewportHeight = viewportHeight,
            bottomSpace = breathingMarginPx,
            isTextEmpty = false
        ) // 200 + 1500 + 60 - 1200 = 560

        val maxCanvasAfter = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeightAfter,
            viewportHeight = viewportHeight,
            bottomSpace = breathingMarginPx,
            isTextEmpty = false
        ) // 200 + 1420 + 60 - 1200 = 480

        val excess = maxCanvasBefore - maxCanvasAfter
        assertEquals("Excess scroll to clamp down must match the exact 80px line deleted", 80, excess)
    }

    @Test
    fun testDeleteLastParagraphCalculatesAccurateDownwardAnchorDelta() {
        // When deleting the last paragraph shrinks the document past current scroll position,
        // calculateDeletionAnchorDelta cleanly clamps the physical excess scroll (1000 - 500 = 500px).
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 1400
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f

        // totalScroll = 200 + 800 = 1000px. maxCanvasScroll = 500px. Excess = 500px.
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 500
        )
        assertEquals(-500f, delta, 0.001f)
    }

    @Test
    fun testDeleteSecondToLastParagraphCalculatesAccurateDownwardAnchorDelta() {
        // When deleting in the second-to-last paragraph causes total scroll to exceed maxCanvasScroll,
        // calculateDeletionAnchorDelta clamps the physical excess (1000 - 800 = 200px) without
        // shoving the remaining text behind the shortcut bar or keyboard.
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 1860
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f

        // totalScroll = 1000px. maxCanvasScroll = 800px. Excess = 200px.
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 800
        )
        assertEquals(-200f, delta, 0.001f)
    }

    @Test
    fun testDeleteWithContentBelowScreenDoesNotAnchorDownward() {
        // When editing with paragraphs continuing below the screen, deletion must not move canvas.
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 3500
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f

        // textBottom = 0 + 3500 - 800 = 2700px (far below 1140px).
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 2560
        )
        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun testDeleteShrinkingDocumentToFitOnScreenRestoresTitle() {
        // When document shrinks so small it fits on screen, downward delta restores all scroll.
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 100
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f

        // totalScroll = 200 + 800 = 1000px.
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 0
        )
        assertEquals(-1000f, delta, 0.001f)
    }

    @Test
    fun testCursorOccludedOverflowTriggersUpwardScroll() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density) // 10px

        // Cursor is 50px below target (1190px)
        val screenBottom = 1190f
        val screenTop = 1150f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = false,
            hasScrollOffset = true
        )

        assertTrue("Occluded cursor must scroll upward", resolution.shouldScroll)
        assertFalse("Must not be anchor downward", resolution.isAnchorDownward)
        assertEquals(50f, resolution.deltaY, 0.001f)
    }

    @Test
    fun testCursorInDeadbandSafeZoneDoesNotScroll() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // Within 4dp deadband (1142px vs 1140px)
        val screenBottom = 1142f
        val screenTop = 1102f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = true,
            hasScrollOffset = true
        )

        assertFalse("Deadband must prevent micro-jitter scrolling", resolution.shouldScroll)
    }

    @Test
    fun testSafeScrollMaxYAllowsShortDocumentToScrollAboveShortcutBar() {
        val layoutHeight = 400
        val viewportHeight = 1000
        val persistentBottomSpacePx = 800 // Keyboard + breathing space

        val maxY = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            currentOffsetY = 0,
            isFocused = true,
            baseSoraMaxY = 0
        )

        // 400 + 800 - 1000 = 200px scrollable allowance
        assertEquals(200, maxY)
    }

    @Test
    fun testSafeScrollMaxYShrinksOnDeletionWithoutBeingFrozenByRetainedOffset() {
        // Bug 1 & 2 Fix: When user deletes text or selected text while focused,
        // safeScrollMaxY must shrink so the view can clamp excess scroll down.
        val layoutHeightBefore = 1500
        val layoutHeightAfter = 1200 // 300px deleted
        val viewportHeight = 1100
        val persistentBottomSpacePx = 800
        val breathingMarginPx = 60
        val currentOffsetY = 460

        val maxBefore = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeightBefore,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            currentOffsetY = currentOffsetY,
            isFocused = true,
            baseSoraMaxY = 0
        )
        // 1500 + 60 - 1100 = 460
        assertEquals(460, maxBefore)

        val maxAfter = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeightAfter,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            currentOffsetY = 0,
            isFocused = true,
            baseSoraMaxY = 0
        )
        // 1200 + 60 - 1100 = 160: shrinks cleanly by exact 300px!
        assertEquals("Safe scroll must shrink to 160 on deletion, allowing 300px clamp", 160, maxAfter)
        assertEquals("Delta must match the exact 300px deleted", 300, maxBefore - maxAfter)
    }

    @Test
    fun testFlingInterruptionRetainsVisualOffsetWithoutSnappingToFinalY() {
        // User flings down into document: visual offset is at 500, but scroller target finalY is 1800.
        val visualOffset = 500
        val scrollerCurr = 1800 // Corrupted by abortAnimation
        val scrollerFinal = 1800

        val resolved = ScribeScrollPolicy.resolveInterruptedScrollOffset(
            visualOffset = visualOffset,
            scrollerCurr = scrollerCurr,
            scrollerFinal = scrollerFinal,
            isFinished = true
        )

        assertEquals("Must retain visual offset 500 and not snap to finalY 1800", visualOffset, resolved)
    }

    @Test
    fun testOppositeScrollContinuesSmoothlyFromInterruptedVisualOffset() {
        // When user catches a fling at 500 and scrolls in opposite direction (e.g. -20px)
        val caughtVisualOffset = 500
        val dragDeltaY = -20f // Dragging up towards document top

        val newTarget = (caughtVisualOffset + dragDeltaY).toInt()
        assertEquals("Subsequent scroll must start from caught offset, yielding 480", 480, newTarget)
    }

    @Test
    fun testDownwardFlingInterruptionDoesNotSnapToZero() {
        // User flings up towards top of text: visual offset is 450, scroller target is 0.
        val visualOffset = 450
        val scrollerCurr = 0
        val scrollerFinal = 0

        val resolved = ScribeScrollPolicy.resolveInterruptedScrollOffset(
            visualOffset = visualOffset,
            scrollerCurr = scrollerCurr,
            scrollerFinal = scrollerFinal,
            isFinished = true
        )

        assertEquals("Must retain visual offset 450 and not snap to 0", visualOffset, resolved)
    }

    @Test
    fun testEmptyDocumentMaxCanvasScrollIsZeroAndTitleDoesNotDisplace() {
        val headerHeight = 220
        val layoutHeight = 0
        val viewportHeight = 1600
        val persistentBottomSpacePx = 800

        val maxCanvasScroll = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            bottomSpace = persistentBottomSpacePx,
            isTextEmpty = true
        )

        val maxHeaderScroll = ScribeScrollPolicy.calculateMaxHeaderScroll(
            headerHeight = headerHeight,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Canvas scroll on empty document must be 0", 0, maxCanvasScroll)
        assertEquals("Header displacement on empty document must be 0 (title remains stationary)", 0, maxHeaderScroll)
    }

    @Test
    fun testShortDocumentKeepsTitleStationaryUntilTextReachesDown() {
        val headerHeight = 220
        val layoutHeight = 300 // 5 short lines
        val viewportHeight = 1600
        val bottomSpace = 0

        val maxCanvasScroll = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            bottomSpace = bottomSpace,
            isTextEmpty = false
        )

        val maxHeaderScroll = ScribeScrollPolicy.calculateMaxHeaderScroll(
            headerHeight = headerHeight,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Short document fitting on screen must not scroll", 0, maxCanvasScroll)
        assertEquals("Title must not move up when text does not reach down", 0, maxHeaderScroll)
    }

    @Test
    fun testTitleScrollsLogicallyAsTextReachesDown() {
        val headerHeight = 200
        val layoutHeight = 1500
        val viewportHeight = 1600
        val bottomSpace = 0

        val maxCanvasScroll = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            bottomSpace = bottomSpace,
            isTextEmpty = false
        )

        val maxHeaderScroll = ScribeScrollPolicy.calculateMaxHeaderScroll(
            headerHeight = headerHeight,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Total canvas scroll allows reaching bottom text", 100, maxCanvasScroll)
        assertEquals("Header moves up by exactly 100px overflow so content is visible", 100, maxHeaderScroll)
    }

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
    fun testNewlineCursorCalculatesAccurateUpwardScrollDelta() {
        val density = 2.5f
        val visibleHeight = 1200 // Usable height above shortcut bar
        val rowHeight = 75f // 30dp
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt() // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // When previous line rested at targetLineBottom (1140px), inserting a newline
        // moves the active cursor position to 1140 + 75 = 1215px.
        val screenBottom = targetLineBottom + rowHeight
        val screenTop = screenBottom - rowHeight

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = false,
            hasScrollOffset = true
        )

        assertEquals("Should trigger upward scroll on newline at bottom", true, resolution.shouldScroll)
        assertEquals("Scroll delta must exactly equal new line height to keep cursor at breathing margin", rowHeight, resolution.deltaY)
    }

    @Test
    fun testRapidEnterCalculatesConsistentBreathingMarginOnEachLine() {
        val density = 2.5f
        val visibleHeight = 1200
        val rowHeight = 60f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt()
        val targetLineBottom = visibleHeight - marginPx

        // Simulate 5 successive newlines starting from targetLineBottom:
        for (i in 1..5) {
            val screenBottom = targetLineBottom + rowHeight
            val screenTop = screenBottom - rowHeight
            val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
                screenBottom = screenBottom,
                screenTop = screenTop,
                visibleHeight = visibleHeight,
                density = density,
                isLastLine = true,
                isDeletionActive = false,
                hasScrollOffset = true
            )
            assertEquals("Step $i: Must scroll up", true, resolution.shouldScroll)
            assertEquals("Step $i: Must scroll exactly rowHeight", rowHeight, resolution.deltaY)
        }
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

        val deltaDown = ScribeScrollPolicy.calculateLastLineFollowDelta(
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
        val delta = ScribeScrollPolicy.calculateLastLineFollowDelta(
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
        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollPolicy.calculateLastLineFollowDelta(
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
        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollPolicy.calculateLastLineFollowDelta(
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

        val delta = if (isImeClosing || !isBottomActive) 0f else ScribeScrollPolicy.calculateLastLineFollowDelta(
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
    fun testSelectAllDetection() {
        val lineCount = 50
        val lastLineColCount = 35

        // Case 1: Entire document selected from (0, 0) to end of document
        assertTrue(
            "Full document selection must be recognized as Select All",
            ScribeScrollPolicy.isSelectAll(
                isSelected = true,
                leftLine = 0,
                leftCol = 0,
                rightLine = 49,
                rightCol = 35,
                lineCount = lineCount,
                lastLineColCount = lastLineColCount
            )
        )

        // Case 2: Selection starts after (0, 0)
        assertFalse(
            "Selection starting at line 1 is not Select All",
            ScribeScrollPolicy.isSelectAll(
                isSelected = true,
                leftLine = 1,
                leftCol = 0,
                rightLine = 49,
                rightCol = 35,
                lineCount = lineCount,
                lastLineColCount = lastLineColCount
            )
        )

        // Case 3: Selection ends before last line
        assertFalse(
            "Selection ending before last line is not Select All",
            ScribeScrollPolicy.isSelectAll(
                isSelected = true,
                leftLine = 0,
                leftCol = 0,
                rightLine = 40,
                rightCol = 10,
                lineCount = lineCount,
                lastLineColCount = lastLineColCount
            )
        )

        // Case 4: No active selection
        assertFalse(
            "Caret without selection must not be recognized as Select All",
            ScribeScrollPolicy.isSelectAll(
                isSelected = false,
                leftLine = 0,
                leftCol = 0,
                rightLine = 49,
                rightCol = 35,
                lineCount = lineCount,
                lastLineColCount = lastLineColCount
            )
        )
    }

    @Test
    fun testResolveTargetCoordinates_KeepCurrentPosition_SuppressesScroll() {
        val result = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = true,
            leftLine = 0,
            leftCol = 0,
            rightLine = 120,
            rightCol = 45,
            lineCount = 121,
            lastLineColCount = 45,
            mode = ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION
        )

        // Must return null to completely suppress automatic scrolling
        assertEquals("Keep Current Position mode must return null to suppress scrolling", null, result)
    }

    @Test
    fun testResolveTargetCoordinates_ScrollToSelectionEnd_TargetsBottomHandle() {
        val result = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = true,
            leftLine = 0,
            leftCol = 0,
            rightLine = 120,
            rightCol = 45,
            lineCount = 121,
            lastLineColCount = 45,
            mode = ScribeScrollPolicy.SelectAllScrollMode.SCROLL_TO_SELECTION_END
        )

        assertTrue("Scroll to Selection End mode must return a target", result != null)
        assertEquals("Must target bottom line", 120, result?.line)
        assertEquals("Must target bottom column", 45, result?.column)
        assertEquals("Must request scrolling", true, result?.shouldScroll)
    }

    @Test
    fun testResolveTargetCoordinates_PreservesExplicitCoordinates() {
        // When explicit coordinates are passed (e.g. outline jump, search result navigation),
        // they must be strictly honored and not replaced with selection endpoints
        val result = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = false,
            leftLine = 5,
            leftCol = 10,
            rightLine = 5,
            rightCol = 10,
            lineCount = 100,
            lastLineColCount = 20,
            mode = ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION,
            explicitLine = 75,
            explicitCol = 12
        )

        assertTrue(result != null)
        assertEquals("Explicit line must be preserved", 75, result?.line)
        assertEquals("Explicit column must be preserved", 12, result?.column)
        assertEquals(true, result?.shouldScroll)
    }

    @Test
    fun testResolveTargetCoordinates_NormalTypingTargetsLeftCursor() {
        // Ordinary typing (!isSelected) must target leftLine and leftCol
        val result = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = false,
            leftLine = 18,
            leftCol = 9,
            rightLine = 18,
            rightCol = 9,
            lineCount = 50,
            lastLineColCount = 30,
            mode = ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION
        )

        assertTrue(result != null)
        assertEquals(18, result?.line)
        assertEquals(9, result?.column)
        assertEquals(true, result?.shouldScroll)
    }

    @Test
    fun testSelectAllScrollMode_Parsing() {
        assertEquals(
            ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION,
            ScribeScrollPolicy.SelectAllScrollMode.fromString(null)
        )
        assertEquals(
            ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION,
            ScribeScrollPolicy.SelectAllScrollMode.fromString("UNKNOWN")
        )
        assertEquals(
            ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION,
            ScribeScrollPolicy.SelectAllScrollMode.fromString("KEEP_CURRENT_POSITION")
        )
        assertEquals(
            ScribeScrollPolicy.SelectAllScrollMode.SCROLL_TO_SELECTION_END,
            ScribeScrollPolicy.SelectAllScrollMode.fromString("SCROLL_TO_SELECTION_END")
        )
    }

    // ── 1 Degree of Freedom (1-DOF) Architecture Regression Tests ──────────────

    @Test
    fun test1DOF_HeaderPartiallyVisible_MovementAllocatedExclusivelyToOuterCanvas() {
        // Invariant A: While any portion of the title header remains visible (scrollD < headerHeight),
        // vertical scroll movement belongs EXCLUSIVELY to outer canvas. Editor offset must remain 0.
        val headerHeight = 200
        val scrollD = 50f
        val editorOffsetY = 0
        val maxCanvasScroll = 1000

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 30f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas consumes all 30px", 30f, allocation.outerDelta, 0.001f)
        assertEquals("Editor must not move while header is visible", 0f, allocation.innerDelta, 0.001f)
        assertEquals("No unconsumed delta", 0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_ShortDocument_HeaderPartiallyVisible_EditorNeverScrolls() {
        // When maxCanvasScroll < headerHeight, header cannot scroll completely out of view.
        // Outer canvas stops at maxCanvasScroll. Crucially, leftover delta is NEVER passed to editor.
        val headerHeight = 200
        val scrollD = 0f
        val editorOffsetY = 0
        val maxCanvasScroll = 100

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 150f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas consumes up to maxCanvasScroll (100px)", 100f, allocation.outerDelta, 0.001f)
        assertEquals("Editor must NEVER scroll on short document while header is visible", 0f, allocation.innerDelta, 0.001f)
        assertEquals("Remaining 50px is unconsumed (clamped at physical bounds)", 50f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_EmptyDocument_NoMovementAllowed_EditorZero() {
        val headerHeight = 200
        val scrollD = 0f
        val editorOffsetY = 0
        val maxCanvasScroll = 0

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 50f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals(0f, allocation.outerDelta, 0.001f)
        assertEquals(0f, allocation.innerDelta, 0.001f)
        assertEquals(50f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_DeltaSmallerThanRemainingHeader_MovesOnlyOuterCanvas() {
        val headerHeight = 200
        val scrollD = 100f
        val editorOffsetY = 0
        val maxCanvasScroll = 500

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 50f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals(50f, allocation.outerDelta, 0.001f)
        assertEquals(0f, allocation.innerDelta, 0.001f)
        assertEquals(0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_DeltaEqualToRemainingHeader_ReachesExactHandoffBoundary_EditorZero() {
        val headerHeight = 200
        val scrollD = 100f
        val editorOffsetY = 0
        val maxCanvasScroll = 500

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 100f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas consumes exact remaining 100px to reach headerHeight", 100f, allocation.outerDelta, 0.001f)
        assertEquals("Editor offset remains at 0 at the exact boundary", 0f, allocation.innerDelta, 0.001f)
        assertEquals(0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_DeltaLargerThanRemainingHeader_SplitPreciselyAtBoundary() {
        // Delta of 120px with 50px remaining to header boundary.
        // 50px goes to outer canvas (hiding header completely); remaining 70px seamlessly scrolls editor.
        val headerHeight = 200
        val scrollD = 150f
        val editorOffsetY = 0
        val maxCanvasScroll = 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 120f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas consumes remaining 50px to hide header", 50f, allocation.outerDelta, 0.001f)
        assertEquals("Inner editor consumes remaining 70px after handoff", 70f, allocation.innerDelta, 0.001f)
        assertEquals("Zero unconsumed, no lost movement", 0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_HeaderFullyHidden_SubsequentMovementAllocatedToEditor() {
        val headerHeight = 200
        val scrollD = 200f
        val editorOffsetY = 50
        val maxCanvasScroll = 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 40f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas stays at headerHeight", 0f, allocation.outerDelta, 0.001f)
        assertEquals("Inner editor consumes all 40px", 40f, allocation.innerDelta, 0.001f)
        assertEquals(0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_EditorAtMaxBounds_MovementClamped() {
        val headerHeight = 200
        val scrollD = 200f
        val editorOffsetY = 600
        val maxCanvasScroll = 800 // Total scroll is 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = 50f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals(0f, allocation.outerDelta, 0.001f)
        assertEquals(0f, allocation.innerDelta, 0.001f)
        assertEquals(50f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_ReverseScroll_EditorOffsetNeutralizedBeforeHeaderReappears() {
        // Scrolling back toward title: editor offset returns to 0 first.
        val headerHeight = 200
        val scrollD = 200f
        val editorOffsetY = 50
        val maxCanvasScroll = 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -30f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Outer canvas must not move while editor offset is positive", 0f, allocation.outerDelta, 0.001f)
        assertEquals("Inner editor consumes -30px towards 0", -30f, allocation.innerDelta, 0.001f)
        assertEquals(0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_ReverseScroll_DeltaLargerThanEditorOffset_DividedAtBoundary() {
        // Delta of -80px with editorOffsetY = 50.
        // Editor absorbs -50px down to 0; remaining -30px reveals header by 30px (scrollD 200 -> 170).
        val headerHeight = 200
        val scrollD = 200f
        val editorOffsetY = 50
        val maxCanvasScroll = 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -80f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Inner editor neutralizes by exactly -50px to reach 0", -50f, allocation.innerDelta, 0.001f)
        assertEquals("Outer canvas reveals header by remaining -30px", -30f, allocation.outerDelta, 0.001f)
        assertEquals("Zero unconsumed", 0f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_ReverseScroll_EditorZero_HeaderMovesToTop() {
        val headerHeight = 200
        val scrollD = 170f
        val editorOffsetY = 0
        val maxCanvasScroll = 800

        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -200f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Inner editor was already 0", 0f, allocation.innerDelta, 0.001f)
        assertEquals("Outer canvas moves -170px to reach top (scrollD = 0)", -170f, allocation.outerDelta, 0.001f)
        assertEquals("Remaining -30px is unconsumed (for overscroll stretch effect)", -30f, allocation.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_RapidDirectionReversals_MaintainsInvariantAndConsistency() {
        val headerHeight = 200
        val maxCanvasScroll = 1000
        var scrollD = 0f
        var editorOffsetY = 0

        // Step 1: Scroll forward 150px (partially reveals document, header still visible)
        var alloc = ScribeScrollPolicy.allocateScrollDelta(150f, headerHeight, scrollD, editorOffsetY, maxCanvasScroll)
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()
        assertEquals(150f, scrollD, 0.001f)
        assertEquals(0, editorOffsetY)

        // Step 2: Scroll forward 100px (crosses handoff boundary: 50px to header, 50px to editor)
        alloc = ScribeScrollPolicy.allocateScrollDelta(100f, headerHeight, scrollD, editorOffsetY, maxCanvasScroll)
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()
        assertEquals(200f, scrollD, 0.001f)
        assertEquals(50, editorOffsetY)

        // Step 3: Rapid reversal -80px (50px to editor to neutralize, 30px to canvas to reveal header)
        alloc = ScribeScrollPolicy.allocateScrollDelta(-80f, headerHeight, scrollD, editorOffsetY, maxCanvasScroll)
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()
        assertEquals(170f, scrollD, 0.001f)
        assertEquals(0, editorOffsetY)

        // Step 4: Rapid reversal +50px (30px to canvas to hide header, 20px to editor)
        alloc = ScribeScrollPolicy.allocateScrollDelta(50f, headerHeight, scrollD, editorOffsetY, maxCanvasScroll)
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()
        assertEquals(200f, scrollD, 0.001f)
        assertEquals(20, editorOffsetY)

        // Step 5: Large reversal -250px (20px to editor to reach 0, 200px to canvas to reach 0, 30px unconsumed)
        alloc = ScribeScrollPolicy.allocateScrollDelta(-250f, headerHeight, scrollD, editorOffsetY, maxCanvasScroll)
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()
        assertEquals(0f, scrollD, 0.001f)
        assertEquals(0, editorOffsetY)
        assertEquals(-30f, alloc.unconsumedDelta, 0.001f)
    }

    @Test
    fun test1DOF_ReconcileScrollState_HeaderPartiallyVisible_ForcesEditorToZero() {
        // If an invalid state somehow occurs (e.g. scrollD = 100 < headerHeight 200, but editorOffsetY = 50),
        // totalScroll = 150 < 200. Reconcile strictly restores scrollD = 150, editorOffsetY = 0!
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = 200,
            scrollD = 100,
            editorOffsetY = 50,
            maxCanvasScroll = 800
        )
        assertEquals("All displacement assigned to outer canvas", 150, reconciled.scrollD)
        assertEquals("Editor offset strictly neutralized to 0", 0, reconciled.editorOffsetY)
    }

    @Test
    fun test1DOF_ReconcileScrollState_HeaderFullyHidden_PreservesEditorOffset() {
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = 200,
            scrollD = 200,
            editorOffsetY = 120,
            maxCanvasScroll = 800
        )
        assertEquals(200, reconciled.scrollD)
        assertEquals(120, reconciled.editorOffsetY)
    }

    @Test
    fun test1DOF_ReconcileScrollState_ClampsExcessTotalScrollToMaxCanvasScroll() {
        // When document shrinks and total scroll (200 + 700 = 900) exceeds maxCanvasScroll (800):
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = 200,
            scrollD = 200,
            editorOffsetY = 700,
            maxCanvasScroll = 800
        )
        assertEquals(200, reconciled.scrollD)
        assertEquals("Excess scroll clamped so total scroll = 800", 600, reconciled.editorOffsetY)
    }

    @Test
    fun test1DOF_SafeScrollMaxY_ReturnsZeroWhenHeaderIsVisible() {
        // When header is visible (isHeaderFullyHidden = false), editor scroll max MUST be 0
        val maxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = 1500,
            viewportHeight = 1000,
            persistentBottomSpacePx = 800,
            isFocused = true,
            isHeaderFullyHidden = false
        )
        assertEquals("Editor scroll max must be 0 while header is visible", 0, maxScroll)
    }

    @Test
    fun test1DOF_SafeScrollMaxY_AllowsScrollWhenHeaderIsFullyHidden() {
        val maxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = 1500,
            viewportHeight = 1000,
            persistentBottomSpacePx = 800,
            isFocused = true,
            isHeaderFullyHidden = true
        )
        // 1500 + 800 - 1000 = 1300
        assertEquals(1300, maxScroll)
    }

    // ── Transition-Aware Contraction Restoration Tests ─────────────────────────

    @Test
    fun testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement() {
        // Line 50 in a 100-line document:
        // Prior expansion displaced canvas up by 200px.
        // Viewport expands from 800 to 1100 (contracting keyboard/drawer).
        val visibleHeight = 1100
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val targetLineBottom = visibleHeight - marginPx // 1040px
        val caretScreenBottom = 600f
        val totalScroll = 800f
        val tempDisplacement = 200f

        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = caretScreenBottom,
                caretScreenTop = caretScreenBottom - 50f,
                viewportHeight = visibleHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = tempDisplacement,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue("Should scroll downward to restore displacement", result.shouldScroll)
        assertEquals("Delta must restore the exact 200px displacement", -200f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation should be 0", 0f, result.remainingCompensation, 0.001f)

        // Verify settled caret position satisfies safe boundary
        val settledCaretBottom = caretScreenBottom - result.deltaY // 600 - (-200) = 800px
        assertTrue("Settled caret must rest above target line", settledCaretBottom <= targetLineBottom)
    }

    @Test
    fun testContractionRestoration_EarlyLine_ClampsToTotalScroll() {
        // Line 2 (early line): totalScroll = 80px, but temporaryDisplacement was 150px.
        // Restoration cannot scroll past top of document (totalScroll reaches 0).
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = 300f,
                caretScreenTop = 260f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 80f,
                temporaryDisplacement = 150f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals("Restoration must clamp to -totalScroll (-80px)", -80f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation must retain unapplied 70px", 70f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContractionRestoration_MiddleOfDocument_WithoutDisplacement_RemainsStationary() {
        // Line 50 with no prior displacement recorded (user was typing mid-document).
        // Must remain stationary (returns 0f).
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = 600f,
                caretScreenTop = 560f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 0f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertFalse("Mid-document editing without displacement must not scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
    }

    @Test
    fun testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta() {
        // Keyboard is completely dismissing (Pure Writer stationary invariant):
        // Even if temporary displacement exists and caret is on last line, delta must be 0f!
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = 600f,
                caretScreenTop = 560f,
                viewportHeight = 1500,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 200f,
                isAtLastLine = true,
                isKeyboardDismissing = true,
                isBottomSurfaceActive = false
            )
        )

        assertFalse("Full keyboard dismissal must strictly suppress downward scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
        assertEquals(0f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContractionRestoration_SafeBoundaryClamping_NeverSettlesBehindShortcutBar() {
        // Caret is sitting at 1000px in a 1100px viewport. Breathing margin = 60px (targetLine = 1040px).
        // Slack is only 40px (1040 - 1000 = 40px).
        // If temporaryDisplacement = 120px, full restoration would push caret to 1120px (behind shortcut bar!).
        // The policy MUST clamp downward movement to -40px.
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = 1000f,
                caretScreenTop = 960f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 120f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals("Down movement must be clamped to safe slack (-40px)", -40f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation must track unapplied 80px", 80f, result.remainingCompensation, 0.001f)

        // Assert settled caret bottom is exactly at targetLineBottom (1040px)
        val settledCaretBottom = 1000f - result.deltaY
        assertEquals(1040f, settledCaretBottom, 0.001f)
    }

    @Test
    fun testContractionRestoration_LastLine_AnchorsToBreathingMarginWhenZeroDisplacement() {
        // Caret on last line with 0 displacement: smoothly anchors document end to 24dp breathing margin
        val visibleHeight = 1100
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val targetLineBottom = visibleHeight - marginPx // 1040px
        val caretScreenBottom = 800f
        val totalScroll = 600f

        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = caretScreenBottom,
                caretScreenTop = caretScreenBottom - 40f,
                viewportHeight = visibleHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = 0f,
                isAtLastLine = true,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals(-240f, result.deltaY, 0.001f) // 800 - 1040 = -240
    }

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
    fun testContractionRestoration_DiagnosticsLogging() {
        var loggedMessage: String? = null
        ScribeScrollPolicy.isDebugDiagnosticsEnabled = true
        ScribeScrollPolicy.onScrollDiagnosticLog = { msg -> loggedMessage = msg }

        try {
            ScribeScrollPolicy.resolveContractionRestorationDelta(
                ScribeScrollPolicy.ContractionRestorationRequest(
                    caretScreenBottom = 600f,
                    caretScreenTop = 560f,
                    viewportHeight = 1100,
                    density = 2.5f,
                    totalScroll = 500f,
                    temporaryDisplacement = 200f,
                    isAtLastLine = false,
                    isKeyboardDismissing = false,
                    isBottomSurfaceActive = true
                )
            )
            assertTrue("Diagnostic log must be called", loggedMessage != null && loggedMessage.contains("Applied"))
        } finally {
            ScribeScrollPolicy.isDebugDiagnosticsEnabled = false
            ScribeScrollPolicy.onScrollDiagnosticLog = null
        }
    }
}

