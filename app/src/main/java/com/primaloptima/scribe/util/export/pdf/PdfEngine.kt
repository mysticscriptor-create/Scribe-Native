package com.primaloptima.scribe.util.export.pdf

import android.content.Context
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.ExportConfig
import java.io.File

/**
 * Standard contract isolating PDF document generation from caching and presentation layers.
 * Decouples PDF creation from WebView lifecycle and allows interchangeable layout engines.
 */
interface PdfEngine {
    fun renderPdfToFile(
        context: Context,
        note: Note,
        config: ExportConfig,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    )
}
