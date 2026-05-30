package com.chopcut

import android.app.Application
import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Item 2 — teste completo do pipeline de compactação rodando no device.
 *
 * Pega um vídeo real aleatório de `Movies/ChopCut/teste/` (< 10min e compactável),
 * roda `TransformerPipeline.trim` com nível LOW de verdade (re-encode no hardware)
 * e prova objetivamente o resultado: codec H.264, dimensões pares, downscale para a
 * altura-alvo e queda no bitrate efetivo.
 */
@RunWith(AndroidJUnit4::class)
class CompressionPipelineTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_VIDEO)

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Clipe curto: re-encode rápido mas suficiente para provar a compactação. */
    private val clipMs = 8_000L

    @Test
    fun low_reEncodaParaH264ComDownscaleEBitrateMenor() {
        val level = CompressionLevel.LOW
        val source = DeviceVideoProvider.pickRandom(app) { v ->
            // Altura acima do alvo garante que há downscale real para provar.
            v.height > level.targetHeight && level.isViable(v.width, v.height, v.bitrate)
        }
        assumeTrue(
            "Nenhum vídeo apto em Movies/${DeviceVideoProvider.RELATIVE_PATH} " +
                "(precisa de vídeo < 10min, altura > ${level.targetHeight}px e compactável em ${level.label}). " +
                "Coloque um vídeo 1080p+ nessa pasta.",
            source != null
        )
        source!!

        val rangeEndMs = minOf(source.durationMs, clipMs)
        val ranges = listOf(TimeRange(0L, rangeEndMs))
        val pipeline = TransformerPipeline(app, VideoRepository(app))

        val output = runTrim { pipeline.trim(source.uri, ranges, aspectRatio = null, compressionLevel = level) }
        val result = VideoProbe.probe(output)

        assertEquals("Compactação deve sempre re-encodar para H.264 (video/avc)", "video/avc", result.videoMime)
        assertTrue(
            "Dimensões de saída devem ser pares (exigência do encoder): ${result.width}x${result.height}",
            result.dimensionsAreEven
        )
        assertTrue(
            "Altura deve cair para ~${level.targetHeight}px: foi ${result.height}px",
            abs(result.height - level.targetHeight) <= 2
        )
        assertTrue(
            "Altura de saída (${result.height}) deve ser menor que a original (${source.height})",
            result.height < source.height
        )

        val originalBps = source.sizeBytes * 1000.0 / source.durationMs
        assertTrue(
            "Bitrate efetivo deve cair — original=${originalBps.toLong()} B/s, saída=${result.bytesPerSecond.toLong()} B/s",
            result.bytesPerSecond < originalBps
        )
        assertTrue(
            "Duração de saída (~${result.durationMs}ms) deve bater com o range pedido (${rangeEndMs}ms)",
            abs(result.durationMs - rangeEndMs) <= 1_500
        )
    }

    /**
     * Coleta o [TrimProgress] de um flow de trim até o término, devolvendo o arquivo gerado.
     *
     * Copiamos o arquivo dentro do próprio lambda de `collect` (antes do flow encerrar e
     * disparar a limpeza do pipeline) para uma cópia estável que sobreviva ao teardown.
     */
    private fun runTrim(start: () -> kotlinx.coroutines.flow.Flow<TrimProgress>): File = runBlocking {
        var output: File? = null
        withTimeout(180_000) {
            start().collect { progress ->
                when (progress) {
                    is TrimProgress.Completed -> {
                        val stable = File.createTempFile("probe_", ".mp4", app.cacheDir)
                        progress.file.copyTo(stable, overwrite = true)
                        output = stable
                    }
                    is TrimProgress.Failed -> throw AssertionError("trim falhou: ${progress.error.message}", progress.error)
                    is TrimProgress.InProgress -> Unit
                }
            }
        }
        output ?: throw AssertionError("trim encerrou sem emitir Completed")
    }
}
