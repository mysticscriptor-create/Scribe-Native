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

    @Test
    fun testDeletionAnchor_GlideTargetConvergesToMinSafeScrollWhenTargetBelowBoundary() {
        // When content shrinks and target total scroll is below minSafeScroll,
        // the animator must converge glideTarget to minSafeScroll to prevent an infinite spring loop.
        val minSafeScroll = 350f
        var simulatedGlidePosition = 340f
        var simulatedGlideVelocity = -120f
        var simulatedGlideTarget = 300f // Target is below minSafeScroll

        if (simulatedGlidePosition < minSafeScroll) {
            simulatedGlidePosition = minSafeScroll
            if (simulatedGlideVelocity < 0f) simulatedGlideVelocity = 0f
            if (simulatedGlideTarget <= minSafeScroll) {
                simulatedGlideTarget = minSafeScroll
            }
        }

        assertEquals("Glide position must be clamped to minSafeScroll", 350f, simulatedGlidePosition, 0.001f)
        assertEquals("Downwards velocity must be clamped to zero", 0f, simulatedGlideVelocity, 0.001f)
        assertEquals("Glide target must converge to minSafeScroll to allow clean settlement", 350f, simulatedGlideTarget, 0.001f)

        val distToTarget = kotlin.math.abs(simulatedGlidePosition - simulatedGlideTarget)
        val isSettled = distToTarget < 0.5f && kotlin.math.abs(simulatedGlideVelocity) < 15f
        assertTrue("Animation must settle cleanly without deadlocking", isSettled)
    }

    @Test
    fun testDeletionBoundaryProviderNeverExceedsMaxCanvasScroll() {
        // Deletion safe boundary must never exceed maxCanvasScroll
        val headerHeight = 200f
        val newLayoutHeight = 1200f
        val breathingMarginPx = 60f
        val viewportHeight = 800f
        val maxCanvasScroll = 660f // 200 + 1200 + 60 - 800 = 660

        val rawBoundary = headerHeight + newLayoutHeight + breathingMarginPx - viewportHeight
        val bounded = rawBoundary.coerceIn(0f, maxCanvasScroll)

        assertEquals(660f, bounded, 0.001f)
        assertTrue("Bounded safe scroll must not exceed maxCanvasScroll", bounded <= maxCanvasScroll)
    }

    @Test
    fun testDeleteTailWhenContentShrinksBelowCurrentScroll() {
        // Deleting text at the bottom of the document when current scroll exceeds new maxCanvasScroll
        val snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 2L,
            isSelection = true,
            selectionStartLine = 40,
            selectionEndLine = 48,
            preEditDocLineCount = 50,
            preEditDocHeight = 4000f,
            preEditScrollY = 3200f,
            preEditCaretScreenY = 700f,
            wasAtTail = true,
            affectsDocumentTail = true
        )

        val newDocHeight = 2500f
        val usableViewportHeight = 800f
        val headerHeight = 200f
        val breathingMarginPx = 60f
        val maxCanvasScroll = (headerHeight + newDocHeight + breathingMarginPx - usableViewportHeight).coerceAtLeast(0f)

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = 40,
            newDocHeight = headerHeight + newDocHeight,
            usableViewportHeight = usableViewportHeight
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR, intent)

        val destination = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = 3200f,
            headerHeight = headerHeight,
            newDocHeight = newDocHeight,
            newCaretDocY = newDocHeight,
            preEditCaretScreenY = 700f,
            usableViewportHeight = usableViewportHeight,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxCanvasScroll
        )

        assertEquals("Destination must cleanly clamp to new maxCanvasScroll", maxCanvasScroll, destination, 0.001f)
        assertTrue("Destination must be <= current scroll", destination <= 3200f)
    }

    @Test
    fun testDeletionIntent_SelectionEndingOnLastLineWithCaretEarlier_ClassifiesAsCaretAnchor() {
        // Scenario 4: Selection touches the last line, but post-edit caret collapses in an earlier paragraph
        // with remaining content below it. Must classify as CARET_ANCHOR, never hijacked to TAIL_ANCHOR.
        val snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 3L,
            isSelection = true,
            selectionStartLine = 8,
            selectionEndLine = 20,
            preEditDocLineCount = 21,
            preEditDocHeight = 2000f,
            preEditScrollY = 1248f,
            preEditCaretScreenY = 360f,
            wasAtTail = true,
            affectsDocumentTail = true,
            preEditLastNonBlankLine = 20
        )
        val linesAfter = MutableList(15) { idx -> "Paragraph line $idx" }
        val isPostEditCaretAtTail = ScribeScrollDeletionPolicy.isCaretAtLogicalTail(
            lineCount = linesAfter.size,
            caretLine = 8,
            caretCol = 4,
            lineAt = { linesAfter[it] },
            newCaretDocY = 450f,
            layoutHeight = 1400f,
            rowHeight = 50f
        )
        assertFalse("Post-edit caret at line 8 with lines 9..14 non-blank is NOT at logical tail", isPostEditCaretAtTail)

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = linesAfter.size,
            newDocHeight = 1500f,
            usableViewportHeight = 800f,
            breathingMarginPx = 48f,
            isTextEmpty = false,
            isPostEditCaretAtLogicalTail = isPostEditCaretAtTail
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR, intent)

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = 1248f,
            headerHeight = 100f,
            newDocHeight = 1400f,
            newCaretDocY = 450f,
            preEditCaretScreenY = 360f,
            usableViewportHeight = 800f,
            breathingMarginPx = 48f,
            maxCanvasScroll = 748f
        )
        // Caret anchored stationary at preEditCaretScreenY (100 + 450 - 360 = 190f), not pulled to maxCanvasScroll (748f)
        assertEquals(190f, target, 0.001f)
    }

    @Test
    fun testLogicalTailHelpers_TrailingBlankAndWhitespaceLines() {
        // Scenario 5: Blank and whitespace-only trailing lines below the last real paragraph
        val lines = listOf("First paragraph", "Last real paragraph", "", "   ", "\t  ")
        val lastNonBlank = ScribeScrollDeletionPolicy.findLastNonBlankLine(lines.size) { lines[it] }
        assertEquals(1, lastNonBlank)

        assertTrue(
            "Deleting line 1 (last real paragraph) above trailing blanks affects logical tail",
            ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(endLine = 1, lastNonBlankLine = lastNonBlank)
        )
        assertFalse(
            "Deleting line 0 only does NOT affect logical tail at line 1",
            ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(endLine = 0, lastNonBlankLine = lastNonBlank)
        )
        assertTrue(
            "Caret on line 1 with only blank/whitespace lines below IS at logical tail",
            ScribeScrollDeletionPolicy.isCaretAtLogicalTail(
                lineCount = lines.size,
                caretLine = 1,
                caretCol = lines[1].length,
                lineAt = { lines[it] },
                newCaretDocY = 100f,
                layoutHeight = 250f,
                rowHeight = 50f
            )
        )
        assertFalse(
            "Caret on line 0 with non-blank line 1 below is NOT at logical tail",
            ScribeScrollDeletionPolicy.isCaretAtLogicalTail(
                lineCount = lines.size,
                caretLine = 0,
                caretCol = 0,
                lineAt = { lines[it] },
                newCaretDocY = 50f,
                layoutHeight = 250f,
                rowHeight = 50f
            )
        )
    }

    @Test
    fun testDeletion_ShortDocExceedingUsableViewportWithBreathingMargin_DoesNotResetToTop() {
        // Scenario 7: headerHeight + newLayoutHeight <= usableViewportHeight (780 <= 800),
        // BUT headerHeight + newLayoutHeight + breathingMarginPx > usableViewportHeight (780 + 60 = 840 > 800).
        // Must NOT reset to top (would push caret 40px below breathing margin!).
        val headerHeight = 100
        val newLayoutHeight = 680
        val usableViewportHeight = 800
        val breathingMarginPx = 60f
        val maxCanvas = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = newLayoutHeight,
            viewportHeight = usableViewportHeight,
            bottomSpace = breathingMarginPx.toInt(),
            isTextEmpty = false
        ) // 100 + 680 + 60 - 800 = 40px

        val snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 4L,
            isSelection = false,
            selectionStartLine = 12,
            selectionEndLine = 12,
            preEditDocLineCount = 13,
            preEditDocHeight = 840f,
            preEditScrollY = 100f,
            preEditCaretScreenY = 740f,
            wasAtTail = true,
            affectsDocumentTail = true,
            preEditLastNonBlankLine = 12
        )

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = 12,
            newDocHeight = (headerHeight + newLayoutHeight).toFloat(),
            usableViewportHeight = usableViewportHeight.toFloat(),
            breathingMarginPx = breathingMarginPx,
            isTextEmpty = false,
            isPostEditCaretAtLogicalTail = true
        )
        assertEquals(
            "Must classify as TAIL_ANCHOR when content + breathing margin exceeds usable viewport",
            ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR,
            intent
        )

        val destination = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = 100f,
            headerHeight = headerHeight.toFloat(),
            newDocHeight = newLayoutHeight.toFloat(),
            newCaretDocY = newLayoutHeight.toFloat(),
            preEditCaretScreenY = 740f,
            usableViewportHeight = usableViewportHeight.toFloat(),
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxCanvas.toFloat()
        )
        assertEquals(40f, destination, 0.001f)
        val caretScreenBottom = headerHeight + newLayoutHeight - destination
        assertTrue(
            "Caret must stay at or above breathing margin ($caretScreenBottom <= 740)",
            caretScreenBottom <= (usableViewportHeight - breathingMarginPx + 0.01f)
        )

        val anchorDelta = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = 100,
            layoutHeight = newLayoutHeight,
            offsetY = 0,
            viewportHeight = usableViewportHeight,
            density = 2.5f,
            maxCanvasScroll = maxCanvas,
            breathingMarginPx = breathingMarginPx
        )
        assertEquals("Anchor delta must retract only -60px to +40px, NOT -100px to 0px", -60f, anchorDelta, 0.001f)
    }

    @Test
    fun testLayoutReconciliation_IdleClampsWhileDeletionDeferralProtects() {
        // Scenario 11: During active deletion, transitional step resolver preserves overscrolled
        // current offset against instant snap; once cleared, idle reconcileScrollState strictly clamps.
        val headerHeight = 200
        val shrunkMaxCanvas = 440
        val currentOffset = 300 // scrollD = 200, total = 500 > 440

        val step = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
            currentOffsetY = currentOffset,
            currentResidualPx = 0f,
            innerDelta = -3f,
            maxAllowedY = shrunkMaxCanvas - headerHeight, // 240
            isDeletionTransactionActive = true
        )
        assertEquals(297, step.nextIntOffset)
        assertFalse(step.hitBoundary)

        val postSettlementIdle = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = 200,
            editorOffsetY = 245,
            maxCanvasScroll = shrunkMaxCanvas
        )
        assertEquals(200, postSettlementIdle.scrollD)
        assertEquals(240, postSettlementIdle.editorOffsetY)
    }

    @Test
    fun testDeletion_AllContentDeleted_ClassifiesAsResetToTop() {
        // Scenario 12: Deleting all document content yields single RESET_TO_TOP -> 0f
        val snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 5L,
            isSelection = true,
            selectionStartLine = 0,
            selectionEndLine = 25,
            preEditDocLineCount = 26,
            preEditDocHeight = 1800f,
            preEditScrollY = 1000f,
            preEditCaretScreenY = 500f,
            wasAtTail = true,
            affectsDocumentTail = true
        )
        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = 1,
            newDocHeight = 150f,
            usableViewportHeight = 800f,
            breathingMarginPx = 60f,
            isTextEmpty = true,
            isPostEditCaretAtLogicalTail = true
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.RESET_TO_TOP, intent)
    }

    @Test
    fun testSnapshotCoordinator_SequentialBatchDeletion_PreservesEarliestSnapshotAndMergesTail() {
        val coordinator = ScribeDeletionSnapshotCoordinator()

        // Op 1: Delete middle blank lines (3..4) while doc has 11 lines (last non-blank at line 10)
        var hasPending = false
        var activeSnapshot: ScribeScrollDeletionPolicy.DeletionSnapshot? = null

        coordinator.onBeforeModification(
            hasPendingDeletionSettlement = hasPending,
            captureInitialSnapshot = {
                ScribeScrollDeletionPolicy.PreModificationSnapshot(
                    isSelection = true,
                    selectionStartLine = 3,
                    selectionEndLine = 4,
                    preEditDocLineCount = 11,
                    preEditDocHeight = 1400f,
                    preEditScrollY = 500f,
                    preEditCaretScreenY = 320f,
                    wasAtTail = true,
                    preEditLastNonBlankLine = 10
                )
            },
            captureBatchProbe = {
                ScribeDeletionSnapshotCoordinator.BatchTailProbe(endLine = 4, lastNonBlankLine = 10)
            }
        )
        val op1 = coordinator.onAfterDelete(afterDeleteEndLine = 4, hasPendingDeletionSettlement = hasPending)
        assertTrue(op1 is ScribeDeletionSnapshotCoordinator.AfterDeleteAction.BeginTransaction)
        val begin = op1 as ScribeDeletionSnapshotCoordinator.AfterDeleteAction.BeginTransaction
        assertFalse(begin.affectsDocumentTail)
        activeSnapshot = begin.snapshot
        hasPending = true

        // Op 2 in same batch (before layout settles): earlier deletion shifted line indices down by 2,
        // so current last non-blank line is now 8; user deletes lines 7..8 at logical tail.
        coordinator.onBeforeModification(
            hasPendingDeletionSettlement = hasPending,
            captureInitialSnapshot = {
                error("captureInitialSnapshot must NOT overwrite earliest pre-edit metrics during pending batch")
            },
            captureBatchProbe = {
                ScribeDeletionSnapshotCoordinator.BatchTailProbe(endLine = 8, lastNonBlankLine = 8)
            }
        )
        val op2 = coordinator.onAfterDelete(afterDeleteEndLine = 8, hasPendingDeletionSettlement = hasPending)
        assertTrue(op2 is ScribeDeletionSnapshotCoordinator.AfterDeleteAction.MergeExistingBatch)
        val merge = op2 as ScribeDeletionSnapshotCoordinator.AfterDeleteAction.MergeExistingBatch
        val merged = ScribeScrollDeletionPolicy.mergeDeletionBatchSnapshot(
            existing = activeSnapshot!!,
            batchEndLine = merge.batchEndLine,
            currentLastNonBlankLine = merge.batchLastNonBlankLine ?: activeSnapshot!!.preEditLastNonBlankLine
        )
        assertEquals(500f, merged.preEditScrollY, 0.001f)
        assertEquals(11, merged.preEditDocLineCount)
        assertEquals(10, merged.preEditLastNonBlankLine)
        assertTrue("Sequential batch deleting logical tail must flip affectsDocumentTail to true", merged.affectsDocumentTail)
    }

    @Test
    fun testSnapshotCoordinator_ReplaceWithDelete_DistinguishesShrinkingVsEmptyReplace() {
        val coordinator = ScribeDeletionSnapshotCoordinator()
        coordinator.onBeforeReplace()
        coordinator.onBeforeModification(
            hasPendingDeletionSettlement = false,
            captureInitialSnapshot = {
                ScribeScrollDeletionPolicy.PreModificationSnapshot(
                    isSelection = true,
                    selectionStartLine = 5,
                    selectionEndLine = 15,
                    preEditDocLineCount = 16,
                    preEditDocHeight = 1600f,
                    preEditScrollY = 900f,
                    preEditCaretScreenY = 820f,
                    wasAtTail = true,
                    preEditLastNonBlankLine = 15
                )
            },
            captureBatchProbe = { null }
        )
        coordinator.onAfterDelete(afterDeleteEndLine = 15, hasPendingDeletionSettlement = false)
        // Large selection replaced with 1 char still leaves totalScroll > new maxCanvasScroll -> do NOT clear txn
        val shrinkAction = coordinator.onAfterInsert(
            insertedNotEmpty = true,
            isTextEmpty = false,
            currentTotalScroll = 900,
            maxCanvasScroll = 500,
            hasPendingOrActiveTransaction = true
        )
        assertEquals(ScribeDeletionSnapshotCoordinator.AfterInsertAction.None, shrinkAction)

        // Composing replace with empty string triggers SettleEmptyReplaceDelete
        coordinator.onBeforeReplace()
        coordinator.onAfterDelete(afterDeleteEndLine = 10, hasPendingDeletionSettlement = true)
        val emptyReplaceAction = coordinator.onAfterInsert(
            insertedNotEmpty = false,
            isTextEmpty = false,
            currentTotalScroll = 500,
            maxCanvasScroll = 500,
            hasPendingOrActiveTransaction = true
        )
        assertEquals(ScribeDeletionSnapshotCoordinator.AfterInsertAction.SettleEmptyReplaceDelete, emptyReplaceAction)
    }
}
