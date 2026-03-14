package com.chopcut.timeline

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chopcut.MainActivity
import com.chopcut.data.thumbnail.OptimizedThumbnailProvider
import com.chopcut.data.thumbnail.ThumbnailCache
import com.chopcut.data.thumbnail.ThumbnailPriority
import com.chopcut.instrumentedTestHelpers.TimelineTestHelper
import com.chopcut.instrumentedTestHelpers.TimberTestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Testa cache window em volta do playhead.
 *
 * O que verifica:
 * 1. Cache mantém thumbnails em janela do playhead (±X itens)
 * 2. Thumbnails distantes são evictados
 * 3. Cache window funciona durante scroll
 * 4. LRU eviction funciona corretamente
 *
 * Teste crítico (P0) - valida sistema de cache.
 */
@RunWith(AndroidJUnit4::class)
class CacheWindowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var provider: OptimizedThumbnailProvider
    private lateinit var cache: ThumbnailCache

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "CacheWindowTest setup")
        )
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            provider.release()
        }
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun cacheMaintainsWindowAroundPlayhead() = runBlocking {
        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Cache window around playhead test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val durationMs = 60_000L
        val itemCount = 900
        val windowSize = 100  // Janela de 100 itens (50 antes + 50 depois)

        cache = ThumbnailCache(windowSize)
        provider = OptimizedThumbnailProvider(
            testContext,
            cache = cache,
            thumbWidth = 120,
            thumbHeight = 120
        )

        // Simular playhead na posição 300 (33% da timeline)
        val playheadPosition = 300
        val playheadMs = (playheadPosition.toLong() * durationMs) / itemCount

        Timber.tag(TimberTestTags.TEST_TIMELINE_WINDOW).d(
            "Playhead at position $playheadPosition (${playheadMs}ms)"
        )

        // Carregar janela em volta do playhead
        val windowStart = (playheadPosition - windowSize / 2).coerceAtLeast(0)
        val windowEnd = (playheadPosition + windowSize / 2).coerceAtMost(itemCount - 1)

        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).d(
            "Window range: $windowStart - $windowEnd"
        )

        val timestampsInWindow = (windowStart..windowEnd).map { pos ->
            (pos.toLong() * durationMs) / itemCount
        }

        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        // Carregar janela
        timestampsInWindow.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar processamento
        val success1 = TimelineTestHelper.waitForCondition(
            condition = { processedTimestamps.size >= timestampsInWindow.size },
            timeoutMs = 10000
        )

        val cacheSize1 = cache.size()

        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).d(
            "Cache size after first window: $cacheSize1/$windowSize"
        )

        // Mover playhead para posição 600 (67% da timeline)
        val newPlayheadPosition = 600
        val newPlayheadMs = (newPlayheadPosition.toLong() * durationMs) / itemCount

        Timber.tag(TimberTestTags.TEST_TIMELINE_WINDOW).d(
            "Playhead moved to position $newPlayheadPosition (${newPlayheadMs}ms)"
        )

        // Carregar nova janela
        val newWindowStart = (newPlayheadPosition - windowSize / 2).coerceAtLeast(0)
        val newWindowEnd = (newPlayheadPosition + windowSize / 2).coerceAtMost(itemCount - 1)

        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).d(
            "New window range: $newWindowStart - $newWindowEnd"
        )

        val timestampsInNewWindow = (newWindowStart..newWindowEnd).map { pos ->
            (pos.toLong() * durationMs) / itemCount
        }

        // Carregar nova janela
        timestampsInNewWindow.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar processamento
        val success2 = TimelineTestHelper.waitForCondition(
            condition = { processedTimestamps.size >= timestampsInWindow.size + timestampsInNewWindow.size },
            timeoutMs = 10000
        )

        val cacheSize2 = cache.size()

        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).d(
            "Cache size after second window: $cacheSize2/$windowSize"
        )

        TimelineTestHelper.printReport(
            title = "CACHE WINDOW — RETENÇÃO",
            lines = listOf(
                "Window size: $windowSize",
                "Playhead 1: $playheadPosition (${playheadMs}ms)",
                "Window 1: $windowStart-$windowEnd",
                "Cache size 1: $cacheSize1",
                "Playhead 2: $newPlayheadPosition (${newPlayheadMs}ms)",
                "Window 2: $newWindowStart-$newWindowEnd",
                "Cache size 2: $cacheSize2",
                if (cacheSize2 <= windowSize) "✅ Cache mantém tamanho" else "❌ Cache cresceu demais"
            )
        )

        assertTrue(
            "Deve carregar primeira janela",
            success1
        )

        assertTrue(
            "Deve carregar segunda janela",
            success2
        )

        assertTrue(
            "Cache deve manter tamanho da janela",
            cacheSize2 <= windowSize
        )

        Timber.tag(TimberTestTags.TEST_CACHE_WINDOW).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Cache window test completed successfully")
        )
    }

    @Test
    fun lruEvictsDistantItems() = runBlocking {
        Timber.tag(TimberTestTags.TEST_CACHE_LRU).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "LRU eviction test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val cacheSize = 10

        cache = ThumbnailCache(cacheSize)
        provider = OptimizedThumbnailProvider(
            testContext,
            cache = cache,
            thumbWidth = 120,
            thumbHeight = 120
        )

        // Preencher cache
        val firstBatch = (0 until 10).map { it * 500L }
        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Filling cache with ${firstBatch.size} items"
        )

        firstBatch.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        val success1 = TimelineTestHelper.waitForCondition(
            condition = { processedTimestamps.size >= firstBatch.size },
            timeoutMs = 5000
        )

        val cacheSize1 = cache.size()

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Cache size after first batch: $cacheSize1"
        )

        // Adicionar mais items (deve evictar os mais antigos)
        val secondBatch = (10 until 20).map { it * 500L }

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Adding ${secondBatch.size} more items (should evict oldest)"
        )

        secondBatch.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        val success2 = TimelineTestHelper.waitForCondition(
            condition = { processedTimestamps.size >= firstBatch.size + secondBatch.size },
            timeoutMs = 5000
        )

        val cacheSize2 = cache.size()

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Cache size after second batch: $cacheSize2"
        )

        // Verificar que items mais antigos foram evictados
        val oldestItemStillInCache = cache.get(uri.toString(), firstBatch.first())

        TimelineTestHelper.printReport(
            title = "LRU EVICTION",
            lines = listOf(
                "Cache size: $cacheSize",
                "First batch: ${firstBatch.size} items",
                "Second batch: ${secondBatch.size} items",
                "Cache size after first: $cacheSize1",
                "Cache size after second: $cacheSize2",
                "Oldest item still in cache: ${oldestItemStillInCache != null}",
                if (cacheSize2 <= cacheSize) "✅ LRU funcionou" else "❌ Cache cresceu"
            )
        )

        assertTrue(
            "Deve preencher cache",
            success1
        )

        assertTrue(
            "Deve adicionar mais items",
            success2
        )

        assertTrue(
            "Cache deve manter tamanho máximo",
            cacheSize2 <= cacheSize
        )

        assertTrue(
            "Item mais antigo deve ter sido evictado",
            oldestItemStillInCache == null
        )

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "LRU eviction test completed successfully")
        )
    }

    @Test
    fun recentlyAccessedItemsAreNotEvicted() = runBlocking {
        Timber.tag(TimberTestTags.TEST_CACHE_LRU).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Recently accessed items retention test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val cacheSize = 10

        cache = ThumbnailCache(cacheSize)
        provider = OptimizedThumbnailProvider(
            testContext,
            cache = cache,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val allTimestamps = (0 until 10).map { it * 500L }
        val processedTimestamps = mutableSetOf<Long>()

        provider.thumbnailUpdates.collect { (ts, _) ->
            processedTimestamps.add(ts)
        }

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Filling cache with ${allTimestamps.size} items"
        )

        allTimestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        val success1 = TimelineTestHelper.waitForCondition(
            condition = { processedTimestamps.size >= allTimestamps.size },
            timeoutMs = 5000
        )

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "All items loaded"
        )

        // Acessar item 0 (torná-lo recentemente acessado)
        val item0 = cache.get(uri.toString(), allTimestamps[0])

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Accessed item 0: ${item0 != null}"
        )

        // Adicionar item 11 (deve evictar item 1, não item 0)
        val newItem = 10 * 500L

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Adding item $newItem (should evict item 1, not item 0)"
        )

        provider.requestThumbnail(uri, newItem, ThumbnailPriority.VISIBLE)

        val success2 = TimelineTestHelper.waitForCondition(
            condition = { newItem in processedTimestamps },
            timeoutMs = 5000
        )

        val cacheSizeAfter = cache.size()
        val item0StillPresent = cache.get(uri.toString(), allTimestamps[0]) != null
        val item1StillPresent = cache.get(uri.toString(), allTimestamps[1]) != null

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).d(
            "Cache size: $cacheSizeAfter"
        )

        TimelineTestHelper.printReport(
            title = "RECENTLY ACCESSED — RETENÇÃO",
            lines = listOf(
                "Cache size: $cacheSize",
                "Item 0 accessed: sim",
                "Item 11 added: sim",
                "Cache size after: $cacheSizeAfter",
                "Item 0 still present: $item0StillPresent",
                "Item 1 still present: $item1StillPresent",
                if (item0StillPresent && !item1StillPresent) "✅ LRU correto" else "❌ LRU incorreto"
            )
        )

        assertTrue(
            "Deve preencher cache",
            success1
        )

        assertTrue(
            "Deve adicionar novo item",
            success2
        )

        assertTrue(
            "Cache deve manter tamanho",
            cacheSizeAfter <= cacheSize
        )

        assertTrue(
            "Item 0 (acessado recentemente) deve permanecer",
            item0StillPresent
        )

        assertTrue(
            "Item 1 (não acessado) deve ser evictado",
            !item1StillPresent
        )

        Timber.tag(TimberTestTags.TEST_CACHE_LRU).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Recently accessed retention test completed successfully")
        )
    }
}
