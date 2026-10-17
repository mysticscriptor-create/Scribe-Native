package com.primaloptima.scribe.util.export

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PdfPrintDocumentBridge
import android.print.PrintAttributes
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.VaultImageManager
import java.io.File

/**
 * Handles HTML publication compilation and headless PDF generation using
 * Android's Print infrastructure and print-media CSS layouts.
 */
object HtmlPdfExporter {

    /**
     * Builds a self-contained HTML document with embedded styles and Base64 images.
     */
    fun buildHtmlDocument(
        context: Context,
        note: Note,
        config: ExportConfig,
        isForPrintPdf: Boolean = false
    ): String {
        val themeColors = ExportThemeMapper.resolveThemeColors(context)
        val title = note.name.ifBlank { "Untitled Manuscript" }
        val titleEscaped = MarkdownUtil.escapeHtml(title)
        val css = ExportThemeMapper.generateExportCss(config, themeColors, isForPrintPdf = isForPrintPdf)

        val bodyHtml = MarkdownUtil.toHtmlBody(note.content) { src ->
            resolveImageToBase64(context, src, note.bookId, note.id) ?: src
        }

        val coverTitleHtml = if (config.includeCoverTitle) {
            """<h1 class="document-title">$titleEscaped</h1>"""
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>$titleEscaped</title>
              <style>
            $css
              </style>
            </head>
            <body>
              $coverTitleHtml
              $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun resolveImageToBase64(
        context: Context,
        uri: String,
        bookId: String?,
        noteId: String
    ): String? {
        val bytes = VaultImageManager.getImageBytes(context, uri, bookId, noteId) ?: return null
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mime = if (uri.endsWith(".png", true)) "image/png" else "image/jpeg"
        return "data:$mime;base64,$base64"
    }

    /**
     * Headless background generation of a genuine print-ready PDF file into [outputFile].
     * Must instantiate WebView on the Main Looper, then coordinates asynchronous printing.
     */
    fun renderPdfToFile(
        context: Context,
        note: Note,
        config: ExportConfig,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val html = buildHtmlDocument(context, note, config, isForPrintPdf = true)
                val webView = WebView(context.applicationContext)
                webView.settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    loadsImagesAutomatically = true
                }

                val mediaSize = when (config.paperSize) {
                    PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
                    PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
                }

                // 1 inch = 1000 mils, 25.4 mm = 1000 mils -> 1 mm = 39.37 mils
                val topMils = (config.marginPreset.topMm * 39.37f).toInt()
                val rightMils = (config.marginPreset.rightMm * 39.37f).toInt()
                val bottomMils = (config.marginPreset.bottomMm * 39.37f).toInt()
                val leftMils = (config.marginPreset.leftMm * 39.37f).toInt()

                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(mediaSize)
                    .setResolution(PrintAttributes.Resolution("scribe_pdf", "Scribe 300 DPI", 300, 300))
                    .setMinMargins(PrintAttributes.Margins(leftMils, topMils, rightMils, bottomMils))
                    .build()

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        try {
                            val jobName = ExportHelper.sanitize(note.name)
                            val adapter = view.createPrintDocumentAdapter(jobName)
                            PdfPrintDocumentBridge.print(adapter, printAttributes, outputFile, onComplete)
                        } catch (t: Throwable) {
                            onComplete(false, t)
                        }
                    }
                }

                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                onComplete(false, t)
            }
        }
    }
}
