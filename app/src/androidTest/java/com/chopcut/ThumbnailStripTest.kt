package com.chopcut

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.data.thumbnail.ThumbnailExtractorBatch
import com.chopcut.data.thumbnail.ThumbnailStripManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testa a lógica de stitch do ThumbnailStripManager em isolamento.
 *
 * Arquitetura das dependências:
 *
 *   ThumbnailStripManager.extractSegment()
 *       └── batchExtractor.extractBatch()   ← injetado via construtor
 *
 *   Em produção : ThumbnailExtractorBatch  → MediaMetadataRetriever → vídeo real
 *   Neste teste : FakeThumbnailExtractorBatch → bitmaps sólidos em memória
 *
 * Por que não mockamos com MockK?
 *   ThumbnailExtractorBatch é `open`, então subclassamos diretamente.
 *   Evita dependência de mockk-android nos testes instrumentados.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailStripTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Fake que retorna bitmaps sólidos sem tocar em vídeo ou disco.
     * Cada posição recebe um bitmap RGB_565 com as dimensões solicitadas.
     */
    private class FakeThumbnailExtractorBatch(context: Context) : ThumbnailExtractorBatch(context) {
        override suspend fun extractBatch(
            uri: Uri,
            positionsMs: List<Long>,
            width: Int,
            height: Int
        ): Map<Long, Bitmap> = positionsMs.associateWith {
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        }
    }

    @Test
    fun extractSegmentProducesCorrectStrip() = runBlocking {
        val thumbWidth = 80
        val thumbHeight = 54
        val thumbsPerStrip = 3
        val durationMs = 3_000L // 3s → 3 frames no segmento 0

        val manager = ThumbnailStripManager(
            context = context,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            thumbsPerStrip = thumbsPerStrip,
            adaptiveStrips = false,
            batchExtractor = FakeThumbnailExtractorBatch(context)
        )

        val strip = manager.extractSegment(
            uri = Uri.parse("file:///fake/video.mp4"),
            segmentIndex = 0,
            durationMs = durationMs
        )

        val expectedWidth = thumbWidth * thumbsPerStrip // 80 × 3 = 240

        println("╔══════════════════════════════════════════════════════════╗")
        println("║             STRIP EXTRACTION — RESULTADOS               ║")
        println("╠══════════════════════════════════════════════════════════╣")
        println("║  Extractor      : FakeThumbnailExtractorBatch            ║")
        println("║  durationMs     : ${durationMs}ms → ${thumbsPerStrip} frames               ║")
        println("║  Esperado       : ${expectedWidth}x${thumbHeight} RGB_565                ║")
        println("║  Produzido      : ${strip?.width}x${strip?.height} ${strip?.config}        ║")
        println("╚══════════════════════════════════════════════════════════╝")

        assertNotNull("Strip não deve ser nula", strip)
        assertEquals("Largura: thumbWidth × frames", expectedWidth, strip!!.width)
        assertEquals("Altura deve ser thumbHeight", thumbHeight, strip!!.height)
        assertEquals("Config deve ser RGB_565", Bitmap.Config.RGB_565, strip!!.config)
    }
}
