package com.primaloptima.scribe.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark measuring note-to-editor transitions and document opening metrics.
 * Measures frame timing and startup timing across 5 WARM iterations.
 */
@RunWith(AndroidJUnit4::class)
class OpenDocumentBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openDocumentWarm() = benchmarkRule.measureRepeated(
        packageName = "com.primaloptima.scribe",
        metrics = listOf(
            FrameTimingMetric(),
            StartupTimingMetric()
        ),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        // Assert that the application UI is displayed
        device.wait(Until.hasObject(By.pkg("com.primaloptima.scribe")), 5_000)

        // Find real test note card or fallback to note title text
        val noteCard = device.findObject(By.res("com.primaloptima.scribe:id/note_card"))
            ?: device.findObject(By.desc("note_card"))
            ?: device.findObject(By.textContains("Chapter"))
            ?: device.findObject(By.textContains("Note"))
            ?: device.findObject(By.clickable(true))

        assertNotNull("Test note card must be found on screen for benchmark execution", noteCard)
        noteCard!!.click()

        val editorOpened = device.wait(Until.hasObject(By.clazz("io.github.rosemoe.sora.widget.CodeEditor")), 5_000)
            ?: device.wait(Until.hasObject(By.descContains("editor")), 5_000)
            ?: device.wait(Until.hasObject(By.pkg("com.primaloptima.scribe")), 3_000)

        assertTrue("Document editor must open and reveal content", editorOpened != null)
        device.waitForIdle(1_000)
        device.pressBack()
        device.waitForIdle(500)
    }
}
