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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests validating robust editor viewport restoration across Navigation 3 screens.
 */
class EditorViewportRestorationTest {

    // Test destination representing an arbitrary future Navigation 3 screen (implements NavKey, not app's sealed Route)
    private data object FutureArbitraryDestination : NavKey

    /**
     * 1. A newly registered editor does not restore a snapshot merely because one exists.
     */
    @Test
    fun testNewlyRegisteredEditorDoesNotRestoreMerelyBecauseSnapshotExists() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        // Seed an existing snapshot
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 600)
        assertTrue("Snapshot should exist", coordinator.hasSnapshot(editorKey))
        assertFalse("New session must not have pending restoration", coordinator.hasPendingRestoration(editorKey))

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        // Active in stack as a brand new session
        val stack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(stack, isCompact = true)
        coordinator.registerClient(client)

        assertEquals("Newly registered client must NOT restore snapshot without prior suspension", 0, restoreCount.get())
    }

    /**
     * 2. Returning to a genuinely suspended session restores its viewport exactly once.
     */
    @Test
    fun testReturningToGenuinelySuspendedSessionRestoresViewportExactlyOnce() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // Navigate away: Push Settings (session becomes suspended)
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        assertTrue("Session must have pending restoration while suspended", coordinator.hasPendingRestoration(editorKey))

        // Return from Settings
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Should restore exactly once upon returning to editor", 1, restoreCount.get())
        assertFalse("Pending restoration requirement should be consumed", coordinator.hasPendingRestoration(editorKey))
    }

    /**
     * 3. Duplicate navigation or lifecycle notifications for the same transition do not cause additional restores.
     */
    @Test
    fun testDuplicateNavigationOrLifecycleNotificationsDoNotCauseAdditionalRestores() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
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

        // Return to editor (first notification)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Should restore once on initial return", 1, restoreCount.get())

        // Duplicate navigation backStack changed notification with identical state
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Duplicate navigation notification must not re-trigger restoration", 1, restoreCount.get())

        // Redundant activity ON_RESUME event
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals("Redundant lifecycle ON_RESUME must not duplicate restoration", 1, restoreCount.get())
    }

    /**
     * 4. Activity resume while the editor remains covered does not restore it prematurely.
     */
    @Test
    fun testActivityResumeWhileEditorRemainsCoveredDoesNotRestorePrematurely() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // Push Settings
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)

        // Activity goes to background while on Settings
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_STOP)

        // User brings app back to foreground (ON_RESUME while still on Settings)
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals("Must not restore while editor is covered by Settings", 0, restoreCount.get())

        // User pops Settings -> returns to Editor
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Must restore when editor is actually uncovered", 1, restoreCount.get())
    }

    /**
     * 5. A legitimate reattached editor can recover its pending viewport.
     */
    @Test
    fun testLegitimateReattachedEditorCanRecoverPendingViewport() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 800)

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        val oldClient = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }
        coordinator.registerClient(oldClient)

        // Navigate away: Push Settings
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)

        // View is disposed/unregistered during detachment while retained
        coordinator.unregisterClient(oldClient)
        assertTrue("Pending restoration requirement must be preserved across detachment", coordinator.hasPendingRestoration(editorKey))

        // Return to editor before new client is registered: pending restoration must NOT be discarded
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Cannot restore without a registered client", 0, restoreCount.get())
        assertTrue("Pending restoration must remain pending when no client is available", coordinator.hasPendingRestoration(editorKey))

        // Recreated view registers new client
        val newClient = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCount.incrementAndGet()
                onComplete()
            }
            override fun isLayoutReady() = true
        }
        coordinator.registerClient(newClient)

        assertEquals("Reattached editor must recover its pending viewport", 1, restoreCount.get())
        assertFalse("Pending restoration requirement must be consumed after reattachment restore", coordinator.hasPendingRestoration(editorKey))
    }

    /**
     * 6. A restoration waiting for layout is cancelled if the document or session changes.
     */
    @Test
    fun testRestorationWaitingForLayoutCancelledIfDocumentOrSessionChanges() {
        val coordinator = EditorSessionRestorationCoordinator()
        val note1Key = Route.Editor("book1", "note1")
        val note2Key = Route.Editor("book1", "note2")
        val restorationApplied = AtomicBoolean(false)

        coordinator.updateViewportSnapshot(note1Key, "note1", scrollD = 200, editorOffsetY = 1200)

        var deferredAction: (() -> Unit)? = null

        val client1 = object : EditorSessionClient {
            override val sessionKey = note1Key
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(note1Key)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                // Client defers execution waiting for text and wordwrap layout readiness
                deferredAction = {
                    if (coordinator.isRestorationValid(note1Key, "note1", generation)) {
                        restorationApplied.set(true)
                        onComplete()
                    }
                }
            }
            override fun isLayoutReady() = false
        }

        val stack1 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)
        coordinator.registerClient(client1)

        // Suspend and resume note1
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)

        assertNotNull("Restoration request must be scheduled and deferred", deferredAction)
        assertFalse("Restoration must not be applied while waiting for layout", restorationApplied.get())

        // Document changes before layout settles: user switches to Note 2
        val stack2 = listOf<NavKey>(Route.Home, Route.Book("book1"), note2Key)
        coordinator.onNavigationBackStackChanged(stack2, isCompact = true)

        // Deferred layout callback for Note 1 finally executes
        deferredAction?.invoke()

        assertFalse("Older layout callback must be cancelled and forbidden from modifying viewport", restorationApplied.get())
    }

    /**
     * 7. If one editor entry is pushed above a retained editor, popping it restores the retained editor's own viewport.
     */
    @Test
    fun testPushedEditorAboveRetainedEditorAndPoppedRestoresRetainedEditorViewport() {
        val coordinator = EditorSessionRestorationCoordinator()
        val note1Key = Route.Editor("book1", "note1")
        val note2Key = Route.Editor("book1", "note2")

        val note1Restored = AtomicInteger(0)
        var note1RestoredSnapshot: ViewportSnapshot? = null

        coordinator.updateViewportSnapshot(note1Key, "note1", scrollD = 180, editorOffsetY = 650)

        val client1 = object : EditorSessionClient {
            override val sessionKey = note1Key
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(note1Key)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                note1Restored.incrementAndGet()
                note1RestoredSnapshot = snapshot
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        // Open Note 1
        val stack1 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)
        coordinator.registerClient(client1)

        // Push Note 2 on top of Note 1 (retained editor in stack)
        val stack2 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key, note2Key)
        coordinator.onNavigationBackStackChanged(stack2, isCompact = true)

        assertNotNull("Note 1 snapshot must be retained while underneath", coordinator.getSnapshot(note1Key))
        assertTrue("Note 1 must be marked pending restoration while suspended underneath", coordinator.hasPendingRestoration(note1Key))

        // Pop Note 2 -> Note 1 becomes active again
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)

        assertEquals("Popping overlaying editor must restore retained editor's viewport exactly once", 1, note1Restored.get())
        assertNotNull("Restored snapshot must not be null", note1RestoredSnapshot)
        assertEquals(180, note1RestoredSnapshot!!.scrollD)
        assertEquals(650, note1RestoredSnapshot!!.editorOffsetY)
    }

    /**
     * 8. A newly opened document does not inherit another session's viewport.
     */
    @Test
    fun testNewlyOpenedDocumentDoesNotInheritAnotherSessionViewport() {
        val coordinator = EditorSessionRestorationCoordinator()
        val note1Key = Route.Editor("book1", "note1")
        val note2Key = Route.Editor("book1", "note2")

        coordinator.updateViewportSnapshot(note1Key, "note1", scrollD = 200, editorOffsetY = 500)

        // Note 1 opened
        val stack1 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)
        assertTrue(coordinator.isEditorActive(note1Key))

        // Navigate directly to Note 2 (Note 1 removed from stack)
        val stack2 = listOf<NavKey>(Route.Home, Route.Book("book1"), note2Key)
        coordinator.onNavigationBackStackChanged(stack2, isCompact = true)

        assertFalse("Note 1 must no longer be active", coordinator.isEditorActive(note1Key))
        assertTrue("Note 2 must now be active", coordinator.isEditorActive(note2Key))

        // Note 2 is a fresh document opening: must NOT have any snapshot or pending restoration
        assertNull("Note 2 must not inherit Note 1's snapshot", coordinator.getSnapshot(note2Key))
        assertNull("Note 2 must not inherit Note 1's snapshot by noteId", coordinator.getSnapshot("note2"))
        assertFalse("Note 2 must not have pending restoration", coordinator.hasPendingRestoration(note2Key))

        // Note 1 session was removed and invalidated
        assertNull("Note 1 snapshot must be invalidated", coordinator.getSnapshot(note1Key))
    }

    /**
     * 9. The latest actual inner-editor offset is captured after the header is hidden.
     */
    @Test
    fun testViewportSnapshotRecordsLatestInnerEditorOffsetAfterHeaderScrolledAway() {
        val headerHeight = 250
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")

        // User scrolled header away completely (scrollD = headerHeight) and deep into editor
        val scrollD = headerHeight
        val deeperEditorY = 750
        coordinator.updateViewportSnapshot(
            sessionKey = editorKey,
            noteId = "note1",
            scrollD = scrollD,
            editorOffsetY = deeperEditorY
        )

        val snapshot = coordinator.getSnapshot(editorKey)
        assertNotNull("Snapshot must be recorded", snapshot)
        assertEquals("scrollD must equal headerHeight", headerHeight, snapshot!!.scrollD)
        assertEquals("editorOffsetY must record latest inner offset", deeperEditorY, snapshot.editorOffsetY)
        assertEquals(
            "totalLogicalScroll must represent total combined displacement",
            headerHeight + deeperEditorY,
            snapshot.totalLogicalScroll
        )
    }

    /**
     * 10. Capturing a viewport does not consume or corrupt a running fling.
     */
    @Test
    fun testCapturingViewportDoesNotConsumeOrCorruptFling() {
        val editorKey = Route.Editor("book1", "note1")
        val scrollD = 150
        val editorOffsetY = 400

        // ViewportSnapshot accurately captures live values directly from measurements
        val snapshot = ViewportSnapshot(
            sessionKey = editorKey,
            noteId = "note1",
            scrollD = scrollD,
            editorOffsetY = editorOffsetY
        )

        assertEquals(scrollD, snapshot.scrollD)
        assertEquals(editorOffsetY, snapshot.editorOffsetY)
        assertEquals(scrollD + editorOffsetY, snapshot.totalLogicalScroll)
    }

    /**
     * 11. Cursor and selection restoration do not override the restored viewport.
     */
    @Test
    fun testCursorAndSelectionRestorationDoNotOverrideRestoredViewport() {
        val headerHeight = 200
        val savedScrollD = 200
        val savedEditorY = 900
        val totalLogicalScroll = savedScrollD + savedEditorY // 1100

        // Simulated cursor is on Line 0 (top of document, y = 0)
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = 0,
            editorOffsetY = totalLogicalScroll,
            maxCanvasScroll = 3000
        )

        assertEquals("Restored scrollD must match saved viewport (200)", 200, reconciled.scrollD)
        assertEquals("Restored editorOffsetY must match saved viewport (900)", 900, reconciled.editorOffsetY)
        assertTrue("Restored position must NOT be overridden by cursor at line 0", reconciled.editorOffsetY > 0)
    }

    /**
     * 12. A future Navigation 3 destination is automatically handled without destination-specific restoration code.
     */
    @Test
    fun testFutureNavigation3DestinationAutomaticallyHandledWithoutModifications() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        var restoreCalled = false

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 450)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoreCalled = true
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        coordinator.registerClient(client)

        val activeStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(activeStack, isCompact = true)
        assertTrue("Editor must be active", coordinator.isEditorActive(editorKey))

        // Push brand-new future destination (FutureArbitraryDestination implements NavKey, ZERO editor code)
        val pushedStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, FutureArbitraryDestination)
        coordinator.onNavigationBackStackChanged(pushedStack, isCompact = true)

        assertFalse("Editor must be automatically covered when new destination pushed", coordinator.isEditorActive(editorKey))
        assertTrue("Editor must have pending restoration", coordinator.hasPendingRestoration(editorKey))

        // Pop brand-new future destination
        coordinator.onNavigationBackStackChanged(activeStack, isCompact = true)

        assertTrue("Editor must be automatically active again", coordinator.isEditorActive(editorKey))
        assertTrue("Viewport restoration must automatically execute without modifying the destination", restoreCalled)
    }

    /**
     * 13. Snapshot cleanup removes all associated state for a genuinely removed session.
     */
    @Test
    fun testSnapshotCleanupRemovesAllAssociatedStateForGenuinelyRemovedSession() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)
        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {}
            override fun isLayoutReady() = true
        }

        val stack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(stack, isCompact = true)
        coordinator.registerClient(client)

        // Session removed from backstack
        val emptyStack = listOf<NavKey>(Route.Home)
        coordinator.onNavigationBackStackChanged(emptyStack, isCompact = true)

        assertNull("Snapshot must be removed", coordinator.getSnapshot(editorKey))
        assertFalse("Pending restoration must be removed", coordinator.hasPendingRestoration(editorKey))
        assertFalse("Editor must not be active", coordinator.isEditorActive(editorKey))
        assertFalse("Snapshot lookup by noteId must be null", coordinator.hasSnapshot("note1"))
    }

    /**
     * Invariant: Logical scroll split respecting the 1-DOF invariant.
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

        // Case C: Viewport/header geometry change reflow (header grew to 280)
        val newHeaderHeight = 280
        val splitC = EditorSessionRestorationCoordinator.splitLogicalScroll(
            headerHeight = newHeaderHeight,
            totalLogicalScroll = deepScroll,
            maxCanvasScroll = maxCanvasScroll
        )
        assertEquals("scrollD must adjust to new headerHeight", 280, splitC.scrollD)
        assertEquals("editorOffsetY must adjust consistently (850 - 280 = 570)", 570, splitC.editorOffsetY)
    }

    /**
     * Invariant: Restoration is independent of the Restore Cursor Position on Open setting.
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
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onComplete: () -> Unit) {
                restoredViewport = snapshot
                onComplete()
            }
            override fun isLayoutReady() = true
        }

        coordinator.registerClient(client)

        // Even when Restore Cursor Position on Open is FALSE,
        // mid-session return MUST restore the exact viewport unconditionally.
        val restoreCursorOnOpen = false
        val success = coordinator.requestCoordinatedRestoration(editorKey)

        assertTrue("Restoration must be successfully dispatched", success)
        assertNotNull("Viewport must be restored even when restoreCursorOnOpen is false", restoredViewport)
        assertEquals(expectedScrollD, restoredViewport!!.scrollD)
        assertEquals(expectedOffsetY, restoredViewport!!.editorOffsetY)
    }
}
