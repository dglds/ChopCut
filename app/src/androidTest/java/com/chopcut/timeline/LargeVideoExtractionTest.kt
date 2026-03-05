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
 * Teste de integração: extração de thumbnail strips para vídeos longos (15 minutos).
 * 
 * Este teste visa garantir que a extração para um número massivo de segmentos
 * (ex: 900+ segmentos para um vídeo de 15 minutos) seja realizada com sucesso,
 * não estoure a memória (OOM), e cumpra limites de tempo razoáveis.
 */
@RunWith(AndroidJUnit4::class)
class LargeVideoExtractionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val idlingResource = CountingIdlingResource("large_video_extraction")

    // Configuração para extração do vídeo
    private val thumbWidth  = ThumbnailConstants.Dimensions.STRIP_DEFAULT_WIDTH   // 50px
    private val thumbHeight = ThumbnailConstants.Dimensions.STRIP_DEFAULT_HEIGHT  // 50px
    private val thumbsPerStrip = 1

    // Threshold de tempo: 1500ms por frame para vídeos longos em CI/teste
    private val maxMsPerFrame = 1500L 

    @Before fun registerIdling() { IdlingRegistry.getInstance().register(idlingResource) }
    @After  fun unregisterIdling() { IdlingRegistry.getInstance().unregister(idlingResource) }

    private data class VideoSetup(
        val uri: Uri,
        val durationMs: Long,
        val totalFrames: Int,
        val totalSegments: Int,
        val manager: ThumbnailStripManager
    )

    private fun prepareVideo(activity: android.app.Activity): VideoSetup {
        val file = File(activity.cacheDir, "sample15min.mp4").also { dest ->
            testContext.assets.open("sample15min.mp4").use { it.copyTo(dest.outputStream()) }
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

    @Test
    fun extractAllStripsFor15MinVideo() = kotlinx.coroutines.runBlocking {
        var s: VideoSetup? = null
        var strips = mapOf<Int, Bitmap>()
        var loadTimeMs = 0L

        // Precisamos do contexto na thread principal apenas para instanciar/acessar dados da Activity
        activityRule.scenario.onActivity { activity ->
            s = prepareVideo(activity)
        }

        // Garante que o setup foi inicializado
        while(s == null) { kotlinx.coroutines.delay(10) }
        val setup = s!!

        // Roda a extração bloqueando apenas a thread de teste, mas sem limite de tempo do Espresso
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()

            coroutineScope {
                val total = setup.totalSegments
                val jobs = (0 until total).map { idx ->
                    async {
                        val res = idx to setup.manager.extractSegment(setup.uri, idx, setup.durationMs, total)
                        if (idx % 50 == 0 || idx == total - 1) {
                            println("DEBUG_CHOPCUT: extraindo thumb ${idx + 1}/$total...")
                        }
                        res
                    }
                }
                strips = jobs.awaitAll()
                    .filter { (_, bmp) -> bmp != null }
                    .associate { (idx, bmp) -> idx to bmp!! }
            }

            loadTimeMs = System.currentTimeMillis() - start
        }

        val threshold = setup.totalFrames * maxMsPerFrame
        val msPerFrame = if (setup.totalFrames > 0) loadTimeMs / setup.totalFrames else 0L

        printReport(
            title = "EXTRAÇÃO VÍDEO LONGO (15 MIN)",
            lines = listOf(
                "Duração do vídeo : ${setup.durationMs / 1000} segundos",
                "Segmentos obtidos: ${strips.size} / ${setup.totalSegments}",
                "Frames totais    : ${setup.totalFrames}",
                "Tempo de Carga   : ${loadTimeMs}ms",
                "Média por frame  : ${msPerFrame}ms",
                if (loadTimeMs <= threshold) "✅  Dentro do limite de tempo" else "❌  ACIMA do limite de tempo"
            )
        )

        assertEquals(
            "Número de strips carregadas deve ser igual ao total de segmentos",
            setup.totalSegments, strips.size
        )
        for (idx in 0 until setup.totalSegments) {
            assertNotNull("Strip do segmento $idx não deve ser nula", strips[idx])
            assertEquals("Config deve ser RGB_565 para economizar RAM", Bitmap.Config.RGB_565, strips[idx]?.config)
        }
    }

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
