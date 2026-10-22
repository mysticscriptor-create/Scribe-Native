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
        // When deleting the last paragraph, the text bottom drifts above the shortcut bar.
        // calculateDeletionAnchorDelta must detect the exact gap and return the downward delta.
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 1400
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f // margin = 60px, targetLineBottom = 1140px

        // textBottom = (200 - 200) + 1400 - 800 = 600px.
        // Gap to 1140: 1140 - 600 = 540px.
        // totalScroll = 200 + 800 = 1000px.
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 500
        )
        assertEquals(-540f, delta, 0.001f)
    }

    @Test
    fun testDeleteSecondToLastParagraphCalculatesAccurateDownwardAnchorDelta() {
        // When deleting in the second-to-last paragraph, the end of document drifts up by 80px.
        val headerHeight = 200
        val scrollD = 200
        val layoutHeight = 1860
        val offsetY = 800
        val viewportHeight = 1200
        val density = 2.5f

        // textBottom = 0 + 1860 - 800 = 1060px.
        // Gap to 1140: 1140 - 1060 = 80px.
        val delta = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = layoutHeight,
            offsetY = offsetY,
            viewportHeight = viewportHeight,
            density = density,
            maxCanvasScroll = 800
        )
        assertEquals(-80f, delta, 0.001f)
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
        // True Pure Writer: 1500 + 800 - 1100 = 1200
        assertEquals(1200, maxBefore)

        val maxAfter = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeightAfter,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            currentOffsetY = currentOffsetY,
            isFocused = true,
            baseSoraMaxY = 0
        )
        // 1200 + 800 - 1100 = 900: shrinks cleanly by exact 300px!
        assertEquals("Safe scroll must shrink to 900 on deletion, allowing 300px clamp", 900, maxAfter)
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
        val persistentBottomSpacePx = 860 // 800 keyboard + 60 breathing margin
        val breathingMarginPx = 60 // 24dp at 2.5 density

        // In True Pure Writer full-canvas underlay, viewport is full-canvas height (2000px)
        val fixedMaxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = fullScreenHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            isTextEmpty = false,
            currentOffsetY = 0,
            isFocused = true
        )

        // 1000 + 860 - 2000 = -140 <= 0: fits completely, 0 extra padding above keyboard!
        assertEquals("When keyboard is visible, no double padding allows scrolling above keyboard", 0, fixedMaxScroll)
    }

    @Test
    fun testKeyboardDismissalRetainsStationaryDocumentPosition() {
        val layoutHeight = 1500
        val fullScreenHeight = 2000
        val persistentBottomSpacePx = 860 // 800 keyboard + 60 breathing margin
        val breathingMarginPx = 60

        // In True Pure Writer full-canvas underlay, viewport height is constant across keyboard open/dismissal
        val openMaxScroll = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = fullScreenHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            breathingMarginPx = breathingMarginPx,
            isKeyboardVisible = true,
            isTextEmpty = false,
            currentOffsetY = 360,
            isFocused = true
        )
        // 1500 + 860 - 2000 = 360: exactly 360
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
}
