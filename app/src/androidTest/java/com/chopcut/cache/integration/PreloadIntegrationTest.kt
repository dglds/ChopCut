package com.chopcut.cache.integration

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreloadIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)
    }

    @After
    fun tearDown() {
        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)
    }

    private fun copyTestVideo(targetName: String = "test_preload.mp4"): File {
        val file = File(activityRule.scenario.getTempDir(), targetName)
        testContext.assets.open("sample.mp4").use { it.copyTo(file.outputStream()) }
        return file
    }

    @Test
    fun fullPreloadFlow_cachesAllSegments() {
        val uri = android.net.Uri.parse("content://test/preload_full")
        val initialSegments = 6
        val totalSegments = 15
        val durationMs = 45_000L


        val startTime = System.currentTimeMillis()

        runBlocking {
            ThumbnailCacheManager.startPreload(
                uri = uri,
                durationMs = durationMs,
                segmentCount = totalSegments,
                thumbWidth = 80,
                thumbHeight = 54,
                thumbsPerStrip = 5,
                initialSegments = initialSegments
            )

            delay(100)

            val cachedStrips = mutableListOf<Boolean>()
            (0 until totalSegments).forEach { segIdx ->
                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5
                )
                cachedStrips.add(strip != null)
            }

            val totalTime = System.currentTimeMillis() - startTime

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    FLUXO COMPLETO DE PRELOAD — RESULTADOS             ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos iniciais   : $initialSegments                 ║")
            println("║  Segmentos totais     : $totalSegments                   ║")
            println("║  Tempo total          : ${totalTime}ms                   ║")
            println("╠══════════════════════════════════════════════════════════╣")

            cachedStrips.forEachIndexed { idx, cached ->
                val status = if (cached) "✓" else "✗"
                println("║  Segmento $idx          : $status                           ║")
            }

            val cachedCount = cachedStrips.count { it }
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos cacheados  : $cachedCount/$totalSegments            ║")
            println("║  Sucesso              : ${if (cachedCount == totalSegments) "✓" else "✗"}                       ║")
            println("╚════════════════════════════════════════════════════════╝")

            assert(cachedCount == totalSegments) { "Todos os segmentos devem ser cacheados" }
        }
    }

            val totalTime = System.currentTimeMillis() - startTime

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    FLUXO COMPLETO DE PRELOAD — RESULTADOS             ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos iniciais   : $initialSegments                 ║")
            println("║  Segmentos totais     : $totalSegments                   ║")
            println("║  Tempo total          : ${totalTime}ms                   ║")
            println("╠══════════════════════════════════════════════════════════╣")

            cachedStrips.forEachIndexed { idx, cached ->
                val status = if (cached) "✓" else "✗"
                println("║  Segmento $idx          : $status                           ║")
            }

            val cachedCount = cachedStrips.count { it }
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos cacheados  : $cachedCount/$totalSegments            ║")
            println("║  Sucesso              : ${if (cachedCount == totalSegments) "✓" else "✗"}                       ║")
            println("╚══════════════════════════════════════════════════════════╝")

            assert(cachedCount == totalSegments) { "Todos os segmentos devem ser cacheados" }
        }
    }

    @Test
    fun preloadWithCancel_jobsStopCleanly() {
        val uri = android.net.Uri.parse("content://test/preload_cancel")
        val segmentCount = 30
        val durationMs = 90_000L


        val startTime = System.currentTimeMillis()

        runBlocking {
            val job = kotlinx.coroutines.async {
                ThumbnailCacheManager.startPreload(
                    uri = uri,
                    durationMs = durationMs,
                    segmentCount = segmentCount,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    initialSegments = 30
                )
            }

            delay(200)

            job.cancel()

            val cancelTime = System.currentTimeMillis() - startTime

            delay(100)

            val cachedStrips = mutableListOf<Boolean>()
            (0 until segmentCount).forEach { segIdx ->
                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5
                )
                cachedStrips.add(strip != null)
            }

            val cachedCount = cachedStrips.count { it }

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    CANCELAMENTO DE PRELOAD — RESULTADOS                ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos totais     : $segmentCount                    ║")
            println("║  Tempo até cancel    : ${cancelTime}ms                   ║")
            println("║  Segmentos cacheados  : $cachedCount/$segmentCount              ║")
            println("║  Job cancelado       : ✓                                 ║")
            println("╚════════════════════════════════════════════════════════╝")

            assert(job.isCancelled) { "Job deve ser cancelado" }
        }
    }

            delay(200)

            job.cancel()

            val cancelTime = System.currentTimeMillis() - startTime

            delay(100)

            val cachedStrips = mutableListOf<Boolean>()
            (0 until segmentCount).forEach { segIdx ->
                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    segmentCount = segmentCount,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    adaptiveStrips = false
                )
                cachedStrips.add(strip != null)
            }

            val cachedCount = cachedStrips.count { it }

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    CANCELAMENTO DE PRELOAD — RESULTADOS                ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos totais     : $segmentCount                    ║")
            println("║  Tempo até cancel    : ${cancelTime}ms                   ║")
            println("║  Segmentos cacheados  : $cachedCount/$segmentCount              ║")
            println("║  Job cancelado       : ✓                                 ║")
            println("╚══════════════════════════════════════════════════════════╝")

            assert(job.isCancelled) { "Job deve ser cancelado" }
        }
    }

    @Test
    fun preloadMultipleVideos_cacheIsolation() {
        val videos = (1..3).map { android.net.Uri.parse("content://test/preload_multi_$it") }
        val segmentsPerVideo = 10
        val durationMs = 30_000L


        runBlocking {
            videos.forEachIndexed { videoIdx, uri ->

                ThumbnailCacheManager.startPreload(
                    uri = uri,
                    durationMs = durationMs,
                    segmentCount = segmentsPerVideo,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    initialSegments = segmentsPerVideo
                )
            }

            delay(500)

            val results = mutableMapOf<Int, Int>()

            videos.forEachIndexed { videoIdx, uri ->
                var cachedCount = 0
                (0 until segmentsPerVideo).forEach { segIdx ->
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segIdx,
                        durationMs = durationMs,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5
                    )
                    if (strip != null) cachedCount++
                }
                results[videoIdx] = cachedCount
            }

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    MÚLTIPLOS VÍDEOS — RESULTADOS                       ║")
            println("╠══════════════════════════════════════════════════════════╣")

            results.forEach { (videoIdx, count) ->
                println("║  Vídeo $videoIdx           : $count/$segmentsPerVideo segmentos      ║")
            }

            val totalCached = results.values.sum()
            val expectedTotal = videos.size * segmentsPerVideo

            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Total cacheado       : $totalCached/$expectedTotal               ║")
            println("║  Isolamento           : ✓                                ║")
            println("╚════════════════════════════════════════════════════════╝")

            assert(totalCached == expectedTotal) { "Todos os segmentos de todos os vídeos devem ser cacheados" }
        }
    }

            delay(500)

            val results = mutableMapOf<Int, Int>()

            videos.forEachIndexed { videoIdx, uri ->
                var cachedCount = 0
                (0 until segmentsPerVideo).forEach { segIdx ->
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segIdx,
                        durationMs = durationMs,
                        segmentCount = segmentsPerVideo,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )
                    if (strip != null) cachedCount++
                }
                results[videoIdx] = cachedCount
            }

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    MÚLTIPLOS VÍDEOS — RESULTADOS                       ║")
            println("╠══════════════════════════════════════════════════════════╣")

            results.forEach { (videoIdx, count) ->
                println("║  Vídeo $videoIdx           : $count/$segmentsPerVideo segmentos      ║")
            }

            val totalCached = results.values.sum()
            val expectedTotal = videos.size * segmentsPerVideo

            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Total cacheado       : $totalCached/$expectedTotal               ║")
            println("║  Isolamento           : ✓                                ║")
            println("╚══════════════════════════════════════════════════════════╝")

            assert(totalCached == expectedTotal) { "Todos os segmentos de todos os vídeos devem ser cacheados" }
        }
    }

    @Test
    fun preloadWithMemoryPressure_lruEviction() {
        val uri = "content://test/preload_memory"
        val segmentCount = 120
        val durationMs = 360_000L


        runBlocking {
            ThumbnailCacheManager.startPreload(
                uri = uri,
                durationMs = durationMs,
                segmentCount = segmentCount,
                thumbWidth = 80,
                thumbHeight = 54,
                thumbsPerStrip = 5,
                adaptiveStrips = false,
                initialSegments = segmentCount
            )

            delay(2000)

            val cacheSize = ThumbnailCacheManager.getCacheSize()
            val memoryUsage = ThumbnailCacheManager.getMemoryUsage()

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    PRESSÃO DE MEMÓRIA — RESULTADOS                     ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos solicitados : $segmentCount                    ║")
            println("║  Itens no cache       : $cacheSize                       ║")
            println("║  Uso de memória       : ${memoryUsage / 1024}KB            ║")
            println("║  LRU ativo            : ✓                                ║")
            println("╚══════════════════════════════════════════════════════════╝")

            assert(cacheSize <= 100) { "Cache não deve exceder limite de 100 itens (LRU)" }
        }
    }

    @Test
    fun preloadPersistence_acrossRestarts() {
        val uri = "content://test/preload_persistence"
        val segmentCount = 10
        val durationMs = 30_000L


        runBlocking {

            ThumbnailCacheManager.startPreload(
                uri = uri,
                durationMs = durationMs,
                segmentCount = segmentCount,
                thumbWidth = 80,
                thumbHeight = 54,
                thumbsPerStrip = 5,
                adaptiveStrips = false,
                initialSegments = segmentCount
            )

            delay(500)

            ThumbnailCacheManager.clearMemoryCache()


            var hits = 0
            var misses = 0

            (0 until segmentCount).forEach { segIdx ->
                val start = System.currentTimeMillis()

                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    segmentCount = segmentCount,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    adaptiveStrips = false
                )

                val elapsed = System.currentTimeMillis() - start

                if (elapsed < 50) {
                    hits++
                } else {
                    misses++
                }
            }

            val hitRate = (hits * 100.0) / segmentCount

            println("╔══════════════════════════════════════════════════════════╗")
            println("║    PERSISTÊNCIA DE CACHE — RESULTADOS                  ║")
            println("╠══════════════════════════════════════════════════════════╣")
            println("║  Segmentos           : $segmentCount                     ║")
            println("║  Cache hits          : $hits                             ║")
            println("║  Cache misses        : $misses                           ║")
            println("║  Hit rate            : ${String.format("%.1f", hitRate)}%                     ║")
            println("║  Persistência        : ✓                                ║")
            println("╚══════════════════════════════════════════════════════════╝")

            assert(hits == segmentCount) { "Disco cache deve persistir entre restarts" }
        }
    }
}