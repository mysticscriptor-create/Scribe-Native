package com.primaloptima.scribe.util.export

import android.content.Context
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.ImageMimeUtil
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
 * Conformance & Architecture:
 * 1. Uncompressed US-ASCII mimetype at archive byte offset 0 with zero extra fields (STORED).
 * 2. Strict W3C EPUB 3.3 Recommendation targeting with package declaration version="3.0".
 * 3. Strict XHTML5 / XML conformance across all content documents with self-closing void tags.
 * 4. Safe, non-lossy AST-driven chapter splitting preserving pre-chapter front matter.
 * 5. Dynamic language identification and bidirectional (LTR/RTL) metadata separation.
 * 6. Dynamic author extraction with front matter sniffing and graceful fallback.
 * 7. EPUB 3 navigation document (nav.xhtml) with semantic landmarks and legacy NCX fallback.
 * 8. Missing vault image asset fallback callout boxes preventing manifest corruption.
 */
object EpubExporter {

    data class EpubChapter(
        val id: String,
        val filename: String,
        val title: String,
        val isFrontMatter: Boolean,
        val nodes: List<MarkdownNode.BlockNode>
    )

    data class LanguageMetadata(
        val primaryLanguage: String,
        val secondaryLanguages: List<String>,
        val documentDirection: String
    )

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val title = note.name.ifBlank { "Untitled Manuscript" }
        val titleXml = MarkdownUtil.xmlEscape(title)

        val authorName = resolveAuthorName(note, config)
        val authorXml = MarkdownUtil.xmlEscape(authorName)

        val uuid = "urn:uuid:" + UUID.nameUUIDFromBytes(note.id.toByteArray()).toString()
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val langMeta = detectLanguageAndDirection(note.content)

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
                    val detected = ImageMimeUtil.detect(bytes, src)
                    val ext = detected.extension
                    val mime = detected.mimeType
                    val imgFilename = "image_$imageIndex.$ext"
                    imageEntries["OEBPS/images/$imgFilename"] = bytes
                    imageManifest.add("""    <item id="img_$imageIndex" href="images/$imgFilename" media-type="$mime"/>""")
                    imageIndex++
                    "images/$imgFilename"
                } else {
                    "MISSING_ASSET"
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
        val cssContent = ExportThemeMapper.generateExportCss(config, themeColors, isForPrintPdf = false, isForEpub = true)
        entries["OEBPS/styles.css"] = cssContent.toByteArray(Charsets.UTF_8)

        // 7. Chapter XHTML documents with strict XHTML5 XML syntax
        for (chap in chapters) {
            val chapTitleXml = MarkdownUtil.xmlEscape(chap.title)
            val chapDoc = MarkdownNode.Document(chap.nodes)

            val bodyType = if (chap.isFrontMatter) "frontmatter" else "bodymatter"
            val sectionType = if (chap.isFrontMatter) "prologue" else "chapter"

            val chapRawText = chap.nodes.joinToString(" ") { node ->
                when (node) {
                    is MarkdownNode.BlockNode.Paragraph -> node.runs.joinToString("") { it.text }
                    is MarkdownNode.BlockNode.Heading -> node.runs.joinToString("") { it.text }
                    else -> ""
                }
            }
            val isChapRtl = HtmlEmitter.isRtlText(chapRawText)
            val chapLang = if (isChapRtl) {
                langMeta.secondaryLanguages.firstOrNull { it == "ur" || it == "ar" || it == "he" }
                    ?: if (langMeta.primaryLanguage == "ur" || langMeta.primaryLanguage == "ar" || langMeta.primaryLanguage == "he") langMeta.primaryLanguage else "ur"
            } else {
                if (langMeta.primaryLanguage == "ur" || langMeta.primaryLanguage == "ar" || langMeta.primaryLanguage == "he") {
                    langMeta.secondaryLanguages.firstOrNull { it == "en" } ?: "en"
                } else {
                    langMeta.primaryLanguage
                }
            }
            val chapDir = if (isChapRtl) "rtl" else langMeta.documentDirection
            val chapHtml = HtmlEmitter.emit(chapDoc, processImageSrc, docDirection = chapDir)

            val chapterDoc = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$chapLang" lang="$chapLang" dir="$chapDir">
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
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${langMeta.primaryLanguage}" lang="${langMeta.primaryLanguage}" dir="${langMeta.documentDirection}">
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
  <nav epub:type="landmarks" hidden="hidden">
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

        val secondaryLangXml = langMeta.secondaryLanguages
            .filter { it != langMeta.primaryLanguage }
            .joinToString("\n") { """    <dc:language>$it</dc:language>""" }
        val secondaryLangBlock = if (secondaryLangXml.isNotEmpty()) "\n$secondaryLangXml" else ""

        val opfDoc = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">$uuid</dc:identifier>
    <dc:title>$titleXml</dc:title>
    <dc:language>${langMeta.primaryLanguage}</dc:language>$secondaryLangBlock
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
     * Extracts dynamic author pen name from [config], with fallback to manuscript front matter,
     * and a final graceful default of 'Scribe Author'.
     */
    fun resolveAuthorName(note: Note, config: ExportConfig): String {
        if (config.authorName.isNotBlank()) {
            return config.authorName.trim()
        }
        val lines = note.content.lineSequence().take(15)
        for (line in lines) {
            val trimmed = line.trim()
            val prefixes = listOf("author:", "by:", "author -", "author —")
            for (p in prefixes) {
                if (trimmed.startsWith(p, ignoreCase = true)) {
                    val candidate = trimmed.substring(p.length).trim().trim('-', '—', '*', '_')
                    if (candidate.isNotBlank() && candidate.length in 2..50) {
                        return candidate
                    }
                }
            }
        }
        return "Scribe Author"
    }

    /**
     * Determines the primary and secondary language codes and document-level directionality.
     * Separates language tags from RTL directionality so mixed-script manuscripts are correctly decorated.
     */
    fun detectLanguageAndDirection(content: String): LanguageMetadata {
        var arabicCount = 0
        var urduSpecificCount = 0
        var hebrewCount = 0
        var latinCount = 0

        for (ch in content) {
            val code = ch.code
            when {
                // Urdu-specific letters: ٹ, ڈ, ڑ, ں, ے, ہ, ھ, چ, پ, ژ, گ
                code in listOf(0x0679, 0x0688, 0x0691, 0x06BA, 0x06D2, 0x06C1, 0x06BE, 0x0686, 0x067E, 0x0698, 0x06AF) -> {
                    urduSpecificCount++
                    arabicCount++
                }
                // Arabic Unicode blocks
                code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF -> {
                    arabicCount++
                }
                // Hebrew Unicode blocks
                code in 0x0590..0x05FF || code in 0xFB1D..0xFB4F -> {
                    hebrewCount++
                }
                // Latin Unicode blocks
                code in 0x0041..0x005A || code in 0x0061..0x007A || code in 0x00C0..0x024F -> {
                    latinCount++
                }
            }
        }

        val totalRtl = arabicCount + hebrewCount
        val isPrimaryRtl = totalRtl > latinCount

        val primaryLang = when {
            isPrimaryRtl && urduSpecificCount > 0 -> "ur"
            isPrimaryRtl && arabicCount > 0 -> "ar"
            isPrimaryRtl && hebrewCount > 0 -> "he"
            else -> "en"
        }

        val secondary = mutableListOf<String>()
        if (primaryLang == "en") {
            if (urduSpecificCount > 0) secondary.add("ur")
            else if (arabicCount > 0) secondary.add("ar")
            if (hebrewCount > 0) secondary.add("he")
        } else {
            if (latinCount > 0) secondary.add("en")
        }

        val docDirection = if (isPrimaryRtl) "rtl" else "ltr"

        return LanguageMetadata(
            primaryLanguage = primaryLang,
            secondaryLanguages = secondary.distinct(),
            documentDirection = docDirection
        )
    }

    /**
     * Safely splits unified AST nodes into individual chapter containers.
     * Guarantees 0% data loss by isolating any pre-chapter prose into [frontmatter.xhtml].
     * Supports manuscripts with H1 headings or H2 headings.
     */
    fun splitIntoChapters(
        ast: MarkdownNode.Document,
        fallbackTitle: String
    ): List<EpubChapter> {
        val h1Count = ast.children.count { it is MarkdownNode.BlockNode.Heading && it.level == 1 }
        val h2Count = ast.children.count { it is MarkdownNode.BlockNode.Heading && it.level == 2 }
        val splitLevel = when {
            h1Count > 1 -> 1
            h2Count > 1 -> 2
            h1Count == 1 -> 1
            h2Count == 1 -> 2
            else -> null
        }

        if (splitLevel == null) {
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
        val currentNodes = mutableListOf<MarkdownNode.BlockNode>()
        var actualChapterIdx = 1

        for (node in ast.children) {
            if (node is MarkdownNode.BlockNode.Heading && node.level == splitLevel) {
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
                currentTitle = node.runs.joinToString("") { it.text }.trim().ifBlank { "Chapter $actualChapterIdx" }
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
