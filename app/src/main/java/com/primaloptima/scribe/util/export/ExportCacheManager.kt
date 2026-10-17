package com.primaloptima.scribe.util.export

import android.content.Context
import java.io.File

/**
 * Legacy compatibility façade delegating to [ExportArtifactRepository]
 * for safe, bounded cache hygiene and file retention.
 */
object ExportCacheManager {

    fun getExportsDir(context: Context): File =
        ExportArtifactRepository.getSharedDir(context)

    fun getPreviewsDir(context: Context): File =
        ExportArtifactRepository.getStagingDir(context)

    fun createPreviewFile(context: Context, extension: String): File {
        val dir = getPreviewsDir(context)
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return File(dir, "staging_${System.currentTimeMillis()}$ext")
    }

    fun createShareFile(context: Context, filename: String, bytes: ByteArray): File {
        val dir = getExportsDir(context)
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return file
    }

    fun cleanupOldExports(context: Context, maxAgeHours: Long = 24) {
        ExportArtifactRepository.pruneCacheIfNeeded(context)
    }

    /**
     * Safe clear: does NOT delete files that have active leases.
     */
    fun clearPreviewCache(context: Context) {
        ExportArtifactRepository.pruneCacheIfNeeded(context)
    }

    fun clearAll(context: Context) {
        ExportArtifactRepository.pruneCacheIfNeeded(context)
    }
}
