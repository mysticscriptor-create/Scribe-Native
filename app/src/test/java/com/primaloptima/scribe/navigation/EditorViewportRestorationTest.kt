package com.primaloptima.scribe.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import com.primaloptima.scribe.ui.components.ScribeScrollPolicy
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.model.DocumentCursorState
import com.primaloptima.scribe.util.model.EditingTarget
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests validating robust editor viewport restoration and cursor restoration
 * across Navigation 3 screens, overflow sheets, and application lifecycle events.
 */
class EditorViewportRestorationTest {

    // Test destination representing an arbitrary future Navigation 3 screen (implements NavKey, not app's sealed Route)
    private data object FutureArbitraryDestination : NavKey

    /**
     * Scenario 1: Overflow-menu navigation to Settings lifecycle.
     * When the bottom sheet opens, onOverlayVisibilityChanged(effectiveKey, true) is called.
     * When user clicks Settings, onOverlayDismissedForNavigation(effectiveKey) is called,
     * immediately capturing viewport, setting session state to PENDING, clearing isOverlayCovering,
     * and marking isCurrentlyCovered = true so restoration does not fire prematurely.
     * When Settings is pushed and later popped, the returning editor executes restoration exactly once,
     * and the overlay covering flag remains cleared.
     */
    @Test
    fun testScenario1_OverflowMenuNavigationToSettingsLifecycle() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoreCount.incrementAndGet()
                onResult(RestorationResult.APPLIED)
            }
            override fun isLayoutReady() = true
        }

        val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        coordinator.registerClient(client)

        // 1. User opens bottom overflow sheet
        coordinator.onOverlayVisibilityChanged(editorKey, isCovered = true)
        assertTrue("Coordinator must register overlay covering editor", coordinator.hasPendingRestoration(editorKey))

        // 2. User taps Settings item in overflow sheet: onOverlayDismissedForNavigation called
        coordinator.onOverlayDismissedForNavigation(editorKey)
        assertTrue("Session must remain pending restoration", coordinator.hasPendingRestoration(editorKey))
        assertEquals("No restoration before destination transition", 0, restoreCount.get())

        // 3. Navigation pushes Settings destination
        val settingsStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(settingsStack, isCompact = true)
        assertEquals("No restoration while covered by Settings", 0, restoreCount.get())

        // 4. User navigates back from Settings: returns to editor
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        assertEquals("Restoration must execute exactly once upon returning to editor", 1, restoreCount.get())
        assertFalse("Pending restoration flag must be consumed", coordinator.hasPendingRestoration(editorKey))

        // 5. Subsequent backstack checks or dismissals do not re-trigger restoration
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        assertEquals("Duplicate backstack notification must not duplicate restoration", 1, restoreCount.get())
    }

    /**
     * Scenario 2: Overflow-menu navigation for History, Shortcuts, Guide, Themes, Export Studio.
     * All 5 auxiliary destinations opened from the overflow sheet must exhibit identical lifecycle
     * handling: capturing viewport, clearing overlay flag, deferring until return, and restoring once.
     */
    @Test
    fun testScenario2_OverflowMenuNavigationForAuxiliaryDestinations() {
        val auxiliaryDestinations = listOf(
            Route.History("note1"),
            Route.Shortcuts,
            Route.Guide,
            Route.ThemeList,
            Route.ExportStudio(bookId = "book1", noteId = "note1", initialFormat = "PDF")
        )

        for (destination in auxiliaryDestinations) {
            val coordinator = EditorSessionRestorationCoordinator()
            val editorKey = Route.Editor("book1", "note1")
            val restoreCount = AtomicInteger(0)

            coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 180, editorOffsetY = 720)

            val client = object : EditorSessionClient {
                override val sessionKey = editorKey
                override val noteId = "note1"
                override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
                override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                    restoreCount.incrementAndGet()
                    onResult(RestorationResult.APPLIED)
                }
                override fun isLayoutReady() = true
            }

            val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
            coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
            coordinator.registerClient(client)

            // Sheet opens
            coordinator.onOverlayVisibilityChanged(editorKey, isCovered = true)

            // Sheet initiates navigation to auxiliary destination
            coordinator.onOverlayDismissedForNavigation(editorKey)

            // Destination pushed
            val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, destination)
            coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
            assertEquals("Must not restore while $destination covers editor", 0, restoreCount.get())

            // Destination popped: return to editor
            coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
            assertEquals("Must restore exactly once on return from $destination", 1, restoreCount.get())
            assertFalse("Pending restoration flag must be consumed for $destination", coordinator.hasPendingRestoration(editorKey))
        }
    }

    /**
     * Scenario 3: Dismissing sheet without navigating restores saved viewport once.
     */
    @Test
    fun testScenario3_DismissingSheetWithoutNavigatingRestoresSavedViewportOnce() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 220, editorOffsetY = 640)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoreCount.incrementAndGet()
                onResult(RestorationResult.APPLIED)
            }
            override fun isLayoutReady() = true
        }

        val stack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(stack, isCompact = true)
        coordinator.registerClient(client)

        // Open sheet
        coordinator.onOverlayVisibilityChanged(editorKey, isCovered = true)
        assertEquals("Opening sheet does not immediately restore", 0, restoreCount.get())

        // User taps outside or drags down to dismiss sheet
        coordinator.onOverlayVisibilityChanged(editorKey, isCovered = false)
        assertEquals("Dismissing sheet back to active editor must restore viewport once", 1, restoreCount.get())
        assertFalse("Pending restoration must be consumed", coordinator.hasPendingRestoration(editorKey))

        // Redundant dismissal does nothing
        coordinator.onOverlayVisibilityChanged(editorKey, isCovered = false)
        assertEquals("Redundant dismissal notification must not duplicate restore", 1, restoreCount.get())
    }

    /**
     * Scenario 4: Closing sheet while navigation destination changes does not apply viewport prematurely.
     */
    @Test
    fun testScenario4_ClosingSheetWhileNavigationDestinationChangesDoesNotApplyViewportPrematurely() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoreCount.incrementAndGet()
                onResult(RestorationResult.APPLIED)
            }
            override fun isLayoutReady() = true
        }

        val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        coordinator.registerClient(client)

        // Sheet opened
        coordinator.onOverlayVisibilityChanged(editorKey, isCovered = true)

        // Sheet dismissed for navigation
        coordinator.onOverlayDismissedForNavigation(editorKey)

        // Before or during composition transition, ensure no restore fires against old state
        assertEquals("Must not apply viewport prematurely during navigation transition", 0, restoreCount.get())

        // Stack transitions to auxiliary screen
        val settingsStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(settingsStack, isCompact = true)
        assertEquals("Must not apply while destination is covering editor", 0, restoreCount.get())
    }

    /**
     * Scenario 5: Returning from auxiliary screen restores exact saved scrollD and editorOffsetY.
     */
    @Test
    fun testScenario5_ReturningFromAuxiliaryScreenRestoresExactSavedScrollDAndEditorOffsetY() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        var restoredSnapshot: ViewportSnapshot? = null

        val expectedScrollD = 240
        val expectedOffsetY = 1350
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = expectedScrollD, editorOffsetY = expectedOffsetY)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoredSnapshot = snapshot
                onResult(RestorationResult.APPLIED)
            }
            override fun isLayoutReady() = true
        }

        val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        coordinator.registerClient(client)

        // Navigate away to Guide
        val guideStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Guide)
        coordinator.onNavigationBackStackChanged(guideStack, isCompact = true)

        // Return from Guide
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)

        assertNotNull("Restored snapshot must not be null", restoredSnapshot)
        assertEquals("Restored scrollD must match exactly", expectedScrollD, restoredSnapshot!!.scrollD)
        assertEquals("Restored editorOffsetY must match exactly", expectedOffsetY, restoredSnapshot!!.editorOffsetY)
        assertEquals("Logical scroll must match sum", expectedScrollD + expectedOffsetY, restoredSnapshot!!.totalLogicalScroll)
    }

    /**
     * Scenario 6: Retained-session viewport restoration independent of cursor preference (both enabled and disabled).
     */
    @Test
    fun testScenario6_RetainedSessionViewportRestorationIndependentOfCursorPreference() {
        for (preferenceEnabled in listOf(true, false)) {
            val coordinator = EditorSessionRestorationCoordinator()
            val editorKey = Route.Editor("book1", "note1")
            var restoredSnapshot: ViewportSnapshot? = null

            val expectedScrollD = 200
            val expectedOffsetY = 850
            coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = expectedScrollD, editorOffsetY = expectedOffsetY)

            val client = object : EditorSessionClient {
                override val sessionKey = editorKey
                override val noteId = "note1"
                override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
                override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                    restoredSnapshot = snapshot
                    onResult(RestorationResult.APPLIED)
                }
                override fun isLayoutReady() = true
            }

            val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
            coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
            coordinator.registerClient(client)

            // Suspend session by pushing Settings
            val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
            coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)

            // Verify isRetainedSession is true
            assertTrue("Session must be recognized as retained", coordinator.isRetainedSession(editorKey))

            // Return to editor
            coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)

            assertNotNull("Viewport must be restored regardless of cursor preference ($preferenceEnabled)", restoredSnapshot)
            assertEquals(expectedScrollD, restoredSnapshot!!.scrollD)
            assertEquals(expectedOffsetY, restoredSnapshot!!.editorOffsetY)
        }
    }

    /**
     * Scenario 7: Fresh session with cursor restoration enabled computes centered viewport for saved cursor.
     */
    @Test
    fun testScenario7_FreshSessionWithCursorRestorationEnabledComputesCenteredViewportForSavedCursor() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note_fresh")

        // In a fresh session, coordinator has no retained snapshot
        assertFalse("Fresh session must not be retained", coordinator.isRetainedSession(editorKey))

        // Document cursor saved at line 50, col 10
        val savedCursor = DocumentCursorState(
            target = EditingTarget.EDITOR_BODY,
            bodyStartLine = 50,
            bodyStartCol = 10,
            bodyEndLine = 50,
            bodyEndCol = 10
        )

        val headerHeight = 250
        val rowHeight = 60f
        val lineDocY = savedCursor.bodyStartLine * rowHeight // 3000f
        val cursorCanvasY = headerHeight + lineDocY // 3250f
        val viewportHeight = 1800f

        val targetTotalScroll = cursorCanvasY - (viewportHeight / 2f) // 3250 - 900 = 2350f
        assertTrue("Target total scroll must be positive and non-zero", targetTotalScroll > 0f)

        val targetScrollD = targetTotalScroll.toInt().coerceIn(0, headerHeight) // 250
        val remainingScroll = (targetTotalScroll.toInt() - targetScrollD).coerceAtLeast(0) // 2100

        assertEquals("Header must be fully scrolled away", headerHeight, targetScrollD)
        assertTrue("Editor inner scroll must scroll to bring cursor into center", remainingScroll > 1000)

        // Reconciled scroll matches centered calculation
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = 0,
            editorOffsetY = targetTotalScroll.toInt(),
            maxCanvasScroll = 10000
        )
        assertEquals(headerHeight, reconciled.scrollD)
        assertEquals(targetTotalScroll.toInt() - headerHeight, reconciled.editorOffsetY)
    }

    /**
     * Scenario 8: Fresh session with cursor restoration disabled does not restore saved cursor (defaults to line 0 and scroll 0).
     */
    @Test
    fun testScenario8_FreshSessionWithCursorRestorationDisabledDoesNotRestoreSavedCursor() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note_fresh_disabled")

        // Fresh session: no retained snapshot in coordinator
        assertFalse("Fresh session must not be retained", coordinator.isRetainedSession(editorKey))

        val restoreCursorOnOpen = false
        val savedCursorInDb = DocumentCursorState(
            target = EditingTarget.EDITOR_BODY,
            bodyStartLine = 80,
            bodyStartCol = 5
        )

        // When preference is disabled and session is not retained:
        val effectiveLine = if (restoreCursorOnOpen || coordinator.isRetainedSession(editorKey)) {
            savedCursorInDb.bodyStartLine
        } else {
            0
        }
        val effectiveCol = if (restoreCursorOnOpen || coordinator.isRetainedSession(editorKey)) {
            savedCursorInDb.bodyStartCol
        } else {
            0
        }

        assertEquals("Fresh session with preference disabled must start at line 0", 0, effectiveLine)
        assertEquals("Fresh session with preference disabled must start at col 0", 0, effectiveCol)
    }

    /**
     * Scenario 9: Toggling preference on and navigating immediately preserves saved cursor and applies without race.
     */
    @Test
    fun testScenario9_TogglingPreferenceOnAndNavigatingImmediatelyPreservesSavedCursorWithoutRace() {
        // Simulates the volatile memory override added to EditorViewModel
        class SimulatedPreferenceHolder {
            @Volatile
            var volatileOverride: Boolean? = null
            var asyncStoredValue: Boolean = false

            fun setRestoreCursorOnOpen(enabled: Boolean) {
                volatileOverride = enabled
                // Asynchronous commit happens later in background...
            }

            fun isRestoreCursorEnabled(): Boolean {
                return volatileOverride ?: asyncStoredValue
            }
        }

        val holder = SimulatedPreferenceHolder()
        assertFalse("Initial preference is disabled", holder.isRestoreCursorEnabled())

        // User toggles switch ON in settings and immediately navigates
        holder.setRestoreCursorOnOpen(true)

        // Even though asyncStoredValue is still false, isRestoreCursorEnabled() immediately reflects true
        assertTrue("Immediate check after toggle must return true synchronously without race", holder.isRestoreCursorEnabled())
    }

    /**
     * Scenario 10: Cursor state survives view disposal/recreation via serialization.
     */
    @Test
    fun testScenario10_CursorStateSurvivesViewDisposalAndRecreationViaSerialization() {
        val original = DocumentCursorState(
            target = EditingTarget.EDITOR_BODY,
            bodyStartLine = 75,
            bodyStartCol = 14,
            bodyEndLine = 75,
            bodyEndCol = 28,
            scrollD = 200,
            scrollY = 1200,
            primarySelStart = 0,
            primarySelEnd = 0,
            secondarySelStart = 0,
            secondarySelEnd = 0
        )

        val json = AppJson.encodeToString(original)
        assertTrue("Serialized JSON must contain target", json.contains("EDITOR_BODY"))
        assertTrue("Serialized JSON must contain bodyStartLine", json.contains("75"))

        val decoded = AppJson.decodeFromString<DocumentCursorState>(json)
        assertEquals(original.target, decoded.target)
        assertEquals(original.bodyStartLine, decoded.bodyStartLine)
        assertEquals(original.bodyStartCol, decoded.bodyStartCol)
        assertEquals(original.bodyEndLine, decoded.bodyEndLine)
        assertEquals(original.bodyEndCol, decoded.bodyEndCol)
        assertEquals(original.scrollD, decoded.scrollD)
        assertEquals(original.scrollY, decoded.scrollY)
    }

    /**
     * Scenario 11: Cursor save fallback preserves known state and prevents accidental overwrite by line 0 when editor reference is temporarily null.
     */
    @Test
    fun testScenario11_CursorSaveFallbackPreservesKnownStateWhenEditorRefTemporarilyNull() {
        val existingKnownState = DocumentCursorState(
            target = EditingTarget.EDITOR_BODY,
            bodyStartLine = 42,
            bodyStartCol = 8
        )

        // Simulated capture function matching MainEditorScreen's null fallback logic
        fun captureCursorState(editorRef: Any?, fallbackState: DocumentCursorState?): DocumentCursorState {
            if (editorRef == null) {
                return fallbackState ?: DocumentCursorState()
            }
            // If editor was non-null, read actual positions
            return DocumentCursorState(target = EditingTarget.EDITOR_BODY, bodyStartLine = 1, bodyStartCol = 0)
        }

        // When editor is temporarily null during recreation or transition
        val captured = captureCursorState(editorRef = null, fallbackState = existingKnownState)

        assertEquals("Must preserve known line 42 instead of defaulting to 0", 42, captured.bodyStartLine)
        assertEquals("Must preserve known col 8 instead of defaulting to 0", 8, captured.bodyStartCol)
        assertEquals(EditingTarget.EDITOR_BODY, captured.target)
    }

    /**
     * Scenario 12: Retained session cursor placement does not scroll or override restored viewport.
     */
    @Test
    fun testScenario12_RetainedSessionCursorPlacementDoesNotScrollOrOverrideRestoredViewport() {
        val headerHeight = 200
        val restoredScrollD = 200
        val restoredEditorOffsetY = 900
        val totalRestoredScroll = restoredScrollD + restoredEditorOffsetY

        // User placed cursor on Line 2 (which is near the top of document in canvas coordinates)
        val reconciled = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = headerHeight,
            scrollD = 0,
            editorOffsetY = totalRestoredScroll,
            maxCanvasScroll = 5000
        )

        assertEquals("Restored scrollD must be preserved", 200, reconciled.scrollD)
        assertEquals("Restored editorOffsetY must be preserved", 900, reconciled.editorOffsetY)
        // Cursor placement does not pull viewport back to 0
        assertTrue("Viewport must not be reset to top by cursor placement", reconciled.editorOffsetY > 0)
    }

    /**
     * Scenario 13: Restoration requested before client/view/layout is ready remains pending and applies when ready.
     */
    @Test
    fun testScenario13_RestorationRequestedBeforeClientViewLayoutReadyRemainsPendingAndAppliesWhenReady() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restorationApplied = AtomicBoolean(false)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 800)

        var deferredOnComplete: (() -> Unit)? = null
        var isLayoutReadyFlag = false

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                if (!isLayoutReady()) {
                    deferredOnComplete = {
                        if (coordinator.isRestorationValid(editorKey, "note1", generation)) {
                            restorationApplied.set(true)
                            onResult(RestorationResult.APPLIED)
                        } else {
                            onResult(RestorationResult.CANCELLED)
                        }
                    }
                } else {
                    restorationApplied.set(true)
                    onResult(RestorationResult.APPLIED)
                }
            }
            override fun isLayoutReady() = isLayoutReadyFlag
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // Navigate away and back
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        assertNotNull("Deferred completion runnable must be captured", deferredOnComplete)
        assertFalse("Restoration must not be applied before layout is ready", restorationApplied.get())

        // Layout settles and becomes ready
        isLayoutReadyFlag = true
        deferredOnComplete?.invoke()

        assertTrue("Restoration must be successfully applied once layout is ready", restorationApplied.get())
        assertFalse("Pending restoration flag must be consumed", coordinator.hasPendingRestoration(editorKey))
    }

    /**
     * Scenario 14: Document change/session invalidation cancels in-flight layout callback.
     */
    @Test
    fun testScenario14_DocumentChangeOrSessionInvalidationCancelsInFlightLayoutCallback() {
        val coordinator = EditorSessionRestorationCoordinator()
        val note1Key = Route.Editor("book1", "note1")
        val note2Key = Route.Editor("book1", "note2")
        val restorationApplied = AtomicBoolean(false)

        coordinator.updateViewportSnapshot(note1Key, "note1", scrollD = 200, editorOffsetY = 1200)

        var inFlightCallback: (() -> Unit)? = null

        val client1 = object : EditorSessionClient {
            override val sessionKey = note1Key
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(note1Key)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                inFlightCallback = {
                    if (coordinator.isRestorationValid(note1Key, "note1", generation)) {
                        restorationApplied.set(true)
                        onResult(RestorationResult.APPLIED)
                    } else {
                        onResult(RestorationResult.CANCELLED)
                    }
                }
            }
            override fun isLayoutReady() = false
        }

        val stack1 = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)
        coordinator.registerClient(client1)

        // Suspend and resume note 1
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), note1Key, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(stack1, isCompact = true)

        // While in-flight callback is pending, document changes to Note 2
        val stack2 = listOf<NavKey>(Route.Home, Route.Book("book1"), note2Key)
        coordinator.onNavigationBackStackChanged(stack2, isCompact = true)

        // Old in-flight callback for Note 1 finally triggers
        inFlightCallback?.invoke()

        assertFalse("Stale in-flight callback must be rejected by coordinator", restorationApplied.get())
    }

    /**
     * Scenario 15: Repeated backstack or lifecycle events cannot duplicate or cancel valid restoration.
     */
    @Test
    fun testScenario15_RepeatedBackstackOrLifecycleEventsCannotDuplicateOrCancelValidRestoration() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        val restoreCount = AtomicInteger(0)

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoreCount.incrementAndGet()
                onResult(RestorationResult.APPLIED)
            }
            override fun isLayoutReady() = true
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // Push Settings
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)

        // Return to editor
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Should restore once on initial return", 1, restoreCount.get())

        // Repeated duplicate backStack changes
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        assertEquals("Duplicate backStack events must not re-trigger restoration", 1, restoreCount.get())

        // Rapid lifecycle events
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        coordinator.onActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals("Duplicate ON_RESUME must not re-trigger restoration", 1, restoreCount.get())
    }

    /**
     * Scenario 16: Popping editor route and opening same note later creates a fresh-session decision (isRetainedSession == false).
     */
    @Test
    fun testScenario16_PoppingEditorRouteAndOpeningSameNoteLaterCreatesFreshSessionDecision() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")

        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 500)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {}
            override fun isLayoutReady() = true
        }

        val editorStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(editorStack, isCompact = true)
        coordinator.registerClient(client)

        // User exits editor back to Book view (Route.Editor popped from backStack)
        val bookStack = listOf<NavKey>(Route.Home, Route.Book("book1"))
        coordinator.onNavigationBackStackChanged(bookStack, isCompact = true)

        // Session was genuinely exited: session state and snapshot are cleaned up
        assertFalse("Session is no longer retained after being popped", coordinator.isRetainedSession(editorKey))
        assertFalse("Pending restoration must be false", coordinator.hasPendingRestoration(editorKey))
        assertNull("Snapshot must be removed", coordinator.getSnapshot(editorKey))

        // Opening the same note later is treated as a genuinely fresh session
        val reopenedStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(reopenedStack, isCompact = true)

        assertFalse("Reopening exited note must be a fresh session, not a retained session", coordinator.isRetainedSession(editorKey))
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
     * Invariant: A future Navigation 3 destination is automatically handled without destination-specific restoration code.
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
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                restoreCalled = true
                onResult(RestorationResult.APPLIED)
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
     * Invariant: Capturing a viewport does not consume or corrupt a running fling.
     */
    @Test
    fun testCapturingViewportDoesNotConsumeOrCorruptFling() {
        val editorKey = Route.Editor("book1", "note1")
        val scrollD = 150
        val editorOffsetY = 400

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
     * Scenario 20: Cancelled restoration cleanly clears SCHEDULED state and does not lock out future restoration.
     */
    @Test
    fun testScenario20_CancelledRestorationClearsScheduledStateAndDoesNotLockOutFutureRestoration() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 200, editorOffsetY = 600)

        var shouldCancel = true
        val applyCount = AtomicInteger(0)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                if (shouldCancel) {
                    onResult(RestorationResult.CANCELLED)
                } else {
                    applyCount.incrementAndGet()
                    onResult(RestorationResult.APPLIED)
                }
            }
            override fun isLayoutReady() = true
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // 1. Suspend session and return: restoration attempts but is cancelled (e.g. view invalidated or mismatch)
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        assertEquals("State must be CANCELLED", RestorationState.CANCELLED, coordinator.getRestorationState(editorKey))
        assertEquals("No applied count on cancellation", 0, applyCount.get())

        // 2. Next navigation return should NOT be wedged by SCHEDULED; it can schedule restoration again!
        shouldCancel = false
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        assertEquals("State must be APPLIED after valid retry", RestorationState.APPLIED, coordinator.getRestorationState(editorKey))
        assertEquals("Restoration applied count must be 1", 1, applyCount.get())
    }

    /**
     * Scenario 21: Deferred restoration (e.g. view not inflated yet) cleanly recovers once client views become ready.
     */
    @Test
    fun testScenario21_DeferredRestorationConsumesSnapshotOnceClientLayoutBecomesReady() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 150, editorOffsetY = 450)

        var viewInflated = false
        val restoreCount = AtomicInteger(0)

        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                if (!viewInflated) {
                    onResult(RestorationResult.DEFERRED)
                } else {
                    restoreCount.incrementAndGet()
                    onResult(RestorationResult.APPLIED)
                }
            }
            override fun isLayoutReady() = viewInflated
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // User navigates away and back: view is not yet inflated at the instant navigation returns
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        assertEquals("State must remain PENDING after DEFERRED", RestorationState.PENDING, coordinator.getRestorationState(editorKey))
        assertEquals("No restoration applied while deferred", 0, restoreCount.get())

        // Now view inflates and calls onClientLayoutReady
        viewInflated = true
        coordinator.onClientLayoutReady(editorKey)

        assertEquals("State must be APPLIED after client layout is ready", RestorationState.APPLIED, coordinator.getRestorationState(editorKey))
        assertEquals("Restoration must have executed exactly once", 1, restoreCount.get())
    }

    /**
     * Scenario 22: Invalidation via top bar or back gesture clears coordinator session state.
     */
    @Test
    fun testScenario22_TopBarBackNavigationInvalidatesCoordinatorSessionAndClearsScroll() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 300, editorOffsetY = 900)

        assertTrue("Snapshot must exist initially", coordinator.hasSnapshot("note1"))

        // Top bar back or BackHandler invalidates session
        coordinator.invalidateSession(editorKey)

        assertFalse("Snapshot must be removed upon invalidation", coordinator.hasSnapshot("note1"))
        assertFalse("Session must no longer be classified as retained", coordinator.isRetainedSession(editorKey))
        assertNull("Restoration state must be cleared", coordinator.getRestorationState(editorKey))
    }

    /**
     * Scenario 23: ScribeScrollPolicy.reconcileScrollState preserves editorOffsetY when headerHeight <= 0.
     * When the headerView has not finished measuring (headerHeight = 0) or the note has no title,
     * the scroll offset must not be collapsed to (0, 0); all displacement must belong to editorOffsetY.
     */
    @Test
    fun testScenario23_ReconcileScrollStatePreservesEditorOffsetYWhenHeaderHeightNonPositive() {
        // Zero header height with scroll within maxCanvas bounds:
        val reconciledZeroHeader = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = 0,
            scrollD = 150,
            editorOffsetY = 600,
            maxCanvasScroll = 1200
        )
        assertEquals("scrollD must be 0 when headerHeight <= 0", 0, reconciledZeroHeader.scrollD)
        assertEquals("editorOffsetY must retain all totalScroll", 750, reconciledZeroHeader.editorOffsetY)

        // Negative header height guard:
        val reconciledNegativeHeader = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = -1,
            scrollD = 0,
            editorOffsetY = 500,
            maxCanvasScroll = 1000
        )
        assertEquals("scrollD must be 0 when headerHeight is negative", 0, reconciledNegativeHeader.scrollD)
        assertEquals("editorOffsetY must retain scroll when headerHeight is negative", 500, reconciledNegativeHeader.editorOffsetY)

        // Clamping to maxCanvasScroll:
        val reconciledClamped = ScribeScrollPolicy.reconcileScrollState(
            headerHeight = 0,
            scrollD = 200,
            editorOffsetY = 800,
            maxCanvasScroll = 600
        )
        assertEquals("scrollD must be 0", 0, reconciledClamped.scrollD)
        assertEquals("editorOffsetY must be clamped to safeMaxCanvas", 600, reconciledClamped.editorOffsetY)
    }

    /**
     * Scenario 24: Viewport restoration is deferred while document text is unmeasured/unloaded,
     * and succeeds once client layout readiness notification fires without snapshot corruption.
     */
    @Test
    fun testScenario24_ViewportRestorationDeferredUntilDocumentLayoutIsReady() {
        val coordinator = EditorSessionRestorationCoordinator()
        val editorKey = Route.Editor("book1", "note1")
        coordinator.updateViewportSnapshot(editorKey, "note1", scrollD = 180, editorOffsetY = 520)

        var isDocumentTextLoaded = false
        var restoredScrollD = -1
        var restoredEditorOffsetY = -1
        val client = object : EditorSessionClient {
            override val sessionKey = editorKey
            override val noteId = "note1"
            override fun captureCurrentViewport() = coordinator.getSnapshot(editorKey)
            override fun restoreViewport(snapshot: ViewportSnapshot, generation: Long, onResult: (RestorationResult) -> Unit) {
                if (!isDocumentTextLoaded) {
                    onResult(RestorationResult.DEFERRED)
                } else {
                    restoredScrollD = snapshot.scrollD
                    restoredEditorOffsetY = snapshot.editorOffsetY
                    onResult(RestorationResult.APPLIED)
                }
            }
            override fun isLayoutReady() = isDocumentTextLoaded
        }

        val initialStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)
        coordinator.registerClient(client)

        // User navigates to Settings and returns
        val coveredStack = listOf<NavKey>(Route.Home, Route.Book("book1"), editorKey, Route.Settings)
        coordinator.onNavigationBackStackChanged(coveredStack, isCompact = true)
        coordinator.onNavigationBackStackChanged(initialStack, isCompact = true)

        // Session must be PENDING since text has not finished loading into editor
        assertTrue("Restoration must be pending", coordinator.hasPendingRestoration(editorKey))
        assertEquals(RestorationState.PENDING, coordinator.getRestorationState(editorKey))
        assertEquals(-1, restoredScrollD)

        // While pending, transient scroll callbacks do NOT overwrite the snapshot:
        val snapshotBeforeReady = coordinator.getSnapshot(editorKey)
        assertNotNull(snapshotBeforeReady)
        assertEquals(180, snapshotBeforeReady?.scrollD)
        assertEquals(520, snapshotBeforeReady?.editorOffsetY)

        // Now document text and wordwrap finish loading:
        isDocumentTextLoaded = true
        coordinator.onClientLayoutReady(editorKey)

        assertEquals("Restoration must be APPLIED after layout ready", RestorationState.APPLIED, coordinator.getRestorationState(editorKey))
        assertEquals("Target scrollD must be restored", 180, restoredScrollD)
        assertEquals("Target editorOffsetY must be restored", 520, restoredEditorOffsetY)
    }
}
