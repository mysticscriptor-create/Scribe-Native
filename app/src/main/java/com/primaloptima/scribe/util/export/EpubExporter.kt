package com.primaloptima.scribe.util.export

import android.content.Context
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Standards-compliant EPUB 3.0 publication engine with uncompressed mimetype,
 * full navigation documents, backwards-compatible NCX table of contents,
 * embedded styles, and chapter splitting.
 */
object EpubExporter {

    data class Chapter(
        val id: String,
        val title: String,
        val filename: String,
        val htmlContent: String
    )

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val title = note.name.ifBlank { "Untitled Manuscript" }
        val titleXml = MarkdownUtil.xmlEscape(title)
        val uuid = "urn:uuid:" + UUID.nameUUIDFromBytes(note.id.toByteArray()).toString()
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val imageEntries = mutableMapOf<String, ByteArray>()
        val imageManifest = mutableListOf<String>()
        var imageIndex = 1

        val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")

        fun processHtml(markdownText: String): String {
            return ScribeMarkdownEngine.toHtml(markdownText) { src: String ->
                val bytes = if (context != null) VaultImageManager.getImageBytes(context, src, note.bookId, note.id) else null
                if (bytes != null) {
                    val ext = if (src.endsWith(".png", true)) "png" else "jpg"
                    val imgFilename = "image_$imageIndex.$ext"
                    val mime = if (ext == "png") "image/png" else "image/jpeg"
                    imageEntries["OEBPS/images/$imgFilename"] = bytes
                    imageManifest.add("""    <item id="img_$imageIndex" href="images/$imgFilename" media-type="$mime"/>""")
                    imageIndex++
                    "images/$imgFilename"
                } else {
                    src
                }
            }
        }

        // Split chapters on H1 headings if multiple exist
        val chapters = splitIntoChapters(note.content, title, ::processHtml)

        val entries = mutableMapOf<String, ByteArray>()

        // 1. mimetype (MUST BE FIRST & STORED)
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        entries["mimetype"] = mimetypeBytes

        // 2. META-INF/container.xml
        entries["META-INF/container.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent().toByteArray(Charsets.UTF_8)

        // 3. OEBPS/styles.css
        val cssContent = ExportThemeMapper.generateExportCss(config, themeColors, isForPrintPdf = false)
        entries["OEBPS/styles.css"] = cssContent.toByteArray(Charsets.UTF_8)

        // 4. Chapter XHTML files
        for (chap in chapters) {
            val chapTitleXml = MarkdownUtil.xmlEscape(chap.title)
            val chapterDoc = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
<head>
  <meta charset="utf-8"/>
  <title>$chapTitleXml</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body epub:type="bodymatter">
  <section epub:type="chapter">
    ${chap.htmlContent}
  </section>
</body>
</html>""".trimIndent()
            entries["OEBPS/${chap.filename}"] = chapterDoc.toByteArray(Charsets.UTF_8)
        }

        // 5. OEBPS/nav.xhtml (EPUB 3 Navigation Document)
        val navItems = chapters.joinToString("\n") { chap ->
            """      <li><a href="${chap.filename}">${MarkdownUtil.xmlEscape(chap.title)}</a></li>"""
        }
        val navDoc = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
<head>
  <meta charset="utf-8"/>
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
$navItems
    </ol>
  </nav>
  <nav epub:type="landmarks" hidden="">
    <h2>Landmarks</h2>
    <ol>
      <li><a epub:type="toc" href="#toc">Table of Contents</a></li>
      <li><a epub:type="bodymatter" href="${chapters.first().filename}">Start of Content</a></li>
    </ol>
  </nav>
</body>
</html>""".trimIndent()
        entries["OEBPS/nav.xhtml"] = navDoc.toByteArray(Charsets.UTF_8)

        // 6. OEBPS/toc.ncx (Backwards compatibility for legacy e-readers)
        val ncxNavPoints = chapters.mapIndexed { idx, chap ->
            val order = idx + 1
            """    <navPoint id="navPoint-$order" playOrder="$order">
      <navLabel><text>${MarkdownUtil.xmlEscape(chap.title)}</text></navLabel>
      <content src="${chap.filename}"/>
    </navPoint>"""
        }.joinToString("\n")

        val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$uuid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$titleXml</text></docTitle>
  <navMap>
$ncxNavPoints
  </navMap>
</ncx>""".trimIndent()
        entries["OEBPS/toc.ncx"] = tocNcx.toByteArray(Charsets.UTF_8)

        // 7. OEBPS/content.opf (Package Document)
        val manifestChapters = chapters.joinToString("\n") { chap ->
            """    <item id="${chap.id}" href="${chap.filename}" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapters.joinToString("\n") { chap ->
            """    <itemref idref="${chap.id}"/>"""
        }
        val extraImagesManifest = if (imageManifest.isNotEmpty()) "\n" + imageManifest.joinToString("\n") else ""

        val opfDoc = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">$uuid</dc:identifier>
    <dc:title>$titleXml</dc:title>
    <dc:language>en</dc:language>
    <dc:creator>Scribe Author</dc:creator>
    <meta property="dcterms:modified">$isoDate</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="styles.css" media-type="text/css"/>
$manifestChapters$extraImagesManifest
  </manifest>
  <spine toc="ncx">
$spineItems
  </spine>
</package>""".trimIndent()
        entries["OEBPS/content.opf"] = opfDoc.toByteArray(Charsets.UTF_8)

        // Add embedded images
        entries.putAll(imageEntries)

        return buildEpubZip(entries)
    }

    private fun splitIntoChapters(
        content: String,
        fallbackTitle: String,
        htmlRenderer: (String) -> String
    ): List<Chapter> {
        val lines = content.lines()
        val h1Indices = mutableListOf<Pair<Int, String>>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("# ") && !line.startsWith("##")) {
                val h1Title = line.removePrefix("# ").trim()
                h1Indices.add(i to h1Title)
            }
        }

        if (h1Indices.size <= 1) {
            // Single chapter
            val chapTitle = h1Indices.firstOrNull()?.second ?: fallbackTitle
            return listOf(
                Chapter(
                    id = "chap_1",
                    title = chapTitle,
                    filename = "chapter_1.xhtml",
                    htmlContent = htmlRenderer(content)
                )
            )
        }

        // Multiple chapters
        val chapters = mutableListOf<Chapter>()
        for (k in h1Indices.indices) {
            val startLine = h1Indices[k].first
            val endLine = if (k + 1 < h1Indices.size) h1Indices[k + 1].first else lines.size
            val chapLines = lines.subList(startLine, endLine)
            val chapMarkdown = chapLines.joinToString("\n")
            val chapNumber = k + 1
            chapters.add(
                Chapter(
                    id = "chap_$chapNumber",
                    title = h1Indices[k].second,
                    filename = "chapter_$chapNumber.xhtml",
                    htmlContent = htmlRenderer(chapMarkdown)
                )
            )
        }
        return chapters
    }

    private fun buildEpubZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Crucial: mimetype MUST be stored first uncompressed
            val mimeBytes = entries["mimetype"] ?: "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimeBytes)
                this.crc = crc.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // Remaining entries compressed
            for ((name, bytes) in entries) {
                if (name == "mimetype") continue
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
