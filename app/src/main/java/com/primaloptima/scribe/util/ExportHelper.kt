package com.primaloptima.scribe.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportHelper {

    /**
     * Export a note to the requested format and open the system share sheet.
     * Must be called from the main thread for PDF (uses WebView).
     */
    fun shareNote(context: Context, note: Note, format: String) {
        when (format.lowercase()) {
            "txt"  -> shareText(context, note, "txt", "text/plain", MarkdownUtil.toPlainText(note.content))
            "md"   -> shareText(context, note, "md", "text/markdown", note.content)
            "html" -> shareText(context, note, "html", "text/html", fullHtml(context, note))
            "epub" -> shareBytes(context, note, "epub", "application/epub+zip", buildEpub(context, note))
            "docx" -> shareBytes(context, note, "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildDocx(context, note))
            "pdf"  -> printToPdf(context, note)
        }
    }

    /**
     * Saves the exported note directly to the device Downloads/Scribe directory.
     * Returns the created file Uri, or null on failure.
     */
    fun saveToStorage(context: Context, note: Note, format: String): Uri? {
        val safeName = sanitize(note.name)
        val ext = format.lowercase()
        val filename = "$safeName.$ext"

        if (ext == "pdf") {
            // PDF generation requires Android Print Framework / PrintDocumentAdapter
            printToPdf(context, note)
            return null
        }

        val (mime, bytes) = when (ext) {
            "txt"  -> "text/plain" to MarkdownUtil.toPlainText(note.content).toByteArray(Charsets.UTF_8)
            "md"   -> "text/markdown" to note.content.toByteArray(Charsets.UTF_8)
            "html" -> "text/html" to fullHtml(context, note).toByteArray(Charsets.UTF_8)
            "epub" -> "application/epub+zip" to buildEpub(context, note)
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to buildDocx(context, note)
            else   -> "text/plain" to note.content.toByteArray(Charsets.UTF_8)
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
     * Produces the raw byte array for a given export format.
     */
    fun exportBytes(context: Context, note: Note, format: String): ByteArray {
        return when (format.lowercase()) {
            "txt"  -> MarkdownUtil.toPlainText(note.content).toByteArray(Charsets.UTF_8)
            "md"   -> note.content.toByteArray(Charsets.UTF_8)
            "html" -> fullHtml(context, note).toByteArray(Charsets.UTF_8)
            "epub" -> buildEpub(context, note)
            "docx" -> buildDocx(context, note)
            else   -> note.content.toByteArray(Charsets.UTF_8)
        }
    }

    // ── Text formats ──────────────────────────────────────────────────────────

    private fun shareText(context: Context, note: Note, ext: String, mime: String,
                          content: String) {
        val file = writeCacheFile(context, sanitize(note.name) + ".$ext", content.toByteArray(Charsets.UTF_8))
        openShareSheet(context, file, mime)
    }

    private fun shareBytes(context: Context, note: Note, ext: String, mime: String, bytes: ByteArray) {
        val file = writeCacheFile(context, sanitize(note.name) + ".$ext", bytes)
        openShareSheet(context, file, mime)
    }

    // ── PDF via print framework with embedded base64 illustrations ─────────────

    fun printToPdf(context: Context, note: Note) {
        val htmlContent = fullHtml(context, note)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = sanitize(note.name)
                val printAdapter = view.createPrintDocumentAdapter(jobName)
                pm.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
    }

    // ── HTML with self-contained Base64 illustrations ─────────────────────────

    fun fullHtml(context: Context, note: Note): String {
        val body = MarkdownUtil.toHtmlBody(note.content) { src: String ->
            resolveImageToBase64(context, src, note.bookId, note.id) ?: src
        }
        return MarkdownUtil.fullHtmlDocument(note.name, body)
    }

    private fun resolveImageToBase64(context: Context, uri: String, bookId: String?, noteId: String): String? {
        val bytes = VaultImageManager.getImageBytes(context, uri, bookId, noteId) ?: return null
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mime = if (uri.endsWith(".png", true)) "image/png" else "image/jpeg"
        return "data:$mime;base64,$base64"
    }

    // ── EPUB with embedded illustrations ──────────────────────────────────────

    fun buildEpub(context: Context, note: Note): ByteArray {
        val title = MarkdownUtil.escapeHtml(note.name)
        val uuid = "scribe-${note.id}-${System.currentTimeMillis()}"

        // Extract and map all image tags
        val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")
        val imageEntries = mutableMapOf<String, ByteArray>()
        val manifestItems = mutableListOf<String>()
        var imageIndex = 1

        val processedBody = MarkdownUtil.toHtmlBody(note.content) { src: String ->
            val bytes = VaultImageManager.getImageBytes(context, src, note.bookId, note.id)
            if (bytes != null) {
                val imgFilename = "image_$imageIndex.jpg"
                imageEntries["OEBPS/images/$imgFilename"] = bytes
                manifestItems.add("""<item id="img_$imageIndex" href="images/$imgFilename" media-type="image/jpeg"/>""")
                imageIndex++
                "images/$imgFilename"
            } else {
                src
            }
        }

        val manifestExtra = if (manifestItems.isNotEmpty()) "\n    " + manifestItems.joinToString("\n    ") else ""

        val entries = mutableMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent().toByteArray(),
            "OEBPS/chapter1.xhtml" to """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
  <title>$title</title>
  <style>
    body { font-family: Georgia, serif; line-height: 1.6; margin: 5%; color: #222; }
    h1 { font-family: sans-serif; text-align: center; margin-bottom: 2em; }
    figure { text-align: center; margin: 2em 0; }
    img { max-width: 100%; height: auto; border-radius: 6px; }
    figcaption { font-size: 0.85em; color: #666; font-style: italic; margin-top: 6px; }
  </style>
</head>
<body>
  <h1>$title</h1>
  $processedBody
</body>
</html>""".trimIndent().toByteArray(),
            "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title>
    <dc:language>en</dc:language>
    <dc:identifier id="BookId">$uuid</dc:identifier>
    <dc:creator>Scribe</dc:creator>
    <meta property="dcterms:modified">2026-10-16T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>$manifestExtra
  </manifest>
  <spine toc="ncx">
    <itemref idref="chapter1"/>
  </spine>
</package>""".trimIndent().toByteArray(),
            "OEBPS/toc.ncx" to """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="$uuid"/></head>
  <docTitle><text>$title</text></docTitle>
  <navMap>
    <navPoint id="chapter1" playOrder="1">
      <navLabel><text>$title</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".trimIndent().toByteArray()
        )

        entries.putAll(imageEntries)
        return buildZip(entries, storeEntries = setOf("mimetype"))
    }

    // ── DOCX with embedded illustrations ──────────────────────────────────────

    fun buildDocx(context: Context, note: Note): ByteArray {
        val titleXml = MarkdownUtil.xmlEscape(note.name)
        val lines = note.content.lines()
        val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")

        val mediaEntries = mutableMapOf<String, ByteArray>()
        val rels = StringBuilder()
        val bodyParas = StringBuilder()

        var imgIdx = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                bodyParas.append("<w:p/>")
                continue
            }

            val imgMatch = imgRegex.find(trimmed)
            if (imgMatch != null) {
                val caption = imgMatch.groupValues[1].trim()
                val uri = imgMatch.groupValues[2].trim()
                val bytes = VaultImageManager.getImageBytes(context, uri, note.bookId, note.id)

                if (bytes != null) {
                    val mediaFilename = "image$imgIdx.jpeg"
                    val rId = "rIdImg$imgIdx"
                    mediaEntries["word/media/$mediaFilename"] = bytes
                    rels.append("""  <Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$mediaFilename"/>\n""")

                    // High-resolution inline drawing part (width ~ 5.5 inches = 5029200 EMUs)
                    bodyParas.append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="5029200" cy="3771900"/><wp:docPr id="$imgIdx" name="Picture $imgIdx"/><wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="$imgIdx" name="Picture $imgIdx"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rId" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5029200" cy="3771900"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")

                    if (caption.isNotBlank()) {
                        val captionXml = MarkdownUtil.xmlEscape(caption)
                        bodyParas.append("""<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:i/><w:sz w:val="20"/><w:color w:val="666666"/></w:rPr><w:t xml:space="preserve">$captionXml</w:t></w:r></w:p>""")
                    }
                    imgIdx++
                    continue
                }
            }

            // Standard Heading / Paragraph text
            val isHeading = trimmed.startsWith("#")
            val cleanText = MarkdownUtil.xmlEscape(trimmed.replace(Regex("^#+\\s*"), ""))
            if (isHeading) {
                val level = trimmed.takeWhile { it == "#"[0] }.length
                val style = if (level == 1) "Heading1" else if (level == 2) "Heading2" else "Heading3"
                bodyParas.append("""<w:p><w:pPr><w:pStyle w:val="$style"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">$cleanText</w:t></w:r></w:p>""")
            } else {
                bodyParas.append("""<w:p><w:r><w:t xml:space="preserve">$cleanText</w:t></w:r></w:p>""")
            }
        }

        val entries = mutableMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".trimIndent().toByteArray(),
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".trimIndent().toByteArray(),
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels</Relationships>""".trimIndent().toByteArray(),
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Title"/><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="48"/></w:rPr><w:t xml:space="preserve">$titleXml</w:t></w:r></w:p>
    $bodyParas
    <w:sectPr/>
  </w:body>
</w:document>""".trimIndent().toByteArray()
        )

        entries.putAll(mediaEntries)
        return buildZip(entries)
    }

    // ── ZIP helper ────────────────────────────────────────────────────────────

    private fun buildZip(entries: Map<String, ByteArray>, storeEntries: Set<String> = emptySet()): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                if (name in storeEntries) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    val crc = java.util.zip.CRC32()
                    crc.update(bytes)
                    entry.crc = crc.value
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    // ── File saving & share helpers ───────────────────────────────────────────

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

    private fun writeCacheFile(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    fun openShareSheet(context: Context, file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, file.name))
    }

    fun sanitize(name: String): String =
        (name.ifBlank { "Untitled" }).replace('\n', '-').replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
}
