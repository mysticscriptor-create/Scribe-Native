package com.primaloptima.scribe.util.export.pdf

import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentAdapterBridge
import java.io.File

/**
 * Public PDF print bridge in [com.primaloptima.scribe.util.export.pdf].
 * Delegates to package-level adapter bridge while maintaining clean external architecture.
 */
object PdfPrintDocumentBridge {
    fun print(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        PrintDocumentAdapterBridge.print(adapter, attributes, outputFile, onComplete)
    }
}
