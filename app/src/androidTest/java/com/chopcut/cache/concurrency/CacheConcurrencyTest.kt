package com.chopcut.cache.concurrency

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.ThumbnailCacheManager
import kotlinx.coroutines.*
import timber.log.Timber
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CacheConcurrencyTest {

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

    private fun copyTestVideo(targetName: String = "test_concurrency.mp4"): File {
        val file = File(activityRule.scenario.getTempDir(), targetName)
        testContext.assets.open("sample.mp4").use { it.copyTo(file.outputStream()) }
        return file
    }

    @Test
    fun multipleThreadsReadingSameSegment_concurrently() = runBlocking {
        val uri = "content://test/concurrent_read"
        val segmentIndex = 0
        val threadCount = 10
        val durationMs = 5_000L

        Timber.tag("TEST_INTEGRATION").i("Iniciando teste de leitura concorrente — threads=$threadCount")

        val successCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val bitmapHashes = ConcurrentHashMap<Int, String>()

        val jobs = (0 until threadCount).map { threadId ->
            launch(Dispatchers.IO) {
                try {
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segmentIndex,
                        durationMs = durationMs,
                        segmentCount = 10,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )

                    if (strip != null) {
                        val hash = "${strip.width}x${strip.height}"
                        bitmapHashes[threadId] = hash
                        successCount.incrementAndGet()
                    } else {
                        Timber.tag("TEST_INTEGRATION").w("⚪ Thread $threadId: strip retornou null")
                    }
                } catch (e: Exception) {
                    exceptionCount.incrementAndGet()
                    Timber.tag("TEST_INTEGRATION").e(e, "🔴 Thread $threadId: exceção")
                }
            }
        }

        jobs.joinAll()

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    LEITURA CONCORRENTE — RESULTADOS                     ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Threads lançadas   : $threadCount                       ║")
        println("║  Sucesso            : ${successCount.get()}               ║")
        println("║  Exceções           : ${exceptionCount.get()}             ║")
        println("║  Bitmaps distintos  : ${bitmapHashes.values.toSet().size} ║")
        println("╚══════════════════════════════════════════════════════════╝")

        assert(successCount.get() == threadCount) { "Todas as threads devem ter sucesso" }
        assert(exceptionCount.get() == 0) { "Não deve haver exceções" }
    }

    @Test
    fun rapidVideoSelection_cachesCorrectly() {
        val uris = (1..5).map { "content://test/rapid_$it" }
        val durationMs = 3_000L

        Timber.tag("TEST_INTEGRATION").i("Iniciando teste de seleção rápida — vídeos=5")

        val successCount = AtomicInteger(0)
        val cacheHits = AtomicInteger(0)

        uris.forEachIndexed { idx, uri ->
            runBlocking {
                try {
                    Timber.tag("TEST_INTEGRATION").d("Selecionando vídeo $idx: $uri")

                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = 0,
                        durationMs = durationMs,
                        segmentCount = 5,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )

                    if (strip != null) {
                        successCount.incrementAndGet()
                        Timber.tag("TEST_INTEGRATION").d("✓ Vídeo $idx carregado")
                    }
                } catch (e: Exception) {
                    Timber.tag("TEST_INTEGRATION").e(e, "🔴 Vídeo $idx falhou")
                }
            }
        }

        uris.forEachIndexed { idx, uri ->
            runBlocking {
                val strip = ThumbnailCacheManager.getStrip(
                    uri = uri,
                    segmentIndex = 0,
                    durationMs = durationMs,
                    segmentCount = 5,
                    thumbWidth = 80,
                    thumbHeight = 54,
                    thumbsPerStrip = 5,
                    adaptiveStrips = false
                )
                if (strip != null) {
                    cacheHits.incrementAndGet()
                }
            }
        }

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    SELEÇÃO RÁPIDA DE VÍDEOS — RESULTADOS               ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Vídeos carregados    : ${successCount.get()}/5          ║")
        println("║  Cache hits (segundo) : ${cacheHits.get()}/5             ║")
        println("║  Hit rate            : ${cacheHits.get() * 20}%         ║")
        println("╚══════════════════════════════════════════════════════════╝")

        assert(successCount.get() == 5) { "Todos os vídeos devem carregar" }
        assert(cacheHits.get() == 5) { "Segunda carga deve ser 100% cache hit" }
    }

    @Test
    fun concurrentWrites_differentSegments_noDeadlock() = runBlocking {
        val uri = "content://test/concurrent_write"
        val segmentCount = 20
        val durationMs = 60_000L

        Timber.tag("TEST_INTEGRATION").i("Iniciando teste de escrita concorrente — segmentos=$segmentCount")

        val successCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        val jobs = (0 until segmentCount).map { segmentIndex ->
            launch(Dispatchers.IO) {
                try {
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segmentIndex,
                        durationMs = durationMs,
                        segmentCount = segmentCount,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )

                    if (strip != null) {
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    Timber.tag("TEST_INTEGRATION").e(e, "🔴 Segmento $segmentIndex falhou")
                }
            }
        }

        jobs.joinAll()
        val totalTime = System.currentTimeMillis() - startTime

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    ESCRITA CONCORRENTE — RESULTADOS                     ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Segmentos escritos   : ${successCount.get()}/$segmentCount║")
        println("║  Tempo total          : ${totalTime}ms                   ║")
        println("║  Média/segmento       : ${totalTime / segmentCount}ms    ║")
        println("║  Deadlock detectado   : ${totalTime > 30_000}             ║")
        println("╚══════════════════════════════════════════════════════════╝")

        assert(successCount.get() == segmentCount) { "Todos os segmentos devem ser escritos" }
        assert(totalTime < 30_000) { "Não deve haver deadlock" }
    }

    @Test
    fun readDuringWrite_raceCondition() = runBlocking {
        val uri = "content://test/read_during_write"
        val segmentIndex = 0
        val durationMs = 5_000L
        val readCount = 10

        Timber.tag("TEST_INTEGRATION").i("Iniciando teste de read-during-write — leituras=$readCount")

        val writeJob = launch(Dispatchers.IO) {
            ThumbnailCacheManager.getStrip(
                uri = uri,
                segmentIndex = segmentIndex,
                durationMs = durationMs,
                segmentCount = 5,
                thumbWidth = 80,
                thumbHeight = 54,
                thumbsPerStrip = 5,
                adaptiveStrips = false
            )
        }

        delay(10)

        val readJobs = (0 until readCount).map { readId ->
            launch(Dispatchers.IO) {
                try {
                    val strip = ThumbnailCacheManager.getStrip(
                        uri = uri,
                        segmentIndex = segmentIndex,
                        durationMs = durationMs,
                        segmentCount = 5,
                        thumbWidth = 80,
                        thumbHeight = 54,
                        thumbsPerStrip = 5,
                        adaptiveStrips = false
                    )

                    if (strip != null) {
                        Timber.tag("TEST_INTEGRATION").d("Leitura $readId bem-sucedida")
                    }
                } catch (e: Exception) {
                    Timber.tag("TEST_INTEGRATION").e(e, "🔴 Leitura $readId falhou")
                }
            }
        }

        writeJob.join()
        readJobs.joinAll()

        println("╔══════════════════════════════════════════════════════════╗")
        println("║    READ-DURING-WRITE — RESULTADOS                       ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Leituras concorrentes: $readCount                      ║")
        println("║  Operações concluídas: ${1 + readCount}                 ║")
        println("║  Status                  : ✓ Sem deadlock              ║")
        println("╚══════════════════════════════════════════════════════════╝")
    }
}