package com.chopcut.timeline

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.config.constants.ThumbnailConstants
import com.chopcut.data.thumbnail.ThumbnailStripManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Teste de integração: carregamento de thumbnail strips para a timeline.
 *
 * Pipeline testado (real, sem mocks):
 *   sample.mp4 (assets) → ThumbnailStripManager → ThumbnailExtractorBatch
 *       → Map<segmentIndex, Bitmap>
 *
 * O que é verificado:
 *   1. Todas as strips do vídeo foram carregadas (nenhum segmento faltando)
 *   2. Cada strip tem as dimensões corretas (thumbWidth × N frames por altura)
 *   3. Cada strip usa RGB_565 (otimização de memória da timeline)
 *   4. O carregamento completa dentro de um tempo aceitável por frame
 *   5. Strips carregam de forma paralela assim como na produção
 *
 * Rodar:
 *   ./gradlew runTest -Ptarget=com.chopcut.timeline.TimelineThumbnailLoadingTest
 */
@RunWith(AndroidJUnit4::class)
class TimelineThumbnailLoadingTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val idlingResource = CountingIdlingResource("timeline_thumbnail_loading")

    // Configuração da timeline — 1 thumb por strip = granularidade máxima para verificação
    private val thumbWidth  = ThumbnailConstants.Dimensions.STRIP_DEFAULT_WIDTH   // 50px
    private val thumbHeight = ThumbnailConstants.Dimensions.STRIP_DEFAULT_HEIGHT  // 50px
    private val thumbsPerStrip = 1

    // Threshold de tempo: 600ms por frame (generoso para dispositivos de CI/teste)
    private val maxMsPerFrame = 600L

    @Before fun registerIdling() { IdlingRegistry.getInstance().register(idlingResource) }
    @After  fun unregisterIdling() { IdlingRegistry.getInstance().unregister(idlingResource) }

    // ─── Setup compartilhado ─────────────────────────────────────────────────

    private data class VideoSetup(
        val uri: Uri,
        val durationMs: Long,
        val totalFrames: Int,
        val totalSegments: Int,
        val manager: ThumbnailStripManager
    )

    private fun prepareVideo(activity: android.app.Activity): VideoSetup {
        val file = File(activity.cacheDir, "sample.mp4").also { dest ->
            testContext.assets.open("sample.mp4").use { it.copyTo(dest.outputStream()) }
        }
        val uri = Uri.fromFile(file)

        val durationMs = MediaMetadataRetriever().run {
            setDataSource(activity, uri)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                .also { release() }
        }

        // Limpa disco para garantir extração real (sem cache interferindo)
        ThumbnailStripManager.clearCache(activity)

        val manager = ThumbnailStripManager(
            context        = activity,
            thumbWidth     = thumbWidth,
            thumbHeight    = thumbHeight,
            thumbsPerStrip = thumbsPerStrip,
            adaptiveStrips = false
        )

        val totalFrames   = ((durationMs + 999) / 1000).toInt()
        val totalSegments = manager.getSegmentCount(durationMs)

        return VideoSetup(uri, durationMs, totalFrames, totalSegments, manager)
    }

    // ─── Teste 1: todas as strips carregadas ─────────────────────────────────

    @Test
    fun allStripsAreLoadedForFullVideo() {
        var setup: VideoSetup? = null
        var strips = mapOf<Int, Bitmap>()

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            setup = prepareVideo(activity)
            val s = setup!!

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coroutineScope {
                        val jobs = (0 until s.totalSegments).map { idx ->
                            async { idx to s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments) }
                        }
                        strips = jobs.awaitAll()
                            .filter { (_, bmp) -> bmp != null }
                            .associate { (idx, bmp) -> idx to bmp!! }
                    }
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        val s = setup!!

        printReport(
            title = "STRIPS — TODAS CARREGADAS?",
            lines = listOf(
                "Vídeo        : sample.mp4 (${s.durationMs}ms  ${s.totalFrames}s)",
                "Segmentos    : ${strips.size} / ${s.totalSegments} carregados",
                "thumbsPerStrip: $thumbsPerStrip   adaptiveStrips: false"
            ) + strips.entries.sortedBy { it.key }.map { (idx, bmp) ->
                "  [$idx] ${bmp.width}x${bmp.height}  ${bmp.config}"
            }
        )

        assertEquals(
            "Número de strips carregadas deve ser igual ao total de segmentos",
            s.totalSegments, strips.size
        )
        for (idx in 0 until s.totalSegments) {
            assertNotNull("Strip do segmento $idx não deve ser nula", strips[idx])
        }
    }

    // ─── Teste 2: dimensões corretas por strip ────────────────────────────────

    @Test
    fun eachStripHasCorrectDimensions() {
        var setup: VideoSetup? = null
        var strips = mapOf<Int, Bitmap>()

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            setup = prepareVideo(activity)
            val s = setup!!

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coroutineScope {
                        val jobs = (0 until s.totalSegments).map { idx ->
                            async { idx to s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments) }
                        }
                        strips = jobs.awaitAll()
                            .filter { (_, bmp) -> bmp != null }
                            .associate { (idx, bmp) -> idx to bmp!! }
                    }
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        val s = setup!!
        val totalSeconds = ((s.durationMs + 999) / 1000).toInt()

        printReport(
            title = "STRIPS — DIMENSÕES CORRETAS?",
            lines = strips.entries.sortedBy { it.key }.map { (idx, bmp) ->
                val startSec    = idx * thumbsPerStrip
                val frames      = minOf(thumbsPerStrip, totalSeconds - startSec)
                val expectedW   = thumbWidth * frames
                val ok = if (bmp.width == expectedW && bmp.height == thumbHeight) "✅" else "❌"
                "$ok  [$idx]  esperado: ${expectedW}x${thumbHeight}  obtido: ${bmp.width}x${bmp.height}"
            }
        )

        strips.entries.sortedBy { it.key }.forEach { (idx, bmp) ->
            val startSec  = idx * thumbsPerStrip
            val frames    = minOf(thumbsPerStrip, totalSeconds - startSec)
            val expectedW = thumbWidth * frames

            assertEquals("Strip $idx: largura incorreta", expectedW, bmp.width)
            assertEquals("Strip $idx: altura incorreta",  thumbHeight, bmp.height)
        }
    }

    // ─── Teste 3: configuração RGB_565 ───────────────────────────────────────

    @Test
    fun allStripsUseRgb565Config() {
        var setup: VideoSetup? = null
        var strips = mapOf<Int, Bitmap>()

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            setup = prepareVideo(activity)
            val s = setup!!

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coroutineScope {
                        val jobs = (0 until s.totalSegments).map { idx ->
                            async { idx to s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments) }
                        }
                        strips = jobs.awaitAll()
                            .filter { (_, bmp) -> bmp != null }
                            .associate { (idx, bmp) -> idx to bmp!! }
                    }
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        printReport(
            title = "STRIPS — CONFIG RGB_565?",
            lines = strips.entries.sortedBy { it.key }.map { (idx, bmp) ->
                val ok = if (bmp.config == Bitmap.Config.RGB_565) "✅" else "❌"
                "$ok  [$idx]  ${bmp.config}  (${bmp.byteCount / 1024}KB)"
            }
        )

        strips.forEach { (idx, bmp) ->
            assertEquals(
                "Strip $idx deve usar RGB_565 (50% menos RAM que ARGB_8888)",
                Bitmap.Config.RGB_565, bmp.config
            )
        }
    }

    // ─── Teste 4: tempo de carregamento aceitável ─────────────────────────────

    @Test
    fun allStripsLoadWithinAcceptableTime() {
        var setup: VideoSetup? = null
        var strips = mapOf<Int, Bitmap>()
        var loadTimeMs = 0L

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            setup = prepareVideo(activity)
            val s = setup!!

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val start = System.currentTimeMillis()

                    coroutineScope {
                        val jobs = (0 until s.totalSegments).map { idx ->
                            async { idx to s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments) }
                        }
                        strips = jobs.awaitAll()
                            .filter { (_, bmp) -> bmp != null }
                            .associate { (idx, bmp) -> idx to bmp!! }
                    }

                    loadTimeMs = System.currentTimeMillis() - start
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        val s = setup!!
        val threshold = s.totalFrames * maxMsPerFrame
        val msPerFrame = if (s.totalFrames > 0) loadTimeMs / s.totalFrames else 0L

        printReport(
            title = "STRIPS — TEMPO ACEITÁVEL?",
            lines = listOf(
                "Frames totais    : ${s.totalFrames}",
                "Threshold total  : ${threshold}ms  (${maxMsPerFrame}ms × ${s.totalFrames} frames)",
                "Tempo real       : ${loadTimeMs}ms",
                "Média por frame  : ${msPerFrame}ms",
                if (loadTimeMs <= threshold) "✅  Dentro do limite" else "❌  ACIMA do limite"
            )
        )

        assertTrue(
            "Tempo de carregamento ${loadTimeMs}ms deve ser < ${threshold}ms (${maxMsPerFrame}ms/frame × ${s.totalFrames} frames)",
            loadTimeMs <= threshold
        )
    }

    // ─── Teste 5: carregamento paralelo é mais rápido que sequencial ──────────

    @Test
    fun parallelLoadingIsFasterThanSequential() {
        var setup: VideoSetup? = null
        var parallelMs  = 0L
        var sequentialMs = 0L

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            setup = prepareVideo(activity)
            val s = setup!!

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Paralelo
                    val t1 = System.currentTimeMillis()
                    coroutineScope {
                        (0 until s.totalSegments)
                            .map { idx -> async { s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments) } }
                            .awaitAll()
                    }
                    parallelMs = System.currentTimeMillis() - t1

                    // Limpa cache de disco para comparação justa
                    ThumbnailStripManager.clearCache(activity)

                    // Sequencial
                    val t2 = System.currentTimeMillis()
                    for (idx in 0 until s.totalSegments) {
                        s.manager.extractSegment(s.uri, idx, s.durationMs, s.totalSegments)
                    }
                    sequentialMs = System.currentTimeMillis() - t2
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        val s = setup!!

        printReport(
            title = "PARALELO vs SEQUENCIAL",
            lines = listOf(
                "Segmentos       : ${s.totalSegments}",
                "Paralelo        : ${parallelMs}ms",
                "Sequencial      : ${sequentialMs}ms",
                if (s.totalSegments > 1) {
                    if (parallelMs <= sequentialMs) "✅  Paralelo foi mais rápido ou igual" else "⚠️  Sequencial foi mais rápido (overhead de coroutine?)"
                } else {
                    "⏭  1 segmento — comparação não aplicável"
                }
            )
        )

        // Com 1 segmento a comparação não faz sentido (mesma operação)
        if (s.totalSegments > 1) {
            assertTrue(
                "Carregamento paralelo (${parallelMs}ms) deve ser ≤ sequencial (${sequentialMs}ms)",
                parallelMs <= sequentialMs
            )
        }
    }

    // ─── Utilitário de output ─────────────────────────────────────────────────

    private fun printReport(title: String, lines: List<String>) {
        val W = 62
        fun bar(l: String, f: String, r: String) = "$l${f.repeat(W)}$r"
        fun row(s: String) = "║ ${s.padEnd(W - 1)}║"

        println(bar("╔", "═", "╗"))
        println(row("  $title"))
        println(bar("╠", "─", "╣"))
        lines.forEach { println(row("  $it")) }
        println(bar("╚", "═", "╝"))
    }
}
