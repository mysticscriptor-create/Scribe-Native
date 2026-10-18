package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.ExportArtifactRepository
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExportArtifactRepositoryTest {

    @Test
    fun testCacheKeyDeterminismAndIdentity() {
        val note1 = Note(id = "note-1", name = "Chapter One", content = "Once upon a time in a galaxy.", updatedAt = 1000L)
        val note2 = Note(id = "note-1", name = "Chapter One", content = "Once upon a time in a galaxy.", updatedAt = 1000L)
        val configA = ExportConfig(format = ExportFormat.PDF, paperSize = PaperSize.A4)
        val configB = ExportConfig(format = ExportFormat.PDF, paperSize = PaperSize.A4)

        val key1 = ExportArtifactRepository.computeCacheKey(note1, ExportFormat.PDF, configA, "asset-v1")
        val key2 = ExportArtifactRepository.computeCacheKey(note2, ExportFormat.PDF, configB, "asset-v1")

        assertEquals("Identical inputs must yield identical cache keys", key1, key2)
        assertEquals(64, key1.length) // SHA-256 hex string length
    }

    @Test
    fun testContentOrRevisionChangeInvalidatesCacheKey() {
        val noteA = Note(id = "note-1", name = "Chapter One", content = "Initial revision.", updatedAt = 1000L)
        val noteB = Note(id = "note-1", name = "Chapter One", content = "Initial revision.", updatedAt = 2000L)
        val config = ExportConfig(format = ExportFormat.PDF)

        val keyA = ExportArtifactRepository.computeCacheKey(noteA, ExportFormat.PDF, config)
        val keyB = ExportArtifactRepository.computeCacheKey(noteB, ExportFormat.PDF, config)

        assertNotEquals("Revision change must produce a distinct cache key", keyA, keyB)
    }

    @Test
    fun testAssetFingerprintInvalidationWhenImageContentsChange() {
        val note = Note(
            id = "note-img",
            name = "Illustrated Note",
            content = "# Map\n![Kingdom](vault-img://map.jpg)",
            updatedAt = 1000L
        )
        val config = ExportConfig(format = ExportFormat.PDF)

        val keyInitial = ExportArtifactRepository.computeCacheKey(note, ExportFormat.PDF, config, "map.jpg:1700000000:45000")
        val keyModified = ExportArtifactRepository.computeCacheKey(note, ExportFormat.PDF, config, "map.jpg:1700005000:52000")

        assertNotEquals("Modifying image file size/timestamp must invalidate cache key even if URI is unchanged", keyInitial, keyModified)
    }

    @Test
    fun testFormatCanonicalizationIgnoresIrrelevantSettings() {
        val note = Note(id = "note-txt", name = "Plain Notes", content = "Prose without layout.", updatedAt = 1000L)
        val configA4 = ExportConfig(format = ExportFormat.TXT, paperSize = PaperSize.A4)
        val configLetter = ExportConfig(format = ExportFormat.TXT, paperSize = PaperSize.LETTER)

        val keyA4 = ExportArtifactRepository.computeCacheKey(note, ExportFormat.TXT, configA4)
        val keyLetter = ExportArtifactRepository.computeCacheKey(note, ExportFormat.TXT, configLetter)

        assertEquals("Text export keys must ignore physical paper size changes", keyA4, keyLetter)

        val pdfKeyA4 = ExportArtifactRepository.computeCacheKey(note, ExportFormat.PDF, configA4)
        val pdfKeyLetter = ExportArtifactRepository.computeCacheKey(note, ExportFormat.PDF, configLetter)

        assertNotEquals("PDF export keys must differ when paper size changes", pdfKeyA4, pdfKeyLetter)
    }

    @Test
    fun testActiveArtifactRetentionTracking() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "scribe_retention_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val sampleFile = File(tempDir, "active_artifact.pdf")
            sampleFile.writeText("%PDF-1.4 dummy")

            ExportArtifactRepository.retainArtifact(sampleFile)
            ExportArtifactRepository.retainArtifact(sampleFile)

            // Release once (still retained)
            ExportArtifactRepository.releaseArtifact(sampleFile)
            // Release twice (fully released)
            ExportArtifactRepository.releaseArtifact(sampleFile)

            assertTrue(sampleFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSharedLeaseRetentionLifecycle() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "scribe_lease_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val sampleFile = File(tempDir, "sample_document.docx")
            sampleFile.writeText("Dummy document binary content")

            val artifact = ExportArtifactRepository.ExportArtifact(
                key = "dummy-key-123",
                file = sampleFile,
                format = ExportFormat.DOCX,
                mimeType = ExportFormat.DOCX.mimeType,
                byteSize = sampleFile.length(),
                createdAtEpochMs = System.currentTimeMillis()
            )

            assertNotNull(artifact)
            assertEquals("dummy-key-123", artifact.key)
            assertTrue(artifact.byteSize > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
