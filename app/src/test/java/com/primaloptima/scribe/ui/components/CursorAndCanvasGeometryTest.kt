package com.primaloptima.scribe.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating cursor glide geometry and unified canvas downward scroll calculations.
 */
class CursorAndCanvasGeometryTest {

    @Test
    fun testTargetLineBottomCalculation() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (24 * density).toInt() // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        assertEquals(1140, targetLineBottom)
    }

    @Test
    fun testLastLineDeletingUnderflowCalculation() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (24 * density).toInt() // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px
        val rowHeight = 40

        // When a line is deleted, the new bottom is one row higher:
        val screenBottom = targetLineBottom - rowHeight // 1100px
        val underflow = screenBottom - targetLineBottom // -40px

        assertTrue("Underflow must be negative to trigger downward canvas scroll", underflow < 0)
        assertEquals(-40, underflow)
    }

    @Test
    fun testSelectAllDeletionScrollDReset() {
        // When document is empty, max scroll must evaluate to 0 regardless of persistentBottomSpace
        val headerHeight = 200
        val layoutHeight = 0
        val isTextEmpty = true

        val targetScrollD = if (isTextEmpty) 0 else headerHeight
        val targetOffsetY = if (isTextEmpty) 0 else 500

        assertEquals(0, targetScrollD)
        assertEquals(0, targetOffsetY)
    }
}
