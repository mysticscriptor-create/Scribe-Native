package com.primaloptima.scribe.ui.components.export

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Sandboxed WebView preview component rendering exact HTML and EPUB chapter layouts
 * with full CSS typography, tables, and embedded images.
 */
@Composable
fun HtmlPreviewViewer(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface.toArgb()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(bgColor)
                    settings.apply {
                        javaScriptEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        loadsImagesAutomatically = true
                    }
                    webViewClient = object : WebViewClient() {}
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
            }
        )
    }
}
