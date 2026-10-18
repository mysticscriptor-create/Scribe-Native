package com.primaloptima.scribe.util.export

/**
 * Unified Single Source of Truth (SSoT) entry point for Scribe Markdown processing.
 * Connects AST parsing directly to standard emitters across HTML, PDF, DOCX, EPUB, and TXT.
 */
object ScribeMarkdownEngine {

    /**
     * Parses markdown text into the structured [MarkdownNode.Document] AST.
     */
    fun parse(markdown: String): MarkdownNode.Document {
        return ScribeMarkdownAstParser.parse(markdown)
    }

    /**
     * Converts markdown text into well-formed semantic HTML/XHTML5.
     */
    fun toHtml(
        markdown: String,
        imageResolver: ((String) -> String)? = null
    ): String = toHtml(markdown, "ltr", imageResolver)

    /**
     * Converts markdown text into well-formed semantic HTML/XHTML5 with explicit document directionality.
     */
    fun toHtml(
        markdown: String,
        docDirection: String,
        imageResolver: ((String) -> String)? = null
    ): String {
        val document = parse(markdown)
        return HtmlEmitter.emit(document, imageResolver, docDirection)
    }

    /**
     * Converts markdown text into clean, unformatted plain prose with editorial markers.
     */
    fun toPlainText(markdown: String): String {
        val document = parse(markdown)
        return PlainEmitter.emit(document)
    }
}
