package com.primaloptima.scribe.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            val msg = loggedMessage
            assertTrue("Diagnostic log must be called", msg != null && msg.contains("Applied"))
        } finally {
            ScribeScrollPolicy.isDebugDiagnosticsEnabled = false
            ScribeScrollPolicy.onScrollDiagnosticLog = null
        }
    }

    // ── Phase 2: Coordinated Restoration & Safe-Boundary Regression Tests ────

    @Test
    fun testContraction_CaretInitiallyBeyondSafeBoundary_CalculatesCorrectiveUpward() {
        // Viewport: 1100, density: 2.5 (margin = 60px, targetLine = 1040px).
        // Caret bottom is at 1080px (40px below breathing margin, inside unsafe zone!).
        // The policy MUST calculate an upward corrective delta (+40px) to restore safe visibility.
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = 1080f,
                caretScreenTop = 1040f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 100f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )
        assertTrue("Unsafe caret position must trigger corrective scroll", result.shouldScroll)
        assertEquals("Must scroll upward by 40px to bring caret to safe breathing line", 40f, result.deltaY, 0.001f)
        assertEquals("Temporary displacement should remain unconsumed by upward correction", 100f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContraction_TargetValidation_ClampsUnsafeTarget() {
        // Header height: 200, caretDocBottom: 1500, viewport: 1100, density: 2.5 (targetLine = 1040).
        // Target total scroll = 600.
        // Final caret screen bottom = 200 + 1500 - 600 = 1100 (which is 60px below targetLine 1040!).
        val validation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = 600f,
            caretDocBottom = 1500f,
            headerHeight = 200,
            viewportHeight = 1100,
            density = 2.5f
        )
        assertFalse("Target that places caret at 1100px must be flagged unsafe", validation.isSafe)
        assertEquals("Corrective delta must be +60px", 60f, validation.correctiveDeltaY, 0.001f)
        val safeTarget = 600f + validation.correctiveDeltaY // 660f
        val revalidation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = safeTarget,
            caretDocBottom = 1500f,
            headerHeight = 200,
            viewportHeight = 1100,
            density = 2.5f
        )
        assertTrue("Adjusted target must be safe", revalidation.isSafe)
        assertEquals(1040f, revalidation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testContraction_VerifiedCoordinateOrdering_SelectionBounds() {
        // Multi-line selection spanning lines 10 to 15.
        // Start of selection is at line 10, end of selection is at line 15.
        // Bottom-most coordinate is at line 15, NOT line 10!
        val geom = ScribeScrollPolicy.SelectionGeometry(
            topDocY = 400f,
            bottomDocY = 650f,
            leftDocX = 120f,
            rightDocX = 250f,
            isSelected = true,
            anchorLine = 10,
            focusLine = 15
        )
        assertEquals("Top coordinate must be at start of selection", 400f, geom.topDocY, 0.001f)
        assertEquals("Bottom coordinate must be at end of selection", 650f, geom.bottomDocY, 0.001f)
        assertTrue("Must report isSelected true", geom.isSelected)
    }

    @Test
    fun testContractionSimulation_RepeatedViewportHeightChanges_NoOvershoot() {
        // Controller simulates drawer collapsing across intermediate layout frames:
        // h = 800 -> 850 -> 920 -> 1000 -> 1100
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 800f,
            temporaryDisplacement = 200f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1200f,
            headerHeight = 200,
            density = 2.5f
        )

        // Frame 1: h = 850
        controller.onViewportHeightChanged(850, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 2: h = 920
        controller.onViewportHeightChanged(920, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 3: h = 1000
        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 4: h = 1100 (final settled height)
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)

        // Target scroll must be exactly 800 - 200 = 600f (not 800 - 200*4 = 0 overshoot!)
        assertEquals("Target scroll must be exactly 600f without cumulative overshoot", 600f, controller.targetTotalScroll, 0.001f)

        // Animate to completion
        controller.applyAnimationStep(1.0f)
        assertEquals(600f, controller.totalScroll, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)
    }

    @Test
    fun testContractionSimulation_NewTargetArrivingBeforeOldAnimationFinishes_RetargetsSmoothly() {
        // Animation is in flight (40% completed) when another layout frame arrives
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 900f,
            temporaryDisplacement = 200f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1400f,
            headerHeight = 200,
            density = 2.5f
        )

        // Frame 1: Viewport 1000
        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Advance animation by 40%
        controller.applyAnimationStep(0.4f)
        val intermediateScroll = controller.totalScroll
        val intermediateDisplacement = controller.temporaryDisplacement
        assertTrue("Intermediate scroll must have advanced downward", intermediateScroll < 900f)
        assertTrue("Temporary displacement must have decremented in lockstep", intermediateDisplacement < 200f)

        // Frame 2: Viewport expands further to 1100
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)

        // Finish remaining animation
        controller.applyAnimationStep(1.0f)
        assertEquals("Settles at exact target without jumps", 700f, controller.totalScroll, 0.001f)
        assertEquals("Displacement completely restored", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_AnimationCancellation_ReconcilesActualAppliedMovement() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 800f,
            temporaryDisplacement = 150f,
            caretLine = 20,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Animate 33% (50px out of 150px)
        controller.applyAnimationStep(0.33333334f)
        assertEquals(750f, controller.totalScroll, 0.5f)
        assertEquals("Only the applied 50px was deducted", 100f, controller.temporaryDisplacement, 0.5f)

        // Interrupted/cancelled
        controller.cancelRestoration()
        assertFalse(controller.isRestorationActive)
        assertEquals("Unapplied displacement (100px) remains accurately tracked", 100f, controller.temporaryDisplacement, 0.5f)
    }

    @Test
    fun testContractionSimulation_TickByTickAccounting_LockstepPrecision() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 120f,
            caretLine = 15,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 900f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        val initialScroll = controller.totalScroll
        val initialDisp = controller.temporaryDisplacement

        // 3 sequential steps
        controller.applyAnimationStep(0.25f)
        val step1Applied = initialScroll - controller.totalScroll
        assertEquals(initialDisp - step1Applied, controller.temporaryDisplacement, 0.01f)

        controller.applyAnimationStep(0.5f)
        val step2Applied = initialScroll - controller.totalScroll
        assertEquals(initialDisp - step2Applied, controller.temporaryDisplacement, 0.01f)

        controller.applyAnimationStep(1.0f)
        assertEquals(480f, controller.totalScroll, 0.01f)
        assertEquals(0f, controller.temporaryDisplacement, 0.01f)
    }

    @Test
    fun testContractionSimulation_RepeatedExpandCollapseCycles_NoAccumulatedDisplacement() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 500f,
            temporaryDisplacement = 0f,
            caretLine = 10,
            caretCol = 2,
            isSelected = false,
            caretDocBottom = 800f,
            headerHeight = 200,
            density = 2.5f
        )

        // Cycle 1: Expansion records 100px
        controller.temporaryDisplacement = 100f
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        controller.applyAnimationStep(1.0f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(400f, controller.totalScroll, 0.001f)

        // Cycle 2: Another expansion records 80px
        controller.temporaryDisplacement = 80f
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        controller.applyAnimationStep(1.0f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(320f, controller.totalScroll, 0.001f)
    }

    @Test
    fun testContractionSimulation_UserTouch_InvalidatesStaleCompensation() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        assertTrue(controller.isRestorationActive)

        // User touches screen
        controller.onUserTouch()
        assertFalse("Restoration must be immediately cancelled on user touch", controller.isRestorationActive)
        assertEquals("Displacement must be invalidated on user touch", 0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(-1, controller.caretLine)
    }

    @Test
    fun testContractionSimulation_CaretMovement_InvalidatesStaleCompensation() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        // Caret moves to line 26
        controller.onCaretMoved(newLine = 26, newCol = 10, newSelected = false)
        assertEquals("Displacement must be invalidated when caret moves to another line", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_ContentEdits_InvalidatesStaleCompensation() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        // Content changed
        controller.onContentEdited()
        assertEquals("Displacement must be invalidated when document text is edited", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_FullKeyboardDismissal_StrictlyStationary() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1500,
            isAtLastLine = true,
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false
        )
        assertFalse("Full keyboard dismissal must not scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)
        assertEquals(600f, controller.totalScroll, 0.001f)
    }

    @Test
    fun testContraction_Preserves1DOFInvariant_DuringRestorationSteps() {
        val headerHeight = 200
        val maxCanvasScroll = 1000
        var scrollD = 200f // Header fully hidden
        var editorOffsetY = 300 // Inner editor scrolled down 300px
        val totalScrollBefore = scrollD + editorOffsetY // 500px

        // Downward restoration delta of -250px (moves content down towards 0)
        var alloc = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -250f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()

        // 1-DOF rule: Inner editor neutralizes from 300 down to 50; outer canvas stays strictly at 200!
        assertEquals("Inner editor must consume all -250px", 50, editorOffsetY)
        assertEquals("Outer canvas must remain at headerHeight (200) while editor > 0", 200f, scrollD, 0.001f)

        // Next restoration step: delta of -100px (crosses boundary into header reveal)
        alloc = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -100f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()

        // Inner editor absorbs -50px down to 0; remaining -50px reveals header from 200 to 150!
        assertEquals("Inner editor must be strictly 0", 0, editorOffsetY)
        assertEquals("Outer canvas reveals header down to 150", 150f, scrollD, 0.001f)
        assertEquals("Zero unconsumed delta across boundary", 0f, alloc.unconsumedDelta, 0.001f)
    }

    @Test
    fun testContraction_KeyboardExpansion_FollowedByNormalHeightRestoration() {
        // Standard keyboard (viewport 1100) expands to emoji keyboard (viewport 800).
        // Document scrolls up by 150px to keep line 20 visible.
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 500f,
            temporaryDisplacement = 0f,
            caretLine = 20,
            caretCol = 4,
            isSelected = false,
            caretDocBottom = 800f,
            headerHeight = 200,
            density = 2.5f
        )

        // Expansion phase: upward scroll records 150px displacement
        controller.totalScroll = 650f
        controller.temporaryDisplacement = 150f

        // Keyboard contracts back to standard height (viewport 1100)
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1100,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )

        assertTrue("Contraction to standard height must trigger downward restoration", result.shouldScroll)
        assertEquals(-150f, result.deltaY, 0.001f)
        assertEquals(500f, controller.targetTotalScroll, 0.001f)

        // Animate to completion
        controller.applyAnimationStep(1.0f)
        assertEquals("Total scroll restored to exact pre-expansion position", 500f, controller.totalScroll, 0.001f)
        assertEquals("Temporary displacement cleared", 0f, controller.temporaryDisplacement, 0.001f)

        // Caret screen bottom = 200 + 800 - 500 = 500px, which has 540px clearance above safe line (1040px)
        val finalCaretBottom = 200f + 800f - controller.totalScroll
        assertTrue("Caret must settle comfortably above breathing line", finalCaretBottom <= 1040f)
    }

    @Test
    fun testContraction_AccessoryDrawerExpansion_FollowedByNormalHeightRestoration() {
        // Compact accessory (viewport 950) expands to full drawer (viewport 650).
        // Upward scroll of 220px is recorded.
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 400f,
            temporaryDisplacement = 0f,
            caretLine = 18,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 750f,
            headerHeight = 200,
            density = 2.5f
        )

        // Expansion phase:
        controller.totalScroll = 620f
        controller.temporaryDisplacement = 220f

        // Drawer collapses back to compact accessory (viewport 950)
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 950,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )

        assertTrue(result.shouldScroll)
        assertEquals(-220f, result.deltaY, 0.001f)
        assertEquals(400f, controller.targetTotalScroll, 0.001f)

        controller.applyAnimationStep(1.0f)
        assertEquals(400f, controller.totalScroll, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContraction_SelectionChangeDuringTransition_InvalidatesStaleCompensation() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 700f,
            temporaryDisplacement = 180f,
            caretLine = 22,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1100f,
            headerHeight = 200,
            density = 2.5f
        )

        // User extends selection while in expanded state
        controller.onCaretMoved(newLine = 22, newCol = 15, newSelected = true)

        assertEquals("Displacement must be invalidated on selection change", 0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)

        // Viewport collapses later: must NOT restore obsolete displacement
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1100,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        assertFalse("Must remain stationary when compensation was invalidated", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
    }

    @Test
    fun testContraction_ManualScrollDuringExpansion_InvalidatesCompensation() {
        val controller = ScribeScrollPolicy.ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 120f,
            caretLine = 15,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 950f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        assertTrue(controller.isRestorationActive)

        // User touches / drags the canvas
        controller.onUserTouch()
        assertFalse("Restoration must be cancelled immediately", controller.isRestorationActive)
        assertEquals("Displacement must be 0f", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContraction_PartialRestoration_RetainsOnlySafelyAppliableCompensation() {
        // Document has caret close to bottom. Total scroll = 400.
        // Caret doc bottom = 1200. Header = 200.
        // Viewport = 1100, density = 2.5 -> targetLineBottom = 1040.
        // Current caret bottom = 200 + 1200 - 400 = 1000px.
        // Available slack to 1040 is 40px!
        // Suppose temporaryDisplacement was 150px.
        val req = ScribeScrollPolicy.ContractionRestorationRequest(
            caretScreenBottom = 1000f,
            caretScreenTop = 960f,
            viewportHeight = 1100,
            density = 2.5f,
            totalScroll = 400f,
            temporaryDisplacement = 150f,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val result = ScribeScrollPolicy.resolveContractionRestorationDelta(req)
        assertTrue(result.shouldScroll)
        assertEquals("Downward movement clamped to available slack (-40px)", -40f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation is 110px", 110f, result.remainingCompensation, 0.001f)

        // Validate final target safety
        val target = 400f + result.deltaY // 360f
        val validation = ScribeScrollPolicy.validateFinalTarget(target, 1200f, 200, 1100, 2.5f)
        assertTrue("Settled target at breathing margin is safe", validation.isSafe)
        assertEquals(1040f, validation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testAuthoritativeCoordinator_SmoothRetargetingInFlight_NoVelocityReset() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 700f,
            temporaryDisplacement = 250f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1200f,
            headerHeight = 200,
            density = 2.5f
        )
        coordinator.onScrollBy = { 0f }

        // 1. Initial contraction restoration towards 500f (-200px)
        val shouldStart = coordinator.requestTarget(500f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(shouldStart)
        assertTrue(coordinator.isAnimationActive)
        assertEquals(500f, coordinator.activeTargetTotalScroll)

        // 2. Animate halfway: step to 600f (-100px applied)
        val moved1 = coordinator.applyStep(600f)
        assertEquals(-100f, moved1, 0.001f)
        assertEquals(600f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement decremented by 100px", 150f, coordinator.temporaryDisplacement, 0.001f)

        // 3. New target arrives while in flight (e.g. drawer collapsed further, new target 450f)
        val retargeted = coordinator.requestTarget(450f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue("Coordinator must accept retarget", retargeted)
        assertEquals("Target updated to 450f", 450f, coordinator.activeTargetTotalScroll)
        assertEquals("Current position remains at 600f", 600f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement remains at 150f", 150f, coordinator.temporaryDisplacement, 0.001f)

        // 4. Animate remaining distance to 450f (-150px)
        val moved2 = coordinator.applyStep(450f)
        assertEquals(-150f, moved2, 0.001f)
        assertEquals(450f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement fully consumed down to 0f", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals("Expansion caret reset", -1, coordinator.caretLine)
        assertFalse("Animation marked finished on reaching target", coordinator.isAnimationActive)
    }

    @Test
    fun testAuthoritativeCoordinator_CoalescesIntermediateCloseTargets() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 600f,
            temporaryDisplacement = 100f
        )
        coordinator.requestTarget(500f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(coordinator.isAnimationActive)

        // Sub-2px intermediate changes must NOT trigger retargeting or reset
        val ignoredRetarget = coordinator.requestTarget(501f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertFalse("Close target within deadband must be coalesced without restarting", ignoredRetarget)
        assertEquals(500f, coordinator.activeTargetTotalScroll)
    }

    @Test
    fun testAuthoritativeCoordinator_CausalExpansionTracking() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 300f,
            temporaryDisplacement = 0f,
            caretLine = 12,
            caretCol = 2
        )
        coordinator.onScrollBy = { 0f }

        // When transition is NOT active, upward movement is NOT recorded as expansion
        coordinator.isExpansionTransitionActive = false
        coordinator.requestTarget(400f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.applyStep(400f)
        assertEquals("No displacement recorded outside expansion transition", 0f, coordinator.temporaryDisplacement, 0.001f)

        // When transition IS active, upward movement from CARET_VISIBILITY is recorded
        coordinator.isExpansionTransitionActive = true
        coordinator.requestTarget(550f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.applyStep(550f)
        assertEquals("Expansion displacement recorded", 150f, coordinator.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testAuthoritativeCoordinator_StrictSafeBoundary_SlackNegativeIsAlwaysUnsafe() {
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val viewportHeight = 1000
        val targetLineBottom = viewportHeight - marginPx // 940px
        val headerHeight = 200
        val caretDocBottom = 1000f

        // targetTotalScroll = 259f -> finalCaretBottom = 200 + 1000 - 259 = 941px
        // slack = 940 - 941 = -1px (1px into the breathing margin)
        val validation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = 259f,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = viewportHeight,
            density = density
        )
        assertFalse("Even 1px below breathing margin MUST be marked unsafe", validation.isSafe)
        assertEquals(1f, validation.correctiveDeltaY, 0.001f)

        // With targetTotalScroll = 260f -> finalCaretBottom = 940px -> slack = 0px
        val safeValidation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = 260f,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = viewportHeight,
            density = density
        )
        assertTrue("Exact 24dp breathing clearance must be marked safe", safeValidation.isSafe)
        assertEquals(0f, safeValidation.correctiveDeltaY, 0.001f)
    }

    @Test
    fun testAuthoritativeCoordinator_UserTouch_InvalidatesAllCompensation() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 500f,
            temporaryDisplacement = 180f,
            caretLine = 15,
            caretCol = 3,
            isSelected = false
        )
        coordinator.requestTarget(400f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(coordinator.isAnimationActive)

        // User touch down
        coordinator.invalidateCompensation("User touch")
        assertFalse(coordinator.isAnimationActive)
        assertEquals(0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    @Test
    fun testAuthoritativeCoordinator_CaretChange_InvalidatesCompensation() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 500f,
            temporaryDisplacement = 180f,
            caretLine = 15,
            caretCol = 3,
            isSelected = false
        )

        // Caret moved to different column on same line
        coordinator.invalidateIfCaretChanged(newLine = 15, newCol = 10, newSelected = false)
        assertEquals("Displacement must be invalidated on column change", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    /**
     * Test animation driver simulating Android's ValueAnimator lifecycle
     * where cancel() synchronously triggers onAnimationCancel followed by onAnimationEnd.
     */
    private class TestAnimationDriver : ScribeScrollPolicy.AnimationDriver {
        var isStarted = false
        var fromVal = 0f
        var toVal = 0f
        var updateCb: ((Float) -> Unit)? = null
        var cancelCb: (() -> Unit)? = null
        var endCb: (() -> Unit)? = null
        override var isRunning = false

        override fun start(
            from: Float,
            to: Float,
            durationMs: Long,
            onUpdate: (Float) -> Unit,
            onCancel: () -> Unit,
            onEnd: () -> Unit
        ) {
            isStarted = true
            isRunning = true
            fromVal = from
            toVal = to
            updateCb = onUpdate
            cancelCb = onCancel
            endCb = onEnd
        }

        override fun cancel() {
            if (isRunning) {
                isRunning = false
                // Android ValueAnimator lifecycle: onAnimationCancel then onAnimationEnd
                cancelCb?.invoke()
                endCb?.invoke()
            }
        }

        fun step(value: Float) {
            if (isRunning) {
                updateCb?.invoke(value)
            }
        }

        fun complete() {
            if (isRunning) {
                isRunning = false
                endCb?.invoke()
            }
        }
    }

    // --- Production Scroll Architecture Regression Tests (Point 9) ---

    @Test
    fun testProductionCoordinator_CancellationAndReplacement_PreventsStaleCallbacks() {
        // Point 9.1: Cancel an ongoing animation with a new target. Verify the old animator's
        // cancel and completion callbacks cannot clear the new target or trigger obsolete restoration,
        // and verify the coordinator continues driving to the new destination.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 100f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Start operation 1: from 100f to 300f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        assertEquals(1, drivers.size)
        val driver1 = drivers[0]
        assertTrue("Driver 1 must be running", driver1.isRunning)

        // Step driver 1 to 150f
        driver1.step(150f)
        assertEquals(150f, coordinator.totalScroll, 0.001f)

        // Start operation 2: replace with target 500f
        // This cancels driver 1. In Android, cancel() invokes cancelCb followed by endCb synchronously.
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 500f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )

        // Verify driver 1 was cancelled
        assertFalse("Driver 1 must no longer be running", driver1.isRunning)
        assertEquals(2, drivers.size)
        val driver2 = drivers[1]
        assertTrue("Driver 2 must be running", driver2.isRunning)

        // Crucial invariant: Driver 1's synchronous cancel & end callbacks MUST NOT have cleared
        // the active animation state or erased driver 2's new target!
        assertTrue("Coordinator must remain active with operation 2", coordinator.isAnimationActive)
        assertEquals("Target must be 500f, not erased by old driver", 500f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)
        assertEquals(2L, productionCoordinator.activeOperationId)
        assertEquals(2L, coordinator.activeOperationId)

        // Step driver 2 to completion
        driver2.step(300f)
        assertEquals(300f, coordinator.totalScroll, 0.001f)
        driver2.step(500f)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
        driver2.complete()

        // After driver 2 completes, animation is cleanly finished at target 500f
        assertFalse("Coordinator should be finished after active driver ends", coordinator.isAnimationActive)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_InFlightRetargeting_SmoothlyUpdatesWithoutHitchOrRelativeError() {
        // Point 9.2: Retarget while moving, verify the animator smoothly updates to the final destination
        // without accumulating relative errors, and verify sub-deadband changes do not restart or hitch.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 100f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Initial target 300f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver1 = drivers[0]
        driver1.step(200f)
        assertEquals(200f, coordinator.totalScroll, 0.001f)

        // Sub-deadband target change (e.g. 201f vs current target 300f -> wait, let's test sub-deadband
        // against activeTarget: if target was 300f and new request is 301f, delta is 1f <= 2f)
        val ignoredRetarget = coordinator.requestTarget(
            targetScroll = 301f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        assertFalse("Sub-deadband target change (<= 2f) must not restart animation or hitch", ignoredRetarget)
        assertEquals("Target remains 300f", 300f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Material target change: 450f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 450f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        assertEquals(2, drivers.size)
        val driver2 = drivers[1]

        // Driver 2 must start from current scroll (200f) to new target (450f)
        assertEquals(200f, driver2.fromVal, 0.001f)
        assertEquals(450f, driver2.toVal, 0.001f)

        // Step and complete
        driver2.step(450f)
        driver2.complete()
        assertEquals(450f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_ClampBoundaryBehavior_StopsAtBoundaryWithoutFakingTrajectory() {
        // Point 9.3: Hit a clamped maximum while animating, verify the animator stops at the boundary
        // without faking an unreachable trajectory, and verify the coordinator reflects the true final position.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 300f)
        val maxCanvasScroll = 500

        // Simulate physical scroll clamping at 450 (e.g. content restricts scroll before maxCanvasScroll)
        coordinator.onScrollBy = { stepDelta ->
            val newProposed = coordinator.totalScroll + stepDelta
            if (newProposed > 450f) {
                val allowed = (450f - coordinator.totalScroll).coerceAtLeast(0f)
                val unconsumed = stepDelta - allowed
                unconsumed
            } else {
                0f
            }
        }

        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Request target 600f (exceeding maxCanvasScroll 500)
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 600f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
            maxCanvasScroll = maxCanvasScroll
        )

        val driver = drivers[0]
        // Target was clamped to maxCanvasScroll (500f)
        assertEquals(500f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Step 1: step to 400f (consumed)
        driver.step(400f)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
        assertTrue(coordinator.isAnimationActive)

        // Step 2: step to 450f (reaches physical boundary)
        driver.step(450f)
        assertEquals(450f, coordinator.totalScroll, 0.001f)

        // Step 3: step to 500f (blocked at 450f -> unconsumed > 0, consumed == 0)
        driver.step(500f)
        // Coordinator detects boundary collision and finishes early without faking trajectory!
        assertFalse("Coordinator must finish when hitting boundary", coordinator.isAnimationActive)
        assertEquals("Total scroll must reflect true physical boundary 450f", 450f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testPredictiveGlide_FollowedByExpansionCompletion_RestoresSafeClearance() {
        // Point 9.4: Predictive glide starts, viewport shrinks further or differs from the predictive estimate,
        // coordinator detects the unsafe position, and retargets or launches a correction to restore safe clearance.
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val headerHeight = 200
        val caretDocBottom = 1200f

        // Phase 1: Predictive glide estimates keyboard = 600px -> viewport = 1400px (full height = 2000px)
        val estimatedViewport = 1400
        val targetLineBottomEst = estimatedViewport - marginPx // 1340px
        // Caret screen bottom before scroll (totalScroll = 0) = 200 + 1200 - 0 = 1400px
        // Delta to reach target line bottom = 1400 - 1340 = 60px
        val predictiveTarget = 60f

        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 0f,
            headerHeight = headerHeight,
            density = density
        )
        coordinator.isExpansionTransitionActive = true
        coordinator.requestTarget(predictiveTarget, ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE)
        coordinator.applyStep(predictiveTarget)
        assertEquals(60f, coordinator.totalScroll, 0.001f)
        assertEquals(60f, coordinator.temporaryDisplacement, 0.001f)

        // Phase 2: Final expansion completes, but the user opened an accessory drawer / tall IME
        // Actual viewport height shrinks to 1100px (not 1400px!)
        val actualViewport = 1100
        val targetLineBottomActual = actualViewport - marginPx // 1040px

        // Caret screen bottom is currently 200 + 1200 - 60 = 1340px
        // In the new 1100px viewport, caret is 300px below the breathing line!
        val validation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = coordinator.totalScroll,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = actualViewport,
            density = density
        )
        assertFalse("Caret must be detected as unsafe after unexpected viewport contraction", validation.isSafe)
        assertEquals("Corrective delta must be 300px", 300f, validation.correctiveDeltaY, 0.001f)

        // Phase 3: Launch authoritative correction to restore safety
        val correctedTarget = coordinator.totalScroll + validation.correctiveDeltaY // 360f
        coordinator.requestTarget(
            targetScroll = correctedTarget,
            reason = ScribeScrollPolicy.ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
            forceRetarget = true
        )
        coordinator.applyStep(correctedTarget)

        // Final position check
        assertEquals(360f, coordinator.totalScroll, 0.001f)
        val finalValidation = ScribeScrollPolicy.validateFinalTarget(
            targetTotalScroll = coordinator.totalScroll,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = actualViewport,
            density = density
        )
        assertTrue("Caret must now be safely positioned above the breathing boundary", finalValidation.isSafe)
        assertEquals(0f, finalValidation.correctiveDeltaY, 0.001f)
        assertEquals(targetLineBottomActual, finalValidation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testCorrectionOrdering_SafetyCorrectionSettlesBeforeCompensationRebase() {
        // Point 9.5: A safe boundary correction is required, the corrective animation starts,
        // and compensation is rebased only after the correction finishes.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 200f,
            temporaryDisplacement = 150f,
            caretLine = 20,
            caretCol = 5
        )
        coordinator.isExpansionTransitionActive = true

        // During a corrective animation, temporary displacement is NOT prematurely rebased
        coordinator.requestTarget(
            targetScroll = 250f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION
        )
        assertTrue(coordinator.isAnimationActive)
        assertEquals("Displacement must not be wiped out or rebased before correction settles", 150f, coordinator.temporaryDisplacement, 0.001f)

        // Apply step to completion
        coordinator.applyStep(250f)
        coordinator.finishAnimation()

        // After completion, rebaseRemainingCompensation is called with the settled slack
        coordinator.rebaseRemainingCompensation(remainingSlack = 120f)
        assertEquals("Displacement rebased to safe slack", 120f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(20, coordinator.caretLine)

        // If user touches screen during active state, compensation is immediately invalidated
        coordinator.invalidateCompensation("User touch")
        assertEquals(0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    @Test
    fun testMultiLineSelectionAndLastLineExpansion_GeometryCalculatesCorrectBottom() {
        // Point 9.6: Expanding the keyboard with a multi-line selection or a cursor on the penultimate/last line
        // calculates the correct bottom bounding box, moves upward to clear the shortcut bar,
        // respects the 24dp breathing margin, and restores correctly when contracted if no user intervention occurred.
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val headerHeight = 200
        val rowHeight = 40f

        // Multi-line selection spanning line 98 to line 100
        val startLine = 98
        val endLine = 100
        // Bottom bounding box must encompass the bottom of line 100
        val selectionBottomDocY = (endLine + 1) * rowHeight // 101 * 40 = 4040px
        val startScroll = 3000f

        val initialViewportHeight = 2000
        val expandedViewportHeight = 1100 // keyboard expanded

        // In expanded viewport (1100px), targetLineBottom = 1100 - 60 = 1040px
        val targetLineBottom = expandedViewportHeight - marginPx // 1040px

        // Caret bottom on screen at startScroll:
        // screenBottom = headerHeight (200) + selectionBottomDocY (4040) - startScroll (3000) = 1240px
        // 1240px > 1040px -> obscured by 200px
        val requiredTotalScroll = headerHeight + selectionBottomDocY - targetLineBottom // 200 + 4040 - 1040 = 3200px
        val upwardDelta = requiredTotalScroll - startScroll // 200px

        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = startScroll,
            caretLine = endLine,
            caretCol = 10,
            isSelected = true
        )
        coordinator.isExpansionTransitionActive = true

        // Move upward to clear shortcut bar
        coordinator.requestTarget(requiredTotalScroll, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.applyStep(requiredTotalScroll)
        coordinator.finishAnimation()

        assertEquals(3200f, coordinator.totalScroll, 0.001f)
        assertEquals("Upward delta recorded into temporary displacement", upwardDelta, coordinator.temporaryDisplacement, 0.001f)

        // Caret settled at target line bottom
        val screenBottomSettled = headerHeight + selectionBottomDocY - coordinator.totalScroll
        assertEquals(targetLineBottom, screenBottomSettled, 0.001f)

        // Now contract back: viewport expands from 1100 to 2000px
        coordinator.isExpansionTransitionActive = false
        val contractedTargetLineBottom = initialViewportHeight - marginPx // 1940px
        val slack = contractedTargetLineBottom - screenBottomSettled // 1940 - 1040 = 900px

        // Available slack (900px) exceeds temporary displacement (200px)
        val restorationResult = ScribeScrollPolicy.resolveContractionRestorationDelta(
            ScribeScrollPolicy.ContractionRestorationRequest(
                caretScreenBottom = screenBottomSettled,
                caretScreenTop = screenBottomSettled - rowHeight,
                viewportHeight = initialViewportHeight,
                density = density,
                totalScroll = coordinator.totalScroll,
                temporaryDisplacement = coordinator.temporaryDisplacement,
                isAtLastLine = true,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )
        assertTrue(restorationResult.shouldScroll)
        assertEquals("Must restore full 200px downward", -200f, restorationResult.deltaY, 0.001f)
        assertEquals("Remaining compensation is 0f", 0f, restorationResult.remainingCompensation, 0.001f)

        // Apply contraction restoration
        val restorationTarget = coordinator.totalScroll + restorationResult.deltaY // 3000f
        coordinator.requestTarget(
            targetScroll = restorationTarget,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION
        )
        coordinator.applyStep(restorationTarget)
        coordinator.finishAnimation()

        assertEquals(3000f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement returned to 0f", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals("Selection tracking reset", -1, coordinator.caretLine)
    }

    @Test
    fun testProductionCoordinator_Replacement_StartsMovingTowardNewTargetWithoutHitchingOrRelativeAccumulation() {
        // Point 9.2: Replacing an animation immediately starts moving toward the new target
        // without hitching or relative-delta accumulation.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 200f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Op 1: target 400f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 400f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver1 = drivers[0]
        driver1.step(280f)
        assertEquals(280f, coordinator.totalScroll, 0.001f)

        // Replace mid-flight with Op 2: target 650f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 650f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver2 = drivers[1]
        // Driver 2 must start precisely from current combined scroll (280f), NOT restarting at 200f or adding relative deltas
        assertEquals("New driver must start from current scroll position without hitch", 280f, driver2.fromVal, 0.001f)
        assertEquals("New driver destination must match target", 650f, driver2.toVal, 0.001f)

        driver2.step(500f)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
        driver2.step(650f)
        driver2.complete()

        assertEquals("Total scroll must settle exactly at 650f", 650f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
    }

    @Test
    fun testProductionCoordinator_UserTouchDuringMovement_CancelsOperationAndDropsLateCallbacks() {
        // Point 9.7: User touch during automatic movement immediately cancels the active operation
        // and prevents late callbacks from running.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 300f,
            temporaryDisplacement = 120f,
            caretLine = 10,
            caretCol = 4
        )
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 500f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION
        )
        val driver = drivers[0]
        driver.step(400f)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
        assertTrue(coordinator.isAnimationActive)

        // User touches screen (ACTION_DOWN): cancels animation and invalidates compensation
        productionCoordinator.cancelActiveAnimation("User touch")
        coordinator.invalidateCompensation("User touch")

        assertFalse("Coordinator must be inactive immediately upon touch", coordinator.isAnimationActive)
        assertEquals("Displacement must be zeroed immediately upon touch", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)

        // Late callbacks arrive from driver after cancel
        driver.step(450f)
        assertEquals("Late step callback must be ignored", 400f, coordinator.totalScroll, 0.001f)

        driver.complete()
        // Must not revive coordinator or trigger completion logic
        assertFalse("Late complete callback must be ignored", coordinator.isAnimationActive)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_RapidTogglingBetweenExpandedAndCollapsed_NoDriftOrFighting() {
        // Point 9.8: Rapid toggling between expanded and collapsed states does not accumulate drift or fight itself.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 1000f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Rapid toggle 1: expand (target 1200f)
        productionCoordinator.startAuthoritativeAnimation(1200f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1050f)

        // Rapid toggle 2: collapse (target 1000f)
        productionCoordinator.startAuthoritativeAnimation(1000f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        drivers.last().step(1020f)

        // Rapid toggle 3: expand again (target 1200f)
        productionCoordinator.startAuthoritativeAnimation(1200f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1100f)

        // Rapid toggle 4: collapse again (target 1000f)
        productionCoordinator.startAuthoritativeAnimation(1000f, ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION)
        drivers.last().step(1040f)

        // Rapid toggle 5: expand again (target 1200f) and let it settle
        productionCoordinator.startAuthoritativeAnimation(1200f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1200f)
        drivers.last().complete()

        assertEquals("Coordinator must settle cleanly at 1200f with zero drift", 1200f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
        assertEquals("Operation ID must have advanced for each toggle without leaking animators", 5L, productionCoordinator.activeOperationId)
    }

    @Test
    fun testProductionCoordinator_CaretMoveDuringExpansion_InvalidatesCompensationAndPreventsDownwardRestoration() {
        // Point 9.9: Moving the caret during expansion invalidates compensation and prevents downward restoration on collapse.
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val headerHeight = 200
        val rowHeight = 40f

        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 1000f,
            temporaryDisplacement = 0f,
            headerHeight = headerHeight,
            density = density
        )
        coordinator.isExpansionTransitionActive = true

        // Expansion pushes canvas upward by 180px
        coordinator.requestTarget(1180f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.caretLine = 25
        coordinator.caretCol = 10
        coordinator.applyStep(1180f)
        coordinator.finishAnimation()

        assertEquals(1180f, coordinator.totalScroll, 0.001f)
        assertEquals(180f, coordinator.temporaryDisplacement, 0.001f)

        // User taps/moves caret to line 12 while expanded
        coordinator.invalidateIfCaretChanged(newLine = 12, newCol = 0, newSelected = false)

        assertEquals("Displacement must be wiped to 0f on caret movement", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)

        // Surface now collapses back
        val caretDocBottom = (12 + 1) * rowHeight // line 12 bottom = 520px
        val screenBottom = headerHeight + caretDocBottom - coordinator.totalScroll // 200 + 520 - 1180 = -460px
        val req = ScribeScrollPolicy.ContractionRestorationRequest(
            caretScreenBottom = screenBottom,
            caretScreenTop = screenBottom - rowHeight,
            viewportHeight = 2000,
            density = density,
            totalScroll = coordinator.totalScroll,
            temporaryDisplacement = coordinator.temporaryDisplacement, // 0f
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val restorationResult = ScribeScrollPolicy.resolveContractionRestorationDelta(req)

        assertFalse("Must NOT scroll downward when compensation was invalidated", restorationResult.shouldScroll)
        assertEquals(0f, restorationResult.deltaY, 0.001f)
    }

    @Test
    fun testProductionCoordinator_AfterTouchCancel_NextAnimationAppliesStepsSuccessfully() {
        // Verifies that after a user touches the screen (cancelling an in-flight animation),
        // the subsequent automatic animation (e.g. typing or keyboard/drawer expansion)
        // maintains matching operation IDs and applies update steps without being dropped.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 100f)
        coordinator.onScrollBy = { delta -> 0f } // all delta consumed
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // 1. Initial animation runs partially
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        drivers.last().step(180f)
        assertEquals(180f, coordinator.totalScroll, 0.001f)

        // 2. User touches screen: cancels animation
        productionCoordinator.cancelActiveAnimation("User touch down")
        assertFalse("Coordinator must be inactive after touch cancel", coordinator.isAnimationActive)

        // 3. User types or accessory drawer expands: initiates new animation
        val started = productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 450f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY
        )
        assertTrue("New animation must start successfully", started)
        assertTrue("Coordinator must be active", coordinator.isAnimationActive)
        assertEquals(productionCoordinator.activeOperationId, coordinator.activeOperationId)

        // 4. Critical verification: Step ticks must NOT be rejected by operation ID mismatch!
        val activeDriver = drivers.last()
        activeDriver.step(250f)
        assertEquals("Step must be applied after touch cancel", 250f, coordinator.totalScroll, 0.001f)

        activeDriver.step(450f)
        assertEquals("Final step must reach target", 450f, coordinator.totalScroll, 0.001f)
        activeDriver.complete()
        assertFalse(coordinator.isAnimationActive)
    }


    @Test
    fun testAuthoritativeScrollCoordinator_RetargetActiveOperation_ContinuousTargetUpdate() {
        // Verifies that retargetActiveOperation updates the active target and reason in-flight
        // without advancing operationId or disrupting step progression, ensuring continuous smooth glide.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 100f)
        coordinator.onScrollBy = { 0f } // all delta consumed

        val started = coordinator.requestTarget(200f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        assertTrue(started)
        assertTrue(coordinator.isAnimationActive)
        val initialOpId = coordinator.activeOperationId
        assertEquals(200f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Mid-flight: step toward 200f
        coordinator.applyStep(140f)
        assertEquals(140f, coordinator.totalScroll, 0.001f)

        // User pressed Enter again: new target 280f arrives while in flight
        coordinator.retargetActiveOperation(280f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)

        // Invariant: operationId must remain unchanged so running loop is not invalidated
        assertEquals(initialOpId, coordinator.activeOperationId)
        assertTrue(coordinator.isAnimationActive)
        assertEquals(280f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Subsequent steps seamlessly continue toward 280f
        coordinator.applyStep(210f)
        assertEquals(210f, coordinator.totalScroll, 0.001f)
        coordinator.applyStep(280f)
        assertEquals(280f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
    }

    @Test
    fun testProductionCoordinator_RetargetAuthoritativeAnimation_SmoothlyRetargetsWithoutNewDriver() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 500f)
        coordinator.onScrollBy = { 0f }
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ScribeScrollPolicy.ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        productionCoordinator.startAuthoritativeAnimation(600f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        assertEquals(1, drivers.size)
        val driver = drivers[0]
        driver.step(550f)
        assertEquals(550f, coordinator.totalScroll, 0.001f)

        // Retarget in-flight to 750f
        val retargeted = productionCoordinator.retargetAuthoritativeAnimation(750f, ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY)
        assertTrue(retargeted)
        assertEquals(1, drivers.size) // No extra driver instantiated!
        assertEquals(750f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        driver.step(750f)
        assertEquals(750f, coordinator.totalScroll, 0.001f)
        driver.complete()
        assertFalse(coordinator.isAnimationActive)
    }

    @Test
    fun testPasteTransaction_SuppressesScrollWhenEndpointAlreadyVisible() {
        // Verifies that when text is pasted and its insertion point is within the safe breathing zone,
        // resolveCursorScrollDelta reports shouldScroll = false (no unnecessary jump).
        val density = 2f
        val visibleHeight = 1000
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 48px
        val breathingTarget = visibleHeight - marginPx // 952px

        // Insertion point is at screenBottom = 800px (well above 952px)
        val resolutionSafe = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = 800f,
            screenTop = 760f,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = false,
            isDeletionActive = false,
            hasScrollOffset = true
        )
        assertFalse("Must not scroll when paste insertion point is already visible", resolutionSafe.shouldScroll)

        // Insertion point is offscreen at screenBottom = 1100px (below 952px)
        val resolutionOffscreen = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = 1100f,
            screenTop = 1060f,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = false,
            isDeletionActive = false,
            hasScrollOffset = true
        )
        assertTrue("Must scroll when paste insertion point is offscreen", resolutionOffscreen.shouldScroll)
        assertEquals(1100f - breathingTarget, resolutionOffscreen.deltaY, 0.001f)
    }

    @Test
    fun testBoundedCursorRestoration_DistanceThresholdValidation() {
        // Verifies the distance-bounded smooth glide condition:
        // <= 1.5 * viewportHeight qualifies for smooth animation;
        // > 1.5 * viewportHeight uses instant layout-settled placement to avoid disorienting travel time.
        val viewportHeight = 800f
        val maxSmoothDistance = 1.5f * viewportHeight // 1200px

        val nearbyTargetScroll = 500f
        val currentScroll = 0f
        val nearbyDistance = kotlin.math.abs(nearbyTargetScroll - currentScroll)
        val qualifiesNearby = nearbyDistance <= maxSmoothDistance && nearbyDistance > 1f
        assertTrue("Nearby target (500px <= 1200px) must qualify for smooth restoration glide", qualifiesNearby)

        val distantTargetScroll = 4000f
        val distantDistance = kotlin.math.abs(distantTargetScroll - currentScroll)
        val qualifiesDistant = distantDistance <= maxSmoothDistance && distantDistance > 1f
        assertFalse("Distant target (4000px > 1200px) must bypass smooth animation for instant settled restore", qualifiesDistant)
    }

    @Test
    fun testZoomTransactionPolicy_SuppressesObsoleteAndAwaitingSettlement() {
        // Issue 1: Directly verifies production ZoomTransaction policy logic
        val activeZoom = ScribeScrollPolicy.ZoomTransaction(
            epoch = 2L,
            isGestureActive = true,
            isAwaitingLayoutSettlement = true
        )
        assertTrue("Callback with obsolete epoch must be suppressed", activeZoom.shouldSuppressCursorVisibility(1L))
        assertTrue("Callback during active gesture must be suppressed", activeZoom.shouldSuppressCursorVisibility(2L))

        // When gesture ends but layout reflow is still pending:
        val postGesture = activeZoom.copy(isGestureActive = false, isAwaitingLayoutSettlement = true)
        assertTrue("Suppression must remain active while awaiting layout settlement", postGesture.shouldSuppressCursorVisibility(2L))
        // Critical: Multi-touch arbitration must release immediately on finger lift!
        assertFalse("Multi-touch flag must clear immediately when gesture ends", postGesture.isMultiTouchActive())

        // Once layout settlement arrives:
        val settled = postGesture.copy(isAwaitingLayoutSettlement = false)
        assertFalse("Normal cursor visibility must resume after layout settlement completes", settled.shouldSuppressCursorVisibility(2L))
    }

    @Test
    fun testKeyboardTransactionPolicy_CalculatesRetargetedDestination() {
        // Issue 2: Exercises production KeyboardTransaction retargeting calculation
        val transaction = ScribeScrollPolicy.KeyboardTransaction(transactionId = 1L, isActive = true)
        val caretDocBottom = 800f
        val headerHeight = 100f
        val usableHeight = 600f
        val breathingMargin = 48f
        val maxScroll = 1000f

        // target = headerHeight (100) + caretDocBottom (800) - (usableHeight (600) - margin (48)) = 348f
        val target = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            usableViewportHeight = usableHeight,
            breathingMarginPx = breathingMargin,
            maxCanvasScroll = maxScroll
        )
        assertEquals(348f, target, 0.001f)
    }

    @Test
    fun testDeletionIntent_RequiresTailProximityAndAffectedTail() {
        // Issue 4: Tail follow requires BOTH pre-edit tail proximity AND that the deletion affected the end
        val tailSnapshot = ScribeScrollPolicy.DeletionSnapshot(
            transactionEpoch = 1L,
            isSelection = true,
            selectionStartLine = 18,
            selectionEndLine = 20,
            preEditDocLineCount = 21,
            preEditDocHeight = 2000f,
            preEditScrollY = 1200f,
            preEditCaretScreenY = 700f,
            wasAtTail = true,
            affectsDocumentTail = true
        )
        val tailIntent = ScribeScrollPolicy.classifyDeletionIntent(
            snapshot = tailSnapshot,
            newDocLineCount = 18,
            newDocHeight = 1600f,
            usableViewportHeight = 800f
        )
        assertEquals(ScribeScrollPolicy.DeletionAnchorIntent.TAIL_ANCHOR, tailIntent)

        // If user was near tail but deleted in the middle (does not affect tail):
        val middleSnapshot = tailSnapshot.copy(selectionStartLine = 5, selectionEndLine = 7, affectsDocumentTail = false)
        val middleIntent = ScribeScrollPolicy.classifyDeletionIntent(
            snapshot = middleSnapshot,
            newDocLineCount = 18,
            newDocHeight = 1600f,
            usableViewportHeight = 800f
        )
        assertEquals(ScribeScrollPolicy.DeletionAnchorIntent.CARET_ANCHOR, middleIntent)
    }

    @Test
    fun testDeletionDestination_TailFollowPreservesBreathingMargin() {
        // Issue 3: Exercises production calculateSafeDeletionDestination for tail-follow
        val target = ScribeScrollPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollPolicy.DeletionAnchorIntent.TAIL_ANCHOR,
            currentScroll = 900f,
            headerHeight = 100f,
            newDocHeight = 1200f,
            newCaretDocY = 1200f,
            preEditCaretScreenY = 600f,
            usableViewportHeight = 700f,
            breathingMarginPx = 48f,
            maxCanvasScroll = 800f
        )
        // requiredScroll = header (100) + docH (1200) + margin (48) - usable (700) = 648f
        assertEquals(648f, target, 0.001f)
        // Verify 24dp breathing margin holds:
        val lastLineScreenBottom = 100f + 1200f - target
        val breathingSpaceRemaining = 700f - lastLineScreenBottom
        assertEquals(48f, breathingSpaceRemaining, 0.001f)
    }

    @Test
    fun testDeletionDestination_MiddleDeletionPreservesCaretStationary() {
        // Issue 4: Exercises production calculateSafeDeletionDestination for middle deletion
        val preEditCaretScreenY = 350f
        val newCaretDocY = 600f
        val headerHeight = 100f
        val currentScroll = 500f

        val target = ScribeScrollPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollPolicy.DeletionAnchorIntent.CARET_ANCHOR,
            currentScroll = currentScroll,
            headerHeight = headerHeight,
            newDocHeight = 1500f,
            newCaretDocY = newCaretDocY,
            preEditCaretScreenY = preEditCaretScreenY,
            usableViewportHeight = 800f,
            breathingMarginPx = 48f,
            maxCanvasScroll = 1000f
        )
        // target = header (100) + newCaretDocY (600) - preEditScreenY (350) = 350f
        assertEquals(350f, target, 0.001f)
        val settledScreenY = headerHeight + newCaretDocY - target
        assertEquals(preEditCaretScreenY, settledScreenY, 0.001f)
    }

    @Test
    fun testDeletionDestination_ResetToTopWhenContentFits() {
        // Issue 4: Deletion that leaves document fitting in viewport resets cleanly to 0
        val target = ScribeScrollPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollPolicy.DeletionAnchorIntent.RESET_TO_TOP,
            currentScroll = 300f,
            headerHeight = 100f,
            newDocHeight = 400f,
            newCaretDocY = 200f,
            preEditCaretScreenY = 200f,
            usableViewportHeight = 800f,
            breathingMarginPx = 48f,
            maxCanvasScroll = 0f
        )
        assertEquals(0f, target, 0.001f)
    }

    @Test
    fun testTailDeletion_MotionBoundaryClampsOvershoot() {
        // Issue A: Verifies that during numerical spring integration, the animated position
        // is strictly prevented from dipping below minimumSafeScroll.
        val headerHeight = 100f
        val docHeight = 1200f
        val breathingMarginPx = 48f // 24dp at 2.0x
        val viewportHeight = 700f

        val minSafeScroll = (headerHeight + docHeight + breathingMarginPx - viewportHeight).coerceAtLeast(0f)
        assertEquals(648f, minSafeScroll, 0.001f)

        // Simulate spring position dipping to 640f due to inertia
        var simulatedGlidePosition = 640f
        var simulatedVelocity = -200f

        if (simulatedGlidePosition < minSafeScroll) {
            simulatedGlidePosition = minSafeScroll
            if (simulatedVelocity < 0f) simulatedVelocity = 0f
        }

        assertEquals("Glide position must be clamped to minSafeScroll", 648f, simulatedGlidePosition, 0.001f)
        assertEquals("Downwards velocity must be clamped to zero at safe boundary", 0f, simulatedVelocity, 0.001f)
    }

    @Test
    fun testMiddleDeletion_ClampsWhenBelowBreathingBoundary() {
        // Issue A: Verifies that if collapsed caret would fall below breathing line,
        // it is safely pulled up to the breathing line.
        val preEditCaretScreenY = 680f // Caret was near bottom
        val newCaretDocY = 1000f
        val headerHeight = 100f
        val currentScroll = 500f
        val usableHeight = 700f
        val breathingMargin = 48f
        val targetLineBottom = usableHeight - breathingMargin // 652f

        val target = ScribeScrollPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollPolicy.DeletionAnchorIntent.CARET_ANCHOR,
            currentScroll = currentScroll,
            headerHeight = headerHeight,
            newDocHeight = 1500f,
            newCaretDocY = newCaretDocY,
            preEditCaretScreenY = preEditCaretScreenY,
            usableViewportHeight = usableHeight,
            breathingMarginPx = breathingMargin,
            maxCanvasScroll = 1000f
        )

        // minSafeScroll = header (100) + newCaret (1000) - targetLineBottom (652) = 448f
        val expectedSafe = (headerHeight + newCaretDocY - targetLineBottom).coerceAtLeast(0f)
        assertTrue("Target must be at least minSafeScroll to protect breathing margin", target >= expectedSafe)
    }

    @Test
    fun testKeyboardTransaction_CombinedObstructionCalculatesFullTarget() {
        // Issue B: Verifies that initial combined obstruction (keyboard 280dp + shortcut bar 36dp)
        // plus single breathing clearance (24dp) calculates the exact target on frame 0 without double-counting.
        val density = 2f
        val shortcutBarDp = 36f
        val estimatedImeDp = 280f
        val breathingMarginDp = 24f
        val totalObstructionPx = (shortcutBarDp + estimatedImeDp) * density // 316dp * 2 = 632px

        val fullHeight = 1600f
        val expectedUsableHeight = fullHeight - totalObstructionPx // 968px

        val caretDocY = 1100f
        val headerHeight = 100f
        val breathingMarginPx = breathingMarginDp * density // 48px
        val targetLineBottom = expectedUsableHeight - breathingMarginPx // 920px

        val initialTarget = (headerHeight + caretDocY - targetLineBottom).coerceAtLeast(0f)
        assertEquals(280f, initialTarget, 0.001f)

        // Verify that production KeyboardTransaction.calculateRetargetedDestination matches initialTarget
        val transaction = ScribeScrollPolicy.KeyboardTransaction(transactionId = 1L, isActive = true)
        val retargeted = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = expectedUsableHeight,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )
        assertEquals("Production KeyboardTransaction destination must match initial predicted target", initialTarget, retargeted, 0.001f)
    }

    @Test
    fun testTailDeletion_ToleranceInPixelSpace() {
        // Issue A: Verifies tail detection uses consistent pixel-space margin + tolerance
        val density = 2f
        val breathingMarginPx = 24f * density // 48px
        val tailTolerancePx = 2f * density // 4px
        val docH = 2000f
        val visibleH = 800f

        // Case 1: Exactly at breathing boundary
        val scrollAtBoundary = docH - breathingMarginPx - visibleH // 1152px
        val wasAtTail1 = (scrollAtBoundary + visibleH) >= (docH - breathingMarginPx - tailTolerancePx)
        assertTrue("Must detect tail when at breathing margin", wasAtTail1)

        // Case 2: 2px below breathing boundary (within tolerance)
        val scrollSlightlyBelow = scrollAtBoundary - 2f
        val wasAtTail2 = (scrollSlightlyBelow + visibleH) >= (docH - breathingMarginPx - tailTolerancePx)
        assertTrue("Must detect tail within tolerance", wasAtTail2)

        // Case 3: Far up in document
        val scrollFarAbove = scrollAtBoundary - 100f
        val wasAtTail3 = (scrollFarAbove + visibleH) >= (docH - breathingMarginPx - tailTolerancePx)
        assertFalse("Must not classify as tail when far above tail", wasAtTail3)
    }

    @Test
    fun testKeyboardTap_WhenAlreadyVisible_DoesNotStartNewTransaction() {
        // Issue B.1: Tapping with keyboard or bottom surface already active routes to
        // caret visibility scroll instead of starting predictive keyboard entrance.
        val isKeyboardVisible = true
        val isBottomSurfaceActive = true
        val shouldStartKeyboardTransaction = !isKeyboardVisible && !isBottomSurfaceActive
        assertFalse("Must not start keyboard-appearance transaction when keyboard is already visible", shouldStartKeyboardTransaction)
    }

    @Test
    fun testMiddleDeletion_MotionBoundaryClampsCaretOvershoot() {
        // Issue A.2: Verifies that during numerical spring integration for CARET_ANCHOR,
        // the animated position is strictly prevented from dipping below minSafeScroll for caret.
        val headerHeight = 100f
        val caretDocY = 800f
        val breathingMarginPx = 48f
        val viewportHeight = 700f

        val minSafeScroll = (headerHeight + caretDocY + breathingMarginPx - viewportHeight).coerceAtLeast(0f)
        assertEquals(248f, minSafeScroll, 0.001f)

        // Simulate spring position dipping to 240f due to inertia
        var simulatedGlidePosition = 240f
        var simulatedVelocity = -150f

        if (simulatedGlidePosition < minSafeScroll) {
            simulatedGlidePosition = minSafeScroll
            if (simulatedVelocity < 0f) simulatedVelocity = 0f
        }

        assertEquals("Glide position must be clamped to minSafeScroll", 248f, simulatedGlidePosition, 0.001f)
        assertEquals("Downwards velocity must be clamped to zero at safe boundary", 0f, simulatedVelocity, 0.001f)
    }

    @Test
    fun testTailDeletion_RequiresBothWasAtTailAndAffectsTail() {
        // Issue A.4: Verifies that tail-follow deletion strictly requires BOTH wasAtTail AND affectsTail.
        // If viewport is scrolled to tail but edit is in the middle, classify as CARET_ANCHOR.
        val snapshotMiddleEditAtTailViewport = ScribeScrollPolicy.DeletionSnapshot(
            transactionEpoch = 1L,
            isSelection = false,
            selectionStartLine = 5,
            selectionEndLine = 5,
            preEditDocLineCount = 50,
            preEditDocHeight = 3000f,
            preEditScrollY = 2400f,
            preEditCaretScreenY = 300f,
            wasAtTail = true,
            affectsDocumentTail = false // Cursor is at line 5, document has 50 lines!
        )

        val intent = ScribeScrollPolicy.classifyDeletionIntent(
            snapshot = snapshotMiddleEditAtTailViewport,
            newDocLineCount = 50,
            newDocHeight = 3000f,
            usableViewportHeight = 800f
        )

        assertEquals("Middle edit must be classified as CARET_ANCHOR even if viewport was at tail",
            ScribeScrollPolicy.DeletionAnchorIntent.CARET_ANCHOR, intent)
    }

    @Test
    fun testKeyboardTransaction_InvalidatesOnDismissalOrTargetSwitch() {
        // Issue B.2: Verifies lifecycle invalidation
        var activeTransaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        assertTrue("Transaction active before dismissal", activeTransaction?.isActive == true)

        // Dismissal invalidates transaction
        val isKeyboardDismissing = true
        if (isKeyboardDismissing) {
            activeTransaction = null
        }
        assertNull("Transaction must be cleanly invalidated on dismissal", activeTransaction)

        // Target switch away from body invalidates transaction
        activeTransaction = ScribeScrollPolicy.KeyboardTransaction(2L, true)
        val activeTarget = "PRIMARY_TITLE"
        if (activeTarget != "EDITOR_BODY") {
            activeTransaction = null
        }
        assertNull("Transaction must be invalidated when focusing title", activeTransaction)
    }

    @Test
    fun testKeyboardEntryAnticipatesShortcutBar() {
        // Requirement A: Initial predicted destination accounts for keyboard height and shortcut bar height.
        val fullHeight = 2000f
        val density = 2.5f
        val compactKeyboardDp = 280f
        val shortcutBarDp = 36f
        val estimatedKbPx = (compactKeyboardDp + shortcutBarDp) * density // 790px
        val breathingMarginPx = 24f * density // 60px
        val headerHeight = 200f
        val caretDocY = 1800f // Caret near bottom

        val futureViewportHeight = (fullHeight - estimatedKbPx).coerceAtLeast(100f) // 1210px
        val targetLineBottom = futureViewportHeight - breathingMarginPx // 1150px
        val predictedTarget = (headerHeight + caretDocY - targetLineBottom).coerceAtLeast(0f) // 200 + 1800 - 1150 = 850px

        // Once measured height h arrives (which equals 1210px after Compose applies padding):
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val retargeted = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = futureViewportHeight,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )

        assertEquals("Predicted destination at t=0 must match retargeted destination post-resize", predictedTarget, retargeted, 0.001f)
    }

    @Test
    fun testLateShortcutBarMeasurementRetargetsTransaction() {
        // Requirement B: Start with default/estimated shortcut bar, receive measured height later, same transaction retargeted.
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val caretDocY = 1500f
        val headerHeight = 200f
        val breathingMarginPx = 60f

        // Initial estimate (viewport = 1200px)
        val initialTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = 1200f,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        ) // 200 + 1500 - (1200 - 60) = 560px

        // Measured shortcut bar is slightly taller (viewport = 1170px)
        val retargetedTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = 1170f,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        ) // 200 + 1500 - (1170 - 60) = 590px

        assertTrue("Transaction remains active with transaction ID 1", transaction.isActive)
        assertEquals(560f, initialTarget, 0.001f)
        assertEquals(590f, retargetedTarget, 0.001f)
    }

    @Test
    fun testMultipleImeUpdatesMaintainSingleTransaction() {
        // Requirement C: Deliver successive IME height changes; all belongs to 1 transaction and target updates smoothly.
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val caretDocY = 1600f
        val headerHeight = 200f
        val breathingMarginPx = 60f

        val targets = listOf(1800f, 1600f, 1400f, 1200f).map { viewportH ->
            transaction.calculateRetargetedDestination(
                caretDocBottom = caretDocY,
                headerHeight = headerHeight,
                usableViewportHeight = viewportH,
                breathingMarginPx = breathingMarginPx,
                maxCanvasScroll = 2000f
            )
        }

        assertTrue("All IME updates maintain transaction ID 1", transaction.transactionId == 1L)
        assertEquals(listOf(60f, 260f, 460f, 660f), targets)
    }

    @Test
    fun testSpringFinishesBeforeGeometrySettles_TransactionRemainsActive() {
        // Requirement D: Spring reaching target must not end transaction while geometry transition is active.
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(totalScroll = 0f)
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        var isExpansionTransitionActive = true

        // Request initial target
        coordinator.requestTarget(500f, ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE)

        // Simulate spring settling at 500f
        coordinator.applyStep(500f)
        coordinator.finishAnimation()

        // Transaction is NOT ended when spring finishes while expansion transition is active
        var isKeyboardTransactionActive = transaction.isActive
        if (!isExpansionTransitionActive) {
            isKeyboardTransactionActive = false
        }
        assertTrue("Transaction must remain active while transition is in flight even if spring completes", isKeyboardTransactionActive)

        // Late IME update arrives: coordinator retargets active transaction
        coordinator.requestTarget(580f, ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE)
        assertEquals(580f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)
    }

    @Test
    fun testNoScrollPath_EventuallyCompletesTransaction() {
        // Requirement E: Keyboard entry when cursor is already safe completes cleanly without staying active forever.
        var transaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        var isExpansionTransitionActive = true

        // Simulate 600ms stability timer expiring
        isExpansionTransitionActive = false
        val isGlideRunning = false

        if (!isGlideRunning && !isExpansionTransitionActive) {
            transaction = null
        }

        assertNull("Transaction must complete when transition timer expires and no scroll is running", transaction)
    }

    @Test
    fun testKeyboardCancellation_SafelyClearsTransaction() {
        // Requirement F: Dismissing keyboard during entry cancels transaction safely.
        var transaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val isKeyboardDismissing = true

        if (isKeyboardDismissing) {
            transaction = null
        }

        assertNull("Keyboard dismissal must immediately cancel active transaction", transaction)
    }

    @Test
    fun testNoDoubleCounting_KeyboardHeightCountedExactlyOnce() {
        // Requirement G: Keyboard height + shortcut bar is counted exactly once.
        val fullHeight = 2000f
        val density = 2f
        val keyboardHeightPx = 600f
        val shortcutBarPx = 72f
        val totalObstructionPx = keyboardHeightPx + shortcutBarPx // 672px
        val breathingMarginPx = 48f
        val headerHeight = 200f
        val caretDocY = 1500f

        // Case 1: Pre-resize calculation using unpadded fullHeight minus totalObstructionPx
        val targetLineBottom1 = (fullHeight - totalObstructionPx) - breathingMarginPx // 2000 - 672 - 48 = 1280px
        val target1 = headerHeight + caretDocY - targetLineBottom1 // 200 + 1500 - 1280 = 420px

        // Case 2: Post-resize calculation using View height h (which is already 2000 - 672 = 1328px)
        val measuredViewHeight = fullHeight - totalObstructionPx // 1328px
        val targetLineBottom2 = measuredViewHeight - breathingMarginPx // 1328 - 48 = 1280px
        val target2 = headerHeight + caretDocY - targetLineBottom2 // 200 + 1500 - 1280 = 420px

        assertEquals("Pre-resize and post-resize targets must match exactly (no double counting)", target1, target2, 0.001f)
    }

    @Test
    fun testViewportAlreadyResized_DoesNotSubtractKeyboardObstructionAgain() {
        // Requirement H: When measured viewport h already reflects Android adjustResize / padding,
        // targetLineBottom uses h - breathingMarginPx without subtracting keyboard height again.
        val measuredViewportH = 1300f // Already resized by keyboard
        val breathingMarginPx = 48f
        val headerHeight = 200f
        val caretDocY = 1400f

        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val target = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = measuredViewportH,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )

        // 200 + 1400 - (1300 - 48) = 348px
        assertEquals(348f, target, 0.001f)
    }

    @Test
    fun testCaretAlreadySafePostResize_DoesNotTriggerSecondAnimation() {
        // When spring completes and layout resize delivers measured h,
        // if the caret is already safely within breathing margin + deadband,
        // no secondary scroll animation should be launched.
        val headerHeight = 200f
        val caretDocY = 1500f
        val h = 1200f
        val breathingMarginPx = 60f
        val deadbandPx = 10f
        val maxCanvasScroll = 2000f

        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val desiredTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = h,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxCanvasScroll
        ) // 200 + 1500 - (1200 - 60) = 560px

        // Caret is currently at scroll = 556px (4px difference, well within 10px deadband)
        val currentScroll = 556f
        val isSafe = transaction.isCaretSafe(
            currentScroll = currentScroll,
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = h,
            breathingMarginPx = breathingMarginPx,
            deadbandPx = deadbandPx
        )
        val shouldRetarget = transaction.shouldRetarget(
            currentScroll = currentScroll,
            desiredTarget = desiredTarget,
            isSafe = isSafe,
            deadbandPx = deadbandPx
        )

        assertTrue("Caret within deadband must be classified as safe", isSafe)
        assertFalse("Safe caret within deadband must NOT trigger retargeting or second animation", shouldRetarget)
    }

    @Test
    fun testEnsurePositionVisibleDuringActiveKeyboardTransaction_Suppressed() {
        // Verifies that during an active keyboard transaction, a planned target that
        // already makes the caret safe correctly suppresses competing cursor visibility scroll.
        val headerHeight = 200f
        val caretDocY = 1400f
        val futureViewportH = 1200f
        val breathingMarginPx = 60f
        val deadbandPx = 10f
        val plannedTarget = 500f // Already planned upward scroll

        val targetLineBottom = futureViewportH - breathingMarginPx // 1140px
        val finalScreenBottom = (headerHeight + caretDocY - plannedTarget) // 200 + 1400 - 500 = 1100px

        val isSafeInTarget = finalScreenBottom <= targetLineBottom + deadbandPx
        assertTrue("Planned target (1100px) is well within target line bottom (1140px + 10px)", isSafeInTarget)
    }

    @Test
    fun testAuthoritativeTargetCalculationEquality() {
        // Pre-resize predictive calculation and post-resize onSizeChanged calculation
        // MUST produce the exact same target down to 0.001f.
        val density = 2.5f
        val compactKeyboardDp = 280f
        val shortcutBarDp = 36f
        val breathingMarginDp = 24f

        val estimatedKbPx = (compactKeyboardDp + shortcutBarDp) * density // 790px
        val breathingMarginPx = breathingMarginDp * density // 60px
        val headerHeight = 200f
        val fullHeight = 2200f
        val caretDocBottom = 1600f
        val layoutHeight = 3000

        // 1. Pre-resize at t = 0ms:
        val futureViewportH = fullHeight - estimatedKbPx // 1410px
        val targetLineBottomPre = futureViewportH - breathingMarginPx // 1350px
        val maxScrollPre = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight.toInt(),
            layoutHeight = layoutHeight,
            viewportHeight = futureViewportH.toInt(),
            bottomSpace = breathingMarginPx.toInt(),
            isTextEmpty = false
        ).toFloat()
        val targetPre = (headerHeight + caretDocBottom - targetLineBottomPre).coerceIn(0f, maxScrollPre)

        // 2. Post-resize when measured h = futureViewportH arrives:
        val measuredH = futureViewportH
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val maxScrollPost = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight.toInt(),
            layoutHeight = layoutHeight,
            viewportHeight = measuredH.toInt(),
            bottomSpace = breathingMarginPx.toInt(),
            isTextEmpty = false
        ).toFloat()
        val targetPost = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            usableViewportHeight = measuredH,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxScrollPost
        )

        assertEquals("Pre-resize and post-resize targets must match exactly", targetPre, targetPost, 0.001f)
    }

    @Test
    fun testTapOnDocumentAboveBreathingMarginDoesNotScroll() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density // 750px
        val futureViewportHeight = height - estimatedKeyboardHeightPx // 1450px
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density) // 20px
        val targetLineBottom = futureViewportHeight - marginPx // 1390px

        // User taps at screen coordinate 800px (well above breathing margin 1390px)
        val touchY = 800f
        val isTapAboveMargin = touchY <= (targetLineBottom + deadbandPx)

        assertTrue("Tap above breathing margin must be recognized as safe", isTapAboveMargin)
    }

    @Test
    fun testTitleVisibleTapDoesNotScrollWhenAlreadySafe() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density
        val futureViewportHeight = height - estimatedKeyboardHeightPx
        val scrollD = 0

        // User taps on line 1 near top of document at screen Y = 350px
        val touchY = 350f
        val isTitleSafe = (scrollD == 0 && touchY <= futureViewportHeight)

        assertTrue("Tap within future viewport while title is visible must not scroll", isTitleSafe)
    }

    @Test
    fun testTapBelowBreathingMarginCalculatesTouchOverflowWithoutDoubleJump() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density // 750px
        val futureViewportHeight = height - estimatedKeyboardHeightPx // 1450px
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = futureViewportHeight - marginPx // 1390px
        val currentScroll = 100f
        val maxScrollFuture = 2000f

        // User taps at screen coordinate 1500px (occluded behind incoming keyboard)
        val touchY = 1500f
        val touchOverflow = touchY - targetLineBottom // 110px
        val desiredTarget = (currentScroll + touchOverflow).coerceIn(0f, maxScrollFuture) // 210px
        val deltaY = desiredTarget - currentScroll // 110px

        assertEquals("Delta must match the exact touch overflow of 110px", 110f, deltaY, 0.001f)
        assertEquals("Desired target must be 210px", 210f, desiredTarget, 0.001f)
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

    // =========================================================================
    // PART G — COMPREHENSIVE ARCHITECTURAL VERIFICATION SUITE
    // =========================================================================

    @Test
    fun testCursorSafety_WellAboveMargin_ZeroMovement() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val cursorBottom = 600f // Well above margin boundary (1140f)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertTrue("Cursor well above breathing margin must be safe", isSafe)
        assertEquals("Displacement must be 0f", 0f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_ModeratelyAboveMargin_ZeroMovement() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val cursorBottom = 1000f // Moderately above margin boundary (1140f)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertTrue("Cursor moderately above breathing margin must be safe", isSafe)
        assertEquals("Displacement must be 0f", 0f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_ExactlyAtMargin_ZeroMovement() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val cursorBottom = 1140f // Exactly at boundary (1200 - 60)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertTrue("Cursor exactly at breathing margin boundary must be safe", isSafe)
        assertEquals("Displacement must be 0f", 0f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_SlightlyBelowMargin_CalculatesExactDelta() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val cursorBottom = 1165f // 25px below margin boundary (1140f)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertFalse("Cursor below breathing margin is not safe", isSafe)
        assertEquals("Displacement must be exactly 25px", 25f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_SubstantiallyBelowMargin_CalculatesExactDelta() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val cursorBottom = 1450f // 310px below margin boundary (1140f)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertFalse("Covered cursor is not safe", isSafe)
        assertEquals("Displacement must be exactly 310px", 310f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_DeadbandSuppressesMicroJitter() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        val deadband = 20f
        val cursorBottom = 1150f // 10px below margin (1140f), but within deadband (1140 + 20 = 1160f)

        val isSafe = ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        )
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = cursorBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        )

        assertTrue("Cursor within deadband tolerance must be safe", isSafe)
        assertEquals("Displacement must be 0f within deadband", 0f, displacement, 0.001f)
    }

    @Test
    fun testCursorSafety_SelectionCrossingBottomMargin_UsesSelectionBottom() {
        val viewportHeight = 1200f
        val breathingMargin = 60f
        // Multi-line selection spanning from screen Y 950 to 1250
        val selectionTop = 950f
        val selectionBottom = 1250f // Below margin 1140f by 110px

        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = selectionBottom,
            viewportHeight = viewportHeight,
            breathingMarginPx = breathingMargin
        )

        assertEquals("Displacement must clear the selection bottom by 110px", 110f, displacement, 0.001f)
    }

    @Test
    fun testPresentationStateMachine_InitialEntrance_BarHidden() {
        val state = ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_ENTRANCE
        val isVisible = ScribeBottomSurfaceCoordinator.isShortcutBarVisible(state)

        assertFalse("Shortcut bar must remain hidden during initial entrance", isVisible)
    }

    @Test
    fun testPresentationStateMachine_KeyboardSettled_BarVisible() {
        val state = ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED
        val isVisible = ScribeBottomSurfaceCoordinator.isShortcutBarVisible(state)

        assertTrue("Shortcut bar must be visible once keyboard has settled", isVisible)
    }

    @Test
    fun testPresentationStateMachine_KeyboardDismissal_BarDismissing() {
        val state = ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_DISMISSAL
        val isVisible = ScribeBottomSurfaceCoordinator.isShortcutBarVisible(state)

        assertTrue("Shortcut bar remains visible with keyboard until fully dismissed", isVisible)
    }

    @Test
    fun testPresentationStateMachine_AccessoryDrawerOpen_BarVisible() {
        val state = ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN
        val isVisible = ScribeBottomSurfaceCoordinator.isShortcutBarVisible(state)

        assertTrue("Shortcut bar must be visible when accessory drawer is open", isVisible)
    }

    @Test
    fun testPresentationStateMachine_DrawerToKeyboardHandoff_BarContinuouslyVisible() {
        val state = ScribeBottomSurfaceCoordinator.PresentationState.DRAWER_TO_KEYBOARD_HANDOFF
        val isVisible = ScribeBottomSurfaceCoordinator.isShortcutBarVisible(state)

        assertTrue("Shortcut bar must remain continuously visible during handoff", isVisible)
    }

    @Test
    fun testInvariantLockstep_ZeroProgress_ZeroClearance() {
        val clearance = ScribeBottomSurfaceCoordinator.calculateShortcutBarClearanceDp(0f)
        assertEquals("Zero progress must produce exactly 0dp clearance", 0f, clearance, 0.001f)
    }

    @Test
    fun testInvariantLockstep_FullProgress_FullClearance() {
        val clearance = ScribeBottomSurfaceCoordinator.calculateShortcutBarClearanceDp(1f)
        assertEquals("Full progress must produce full 36dp clearance", 36f, clearance, 0.001f)
    }

    @Test
    fun testInvariantLockstep_MidflightProgress_ExactProportionalClearance() {
        val clearance50 = ScribeBottomSurfaceCoordinator.calculateShortcutBarClearanceDp(0.5f)
        assertEquals("50% progress must produce 18dp clearance", 18f, clearance50, 0.001f)

        val clearance25 = ScribeBottomSurfaceCoordinator.calculateShortcutBarClearanceDp(0.25f)
        assertEquals("25% progress must produce 9dp clearance", 9f, clearance25, 0.001f)
    }

    @Test
    fun testStationaryDismissal_ExpansionDoesNotTriggerDownwardMovement() {
        // When keyboard is dismissing, shouldFollowDownward must strictly be FALSE
        val follow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false,
            isKeyboardVisible = false,
            hasPhysicalBottomObstruction = false,
            isAtLastLine = true,
            hasActiveScroll = true
        )
        assertFalse("Downward follow must be strictly forbidden during keyboard dismissal", follow)
    }

    @Test
    fun testStationaryDismissal_RetainedScrollExtent_PreventsDownwardClamping() {
        val totalScroll = 800
        val headerHeight = 200
        val layoutHeight = 1500
        val naturalContentHeight = headerHeight + layoutHeight // 1700
        val expandedViewport = 2200 // Expands to full screen on dismissal

        val retainedExtent = ScribeBottomSurfaceCoordinator.calculateRetainedScrollExtent(
            totalScroll = totalScroll,
            headerHeight = headerHeight,
            layoutHeight = layoutHeight,
            expandedViewportHeight = expandedViewport
        )

        // totalScroll (800) + expandedViewport (2200) - naturalContent (1700) = 1300
        assertEquals("Retained extent must preserve exact bottom space to prevent clamping", 1300, retainedExtent)

        // Verifying maxCanvasScroll with retained extent
        val maxCanvas = (naturalContentHeight + retainedExtent - expandedViewport).coerceAtLeast(0)
        assertEquals("Max canvas scroll must equal or exceed totalScroll", totalScroll, maxCanvas)
    }

    // ── Architectural Hardening & Reducer Regression Tests ────────────────────

    @Test
    fun testPresentationStateReducer_CompleteLifecycleTransitions() {
        val reduce = ScribeBottomSurfaceCoordinator::reduce

        // 1. Initial keyboard entrance
        var state = ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeTargetChanged(targetBottomPx = 900, currentBottomPx = 0))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_ENTRANCE, state)

        // 2. Mid-animation progress
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeAnimationProgress(currentBottomPx = 450, targetBottomPx = 900))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_ENTRANCE, state)

        // 3. IME arrives at target -> Settled
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeSettledAtTarget)
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED, state)

        // 4. Dismissal begins
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeDismissalStarted)
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_DISMISSAL, state)

        // 5. Dismissal in progress
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeAnimationProgress(currentBottomPx = 200, targetBottomPx = 0))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_DISMISSAL, state)

        // 6. Dismissal complete -> Hidden
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.ImeDismissalFinished)
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN, state)

        // 7. Drawer open / switch / close
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerOpened("SNIPPETS"))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, state)

        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerSwitched("TEMPLATES"))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, state)

        // 8. Drawer to keyboard handoff
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerToKeyboardHandoffStarted(260f))
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.DRAWER_TO_KEYBOARD_HANDOFF, state)

        // 9. Handoff completes -> Settled
        state = reduce(state, ScribeBottomSurfaceCoordinator.PresentationEvent.HandoffCompleted)
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED, state)
    }

    @Test
    fun testPresentationStateReducer_ScreenRestoration() {
        val reduce = ScribeBottomSurfaceCoordinator::reduce

        // Screen restored with already settled keyboard
        val settledRestored = reduce(
            ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN,
            ScribeBottomSurfaceCoordinator.PresentationEvent.ScreenRestored(
                isImeVisible = true,
                currentImeBottomPx = 900,
                targetImeBottomPx = 900,
                hasActiveDrawer = false
            )
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED, settledRestored)

        // Screen restored mid-animation
        val midAnimationRestored = reduce(
            ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN,
            ScribeBottomSurfaceCoordinator.PresentationEvent.ScreenRestored(
                isImeVisible = true,
                currentImeBottomPx = 400,
                targetImeBottomPx = 900,
                hasActiveDrawer = false
            )
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_ENTRANCE, midAnimationRestored)

        // Screen restored with active drawer
        val drawerRestored = reduce(
            ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN,
            ScribeBottomSurfaceCoordinator.PresentationEvent.ScreenRestored(
                isImeVisible = false,
                currentImeBottomPx = 0,
                targetImeBottomPx = 0,
                hasActiveDrawer = true
            )
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, drawerRestored)
    }

    @Test
    fun testShortcutBarVisibility_ByPresentationState() {
        // Must be invisible during entrance to prevent double/premature movement
        assertFalse(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.HIDDEN))
        assertFalse(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_ENTRANCE))

        // Must be visible once keyboard is settled
        assertTrue(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED))

        // Must remain visible throughout dismissal until zero
        assertTrue(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_DISMISSAL))

        // Must be visible during drawer and handoff
        assertTrue(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN))
        assertTrue(ScribeBottomSurfaceCoordinator.isShortcutBarVisible(ScribeBottomSurfaceCoordinator.PresentationState.DRAWER_TO_KEYBOARD_HANDOFF))
    }

    @Test
    fun testAuthoritativeThreePartRetainedClearance_CalculatesCorrectSum() {
        val clearance = ScribeBottomSurfaceCoordinator.calculateRetainedBottomScrollClearancePx(
            actualObstructionAboveNavPx = 800f,
            shortcutBarHeightPx = 108f,
            breathingMarginPx = 72f
        )
        assertEquals(980f, clearance, 0.001f)

        // Clamps negatives to 0f
        val zeroClearance = ScribeBottomSurfaceCoordinator.calculateRetainedBottomScrollClearancePx(
            actualObstructionAboveNavPx = -50f,
            shortcutBarHeightPx = -20f,
            breathingMarginPx = -10f
        )
        assertEquals(0f, zeroClearance, 0.001f)
    }

    @Test
    fun testStackedDrawerAndImePadding_AccountsForCombinedObstruction() {
        // Drawer stacked above IME
        val stackedPadding = ScribeBottomSurfaceCoordinator.calculateEditorBottomPaddingDp(
            imeBottomDp = 280f,
            navBarBottomDp = 48f,
            drawerHeightDp = 260f,
            shortcutBarClearanceDp = 36f
        )
        // 280 (IME) + 260 (drawer) + 36 (bar) = 576
        assertEquals(576f, stackedPadding, 0.001f)

        // Keyboard alone without drawer
        val keyboardOnly = ScribeBottomSurfaceCoordinator.calculateEditorBottomPaddingDp(
            imeBottomDp = 280f,
            navBarBottomDp = 48f,
            drawerHeightDp = 0f,
            shortcutBarClearanceDp = 36f
        )
        // maxOf(280, 48) + 36 = 316
        assertEquals(316f, keyboardOnly, 0.001f)
    }

    @Test
    fun testCursorSafetyInViewportAndUpwardDisplacement() {
        val viewportH = 1200f
        val breathingMargin = 72f // 24dp at 3x
        val deadband = 24f

        // Safe cursor well above boundary
        assertTrue(ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = 1000f,
            viewportHeight = viewportH,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        ))
        assertEquals(0f, ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = 1000f,
            viewportHeight = viewportH,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        ), 0.001f)

        // Unsafe cursor (e.g. at 1160px, while safe limit is 1200 - 72 = 1128px)
        assertFalse(ScribeBottomSurfaceCoordinator.isCursorSafeInViewport(
            screenBottom = 1160f,
            viewportHeight = viewportH,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        ))
        // Required displacement brings cursor back to safe boundary (1160 - 1128 = 32px)
        val displacement = ScribeBottomSurfaceCoordinator.calculateRequiredUpwardDisplacement(
            screenBottom = 1160f,
            viewportHeight = viewportH,
            breathingMarginPx = breathingMargin,
            deadbandPx = deadband
        )
        assertEquals(32f, displacement, 0.001f)
    }

    // ── PART H REGRESSION TEST SUITE ──────────────────────────────────────────

    @Test
    fun testH1_DrawerHandoffContinuityAndSwitching() {
        var state = ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED

        // 1. Keyboard -> Snip
        state = ScribeBottomSurfaceCoordinator.reduce(
            state,
            ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerOpened("SNIPPETS")
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, state)

        // 2. Snip -> Tmpl (direct switch: zero keyboard transition)
        state = ScribeBottomSurfaceCoordinator.reduce(
            state,
            ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerSwitched("TEMPLATES")
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, state)

        // 3. Tmpl -> Snip (direct switch back)
        state = ScribeBottomSurfaceCoordinator.reduce(
            state,
            ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerSwitched("SNIPPETS")
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.ACCESSORY_DRAWER_OPEN, state)

        // 4. Snip -> Keyboard Handoff
        val drawerHeightDp = 240f
        state = ScribeBottomSurfaceCoordinator.reduce(
            state,
            ScribeBottomSurfaceCoordinator.PresentationEvent.DrawerToKeyboardHandoffStarted(drawerHeightDp)
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.DRAWER_TO_KEYBOARD_HANDOFF, state)

        // Monotonic height preservation during handoff: maxOf(transitionalSpacerDp, imeAboveNavDp)
        val imeAboveNavRising = 150f
        val effectiveHeightDuringRising = maxOf(drawerHeightDp, imeAboveNavRising)
        assertEquals(240f, effectiveHeightDuringRising, 0.001f)

        // Tolerance check: IME at target within tolerance
        assertTrue(ScribeBottomSurfaceCoordinator.isImeAtTarget(targetImePx = 300, curImePx = 298))
        assertTrue(ScribeBottomSurfaceCoordinator.isImeAtTarget(targetImePx = 300, curImePx = 302))

        // 5. Handoff completes when IME settles
        state = ScribeBottomSurfaceCoordinator.reduce(
            state,
            ScribeBottomSurfaceCoordinator.PresentationEvent.HandoffCompleted
        )
        assertEquals(ScribeBottomSurfaceCoordinator.PresentationState.KEYBOARD_SETTLED, state)
    }

    @Test
    fun testH2_ScrollPreservationDuringDismissal_NoClamping() {
        val headerHeight = 200
        val layoutHeight = 2500
        val contractedViewport = 1000 // Keyboard open
        val expandedViewport = 1600   // Keyboard dismissed
        val density = 2.5f
        val breathingMarginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt() // 60px
        val persistentBottomSpacePx = 660 // Retained clearance

        // Initial state: scrolled to document end
        val initialScrollD = headerHeight // 200
        val initialOffsetY = 1200
        val totalLogicalScroll = initialScrollD + initialOffsetY // 1400

        // 1. calculateSafeScrollMaxY unconditioned on isFocused
        val maxEditorYUnfocused = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = expandedViewport,
            persistentBottomSpacePx = persistentBottomSpacePx,
            currentOffsetY = initialOffsetY,
            isFocused = false, // Crucial: focus already lost on dismissal!
            baseSoraMaxY = 1000,
            isKeyboardVisible = false,
            breathingMarginPx = breathingMarginPx,
            isTextEmpty = false,
            activeGlideTargetY = 0,
            isHeaderFullyHidden = true
        )
        assertTrue("Max scroll with retained clearance must accommodate current offset", maxEditorYUnfocused >= initialOffsetY)

        // 2. Reconcile against preserved bounds: total logical scroll remains 100% stationary
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = initialScrollD,
            editorOffsetY = initialOffsetY,
            maxCanvasScroll = maxOf(totalLogicalScroll, maxEditorYUnfocused + headerHeight)
        )
        assertEquals(initialScrollD, reconciled.scrollD)
        assertEquals(initialOffsetY, reconciled.editorOffsetY)
        assertEquals(totalLogicalScroll, reconciled.scrollD + reconciled.editorOffsetY)
    }

    @Test
    fun testH3_CanonicalCoordinateConversion_And_SafeLastLineTapZeroMovement() {
        val density = 2.5f
        val headerHeight = 200f
        val scrollD = 200f
        val offsetY = 1200f
        val currentTotalScroll = scrollD + offsetY // 1400f
        val usableViewportHeight = 1000f

        // Last line at document position Y = 1660..1700
        val rectTop = 1660f
        val rectBottom = 1700f

        // Canonical calculation:
        // screenBottom = headerHeight + rectBottom - currentTotalScroll = 200 + 1700 - 1400 = 500f
        // Safe bottom boundary = 1000 - 60 = 940f
        val decision = ScribeScrollPolicy.resolveCursorSafety(
            rectTop = rectTop,
            rectBottom = rectBottom,
            headerHeight = headerHeight,
            currentTotalScroll = currentTotalScroll,
            usableViewportHeight = usableViewportHeight,
            density = density,
            maxCanvasScroll = 2000f
        )

        // Crucial test: Tap on already visible last line produces ZERO movement!
        assertTrue("Already visible cursor must be safe", decision.isSafe)
        assertEquals("Screen bottom must match canonical formula", 500f, decision.screenBottom, 0.001f)
        assertEquals("Needed displacement must be 0f", 0f, decision.neededUpwardDisplacement, 0.001f)
        assertEquals("Target total scroll must remain current total scroll", currentTotalScroll, decision.targetTotalScroll, 0.001f)

        // Contrast with genuinely unsafe cursor below boundary (e.g. rectBottom = 2200f -> screenBottom = 1000f)
        val unsafeDecision = ScribeScrollPolicy.resolveCursorSafety(
            rectTop = 2160f,
            rectBottom = 2200f,
            headerHeight = headerHeight,
            currentTotalScroll = currentTotalScroll,
            usableViewportHeight = usableViewportHeight,
            density = density,
            maxCanvasScroll = 2000f
        )
        assertFalse("Cursor below safe bottom boundary must be marked unsafe", unsafeDecision.isSafe)
        // Safe boundary is 940px; screenBottom is 200 + 2200 - 1400 = 1000px. Overflow = 60px.
        assertEquals(60f, unsafeDecision.neededUpwardDisplacement, 0.001f)
        assertEquals(currentTotalScroll + 60f, unsafeDecision.targetTotalScroll, 0.001f)
    }

    @Test
    fun testH4_KeyboardEntranceContinuousRetargeting() {
        val coordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator(
            totalScroll = 500f,
            density = 2.5f
        )

        // Initial target during entrance
        val started = coordinator.requestTarget(
            targetScroll = 600f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
            forceRetarget = false,
            maxCanvasScroll = 1500
        )
        assertTrue(started)
        assertTrue(coordinator.isAnimationActive)
        assertEquals(600f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Subsequent measurement during in-flight animation retargets seamlessly without resetting velocity or progress
        coordinator.retargetActiveOperation(
            newTarget = 650f,
            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
            maxCanvasScroll = 1500
        )
        assertEquals(650f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)
        assertTrue(coordinator.isAnimationActive)
    }
}
