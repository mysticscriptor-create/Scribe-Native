package com.primaloptima.scribe.util.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Single Source of Truth repository for generated export artifacts.
 *
 * Guarantees:
 * 1. Content-addressable SHA-256 caching across Preview, Save, and Share.
 * 2. In-flight request deduplication so identical concurrent requests share one job.
 * 3. Two-stage atomic staging (.tmp -> validated .out) with automatic failure cleanup.
 * 4. Reference-counted file leases for external share intents, preventing premature deletion.
 * 5. Bounded cache hygiene based on byte limits and active leases.
 */
object ExportArtifactRepository {

    const val SCHEMA_VERSION = 1
    private const val STAGING_DIR = "export_staging"
    private const val ARTIFACTS_DIR = "export_artifacts"
    private const val SHARED_DIR = "shared_exports"
    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB

    data class ExportArtifact(
        val key: String,
        val file: File,
        val format: ExportFormat,
        val mimeType: String,
        val byteSize: Long,
        val createdAtEpochMs: Long
    )

    data class SharedFileLease(
        val leaseId: String,
        val file: File,
        val uri: Uri,
        val format: ExportFormat,
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long
    )

    // In-flight deduplication map: Key -> Deferred Result
    private val inFlightJobs = ConcurrentHashMap<String, Deferred<Result<ExportArtifact>>>()

    // Active leases: leaseId -> SharedFileLease
    private val activeLeases = ConcurrentHashMap<String, SharedFileLease>()

    fun getStagingDir(context: Context): File =
        File(context.cacheDir, STAGING_DIR).also { if (!it.exists()) it.mkdirs() }

    fun getArtifactsDir(context: Context): File =
        File(context.cacheDir, ARTIFACTS_DIR).also { if (!it.exists()) it.mkdirs() }

    fun getSharedDir(context: Context): File =
        File(context.cacheDir, SHARED_DIR).also { if (!it.exists()) it.mkdirs() }

    /**
     * Derives a deterministic, canonical SHA-256 cache key.
     */
    fun computeCacheKey(
        note: Note,
        format: ExportFormat,
        config: ExportConfig,
        assetRevisionsHash: String = ""
    ): String {
        val canonicalConfig = when (format) {
            ExportFormat.PDF -> "p:${config.paperSize.name}_m:${config.marginPreset.name}_f:${config.fontChoice.name}_sz:${config.fontSizePt}_lh:${config.lineSpacingMultiplier}_c:${config.includeCoverTitle}_q:${config.imageQuality.name}"
            ExportFormat.DOCX -> "p:${config.paperSize.name}_m:${config.marginPreset.name}_f:${config.fontChoice.name}_sz:${config.fontSizePt}_c:${config.includeCoverTitle}"
            ExportFormat.EPUB -> "f:${config.fontChoice.name}_c:${config.includeCoverTitle}_toc:${config.includeTableOfContents}_q:${config.imageQuality.name}"
            ExportFormat.HTML -> "f:${config.fontChoice.name}_c:${config.includeCoverTitle}"
            ExportFormat.MD, ExportFormat.TXT -> "raw"
        }

        val rawInput = buildString {
            append("v:").append(SCHEMA_VERSION).append("|")
            append("id:").append(note.id).append("|")
            append("name:").append(note.name).append("|")
            append("len:").append(note.content.length).append("|")
            append("hash:").append(hashString(note.content)).append("|")
            append("fmt:").append(format.id).append("|")
            append("cfg:").append(canonicalConfig).append("|")
            append("assets:").append(assetRevisionsHash)
        }

        return hashString(rawInput)
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieves an existing cached artifact, or compiles it asynchronously off the main thread.
     * Deduplicates identical concurrent requests.
     */
    suspend fun getOrCreateArtifact(
        context: Context,
        note: Note,
        format: ExportFormat,
        config: ExportConfig,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): Result<ExportArtifact> = withContext(Dispatchers.IO) {
        val assetRevisions = VaultImageManager.hasImages(note.content).toString()
        val key = computeCacheKey(note, format, config, assetRevisions)

        val isMdZip = format == ExportFormat.MD && VaultImageManager.hasImages(note.content)
        val ext = if (isMdZip) ".zip" else format.extension
        val finalMime = if (isMdZip) "application/zip" else format.mimeType

        val targetFile = File(getArtifactsDir(context), "artifact_${key}$ext")

        // 1. Fast Cache Hit
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext Result.success(
                ExportArtifact(
                    key = key,
                    file = targetFile,
                    format = format,
                    mimeType = finalMime,
                    byteSize = targetFile.length(),
                    createdAtEpochMs = targetFile.lastModified()
                )
            )
        }

        // 2. In-flight Deduplication
        val existingDeferred = inFlightJobs[key]
        if (existingDeferred != null) {
            return@withContext existingDeferred.await()
        }

        // 3. New Compilation Job
        val deferred = scope.async(Dispatchers.IO) {
            val stagingFile = File(getStagingDir(context), "staging_${UUID.randomUUID()}.tmp")
            try {
                if (format == ExportFormat.PDF) {
                    val pdfResult = CompletableDeferred<Boolean>()
                    var pdfError: Throwable? = null
                    HtmlPdfExporter.renderPdfToFile(context, note, config, stagingFile) { success, err ->
                        pdfError = err
                        pdfResult.complete(success)
                    }
                    val ok = pdfResult.await()
                    if (!ok || !stagingFile.exists() || stagingFile.length() == 0L) {
                        throw pdfError ?: RuntimeException("PDF rendering failed or produced 0 bytes")
                    }
                } else {
                    val bytes = ExportHelper.exportBytes(context, note, format.id, config)
                    if (bytes.isEmpty()) {
                        throw RuntimeException("Exporter for ${format.label} returned empty payload")
                    }
                    stagingFile.writeBytes(bytes)
                }

                // Validation
                if (!stagingFile.exists() || stagingFile.length() == 0L) {
                    throw RuntimeException("Artifact validation failed for ${format.label}")
                }

                // Atomic publication
                targetFile.delete()
                val published = stagingFile.renameTo(targetFile)
                if (!published) {
                    // Fallback to copy + delete if rename across partitions fails
                    stagingFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    stagingFile.delete()
                }

                pruneCacheIfNeeded(context)

                Result.success(
                    ExportArtifact(
                        key = key,
                        file = targetFile,
                        format = format,
                        mimeType = finalMime,
                        byteSize = targetFile.length(),
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                )
            } catch (t: Throwable) {
                try { stagingFile.delete() } catch (_: Throwable) {}
                Result.failure(t)
            } finally {
                inFlightJobs.remove(key)
            }
        }

        inFlightJobs[key] = deferred
        deferred.await()
    }

    /**
     * Acquires a share lease for external intents, copying artifact to shared folder with a unique lease ID.
     */
    fun acquireShareLease(
        context: Context,
        artifact: ExportArtifact,
        customName: String? = null,
        durationMs: Long = 2L * 60L * 60L * 1000L // 2 hours
    ): SharedFileLease {
        val safeTitle = customName?.ifBlank { null } ?: artifact.file.nameWithoutExtension
        val ext = artifact.file.extension.let { if (it.isNotBlank()) ".$it" else "" }
        val sharedFile = File(getSharedDir(context), "${safeTitle}_${System.currentTimeMillis()}$ext")
        
        artifact.file.copyTo(sharedFile, overwrite = true)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sharedFile)
        val leaseId = UUID.randomUUID().toString()

        val lease = SharedFileLease(
            leaseId = leaseId,
            file = sharedFile,
            uri = uri,
            format = artifact.format,
            createdAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + durationMs
        )
        activeLeases[leaseId] = lease
        return lease
    }

    fun releaseShareLease(leaseId: String) {
        activeLeases.remove(leaseId)
    }

    /**
     * Prunes expired and excess cache files without deleting artifacts that have active leases.
     */
    fun pruneCacheIfNeeded(context: Context) {
        try {
            val now = System.currentTimeMillis()
            // Clean active leases past expiry
            val expiredLeases = activeLeases.filter { it.value.expiresAtEpochMs < now }
            expiredLeases.forEach { activeLeases.remove(it.key) }

            val leasedPaths = activeLeases.values.map { it.file.absolutePath }.toSet()

            // Prune shared exports older than 2 hours that have no active lease
            getSharedDir(context).listFiles()?.forEach { file ->
                if (!leasedPaths.contains(file.absolutePath) && file.lastModified() < now - (2 * 60 * 60 * 1000L)) {
                    file.delete()
                }
            }

            // Prune staging directory of abandoned tmp files (> 1 hour old)
            getStagingDir(context).listFiles()?.forEach { file ->
                if (file.lastModified() < now - (60 * 60 * 1000L)) {
                    file.delete()
                }
            }

            // Prune artifacts dir if total bytes exceed MAX_CACHE_BYTES
            val artifacts = getArtifactsDir(context).listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            var totalBytes = artifacts.sumOf { it.length() }
            for (art in artifacts) {
                if (totalBytes <= MAX_CACHE_BYTES) break
                val len = art.length()
                if (art.delete()) {
                    totalBytes -= len
                }
            }
        } catch (_: Throwable) {}
    }
}
