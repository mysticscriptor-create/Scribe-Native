package com.primaloptima.scribe.util.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Storage and lifecycle manager for images inserted into Scribe documents.
 *
 * Architecture:
 * 1. Storage Location: internal sandbox storage:
 *    context.filesDir/vault_images/{bookId}/{noteId}/img_{noteId}_{timestamp}_{uuid}.jpg
 * 2. Markdown Reference: ![Alt Text](vault-img://img_{noteId}_{timestamp}_{uuid}.jpg) or https://...
 * 3. Downsampling & Safety: Multi-megapixel camera uploads are downsampled to max 2048px on import
 *    to preserve memory and maintain snappy rendering while keeping quality pristine.
 */
object VaultImageManager {

    private const val VAULT_SCHEME = "vault-img://"
    private const val MAX_DIMENSION = 2048
    private const val JPEG_QUALITY = 88

    data class ImageMetadata(
        val filename: String,
        val width: Int = 0,
        val height: Int = 0,
        val sizeBytes: Long = 0L,
        val isLocalVault: Boolean = false
    ) {
        val sizeFormatted: String
            get() = when {
                sizeBytes <= 0L -> ""
                sizeBytes < 1024L -> "$sizeBytes B"
                sizeBytes < 1024L * 1024L -> String.format("%.1f KB", sizeBytes / 1024.0)
                else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            }

        val dimensionsFormatted: String
            get() = if (width > 0 && height > 0) "${width}×${height}" else ""
    }

    /**
     * Resolves storage directory for a specific note.
     */
    fun getNoteImagesDir(context: Context, bookId: String?, noteId: String): File {
        val safeBookId = if (bookId.isNullOrBlank()) "global" else bookId.replace("/", "_")
        val safeNoteId = if (noteId.isBlank()) "unsorted" else noteId.replace("/", "_")
        val dir = File(context.filesDir, "vault_images/$safeBookId/$safeNoteId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Resolves an image URI string into a loadable model for Coil.
     * For vault-img:// URIs, resolves to the local File in the internal sandbox.
     * For http://, https://, or content:// URIs, returns the string or Uri as-is.
     */
    fun resolveModel(context: Context, uriString: String, bookId: String?, noteId: String): Any {
        val trimmed = uriString.trim()
        if (trimmed.startsWith(VAULT_SCHEME, ignoreCase = true)) {
            val filename = trimmed.substring(VAULT_SCHEME.length).trim()
            val primaryFile = File(getNoteImagesDir(context, bookId, noteId), filename)
            if (primaryFile.exists()) return primaryFile

            // Fallback search in entire vault_images directory in case note was moved
            val baseVaultDir = File(context.filesDir, "vault_images")
            val found = findFileRecursive(baseVaultDir, filename)
            if (found != null && found.exists()) return found

            return primaryFile
        }
        return trimmed
    }

    /**
     * Imports a user-selected image Uri (from Photo Picker / File Provider),
     * downsamples if large, and saves it into the note's vault storage.
     *
     * @return Pair of the saved File and its markdown vault URI (vault-img://filename).
     */
    suspend fun importLocalImage(
        context: Context,
        sourceUri: Uri,
        bookId: String?,
        noteId: String
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val targetDir = getNoteImagesDir(context, bookId, noteId)
        val timestamp = System.currentTimeMillis()
        val shortUuid = UUID.randomUUID().toString().take(6)
        val cleanNoteId = (if (noteId.isBlank()) "note" else noteId.take(8)).replace("/", "_")
        val filename = "img_${cleanNoteId}_${timestamp}_${shortUuid}.jpg"
        val destFile = File(targetDir, filename)

        // 1. Decode bounds & calculate inSampleSize
        var inSampleSize = 1
        var rotationDegrees = 0
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)
            }
        } catch (_: Throwable) {}

        // Check EXIF orientation
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Throwable) {}

        // 2. Decode sampled bitmap and apply rotation if needed
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var bitmap: Bitmap? = null
        context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
        }

        val finalBitmap = bitmap ?: throw IllegalStateException("Unable to decode selected image")

        val orientedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(finalBitmap, 0, 0, finalBitmap.width, finalBitmap.height, matrix, true)
            if (rotated != finalBitmap) {
                finalBitmap.recycle()
            }
            rotated
        } else {
            finalBitmap
        }

        // 3. Write to destination file
        FileOutputStream(destFile).use { out ->
            orientedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }
        orientedBitmap.recycle()

        destFile to "$VAULT_SCHEME$filename"
    }

    /**
     * Inspects image dimensions and file size.
     */
    fun getImageMetadata(context: Context, uriString: String, bookId: String?, noteId: String): ImageMetadata {
        val trimmed = uriString.trim()
        if (trimmed.startsWith(VAULT_SCHEME, ignoreCase = true)) {
            val filename = trimmed.substring(VAULT_SCHEME.length).trim()
            val resolved = resolveModel(context, trimmed, bookId, noteId)
            val file = resolved as? File
            if (file != null && file.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                return ImageMetadata(
                    filename = filename,
                    width = opts.outWidth,
                    height = opts.outHeight,
                    sizeBytes = file.length(),
                    isLocalVault = true
                )
            }
            return ImageMetadata(filename = filename, isLocalVault = true)
        } else {
            val filename = trimmed.substringAfterLast("/").substringBefore("?").ifBlank { "Remote Image" }
            return ImageMetadata(filename = filename, isLocalVault = false)
        }
    }

    /**
     * Updates alt description for an image URI in Markdown text.
     */
    fun renameAltInMarkdown(markdown: String, targetUri: String, newAlt: String): String {
        val escapedUri = Regex.escape(targetUri.trim())
        val regex = Regex("""!\[(.*?)\]\($escapedUri\)""")
        return regex.replace(markdown) {
            "![$newAlt](${targetUri.trim()})"
        }
    }

    /**
     * Removes an image markdown tag from document text.
     */
    fun removeImageFromMarkdown(markdown: String, targetUri: String): String {
        val escapedUri = Regex.escape(targetUri.trim())
        val regex = Regex("""\s*!\[(.*?)\]\($escapedUri\)\s*""")
        return regex.replace(markdown, "")
    }

    /**
     * Deletes the local vault image file from disk if applicable.
     */
    fun deleteVaultImageFile(context: Context, uriString: String, bookId: String?, noteId: String): Boolean {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith(VAULT_SCHEME, ignoreCase = true)) return false
        val resolved = resolveModel(context, trimmed, bookId, noteId)
        val file = resolved as? File ?: return false
        return if (file.exists()) file.delete() else false
    }

    /**
     * Shares image via standard Android Share Sheet using FileProvider.
     */
    fun shareImage(context: Context, uriString: String, bookId: String?, noteId: String, title: String) {
        val trimmed = uriString.trim()
        if (trimmed.startsWith(VAULT_SCHEME, ignoreCase = true)) {
            val resolved = resolveModel(context, trimmed, bookId, noteId)
            val file = resolved as? File
            if (file != null && file.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { file.name })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Image"))
                return
            }
        }

        // Fallback for web URLs
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, trimmed)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image Link"))
    }

    /**
     * Saves a local copy of the image into the system's Pictures / Gallery.
     */
    suspend fun saveToGallery(
        context: Context,
        uriString: String,
        bookId: String?,
        noteId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolved = resolveModel(context, uriString, bookId, noteId)
            val file = resolved as? File ?: return@withContext false
            if (!file.exists()) return@withContext false

            val filename = "Scribe_${System.currentTimeMillis()}_${file.name}"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scribe")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val insertUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            resolver.openOutputStream(insertUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(insertUri, values, null, null)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun findFileRecursive(dir: File, targetName: String): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        val files = dir.listFiles() ?: return null
        for (f in files) {
            if (f.isDirectory) {
                val found = findFileRecursive(f, targetName)
                if (found != null) return found
            } else if (f.name == targetName) {
                return f
            }
        }
        return null
    }
}
