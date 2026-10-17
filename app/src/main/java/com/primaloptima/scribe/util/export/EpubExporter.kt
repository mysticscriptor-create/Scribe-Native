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
 * Modern standards-compliant EPUB 3.3 publication engine for Scribe.
 *
 * Conformance:
 * 1. Uncompressed US-ASCII mimetype at archive byte offset 0 with zero extra fields.
 * 2. Strict XHTML5 / XML conformance across all content documents with self-closing void tags.
 * 3. Safe, non-lossy AST-driven chapter splitting preserving pre-chapter front matter.
 * 4. EPUB 3 navigation document (nav.xhtml) with semantic landmarks and legacy NCX fallback.
 * 5. Dynamic author profile, UUID identifier, and ISO-8601 UTC timestamp metadata.
 */
object EpubExporter {

    data class EpubChapter(
        val id: String,
        val filename: String,
        val title: String,
        val isFrontMatter: Boolean,
        val nodes: List<MarkdownNode.BlockNode>
    )

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val title = note.name.ifBlank { "Untitled Manuscript" }
        val titleXml = MarkdownUtil.xmlEscape(title)
        val authorName = config.authorName.ifBlank { "Scribe Author" }
        val authorXml = MarkdownUtil.xmlEscape(authorName)
        val uuid = "urn:uuid:" + UUID.nameUUIDFromBytes(note.id.toByteArray()).toString()
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        // 1. Parse markdown source into unified AST
        val ast = ScribeMarkdownAstParser.parse(note.content)

        // 2. Split into chapters with zero data loss guarantee
        val chapters = splitIntoChapters(ast, title)

        // 3. Resolve images across chapters
        val imageEntries = mutableMapOf<String, ByteArray>()
        val imageManifest = mutableListOf<String>()
        val imageCache = mutableMapOf<String, String>()
        var imageIndex = 1

        val processImageSrc: (String) -> String = { src ->
            imageCache.getOrPut(src) {
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

        val entries = mutableMapOf<String, ByteArray>()

        // 4. mimetype (MUST BE FIRST & STORED)
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        entries["mimetype"] = mimetypeBytes

        // 5. META-INF/container.xml
        entries["META-INF/container.xml"] = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent().toByteArray(Charsets.UTF_8)

        // 6. OEBPS/styles.css
        val cssContent = ExportThemeMapper.generateExportCss(config, themeColors, isForPrintPdf = false)
        entries["OEBPS/styles.css"] = cssContent.toByteArray(Charsets.UTF_8)

        // 7. Chapter XHTML documents with strict XHTML5 XML syntax
        for (chap in chapters) {
            val chapTitleXml = MarkdownUtil.xmlEscape(chap.title)
            val chapDoc = MarkdownNode.Document(chap.nodes)
            val chapHtml = HtmlEmitter.emit(chapDoc, processImageSrc)
            val bodyType = if (chap.isFrontMatter) "frontmatter" else "bodymatter"
            val sectionType = if (chap.isFrontMatter) "prologue" else "chapter"

            val chapterDoc = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
<head>
  <meta charset="utf-8"/>
  <title>$chapTitleXml</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body epub:type="$bodyType">
  <section epub:type="$sectionType">
    $chapHtml
  </section>
</body>
</html>""".trimIndent()
            entries["OEBPS/${chap.filename}"] = chapterDoc.toByteArray(Charsets.UTF_8)
        }

        // 8. OEBPS/nav.xhtml (EPUB 3 Navigation Document with TOC & Landmarks)
        val navItems = chapters.joinToString("\n") { chap ->
            """      <li><a href="${chap.filename}">${MarkdownUtil.xmlEscape(chap.title)}</a></li>"""
        }
        val firstBodyChapter = chapters.firstOrNull { !it.isFrontMatter } ?: chapters.first()

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
      <li><a epub:type="bodymatter" href="${firstBodyChapter.filename}">Start of Content</a></li>
    </ol>
  </nav>
</body>
</html>""".trimIndent()
        entries["OEBPS/nav.xhtml"] = navDoc.toByteArray(Charsets.UTF_8)

        // 9. OEBPS/toc.ncx (Backwards compatibility for legacy e-readers)
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

        // 10. OEBPS/content.opf (Package Document)
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
    <dc:creator id="author">$authorXml</dc:creator>
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

        // 11. Add embedded images
        entries.putAll(imageEntries)

        return buildEpubZip(entries)
    }

    /**
     * Safely splits unified AST nodes into individual chapter containers.
     * Guarantees 0% data loss by isolating any pre-chapter prose into [frontmatter.xhtml].
     */
    fun splitIntoChapters(
        ast: MarkdownNode.Document,
        fallbackTitle: String
    ): List<EpubChapter> {
        val h1Nodes = ast.children.filterIsInstance<MarkdownNode.BlockNode.Heading>().filter { it.level == 1 }

        if (h1Nodes.isEmpty()) {
            return listOf(
                EpubChapter(
                    id = "chap_1",
                    filename = "chapter_1.xhtml",
                    title = fallbackTitle,
                    isFrontMatter = false,
                    nodes = ast.children
                )
            )
        }

        val chapters = mutableListOf<EpubChapter>()
        var currentTitle = "Front Matter"
        var currentNodes = mutableListOf<MarkdownNode.BlockNode>()
        var actualChapterIdx = 1

        for (node in ast.children) {
            if (node is MarkdownNode.BlockNode.Heading && node.level == 1) {
                if (currentNodes.isNotEmpty()) {
                    val isFrontMatter = chapters.isEmpty() && currentTitle == "Front Matter"
                    val id = if (isFrontMatter) "frontmatter" else "chap_$actualChapterIdx"
                    val filename = if (isFrontMatter) "frontmatter.xhtml" else "chapter_$actualChapterIdx.xhtml"
                    chapters.add(
                        EpubChapter(
                            id = id,
                            filename = filename,
                            title = currentTitle,
                            isFrontMatter = isFrontMatter,
                            nodes = currentNodes.toList()
                        )
                    )
                    if (!isFrontMatter) {
                        actualChapterIdx++
                    }
                    currentNodes.clear()
                }
                currentTitle = node.runs.joinToString("") { it.text }.ifBlank { "Chapter $actualChapterIdx" }
            }
            currentNodes.add(node)
        }

        if (currentNodes.isNotEmpty()) {
            val isFrontMatter = chapters.isEmpty() && currentTitle == "Front Matter"
            val id = if (isFrontMatter) "frontmatter" else "chap_$actualChapterIdx"
            val filename = if (isFrontMatter) "frontmatter.xhtml" else "chapter_$actualChapterIdx.xhtml"
            chapters.add(
                EpubChapter(
                    id = id,
                    filename = filename,
                    title = currentTitle,
                    isFrontMatter = isFrontMatter,
                    nodes = currentNodes.toList()
                )
            )
        }

        return chapters
    }

    private fun buildEpubZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Crucial: mimetype MUST be stored first uncompressed with zero extra fields
            val mimeBytes = entries["mimetype"] ?: "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimeBytes)
                this.crc = crc.value
                extra = null
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // Remaining entries compressed
            for ((name, bytes) in entries) {
                if (name == "mimetype") continue
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
