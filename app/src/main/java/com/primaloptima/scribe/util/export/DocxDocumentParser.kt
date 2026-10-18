package com.primaloptima.scribe.util.export

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class DocxRunModel(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val colorHex: String? = null,
    val fontName: String? = null,
    val isHyperlink: Boolean = false,
    val hyperlinkUrl: String? = null
)

sealed class DocxBlockElement {
    data class Paragraph(
        val runs: List<DocxRunModel>,
        val style: String? = null,
        val isBullet: Boolean = false,
        val isOrdered: Boolean = false,
        val ilvl: Int = 0,
        val isRtl: Boolean = false,
        val alignment: String = "left",
        val isBlockquote: Boolean = false,
        val isCodeBlock: Boolean = false,
        val isThematicBreak: Boolean = false,
        val isEditorialCallout: Boolean = false
    ) : DocxBlockElement()

    data class Table(
        val rows: List<List<DocxTableCellModel>>
    ) : DocxBlockElement()

    data class DrawingImage(
        val relId: String,
        val imageBytes: ByteArray? = null,
        val caption: String? = null
    ) : DocxBlockElement() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DrawingImage
            if (relId != other.relId) return false
            if (caption != other.caption) return false
            if (imageBytes != null) {
                if (other.imageBytes == null) return false
                if (!imageBytes.contentEquals(other.imageBytes)) return false
            } else if (other.imageBytes != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = relId.hashCode()
            result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
            result = 31 * result + (caption?.hashCode() ?: 0)
            return result
        }
    }
}

data class DocxTableCellModel(
    val paragraphs: List<DocxBlockElement.Paragraph>,
    val isHeader: Boolean = false,
    val alignment: String = "left"
)

data class ParsedDocxDocument(
    val title: String? = null,
    val elements: List<DocxBlockElement>,
    val mediaEntries: Map<String, ByteArray> = emptyMap()
)

/**
 * Standard-compliant pure Kotlin OpenXML (.docx) document parser.
 * Extracts paragraphs, headings, multi-level lists (ilvl 0..8), tables,
 * drawings, embedded media, and editorial callout boxes directly from word/document.xml.
 */
object DocxDocumentParser {

    fun parseFromZip(zipFile: ZipFile): ParsedDocxDocument {
        // 1. Parse word/_rels/document.xml.rels
        val relationships = mutableMapOf<String, String>()
        val relsEntry = zipFile.getEntry("word/_rels/document.xml.rels")
        if (relsEntry != null) {
            zipFile.getInputStream(relsEntry).use { stream ->
                parseRelationships(stream, relationships)
            }
        }

        // 2. Extract media entries
        val mediaMap = mutableMapOf<String, ByteArray>()
        for ((rId, target) in relationships) {
            val normalizedPath = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
            val mediaEntry = zipFile.getEntry(normalizedPath)
            if (mediaEntry != null) {
                val bytes = zipFile.getInputStream(mediaEntry).use { it.readBytes() }
                mediaMap[rId] = bytes
            }
        }

        // 3. Parse word/document.xml
        val docEntry = zipFile.getEntry("word/document.xml")
            ?: return ParsedDocxDocument(elements = emptyList())

        return zipFile.getInputStream(docEntry).use { stream ->
            parseDocumentXml(stream, relationships, mediaMap)
        }
    }

    fun parseRelationships(stream: InputStream, outMap: MutableMap<String, String>) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(stream)
            val relNodes = doc.getElementsByTagName("Relationship")
            for (i in 0 until relNodes.length) {
                val el = relNodes.item(i) as? Element ?: continue
                val id = el.getAttribute("Id")
                val target = el.getAttribute("Target")
                if (id.isNotBlank() && target.isNotBlank()) {
                    outMap[id] = target
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback
        }
    }

    fun parseDocumentXml(
        stream: InputStream,
        relationships: Map<String, String> = emptyMap(),
        mediaMap: Map<String, ByteArray> = emptyMap()
    ): ParsedDocxDocument {
        val elements = mutableListOf<DocxBlockElement>()
        var extractedTitle: String? = null

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(stream)

        // Find body element
        val bodyNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "body")
        val body = if (bodyNodes.length > 0) bodyNodes.item(0) as? Element else doc.documentElement ?: return ParsedDocxDocument(elements = emptyList())

        val childNodes = body.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            val local = element.localName ?: element.tagName.substringAfter(":")

            when (local) {
                "p" -> {
                    // Check if paragraph contains drawing image
                    val drawing = parseDrawing(element, relationships, mediaMap)
                    if (drawing != null) {
                        elements.add(drawing)
                    } else {
                        val parsedPara = parseParagraph(element, relationships)
                        if (parsedPara != null) {
                            if (parsedPara.style.equals("Title", ignoreCase = true) && extractedTitle == null) {
                                extractedTitle = parsedPara.runs.joinToString("") { it.text }
                            }
                            elements.add(parsedPara)
                        }
                    }
                }
                "tbl" -> {
                    val table = parseTable(element, relationships)
                    if (table != null) {
                        elements.add(table)
                    }
                }
            }
        }

        return ParsedDocxDocument(
            title = extractedTitle,
            elements = elements,
            mediaEntries = mediaMap
        )
    }

    private fun parseParagraph(
        pElement: Element,
        relationships: Map<String, String>
    ): DocxBlockElement.Paragraph? {
        var style: String? = null
        var isBullet = false
        var isOrdered = false
        var ilvl = 0
        var isRtl = false
        var alignment = "left"
        var isBlockquote = false
        var isCodeBlock = false
        var isThematicBreak = false
        var isEditorialCallout = false

        // 1. Inspect pPr
        val pPr = getFirstChildElement(pElement, "pPr")
        if (pPr != null) {
            val pStyle = getFirstChildElement(pPr, "pStyle")
            if (pStyle != null) {
                style = pStyle.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                if (style.isBlank()) style = pStyle.getAttribute("w:val")
                if (style.equals("Blockquote", ignoreCase = true) || style.equals("Quote", ignoreCase = true)) {
                    isBlockquote = true
                }
            }

            val numPr = getFirstChildElement(pPr, "numPr")
            if (numPr != null) {
                val ilvlEl = getFirstChildElement(numPr, "ilvl")
                val numIdEl = getFirstChildElement(numPr, "numId")
                val ilvlStr = ilvlEl?.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                    ?.ifBlank { ilvlEl.getAttribute("w:val") }
                val numIdStr = numIdEl?.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                    ?.ifBlank { numIdEl.getAttribute("w:val") }

                ilvl = ilvlStr?.toIntOrNull() ?: 0
                if (numIdStr == "2") {
                    isOrdered = true
                } else {
                    isBullet = true
                }
            }

            val jc = getFirstChildElement(pPr, "jc")
            if (jc != null) {
                val jcVal = jc.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                    .ifBlank { jc.getAttribute("w:val") }
                if (jcVal.isNotBlank()) alignment = jcVal.lowercase()
            }

            if (getFirstChildElement(pPr, "bidi") != null) {
                isRtl = true
            }

            val pBdr = getFirstChildElement(pPr, "pBdr")
            if (pBdr != null) {
                val bottom = getFirstChildElement(pBdr, "bottom")
                if (bottom != null) {
                    isThematicBreak = true
                }
                val left = getFirstChildElement(pBdr, "left")
                if (left != null && !isBlockquote) {
                    isCodeBlock = true
                }
            }

            val shd = getFirstChildElement(pPr, "shd")
            if (shd != null) {
                val fill = shd.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fill")
                    .ifBlank { shd.getAttribute("w:fill") }
                if (fill.equals("F8F9FA", ignoreCase = true)) {
                    isEditorialCallout = true
                }
            }
        }

        // 2. Parse text runs & hyperlinks
        val runs = mutableListOf<DocxRunModel>()
        val pChildren = pElement.childNodes
        for (i in 0 until pChildren.length) {
            val c = pChildren.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val cEl = c as Element
            val local = cEl.localName ?: cEl.tagName.substringAfter(":")

            when (local) {
                "r" -> {
                    runs.addAll(parseRun(cEl))
                }
                "hyperlink" -> {
                    val rId = cEl.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        .ifBlank { cEl.getAttribute("r:id") }
                    val linkUrl = relationships[rId]
                    val hyperChildren = cEl.childNodes
                    for (j in 0 until hyperChildren.length) {
                        val hr = hyperChildren.item(j)
                        if (hr.nodeType == Node.ELEMENT_NODE && (hr.localName == "r" || hr.nodeName.endsWith(":r"))) {
                            val rModels = parseRun(hr as Element)
                            for (rm in rModels) {
                                runs.add(rm.copy(isHyperlink = true, hyperlinkUrl = linkUrl))
                            }
                        }
                    }
                }
            }
        }

        if (runs.isEmpty() && isThematicBreak) {
            return DocxBlockElement.Paragraph(
                runs = emptyList(),
                isThematicBreak = true
            )
        }

        if (runs.isEmpty()) return null

        return DocxBlockElement.Paragraph(
            runs = runs,
            style = style,
            isBullet = isBullet,
            isOrdered = isOrdered,
            ilvl = ilvl,
            isRtl = isRtl,
            alignment = alignment,
            isBlockquote = isBlockquote,
            isCodeBlock = isCodeBlock,
            isThematicBreak = isThematicBreak,
            isEditorialCallout = isEditorialCallout
        )
    }

    private fun parseRun(rElement: Element): List<DocxRunModel> {
        val runs = mutableListOf<DocxRunModel>()
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var colorHex: String? = null
        var fontName: String? = null

        val rPr = getFirstChildElement(rElement, "rPr")
        if (rPr != null) {
            if (getFirstChildElement(rPr, "b") != null) isBold = true
            if (getFirstChildElement(rPr, "i") != null) isItalic = true
            if (getFirstChildElement(rPr, "u") != null) isUnderline = true
            val color = getFirstChildElement(rPr, "color")
            if (color != null) {
                colorHex = color.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                    .ifBlank { color.getAttribute("w:val") }
            }
            val rFonts = getFirstChildElement(rPr, "rFonts")
            if (rFonts != null) {
                fontName = rFonts.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "ascii")
                    .ifBlank { rFonts.getAttribute("w:ascii") }
            }
        }

        val textSb = StringBuilder()
        val rChildren = rElement.childNodes
        for (i in 0 until rChildren.length) {
            val c = rChildren.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val cEl = c as Element
            val local = cEl.localName ?: cEl.tagName.substringAfter(":")
            if (local == "t") {
                textSb.append(cEl.textContent)
            } else if (local == "br") {
                textSb.append("\n")
            }
        }

        if (textSb.isNotEmpty()) {
            runs.add(
                DocxRunModel(
                    text = textSb.toString(),
                    isBold = isBold,
                    isItalic = isItalic,
                    isUnderline = isUnderline,
                    colorHex = colorHex,
                    fontName = fontName
                )
            )
        }

        return runs
    }

    private fun parseDrawing(
        pElement: Element,
        relationships: Map<String, String>,
        mediaMap: Map<String, ByteArray>
    ): DocxBlockElement.DrawingImage? {
        val drawings = pElement.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "drawing")
        val drawingNode = if (drawings.length > 0) drawings.item(0) as? Element else null ?: return null

        val blipNodes = drawingNode.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "blip")
        if (blipNodes.length == 0) return null
        val blip = blipNodes.item(0) as Element
        var rId = blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
        if (rId.isBlank()) rId = blip.getAttribute("r:embed")

        if (rId.isBlank()) return null
        val bytes = mediaMap[rId]

        return DocxBlockElement.DrawingImage(
            relId = rId,
            imageBytes = bytes
        )
    }

    private fun parseTable(
        tblElement: Element,
        relationships: Map<String, String>
    ): DocxBlockElement.Table? {
        val rows = mutableListOf<List<DocxTableCellModel>>()
        val trNodes = tblElement.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tr")

        for (r in 0 until trNodes.length) {
            val tr = trNodes.item(r) as? Element ?: continue
            val rowCells = mutableListOf<DocxTableCellModel>()
            val tcNodes = tr.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tc")

            for (c in 0 until tcNodes.length) {
                val tc = tcNodes.item(c) as? Element ?: continue
                var isHeader = false
                var alignment = "left"

                val tcPr = getFirstChildElement(tc, "tcPr")
                if (tcPr != null) {
                    val shd = getFirstChildElement(tcPr, "shd")
                    if (shd != null) {
                        val fill = shd.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fill")
                            .ifBlank { shd.getAttribute("w:fill") }
                        if (fill.equals("F2F2F2", ignoreCase = true)) isHeader = true
                    }
                }

                val pList = mutableListOf<DocxBlockElement.Paragraph>()
                val pNodes = tc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p")
                for (pIdx in 0 until pNodes.length) {
                    val p = pNodes.item(pIdx) as? Element ?: continue
                    val parsedP = parseParagraph(p, relationships)
                    if (parsedP != null) {
                        pList.add(parsedP)
                        if (parsedP.alignment.isNotBlank()) alignment = parsedP.alignment
                    }
                }

                rowCells.add(
                    DocxTableCellModel(
                        paragraphs = pList,
                        isHeader = isHeader || r == 0,
                        alignment = alignment
                    )
                )
            }
            if (rowCells.isNotEmpty()) {
                rows.add(rowCells)
            }
        }

        if (rows.isEmpty()) return null
        return DocxBlockElement.Table(rows = rows)
    }

    private fun getFirstChildElement(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val l = el.localName ?: el.tagName.substringAfter(":")
                if (l.equals(localName, ignoreCase = true)) {
                    return el
                }
            }
        }
        return null
    }
}
