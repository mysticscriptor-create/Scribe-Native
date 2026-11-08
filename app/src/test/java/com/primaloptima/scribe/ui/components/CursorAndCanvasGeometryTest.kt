package com.primaloptima.scribe.ui.components

import com.primaloptima.scribe.ui.editor.scroll.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating caret & selection geometry and 1-Degree-of-Freedom
 * canvas/editor movement allocation invariants.
 */
class CursorAndCanvasGeometryTest {
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
    fun testContraction_VerifiedCoordinateOrdering_SelectionBounds() {
        // Multi-line selection spanning lines 10 to 15.
        // Start of selection is at line 10, end of selection is at line 15.
        // Bottom-most coordinate is at line 15, NOT line 10!
        val geom = SelectionGeometry(
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

        val coordinator = ScribeScrollCoordinator(
            totalScroll = startScroll,
            caretLine = endLine,
            caretCol = 10,
            isSelected = true
        )
        coordinator.isExpansionTransitionActive = true

        // Move upward to clear shortcut bar
        coordinator.requestTarget(requiredTotalScroll, ScrollAnimationReason.CARET_VISIBILITY)
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
        val restorationResult = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
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
            reason = ScrollAnimationReason.CONTRACTION_RESTORATION
        )
        coordinator.applyStep(restorationTarget)
        coordinator.finishAnimation()

        assertEquals(3000f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement returned to 0f", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals("Selection tracking reset", -1, coordinator.caretLine)
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
    fun testCanScrollVertically() {
        val maxScroll = 500

        // Positive direction (scrolling down into content)
        // scrollD + offsetY < maxCanvasScroll
        assertEquals(true, ScribeScrollPolicy.canScrollVertically(direction = 1, scrollD = 100, offsetY = 200, maxCanvasScroll = maxScroll))
        assertEquals(false, ScribeScrollPolicy.canScrollVertically(direction = 1, scrollD = 200, offsetY = 300, maxCanvasScroll = maxScroll))
        assertEquals(false, ScribeScrollPolicy.canScrollVertically(direction = 1, scrollD = 300, offsetY = 300, maxCanvasScroll = maxScroll))

        // Negative direction (scrolling up towards header)
        // scrollD > 0 || offsetY > 0
        assertEquals(true, ScribeScrollPolicy.canScrollVertically(direction = -1, scrollD = 50, offsetY = 0, maxCanvasScroll = maxScroll))
        assertEquals(true, ScribeScrollPolicy.canScrollVertically(direction = -1, scrollD = 0, offsetY = 50, maxCanvasScroll = maxScroll))
        assertEquals(true, ScribeScrollPolicy.canScrollVertically(direction = -1, scrollD = 50, offsetY = 50, maxCanvasScroll = maxScroll))
        assertEquals(false, ScribeScrollPolicy.canScrollVertically(direction = -1, scrollD = 0, offsetY = 0, maxCanvasScroll = maxScroll))

        // Zero direction -> returns null for superclass fallback at adapter boundary
        assertNull(ScribeScrollPolicy.canScrollVertically(direction = 0, scrollD = 100, offsetY = 100, maxCanvasScroll = maxScroll))
    }

    @Test
    fun testShouldForwardScrollToCanvas() {
        val headerH = 100

        // 1. Header still visible (scrollD < headerHeight) -> always forward to canvas
        assertTrue(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 50, headerHeight = headerH, offsetY = 0, deltaY = 10))
        assertTrue(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 50, headerHeight = headerH, offsetY = 0, deltaY = -10))
        assertTrue(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 0, headerHeight = headerH, offsetY = 0, deltaY = 10))

        // 2. Header hidden with downward movement and editor at top -> forward to reveal header
        assertTrue(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 100, headerHeight = headerH, offsetY = 0, deltaY = -10))

        // 3. Header hidden while editor has positive offset -> editor consumes downward movement
        assertFalse(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 100, headerHeight = headerH, offsetY = 50, deltaY = -10))

        // 4. Header hidden with upward (forward) movement -> editor consumes forward movement
        assertFalse(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 100, headerHeight = headerH, offsetY = 0, deltaY = 10))
        assertFalse(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 100, headerHeight = headerH, offsetY = 50, deltaY = 10))

        // 5. Zero vertical delta (horizontal-only movement)
        assertFalse(ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD = 100, headerHeight = headerH, offsetY = 0, deltaY = 0))
    }

}
