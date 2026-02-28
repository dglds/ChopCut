package com.chopcut.performance

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.data.thumbnail.ThumbnailExtractorBatch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testa TODOS os vídeos da pasta Movies/ChopCut/tests
 */
@RunWith(AndroidJUnit4::class)
class MultiVideoPerformanceTest {

    private lateinit var context: Context
    private lateinit var videoRepository: VideoRepository
    private lateinit var thumbnailExtractor: ThumbnailExtractor
    private lateinit var thumbnailExtractorBatch: ThumbnailExtractorBatch
    private lateinit var testVideoProvider: TestVideoProvider
    private lateinit var performanceReporter: PerformanceReporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoRepository = VideoRepository(context)
        thumbnailExtractor = ThumbnailExtractor(context)
        thumbnailExtractorBatch = ThumbnailExtractorBatch(context)
        testVideoProvider = TestVideoProvider(context)
        performanceReporter = PerformanceReporter(context)

        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @Test
    fun testAllVideosInTestFolder() = runBlocking {
        println("\n" + "═".repeat(60))
        println("  MULTI-VIDEO PERFORMANCE TEST")
        println("  Target Dir: ${TestVideoProvider.TEST_VIDEOS_DIR}")
        println("═".repeat(60) + "\n")

        val videos = testVideoProvider.getAllTestVideos().filter { !it.name.startsWith("synthetic_") }
        val videoResults = mutableListOf<Pair<VideoTestInfo, PerformanceStatistics>>()

        if (videos.isEmpty()) {
            val errorMsg = "❌ Nenhum vídeo real encontrado! Coloque vídeos em ${TestVideoProvider.TEST_VIDEOS_DIR} ou ${TestVideoProvider.FALLBACK_VIDEOS_DIR} antes de rodar os testes."
            println(errorMsg)
            throw AssertionError(errorMsg)
        } else {
            println("📁 Found ${videos.size} real video(s) to test:")
            videos.forEachIndexed { index, file ->
                val info = testVideoProvider.getVideoInfo(file)
                println("  ${index + 1}. ${info.file.name} (${info.resolution}, ${String.format("%.1f", info.durationSeconds)}s)")
            }
            println()

            videos.forEachIndexed { index, file ->
                println("\n" + "─".repeat(60))
                println("  TESTING VIDEO ${index + 1}/${videos.size}: ${file.name}")
                println("─".repeat(60) + "\n")

                try {
                    val (info, stats) = testSingleVideoWithStats(Uri.fromFile(file), file.name)
                    videoResults.add(Pair(info, stats))
                } catch (e: Exception) {
                    println("❌ FAILED to test ${file.name}: ${e.message}")
                    Timber.e(e, "Failed to test ${file.name}")
                }
            }
        }

        // Comparação final entre vídeos
        if (videoResults.size > 1) {
            printVideoComparison(videoResults)
        }

        println("\n" + "═".repeat(60))
        println("  ALL VIDEOS TESTED")
        println("═".repeat(60))
        println("📊 Reports saved to: /storage/emulated/0/Android/data/com.chopcut/files/performance_reports")
        println("📄 View HTML report: app/build/reports/androidTests/connected/debug/index.html")
        println("═".repeat(60) + "\n")
    }

    private suspend fun testSingleVideoWithStats(videoUri: Uri, videoName: String): Pair<VideoTestInfo, PerformanceStatistics> {
        val performanceMeasurer = PerformanceMeasurer()
        val videoInfo = testVideoProvider.getVideoInfo(
            if (videoUri.scheme == "file") {
                java.io.File(videoUri.path!!)
            } else {
                java.io.File(videoUri.toString())
            }
        )

        // Header do vídeo
        println("\n" + "╔".repeat(60))
        println("║ VIDEO: ${videoInfo.file.name}")
        println("╠".repeat(60))
        println("║ 📐 Resolution: ${videoInfo.resolution}")
        println("║ ⏱️  Duration: ${String.format("%.1f", videoInfo.durationSeconds)}s")
        println("║ 📦 Size: ${String.format("%.2f", videoInfo.sizeMB)}MB")
        println("║ 🔄 Rotation: ${videoInfo.rotation}°")
        println("╚".repeat(60))
        println()

        // Executar testes (mesmo código de antes)
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

        println("📸 Testing Thumbnail Extraction...")
        val positions = listOf(0L, videoInfo.durationMs / 4, videoInfo.durationMs / 2,
                               videoInfo.durationMs * 3 / 4, videoInfo.durationMs - 1000)

        positions.forEach { positionMs ->
            performanceMeasurer.measure(
                operationName = "Single Thumbnail",
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

        val batchPositions = (0 until 10).map { it * (videoInfo.durationMs / 10) }
        performanceMeasurer.measure(
            operationName = "Batch Extraction (10 thumbnails)",
            metadata = mapOf("batchSize" to 10)
        ) {
            thumbnailExtractorBatch.extractBatch(
                uri = videoUri,
                positionsMs = batchPositions,
                width = 320,
                height = 180
            )
        }

        println("🎨 Testing Different Resolutions...")
        val resolutions = listOf(
            Triple(160, 90, "Low"),
            Triple(320, 180, "Medium"),
            Triple(640, 360, "High")
        )

        resolutions.forEach { (width, height, quality) ->
            performanceMeasurer.measure(
                operationName = "Thumbnail $quality",
                metadata = mapOf(
                    "width" to width,
                    "height" to height,
                    "resolution" to "${width}x${height}",
                    "quality" to quality
                )
            ) {
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = videoInfo.durationMs / 2,
                    width = width,
                    height = height
                )
            }
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()

        // Imprimir relatório detalhado
        printDetailedReport(videoInfo, metrics, statistics)

        val report = performanceReporter.generateReport(
            testName = videoName.substringBeforeLast("."),
            metrics = metrics,
            statistics = statistics,
            videoInfo = videoInfo.toString()
        )

        println(report)
        println("===JSON_START===")
        println(performanceReporter.generateJsonString(metrics, statistics, videoInfo.toString()))
        println("===JSON_END===")

        return Pair(videoInfo, statistics)
    }

    private fun printDetailedReport(
        videoInfo: VideoTestInfo,
        metrics: List<PerformanceMetrics>,
        statistics: PerformanceStatistics
    ) {
        println("\n" + "╔".repeat(60))
        println("║ DETAILED RESULTS - ${videoInfo.file.name}")
        println("╠".repeat(60))
        println()

        val metadataOps = metrics.filter { it.operationName.contains("Metadata") }
        val singleThumbOps = metrics.filter { it.operationName.contains("Single Thumbnail") }
        val batchOps = metrics.filter { it.operationName.contains("Batch") }
        val resolutionOps = metrics.filter { it.operationName.contains("Thumbnail") && it.metadata.containsKey("resolution") }

        if (metadataOps.isNotEmpty()) {
            println("📋 METADATA EXTRACTION")
            println("─".repeat(60))
            metadataOps.forEach { metric ->
                println("  ${metric.operationName.padEnd(30)} ${metric.durationMs}ms")
            }
            println()
        }

        if (singleThumbOps.isNotEmpty()) {
            println("📸 SINGLE THUMBNAIL EXTRACTION")
            println("─".repeat(60))
            singleThumbOps.forEach { metric ->
                val pos = metric.metadata["position"] ?: "?"
                println("  Position ${pos.toString().padEnd(10)}ms  →  ${metric.durationMs}ms")
            }
            val avgSingle = singleThumbOps.map { it.durationMs }.average()
            println("  ${"Average".padEnd(22)}  →  ${String.format("%.1f", avgSingle)}ms")
            println()
        }

        if (batchOps.isNotEmpty()) {
            println("🎬 BATCH EXTRACTION")
            println("─".repeat(60))
            batchOps.forEach { metric ->
                val batchSize = metric.metadata["batchSize"] ?: "?"
                val perThumb = metric.durationMs.toDouble() / (batchSize.toString().toIntOrNull() ?: 1)
                println("  ${batchSize} thumbnails  →  ${metric.durationMs}ms total  (${String.format("%.1f", perThumb)}ms/thumb)")
            }
            println()
        }

        if (resolutionOps.isNotEmpty()) {
            println("🎨 RESOLUTION COMPARISON")
            println("─".repeat(60))
            resolutionOps.forEach { metric ->
                val res = metric.metadata["resolution"] ?: "?"
                val quality = metric.metadata["quality"] ?: ""
                println("  ${res.toString().padEnd(12)} ($quality)  →  ${metric.durationMs}ms")
            }
            println()
        }

        println("╚".repeat(60))
        println("\n" + statistics)

        println("\n" + "╔".repeat(60))
        println("║ PERFORMANCE ANALYSIS")
        println("╠".repeat(60))

        if (singleThumbOps.isNotEmpty() && batchOps.isNotEmpty()) {
            val avgSingle = singleThumbOps.map { it.durationMs }.average()
            val batch10 = batchOps.find { (it.metadata["batchSize"] as? Int) == 10 }
            if (batch10 != null) {
                val perThumbBatch = batch10.durationMs / 10.0
                val improvement = (avgSingle / perThumbBatch)
                println("║ Batch vs Individual:")
                println("║   • Individual: ${String.format("%.1f", avgSingle)}ms per thumbnail")
                println("║   • Batch (10): ${String.format("%.1f", perThumbBatch)}ms per thumbnail")
                println("║   • Improvement: ${String.format("%.1f", improvement)}x faster ⚡")
            }
        }

        val totalTime = statistics.totalDurationMs
        val opsPerSecond = (statistics.totalOperations.toDouble() / totalTime) * 1000
        println("║")
        println("║ Throughput: ${String.format("%.1f", opsPerSecond)} operations/second")
        println("╚".repeat(60))
    }

    private fun printVideoComparison(results: List<Pair<VideoTestInfo, PerformanceStatistics>>) {
        println("\n" + "╔".repeat(60))
        println("║ VIDEO COMPARISON SUMMARY")
        println("╠".repeat(60))
        println()

        println("📊 Performance by Video:")
        println("─".repeat(60))
        results.forEachIndexed { index, (info, stats) ->
            println("${index + 1}. ${info.file.name}")
            println("   Resolution: ${info.resolution}  |  Duration: ${String.format("%.1f", info.durationSeconds)}s")
            println("   Avg operation: ${String.format("%.1f", stats.averageDurationMs)}ms  |  Total: ${stats.totalDurationMs}ms")
            println()
        }

        val fastest = results.minByOrNull { it.second.averageDurationMs }
        val slowest = results.maxByOrNull { it.second.averageDurationMs }

        if (fastest != null && slowest != null && fastest != slowest) {
            println("🏆 Performance Winner:")
            println("   Fastest: ${fastest.first.file.name} (${String.format("%.1f", fastest.second.averageDurationMs)}ms avg)")
            println("   Slowest: ${slowest.first.file.name} (${String.format("%.1f", slowest.second.averageDurationMs)}ms avg)")
            val diff = slowest.second.averageDurationMs / fastest.second.averageDurationMs
            println("   Difference: ${String.format("%.1f", diff)}x")
        }

        println()
        println("╚".repeat(60))
    }

    private suspend fun testSingleVideo(videoUri: Uri, videoName: String) {
        val performanceMeasurer = PerformanceMeasurer()
        val videoInfo = testVideoProvider.getVideoInfo(
            if (videoUri.scheme == "file") {
                java.io.File(videoUri.path!!)
            } else {
                java.io.File(videoUri.toString())
            }
        )

        // Header do vídeo
        println("\n" + "╔".repeat(60))
        println("║ VIDEO: ${videoInfo.file.name}")
        println("╠".repeat(60))
        println("║ 📐 Resolution: ${videoInfo.resolution}")
        println("║ ⏱️  Duration: ${String.format("%.1f", videoInfo.durationSeconds)}s")
        println("║ 📦 Size: ${String.format("%.2f", videoInfo.sizeMB)}MB")
        println("║ 🔄 Rotation: ${videoInfo.rotation}°")
        println("╚".repeat(60))
        println()

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

        // ========== THUMBNAIL EXTRACTION ==========
        println("📸 Testing Thumbnail Extraction...")

        // Single thumbnails
        val positions = listOf(0L, videoInfo.durationMs / 4, videoInfo.durationMs / 2,
                               videoInfo.durationMs * 3 / 4, videoInfo.durationMs - 1000)

        positions.forEach { positionMs ->
            performanceMeasurer.measure(
                operationName = "Single Thumbnail",
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

        // Batch extraction
        val batchPositions = (0 until 10).map { it * (videoInfo.durationMs / 10) }

        performanceMeasurer.measure(
            operationName = "Batch Extraction (10 thumbnails)",
            metadata = mapOf("batchSize" to 10)
        ) {
            thumbnailExtractorBatch.extractBatch(
                uri = videoUri,
                positionsMs = batchPositions,
                width = 320,
                height = 180
            )
        }

        // Different resolutions
        println("🎨 Testing Different Resolutions...")
        val resolutions = listOf(
            Triple(160, 90, "Low"),
            Triple(320, 180, "Medium"),
            Triple(640, 360, "High")
        )

        resolutions.forEach { (width, height, quality) ->
            performanceMeasurer.measure(
                operationName = "Thumbnail $quality",
                metadata = mapOf(
                    "width" to width,
                    "height" to height,
                    "resolution" to "${width}x${height}"
                )
            ) {
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = videoInfo.durationMs / 2,
                    width = width,
                    height = height
                )
            }
        }

        // ========== GENERATE REPORT ==========
        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()

        val report = performanceReporter.generateReport(
            testName = videoName.substringBeforeLast("."),
            metrics = metrics,
            statistics = statistics,
            videoInfo = videoInfo.toString()
        )

        // Relatório detalhado por operação
        println("\n" + "╔".repeat(60))
        println("║ DETAILED RESULTS - ${videoInfo.file.name}")
        println("╠".repeat(60))
        println()

        // Agrupar métricas por tipo
        val metadataOps = metrics.filter { it.operationName.contains("Metadata") }
        val singleThumbOps = metrics.filter { it.operationName.contains("Single Thumbnail") }
        val batchOps = metrics.filter { it.operationName.contains("Batch") }
        val resolutionOps = metrics.filter { it.operationName.contains("Thumbnail") && it.metadata.containsKey("resolution") }

        // Metadata Extraction
        if (metadataOps.isNotEmpty()) {
            println("📋 METADATA EXTRACTION")
            println("─".repeat(60))
            metadataOps.forEach { metric ->
                println("  ${metric.operationName.padEnd(30)} ${metric.durationMs}ms")
            }
            println()
        }

        // Single Thumbnails
        if (singleThumbOps.isNotEmpty()) {
            println("📸 SINGLE THUMBNAIL EXTRACTION")
            println("─".repeat(60))
            singleThumbOps.forEach { metric ->
                val pos = metric.metadata["position"] ?: "?"
                println("  Position ${pos.toString().padEnd(10)}ms  →  ${metric.durationMs}ms")
            }
            val avgSingle = singleThumbOps.map { it.durationMs }.average()
            println("  ${"Average".padEnd(22)}  →  ${String.format("%.1f", avgSingle)}ms")
            println()
        }

        // Batch Extraction
        if (batchOps.isNotEmpty()) {
            println("🎬 BATCH EXTRACTION")
            println("─".repeat(60))
            batchOps.forEach { metric ->
                val batchSize = metric.metadata["batchSize"] ?: "?"
                val perThumb = metric.durationMs.toDouble() / (batchSize.toString().toIntOrNull() ?: 1)
                println("  ${batchSize} thumbnails  →  ${metric.durationMs}ms total  (${String.format("%.1f", perThumb)}ms/thumb)")
            }
            println()
        }

        // Resolution Comparison
        if (resolutionOps.isNotEmpty()) {
            println("🎨 RESOLUTION COMPARISON")
            println("─".repeat(60))
            resolutionOps.forEach { metric ->
                val res = metric.metadata["resolution"] ?: "?"
                val quality = metric.metadata["quality"] ?: ""
                println("  ${res.toString().padEnd(12)} ($quality)  →  ${metric.durationMs}ms")
            }
            println()
        }

        println("╚".repeat(60))

        // Estatísticas gerais
        println("\n" + statistics)

        // Análise de eficiência
        println("\n" + "╔".repeat(60))
        println("║ PERFORMANCE ANALYSIS")
        println("╠".repeat(60))

        if (singleThumbOps.isNotEmpty() && batchOps.isNotEmpty()) {
            val avgSingle = singleThumbOps.map { it.durationMs }.average()
            val batch10 = batchOps.find { (it.metadata["batchSize"] as? Int) == 10 }
            if (batch10 != null) {
                val perThumbBatch = batch10.durationMs / 10.0
                val improvement = (avgSingle / perThumbBatch)
                println("║ Batch vs Individual:")
                println("║   • Individual: ${String.format("%.1f", avgSingle)}ms per thumbnail")
                println("║   • Batch (10): ${String.format("%.1f", perThumbBatch)}ms per thumbnail")
                println("║   • Improvement: ${String.format("%.1f", improvement)}x faster ⚡")
            }
        }

        val totalTime = statistics.totalDurationMs
        val opsPerSecond = (statistics.totalOperations.toDouble() / totalTime) * 1000
        println("║")
        println("║ Throughput: ${String.format("%.1f", opsPerSecond)} operations/second")
        println("╚".repeat(60))

        println(report)
        println("===JSON_START===")
        println(performanceReporter.generateJsonString(metrics, statistics, videoInfo.toString()))
        println("===JSON_END===")
    }
}
