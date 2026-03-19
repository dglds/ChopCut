package com.chopcut.timeline

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.v3.FastFrameExtractor
import kotlinx.coroutines.runBlocking
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
 * Testes de regressão para FastFrameExtractor (PERF-001, PERF-004).
 *
 * Valida:
 *   1. prepare() produz frames corretos (PERF-001: sem quebra pelo trace)
 *   2. Frames consecutivos são distintos (PERF-004: reuso de stream sem corrupção)
 *   3. Dimensões e config do Bitmap respeitam o target passado em prepare()
 *   4. prepare() pode ser chamado múltiplas vezes sem crash (regressão PERF-003)
 */
@RunWith(AndroidJUnit4::class)
class FastFrameExtractorTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context

    private val thumbWidth  = 48
    private val thumbHeight = 36

    private lateinit var videoUri: Uri
    private lateinit var extractor: FastFrameExtractor
    private var durationMs = 0L

    @Before
    fun setup() {
        activityRule.scenario.onActivity { activity ->
            val file = File(activity.cacheDir, "ffe_test_sample.mp4").also { dest ->
                testContext.assets.open("sample.mp4").use { it.copyTo(dest.outputStream()) }
            }
            videoUri = Uri.fromFile(file)
            durationMs = MediaMetadataRetriever().run {
                setDataSource(activity, videoUri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    .also { release() }
            }
            extractor = FastFrameExtractor(activity, videoUri)
        }
    }

    @After
    fun teardown() {
        extractor.release()
    }

    // ─── Teste 1: prepare() retorna true e frames são extraídos ──────────────

    @Test
    fun prepareSucceedsAndFramesAreNotNull() = runBlocking {
        val prepared = extractor.prepare(thumbWidth, thumbHeight)
        assertTrue("prepare() deve retornar true para vídeo válido", prepared)

        val frame = extractor.getFrameAt(0L)
        assertNotNull("Frame no instante 0 não deve ser nulo", frame)

        printReport(
            title = "PERF-001 · prepare + getFrameAt",
            lines = listOf(
                "prepare()    : ${if (prepared) "✅ true" else "❌ false"}",
                "getFrameAt(0): ${if (frame != null) "✅ ${frame.width}x${frame.height} ${frame.config}" else "❌ null"}"
            )
        )
    }

    // ─── Teste 2: dimensões e config respeitam o target de prepare() ──────────

    @Test
    fun frameDimensionsMatchPrepareTarget() = runBlocking {
        extractor.prepare(thumbWidth, thumbHeight)

        val frame = extractor.getFrameAt(0L)
        assertNotNull("Frame não deve ser nulo", frame)

        assertEquals("Largura do frame deve ser $thumbWidth", thumbWidth, frame!!.width)
        assertEquals("Altura do frame deve ser $thumbHeight", thumbHeight, frame.height)
        assertEquals("Config deve ser RGB_565", Bitmap.Config.RGB_565, frame.config)

        printReport(
            title = "PERF-001 · Dimensões e Config",
            lines = listOf(
                "Target : ${thumbWidth}x${thumbHeight} RGB_565",
                "Obtido : ${frame.width}x${frame.height} ${frame.config}",
                if (frame.width == thumbWidth && frame.height == thumbHeight && frame.config == Bitmap.Config.RGB_565)
                    "✅ Correto" else "❌ Divergência"
            )
        )
    }

    // ─── Teste 3: frames consecutivos são distintos (PERF-004 sem corrupção) ──

    @Test
    fun consecutiveFramesAreDistinct() = runBlocking {
        extractor.prepare(thumbWidth, thumbHeight)

        val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(2)
        val sampleCount = minOf(totalSeconds, 5)

        val frames = (0 until sampleCount).map { sec ->
            sec to extractor.getFrameAt(sec * 1_000_000L)
        }
        val nonNull = frames.filter { (_, bmp) -> bmp != null }

        printReport(
            title = "PERF-004 · Frames Distintos (reuso ByteArrayOutputStream)",
            lines = nonNull.map { (sec, bmp) ->
                val px = bmp!!.getPixel(bmp.width / 2, bmp.height / 2)
                "  [${sec}s] pixel central=0x${px.toString(16).padStart(8, '0')}"
            } + listOf("Não-nulos: ${nonNull.size} / $sampleCount")
        )

        assertTrue("Pelo menos 2 frames para comparação", nonNull.size >= 2)

        // Se o .reset() corromper o stream, todos os frames teriam o mesmo conteúdo.
        val pixels = nonNull.map { (_, bmp) -> bmp!!.getPixel(bmp.width / 2, bmp.height / 2) }
        assertTrue(
            "Frames de instantes diferentes não devem ser todos idênticos (indica corrupção do stream)",
            pixels.any { it != pixels.first() }
        )
    }

    // ─── Teste 4: prepare() chamado duas vezes não causa crash ───────────────

    @Test
    fun prepareCanBeCalledTwiceWithoutCrash() = runBlocking {
        val first  = extractor.prepare(thumbWidth, thumbHeight)
        val second = extractor.prepare(thumbWidth, thumbHeight)

        assertTrue("prepare() #1 deve retornar true", first)
        assertTrue("prepare() #2 deve retornar true", second)

        val frame = extractor.getFrameAt(0L)
        assertNotNull("Frame após duplo prepare() não deve ser nulo", frame)

        printReport(
            title = "PERF-003 · Duplo prepare() sem crash",
            lines = listOf(
                "prepare() #1 : ${if (first) "✅" else "❌"}",
                "prepare() #2 : ${if (second) "✅" else "❌"}",
                "getFrameAt() : ${if (frame != null) "✅ ${frame.width}x${frame.height}" else "❌ null"}"
            )
        )
    }

    // ─── Teste 5: N frames sequenciais sem OOM (stream reuse em ação) ─────────

    @Test
    fun multipleFramesWithoutOom() = runBlocking {
        extractor.prepare(thumbWidth, thumbHeight)

        val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
        val sampleCount = minOf(totalSeconds, 10)

        var successCount = 0
        val start = System.currentTimeMillis()

        for (sec in 0 until sampleCount) {
            val bmp = extractor.getFrameAt(sec * 1_000_000L)
            if (bmp != null) successCount++
        }

        val elapsed = System.currentTimeMillis() - start
        val avgMs = if (sampleCount > 0) elapsed / sampleCount else 0L

        printReport(
            title = "PERF-004 · $sampleCount Frames Sequenciais (sem OOM)",
            lines = listOf(
                "Extraídos   : $successCount / $sampleCount",
                "Tempo total : ${elapsed}ms",
                "Média/frame : ${avgMs}ms",
                if (successCount == sampleCount) "✅ Todos extraídos" else "⚠️  ${sampleCount - successCount} falha(s)"
            )
        )

        assertTrue(
            "Pelo menos 80% dos frames devem ser extraídos com sucesso",
            successCount >= (sampleCount * 0.8).toInt()
        )
    }

    // ─── Utilitário ──────────────────────────────────────────────────────────

    private fun printReport(title: String, lines: List<String>) {
        val W = 60
        fun bar(l: String, f: String, r: String) = "$l${f.repeat(W)}$r"
        fun row(s: String) = "║ ${s.padEnd(W - 1)}║"
        println(bar("╔", "═", "╗"))
        println(row("  $title"))
        println(bar("╠", "─", "╣"))
        lines.forEach { println(row("  $it")) }
        println(bar("╚", "═", "╝"))
    }
}
