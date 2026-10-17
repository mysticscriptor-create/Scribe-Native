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
        val note1 = Note(id = "note-1", name = "Chapter One", content = "Once upon a time in a galaxy.")
        val note2 = Note(id = "note-1", name = "Chapter One", content = "Once upon a time in a galaxy.")

        val configA = ExportConfig(format = ExportFormat.PDF, paperSize = PaperSize.A4)
        val configB = ExportConfig(format = ExportFormat.PDF, paperSize = PaperSize.A4)

        val key1 = ExportArtifactRepository.computeCacheKey(note1, ExportFormat.PDF, configA)
        val key2 = ExportArtifactRepository.computeCacheKey(note2, ExportFormat.PDF, configB)

        assertEquals("Identical inputs must yield identical cache keys", key1, key2)
        assertEquals(64, key1.length) // SHA-256 hex string length
    }

    @Test
    fun testContentChangeInvalidatesCacheKey() {
        val noteA = Note(id = "note-1", name = "Chapter One", content = "Initial revision.")
        val noteB = Note(id = "note-1", name = "Chapter One", content = "Revised content with edits.")

        val config = ExportConfig(format = ExportFormat.PDF)

        val keyA = ExportArtifactRepository.computeCacheKey(noteA, ExportFormat.PDF, config)
        val keyB = ExportArtifactRepository.computeCacheKey(noteB, ExportFormat.PDF, config)

        assertNotEquals("Content modification must produce a distinct cache key", keyA, keyB)
    }

    @Test
    fun testFormatCanonicalizationIgnoresIrrelevantSettings() {
        val note = Note(id = "note-txt", name = "Plain Notes", content = "Prose without layout.")

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
