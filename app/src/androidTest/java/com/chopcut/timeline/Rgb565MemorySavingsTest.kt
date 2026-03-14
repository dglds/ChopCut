package com.chopcut.timeline

import android.graphics.Bitmap
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.OptimizedThumbnailProvider
import com.chopcut.data.thumbnail.ThumbnailPriority
import com.chopcut.instrumentedTestHelpers.TimelineTestHelper
import com.chopcut.instrumentedTestHelpers.TimberTestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testa economia de memória RGB565.
 *
 * O que verifica:
 * 1. RGB565 usa exatamente 50% da memória de ARGB_8888
 * 2. OptimizedThumbnailProvider converte para RGB565
 * 3. Thumbnails extraídos usam RGB565
 * 4. Economia de 50% é significativa
 *
 * Teste importante (P1) - valida otimização de memória.
 */
@RunWith(AndroidJUnit4::class)
class Rgb565MemorySavingsTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var provider: OptimizedThumbnailProvider

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Rgb565MemorySavingsTest setup")
        )
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            provider.release()
            Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
                TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider released")
            )
        }
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun rgb565UsesHalfMemoryOfArgb8888() {
        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "RGB565 vs ARGB_8888 comparison")
        )

        val width = 120
        val height = 120

        val rgb565 = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val argb8888 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val rgb565Bytes = rgb565.byteCount.toLong()
        val argb8888Bytes = argb8888.byteCount.toLong()

        val expectedDiff = argb8888Bytes - rgb565Bytes
        val ratio = rgb565Bytes.toFloat() / argb8888Bytes
        val percentageSaved = (1.0f - ratio) * 100

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "RGB565: $rgb565Bytes (${rgb565Bytes / 1024}KB)"
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "ARGB_8888: $argb8888Bytes (${argb8888Bytes / 1024}KB)"
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "Economia: ${expectedDiff / 1024}KB (${String.format("%.1f", percentageSaved)}%)"
        )

        TimelineTestHelper.printReport(
            title = "RGB_565 — ECONOMIA DE MEMÓRIA",
            lines = listOf(
                "Dimensões: ${width}x${height}",
                "ARGB_8888: ${argb8888Bytes}B (${argb8888Bytes / 1024}KB)",
                "RGB_565: $rgb565Bytes B (${rgb565Bytes / 1024}KB)",
                "Economia: ${expectedDiff}B (${expectedDiff / 1024}KB)",
                "Ratio: ${String.format("%.2f", ratio * 100)}%",
                "Meta: 50%",
                if (ratio == 0.5f) "✅ ATINGIDO" else "❌ NÃO ATINGIDO"
            )
        )

        assertEquals(
            "RGB_565 deve usar exatamente metade da memória",
            0.5f,
            ratio,
            0.01f
        )

        assertTrue(
            "Economia deve ser de 50%",
            percentageSaved == 50.0f
        )

        rgb565.recycle()
        argb8888.recycle()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "RGB565 comparison test completed successfully")
        )
    }

    @Test
    fun providerConvertsBitmapsToRgb565() = runBlocking {
        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Provider RGB565 conversion test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val timestamps = (0 until 10).map { it * 1000L }
        val configs = mutableListOf<Bitmap.Config>()

        provider.thumbnailUpdates.collect { (ts, bmp) ->
            configs.add(bmp.config)
            Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
                "Timestamp ${ts}ms: ${bmp.config}"
            )
        }

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "Requesting ${timestamps.size} thumbnails"
        )

        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        val success = TimelineTestHelper.waitForCondition(
            condition = { configs.size >= timestamps.size },
            timeoutMs = 10000
        )

        val allRgb565 = configs.all { it == Bitmap.Config.RGB_565 }
        val configCount = configs.groupingBy { it }.eachCount()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "Configurações encontradas: $configCount"
        )

        TimelineTestHelper.printReport(
            title = "PROVIDER — CONVERSÃO PARA RGB_565",
            lines = listOf(
                "Timestamps: ${timestamps.size}",
                "Bitmaps convertidos: ${configs.size}",
                "Todos RGB_565: $allRgb565",
                "Configurações: ${configCount.entries.joinToString { "${it.key}=${it.value}" }}",
                if (allRgb565) "✅ TODOS RGB_565" else "❌ NEM TODOS RGB_565"
            )
        )

        assertTrue(
            "Deve processar todos os timestamps",
            success
        )

        assertTrue(
            "Todos os bitmaps devem ser RGB_565",
            allRgb565
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider RGB565 conversion test completed successfully")
        )
    }

    @Test
    fun rgb565MemorySavingsScaleWithBitmapSize() {
        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "RGB565 savings scaling test")
        )

        val sizes = listOf(
            100 to 100,
            120 to 120,
            200 to 200,
            300 to 300
        )

        val results = mutableListOf<Pair<Pair<Int, Int>, Float>>()

        sizes.forEach { (width, height) ->
            val rgb565 = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val argb8888 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val rgb565Bytes = rgb565.byteCount.toLong()
            val argb8888Bytes = argb8888.byteCount.toLong()
            val ratio = rgb565Bytes.toFloat() / argb8888Bytes
            val savedBytes = argb8888Bytes - rgb565Bytes

            results.add((width to height) to ratio)

            Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
                "${width}x${height}: RGB565=$rgb565Bytes B, ARGB8888=$argb8888Bytes B, economia=${savedBytes}B"
            )

            rgb565.recycle()
            argb8888.recycle()
        }

        val allHalf = results.all { it.second == 0.5f }

        TimelineTestHelper.printReport(
            title = "RGB_565 — ESCALAMENTO DE ECONOMIA",
            lines = listOf(
                "Tamanhos testados: ${sizes.size}",
                *results.map { (size, ratio) ->
                    val (w, h) = size
                    "  ${w}x${h}: ratio=${String.format("%.2f", ratio * 100)}%"
                },
                if (allHalf) "✅ SEMPRE 50%" else "❌ NÃO 50%"
            )
        )

        assertTrue(
            "Economia deve ser sempre 50% independente do tamanho",
            allHalf
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "RGB565 savings scaling test completed successfully")
        )
    }
}
