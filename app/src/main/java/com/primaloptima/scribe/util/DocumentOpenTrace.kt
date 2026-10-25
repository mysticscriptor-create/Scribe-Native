package com.primaloptima.scribe.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight performance tracing around all major document opening stages.
 * Measures time spent in local storage, external providers, Kotlin code,
 * inlay hint computation, Sora layout, first visible frame, and background analysis.
 *
 * Never logs user document contents.
 */
object DocumentOpenTrace {
    private const val TAG = "DocumentOpenTrace"

    class Trace(
        val noteId: String,
        val startTimeNs: Long = System.nanoTime()
    ) {
        private val marks = ConcurrentHashMap<String, Long>()

        fun mark(stage: String) {
            val now = System.nanoTime()
            marks[stage] = now
            val elapsedMs = (now - startTimeNs) / 1_000_000.0
            try {
                Log.d(TAG, "[$noteId] Stage: $stage at +${String.format(java.util.Locale.US, "%.2f", elapsedMs)}ms")
            } catch (_: Throwable) {
                // Safe in unit tests where android.util.Log is not mocked
            }
        }

        fun getElapsedMs(stage: String): Double? {
            val markNs = marks[stage] ?: return null
            return (markNs - startTimeNs) / 1_000_000.0
        }

        fun finish(summary: String = "") {
            val totalMs = (System.nanoTime() - startTimeNs) / 1_000_000.0
            try {
                Log.i(TAG, "[$noteId] COMPLETED in ${String.format(java.util.Locale.US, "%.2f", totalMs)}ms. $summary")
            } catch (_: Throwable) {
                // Safe in unit tests where android.util.Log is not mocked
            }
        }
    }

    private val activeTrace = AtomicReference<Trace?>(null)

    fun start(noteId: String): Trace {
        val trace = Trace(noteId)
        activeTrace.set(trace)
        trace.mark("document_selected")
        return trace
    }

    fun mark(stage: String) {
        activeTrace.get()?.mark(stage)
    }

    fun mark(noteId: String, stage: String) {
        val current = activeTrace.get()
        if (current?.noteId == noteId) {
            current.mark(stage)
        }
    }

    fun finish(noteId: String, summary: String = "") {
        val current = activeTrace.get()
        if (current?.noteId == noteId) {
            current.finish(summary)
        }
    }

    fun getElapsedMs(noteId: String, stage: String): Double? {
        val current = activeTrace.get()
        return if (current?.noteId == noteId) current.getElapsedMs(stage) else null
    }

    fun current(): Trace? = activeTrace.get()
}
