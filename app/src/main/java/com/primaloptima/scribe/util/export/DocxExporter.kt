package com.primaloptima.scribe.util.export

import android.content.Context
import android.graphics.BitmapFactory
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Modern standards-compliant OpenXML DOCX exporter for Scribe.
 * Produces structured Microsoft Word (.docx) documents complete with OpenXML styles,
 * numbering definitions for lists, tables, drawings, blockquotes, and headings.
 */
object DocxExporter {

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val fontName = ExportThemeMapper.resolveDocxFontName(config.fontChoice)
        val primaryColorHex = ExportThemeMapper.cleanHexForDocx(themeColors.accent, "1A5276")
        val textColorHex = ExportThemeMapper.cleanHexForDocx(themeColors.text, "222222")

        val ast = MarkdownAstParser.parse(note.content)

        val mediaEntries = mutableMapOf<String, ByteArray>()
        val relsXml = StringBuilder()
        var imageIndex = 1

        // Standard relationships
        relsXml.append("""  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>${"\n"}""")
        relsXml.append("""  <Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>${"\n"}""")

        val bodyXml = StringBuilder()

        // 1. Cover / Title Paragraph if configured
        if (config.includeCoverTitle) {
            val titleEscaped = MarkdownUtil.xmlEscape(note.name.ifBlank { "Untitled Manuscript" })
            bodyXml.append("""
            <w:p>
              <w:pPr>
                <w:pStyle w:val="Title"/>
                <w:jc w:val="center"/>
                <w:spacing w:before="720" w:after="360"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="48"/>
                  <w:color w:val="$primaryColorHex"/>
                </w:rPr>
                <w:t xml:space="preserve">$titleEscaped</w:t>
              </w:r>
            </w:p>
            """.trimIndent()).append("\n")
        }

        // 2. Iterate AST Nodes
        for (node in ast.nodes) {
            when (node) {
                is MarkdownAstParser.AstNode.HeadingNode -> {
                    val styleVal = when (node.level) {
                        1 -> "Heading1"
                        2 -> "Heading2"
                        else -> "Heading3"
                    }
                    val headingRuns = buildRunsXml(node.runs, defaultBold = true)
                    bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:pStyle w:val="$styleVal"/>
                      </w:pPr>
                      $headingRuns
                    </w:p>
                    """.trimIndent()).append("\n")
                }

                is MarkdownAstParser.AstNode.ParagraphNode -> {
                    val runsXml = buildRunsXml(node.runs)
                    bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:spacing w:after="160" w:line="320" w:lineRule="auto"/>
                      </w:pPr>
                      $runsXml
                    </w:p>
                    """.trimIndent()).append("\n")
                }

                is MarkdownAstParser.AstNode.ListNode -> {
                    val numId = if (node.isOrdered) "2" else "1"
                    for (item in node.items) {
                        val itemRuns = buildRunsXml(item.runs)
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:numPr>
                              <w:ilvl w:val="0"/>
                              <w:numId w:val="$numId"/>
                            </w:numPr>
                            <w:spacing w:after="80"/>
                          </w:pPr>
                          $itemRuns
                        </w:p>
                        """.trimIndent()).append("\n")
                    }
                }

                is MarkdownAstParser.AstNode.BlockquoteNode -> {
                    for (child in node.children) {
                        val childRuns = when (child) {
                            is MarkdownAstParser.AstNode.ParagraphNode -> buildRunsXml(child.runs, defaultItalic = true)
                            is MarkdownAstParser.AstNode.HeadingNode -> buildRunsXml(child.runs, defaultItalic = true, defaultBold = true)
                            else -> ""
                        }
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:pStyle w:val="Blockquote"/>
                            <w:pBdr>
                              <w:left w:val="single" w:sz="18" w:space="12" w:color="$primaryColorHex"/>
                            </w:pBdr>
                            <w:ind w:left="720"/>
                            <w:spacing w:after="120"/>
                          </w:pPr>
                          $childRuns
                        </w:p>
                        """.trimIndent()).append("\n")
                    }
                }

                is MarkdownAstParser.AstNode.DividerNode -> {
                    bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:pBdr>
                          <w:bottom w:val="single" w:sz="6" w:space="8" w:color="CCCCCC"/>
                        </w:pBdr>
                        <w:spacing w:before="240" w:after="240"/>
                      </w:pPr>
                    </w:p>
                    """.trimIndent()).append("\n")
                }

                is MarkdownAstParser.AstNode.TableNode -> {
                    bodyXml.append(buildTableXml(node, primaryColorHex))
                }

                is MarkdownAstParser.AstNode.CodeBlockNode -> {
                    val lines = node.code.lines()
                    for (codeLine in lines) {
                        val codeEscaped = MarkdownUtil.xmlEscape(codeLine)
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:pBdr>
                              <w:left w:val="single" w:sz="12" w:space="8" w:color="BBBBBB"/>
                            </w:pBdr>
                            <w:shd w:val="clear" w:color="auto" w:fill="F5F5F5"/>
                            <w:ind w:left="360" w:right="360"/>
                            <w:spacing w:after="40" w:line="240" w:lineRule="auto"/>
                          </w:pPr>
                          <w:r>
                            <w:rPr>
                              <w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/>
                              <w:sz w:val="20"/>
                            </w:rPr>
                            <w:t xml:space="preserve">$codeEscaped</w:t>
                          </w:r>
                        </w:p>
                        """.trimIndent()).append("\n")
                    }
                }

                is MarkdownAstParser.AstNode.ImageNode -> {
                    val bytes = if (context != null) VaultImageManager.getImageBytes(context, node.uri, note.bookId, note.id) else null
                    if (bytes != null) {
                        val rId = "rIdImg$imageIndex"
                        val ext = if (node.uri.endsWith(".png", true)) "png" else "jpeg"
                        val mediaPath = "media/image_$imageIndex.$ext"
                        mediaEntries["word/$mediaPath"] = bytes

                        relsXml.append("""  <Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$mediaPath"/>${"\n"}""")

                        val (cx, cy) = calculateEmuDimensions(bytes)
                        val captionEscaped = MarkdownUtil.xmlEscape(node.caption)

                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:jc w:val="center"/>
                            <w:spacing w:before="240" w:after="80"/>
                          </w:pPr>
                          <w:r>
                            <w:drawing>
                              <wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="$imageIndex" name="Image $imageIndex"/>
                                <wp:cNvGraphicFramePr>
                                  <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                                </wp:cNvGraphicFramePr>
                                <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                                  <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                    <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                      <pic:nvPicPr>
                                        <pic:cNvPr id="$imageIndex" name="Image $imageIndex"/>
                                        <pic:cNvPicPr/>
                                      </pic:nvPicPr>
                                      <pic:blipFill>
                                        <a:blip r:embed="$rId" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                                        <a:stretch><a:fillRect/></a:stretch>
                                      </pic:blipFill>
                                      <pic:spPr>
                                        <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                                      </pic:spPr>
                                    </pic:pic>
                                  </a:graphicData>
                                </a:graphic>
                              </wp:inline>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        """.trimIndent()).append("\n")

                        if (captionEscaped.isNotBlank()) {
                            bodyXml.append("""
                            <w:p>
                              <w:pPr>
                                <w:jc w:val="center"/>
                                <w:spacing w:after="240"/>
                              </w:pPr>
                              <w:r>
                                <w:rPr>
                                  <w:i/>
                                  <w:sz w:val="18"/>
                                  <w:color w:val="666666"/>
                                </w:rPr>
                                <w:t xml:space="preserve">$captionEscaped</w:t>
                              </w:r>
                            </w:p>
                            """.trimIndent()).append("\n")
                        }
                        imageIndex++
                    }
                }

                else -> {}
            }
        }

        // Section Properties
        bodyXml.append("""
        <w:sectPr>
          <w:pgSz w:w="${config.paperSize.dxaWidth}" w:h="${config.paperSize.dxaHeight}"/>
          <w:pgMar w:top="${config.marginPreset.dxaTop}" w:right="${config.marginPreset.dxaRight}" w:bottom="${config.marginPreset.dxaBottom}" w:left="${config.marginPreset.dxaLeft}" w:header="720" w:footer="720" w:gutter="0"/>
        </w:sectPr>
        """.trimIndent())

        val entries = mutableMapOf<String, ByteArray>()

        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>""".trimIndent().toByteArray(Charsets.UTF_8)

        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)

        entries["word/_rels/document.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$relsXml</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)

        entries["word/styles.xml"] = buildStylesXml(fontName, primaryColorHex, textColorHex, config.fontSizePt).toByteArray(Charsets.UTF_8)

        entries["word/numbering.xml"] = buildNumberingXml().toByteArray(Charsets.UTF_8)

        entries["word/document.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
$bodyXml
  </w:body>
</w:document>""".trimIndent().toByteArray(Charsets.UTF_8)

        entries.putAll(mediaEntries)

        return buildZip(entries)
    }

    private fun buildRunsXml(
        runs: List<MarkdownAstParser.InlineRun>,
        defaultBold: Boolean = false,
        defaultItalic: Boolean = false
    ): String {
        val sb = StringBuilder()
        for (run in runs) {
            val textEscaped = MarkdownUtil.xmlEscape(run.text)
            val isBold = run.isBold || defaultBold
            val isItalic = run.isItalic || defaultItalic
            val isStrike = run.isStrikethrough
            val isCode = run.isCode
            val isLink = run.linkUrl != null

            val rPr = StringBuilder()
            if (isBold) rPr.append("<w:b/>")
            if (isItalic) rPr.append("<w:i/>")
            if (isStrike) rPr.append("<w:strike/>")
            if (isCode) {
                rPr.append("""<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/><w:shd w:val="clear" w:color="auto" w:fill="F0F0F0"/><w:sz w:val="20"/>""")
            }
            if (isLink) {
                rPr.append("""<w:color w:val="0066CC"/><w:u w:val="single"/>""")
            }

            val rPrStr = if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else ""
            sb.append("<w:r>$rPrStr<w:t xml:space=\"preserve\">$textEscaped</w:t></w:r>")
        }
        return sb.toString()
    }

    private fun buildTableXml(tableNode: MarkdownAstParser.AstNode.TableNode, primaryColorHex: String): String {
        val sb = StringBuilder()
        sb.append("""
        <w:tbl>
          <w:tblPr>
            <w:tblW w:w="5000" w:type="pct"/>
            <w:tblBorders>
              <w:top w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:left w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:right w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
              <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
            </w:tblBorders>
            <w:tblCellMar>
              <w:top w:w="120" w:type="dxa"/>
              <w:left w:w="160" w:type="dxa"/>
              <w:bottom w:w="120" w:type="dxa"/>
              <w:right w:w="160" w:type="dxa"/>
            </w:tblCellMar>
          </w:tblPr>
        """.trimIndent()).append("\n")

        // Header Row
        if (tableNode.headers.isNotEmpty()) {
            sb.append("  <w:tr><w:trPr><w:tblHeader/></w:trPr>\n")
            for (headerCell in tableNode.headers) {
                val runs = buildRunsXml(headerCell, defaultBold = true)
                sb.append("""
                <w:tc>
                  <w:tcPr>
                    <w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/>
                  </w:tcPr>
                  <w:p>
                    <w:pPr><w:spacing w:after="0"/></w:pPr>
                    $runs
                  </w:p>
                </w:tc>
                """.trimIndent()).append("\n")
            }
            sb.append("  </w:tr>\n")
        }

        // Data Rows
        for (row in tableNode.rows) {
            sb.append("  <w:tr>\n")
            for (cell in row) {
                val runs = buildRunsXml(cell)
                sb.append("""
                <w:tc>
                  <w:p>
                    <w:pPr><w:spacing w:after="0"/></w:pPr>
                    $runs
                  </w:p>
                </w:tc>
                """.trimIndent()).append("\n")
            }
            sb.append("  </w:tr>\n")
        }

        sb.append("</w:tbl>\n")
        return sb.toString()
    }

    private fun calculateEmuDimensions(bytes: ByteArray): Pair<Long, Long> {
        val maxEmuWidth = 5400000L // ~6.0 inches
        val maxEmuHeight = 7200000L // ~8.0 inches
        val (origW, origH) = try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            (opts.outWidth.coerceAtLeast(1)) to (opts.outHeight.coerceAtLeast(1))
        } catch (_: Throwable) {
            1200 to 800
        }
        val aspect = origW.toDouble() / origH.toDouble()
        return if (aspect >= 1.0) {
            val w = maxEmuWidth
            val h = (maxEmuWidth / aspect).toLong().coerceAtMost(maxEmuHeight)
            w to h
        } else {
            val h = maxEmuHeight
            val w = (maxEmuHeight * aspect).toLong().coerceIn(720000L, maxEmuWidth)
            w to h
        }
    }

    private fun buildStylesXml(fontName: String, primaryColorHex: String, textColorHex: String, fontSizePt: Int): String {
        val halfPtSize = fontSizePt * 2
        val h1Size = (fontSizePt * 1.8 * 2).toInt()
        val h2Size = (fontSizePt * 1.4 * 2).toInt()
        val h3Size = (fontSizePt * 1.2 * 2).toInt()

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/>
        <w:sz w:val="$halfPtSize"/>
        <w:szCs w:val="$halfPtSize"/>
        <w:color w:val="$textColorHex"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="160" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>

  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="48"/>
      <w:color w:val="$primaryColorHex"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:spacing w:before="360" w:after="140"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="$h1Size"/>
      <w:color w:val="$primaryColorHex"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:spacing w:before="260" w:after="100"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:sz w:val="$h2Size"/>
      <w:color w:val="$primaryColorHex"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:spacing w:before="200" w:after="80"/>
    </w:pPr>
    <w:rPr>
      <w:b/>
      <w:i/>
      <w:sz w:val="$h3Size"/>
      <w:color w:val="$textColorHex"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Blockquote">
    <w:name w:val="Quote"/>
    <w:basedOn w:val="Normal"/>
    <w:rPr>
      <w:i/>
      <w:color w:val="555555"/>
    </w:rPr>
  </w:style>
</w:styles>""".trimIndent()
    }

    private fun buildNumberingXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <!-- Unordered bullet list -->
  <w:abstractNum w:abstractNumId="1">
    <w:lvl w:ilvl="0">
      <w:start w:val="1"/>
      <w:numFmt w:val="bullet"/>
      <w:lvlText w:val="&#x2022;"/>
      <w:lvlJc w:val="left"/>
      <w:pPr>
        <w:ind w:left="720" w:hanging="360"/>
      </w:pPr>
      <w:rPr>
        <w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:hint="default"/>
      </w:rPr>
    </w:lvl>
  </w:abstractNum>

  <!-- Ordered decimal list -->
  <w:abstractNum w:abstractNumId="2">
    <w:lvl w:ilvl="0">
      <w:start w:val="1"/>
      <w:numFmt w:val="decimal"/>
      <w:lvlText w:val="%1."/>
      <w:lvlJc w:val="left"/>
      <w:pPr>
        <w:ind w:left="720" w:hanging="360"/>
      </w:pPr>
    </w:lvl>
  </w:abstractNum>

  <w:num w:numId="1">
    <w:abstractNumId w:val="1"/>
  </w:num>
  <w:num w:numId="2">
    <w:abstractNumId w:val="2"/>
  </w:num>
</w:numbering>""".trimIndent()
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
}
