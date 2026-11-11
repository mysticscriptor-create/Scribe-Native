package com.primaloptima.scribe.viewmodel

import com.primaloptima.scribe.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DocumentOpenCacheTest {

    @Before
    fun setUp() {
        DocumentOpenCache.clear()
    }

    @Test
    fun testPutAndGet() {
        val note = Note(id = "n1", name = "Test Note", content = "Content 1")
        DocumentOpenCache.put(note)
        val cached = DocumentOpenCache.get("n1")
        assertNotNull(cached)
        assertEquals("Content 1", cached?.content)
    }

    @Test
    fun testInvalidateRemovesEntry() {
        val note = Note(id = "n1", name = "Test Note", content = "Content 1")
        DocumentOpenCache.put(note)
        DocumentOpenCache.invalidate("n1")
        assertNull(DocumentOpenCache.get("n1"))
    }

    @Test
    fun testLruEvictionRetainsAtMost4Entries() {
        val n1 = Note(id = "n1", name = "1", content = "1")
        val n2 = Note(id = "n2", name = "2", content = "2")
        val n3 = Note(id = "n3", name = "3", content = "3")
        val n4 = Note(id = "n4", name = "4", content = "4")
        val n5 = Note(id = "n5", name = "5", content = "5")

        DocumentOpenCache.put(n1)
        DocumentOpenCache.put(n2)
        DocumentOpenCache.put(n3)
        DocumentOpenCache.put(n4)
        DocumentOpenCache.put(n5)

        // n1 should have been evicted as eldest
        assertNull(DocumentOpenCache.get("n1"))
        assertNotNull(DocumentOpenCache.get("n2"))
        assertNotNull(DocumentOpenCache.get("n5"))
    }
}
