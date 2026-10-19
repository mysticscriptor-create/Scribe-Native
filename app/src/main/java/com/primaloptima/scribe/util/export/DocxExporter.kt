package com.primaloptima.scribe.util.export

import android.content.Context
import android.graphics.BitmapFactory
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.ImageMimeUtil
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Modern standards-compliant OpenXML DOCX exporter for Scribe (ECMA-376).
 * Produces structured Microsoft Word (.docx) documents complete with OpenXML styles,
 * multi-level numbering definitions for lists (ilvl 0..8), external hyperlinks with TargetMode="External",
 * semantic paragraph reflow (soft breaks as spaces, hard breaks as <w:br/>),
 * proportional table grids with alignment, and robust image handling with editorial fallbacks.
 */
object DocxExporter {

    fun export(context: Context?, note: Note, config: ExportConfig): ByteArray {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val fontName = ExportThemeMapper.resolveDocxFontName(config.fontChoice)
        val primaryColorHex = if (config.fontChoice == FontChoice.INHERIT_THEME) {
            ExportThemeMapper.cleanHexForDocx(themeColors.accent, "1A5276")
        } else {
            "1A5276"
        }
        val textColorHex = "1A1A1A"

        val ast = ScribeMarkdownAstParser.parse(note.content)
        val mediaEntries = mutableMapOf<String, ByteArray>()
        val relsXml = StringBuilder()
        var relIdCounter = 1

        // Standard relationships
        relsXml.append("""  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>${"\n"}""")
        relsXml.append("""  <Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>${"\n"}""")

        // External Hyperlink registry: Map<Url, rId>
        val hyperlinkRels = mutableMapOf<String, String>()
        fun registerHyperlink(url: String): String {
            return hyperlinkRels.getOrPut(url) {
                val rId = "rIdLink${relIdCounter++}"
                val safeUrl = MarkdownUtil.xmlEscape(url)
                relsXml.append("""  <Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="$safeUrl" TargetMode="External"/>${"\n"}""")
                rId
            }
        }

        val bodyXml = StringBuilder()

        // 1. Cover / Title Paragraph if configured
        val header = ManuscriptTitleHeader.from(note, config.ornamentId)
        if (config.includeCoverTitle && header.isPresent) {
            val isTitleRtl = HtmlEmitter.isRtlText(note.name)
            val bidiXml = if (isTitleRtl) "\n                <w:bidi/>" else ""
            val hasBoth = header.hasPrimary && header.hasSecondary
            if (hasBoth) {
                val kickerEscaped = MarkdownUtil.xmlEscape(header.primaryTitle.uppercase())
                bodyXml.append("""
            <w:p>
              <w:pPr>
                <w:pStyle w:val="Subtitle"/>
                <w:jc w:val="center"/>
                <w:spacing w:before="600" w:after="120"/>$bidiXml
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="26"/>
                  <w:color w:val="$primaryColorHex"/>
                </w:rPr>
                <w:t xml:space="preserve">$kickerEscaped</w:t>
              </w:r>
            </w:p>
                """.trimIndent()).append("\n")
            }

            val mainTitleText = when {
                hasBoth -> header.secondaryTitle
                header.hasSecondary -> header.secondaryTitle
                header.hasPrimary -> header.primaryTitle
                else -> note.name.ifBlank { "Untitled Manuscript" }
            }
            val secEscaped = MarkdownUtil.xmlEscape(mainTitleText)
            bodyXml.append("""
            <w:p>
              <w:pPr>
                <w:pStyle w:val="Title"/>
                <w:jc w:val="center"/>
                <w:spacing w:before="120" w:after="240"/>$bidiXml
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:b/>
                  <w:sz w:val="48"/>
                  <w:color w:val="$primaryColorHex"/>
                </w:rPr>
                <w:t xml:space="preserve">$secEscaped</w:t>
              </w:r>
            </w:p>
            """.trimIndent()).append("\n")
        }
            if (config.ornamentId != "none") {
                val dividerText = when (config.ornamentId) {
                    "fleuron" -> "─── ❦ ───"
                    "asterism" -> "─── ⁂ ───"
                    "minimal_rule" -> "────── • ──────"
                    else -> "─── ❖ ───"
                }
                bodyXml.append("""
            <w:p>
              <w:pPr>
                <w:jc w:val="center"/>
                <w:spacing w:before="180" w:after="360"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  <w:rFonts w:ascii="Cambria Math" w:hAnsi="Cambria Math"/>
                  <w:color w:val="$primaryColorHex"/>
                  <w:sz w:val="26"/>
                </w:rPr>
                <w:t xml:space="preserve">$dividerText</w:t>
              </w:r>
            </w:p>
                """.trimIndent()).append("\n")
            }
        }

        // Printable width in dxa (1/20 of a pt)
        val printableDxa = ((config.paperSize.dxaWidth - config.marginPreset.dxaLeft - config.marginPreset.dxaRight).coerceAtLeast(4000L)).toInt()

        var dropCapApplied = false

        // 2. Iterate AST Nodes
        for (node in ast.children) {
            when (node) {
                is MarkdownNode.BlockNode.Heading -> {
                    val styleVal = when (node.level) {
                        1 -> "Heading1"
                        2 -> "Heading2"
                        else -> "Heading3"
                    }
                    val isRtl = HtmlEmitter.isRtlRuns(node.runs)
                    val bidiXml = if (isRtl) "\n    <w:bidi/>" else ""
                    val headingRuns = buildRunsXml(node.runs, defaultBold = true, isRtlParagraph = isRtl, onRegisterHyperlink = ::registerHyperlink)
                    bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:pStyle w:val="$styleVal"/>$bidiXml
                      </w:pPr>
                      $headingRuns
                    </w:p>
                    """.trimIndent()).append("\n")
                }
                is MarkdownNode.BlockNode.Paragraph -> {
                    val isRtl = HtmlEmitter.isRtlRuns(node.runs)
                    val bidiXml = if (isRtl) "\n    <w:bidi/>" else ""
                    val shouldDropCap = !dropCapApplied && config.dropCapStyle != DropCapStyle.DISABLED && !isRtl && node.runs.any { it.text.isNotBlank() }
                    if (shouldDropCap) {
                        dropCapApplied = true
                        val fullText = node.runs.joinToString("") { it.text }
                        val firstChar = fullText.firstOrNull { !it.isWhitespace() } ?: ' '
                        val hasQuote = firstChar == '"' || firstChar == '“' || firstChar == '\'' || firstChar == '‘'
                        val charIter = java.text.BreakIterator.getCharacterInstance()
                        charIter.setText(fullText)
                        val firstBound = charIter.next()
                        val glyphLen = if (hasQuote && fullText.length > firstBound) {
                            val secondBound = charIter.next()
                            if (secondBound != java.text.BreakIterator.DONE) secondBound else firstBound
                        } else {
                            if (firstBound != java.text.BreakIterator.DONE) firstBound else 1
                        }
                        val dropGlyph = MarkdownUtil.xmlEscape(fullText.substring(0, glyphLen))
                        val remainingRuns = splitFirstGlyph(node.runs, glyphLen)
                        val runsXml = buildRunsXml(remainingRuns, isRtlParagraph = isRtl, onRegisterHyperlink = ::registerHyperlink)
                        bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:spacing w:after="160" w:line="320" w:lineRule="auto"/>
                        <w:framePr w:dropCap="drop" w:lines="${config.dropCapLines}" w:wrap="around" w:hSpace="180"/>$bidiXml
                      </w:pPr>
                      <w:r>
                        <w:rPr>
                          <w:rFonts w:ascii="$fontName" w:hAnsi="$fontName"/>
                          <w:b/>
                          <w:color w:val="$primaryColorHex"/>
                        </w:rPr>
                        <w:t xml:space="preserve">$dropGlyph</w:t>
                      </w:r>
                      $runsXml
                    </w:p>
                        """.trimIndent()).append("\n")
                    } else {
                        val runsXml = buildRunsXml(node.runs, isRtlParagraph = isRtl, onRegisterHyperlink = ::registerHyperlink)
                        bodyXml.append("""
                    <w:p>
                      <w:pPr>
                        <w:spacing w:after="160" w:line="320" w:lineRule="auto"/>$bidiXml
                      </w:pPr>
                      $runsXml
                    </w:p>
                        """.trimIndent()).append("\n")
                    }
                }
                is MarkdownNode.BlockNode.ListBlock -> {
                    val numId = if (node.isOrdered) "2" else "1"
                    for (item in node.items) {
                        val isRtl = HtmlEmitter.isRtlRuns(item.runs)
                        val bidiXml = if (isRtl) "\n        <w:bidi/>" else ""
                        val itemRuns = buildRunsXml(item.runs, isRtlParagraph = isRtl, onRegisterHyperlink = ::registerHyperlink)
                        val ilvl = item.level.coerceIn(0, 8)
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:numPr>
                              <w:ilvl w:val="$ilvl"/>
                              <w:numId w:val="$numId"/>
                            </w:numPr>
                            <w:spacing w:after="80"/>$bidiXml
                          </w:pPr>
                          $itemRuns
                        </w:p>
                        """.trimIndent()).append("\n")
                    }
                }

                is MarkdownNode.BlockNode.Blockquote -> {
                    for (child in node.children) {
                        val (childRuns, isChildRtl) = when (child) {
                            is MarkdownNode.BlockNode.Paragraph -> {
                                val rtl = HtmlEmitter.isRtlRuns(child.runs)
                                buildRunsXml(child.runs, defaultItalic = true, isRtlParagraph = rtl, onRegisterHyperlink = ::registerHyperlink) to rtl
                            }
                            is MarkdownNode.BlockNode.Heading -> {
                                val rtl = HtmlEmitter.isRtlRuns(child.runs)
                                buildRunsXml(child.runs, defaultItalic = true, defaultBold = true, isRtlParagraph = rtl, onRegisterHyperlink = ::registerHyperlink) to rtl
                            }
                            else -> "" to false
                        }
                        val bidiXml = if (isChildRtl) "\n        <w:bidi/>" else ""
                        val borderSide = if (isChildRtl) "right" else "left"
                        val indXml = if (isChildRtl) """<w:ind w:right="720"/>""" else """<w:ind w:left="720"/>"""
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:pStyle w:val="Blockquote"/>
                            <w:pBdr>
                              <w:$borderSide w:val="single" w:sz="18" w:space="12" w:color="$primaryColorHex"/>
                            </w:pBdr>
                            $indXml
                            <w:spacing w:after="120"/>$bidiXml
                          </w:pPr>
                          $childRuns
                        </w:p>
                        """.trimIndent()).append("\n")
                    }
                }

                is MarkdownNode.BlockNode.ThematicBreak -> {
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

                is MarkdownNode.BlockNode.Table -> {
                    bodyXml.append(buildTableXml(node, printableDxa, ::registerHyperlink))
                }

                is MarkdownNode.BlockNode.CodeBlock -> {
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

                is MarkdownNode.BlockNode.ImageBlock -> {
                    val bytes = if (context != null) VaultImageManager.getImageBytes(context, node.uri, note.bookId, note.id) else null
                    if (bytes != null && bytes.isNotEmpty()) {
                        val rId = "rIdImg${relIdCounter++}"
                        val detected = ImageMimeUtil.detect(bytes, node.uri)
                        val ext = if (detected.extension == "jpg") "jpeg" else detected.extension
                        val mediaPath = "media/image_$rId.$ext"
                        mediaEntries["word/$mediaPath"] = bytes
                        relsXml.append("""  <Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$mediaPath"/>${"\n"}""")

                        val (cx, cy) = calculateEmuDimensions(bytes)
                        val captionEscaped = MarkdownUtil.xmlEscape(node.caption ?: node.alt)
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
                                <wp:docPr id="$relIdCounter" name="Image $rId"/>
                                <wp:cNvGraphicFramePr>
                                  <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                                </wp:cNvGraphicFramePr>
                                <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                                  <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                    <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                      <pic:nvPicPr>
                                        <pic:cNvPr id="$relIdCounter" name="Image $rId"/>
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
                    } else {
                        // Clean editorial fallback callout box for missing assets
                        val captionOrAlt = MarkdownUtil.xmlEscape((node.caption ?: node.alt).ifBlank { "Illustration" })
                        bodyXml.append("""
                        <w:p>
                          <w:pPr>
                            <w:pBdr>
                              <w:top w:val="single" w:sz="6" w:space="8" w:color="CCCCCC"/>
                              <w:left w:val="single" w:sz="6" w:space="8" w:color="CCCCCC"/>
                              <w:bottom w:val="single" w:sz="6" w:space="8" w:color="CCCCCC"/>
                              <w:right w:val="single" w:sz="6" w:space="8" w:color="CCCCCC"/>
                            </w:pBdr>
                            <w:shd w:val="clear" w:color="auto" w:fill="F8F9FA"/>
                            <w:jc w:val="center"/>
                            <w:spacing w:before="160" w:after="160"/>
                          </w:pPr>
                          <w:r>
                            <w:rPr>
                              <w:i/>
                              <w:sz w:val="18"/>
                              <w:color w:val="777777"/>
                            </w:rPr>
                            <w:t xml:space="preserve">[Illustration: $captionOrAlt]</w:t>
                          </w:r>
                        </w:p>
                        """.trimIndent()).append("\n")
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
        entries["[Content_Types].xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>  <Default Extension="xml" ContentType="application/xml"/>  <Default Extension="jpeg" ContentType="image/jpeg"/>  <Default Extension="jpg" ContentType="image/jpeg"/>  <Default Extension="png" ContentType="image/png"/>  <Default Extension="gif" ContentType="image/gif"/>  <Default Extension="webp" ContentType="image/webp"/>  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>  <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""".trimIndent().toByteArray(Charsets.UTF_8)
        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
        entries["word/_rels/document.xml.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$relsXml</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
        entries["word/styles.xml"] = buildStylesXml(fontName, primaryColorHex, textColorHex, config.fontSizePt).toByteArray(Charsets.UTF_8)
        entries["word/numbering.xml"] = buildNumberingXml().toByteArray(Charsets.UTF_8)
        entries["word/document.xml"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">  <w:body>$bodyXml  </w:body></w:document>""".trimIndent().toByteArray(Charsets.UTF_8)
        entries.putAll(mediaEntries)

        return buildZip(entries)
    }

    /**
     * Emits WordprocessingML runs preserving paragraph reflow:
     * - Soft line breaks in Markdown are spaces, allowing Word to reflow text naturally.
     * - Explicit hard breaks (two spaces / backslash) emit <w:r><w:br/></w:r>.
     * - External hyperlinks are wrapped with valid <w:hyperlink r:id="..."> tags.
     */
    private fun buildRunsXml(
        runs: List<MarkdownNode.InlineRun>,
        defaultBold: Boolean = false,
        defaultItalic: Boolean = false,
        isRtlParagraph: Boolean = false,
        onRegisterHyperlink: ((String) -> String)? = null
    ): String {
        val sb = StringBuilder()
        for (run in runs) {
            if (run.isHardBreak) {
                sb.append("<w:r><w:br/></w:r>")
                continue
            }
            val isBold = run.isBold || defaultBold
            val isItalic = run.isItalic || defaultItalic
            val isStrike = run.isStrikethrough
            val isCode = run.isCode
            val isLink = run.linkUrl != null
            val isRtl = isRtlParagraph || HtmlEmitter.isRtlText(run.text)

            val rPr = StringBuilder()
            if (isBold) rPr.append("<w:b/>")
            if (isItalic) rPr.append("<w:i/>")
            if (isStrike) rPr.append("<w:strike/>")
            if (isRtl) rPr.append("<w:rtl/>")
            if (isCode) {
                rPr.append("""<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/><w:shd w:val="clear" w:color="auto" w:fill="F0F0F0"/><w:sz w:val="20"/>""")
            }
            if (isLink) {
                rPr.append("""<w:rStyle w:val="Hyperlink"/><w:color w:val="0066CC"/><w:u w:val="single"/>""")
            }
            val rPrStr = if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else ""

            val escaped = MarkdownUtil.xmlEscape(run.text)
            val textContent = "<w:t xml:space=\"preserve\">$escaped</w:t>"

            val runXml = "<w:r>$rPrStr$textContent</w:r>"
            if (isLink && onRegisterHyperlink != null) {
                val rId = onRegisterHyperlink(run.linkUrl!!)
                sb.append("<w:hyperlink r:id=\"$rId\" w:history=\"1\">$runXml</w:hyperlink>")
            } else {
                sb.append(runXml)
            }
        }
        return sb.toString()
    }

    private fun buildTableXml(
        table: MarkdownNode.BlockNode.Table,
        printableDxa: Int,
        onRegisterHyperlink: (String) -> String
    ): String {
        val colCount = table.headers.size.coerceAtLeast(table.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val colDxa = (printableDxa / colCount).coerceAtLeast(500)
        val sb = StringBuilder()

        sb.append("""
        <w:tbl>
          <w:tblPr>
            <w:tblW w:w="$printableDxa" w:type="dxa"/>
            <w:jc w:val="center"/>
            <w:tblBorders>
              <w:top w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:left w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:right w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
              <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E5E5E5"/>
              <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E5E5E5"/>
            </w:tblBorders>
          </w:tblPr>
          <w:tblGrid>
        """.trimIndent()).append("\n")

        for (c in 0 until colCount) {
            sb.append("    <w:gridCol w:w=\"$colDxa\"/>\n")
        }
        sb.append("  </w:tblGrid>\n")

        fun alignToJc(alignment: TableAlignment): String {
            return when (alignment) {
                TableAlignment.LEFT -> "left"
                TableAlignment.CENTER -> "center"
                TableAlignment.RIGHT -> "right"
                TableAlignment.DEFAULT -> "left"
            }
        }

        // Header Row
        if (table.headers.isNotEmpty()) {
            sb.append("  <w:tr><w:trPr><w:tblHeader/></w:trPr>\n")
            table.headers.forEachIndexed { colIdx, headerCell ->
                val align = table.alignments.getOrElse(colIdx) { TableAlignment.DEFAULT }
                val isRtl = HtmlEmitter.isRtlRuns(headerCell.runs)
                val jcVal = if (align == TableAlignment.DEFAULT && isRtl) "right" else alignToJc(align)
                val bidiXml = if (isRtl) "\n                      <w:bidi/>" else ""
                val runs = buildRunsXml(headerCell.runs, defaultBold = true, isRtlParagraph = isRtl, onRegisterHyperlink = onRegisterHyperlink)
                sb.append("""
                <w:tc>
                  <w:tcPr>
                    <w:tcW w:w="$colDxa" w:type="dxa"/>
                    <w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/>
                  </w:tcPr>
                  <w:p>
                    <w:pPr>
                      <w:jc w:val="$jcVal"/>
                      <w:spacing w:after="0"/>$bidiXml
                    </w:pPr>
                    $runs
                  </w:p>
                </w:tc>
                """.trimIndent()).append("\n")
            }
            sb.append("  </w:tr>\n")
        }

        // Data Rows
        for (row in table.rows) {
            sb.append("  <w:tr>\n")
            for (colIdx in 0 until colCount) {
                val cell = if (colIdx < row.size) row[colIdx] else MarkdownNode.BlockNode.TableCell(emptyList())
                val align = table.alignments.getOrElse(colIdx) { TableAlignment.DEFAULT }
                val isRtl = HtmlEmitter.isRtlRuns(cell.runs)
                val jcVal = if (align == TableAlignment.DEFAULT && isRtl) "right" else alignToJc(align)
                val bidiXml = if (isRtl) "\n                      <w:bidi/>" else ""
                val runs = buildRunsXml(cell.runs, isRtlParagraph = isRtl, onRegisterHyperlink = onRegisterHyperlink)
                sb.append("""
                <w:tc>
                  <w:tcPr>
                    <w:tcW w:w="$colDxa" w:type="dxa"/>
                  </w:tcPr>
                  <w:p>
                    <w:pPr>
                      <w:jc w:val="$jcVal"/>
                      <w:spacing w:after="0"/>$bidiXml
                    </w:pPr>
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
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">  <w:docDefaults>    <w:rPrDefault>      <w:rPr>        <w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/>        <w:sz w:val="$halfPtSize"/>        <w:szCs w:val="$halfPtSize"/>        <w:color w:val="$textColorHex"/>      </w:rPr>    </w:rPrDefault>    <w:pPrDefault>      <w:pPr>        <w:spacing w:after="160" w:line="276" w:lineRule="auto"/>      </w:pPr>    </w:pPrDefault>  </w:docDefaults>  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">    <w:name w:val="Normal"/>  </w:style>  <w:style w:type="paragraph" w:styleId="Title">    <w:name w:val="Title"/>    <w:basedOn w:val="Normal"/>    <w:rPr>      <w:b/>      <w:sz w:val="48"/>      <w:color w:val="$primaryColorHex"/>    </w:rPr>  </w:style>  <w:style w:type="paragraph" w:styleId="Heading1">    <w:name w:val="heading 1"/>    <w:basedOn w:val="Normal"/>    <w:pPr>      <w:spacing w:before="360" w:after="140"/>    </w:pPr>    <w:rPr>      <w:b/>      <w:sz w:val="$h1Size"/>      <w:color w:val="$primaryColorHex"/>    </w:rPr>  </w:style>  <w:style w:type="paragraph" w:styleId="Heading2">    <w:name w:val="heading 2"/>    <w:basedOn w:val="Normal"/>    <w:pPr>      <w:spacing w:before="260" w:after="100"/>    </w:pPr>    <w:rPr>      <w:b/>      <w:sz w:val="$h2Size"/>      <w:color w:val="$primaryColorHex"/>    </w:rPr>  </w:style>  <w:style w:type="paragraph" w:styleId="Heading3">    <w:name w:val="heading 3"/>    <w:basedOn w:val="Normal"/>    <w:pPr>      <w:spacing w:before="200" w:after="80"/>    </w:pPr>    <w:rPr>      <w:b/>      <w:i/>      <w:sz w:val="$h3Size"/>      <w:color w:val="$textColorHex"/>    </w:rPr>  </w:style>  <w:style w:type="paragraph" w:styleId="Blockquote">    <w:name w:val="Quote"/>    <w:basedOn w:val="Normal"/>    <w:rPr>      <w:i/>      <w:color w:val="555555"/>    </w:rPr>  </w:style>  <w:style w:type="character" w:styleId="Hyperlink">    <w:name w:val="Hyperlink"/>    <w:rPr>      <w:color w:val="0066CC"/>      <w:u w:val="single"/>    </w:rPr>  </w:style></w:styles>""".trimIndent()
    }

    /**
     * Builds word/numbering.xml with full 9-level hierarchies (levels 0..8)
     * for unordered bullet lists (abstractNum 1) and ordered lists (abstractNum 2).
     */
    private fun buildNumberingXml(): String {
        val bulletLvls = StringBuilder()
        val glyphs = listOf("&#x2022;", "&#x25E6;", "&#x25AA;")
        val bulletFonts = listOf("Arial", "Courier New", "Wingdings")
        for (lvl in 0..8) {
            val glyph = glyphs[lvl % 3]
            val font = bulletFonts[lvl % 3]
            val leftIndent = 720 * (lvl + 1)
            bulletLvls.append("""    <w:lvl w:ilvl="$lvl">      <w:start w:val="1"/>      <w:numFmt w:val="bullet"/>      <w:lvlText w:val="$glyph"/>      <w:lvlJc w:val="left"/>      <w:pPr>        <w:ind w:left="$leftIndent" w:hanging="360"/>      </w:pPr>      <w:rPr>        <w:rFonts w:ascii="$font" w:hAnsi="$font" w:hint="default"/>      </w:rPr>    </w:lvl>
            """.trimIndent()).append("\n")
        }

        val decimalLvls = StringBuilder()
        for (lvl in 0..8) {
            val numFmt = when (lvl % 3) {
                0 -> "decimal"
                1 -> "lowerLetter"
                else -> "lowerRoman"
            }
            val lvlIndex = lvl + 1
            val leftIndent = 720 * (lvl + 1)
            decimalLvls.append("""    <w:lvl w:ilvl="$lvl">      <w:start w:val="1"/>      <w:numFmt w:val="$numFmt"/>      <w:lvlText w:val="%$lvlIndex."/>      <w:lvlJc w:val="left"/>      <w:pPr>        <w:ind w:left="$leftIndent" w:hanging="360"/>      </w:pPr>    </w:lvl>
            """.trimIndent()).append("\n")
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">  <!-- Unordered bullet list with levels 0..8 -->  <w:abstractNum w:abstractNumId="1">$bulletLvls  </w:abstractNum>  <!-- Ordered list with levels 0..8 -->  <w:abstractNum w:abstractNumId="2">$decimalLvls  </w:abstractNum>  <w:num w:numId="1">    <w:abstractNumId w:val="1"/>  </w:num>  <w:num w:numId="2">    <w:abstractNumId w:val="2"/>  </w:num></w:numbering>""".trimIndent()
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

    private fun splitFirstGlyph(runs: List<MarkdownNode.InlineRun>, glyphLen: Int): List<MarkdownNode.InlineRun> {
        var remainingToTrim = glyphLen
        val result = mutableListOf<MarkdownNode.InlineRun>()
        for (run in runs) {
            if (remainingToTrim <= 0) {
                result.add(run)
            } else if (run.text.length <= remainingToTrim) {
                remainingToTrim -= run.text.length
            } else {
                result.add(run.copy(text = run.text.substring(remainingToTrim)))
                remainingToTrim = 0
            }
        }
        return result
    }

}
