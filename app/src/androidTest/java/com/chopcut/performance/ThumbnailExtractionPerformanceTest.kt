package com.chopcut.performance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.data.thumbnail.ThumbnailExtractorBatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testes de performance para extração de thumbnails
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailExtractionPerformanceTest {
    // ... rest of the properties remain same ...

    @Test
    fun testExtractionCountAccuracy() = runBlocking {
        kotlinx.coroutines.withTimeout(60_000L) { // Timeout de 60s
            // 1. Obter qualquer vídeo disponível
            val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10) // Fallback para 15s se não houver vídeo
            
            // 2. Extrair a duração real do vídeo
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
            } catch (e: Exception) {
                Timber.e(e, "Erro ao obter metadata do video no teste de assert")
            }
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15000L
            retriever.release()
            
            val durationSeconds = durationMs / 1000.0

            // 3. Definir intervalo e calcular quantidade esperada
            val intervalMs = 1000L // 1 thumbnail por segundo
            val expectedThumbnailCount = (durationMs / intervalMs).toInt()
            val positions = (0 until expectedThumbnailCount).map { it * intervalMs }
            
            println("🧪 Iniciando Assert Test Dinâmico...")
            println("📹 Duração Real do Vídeo: ${String.format("%.1f", durationSeconds)} segundos")
            println("🎯 Calculado/Esperado: $expectedThumbnailCount thumbnails (intervalo de ${intervalMs}ms)")

            // 4. Executar a extração em lote
            val thumbnails = thumbnailExtractorBatch.extractBatch(
                uri = videoUri,
                positionsMs = positions,
                width = 320,
                height = 180
            )

            // 5. ASSERT: Validar se a quantidade extraída bate com o cálculo da duração
            println("✅ Total Extraído: ${thumbnails.size} thumbnails")
            
            assertEquals(
                "Falha na extração: Era esperado $expectedThumbnailCount thumbs para um vídeo de ${durationSeconds}s, mas retornou ${thumbnails.size}.",
                expectedThumbnailCount, 
                thumbnails.size
            )
            
            println("✨ Teste de assertiva PASSOU: O lote gerou a contagem exata para a timeline do vídeo.")
        }
    }

    @Test
    fun testSingleThumbnailExtraction() = runBlocking {
        // Usa vídeo de amostra ou cria um sintético
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        // Testa extração de thumbnail único em diferentes posições
        val positions = listOf(0L, 2500L, 5000L, 7500L, 10000L)

        positions.forEach { positionMs ->
            performanceMeasurer.measure(
                operationName = "Extract Single Thumbnail",
                metadata = mapOf(
                    "position" to positionMs,
                    "width" to 320,
                    "height" to 180
                )
            ) {
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = positionMs,
                    width = 320,
                    height = 180
                )
            }
        }

        // Gera relatório
        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "SingleThumbnailExtraction",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testBatchThumbnailExtraction() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        // Testa extração em batch com diferentes quantidades
        val batchSizes = listOf(5, 10, 20, 50)

        batchSizes.forEach { batchSize ->
            val positions = (0 until batchSize).map { it * 500L }

            performanceMeasurer.measure(
                operationName = "Extract Batch Thumbnails",
                metadata = mapOf(
                    "batchSize" to batchSize,
                    "width" to 320,
                    "height" to 180
                )
            ) {
                thumbnailExtractorBatch.extractBatch(
                    uri = videoUri,
                    positionsMs = positions,
                    width = 320,
                    height = 180
                )
            }
        }

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "BatchThumbnailExtraction",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testThumbnailExtractionComparison() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        val positions = (0 until 10).map { it * 1000L }

        // Método 1: Extração individual (ThumbnailExtractor)
        performanceMeasurer.measure(
            operationName = "Individual Extraction (10 thumbnails)",
            metadata = mapOf("method" to "ThumbnailExtractor")
        ) {
            positions.forEach { positionMs ->
                thumbnailExtractor.extractAt(
                    uri = videoUri,
                    positionMs = positionMs,
                    width = 320,
                    height = 180
                )
            }
        }

        // Método 2: Extração em batch (ThumbnailExtractorBatch)
        performanceMeasurer.measure(
            operationName = "Batch Extraction (10 thumbnails)",
            metadata = mapOf("method" to "ThumbnailExtractorBatch")
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
            testName = "ThumbnailExtractionComparison",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testDifferentThumbnailSizes() = runBlocking {
        val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 10)

        val sizes = listOf(
            Pair(160, 90),   // Baixa resolução
            Pair(320, 180),  // Média resolução
            Pair(640, 360),  // Alta resolução
            Pair(1280, 720)  // Muito alta resolução
        )

        sizes.forEach { (width, height) ->
            performanceMeasurer.measure(
                operationName = "Extract Thumbnail",
                metadata = mapOf(
                    "width" to width,
                    "height" to height,
                    "resolution" to "${width}x${height}"
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

        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        val report = performanceReporter.generateReport(
            testName = "ThumbnailSizeComparison",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testStressTest() = runBlocking {
        val videoUri = testVideoProvider.getSampleVideoUri()
            ?: testVideoProvider.createTestVideo(durationSeconds = 30)

        // Stress test: extrair 100 thumbnails
        val positions = (0 until 100).map { it * 300L }

        performanceMeasurer.measure(
            operationName = "Stress Test - 100 Thumbnails (Batch)",
            metadata = mapOf(
                "thumbnailCount" to 100,
                "method" to "batch"
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
            testName = "ThumbnailExtractionStressTest",
            metrics = metrics,
            statistics = statistics
        )

        println(statistics)
        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(metrics, statistics))
    }

    @Test
    fun testExtractAllThumbnailsForTimeline() = runBlocking {
        kotlinx.coroutines.withTimeout(60_000L) { // Timeout de segurança: 60 segundos máximos
            // Obter um vídeo de teste longo (ex: 60 segundos)
            val videoUri = testVideoProvider.getTestVideoUri(durationSeconds = 60)
            
            // Obter a duração real do vídeo para calcular as posições exatas
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
            } catch (e: Exception) {
                Timber.e(e, "Erro ao obter metadata do video no teste")
            }
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 60000L
            retriever.release()

            // Simular a extração de thumbnails para cobrir toda a timeline do vídeo
            // Definido para extrair 1 thumbnail a cada 1 segundo (1000ms)
            val intervalMs = 1000L
            val positionsCount = (durationMs / intervalMs).toInt()
            val positions = (0 until positionsCount).map { it * intervalMs }

            Timber.d("Iniciando extração de \$positionsCount thumbnails para toda a timeline do vídeo...")

            performanceMeasurer.measure(
                operationName = "Extract All Timeline Thumbnails",
                metadata = mapOf(
                    "thumbnailCount" to positionsCount,
                    "intervalMs" to intervalMs,
                    "videoDurationMs" to durationMs,
                    "method" to "batch"
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
                testName = "ExtractAllTimelineThumbnails",
                metrics = metrics,
                statistics = statistics
            )

            println(statistics)
            println(report)
            println("\n📊 JSON REPORT:\n")
            println(performanceReporter.generateJsonString(metrics, statistics))
        }
    }
}
