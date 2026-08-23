package com.primaloptima.scribe.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object WorldImageUtil {

    enum class AspectRatio(val label: String, val ratioWidth: Float, val ratioHeight: Float) {
        PORTRAIT_3_4("Portrait (3:4)", 3f, 4f),
        PORTRAIT_2_3("Portrait (2:3)", 2f, 3f),
        LANDSCAPE_16_9("Landscape (16:9)", 16f, 9f),
        LANDSCAPE_4_3("Landscape (4:3)", 4f, 3f),
        SQUARE_1_1("Square (1:1)", 1f, 1f),
        FREE("Original / Free", 0f, 0f);

        val ratio: Float
            get() = if (ratioHeight > 0f) ratioWidth / ratioHeight else 1f
    }

    /**
     * Loads and downsamples a Bitmap from a Uri safely on IO dispatcher.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Decode bounds only
                var input: InputStream? = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                input?.close()

                val origWidth = options.outWidth
                val origHeight = options.outHeight
                if (origWidth <= 0 || origHeight <= 0) return@withContext null

                // Step 2: Calculate inSampleSize
                var inSampleSize = 1
                while (origWidth / inSampleSize > maxDimension || origHeight / inSampleSize > maxDimension) {
                    inSampleSize *= 2
                }

                // Step 3: Decode scaled bitmap
                input = context.contentResolver.openInputStream(uri)
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
                input?.close()
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Saves a cropped/adjusted Bitmap to internal app storage under `files/world_images/`
     * and returns the file URI string.
     */
    suspend fun saveCroppedBitmap(context: Context, entryId: String, bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val worldDir = File(context.filesDir, "world_images").apply { mkdirs() }
                val destFile = File(worldDir, "world_${entryId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                // Clean up previous image files for this entry
                worldDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("world_${entryId}_") && file != destFile) {
                        file.delete()
                    }
                }

                Uri.fromFile(destFile).toString()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }

    /**
     * Inspects image dimensions to determine whether it's landscape (w > h * 1.1) or portrait.
     */
    fun isLandscapeImage(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val input = if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()
            options.outWidth > (options.outHeight * 1.1f)
        } catch (_: Exception) {
            false
        }
    }
}
