package com.chopcut.audio

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.audio.WaveformExtractor
import com.chopcut.instrumentedTestHelpers.TimelineTestHelper
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioDecodeLoopTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Test: DecodeLoop performance with 15-minute video
     * Captures Trace events for Perfetto analysis
     *
     * Run with:
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.audio.AudioDecodeLoopTest
     *
     * Then capture with trace.sh or manual Perfetto setup
     */
    @Test
    fun testDecodeLoop15MinVideo() {
        android.os.Trace.beginSection("AudioDecodeLoopTest.15MinVideo")

        val testVideoFile = TimelineTestHelper.copyTestVideo(context, "sample15min.mp4")
        assertNotNull("Test video should be copied", testVideoFile)
        assertTrue("Test video should exist", testVideoFile!!.exists())

        val uri = Uri.fromFile(testVideoFile)
        val extractor = WaveformExtractor(context)

        val startTime = System.currentTimeMillis()
        android.os.Trace.beginSection("AudioDecodeLoopTest.extractRawPcmData")

        val result = runBlocking {
            extractor.extractRawPcmData(uri)
        }

        android.os.Trace.endSection()
        val elapsed = System.currentTimeMillis() - startTime

        android.os.Trace.setCounter("AudioDecodeLoopTest.pcmDataPoints", result.pcmSamples.size)
        android.os.Trace.setCounter("AudioDecodeLoopTest.elapsedMs", elapsed.toInt())

        println("DecodeLoop(15min): ${elapsed}ms, ${result.pcmSamples.size} PCM points")

        assertNotNull("Result should not be null", result)
        assertTrue("Result should have PCM data", result.pcmSamples.isNotEmpty())
        assertTrue("DecodeLoop should complete within 30s (KPI ~5s)", elapsed < 30000)

        android.os.Trace.endSection()
    }
}

// Workaround for coroutines in test
private fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
