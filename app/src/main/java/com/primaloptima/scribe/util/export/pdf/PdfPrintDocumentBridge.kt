package com.primaloptima.scribe.util.export.pdf

import android.content.Context
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.ExportConfig
import java.io.File

/**
 * Public PDF bridge in [com.primaloptima.scribe.util.export.pdf].
 * Completely eliminates any package-private placement in android.* namespace
 * ensuring 100% legal compliance with Android 14+ strict runtime classloader verification.
 */
object PdfPrintDocumentBridge {
    fun render(
        context: Context,
        note: Note,
        config: ExportConfig,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        PrintedPdfDocumentEngine.renderPdfToFile(context, note, config, outputFile, onComplete)
    }
}
