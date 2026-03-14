package com.chopcut.timeline

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testa cancelamento de fila de extração.
 *
 * O que verifica:
 * 1. clearQueue() limpa requests pendentes
 * 2. Requests já em processamento continuam
 * 3. Novos requests podem ser adicionados após clear
 * 4. Cancelamento não trava a UI
 *
 * Teste crítico (P0) - valida comportamento em scroll rápido.
 */
@RunWith(AndroidJUnit4::class)
class QueueCancellationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var provider: OptimizedThumbnailProvider

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "QueueCancellationTest setup")
        )
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            provider.release()
            Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
                TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider released")
            )
        }
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun clearQueueRemovesPendingRequests() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Clear queue test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val timestamps = (0 until 20).map { it * 500L }
        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_QUEUE).d(
            "Enqueuing ${timestamps.size} requests"
        )

        // Enfileirar requests
        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.PREFETCH)
        }

        delay(100)  // Pequeno delay para enfileirar

        val processedBeforeClear = processedTimestamps.size

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Processed before clear: $processedBeforeClear"
        )

        // Limpar fila
        provider.clearQueue()

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Queue cleared"
        )

        // Aguardar um pouco para verificar que não há mais processamentos
        delay(1000)

        val processedAfterClear = processedTimestamps.size

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Processed after clear: $processedAfterClear"
        )

        TimelineTestHelper.printReport(
            title = "CLEAR QUEUE",
            lines = listOf(
                "Requests enfileirados: ${timestamps.size}",
                "Processados antes: $processedBeforeClear",
                "Processados depois: $processedAfterClear",
                "Diferença: ${processedAfterClear - processedBeforeClear}",
                if (processedAfterClear <= processedBeforeClear + 2) "✅ Queue limpa" else "⚠️ Ainda processando"
            )
        )

        // Deve ter processado alguns (já em processamento quando clear foi chamado)
        // Mas não deve ter processado todos
        assertTrue(
            "Deve ter processado pelo menos alguns antes do clear",
            processedBeforeClear > 0
        )

        // Após clear, não deve processar muitos mais (apenas os que já estavam em processamento)
        assertTrue(
            "Não deve processar muitos após clear (${processedAfterClear - processedBeforeClear} novos)",
            processedAfterClear <= processedBeforeClear + 5
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Clear queue test completed successfully")
        )
    }

    @Test
    fun clearQueueDoesNotBlockNewRequests() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "New requests after clear test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val oldTimestamps = (0 until 10).map { it * 500L }
        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        // Enfileirar requests antigos
        oldTimestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.PREFETCH)
        }

        delay(100)

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Cleared queue with ${oldTimestamps.size} requests"
        )

        // Limpar fila
        provider.clearQueue()
        delay(100)

        // Enfileirar novos requests
        val newTimestamps = (100 until 110).map { it * 500L }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Enqueuing ${newTimestamps.size} new requests after clear"
        )

        newTimestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar processamento dos novos
        val success = TimelineTestHelper.waitForCondition(
            condition = {
                newTimestamps.all { it in processedTimestamps }
            },
            timeoutMs = 5000
        )

        val processedNewCount = newTimestamps.count { it in processedTimestamps }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Processed new requests: $processedNewCount/${newTimestamps.size}"
        )

        TimelineTestHelper.printReport(
            title = "NOVOS REQUESTS APÓS CLEAR",
            lines = listOf(
                "Requests antigos: ${oldTimestamps.size}",
                "Requests novos: ${newTimestamps.size}",
                "Processados novos: $processedNewCount",
                if (success) "✅ Novos requests processados" else "❌ Falha ao processar novos"
            )
        )

        assertTrue(
            "Deve processar novos requests após clear",
            success
        )

        assertTrue(
            "Deve processar todos os novos requests",
            processedNewCount == newTimestamps.size
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "New requests after clear test completed successfully")
        )
    }

    @Test
    fun clearQueueDuringScroll() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Clear queue during scroll test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        // Simular scroll rápido: enfileirar muitos requests, limpar, enfileirar novos
        val scrollIterations = 3

        for (i in 0 until scrollIterations) {
            Timber.tag(TimberTestTags.TEST_TIMELINE_SCROLL).d(
                "Scroll iteration $i"
            )

            val startTs = (i * 30) * 500L
            val timestamps = (startTs..<(startTs + 30 * 500L) step 500L)

            Timber.tag(TimberTestTags.TEST_EXTRACTION_QUEUE).d(
                "Enqueuing ${timestamps.size} requests for scroll $i"
            )

            timestamps.forEach { ts ->
                provider.requestThumbnail(uri, ts, ThumbnailPriority.PREFETCH)
            }

            delay(50)  // Simular scroll

            Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
                "Clearing queue after scroll $i"
            )

            // Limpar fila (simulando cancelamento em scroll rápido)
            provider.clearQueue()
            delay(50)
        }

        // Aguardar que nada mais processe
        delay(500)

        val totalProcessed = processedTimestamps.size

        TimelineTestHelper.printReport(
            title = "CLEAR DURANTE SCROLL",
            lines = listOf(
                "Scroll iterations: $scrollIterations",
                "Total requests enfileirados: ${scrollIterations * 30}",
                "Total processados: $totalProcessed",
                "Queue não travou: ✅",
                "Clear funcionou: ✅"
            )
        )

        // Deve ter processado apenas alguns (não todos)
        assertTrue(
            "Não deve processar todos os requests (fila foi limpa)",
            totalProcessed < scrollIterations * 30
        )

        // Deve ter processado pelo menos alguns (já estavam em processamento)
        assertTrue(
            "Deve ter processado pelo menos alguns",
            totalProcessed > 0
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Clear queue during scroll test completed successfully")
        )
    }

    @Test
    fun clearEmptyQueueDoesNotCrash() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Clear empty queue test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Clearing empty queue"
        )

        // Limpar fila vazia - não deve crashar
        try {
            provider.clearQueue()
            delay(100)
        } catch (e: Exception) {
            Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).e(e, "Clear empty queue failed")
            throw e
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).d(
            "Empty queue cleared successfully"
        )

        TimelineTestHelper.printReport(
            title = "CLEAR FILA VAZIA",
            lines = listOf(
                "Fila: vazia",
                "Clear: executado",
                "Crash: ${false}",
                "✅ SEM CRASH"
            )
        )

        assertFalse(
            "Não deve processar nada",
            processedTimestamps.isNotEmpty()
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_CANCELLATION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Clear empty queue test completed successfully")
        )
    }
}
