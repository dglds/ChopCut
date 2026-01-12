package com.chopcut

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.model.TimeRange
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.pipeline.TranscodeOperations
import com.chopcut.data.repository.VideoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * Instrumented test for video operations (trim, compress, resize, crop)
 */
@RunWith(AndroidJUnit4::class)
class VideoOperationsTest {

    private lateinit var context: Context
    private lateinit var videoRepository: VideoRepository
    private lateinit var copyPipeline: CopyPipeline
    private lateinit var transcodeOperations: TranscodeOperations
    private lateinit var sampleVideoUri: Uri
    private lateinit var outputDir: File

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        videoRepository = VideoRepository(context)
        copyPipeline = CopyPipeline(context, videoRepository)
        transcodeOperations = TranscodeOperations(context, videoRepository)

        // Create output directory
        outputDir = File(context.externalCacheDir, "test_output")
        outputDir.mkdirs()
        Timber.plant(Timber.DebugTree())

        // Copy sample video from assets to cache
        val sampleFile = File(context.cacheDir, "sample.mp4")
        if (!sampleFile.exists()) {
            context.assets.open("sample-data/sample.mp4").use { input ->
                sampleFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        sampleVideoUri = Uri.fromFile(sampleFile)

        Timber.d("Sample video: ${sampleFile.absolutePath}, exists: ${sampleFile.exists()}, size: ${sampleFile.length()}")
    }

    @Test
    fun testVideoOperations() = runTest {
        // Get video metadata
        val metadata = videoRepository.getMetadata(sampleVideoUri)
        assertNotNull("Video metadata should not be null", metadata)
        Timber.d("Video metadata: ${metadata?.width}x${metadata?.height}, ${metadata?.durationMs}ms")

        if (metadata == null) {
            Timber.e("Failed to load video metadata")
            return@runTest
        }

        // Test 1: Trim (CopyPipeline)
        testTrim(metadata)

        // Test 2: Compress
        testCompress(metadata)

        // Test 3: Resize
        testResize(metadata)

        // Test 4: Crop
        testCrop(metadata)
    }

    private suspend fun testTrim(metadata: com.chopcut.data.model.VideoInfo) {
        Timber.d("=== Testing TRIM ===")
        val outputFile = File(outputDir, "test_trim.mp4")

        try {
            val range = TimeRange(startMs = 0, endMs = 5000)
            var completed = false

            copyPipeline.trim(sampleVideoUri, listOf(range))
                .collect { result ->
                    result.onSuccess { file ->
                        Timber.d("Trim SUCCESS: ${file.absolutePath}, size: ${file.length()}")
                        file.copyTo(outputFile, overwrite = true)
                        completed = true
                    }.onFailure { error ->
                        Timber.e(error, "Trim FAILED")
                    }
                }

            assertTrue("Trim should complete", completed)
            assertTrue("Trim output should exist", outputFile.exists())
            Timber.d("Trim output: ${outputFile.absolutePath}, ${outputFile.length()} bytes")
        } catch (e: Exception) {
            Timber.e(e, "Trim exception")
        }
    }

    private suspend fun testCompress(metadata: com.chopcut.data.model.VideoInfo) {
        Timber.d("=== Testing COMPRESS ===")
        val outputFile = File(outputDir, "test_compress.mp4")

        try {
            val targetBitrate = 2_000_000
            var completed = false

            transcodeOperations.compress(sampleVideoUri, targetBitrate)
                .collect { result ->
                    result.onSuccess { file ->
                        Timber.d("Compress SUCCESS: ${file.absolutePath}, size: ${file.length()}")
                        file.copyTo(outputFile, overwrite = true)
                        completed = true
                    }.onFailure { error ->
                        Timber.e(error, "Compress FAILED")
                    }
                }

            assertTrue("Compress should complete", completed)
            assertTrue("Compress output should exist", outputFile.exists())
            Timber.d("Compress output: ${outputFile.absolutePath}, ${outputFile.length()} bytes")
        } catch (e: Exception) {
            Timber.e(e, "Compress exception")
        }
    }

    private suspend fun testResize(metadata: com.chopcut.data.model.VideoInfo) {
        Timber.d("=== Testing RESIZE ===")
        val outputFile = File(outputDir, "test_resize.mp4")

        try {
            val targetWidth = metadata.width / 2
            val targetHeight = metadata.height / 2
            var completed = false

            transcodeOperations.resize(sampleVideoUri, targetWidth, targetHeight)
                .collect { result ->
                    result.onSuccess { file ->
                        Timber.d("Resize SUCCESS: ${file.absolutePath}, size: ${file.length()}")
                        file.copyTo(outputFile, overwrite = true)
                        completed = true
                    }.onFailure { error ->
                        Timber.e(error, "Resize FAILED")
                    }
                }

            assertTrue("Resize should complete", completed)
            assertTrue("Resize output should exist", outputFile.exists())
            Timber.d("Resize output: ${outputFile.absolutePath}, ${outputFile.length()} bytes")
        } catch (e: Exception) {
            Timber.e(e, "Resize exception")
        }
    }

    private suspend fun testCrop(metadata: com.chopcut.data.model.VideoInfo) {
        Timber.d("=== Testing CROP ===")
        val outputFile = File(outputDir, "test_crop.mp4")

        try {
            // Crop center 50%
            val cropRect = RectF(0.25f, 0.25f, 0.75f, 0.75f)
            var completed = false

            transcodeOperations.crop(sampleVideoUri, cropRect)
                .collect { result ->
                    result.onSuccess { file ->
                        Timber.d("Crop SUCCESS: ${file.absolutePath}, size: ${file.length()}")
                        file.copyTo(outputFile, overwrite = true)
                        completed = true
                    }.onFailure { error ->
                        Timber.e(error, "Crop FAILED")
                    }
                }

            assertTrue("Crop should complete", completed)
            assertTrue("Crop output should exist", outputFile.exists())
            Timber.d("Crop output: ${outputFile.absolutePath}, ${outputFile.length()} bytes")
        } catch (e: Exception) {
            Timber.e(e, "Crop exception")
        }
    }
}
