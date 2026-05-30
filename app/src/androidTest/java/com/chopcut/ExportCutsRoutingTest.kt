package com.chopcut

import android.Manifest
import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Item 3 — roteamento de pipeline em `TimelineViewModel.exportCuts`, ponta a ponta no device.
 *
 * Prova **comportamentalmente** a escolha de pipeline sobre o mesmo vídeo real:
 *  - `ORIGINAL` → `CopyPipeline` (cópia sem re-encode) → resolução preservada;
 *  - `LOW` → `TransformerPipeline` (re-encode) → downscale para a altura-alvo.
 *
 * Também valida a máquina de estados de [ExportUiState] (Exporting → Success).
 */
@RunWith(AndroidJUnit4::class)
class ExportCutsRoutingTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_VIDEO)

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun exportCuts_originalPreservaResolucao_lowFazDownscale() {
        val level = CompressionLevel.LOW
        val source = DeviceVideoProvider.pickRandom(app) { v ->
            v.height > level.targetHeight && level.isViable(v.width, v.height, v.bitrate)
        }
        assumeTrue(
            "Nenhum vídeo apto em Movies/${DeviceVideoProvider.RELATIVE_PATH} " +
                "(precisa de vídeo < 10min, altura > ${level.targetHeight}px e compactável em ${level.label}).",
            source != null
        )
        source!!

        // ViewModel cria ExoPlayer no init → precisa nascer na main thread.
        lateinit var viewModel: TimelineViewModel
        instrumentation.runOnMainSync { viewModel = TimelineViewModel(app, source.uri) }

        // Espera a metadata carregar (init dispara um launch assíncrono).
        runBlocking { withTimeout(20_000) { viewModel.videoDetails.first { it != null } } }

        // Marca um pequeno intervalo no meio para cortar — mantém o resto do vídeo.
        val cutStart = (source.durationMs * 0.40).toLong()
        val cutEnd = (source.durationMs * 0.50).toLong()
        instrumentation.runOnMainSync {
            viewModel.toggleMarker(cutStart)
            viewModel.toggleMarker(cutEnd)
        }

        // ORIGINAL → CopyPipeline: resolução intacta.
        val originalUri = runExport(viewModel, CompressionLevel.ORIGINAL)
        val originalOut = VideoProbe.probe(app, originalUri)
        assertEquals(
            "ORIGINAL deve preservar a altura (CopyPipeline, sem re-encode): " +
                "esperado ${source.height}, foi ${originalOut.height}",
            source.height,
            originalOut.height
        )

        // LOW → TransformerPipeline: downscale para a altura-alvo.
        instrumentation.runOnMainSync { viewModel.resetExportState() }
        val lowUri = runExport(viewModel, CompressionLevel.LOW)
        val lowOut = VideoProbe.probe(app, lowUri)
        assertTrue(
            "LOW deve fazer downscale para ~${level.targetHeight}px (TransformerPipeline): foi ${lowOut.height}",
            abs(lowOut.height - level.targetHeight) <= 2
        )
        assertTrue(
            "LOW (${lowOut.height}px) deve ter resolução menor que ORIGINAL (${originalOut.height}px)",
            lowOut.height < originalOut.height
        )
    }

    /** Dispara o export na main thread e bloqueia até [ExportUiState.Success], devolvendo o `shareUri`. */
    private fun runExport(viewModel: TimelineViewModel, level: CompressionLevel): Uri {
        instrumentation.runOnMainSync { viewModel.exportCuts(level) }
        return runBlocking {
            val state = withTimeout(180_000) {
                viewModel.exportState.first { it is ExportUiState.Success || it is ExportUiState.Error }
            }
            when (state) {
                is ExportUiState.Success -> state.shareUri
                is ExportUiState.Error -> throw AssertionError("export ($level) falhou: ${state.message}")
                else -> throw AssertionError("estado de export inesperado: $state")
            }
        }
    }
}
