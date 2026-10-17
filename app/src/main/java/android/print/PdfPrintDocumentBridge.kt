package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Bridge helper located within [android.print] package to enable access
 * to package-private [PrintDocumentAdapter.LayoutResultCallback] and
 * [PrintDocumentAdapter.WriteResultCallback] constructors during headless PDF rendering.
 */
object PdfPrintDocumentBridge {
    fun print(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        outputFile: File,
        onComplete: (Boolean, Throwable?) -> Unit
    ) {
        val cancellationSignal = CancellationSignal()
        adapter.onLayout(
            null,
            attributes,
            cancellationSignal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    try {
                        if (outputFile.exists()) outputFile.delete()
                        val pfd = ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
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
                                    onComplete(true, null)
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    try {
                                        pfd.close()
                                    } catch (_: Throwable) {}
                                    onComplete(false, RuntimeException(error?.toString() ?: "PDF write failed"))
                                }

                                override fun onWriteCancelled() {
                                    try {
                                        pfd.close()
                                    } catch (_: Throwable) {}
                                    onComplete(false, RuntimeException("PDF write cancelled"))
                                }
                            }
                        )
                    } catch (t: Throwable) {
                        onComplete(false, t)
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    onComplete(false, RuntimeException(error?.toString() ?: "PDF layout failed"))
                }

                override fun onLayoutCancelled() {
                    onComplete(false, RuntimeException("PDF layout cancelled"))
                }
            },
            null as Bundle?
        )
    }
}
