package com.chopcut.performance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chopcut.data.thumbnail.ThumbnailStripManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testes de performance focados na etapa de Renderização das thumbnails.
 * 
 * Avalia o tempo que a CPU/GPU leva para processar os bitmaps extraídos
 * e desenhá-los no Canvas, simulando o comportamento de scroll da Timeline.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailRenderingPerformanceTest {

    private lateinit var context: Context
    private lateinit var stripManager: ThumbnailStripManager
    private lateinit var testVideoProvider: TestVideoProvider
    private lateinit var performanceMeasurer: PerformanceMeasurer
    private lateinit var performanceReporter: PerformanceReporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Simulando densidade 3x (típico de aparelhos modernos: 60dp de largura -> 180px)
        val thumbWidth = 320
        val thumbHeight = 180
        stripManager = ThumbnailStripManager(context, thumbWidth, thumbHeight)
        
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
    fun testNativeCanvasRenderingPerformance() = runBlocking {
        // 1. Setup: Pega o vídeo real usando o caminho físico direto para evitar bloqueios do Scoped Storage no runner
        val videoFile = java.io.File("/sdcard/Movies/ChopCut/tests/1-30_seg.mp4")
        require(videoFile.exists()) { "Vídeo real não encontrado em ${videoFile.absolutePath}" }
        
        val videoUri = android.net.Uri.fromFile(videoFile)
        val videoInfo = testVideoProvider.getVideoInfo(videoFile)
        
        var testStrip: Bitmap? = null
        val durationMs = videoInfo.durationMs

        
        performanceMeasurer.measure(
            operationName = "1. Setup - Mock Strip Assembly"
        ) {
            // Cria a strip inteiramente em memória preenchendo com pixels
            // para isolar o teste do MediaMetadataRetriever que possui restrições de permissão no Runner
            val framesInSegment = stripManager.thumbsPerStrip
            testStrip = Bitmap.createBitmap(stripManager.thumbWidth * framesInSegment, stripManager.thumbHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(testStrip!!)

            // Pinta com um checkerboard simulando o conteúdo
            val paint1 = Paint().apply { color = android.graphics.Color.DKGRAY }
            val paint2 = Paint().apply { color = android.graphics.Color.LTGRAY }

            for (i in 0 until framesInSegment) {
                val paint = if (i % 2 == 0) paint1 else paint2
                canvas.drawRect(
                    (i * stripManager.thumbWidth).toFloat(),
                    0f,
                    ((i + 1) * stripManager.thumbWidth).toFloat(),
                    stripManager.thumbHeight.toFloat(),
                    paint
                )
            }
        }

        requireNotNull(testStrip) { "Falha ao extrair strip de teste" }

        // 2. Preparar ambiente de renderização offline simulando a Timeline
        // A Timeline visível costuma ter a largura da tela do celular (ex: 1080px)
        val displayWidth = 1080
        val displayHeight = 144 // 48dp * 3
        val offlineBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(offlineBitmap)

        val srcRect = Rect()
        val dstRect = Rect()
        
        val renderPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            isDither = true
        }

        // 3. Simular o evento de renderização de múltiplos quadros (como em um scroll violento a 60fps)
        // 60 frames = 1 segundo de animação suave
        val framesToSimulate = 60
        val framesInStrip = stripManager.thumbsPerStrip // 10 (padrão)

        performanceMeasurer.measure(
            operationName = "2. Render - 60 FPS Scroll Simulation",
            metadata = mapOf(
                "framesSimulated" to framesToSimulate,
                "stripConfig" to (testStrip!!.config?.name ?: "UNKNOWN"),
                "paintFlags" to "FILTER_BITMAP_FLAG"
            )
        ) {
            for (frame in 0 until framesToSimulate) {
                // Simula o cálculo de offset baseado no scroll atual
                val scrollOffsetX = (frame * 10) % displayWidth

                // Desenha todos os frames visíveis na strip atual para a tela
                for (frameIdx in 0 until framesInStrip) {
                    val srcX = frameIdx * stripManager.thumbWidth
                    srcRect.set(
                        srcX, 0,
                        srcX + stripManager.thumbWidth, stripManager.thumbHeight
                    )

                    // Calcula destino na tela simulada
                    val dstX = (frameIdx * stripManager.thumbWidth) - scrollOffsetX

                    // Otimização de culling (não desenha se estiver fora da tela)
                    if (dstX + stripManager.thumbWidth < 0 || dstX > displayWidth) continue

                    dstRect.set(
                        dstX, 0,
                        dstX + stripManager.thumbWidth, stripManager.thumbHeight
                    )

                    canvas.drawBitmap(testStrip!!, srcRect, dstRect, renderPaint)
                }
            }
        }

        // 4. Teste de Stress: Quantas strips conseguimos desenhar em 16ms?
        // 16ms é a "budget" máxima para manter 60fps constantes.
        var drawsIn16ms = 0
        
        performanceMeasurer.measure(
            operationName = "3. Render - 16ms Budget Stress Test"
        ) {
            val stressStartTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - stressStartTime < 16) {
                srcRect.set(0, 0, stripManager.thumbWidth, stripManager.thumbHeight)
                dstRect.set(0, 0, stripManager.thumbWidth, stripManager.thumbHeight)
                canvas.drawBitmap(testStrip!!, srcRect, dstRect, renderPaint)
                drawsIn16ms++
            }
        }

        // Reciclar bitmaps
        testStrip?.recycle()
        offlineBitmap.recycle()

        // 5. Relatórios
        val statistics = performanceMeasurer.getStatistics()
        val metrics = performanceMeasurer.getAllMetrics()
        
        // Injetar o throughput nas métricas
        val updatedMetrics = metrics.toMutableList()
        updatedMetrics.add(
            PerformanceMetrics(
                operationName = "Throughput Result",
                durationMs = 16,
                memoryUsedKb = 0,
                success = true,
                metadata = mapOf("drawCallsPer16ms" to drawsIn16ms)
            )
        )

        val report = performanceReporter.generateReport(
            testName = "ThumbnailRenderingPerformance",
            metrics = updatedMetrics,
            statistics = statistics
        )

        println("\n" + "=".repeat(60))
        println(" RENDERING PERFORMANCE RESULTS ")
        println("=".repeat(60))
        println(" Draws executed within 16ms (60fps budget): $drawsIn16ms")
        if (drawsIn16ms > 100) {
            println(" ✅ EXCELLENT! Far exceeds the needed ~10 draws per frame.")
        } else if (drawsIn16ms > 20) {
            println(" ⚠️ GOOD. Comfortably meets requirements.")
        } else {
            println(" ❌ POOR. At risk of dropping frames during fast scrolls.")
        }
        println("=".repeat(60) + "\n")

        println(report)
        println("\n📊 JSON REPORT:\n")
        println(performanceReporter.generateJsonString(updatedMetrics, statistics))
    }
}
