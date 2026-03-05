package com.chopcut

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.data.thumbnail.ThumbnailExtractorBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

@RunWith(AndroidJUnit4::class)
class ThumbnailExtractionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val idlingResource = CountingIdlingResource("thumbnail_extraction")

    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(idlingResource)
    }

    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun extractsOneThumbPerSecond() {
        var thumbnails: Map<Long, Bitmap> = emptyMap()
        var expectedCount = 0
        var durationMs = 0L
        var extractionTimeMs = 0L

        idlingResource.increment()

        activityRule.scenario.onActivity { activity ->
            val file = File(activity.cacheDir, "sample.mp4").also { dest ->
                testContext.assets.open("sample.mp4").use { it.copyTo(dest.outputStream()) }
            }
            val uri = Uri.fromFile(file)

            durationMs = MediaMetadataRetriever().run {
                setDataSource(activity, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    .also { release() }
            }

            val positions = (0 until (durationMs / 1000).toInt()).map { it * 1000L }
            expectedCount = positions.size

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val start = System.currentTimeMillis()
                    thumbnails = ThumbnailExtractorBatch(activity).extractBatch(uri, positions)
                    extractionTimeMs = System.currentTimeMillis() - start
                } finally {
                    idlingResource.decrement()
                }
            }
        }

        Espresso.onIdle()

        // ── Relatório ─────────────────────────────────────────────────────────
        val valid = thumbnails.values.toList()
        val nullCount = expectedCount - valid.size
        val avgMs = if (valid.isNotEmpty()) extractionTimeMs / valid.size else 0
        val widths = valid.map { it.width }.toSet()
        val heights = valid.map { it.height }.toSet()

        println("╔══════════════════════════════════════════════════════════╗")
        println("║           THUMBNAIL EXTRACTION — RESULTADOS              ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Vídeo      : sample.mp4 (${durationMs}ms)${" ".repeat((26 - durationMs.toString().length).coerceAtLeast(0))}║")
        println("║  Esperados  : $expectedCount thumb(s)${" ".repeat((38 - expectedCount.toString().length).coerceAtLeast(0))}║")
        println("║  Extraídos  : ${valid.size} thumb(s)${" ".repeat((38 - valid.size.toString().length).coerceAtLeast(0))}║")
        println("║  Falhas     : $nullCount${" ".repeat((43 - nullCount.toString().length).coerceAtLeast(0))}║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Tempo total: ${extractionTimeMs}ms${" ".repeat((41 - extractionTimeMs.toString().length).coerceAtLeast(0))}║")
        println("║  Média/frame: ${avgMs}ms${" ".repeat((41 - avgMs.toString().length).coerceAtLeast(0))}║")
        println("║  Resolução  : ${widths.first()}x${heights.first()}${" ".repeat((39 - widths.first().toString().length - heights.first().toString().length).coerceAtLeast(0))}║")
        println("╠══════════════════════════════════════════════════════════╣")

        thumbnails.entries.sortedBy { it.key }.forEach { (posMs, bmp) ->
            val label = "  [${posMs}ms] ${bmp.width}x${bmp.height} ${bmp.config}"
            println("║$label${" ".repeat((58 - label.length).coerceAtLeast(0))}║")
        }

        println("╚══════════════════════════════════════════════════════════╝")
        // ─────────────────────────────────────────────────────────────────────

        assertEquals("Número de thumbnails extraídos", expectedCount, thumbnails.size)
        assertTrue("Tempo de extração deve ser > 0ms", extractionTimeMs > 0)
        valid.forEach { bmp ->
            assertNotNull("Bitmap não deve ser nulo", bmp)
            assertTrue("Largura deve ser > 0", bmp.width > 0)
            assertTrue("Altura deve ser > 0", bmp.height > 0)
        }
    }
}
