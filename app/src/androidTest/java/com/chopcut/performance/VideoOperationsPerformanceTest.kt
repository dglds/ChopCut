package com.chopcut.performance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.repository.VideoRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testes de performance para operações de vídeo
 */
@RunWith(AndroidJUnit4::class)
class VideoOperationsPerformanceTest {

    private lateinit var context: Context
    private lateinit var videoRepository: VideoRepository
    private lateinit var testVideoProvider: TestVideoProvider
    private lateinit var performanceMeasurer: PerformanceMeasurer
    private lateinit var performanceReporter: PerformanceReporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoRepository = VideoRepository(context)
        testVideoProvider = TestVideoProvider(context)
        performanceMeasurer = PerformanceMeasurer()
        performanceReporter = PerformanceReporter(context)

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @After
    fun tearDown() {
        performanceMeasurer.clear()
    }

    @Test
    fun testMetadataExtraction() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        // Testa extração de metadata múltiplas vezes
        repeat(5) { iteration ->
            performanceMeasurer.measure(
                operationName = "Extract Video Metadata",
                metadata = mapOf("iteration" to iteration + 1)
            ) {
                videoRepository.getMetadata(videoUri)
            }
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "VideoMetadataExtraction",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testAudioMetadataExtraction() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        repeat(5) { iteration ->
            performanceMeasurer.measure(
                operationName = "Extract Audio Metadata",
                metadata = mapOf("iteration" to iteration + 1)
            ) {
                videoRepository.getAudioMetadata(videoUri)
            }
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "AudioMetadataExtraction",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testCopyToTempFile() = runBlocking {
        val videoUri = testVideoProvider.getSampleVideoUri()
            ?: testVideoProvider.createTestVideo(durationSeconds = 5)

        // Testa cópia para arquivo temporário
        val (tempFile, metric) = performanceMeasurer.measure(
            operationName = "Copy Video to Temp File"
        ) {
            videoRepository.copyToTempFile(videoUri)
        }

        // Limpa arquivo temporário
        tempFile?.let { videoRepository.deleteTempFile(it) }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "CopyVideoToTempFile",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testFileOperations() = runBlocking {
        // Testa criação de arquivo temporário
        performanceMeasurer.measure(
            operationName = "Create Temp File"
        ) {
            videoRepository.createTempFile()
        }

        // Testa múltiplas criações
        repeat(10) { iteration ->
            performanceMeasurer.measure(
                operationName = "Create Temp File (Batch)",
                metadata = mapOf("iteration" to iteration + 1)
            ) {
                videoRepository.createTempFile()
            }
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "FileOperations",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testCompleteWorkflow() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        // Simula workflow completo
        performanceMeasurer.measure(
            operationName = "Complete Workflow - Get Metadata"
        ) {
            videoRepository.getMetadata(videoUri)
        }

        performanceMeasurer.measure(
            operationName = "Complete Workflow - Get Audio Metadata"
        ) {
            videoRepository.getAudioMetadata(videoUri)
        }

        performanceMeasurer.measure(
            operationName = "Complete Workflow - Copy to Temp"
        ) {
            videoRepository.copyToTempFile(videoUri)
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "CompleteVideoWorkflow",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }
}
