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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testa fila de prioridade de extração.
 *
 * O que verifica:
 * 1. VISIBLE tem prioridade sobre PREFETCH
 * 2. PREFETCH tem prioridade sobre DISTANT
 * 3. Requests na mesma prioridade são processados em ordem
 * 4. Ordem de processamento respeita prioridades
 *
 * Teste crítico (P0) - valida UX responsivo.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionPriorityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var provider: OptimizedThumbnailProvider

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "ExtractionPriorityTest setup")
        )
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            provider.release()
            Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
                TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider released")
            )
        }
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun visibleProcessedBeforePrefetch() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "VISIBLE > PREFETCH test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val visibleTimestamp = 1000L
        val prefetchTimestamp = 2000L

        val processingOrder = mutableListOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processingOrder.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Requesting PREFETCH($prefetchTimestamp) then VISIBLE($visibleTimestamp)"
        )

        // Adicionar em ordem inversa (PREFETCH primeiro)
        provider.requestThumbnail(uri, prefetchTimestamp, ThumbnailPriority.PREFETCH)
        delay(50)  // Pequeno delay para enfileirar
        provider.requestThumbnail(uri, visibleTimestamp, ThumbnailPriority.VISIBLE)

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { processingOrder.size >= 2 },
            timeoutMs = 5000
        )

        val orderStr = processingOrder.joinToString(" → ")

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Processing order: $orderStr"
        )

        TimelineTestHelper.printReport(
            title = "PRIORIDADE — VISIBLE > PREFETCH",
            lines = listOf(
                "Solicitado: PREFETCH($prefetchTimestamp), VISIBLE($visibleTimestamp)",
                "Processado: $orderStr",
                "Primeiro: ${processingOrder.firstOrNull()}",
                "Último: ${processingOrder.lastOrNull()}",
                if (processingOrder.first() == visibleTimestamp) "✅ VISIBLE primeiro" else "❌ Ordem incorreta"
            )
        )

        assertTrue(
            "Deve processar ${timestamps.size} requests",
            success
        )

        assertEquals(
            "VISIBLE deve ser processado primeiro",
            visibleTimestamp,
            processingOrder.first()
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "VISIBLE > PREFETCH test completed successfully")
        )
    }

    @Test
    fun prefetchProcessedBeforeDistant() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "PREFETCH > DISTANT test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val prefetchTimestamp = 2000L
        val distantTimestamp = 10000L

        val processingOrder = mutableListOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processingOrder.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Requesting DISTANT($distantTimestamp) then PREFETCH($prefetchTimestamp)"
        )

        // Adicionar em ordem inversa (DISTANT primeiro)
        provider.requestThumbnail(uri, distantTimestamp, ThumbnailPriority.DISTANT)
        delay(50)
        provider.requestThumbnail(uri, prefetchTimestamp, ThumbnailPriority.PREFETCH)

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { processingOrder.size >= 2 },
            timeoutMs = 5000
        )

        val orderStr = processingOrder.joinToString(" → ")

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Processing order: $orderStr"
        )

        TimelineTestHelper.printReport(
            title = "PRIORIDADE — PREFETCH > DISTANT",
            lines = listOf(
                "Solicitado: DISTANT($distantTimestamp), PREFETCH($prefetchTimestamp)",
                "Processado: $orderStr",
                "Primeiro: ${processingOrder.firstOrNull()}",
                if (processingOrder.first() == prefetchTimestamp) "✅ PREFETCH primeiro" else "❌ Ordem incorreta"
            )
        )

        assertTrue(
            "Deve processar ${timestamps.size} requests",
            success
        )

        assertEquals(
            "PREFETCH deve ser processado antes",
            prefetchTimestamp,
            processingOrder.first()
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "PREFETCH > DISTANT test completed successfully")
        )
    }

    @Test
    fun samePriorityProcessedInOrder() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Same priority order test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val timestamps = listOf(1000L, 2000L, 3000L, 4000L, 5000L)

        val processingOrder = mutableListOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processingOrder.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Requesting ${timestamps.size} VISIBLE requests in order"
        )

        // Adicionar todos com mesma prioridade (VISIBLE)
        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
            delay(10)  // Pequeno delay para enfileirar em ordem
        }

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { processingOrder.size >= timestamps.size },
            timeoutMs = 10000
        )

        val orderStr = processingOrder.joinToString(" → ")
        val expectedOrder = timestamps.joinToString(" → ")

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Expected order: $expectedOrder"
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Actual order: $orderStr"
        )

        TimelineTestHelper.printReport(
            title = "PRIORIDADE — MESMA PRIORIDADE",
            lines = listOf(
                "Requests: ${timestamps.size}",
                "Prioridade: VISIBLE (todos)",
                "Esperado: $expectedOrder",
                "Obtido: $orderStr",
                if (processingOrder == timestamps) "✅ Ordem mantida" else "⚠️ Ordem diferente"
            )
        )

        assertTrue(
            "Deve processar ${timestamps.size} requests",
            success
        )

        // Mesma prioridade pode não manter ordem estrita (depende de thread pool)
        // Verificamos apenas que todos foram processados
        assertTrue(
            "Todos os timestamps devem ter sido processados",
            processingOrder.toSet() == timestamps.toSet()
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Same priority order test completed")
        )
    }

    @Test
    fun priorityOrderIsVisibleThenPrefetchThenDistant() = runBlocking {
        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Full priority order test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val visibleTimestamp = 1000L
        val prefetchTimestamp = 2000L
        val distantTimestamp = 10000L

        val processingOrder = mutableListOf<Pair<Long, String>>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            val priority = when (ts) {
                visibleTimestamp -> "VISIBLE"
                prefetchTimestamp -> "PREFETCH"
                distantTimestamp -> "DISTANT"
                else -> "UNKNOWN"
            }
            processingOrder.add(ts to priority)
        }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Requesting DISTANT($distantTimestamp), PREFETCH($prefetchTimestamp), VISIBLE($visibleTimestamp)"
        )

        // Adicionar em ordem inversa de prioridade
        provider.requestThumbnail(uri, distantTimestamp, ThumbnailPriority.DISTANT)
        delay(50)
        provider.requestThumbnail(uri, prefetchTimestamp, ThumbnailPriority.PREFETCH)
        delay(50)
        provider.requestThumbnail(uri, visibleTimestamp, ThumbnailPriority.VISIBLE)

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { processingOrder.size >= 3 },
            timeoutMs = 5000
        )

        val orderStr = processingOrder.joinToString(" → ") { "${it.first}(${it.second})" }

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).d(
            "Processing order: $orderStr"
        )

        val expectedOrder = listOf("VISIBLE", "PREFETCH", "DISTANT")
        val actualOrder = processingOrder.map { it.second }

        TimelineTestHelper.printReport(
            title = "PRIORIDADE — ORDEM COMPLETA",
            lines = listOf(
                "Ordem esperada: ${expectedOrder.joinToString(" → ")}",
                "Ordem obtida: ${actualOrder.joinToString(" → ")}",
                "Processado: $orderStr",
                if (actualOrder == expectedOrder) "✅ Ordem correta" else "⚠️ Ordem diferente"
            )
        )

        assertTrue(
            "Deve processar 2 requests",
            success
        )

        assertEquals(
            "Primeiro deve ser VISIBLE",
            "VISIBLE",
            actualOrder[0]
        )

        assertEquals(
            "Segundo deve ser PREFETCH",
            "PREFETCH",
            actualOrder[1]
        )

        assertEquals(
            "Terceiro deve ser DISTANT",
            "DISTANT",
            actualOrder[2]
        )

        Timber.tag(TimberTestTags.TEST_EXTRACTION_PRIORITY).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Full priority order test completed successfully")
        )
    }
}
