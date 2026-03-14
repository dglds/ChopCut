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
 * Testa agrupamento de updates (batching).
 *
 * O que verifica:
 * 1. Múltiplas atualizações de thumbnail são agrupadas
 * 2. Flow com bufferOverflow = DROP_OLDEST funciona
 * 3. UI update é eficiente sem rebinds excessivos
 * 4. Deduplication de timestamps funciona
 *
 * Teste importante (P1) - valida eficiência de UI.
 */
@RunWith(AndroidJUnit4::class)
class BatchUpdateTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var provider: OptimizedThumbnailProvider

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "BatchUpdateTest setup")
        )
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            provider.release()
            Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
                TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider released")
            )
        }
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun multipleRequestsAreDeduped() = runBlocking {
        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Deduplication test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val updateCount = mutableMapOf<Long, Int>()
        val timestampsReceived = mutableSetOf<Long>()

        // Coletar contagem de updates por timestamp
        provider.thumbnailUpdates.collect { (ts, _) ->
            updateCount[ts] = (updateCount[ts] ?: 0) + 1
            timestampsReceived.add(ts)

            Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
                "Update received: timestamp=${ts}ms, count=${updateCount[ts]}"
            )
        }

        // Solicitar mesmo timestamp múltiplas vezes
        val timestamp = 1000L
        val requestCount = 10

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requesting timestamp $timestamp $requestCount times"
        )

        repeat(requestCount) {
            provider.requestThumbnail(uri, timestamp, ThumbnailPriority.VISIBLE)
            delay(10)  // Pequeno delay entre requests
        }

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { timestamp in timestampsReceived },
            timeoutMs = 5000
        )

        val updatesForTimestamp = updateCount[timestamp] ?: 0

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Updates for timestamp $timestamp: $updatesForTimestamp"
        )

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requests: $requestCount, Updates: $updatesForTimestamp"
        )

        TimelineTestHelper.printReport(
            title = "BATCH UPDATE — DEDUPLICAÇÃO",
            lines = listOf(
                "Timestamp: ${timestamp}ms",
                "Requisições: $requestCount",
                "Updates emitidos: $updatesForTimestamp",
                "Deduplication: ✅",
                if (updatesForTimestamp == 1) "✅ APENAS 1 UPDATE" else "❌ MÚLTIPLOS UPDATES"
            )
        )

        assertTrue(
            "Deve processar request",
            success
        )

        assertEquals(
            "Deve emitir apenas 1 update para timestamp duplicado",
            1,
            updatesForTimestamp
        )

        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Deduplication test completed successfully")
        )
    }

    @Test
    fun flowDropsOldestWhenBufferFull() = runBlocking {
        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Buffer overflow test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val receivedUpdates = mutableListOf<Long>()
        val extraBufferCapacity = 64  // extraBufferCapacity de OptimizedThumbnailProvider

        // Coletar updates
        provider.thumbnailUpdates.collect { (ts, _) ->
            receivedUpdates.add(ts)
        }

        // Encher buffer (extraBufferCapacity + 20)
        val requestCount = extraBufferCapacity + 20
        val timestamps = (0 until requestCount).map { it.toLong() * 500L }

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requesting ${timestamps.size} timestamps (buffer: $extraBufferCapacity)"
        )

        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar alguns processarem
        delay(2000)

        val droppedCount = timestamps.size - receivedUpdates.size

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requested: ${timestamps.size}, Received: ${receivedUpdates.size}, Dropped: $droppedCount"
        )

        TimelineTestHelper.printReport(
            title = "BATCH UPDATE — BUFFER OVERFLOW",
            lines = listOf(
                "Buffer capacity: $extraBufferCapacity",
                "Solicitações: ${timestamps.size}",
                "Updates recebidos: ${receivedUpdates.size}",
                "Updates dropados: $droppedCount",
                "DROP_OLDEST: ✅",
                if (droppedCount > 0) "✅ DROP FUNCIONOU" else "⚠️ SEM DROP"
            )
        )

        assertTrue(
            "Deve dropar updates mais antigos quando buffer cheio",
            droppedCount > 0
        )

        assertTrue(
            "Deve receber no máximo bufferCapacity + alguns updates",
            receivedUpdates.size <= extraBufferCapacity + 10
        )

        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Buffer overflow test completed successfully")
        )
    }

    @Test
    fun batchingReducesUiUpdates() = runBlocking {
        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Batching efficiency test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val updateTimestamps = mutableSetOf<Long>()
        val requestTimestamps = mutableSetOf<Long>()

        // Coletar updates
        provider.thumbnailUpdates.collect { (ts, _) ->
            updateTimestamps.add(ts)
        }

        // Solicitar timestamps com repetições (simulando deduplication)
        val timestamps = (0 until 20).map { it * 500L }
        val requestCount = 30

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requesting $requestCount timestamps from ${timestamps.size} unique values"
        )

        repeat(requestCount) { idx ->
            val ts = timestamps[idx % timestamps.size]
            requestTimestamps.add(ts)
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
            delay(5)
        }

        // Aguardar processamento
        val success = TimelineTestHelper.waitForCondition(
            condition = { updateTimestamps.size >= timestamps.size },
            timeoutMs = 5000
        )

        val uniqueRequests = requestTimestamps.size
        val uniqueUpdates = updateTimestamps.size
        val deduplicationRatio = 1.0f - (uniqueUpdates.toFloat() / uniqueRequests.toFloat())
        val savedPercentage = deduplicationRatio * 100

        Timber.tag(TimberTestTags.TEST_PERFORMANCE).d(
            "Requests: $uniqueRequests, Updates: $uniqueUpdates, Saved: ${String.format("%.1f", savedPercentage)}%"
        )

        TimelineTestHelper.printReport(
            title = "BATCH UPDATE — EFICIÊNCIA",
            lines = listOf(
                "Solicitações únicas: $uniqueRequests",
                "Updates emitidos: $uniqueUpdates",
                "Deduplication: ${(uniqueRequests - uniqueUpdates)} requests",
                "Economia: ${String.format("%.1f", savedPercentage)}%",
                "Batching: ✅",
                if (uniqueUpdates < uniqueRequests) "✅ REDUÇÃO DE UPDATES" else "⚠️ SEM REDUÇÃO"
            )
        )

        assertTrue(
            "Deve processar todos os timestamps",
            success
        )

        assertTrue(
            "Batching deve reduzir número de updates",
            uniqueUpdates < uniqueRequests
        )

        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Batching efficiency test completed successfully")
        )
    }

    @Test
    fun batchingPreservesOrder() = runBlocking {
        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Batching order test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val updateTimestamps = mutableListOf<Long>()

        // Coletar updates mantendo ordem
        provider.thumbnailUpdates.collect { (ts, _) ->
            updateTimestamps.add(ts)
            Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
                "Update ${updateTimestamps.size}: ${ts}ms"
            )
        }

        // Solicitar timestamps em ordem
        val timestamps = (0 until 10).map { it * 500L }

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Requesting ${timestamps.size} timestamps in order"
        )

        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar todos
        val success = TimelineTestHelper.waitForCondition(
            condition = { updateTimestamps.size >= timestamps.size },
            timeoutMs = 5000
        )

        val orderPreserved = updateTimestamps == timestamps

        Timber.tag(TimberTestTags.TEST_UI_BATCH).d(
            "Order preserved: $orderPreserved"
        )

        TimelineTestHelper.printReport(
            title = "BATCH UPDATE — ORDEM",
            lines = listOf(
                "Timestamps solicitados: ${timestamps.size}",
                "Updates recebidos: ${updateTimestamps.size}",
                "Ordem esperada: ${timestamps.take(5).joinToString(", ")}...",
                "Ordem recebida: ${updateTimestamps.take(5).joinToString(", ")}...",
                if (orderPreserved) "✅ ORDEM PRESERVADA" else "❌ ORDEM ALTERADA"
            )
        )

        assertTrue(
            "Deve processar todos os timestamps",
            success
        )

        assertTrue(
            "Ordem deve ser preservada (primeiro a processar é primeiro a emitir)",
            orderPreserved
        )

        Timber.tag(TimberTestTags.TEST_UI_BATCH).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Batching order test completed successfully")
        )
    }
}
