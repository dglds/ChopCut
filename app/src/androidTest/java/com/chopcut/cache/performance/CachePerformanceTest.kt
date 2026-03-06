package com.chopcut.cache.performance

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CachePerformanceTest {

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

    private fun copyTestVideo(targetName: String = "test_perf.mp4"): File {
        val file = File(activityRule.scenario.getTempDir(), targetName)
        testContext.assets.open("sample.mp4").use { it.copyTo(file.outputStream()) }
        return file
    }

    @Test
    fun extractionTimeBenchmark() {
        val uri = "content://test/perf_extraction"
        val segmentCount = 20
        val durationMs = 60_000L

        Timber.tag("TEST_PERF").i("Iniciando benchmark de extração — segmentos=$segmentCount")

        val times = mutableListOf<Long>()

        runBlocking {
            repeat(segmentCount) { segIdx ->
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
                times.add(elapsed)

                Timber.tag("TEST_PERF").d("Segmento $segIdx: ${elapsed}ms")
            }
        }

        val avg = times.average()
        val p50 = times.sorted()[times.size / 2]
        val p90 = times.sorted()[(times.size * 0.9).toInt().coerceAtMost(times.size - 1)]
        val p99 = times.sorted()[(times.size * 0.99).toInt().coerceAtMost(times.size - 1)]
        val min = times.minOrNull() ?: 0
        val max = times.maxOrNull() ?: 0
        val total = times.sum()

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    BENCHMARK DE EXTRAÇÃO — RESULTADOS                  ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Segmentos           : $segmentCount                     ║")
        println("║  Tempo total         : ${total}ms                        ║")
        println("║  Média               : ${avg.toInt()}ms                  ║")
        println("║  Mínimo              : ${min}ms                         ║")
        println("║  Máximo              : ${max}ms                         ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  P50 (mediana)       : ${p50}ms                         ║")
        println("║  P90                 : ${p90}ms                         ║")
        println("║  P99                 : ${p99}ms                         ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun cacheHitRateBenchmark() {
        val uris = (1..10).map { "content://test/perf_hit_$it" }
        val segmentCount = 5
        val durationMs = 15_000L
        val rounds = 3

        Timber.tag("TEST_PERF").i("Iniciando benchmark de hit rate — vídeos=${uris.size}, rounds=$rounds")

        val hits = mutableListOf<Int>()
        val misses = mutableListOf<Int>()

        repeat(rounds) { round ->
            Timber.tag("TEST_PERF").i("Round $round")

            var roundHits = 0
            var roundMisses = 0

            runBlocking {
                uris.forEach { uri ->
                    repeat(segmentCount) { segIdx ->
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
                            roundHits++
                        } else {
                            roundMisses++
                        }
                    }
                }
            }

            hits.add(roundHits)
            misses.add(roundMisses)

            val totalRequests = roundHits + roundMisses
            val hitRate = if (totalRequests > 0) (roundHits * 100.0 / totalRequests) else 0.0
            Timber.tag("TEST_PERF").i("Round $round: ${roundHits} hits, ${roundMisses} misses, hit rate=${String.format("%.1f", hitRate)}%")
        }

        val totalHits = hits.sum()
        val totalMisses = misses.sum()
        val totalRequests = totalHits + totalMisses
        val overallHitRate = if (totalRequests > 0) (totalHits * 100.0 / totalRequests) else 0.0

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    BENCHMARK DE CACHE HIT RATE — RESULTADOS             ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Vídeos             : ${uris.size}                        ║")
        println("║  Segmentos/vídeo   : $segmentCount                       ║")
        println("║  Rounds             : $rounds                             ║")
        println("╠══════════════════════════════════════════════════════════╣")

        hits.forEachIndexed { idx, h ->
            println("║  Round ${idx + 1}            : $h hits, ${misses[idx]} misses          ║")
        }

        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Total hits         : $totalHits                         ║")
        println("║  Total misses       : $totalMisses                       ║")
        println("║  Total requests     : $totalRequests                     ║")
        println("║  Hit rate overall   : ${String.format("%.1f", overallHitRate)}%                    ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun memoryUsageBenchmark() {
        val uri = "content://test/perf_memory"
        val segmentCount = 50
        val durationMs = 150_000L

        Timber.tag("TEST_PERF").i("Iniciando benchmark de uso de memória — segmentos=$segmentCount")

        val memorySnapshots = mutableListOf<Long>()

        runBlocking {
            repeat(segmentCount) { segIdx ->
                val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

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

                val afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                val memoryUsed = afterMemory - beforeMemory

                memorySnapshots.add(memoryUsed)

                if (segIdx % 10 == 0) {
                    Timber.tag("TEST_PERF").d("Segmento $segIdx: +${memoryUsed / 1024}KB")
                }
            }
        }

        val avgMemory = memorySnapshots.average()
        val maxMemory = memorySnapshots.maxOrNull() ?: 0
        val minMemory = memorySnapshots.minOrNull() ?: 0
        val totalMemory = memorySnapshots.sum()

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    BENCHMARK DE USO DE MEMÓRIA — RESULTADOS            ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Segmentos           : $segmentCount                     ║")
        println("║  Memória total       : ${totalMemory / 1024}KB            ║")
        println("║  Média/segmento     : ${(avgMemory / 1024).toInt()}KB      ║")
        println("║  Mínimo              : ${minMemory / 1024}KB              ║")
        println("║  Máximo              : ${maxMemory / 1024}KB              ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun diskWritePerformanceBenchmark() {
        val uris = (1..20).map { "content://test/perf_disk_$it" }
        val segmentCount = 1
        val durationMs = 3_000L

        Timber.tag("TEST_PERF").i("Iniciando benchmark de escrita em disco — vídeos=${uris.size}")

        val writeTimes = mutableListOf<Long>()

        runBlocking {
            uris.forEach { uri ->
                val start = System.currentTimeMillis()

                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = 0,
                    durationMs = durationMs,
                    segmentCount = segmentCount,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    adaptiveStrips = false
                )

                val elapsed = System.currentTimeMillis() - start
                writeTimes.add(elapsed)

                Timber.tag("TEST_PERF").d("Escrita ${uri.split("_").last()}: ${elapsed}ms")
            }
        }

        val avgWrite = writeTimes.average()
        val p90Write = writeTimes.sorted()[(writeTimes.size * 0.9).toInt().coerceAtMost(writeTimes.size - 1)]
        val slowWrites = writeTimes.count { it > 100 }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    BENCHMARK DE ESCRITA EM DISCO — RESULTADOS          ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Arquivos escritos    : ${uris.size}                     ║")
        println("║  Tempo médio          : ${avgWrite.toInt()}ms               ║")
        println("║  P90                  : ${p90Write}ms                    ║")
        println("║  Escritas >100ms      : $slowWrites                       ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }

    @Test
    fun parallelVsSequentialComparison() {
        val uri = "content://test/perf_parallel"
        val segmentCount = 10
        val durationMs = 30_000L

        Timber.tag("TEST_PERF").i("Iniciando comparação paralelo vs sequencial")

        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)

        val sequentialTimes = mutableListOf<Long>()

        runBlocking {
            repeat(segmentCount) { segIdx ->
                val start = System.currentTimeMillis()

                ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = segIdx,
                    durationMs = durationMs,
                    segmentCount = segmentCount,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    adaptiveStrips = false
                )

                sequentialTimes.add(System.currentTimeMillis() - start)
            }
        }

        ThumbnailCacheManager.clearMemoryCache()
        ThumbnailCacheManager.clearAll(testContext)

        val startParallel = System.currentTimeMillis()

        runBlocking {
            val jobs = (0 until segmentCount).map { segIdx ->
                kotlinx.coroutines.async {
                    ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segIdx,
                        durationMs = durationMs,
                        segmentCount = segmentCount,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )
                }
            }
            jobs.awaitAll()
        }

        val parallelTime = System.currentTimeMillis() - startParallel
        val sequentialTime = sequentialTimes.sum()
        val speedup = sequentialTime.toDouble() / parallelTime

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    PARALELO VS SEQUENCIAL — RESULTADOS                  ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Segmentos           : $segmentCount                     ║")
        println("║  Tempo sequencial    : ${sequentialTime}ms                ║")
        println("║  Tempo paralelo      : ${parallelTime}ms                 ║")
        println("║  Speedup             : ${String.format("%.2f", speedup)}x                        ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Média segm. seq.    : ${(sequentialTimes.average()).toInt()}ms                      ║")
        println("║  Média segm. par.    : ${(parallelTime / segmentCount)}ms                      ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }
}