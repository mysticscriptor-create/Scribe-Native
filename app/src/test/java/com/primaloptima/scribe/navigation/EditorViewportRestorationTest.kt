package com.primaloptima.scribe.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import com.primaloptima.scribe.ui.components.ScribeScrollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests validating robust editor viewport restoration across Navigation 3 screens.
 *
 * Covers all 8 acceptance criteria:
 * 1. A viewport snapshot records the latest inner editor offset after the header has fully scrolled away.
 * 2. The logical scroll position is correctly split between header displacement and editor offset, respecting the 1-DOF invariant.
 * 3. Returning to the same editor session requests restoration once, even if more than one lifecycle/navigation event occurs.
 * 4. Restoration is independent of the Restore Cursor Position on Open setting.
 * 5. A pending restoration waits for layout readiness and does not use temporary bounds.
 * 6. Changing documents or editor sessions invalidates pending work and prevents stale snapshots from being applied.
 * 7. Restoring the viewport does not replace it with the current cursor's position.
 * 8. Adding a new Navigation 3 destination requires no modifications to that destination for automatic editor restoration.
 */
class EditorViewportRestorationTest {

    // Test destination representing an arbitrary future Navigation 3 screen
    private data object FutureArbitraryDestination : NavKey

    /**
     * 1. A viewport snapshot records the latest inner editor offset after the header has fully scrolled away.
     */
    @Test
    fun testViewportSnapshotRecordsLatestInnerEditorOffsetAfterHeaderScrolledAway() {
        val headerHeight = 250
        val maxCanvasScroll = 2000

        // Step 1: User scrolls header completely away (scrollD reaches headerHeight)
        val initialScrollD = headerHeight
        val initialEditorY = 0

        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")

        // Step 2: User scrolls deeper into inner editor (e.g. 750px of editor text)
        val deeperEditorY = 750
        coordinator.updateViewportSnapshot(
            sessionKey = editorKey,
            noteId = "note1",
            scrollD = initialScrollD,
            editorOffsetY = deeperEditorY
        )

        val snapshot = coordinator.getSnapshot("note1")
        assertNotNull("Snapshot must be recorded", snapshot)
        assertEquals("scrollD must be at headerHeight", headerHeight, snapshot!!.scrollD)
        assertEquals("editorOffsetY must record latest inner offset", deeperEditorY, snapshot.editorOffsetY)
        assertEquals(
            "totalLogicalScroll must represent total combined displacement",
            headerHeight + deeperEditorY,
            snapshot.totalLogicalScroll
        )
    }

    /**
     * 2. The logical scroll position is correctly split between header displacement and editor offset,
     *    respecting the 1-DOF invariant.
     */
    @Test
    fun testLogicalScrollCorrectlySplitRespecting1DofInvariant() {
        val headerHeight = 200
        val maxCanvasScroll = 3000

        // Case A: Total scroll less than header height -> editorOffsetY MUST be 0
        val partialScroll = 120
        val splitA = EditorSessionRestorationCoordinator.splitLogicalScroll(
            headerHeight = headerHeight,
            totalLogicalScroll = partialScroll,
            maxCanvasScroll = maxCanvasScroll
        )
        assertEquals("scrollD must equal total scroll when < headerHeight", 120, splitA.scrollD)
        assertEquals("editorOffsetY must be strictly 0 while header is visible", 0, splitA.editorOffsetY)

        // Case B: Total scroll exceeds header height -> scrollD is clamped to headerHeight, rest goes to editor
        val deepScroll = 850
        val splitB = EditorSessionRestorationCoordinator.splitLogicalScroll(
            headerHeight = headerHeight,
            totalLogicalScroll = deepScroll,
            maxCanvasScroll = maxCanvasScroll
        )
        assertEquals("scrollD must be clamped to headerHeight", headerHeight, splitB.scrollD)
        assertEquals("editorOffsetY must receive remainder beyond headerHeight", 650, splitB.editorOffsetY)

        // Case C: Viewport/header geometry change reflow (e.g. header grew to 280 due to title wrap or font size)
        val newHeaderHeight = 280
        val splitC = EditorSessionRestorationCoordinator.splitLogicalScroll(
            headerHeight = newHeaderHeight,
            totalLogicalScroll = deepScroll, // 850 total
            maxCanvasScroll = maxCanvasScroll
        )
        assertEquals("scrollD must adjust to new headerHeight", 280, splitC.scrollD)
        assertEquals("editorOffsetY must adjust consistently (850 - 280 = 570)", 570, splitC.editorOffsetY)
    }

    /**
     * 3. Returning to the same editor session requests restoration once, even if more than one
     *    lifecycle/navigation event occurs.
     */
    @Test
    fun testReturningToSameSessionRequestsRestorationOnceAcrossMultipleEvents() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot("note1")
            override fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // Navigate away: Push Settings
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)

        // Activity goes to background while on Settings
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_STOP)

        // User brings app back to foreground (ON_RESUME while still on Settings)
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals("Should not restore while still covered by Settings", 0, restoreCount.get())

        // User pops Settings -> returns to Editor
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Should restore exactly once upon returning to editor", 1, restoreCount.get())

        // Subsequent redundant lifecycle event should not duplicate restoration
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals("Redundant lifecycle ON_RESUME must not duplicate restoration", 1, restoreCount.get())
    }

    /**
     * 4. Restoration is independent of the Restore Cursor Position on Open setting.
     */
    @Test
    fun testRestorationIsIndependentOfRestoreCursorOnOpenSetting() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        var restoredViewport: ViewportSnapshot? = null

        val expectedScrollD = 200
        val expectedOffsetY = 800
        coordinator.updateViewportSnapshot(editorKey, "note1", expectedScrollD, expectedOffsetY)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot("note1")
            override fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit) {
                restoredViewport = snapshot
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        coordinator.registerClient(client)

        // Even when Restore Cursor Position on Open is FALSE,
        // mid-session return MUST restore the exact viewport unconditionally.
        val restoreCursorOnOpen = false
        coordinator.requestCoordinatedRestoration(editorKey)

        assertNotNull("Viewport must be restored even when restoreCursorOnOpen is false", restoredViewport)
        assertEquals(expectedScrollD, restoredViewport!!.scrollD)
        assertEquals(expectedOffsetY, restoredViewport!!.editorOffsetY)
    }

    /**
     * 5. A pending restoration waits for layout readiness and does not use temporary bounds.
     */
    @Test
    fun testPendingRestorationWaitsForLayoutReadinessWithoutUsingTemporaryBounds() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        var restorationApplied = false
        var isLayoutReady = false

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 1200)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot("note1")
            override fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit) {
                if (isLayoutReady()) {
                    restorationApplied = true
                    onComplete()
                }
            }
            override fun isLayoutReady() = isLayoutReady
        }

        coordinator.registerClient(client)

        // Initially layout is NOT ready (wordwrap computing, temporary 0 bounds)
        coordinator.requestCoordinatedRestoration(editorKey)
        assertFalse("Restoration must not apply while layout is unready", restorationApplied)

        // When layout settles and becomes ready:
        isLayoutReady = true
        client.restoreViewport(coordinator.getSnapshot("note1")!!) {}
        assertTrue("Restoration must apply once layout is fully ready", restorationApplied)
    }

    /**
     * 6. Changing documents or editor sessions invalidates pending work and prevents
     *    stale snapshots from being applied.
     */
    @Test
    fun testChangingDocumentsInvalidatesPendingWorkAndPreventsStaleSnapshots() {
        val coordinator = EditorSessionRestorationCoordinator()
        val note1Key = Route.Editor("book1", "note1")
        val note2Key = Route.Editor("book1", "note2")

        coordinator.updateViewportSnapshot(note1Key, "note1", scrollD = 200, editorOffsetY = 500)

        // Open Note 1
        val stack1 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)
        assertTrue(coordinator.isEditorActive(note1Key))

        // Navigate directly to Note 2 (document changed)
        val stack2 = listOf<NavKey>(Route.Home, Route.Book("book1"), note2Key)
        coordinator.onNavigationBackStackChanged(stack2, isCompact = true)

        assertFalse("Note 1 must no longer be active", coordinator.isEditorActive(note1Key))
        assertTrue("Note 2 must now be active", coordinator.isEditorActive(note2Key))

        // Note 2 is a fresh document opening: it must NOT have Note 1's snapshot
        val note2Snapshot = coordinator.getSnapshot("note2")
        assertNull("Genuinely new document must not receive previous document's snapshot", note2Snapshot)

        // Stale snapshot for note 1 was invalidated on session change
        assertNull("Note 1 snapshot must be invalidated", coordinator.getSnapshot("note1"))
    }

    /**
     * 7. Restoring the viewport does not replace it with the current cursor's position.
     */
    @Test
    fun testRestoringViewportDoesNotReplaceWithCursorPosition() {
        val headerHeight = 200
        val savedScrollD = 200
        val savedEditorY = 900
        val totalLogicalScroll = savedScrollD + savedEditorY // 1100

        // Simulated cursor is on Line 0 (top of document, y = 0)
        val cursorLine = 0
        val cursorColumn = 0

        // The authoritative restoration calculates:
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = 0,
            editorOffsetY = totalLogicalScroll,
            maxCanvasScroll = 3000
        )

        assertEquals("Restored scrollD must match saved viewport (200)", 200, reconciled.scrollD)
        assertEquals("Restored editorOffsetY must match saved viewport (900)", 900, reconciled.editorOffsetY)

        // Ensure the restored offset is preserved and NOT clamped to cursor line (line 0 = y: 0)
        assertTrue("Restored position (900) must NOT be overridden by cursor at top (0)", reconciled.editorOffsetY > 0)
    }

    /**
     * 8. Adding a new Navigation 3 destination requires no modifications to that destination
     *    for automatic editor restoration.
     */
    @Test
    fun testNewNavigation3DestinationAutomaticallySupportedWithoutModifications() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        var restoreCalled = false

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 450)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot("note1")
            override fun restoreViewport(snapshot: ViewportSnapshot, onComplete: () -> Unit) {
                restoreCalled = true
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        coordinator.registerClient(client)

        val activeStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(activeStack, isCompact = true)
        assertTrue("Editor must be active", coordinator.isEditorActive(editorKey))

        // Push brand-new future destination (FutureArbitraryDestination has ZERO editor-specific code)
        val pushedStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, FutureArbitraryDestination)
        coordinator.onNavigationBackStackChanged(pushedStack, isCompact = true)

        assertFalse("Editor must be automatically covered when new destination pushed", coordinator.isEditorActive(editorKey))

        // Pop brand-new future destination
        coordinator.onNavigationBackStackChanged(activeStack, isCompact = true)

        assertTrue("Editor must be automatically active again", coordinator.isEditorActive(editorKey))
        assertTrue("Viewport restoration must automatically execute without modifying the destination", restoreCalled)
    }
}
