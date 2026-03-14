package com.chopcut.instrumentedTestHelpers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper compartilhado para testes instrumentados da timeline de thumbnails.
 *
 * Fornece utilitários para:
 * - Setup comum de testes
 * - Criação de vídeos de teste
 * - Medições de memória
 * - Espera assíncrona com timeout
 * - Relatórios formatados
 *
 * Padroniza testes e reduz duplicação de código.
 */
object TimelineTestHelper {

    /**
     * Cria e configura o RecyclerView para testes da timeline.
     *
     * @param context Contexto do teste
     * @param width Largura do RecyclerView
     * @param height Altura do RecyclerView
     * @return RecyclerView configurado
     */
    fun createRecyclerView(
        context: Context,
        width: Int = 1200,
        height: Int = 120
    ): FrameLayout {
        return FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(width, height)
        }
    }

    /**
     * Copia vídeo de teste dos assets para o cache.
     *
     * @param context Contexto do teste
     * @param assetName Nome do arquivo nos assets
     * @param targetName Nome do arquivo de destino
     * @return URI do vídeo copiado
     */
    fun copyTestVideo(
        context: Context,
        assetName: String = "sample.mp4",
        targetName: String = "test_video_${System.currentTimeMillis()}.mp4"
    ): Uri {
        val file = File(context.cacheDir, targetName)
        context.assets.open(assetName).use { it.copyTo(file.outputStream()) }
        return Uri.fromFile(file)
    }

    /**
     * Verifica se um strip foi carregado corretamente.
     *
     * @param bitmap Bitmap a verificar
     * @param expectedWidth Largura esperada
     * @param expectedHeight Altura esperada
     * @param expectedConfig Configuração de bitmap esperada
     * @return true se o strip for válido
     */
    fun assertStripLoaded(
        bitmap: Bitmap?,
        expectedWidth: Int,
        expectedHeight: Int,
        expectedConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Boolean {
        if (bitmap == null) {
            printReport(
                title = "STRIP VALIDATION",
                lines = listOf(
                    "Resultado: ❌ FALHA",
                    "Bitmap: null",
                    "Esperado: ${expectedWidth}x${expectedHeight} $expectedConfig"
                )
            )
            return false
        }

        val isValid = bitmap.width == expectedWidth &&
                bitmap.height == expectedHeight &&
                bitmap.config == expectedConfig

        printReport(
            title = "STRIP VALIDATION",
            lines = listOf(
                "Resultado: ${if (isValid) "✅ SUCESSO" else "❌ FALHA"}",
                "Bitmap: ${bitmap.width}x${bitmap.height} ${bitmap.config}",
                "Esperado: ${expectedWidth}x${expectedHeight} $expectedConfig"
            )
        )

        return isValid
    }

    /**
     * Mede o uso de memória atual.
     *
     * @return Uso de memória em bytes
     */
    fun measureMemory(): Long {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        return totalMemory - freeMemory
    }

    /**
     * Aguarda até que uma condição seja verdadeira com timeout.
     *
     * @param condition Função a verificar
     * @param timeoutMs Timeout em milissegundos (padrão: 5000)
     * @param checkIntervalMs Intervalo de verificação em milissegundos (padrão: 100)
     * @return true se a condição foi atendida, false se timeout
     */
    suspend fun waitForCondition(
        condition: () -> Boolean,
        timeoutMs: Long = 5000,
        checkIntervalMs: Long = 100
    ): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            delay(checkIntervalMs)
        }

        return false
    }

    /**
     * Aguarda até que um strip seja carregado com timeout.
     *
     * @param loadedSet Set de timestamps carregados
     * @param timestamp Timestamp a aguardar
     * @param timeoutMs Timeout em milissegundos (padrão: 5000)
     * @return true se o strip foi carregado, false se timeout
     */
    suspend fun waitForStrip(
        loadedSet: Set<Long>,
        timestamp: Long,
        timeoutMs: Long = 5000
    ): Boolean {
        return withTimeout(timeoutMs) {
            while (timestamp !in loadedSet) {
                delay(100)
            }
            true
        }
    }

    /**
     * Cria um bitmap de teste com dimensões específicas.
     *
     * @param width Largura
     * @param height Altura
     * @param config Configuração (padrão: RGB_565)
     * @return Bitmap criado
     */
    fun createTestBitmap(
        width: Int = 120,
        height: Int = 120,
        config: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Imprime relatório formatado com tabela.
     *
     * @param title Título do relatório
     * @param lines Linhas do conteúdo
     */
    fun printReport(title: String, lines: List<String>) {
        val W = 62
        fun bar(l: String, f: String, r: String) = "$l${f.repeat(W)}$r"
        fun row(s: String) = "║ ${s.padEnd(W - 1)}║"

        println(bar("╔", "═", "╗"))
        println(row("  $title"))
        println(bar("╠", "─", "╣"))
        lines.forEach { println(row("  $it")) }
        println(bar("╚", "═", "╝"))
    }

    /**
     * Assere que um valor de performance está dentro do limite esperado.
     *
     * @param testName Nome do teste
     * @param actual Valor atual
     * @param expectedMax Valor máximo esperado
     * @param unit Unidade (padrão: "ms")
     * @return true se estiver dentro do limite
     */
    fun assertPerformance(
        testName: String,
        actual: Long,
        expectedMax: Long,
        unit: String = "ms"
    ): Boolean {
        val ok = actual <= expectedMax
        printReport(
            title = testName,
            lines = listOf(
                "Atual: $actual$unit",
                "Máximo: $expectedMax$unit",
                if (ok) "✅ ATINGIDO" else "❌ NÃO ATINGIDO"
            )
        )
        return ok
    }

    /**
     * Obtém o contexto de teste.
     *
     * @return Contexto de teste
     */
    fun getTestContext(): Context {
        return InstrumentationRegistry.getInstrumentation().context
    }

    /**
     * Obtém o contexto alvo (app).
     *
     * @return Contexto alvo
     */
    fun getTargetContext(): Context {
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * Limpa o cache de testes.
     *
     * @param context Contexto
     */
    fun clearTestCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("test_")) {
                file.delete()
            }
        }
    }
}
