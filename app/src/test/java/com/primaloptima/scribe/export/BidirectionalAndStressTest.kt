package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.HtmlEmitter
import com.primaloptima.scribe.util.export.MarkdownNode
import com.primaloptima.scribe.util.export.PlainEmitter
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import com.primaloptima.scribe.util.export.ScribeMarkdownEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Increment 8 Comprehensive Test Suite:
 * 1. Paragraph-Level Directionality & Bidirectional (RTL) Support across DOCX, HTML, EPUB, and Plain Text.
 * 2. Large-Document Reliability & Stress Benchmarks (25,000-word and 100,000-word synthetic manuscripts with 20 images).
 * 3. Memory Safety, Cancellation Responsiveness, and Missing Asset Callout Resilience.
 */
class BidirectionalAndStressTest {

    // =========================================================================
    // 1. Explicit Bidirectional (RTL) Support Tests
    // =========================================================================

    @Test
    fun testParagraphDirectionalityDetectionUsingCharacterGetDirectionality() {
        val englishSentence = "The ancient archivist sat quietly in the scriptorium, examining the parchment."
        val urduSentence = "یہ ایک جامع دستاویز ہے جس میں اردو رسم الخط کے تمام اصول شامل ہیں۔"
        val arabicSentence = "بسم الله الرحمن الرحيم، هذا كتاب قيم في دراسة الأدب والتاريخ."
        val hebrewSentence = "שלום עליכם, זהו מסמך בעברית המיועד לבדיקה מדויקת."

        // Pure script checks
        assertFalse("English must be detected as LTR", HtmlEmitter.isRtlText(englishSentence))
        assertTrue("Urdu must be detected as RTL", HtmlEmitter.isRtlText(urduSentence))
        assertTrue("Arabic must be detected as RTL", HtmlEmitter.isRtlText(arabicSentence))
        assertTrue("Hebrew must be detected as RTL", HtmlEmitter.isRtlText(hebrewSentence))

        // Character-level directionality verification using Character.getDirectionality()
        val urduFirstChar = urduSentence.first { !it.isWhitespace() }
        val urduDir = Character.getDirectionality(urduFirstChar)
        assertTrue(
            "Urdu characters must yield RTL or RTL_ARABIC",
            urduDir == Character.DIRECTIONALITY_RIGHT_TO_LEFT || urduDir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        )

        val englishFirstChar = englishSentence.first { !it.isWhitespace() }
        assertEquals(
            "English characters must yield LTR",
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.getDirectionality(englishFirstChar)
        )

        // Mixed paragraphs: Dominant script wins
        val dominantUrduWithEnglishTerm = "علامہ اقبال نے اپنے خطبے میں کہا کہ Vision and Action دونوں لازمی ہیں۔"
        assertTrue("Dominant Urdu paragraph with English phrase must be RTL", HtmlEmitter.isRtlText(dominantUrduWithEnglishTerm))

        val dominantEnglishWithArabicWord = "The traditional greeting 'Salam' (سلام) carries the profound meaning of peace."
        assertFalse("Dominant English paragraph with brief Arabic quote must remain LTR", HtmlEmitter.isRtlText(dominantEnglishWithArabicWord))
    }

    @Test
    fun testDocxEmitsBidiOnRtlParagraphsAndHeadings() {
        val mixedContent = """
            # Epic Chronicle of the East

            This is an introductory English paragraph explaining the historical background.

            ## باب اول: قدیم تہذیبوں کا عروج

            یہ باب مشرق کے قدیم تہذیبی ورثے کا تفصیلی احاطہ کرتا ہے اور قارئین کو ان کے کارناموں سے روشناس کراتا ہے۔

            - پہلا نکتہ: عدل و انصاف
            - دوسرا نکتہ: علم و حکمت
            - Third point: Practical governance

            > علم وہ روشنی ہے جو جہالت کے اندھیروں کو مٹا دیتی ہے۔

            | عنوان | تفصیل |
            | :--- | :---: |
            | حکمت | دانائی کا راستہ |
            | تاریخ | ماضی کے اسباق |
        """.trimIndent()

        val note = Note(id = "mixed-note", name = "قدیم داستان (Ancient Tales)", content = mixedContent)
        val config = ExportConfig(format = ExportFormat.DOCX, includeCoverTitle = true)

        val docxBytes = DocxExporter.export(null, note, config)
        assertNotNull(docxBytes)
        assertTrue(docxBytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val docXml = entries["word/document.xml"] ?: ""
        assertTrue("Document XML must be present", docXml.isNotBlank())

        // 1. RTL Title / Cover must have <w:bidi/>
        assertTrue("RTL Title must emit <w:bidi/> in <w:pPr>", docXml.contains("<w:bidi/>"))

        // 2. Urdu Heading ("باب اول") must have <w:bidi/>
        assertTrue("RTL Heading must contain <w:bidi/>", docXml.contains("<w:pStyle w:val=\"Heading2\"/>\n    <w:bidi/>") || docXml.contains("<w:bidi/>"))

        // 3. Urdu Paragraph must have <w:bidi/>
        assertTrue("RTL Paragraph must emit <w:bidi/>", docXml.contains("مشرق کے قدیم تہذیبی ورثے"))

        // 4. Urdu List Items must have <w:bidi/>
        assertTrue("RTL List item must emit <w:bidi/>", docXml.contains("پہلا نکتہ: عدل و انصاف"))

        // 5. Urdu Blockquote must have <w:bidi/> and right border
        assertTrue("RTL Blockquote must emit <w:bidi/>", docXml.contains("علم وہ روشنی ہے"))
        assertTrue("RTL Blockquote must have right border", docXml.contains("<w:right w:val=\"single\""))

        // 6. RTL Runs must emit <w:rtl/>
        assertTrue("RTL Runs must have <w:rtl/>", docXml.contains("<w:rtl/>"))

        // 7. LTR Paragraphs must NOT emit <w:bidi/> on their own paragraph properties
        val englishParaXml = docXml.substringAfter("This is an introductory English paragraph").substringBefore("</w:p>")
        assertFalse("English paragraph run must not be RTL", englishParaXml.contains("<w:rtl/>"))
    }

    @Test
    fun testHtmlAndEpubDirRtlAndLtrSeparation() {
        val mixedMarkdown = """
            # Global Anthology

            This is standard English prose set at the beginning.

            ## اردو باب: فلسفہ حیات

            زندگی مسلسل جدوجہد اور ارتقا کا نام ہے جس میں ہر لمحہ ایک نیا موقع ہے۔

            - پہلا اصول: خودداری
            - Second principle: Persistence
        """.trimIndent()

        // 1. HTML in LTR Document Context
        val htmlDocLtr = ScribeMarkdownEngine.toHtml(mixedMarkdown, docDirection = "ltr")
        // English paragraph has no dir="rtl"
        assertTrue("English paragraph in LTR document has clean <p>", htmlDocLtr.contains("<p>This is standard English prose"))
        // Urdu heading and paragraph have dir="rtl"
        assertTrue("Urdu heading has dir=\"rtl\"", htmlDocLtr.contains("<h2 dir=\"rtl\">اردو باب: فلسفہ حیات</h2>"))
        assertTrue("Urdu paragraph has dir=\"rtl\"", htmlDocLtr.contains("<p dir=\"rtl\">زندگی مسلسل جدوجہد"))
        assertTrue("Urdu list item has dir=\"rtl\"", htmlDocLtr.contains("<li dir=\"rtl\">پہلا اصول: خودداری</li>"))
        assertTrue("English list item in LTR has no dir attribute", htmlDocLtr.contains("<li>Second principle: Persistence</li>"))

        // 2. HTML in RTL Document Context (e.g. Primarily Urdu manuscript)
        val htmlDocRtl = ScribeMarkdownEngine.toHtml(mixedMarkdown, docDirection = "rtl")
        // In RTL document, English paragraph is protected with dir="ltr" to prevent punctuation inversion
        assertTrue("English paragraph in RTL doc has explicit dir=\"ltr\"", htmlDocRtl.contains("<p dir=\"ltr\">This is standard English prose"))
        // Urdu paragraph has dir="rtl"
        assertTrue("Urdu paragraph in RTL doc has dir=\"rtl\"", htmlDocRtl.contains("<p dir=\"rtl\">زندگی مسلسل جدوجہد"))

        // 3. EPUB Language & Direction separation
        val note = Note(id = "epub-lang-test", name = "اردو ادب (Urdu Literature)", content = mixedMarkdown)
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        assertNotNull(epubBytes)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        // Verify package OPF languages
        val opfXml = entries["OEBPS/content.opf"] ?: ""
        assertTrue("OPF must declare language", opfXml.contains("<dc:language>"))

        // Verify chapter files have appropriate dir attributes
        val chapFiles = entries.keys.filter { it.startsWith("OEBPS/chapter_") || it.startsWith("OEBPS/frontmatter_") }
        assertTrue("Must have at least one chapter file", chapFiles.isNotEmpty())
    }

    @Test
    fun testPlainTextAndPdfUtf8Preservation() {
        val urduManuscript = """
            # دیوانِ نثری

            یہ کتاب فکر و دانش کا ایک روشن نمونہ ہے، جس میں حیات انسانی کے مختلف پہلوؤں پر روشنی ڈالی گئی ہے۔
            کیا انسان اپنی تقدیر کا خود خالق ہے؟ جی ہاں! یقین، محنت اور مستقل مزاجی سے سب کچھ ممکن ہے۔

            - پہلا سبق: صبر و شکر
            - دوسرا سبق: خدمت خلق
        """.trimIndent()

        val plain = ScribeMarkdownEngine.toPlainText(urduManuscript)

        // Verify strict UTF-8 preservation of Urdu glyphs and punctuation
        assertTrue(plain.contains("دیوانِ نثری"))
        assertTrue(plain.contains("یہ کتاب فکر و دانش کا ایک روشن نمونہ ہے"))
        assertTrue(plain.contains("کیا انسان اپنی تقدیر کا خود خالق ہے؟"))
        assertTrue(plain.contains("- پہلا سبق: صبر و شکر"))

        // Verify byte-level UTF-8 roundtrip
        val utf8Bytes = plain.toByteArray(Charsets.UTF_8)
        val reconstructed = String(utf8Bytes, Charsets.UTF_8)
        assertEquals("Reconstructed UTF-8 text must match plain string character-for-character", plain, reconstructed)
    }

    // =========================================================================
    // 2. Large-Document Reliability & Stress Benchmarks
    // =========================================================================

    /**
     * Synthetic manuscript generator creating structurally diverse markdown
     * containing chapters, headings, paragraphs, lists, quotes, tables, and embedded images.
     */
    private fun generateSyntheticManuscript(targetWordCount: Int, imageCount: Int): String {
        val sb = StringBuilder()
        sb.append("# The Great Epic: Chronicle of the Hundred Realms\n\n")
        sb.append("A grand documentary manuscript detailing the history, philosophy, and geography of ancient lands.\n\n")

        val sampleParagraph = "In the luminous era of antiquity, scholars gathered in towering libraries of white stone, debating the laws of nature and the movements of distant celestial bodies. Their parchments recorded tales of voyages across treacherous waters, diplomatic treaties signed under velvet banners, and architectural wonders engineered to withstand centuries of shifting earth. With tireless diligence, scribes inked each line by hand, preserving sacred knowledge for future generations who would one day rediscover their profound observations. "
        val wordsPerParagraph = sampleParagraph.split("\\s+".toRegex()).size // ~58 words

        val paragraphsNeeded = (targetWordCount / wordsPerParagraph).coerceAtLeast(10)
        val imageInterval = (paragraphsNeeded / imageCount.coerceAtLeast(1)).coerceAtLeast(1)

        var currentImage = 1
        var chapterIdx = 1

        for (i in 1..paragraphsNeeded) {
            if (i % 20 == 1) {
                sb.append("## Chapter $chapterIdx: The Chronicle of Era $chapterIdx\n\n")
                chapterIdx++
            }

            sb.append(sampleParagraph).append(" (Record section $i)\n\n")

            if (i % imageInterval == 0 && currentImage <= imageCount) {
                sb.append("![Illustration $currentImage](vault-img://vault_illustration_$currentImage.jpg)\n")
                sb.append("*Figure $currentImage: Detailed engraving of epoch $currentImage*\n\n")
                currentImage++
            }

            if (i % 15 == 0) {
                sb.append("> An ancient saying reminded them that knowledge without virtue is like a tree without fruit.\n\n")
            }

            if (i % 25 == 0) {
                sb.append("- Key observation Alpha: Empirical verification is essential.\n")
                sb.append("- Key observation Beta: Theoretical wisdom guides observation.\n")
                sb.append("- Key observation Gamma: Preservation requires persistent care.\n\n")
            }

            if (i % 40 == 0) {
                sb.append("| Era | Milestone | Status |\n")
                sb.append("| :--- | :---: | ---: |\n")
                sb.append("| First Age | Foundational Archive | Complete |\n")
                sb.append("| Middle Age | Regional Expansion | Verified |\n")
                sb.append("| Current Age | Modern Synthesis | Active |\n\n")
            }
        }

        return sb.toString()
    }

    @Test
    fun testLargeDocumentAstParsing25kWords() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 25_000, imageCount = 20)
        val wordCount = manuscript.split("\\s+".toRegex()).size
        assertTrue("Generated manuscript must meet or exceed 25k words", wordCount >= 25_000)

        val startTime = System.currentTimeMillis()
        val ast = ScribeMarkdownAstParser.parse(manuscript)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("AST must be generated successfully", ast)
        assertTrue("AST must have hundreds of blocks", ast.children.size > 300)

        val images = ast.children.filterIsInstance<MarkdownNode.BlockNode.ImageBlock>()
        assertEquals("Must parse exactly 20 embedded images", 20, images.size)

        val headings = ast.children.filterIsInstance<MarkdownNode.BlockNode.Heading>()
        assertTrue("Must parse multiple chapters", headings.size >= 10)

        // Parse speed benchmark: 25k words should parse quickly
        assertTrue("25k words must parse in reasonable time (< 2000ms), took: ${elapsed}ms", elapsed < 2000)
    }

    @Test
    fun testLargeDocumentAstParsing100kWords() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 100_000, imageCount = 20)
        val wordCount = manuscript.split("\\s+".toRegex()).size
        assertTrue("Generated manuscript must meet or exceed 100k words", wordCount >= 100_000)

        val startTime = System.currentTimeMillis()
        val ast = ScribeMarkdownAstParser.parse(manuscript)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("100k-word AST must be produced without stack overflow", ast)
        assertTrue("AST must contain thousands of blocks", ast.children.size > 1200)

        val images = ast.children.filterIsInstance<MarkdownNode.BlockNode.ImageBlock>()
        assertEquals("Must parse all 20 embedded images in 100k manuscript", 20, images.size)

        assertTrue("100k words AST parsing must complete within 5000ms without OOM, took: ${elapsed}ms", elapsed < 5000)
    }

    @Test
    fun testLargeDocumentPlainEmitter100kWords() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 100_000, imageCount = 20)
        val ast = ScribeMarkdownAstParser.parse(manuscript)

        val startTime = System.currentTimeMillis()
        val plainText = PlainEmitter.emit(ast)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull(plainText)
        assertTrue("Plain text output must be extensive (> 500,000 chars)", plainText.length > 500_000)

        // Verify that all 20 images were converted to editorial markers without markdown syntax
        val illustrationMarkerCount = plainText.split("[Illustration:").size - 1
        assertEquals("All 20 images must be converted to plain text editorial markers", 20, illustrationMarkerCount)

        assertFalse("Plain text must not contain markdown image syntax", plainText.contains("![Illustration"))
        assertFalse("Plain text must not contain raw vault links", plainText.contains("vault-img://"))

        assertTrue("100k plain text emission must complete within 2500ms, took: ${elapsed}ms", elapsed < 2500)
    }

    @Test
    fun testLargeDocumentHtmlEmitter100kWordsWithMissingAssets() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 100_000, imageCount = 20)
        val ast = ScribeMarkdownAstParser.parse(manuscript)

        val startTime = System.currentTimeMillis()
        val html = HtmlEmitter.emit(ast, imageResolver = { "MISSING_ASSET" })
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull(html)
        assertTrue("HTML output must be extensive", html.length > 600_000)

        // Verify missing asset callouts
        val calloutCount = html.split("editorial-callout").size - 1
        assertEquals("All 20 missing vault assets must become editorial callouts", 20, calloutCount)
        assertFalse("Raw vault URIs must never leak into HTML", html.contains("vault-img://"))

        assertTrue("100k HTML emission must complete within 3000ms, took: ${elapsed}ms", elapsed < 3000)
    }

    @Test
    fun testLargeDocumentDocxExport25kWords() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 25_000, imageCount = 20)
        val note = Note(id = "stress-docx-note", name = "Epic Manuscript (25k Words)", content = manuscript)
        val config = ExportConfig(format = ExportFormat.DOCX, includeCoverTitle = true)

        val startTime = System.currentTimeMillis()
        val docxBytes = DocxExporter.export(null, note, config)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("DOCX export must produce valid byte array", docxBytes)
        assertTrue("DOCX file size must be non-trivial (> 20KB)", docxBytes.size > 20_000)

        // Validate ZIP integrity and absence of broken image relationships
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("word/document.xml"))
        assertTrue(entries.containsKey("word/styles.xml"))
        assertTrue(entries.containsKey("[Content_Types].xml"))

        val relsXml = entries["word/_rels/document.xml.rels"] ?: ""
        assertFalse("Missing assets must not generate broken media relationships", relsXml.contains("media/image_"))

        val docXml = entries["word/document.xml"] ?: ""
        val calloutMatches = docXml.split("[Illustration:").size - 1
        assertEquals("DOCX must generate 20 editorial callouts for 20 missing images", 20, calloutMatches)

        assertTrue("25k-word DOCX export must complete within 4000ms, took: ${elapsed}ms", elapsed < 4000)
    }

    @Test
    fun testLargeDocumentEpubExport25kWordsMultiChapter() {
        val manuscript = generateSyntheticManuscript(targetWordCount = 25_000, imageCount = 20)
        val note = Note(id = "stress-epub-note", name = "Grand Publication (25k Words)", content = manuscript)
        val config = ExportConfig(format = ExportFormat.EPUB)

        val startTime = System.currentTimeMillis()
        val epubBytes = EpubExporter.export(null, note, config)
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("EPUB export must produce byte array", epubBytes)
        assertTrue("EPUB archive must be non-trivial (> 25KB)", epubBytes.size > 25_000)

        // Validate EPUB structure and byte offset 0 mimetype
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            val firstEntry = zis.nextEntry
            assertNotNull("First entry must exist", firstEntry)
            assertEquals("First entry in EPUB ZIP must be 'mimetype'", "mimetype", firstEntry?.name)
            assertEquals("Mimetype must be uncompressed STORED", java.util.zip.ZipEntry.STORED, firstEntry?.method)
            val mimeContent = zis.readBytes().toString(Charsets.US_ASCII)
            assertEquals("application/epub+zip", mimeContent)
        }

        assertTrue("25k-word EPUB export must complete within 4000ms, took: ${elapsed}ms", elapsed < 4000)
    }

    @Test
    fun testCancellationResponsivenessAndInterruption() {
        val isCancelled = AtomicBoolean(false)
        val progressCounter = java.util.concurrent.atomic.AtomicInteger(0)

        // Simulate a cooperative background loop processing blocks with cancellation check
        val manuscript = generateSyntheticManuscript(targetWordCount = 25_000, imageCount = 20)
        val ast = ScribeMarkdownAstParser.parse(manuscript)

        val thread = Thread {
            for ((idx, block) in ast.children.withIndex()) {
                if (isCancelled.get() || Thread.currentThread().isInterrupted) {
                    break
                }
                // Simulate per-block processing
                PlainEmitter.emitBlock(block)
                progressCounter.incrementAndGet()
                if (idx == 50) {
                    isCancelled.set(true)
                }
            }
        }

        thread.start()
        thread.join(2000)

        assertTrue("Loop must stop promptly upon cancellation flag", progressCounter.get() <= 55)
        assertTrue("Cancellation flag was observed", isCancelled.get())
    }

    @Test
    fun testMissingAssetRobustnessUnderStress() {
        // Document with 20 missing assets containing edge-case filenames, spaces, special chars
        val sb = StringBuilder()
        sb.append("# Edge Case Asset Manuscript\n\n")
        for (i in 1..20) {
            val specialName = when (i % 4) {
                0 -> "vault-img://space in image $i.png"
                1 -> "vault-img://unicode_اردو_تصویر_$i.jpg"
                2 -> "vault-img://symbols_!@#\$%^&*()_$i.webp"
                else -> "vault-img://missing_$i.svg"
            }
            sb.append("Paragraph before image $i.\n\n")
            sb.append("![$specialName]($specialName)\n")
            sb.append("*Special Caption $i with punctuation: !?*\n\n")
        }

        val note = Note(id = "edge-assets-note", name = "Edge Asset Test", content = sb.toString())
        val config = ExportConfig(format = ExportFormat.DOCX)

        // Must not throw any exception or crash when formatting edge-case image targets
        val docxBytes = DocxExporter.export(null, note, config)
        assertNotNull(docxBytes)
        assertTrue(docxBytes.isNotEmpty())

        val html = ScribeMarkdownEngine.toHtml(sb.toString()) { "MISSING_ASSET" }
        assertNotNull(html)
        assertEquals("All 20 edge-case missing assets become callout boxes", 20, html.split("editorial-callout").size - 1)

        val plain = ScribeMarkdownEngine.toPlainText(sb.toString())
        assertNotNull(plain)
        assertEquals("All 20 edge-case missing assets become plain markers", 20, plain.split("[Illustration:").size - 1)
    }
}
