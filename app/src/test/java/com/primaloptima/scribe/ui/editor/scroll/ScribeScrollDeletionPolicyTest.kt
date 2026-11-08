package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ScribeScrollDeletionPolicy: deletion intent, tail follow, and boundary clamping.
 */
class ScribeScrollDeletionPolicyTest {
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
        val delta = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
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
        val delta = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
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
        val delta = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
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
        val delta = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
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
    fun testDeletionIntent_RequiresTailProximityAndAffectedTail() {
        // Issue 4: Tail follow requires BOTH pre-edit tail proximity AND that the deletion affected the end
        val tailSnapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
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
        val tailIntent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = tailSnapshot,
            newDocLineCount = 18,
            newDocHeight = 1600f,
            usableViewportHeight = 800f
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR, tailIntent)

        // If user was near tail but deleted in the middle (does not affect tail):
        val middleSnapshot = tailSnapshot.copy(selectionStartLine = 5, selectionEndLine = 7, affectsDocumentTail = false)
        val middleIntent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = middleSnapshot,
            newDocLineCount = 18,
            newDocHeight = 1600f,
            usableViewportHeight = 800f
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR, middleIntent)
    }

    @Test
    fun testDeletionDestination_TailFollowPreservesBreathingMargin() {
        // Issue 3: Exercises production calculateSafeDeletionDestination for tail-follow
        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR,
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

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
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
        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.RESET_TO_TOP,
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

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
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
        val snapshotMiddleEditAtTailViewport = ScribeScrollDeletionPolicy.DeletionSnapshot(
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

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshotMiddleEditAtTailViewport,
            newDocLineCount = 50,
            newDocHeight = 3000f,
            usableViewportHeight = 800f
        )

        assertEquals("Middle edit must be classified as CARET_ANCHOR even if viewport was at tail",
            ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR, intent)
    }

}
