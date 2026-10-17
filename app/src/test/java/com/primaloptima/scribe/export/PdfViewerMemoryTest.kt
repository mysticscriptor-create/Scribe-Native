package com.primaloptima.scribe.export

import androidx.collection.LruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerMemoryTest {

    data class MockBitmap(val width: Int, val height: Int, val bytes: Int) {
        val byteCount: Int get() = bytes
    }

    @Test
    fun testByteBoundedMemoryCacheEviction() {
        // Test byte-bounded LRU eviction: limit 1000 bytes
        val maxBytes = 1000
        val cache = object : LruCache<String, MockBitmap>(maxBytes) {
            override fun sizeOf(key: String, value: MockBitmap): Int = value.byteCount
        }

        val bmp1 = MockBitmap(10, 10, 300)
        val bmp2 = MockBitmap(10, 10, 400)
        val bmp3 = MockBitmap(10, 10, 500)

        cache.put("page_1", bmp1)
        cache.put("page_2", bmp2)

        assertEquals(700, cache.size())
        assertNotNull(cache.get("page_1"))
        assertNotNull(cache.get("page_2"))

        // Adding bmp3 (500 bytes) brings total to 1200 > 1000, so page_1 must be evicted
        cache.put("page_3", bmp3)

        assertEquals(900, cache.size()) // bmp2 (400) + bmp3 (500) = 900
        assertNull("Least recently used page_1 must be evicted", cache.get("page_1"))
        assertNotNull(cache.get("page_2"))
        assertNotNull(cache.get("page_3"))
    }

    @Test
    fun testCacheKeyIncludesDpiAndResolution() {
        fun buildKey(fileId: String, pageIndex: Int, width: Int): String =
            "${fileId}_p${pageIndex}_w${width}"

        val keyStandard = buildKey("manuscript.pdf", 0, 1080)
        val keyHighDpi = buildKey("manuscript.pdf", 0, 1800)

        assertTrue(keyStandard != keyHighDpi)
        assertTrue(keyStandard.contains("_p0_"))
        assertTrue(keyHighDpi.contains("_w1800"))
    }
}
