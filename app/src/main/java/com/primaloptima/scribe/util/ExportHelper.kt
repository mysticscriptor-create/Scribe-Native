package com.primaloptima.scribe.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportCacheManager
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.HtmlPdfExporter
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Modernized coordinator for Scribe's multi-format export pipeline.
 * Delegates to dedicated modular engines ([DocxExporter], [EpubExporter], [HtmlPdfExporter])
 * while providing unified caching, SAF saving, and sharing workflows.
 */
object ExportHelper {

    /**
     * Legacy entry point: generates the document and attempts to save to Downloads.
     */
    fun exportToFile(context: Context, note: Note, ext: String): Uri? {
        val format = ExportFormat.fromId(ext)
        val config = ExportConfig(format = format)
        val safeName = sanitize(note.name)
        val isMdZip = format == ExportFormat.MD && VaultImageManager.hasImages(note.content)
        val finalExt = if (isMdZip) "zip" else ext
        val filename = "$safeName.$finalExt"

        val (mime, bytes) = when (format) {
            ExportFormat.TXT -> "text/plain" to MarkdownUtil.toPlainText(note.content).toByteArray(Charsets.UTF_8)
            ExportFormat.MD -> {
                if (isMdZip) {
                    "application/zip" to buildMarkdownZip(context, note)
                } else {
                    "text/markdown" to note.content.toByteArray(Charsets.UTF_8)
                }
            }
            ExportFormat.HTML -> "text/html" to fullHtml(context, note, config).toByteArray(Charsets.UTF_8)
            ExportFormat.EPUB -> "application/epub+zip" to buildEpub(context, note, config)
            ExportFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to buildDocx(context, note, config)
            ExportFormat.PDF -> {
                printToPdf(context, note, config)
                return null
            }
        }

        val uri = saveToDownloads(context, filename, mime, bytes)
        if (uri != null) {
            Toast.makeText(context, "Saved to Downloads/Scribe/$filename", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to save $filename", Toast.LENGTH_SHORT).show()
        }
        return uri
    }

    /**
     * Produces the raw byte array for a given export format and configuration.
     */
    fun exportBytes(
        context: Context,
        note: Note,
        formatStr: String,
        config: ExportConfig = ExportConfig()
    ): ByteArray {
        val format = ExportFormat.fromId(formatStr)
        val effectiveConfig = config.copy(format = format)
        return when (format) {
            ExportFormat.TXT -> MarkdownUtil.toPlainText(note.content).toByteArray(Charsets.UTF_8)
            ExportFormat.MD -> {
                if (VaultImageManager.hasImages(note.content)) {
                    buildMarkdownZip(context, note)
                } else {
                    note.content.toByteArray(Charsets.UTF_8)
                }
            }
            ExportFormat.HTML -> fullHtml(context, note, effectiveConfig).toByteArray(Charsets.UTF_8)
            ExportFormat.EPUB -> buildEpub(context, note, effectiveConfig)
            ExportFormat.DOCX -> buildDocx(context, note, effectiveConfig)
            ExportFormat.PDF -> {
                // For PDF, return the rendered HTML as UTF-8 fallback or empty bytes if invoked synchronously
                fullHtml(context, note, effectiveConfig).toByteArray(Charsets.UTF_8)
            }
        }
    }

    /**
     * Builds OpenXML (.docx) bytes using the modular [DocxExporter].
     */
    fun buildDocx(context: Context, note: Note, config: ExportConfig = ExportConfig()): ByteArray {
        return DocxExporter.export(context, note, config)
    }

    /**
     * Builds EPUB 3.0 publication bytes using the modular [EpubExporter].
     */
    fun buildEpub(context: Context, note: Note, config: ExportConfig = ExportConfig()): ByteArray {
        return EpubExporter.export(context, note, config)
    }

    /**
     * Builds styled HTML document using [HtmlPdfExporter].
     */
    fun fullHtml(context: Context, note: Note, config: ExportConfig = ExportConfig()): String {
        return HtmlPdfExporter.buildHtmlDocument(context, note, config)
    }

    /**
     * Builds a self-contained ZIP archive for Markdown manuscripts containing the markdown file
     * with relative image paths and an images/ directory with all asset binaries.
     */
    fun buildMarkdownZip(context: Context, note: Note): ByteArray {
        val safeName = sanitize(note.name)
        val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")
        val entries = mutableMapOf<String, ByteArray>()
        var imageIndex = 1

        val processedMd = imgRegex.replace(note.content) { mr ->
            val alt = mr.groupValues[1]
            val src = mr.groupValues[2]
            val bytes = VaultImageManager.getImageBytes(context, src, note.bookId, note.id)
            if (bytes != null) {
                val ext = if (src.endsWith(".png", true)) "png" else "jpg"
                val imgFilename = "image_$imageIndex.$ext"
                entries["images/$imgFilename"] = bytes
                imageIndex++
                "![$alt](images/$imgFilename)"
            } else {
                mr.value
            }
        }
        entries["$safeName.md"] = processedMd.toByteArray(Charsets.UTF_8)
        return buildZip(entries)
    }

    /**
     * Prints or saves PDF using Android print manager.
     */
    fun printToPdf(context: Context, note: Note, config: ExportConfig = ExportConfig()) {
        val safeName = sanitize(note.name)
        val tempPdf = ExportCacheManager.createPreviewFile(context, ".pdf")
        HtmlPdfExporter.renderPdfToFile(context, note, config, tempPdf) { success, _ ->
            if (success) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempPdf)
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "Open $safeName.pdf"))
                } catch (_: Throwable) {
                    Toast.makeText(context, "PDF generated in cache", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Shares the note in the specified format via Android Intent.ACTION_SEND.
     */
    fun shareNote(
        context: Context,
        note: Note,
        ext: String,
        config: ExportConfig = ExportConfig()
    ) {
        val format = ExportFormat.fromId(ext)
        val effectiveConfig = config.copy(format = format)
        val safeName = sanitize(note.name)

        if (format == ExportFormat.PDF) {
            val shareFile = ExportCacheManager.createPreviewFile(context, ".pdf")
            HtmlPdfExporter.renderPdfToFile(context, note, effectiveConfig, shareFile) { success, err ->
                if (success) {
                    openShareSheet(context, shareFile, "application/pdf")
                } else {
                    Toast.makeText(context, "Share failed: ${err?.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val isMdZip = format == ExportFormat.MD && VaultImageManager.hasImages(note.content)
        val finalExt = if (isMdZip) "zip" else ext
        val mime = if (isMdZip) "application/zip" else format.mimeType
        val bytes = exportBytes(context, note, ext, effectiveConfig)
        val file = ExportCacheManager.createShareFile(context, "$safeName.$finalExt", bytes)
        openShareSheet(context, file, mime)
    }

    /**
     * Opens the standard Android chooser share sheet.
     */
    fun openShareSheet(context: Context, file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    /**
     * Legacy direct save to Downloads/Scribe folder via MediaStore.
     */
    fun saveToDownloads(context: Context, filename: String, mime: String, bytes: ByteArray): Uri? {
        return try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scribe")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val insertUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (insertUri != null) {
                    resolver.openOutputStream(insertUri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(insertUri, values, null, null)
                }
                insertUri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Scribe")
                dir.mkdirs()
                val file = File(dir, filename)
                file.writeBytes(bytes)
                Uri.fromFile(file)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun sanitize(name: String): String =
        (name.ifBlank { "Untitled" }).replace('\n', '-').replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
}
