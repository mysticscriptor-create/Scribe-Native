package com.primaloptima.scribe.util.export.pdf

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File

/**
 * Public PDF print bridge located in [com.primaloptima.scribe.util.export.pdf].
 * Completely eliminates the illegal package-private placement in android.* namespace
 * ensuring compliance with Android 14+ strict runtime classloader verification.
 */
object PdfPrintDocumentBridge {
    fun print(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        val cancellationSignal = CancellationSignal()
        try {
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
        } catch (_: Throwable) {}

        try {
            adapter.onStart()
        } catch (_: Throwable) {}

        adapter.onLayout(
            attributes,
            attributes,
            cancellationSignal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    try {
                        val pfd = ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
                        )
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            pfd,
                            cancellationSignal,
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<out PageRange>?) {
                                    try {
                                        pfd.close()
                                    } catch (_: Throwable) {}
                                    try {
                                        adapter.onFinish()
                                    } catch (_: Throwable) {}
                                    onComplete(true, null)
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    try {
                                        pfd.close()
                                    } catch (_: Throwable) {}
                                    try {
                                        adapter.onFinish()
                                    } catch (_: Throwable) {}
                                    onComplete(false, RuntimeException(error?.toString() ?: "PDF write failed"))
                                }

                                override fun onWriteCancelled() {
                                    try {
                                        pfd.close()
                                    } catch (_: Throwable) {}
                                    try {
                                        adapter.onFinish()
                                    } catch (_: Throwable) {}
                                    onComplete(false, RuntimeException("PDF write cancelled"))
                                }
                            }
                        )
                    } catch (t: Throwable) {
                        try {
                            adapter.onFinish()
                        } catch (_: Throwable) {}
                        onComplete(false, t)
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    try {
                        adapter.onFinish()
                    } catch (_: Throwable) {}
                    onComplete(false, RuntimeException(error?.toString() ?: "PDF layout failed"))
                }

                override fun onLayoutCancelled() {
                    try {
                        adapter.onFinish()
                    } catch (_: Throwable) {}
                    onComplete(false, RuntimeException("PDF layout cancelled"))
                }
            },
            Bundle().apply { putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, false) }
        )
    }
}
