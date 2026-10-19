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
 * 4. Safe, non-lossy AST-driven chapter splitting on explicit chapter boundaries (never on H2..H6).
 * 5. Dynamic language identification and bidirectional (LTR/RTL) metadata separation.
 * 6. Dynamic author extraction with front matter sniffing and graceful fallback.
 * 7. Hierarchical EPUB 3 navigation document (nav.xhtml) with semantic landmarks and nested legacy NCX fallback.
 * 8. Two-title manuscript header and vector ornament embedding in chapter body.
 * 9. Missing vault image asset fallback callout boxes preventing manifest corruption.
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

    data class TocNode(
        val label: String,
        val target: String,
        val children: MutableList<TocNode> = mutableListOf()
    )

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val header = ManuscriptTitleHeader.from(note, config.ornamentId)
        val title = header.toTocLabel(fallback = note.name.ifBlank { "Untitled Manuscript" })
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

        // 2. Split into chapters on top-level boundaries only (never H2..H6)
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

        val firstBodyChapterIdx = chapters.indexOfFirst { !it.isFrontMatter }.let { if (it >= 0) it else 0 }

        // 7. Chapter XHTML documents with strict XHTML5 XML syntax
        for ((chapIdx, chap) in chapters.withIndex()) {
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

            val enableDropCaps = !isChapRtl && when (config.dropCapStyle) {
                DropCapStyle.DISABLED -> false
                DropCapStyle.OPENING_PARAGRAPH_ONLY -> (chapIdx == firstBodyChapterIdx)
                DropCapStyle.EVERY_CHAPTER -> !chap.isFrontMatter
            }

            val chapHtml = HtmlEmitter.emit(
                document = chapDoc,
                imageResolver = processImageSrc,
                docDirection = chapDir,
                enableDropCaps = enableDropCaps,
                addHeadingAnchors = true
            )

            // Two-title header and ornament embedding at the top of the body chapter
            val headerHtml = if (config.includeCoverTitle && chapIdx == firstBodyChapterIdx && header.isPresent) {
                val hasBoth = header.hasPrimary && header.hasSecondary
                val kickerHtml = if (hasBoth) """<p class="title-kicker">${MarkdownUtil.xmlEscape(header.primaryTitle)}</p>""" else ""
                val mainTitleText = when {
                    hasBoth -> header.secondaryTitle
                    header.hasSecondary -> header.secondaryTitle
                    header.hasPrimary -> header.primaryTitle
                    else -> title
                }
                val mainTitleHtml = """<h1 class="document-title">${MarkdownUtil.xmlEscape(mainTitleText)}</h1>"""
                val ornamentSvg = if (config.ornamentId != "none") {
                    val svg = ExportThemeMapper.renderOrnamentSvg(config.ornamentId)
                    if (svg.isNotBlank()) """<div class="manuscript-ornament" aria-hidden="true">$svg</div>""" else ""
                } else ""
                """
  <header class="manuscript-header">
    $kickerHtml
    $mainTitleHtml
    $ornamentSvg
  </header>
                """.trimIndent()
            } else ""

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
    $headerHtml
    $chapHtml
  </section>
</body>
</html>""".trimIndent()

            entries["OEBPS/${chap.filename}"] = chapterDoc.toByteArray(Charsets.UTF_8)
        }

        // 8. Build Hierarchical TOC Navigation Tree
        val tocRootNodes = mutableListOf<TocNode>()
        for ((chapIdx, chap) in chapters.withIndex()) {
            val isMainChapter = (chapIdx == firstBodyChapterIdx)
            val chapLabel = if (isMainChapter && header.isPresent) {
                header.toTocLabel(fallback = chap.title)
            } else {
                chap.title
            }
            val chapNode = TocNode(label = chapLabel, target = chap.filename)

            // Extract headings within the chapter to form a nested hierarchy
            val secTitle = header.secondaryTitle.trim()
            val priTitle = header.primaryTitle.trim()
            var currentH2: TocNode? = null
            var currentH3: TocNode? = null

            for ((nodeIdx, node) in chap.nodes.withIndex()) {
                if (node is MarkdownNode.BlockNode.Heading) {
                    val headingText = node.runs.joinToString("") { it.text }.trim()
                    // Duplicate suppression: Skip if heading in body matches manuscript secondary title or combined title
                    if (isMainChapter && (headingText.equals(secTitle, ignoreCase = true) ||
                                          headingText.equals(priTitle, ignoreCase = true) ||
                                          headingText.equals(chapLabel, ignoreCase = true) ||
                                          headingText.equals(chap.title, ignoreCase = true))) {
                        continue
                    }
                    val anchor = HtmlEmitter.generateHeadingSlug(headingText, nodeIdx)
                    val target = "${chap.filename}#$anchor"
                    val subNode = TocNode(label = headingText.ifBlank { "Section $nodeIdx" }, target = target)

                    when (node.level) {
                        1 -> {
                            // Secondary H1 within chapter
                            chapNode.children.add(subNode)
                            currentH2 = null
                            currentH3 = null
                        }
                        2 -> {
                            chapNode.children.add(subNode)
                            currentH2 = subNode
                            currentH3 = null
                        }
                        3 -> {
                            if (currentH2 != null) {
                                currentH2.children.add(subNode)
                            } else {
                                chapNode.children.add(subNode)
                            }
                            currentH3 = subNode
                        }
                        else -> {
                            if (currentH3 != null) {
                                currentH3.children.add(subNode)
                            } else if (currentH2 != null) {
                                currentH2.children.add(subNode)
                            } else {
                                chapNode.children.add(subNode)
                            }
                        }
                    }
                }
            }
            tocRootNodes.add(chapNode)
        }

        // 9. OEBPS/nav.xhtml (EPUB 3 Navigation Document with Nested Lists & Landmarks)
        fun buildNavList(nodes: List<TocNode>): String {
            if (nodes.isEmpty()) return ""
            val sb = StringBuilder("<ol>\n")
            for (n in nodes) {
                val safeLabel = MarkdownUtil.xmlEscape(n.label)
                if (n.children.isEmpty()) {
                    sb.append("      <li><a href=\"${n.target}\">$safeLabel</a></li>\n")
                } else {
                    sb.append("      <li>\n        <a href=\"${n.target}\">$safeLabel</a>\n")
                    sb.append(buildNavList(n.children).prependIndent("        "))
                    sb.append("      </li>\n")
                }
            }
            sb.append("    </ol>\n")
            return sb.toString()
        }

        val navItems = buildNavList(tocRootNodes)
        val firstBodyChapter = chapters.getOrNull(firstBodyChapterIdx) ?: chapters.first()
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
    $navItems  </nav>
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

        // 10. OEBPS/toc.ncx (Backwards compatibility for legacy e-readers with nested navPoints)
        var ncxOrderCounter = 1
        fun buildNcxPoints(nodes: List<TocNode>): String {
            val sb = StringBuilder()
            for (n in nodes) {
                val order = ncxOrderCounter++
                val safeLabel = MarkdownUtil.xmlEscape(n.label)
                sb.append("""    <navPoint id="navPoint-$order" playOrder="$order">
      <navLabel><text>$safeLabel</text></navLabel>
      <content src="${n.target}"/>
""")
                if (n.children.isNotEmpty()) {
                    sb.append(buildNcxPoints(n.children).prependIndent("  "))
                }
                sb.append("    </navPoint>\n")
            }
            return sb.toString()
        }

        val ncxNavPoints = buildNcxPoints(tocRootNodes)
        val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$uuid"/>
    <meta name="dtb:depth" content="3"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$titleXml</text></docTitle>
  <navMap>
$ncxNavPoints  </navMap>
</ncx>""".trimIndent()

        entries["OEBPS/toc.ncx"] = tocNcx.toByteArray(Charsets.UTF_8)

        // 11. OEBPS/content.opf (Package Document)
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

        // 12. Add embedded images
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
     * CRITICAL RULE: Splits on chapter boundaries:
     * - If document has multiple H1s, splits on H1 (H2..H6 stay inside chapter).
     * - If document has single/zero H1 and multiple H2s, splits on H2 (H3..H6 stay inside chapter).
     * - Otherwise keeps as a single chapter.
     */
    fun splitIntoChapters(
        ast: MarkdownNode.Document,
        fallbackTitle: String
    ): List<EpubChapter> {
        val h1Headings = ast.children.filterIsInstance<MarkdownNode.BlockNode.Heading>().filter { it.level == 1 }
        val h2Headings = ast.children.filterIsInstance<MarkdownNode.BlockNode.Heading>().filter { it.level == 2 }
        val h1Count = h1Headings.size
        val h2Count = h2Headings.size

        val splitLevel = when {
            h1Count > 1 -> 1
            h1Count == 0 && h2Count > 1 -> 2
            h1Count == 1 && h2Count > 1 -> {
                val h1Text = h1Headings.first().runs.joinToString("") { it.text }.trim()
                val isH1Chapter = Regex("""^(chapter|part|act|book)\b""", RegexOption.IGNORE_CASE).containsMatchIn(h1Text)
                val h2HasChapters = h2Headings.any { h2 ->
                    val text = h2.runs.joinToString("") { it.text }.trim()
                    Regex("""^(chapter|part|act|book)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
                }
                if (!isH1Chapter && h2HasChapters) 2 else null
            }
            else -> null
        }

        if (splitLevel == null) {
            val title = ast.children
                .filterIsInstance<MarkdownNode.BlockNode.Heading>()
                .firstOrNull { it.level == 1 }
                ?.runs?.joinToString("") { it.text }?.trim()
                ?: fallbackTitle

            return listOf(
                EpubChapter(
                    id = "chap_1",
                    filename = "chapter_1.xhtml",
                    title = title.ifBlank { fallbackTitle },
                    isFrontMatter = false,
                    nodes = ast.children
                )
            )
        }

        // Split at the determined splitLevel (1 or 2)
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
            val mimetypeBytes = entries["mimetype"] ?: "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimetypeBytes)
                this.crc = crc.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            for ((path, data) in entries) {
                if (path == "mimetype") continue
                val entry = ZipEntry(path).apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
