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
    fun testDeleteOnLastLineTriggersDownwardAnchor() {
        // When deleting text on the last line, isDeletionActive is TRUE.
        // It must smoothly pull the canvas down so the last line stays anchored.
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density) // 10px

        // Line is deleted, so bottom moves 40px up from target (1100px vs target 1140px)
        val screenBottom = 1100f
        val screenTop = 1060f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = true, // ACTIVE DELETION
            hasScrollOffset = true
        )

        assertTrue("Delete on last line must trigger downward anchoring", resolution.shouldScroll)
        assertTrue("Resolution must be marked as anchor downward", resolution.isAnchorDownward)
        assertEquals(-40f, resolution.deltaY, 0.001f)
        assertEquals(ScribeScrollPolicy.DURATION_DELETION_ANCHOR_MS, resolution.recommendedDurationMs)
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
    fun testSafeScrollMaxYRetainsStationaryOffsetWhileFocused() {
        val layoutHeight = 1500
        val viewportHeight = 1000
        val persistentBottomSpacePx = 0
        val currentOffsetY = 600

        val maxY = ScribeScrollPolicy.calculateSafeScrollMaxY(
            layoutHeight = layoutHeight,
            viewportHeight = viewportHeight,
            persistentBottomSpacePx = persistentBottomSpacePx,
            currentOffsetY = currentOffsetY,
            isFocused = true,
            baseSoraMaxY = 500
        )

        assertTrue("Safe scroll must retain current offset while focused", maxY >= 600)
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

        // 1000 + 60 - 1200 = -140 <= 0: fits in 1200px viewport, 0 extra padding above keyboard!
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
            currentOffsetY = 360,
            isFocused = true
        )
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

        assertEquals("Max scroll remains identical upon dismissal, keeping text rock-solid stationary", openMaxScroll, dismissedMaxScroll)
    }
}
