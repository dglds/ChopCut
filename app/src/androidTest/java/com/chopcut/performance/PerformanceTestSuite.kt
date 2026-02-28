package com.chopcut.performance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.data.thumbnail.ThumbnailExtractorBatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Suite completa de testes de performance
 * Executa todos os testes e gera relatório consolidado
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTestSuite {

    private lateinit var context: Context
    private lateinit var videoRepository: VideoRepository
    private lateinit var thumbnailExtractor: ThumbnailExtractor
    private lateinit var thumbnailExtractorBatch: ThumbnailExtractorBatch
    private lateinit var testVideoProvider: TestVideoProvider
    private lateinit var performanceMeasurer: PerformanceMeasurer
    private lateinit var performanceReporter: PerformanceReporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoRepository = VideoRepository(context)
        thumbnailExtractor = ThumbnailExtractor(context)
        thumbnailExtractorBatch = ThumbnailExtractorBatch(context)
        testVideoProvider = TestVideoProvider(context)
        performanceMeasurer = PerformanceMeasurer()
        performanceReporter = PerformanceReporter(context)

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        println("\n" + "═".repeat(60))
        println("  CHOPCUT PERFORMANCE TEST SUITE")
        println("═".repeat(60) + "\n")
    }

    @After
    fun tearDown() {
        // Gera relatório final
        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()

        val report = performanceReporter.generateReport(
            testName = "CompletePerformanceTestSuite",
            metrics = metrics,
            statistics = statistics
        )

        println("\n" + "═".repeat(60))
        println("  TEST SUITE COMPLETED")
        println("═".repeat(60))
        println(statistics)
        println(report)

        // Imprime JSON no console também
        println("\n" + "─".repeat(60))
        println("JSON REPORT:")
        println("─".repeat(60))
        val jsonReport = performanceReporter.generateJsonString(metrics, statistics)
        println(jsonReport)
        println("─".repeat(60) + "\n")

        performanceMeasurer.clear()
    }

    @Test
    fun runCompletePerformanceTestSuite() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 30)

        println("📹 Using video: $videoUri")
        println("📁 Test videos dir: ${TestVideoProvider.TEST_VIDEOS_DIR}\n")

        // ========== METADATA EXTRACTION ==========
        println("🔍 Testing Metadata Extraction...")
        performanceMeasurer.measure(
            operationName = "Video Metadata Extraction"
        ) {
            videoRepository.getMetadata(videoUri)
        }

        performanceMeasurer.measure(
            operationName = "Audio Metadata Extraction"
        ) {
            videoRepository.getAudioMetadata(videoUri)
        }

        // ========== SINGLE THUMBNAIL EXTRACTION ==========
        println("\n📸 Testing Single Thumbnail Extraction...")
        val singleThumbPositions = listOf(0L, 5000L, 10000L, 15000L, 20000L)

        singleThumbPositions.forEach { positionMs ->
            performanceMeasurer.measure(
                operationName = "Single Thumbnail @ ${positionMs}ms",
                metadata = mapOf("position" to positionMs)
            ) {
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = positionMs,
                    width = 320,
                    height = 180
                )
            }
        }

        // ========== BATCH THUMBNAIL EXTRACTION ==========
        println("\n🎬 Testing Batch Thumbnail Extraction...")
        val batchSizes = listOf(5, 10, 25, 50)

        batchSizes.forEach { batchSize ->
            val positions = (0 until batchSize).map { it * 500L }

            performanceMeasurer.measure(
                operationName = "Batch Extraction ($batchSize thumbnails)",
                metadata = mapOf("batchSize" to batchSize)
            ) {
                thumbnailExtractorBatch.extractBatch(
                    uri = videoUri,
                    positionsMs = positions,
                    width = 320,
                    height = 180
                )
            }
        }

        // ========== COMPARISON: INDIVIDUAL vs BATCH ==========
        println("\n⚖️ Testing Individual vs Batch Comparison...")
        val comparisonPositions = (0 until 20).map { it * 1000L }

        performanceMeasurer.measure(
            operationName = "Individual Extraction (20x)",
            metadata = mapOf("method" to "individual", "count" to 20)
        ) {
            comparisonPositions.forEach { positionMs ->
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = positionMs,
                    width = 320,
                    height = 180
                )
            }
        }

        performanceMeasurer.measure(
            operationName = "Batch Extraction (20x)",
            metadata = mapOf("method" to "batch", "count" to 20)
        ) {
            thumbnailExtractorBatch.extractBatch(
                uri = videoUri,
                positionsMs = comparisonPositions,
                width = 320,
                height = 180
            )
        }

        // ========== DIFFERENT RESOLUTIONS ==========
        println("\n🎨 Testing Different Resolutions...")
        val resolutions = listOf(
            Triple(160, 90, "Low"),
            Triple(320, 180, "Medium"),
            Triple(640, 360, "High"),
            Triple(1280, 720, "Very High")
        )

        resolutions.forEach { (width, height, quality) ->
            performanceMeasurer.measure(
                operationName = "Thumbnail $quality ($width x $height)",
                metadata = mapOf(
                    "width" to width,
                    "height" to height,
                    "quality" to quality
                )
            ) {
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = 5000,
                    width = width,
                    height = height
                )
            }
        }

        // ========== FILE OPERATIONS ==========
        println("\n💾 Testing File Operations...")
        val (tempFile, _) = performanceMeasurer.measure(
            operationName = "Copy to Temp File"
        ) {
            videoRepository.copyToTempFile(videoUri)
        }

        performanceMeasurer.measure(
            operationName = "Create Temp File"
        ) {
            videoRepository.createTempFile()
        }

        // Cleanup
        tempFile?.let { videoRepository.deleteTempFile(it) }

        println("\n✅ All tests completed!")
    }

    @Test
    fun runStressTest() = runBlocking {
        println("\n" + "⚡".repeat(30))
        println("  STRESS TEST MODE")
        println("⚡".repeat(30) + "\n")

        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 60)

        // Extrai 200 thumbnails em batch
        val positions = (0 until 200).map { it * 300L }

        performanceMeasurer.measure(
            operationName = "STRESS TEST - 200 Thumbnails",
            metadata = mapOf(
                "thumbnailCount" to 200,
                "videoDuration" to "60s"
            )
        ) {
            thumbnailExtractorBatch.extractBatch(
                uri = videoUri,
                positionsMs = positions,
                width = 320,
                height = 180
            )
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "StressTest_200Thumbnails",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }
}
