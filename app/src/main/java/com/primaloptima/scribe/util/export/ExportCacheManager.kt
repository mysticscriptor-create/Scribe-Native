package com.primaloptima.scribe.util.export

import android.content.Context
import java.io.File

/**
 * Manages transient files for previews and sharing, guaranteeing cache hygiene
 * and automatic eviction of stale export files.
 */
object ExportCacheManager {

    private const val EXPORTS_DIR = "exports"
    private const val PREVIEWS_DIR = "export_preview"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun getExportsDir(context: Context): File {
        return File(context.cacheDir, EXPORTS_DIR).also { if (!it.exists()) it.mkdirs() }
    }

    fun getPreviewsDir(context: Context): File {
        return File(context.cacheDir, PREVIEWS_DIR).also { if (!it.exists()) it.mkdirs() }
    }

    /**
     * Allocates a temporary preview file in a dedicated preview cache directory.
     */
    fun createPreviewFile(context: Context, extension: String): File {
        val dir = getPreviewsDir(context)
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return File(dir, "preview_${System.currentTimeMillis()}$ext")
    }

    /**
     * Writes binary payload to a designated share file and automatically cleans up old shares.
     */
    fun createShareFile(context: Context, filename: String, bytes: ByteArray): File {
        cleanupOldExports(context)
        val dir = getExportsDir(context)
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return file
    }

    /**
     * Deletes temporary export and preview files older than [maxAgeHours].
     */
    fun cleanupOldExports(context: Context, maxAgeHours: Long = 24) {
        val cutoff = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
        listOf(getExportsDir(context), getPreviewsDir(context)).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        try {
                            file.delete()
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    /**
     * Purges all cached preview files immediately.
     */
    fun clearPreviewCache(context: Context) {
        val dir = getPreviewsDir(context)
        if (dir.exists()) {
            dir.listFiles()?.forEach {
                try {
                    it.delete()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Completely cleans both export and preview caches.
     */
    fun clearAll(context: Context) {
        listOf(getExportsDir(context), getPreviewsDir(context)).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach {
                    try {
                        it.delete()
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}
