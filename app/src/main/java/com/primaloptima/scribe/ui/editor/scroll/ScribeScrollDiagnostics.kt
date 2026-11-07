package com.primaloptima.scribe.ui.editor.scroll

/**
 * Scoped diagnostic logging toggle and callback for scroll & viewport coordination.
 */
object ScribeScrollDiagnostics {
    var isDebugDiagnosticsEnabled: Boolean = false
    var onScrollDiagnosticLog: ((String) -> Unit)? = null

    fun log(tag: String, message: String) {
        if (isDebugDiagnosticsEnabled) {
            onScrollDiagnosticLog?.invoke("[$tag] $message")
        }
    }
}
