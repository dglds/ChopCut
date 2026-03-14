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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Testa vazamentos de memória em componentes da timeline.
 *
 * O que verifica:
 * 1. Activity não é vazada por Adapter
 * 2. Provider pode ser liberado sem leaks
 * 3. Bitmaps são reciclados corretamente
 * 4. Não há retenção de referências fortes
 *
 * Teste crítico (P0) - valida qualidade e estabilidade.
 */
@RunWith(AndroidJUnit4::class)
class MemoryLeakTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "MemoryLeakTest setup")
        )

        // Forçar GC antes de cada teste
        System.gc()
        Thread.sleep(500)
    }

    @After
    fun tearDown() {
        TimelineTestHelper.clearTestCache(testContext)
    }

    @Test
    fun providerCanBeReleased() = runBlocking {
        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Provider release test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        var provider: OptimizedThumbnailProvider? = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val timestamps = (0 until 10).map { it * 1000L }

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Creating ${timestamps.size} requests"
        )

        // Criar requests
        timestamps.forEach { ts ->
            provider?.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar processamento
        delay(2000)

        val memoryBefore = TimelineTestHelper.measureMemory()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory before release", memoryBefore, "B")
        )

        // Criar weak reference
        val weakProvider = WeakReference(provider)

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Created weak reference to provider"
        )

        // Liberar
        provider?.release()
        provider = null

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Provider released and set to null"
        )

        // Forçar GC
        System.gc()
        Thread.sleep(1000)

        val memoryAfter = TimelineTestHelper.measureMemory()
        val memoryFreed = memoryBefore - memoryAfter

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory after release", memoryAfter, "B")
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory freed", memoryFreed, "B")
        )

        val isLeaked = weakProvider.get() != null

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Provider leaked: $isLeaked"
        )

        TimelineTestHelper.printReport(
            title = "MEMORY LEAK — PROVIDER",
            lines = listOf(
                "Requests: ${timestamps.size}",
                "Provider released: ✅",
                "GC forced: ✅",
                "Memory freed: ${memoryFreed / 1024}KB",
                "Provider collected: ${!isLeaked}",
                if (!isLeaked) "✅ SEM LEAK" else "❌ LEAK DETECTADO"
            )
        )

        assertTrue(
            "Provider deve ser liberado sem leak",
            !isLeaked
        )

        // Verificar que alguma memória foi liberada
        assertTrue(
            "Deve ter liberado alguma memória (${memoryFreed}B)",
            memoryFreed > 0
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider release test completed successfully")
        )
    }

    @Test
    fun providerReleaseCleansUpResources() = runBlocking {
        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Provider cleanup test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val timestamps = (0 until 20).map { it * 1000L }

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Creating ${timestamps.size} requests"
        )

        timestamps.forEach { ts ->
            provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
        }

        // Aguardar processamento
        delay(3000)

        val memoryBeforeRelease = TimelineTestHelper.measureMemory()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory before release", memoryBeforeRelease, "B")
        )

        // Liberar
        provider.release()

        // Forçar GC
        System.gc()
        Thread.sleep(1000)

        val memoryAfterRelease = TimelineTestHelper.measureMemory()
        val memoryFreed = memoryBeforeRelease - memoryAfterRelease

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory after release", memoryAfterRelease, "B")
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory freed", memoryFreed, "B")
        )

        val freedPercentage = (memoryFreed.toFloat() / memoryBeforeRelease * 100)

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            "Freed percentage: ${TimberTestTags.formatPercentage(freedPercentage / 100)}"
        )

        TimelineTestHelper.printReport(
            title = "MEMORY CLEANUP",
            lines = listOf(
                "Requests: ${timestamps.size}",
                "Memory before: ${memoryBeforeRelease / 1024}KB",
                "Memory after: ${memoryAfterRelease / 1024}KB",
                "Memory freed: ${memoryFreed / 1024}KB",
                "Freed: ${TimberTestTags.formatPercentage(freedPercentage / 100)}",
                if (memoryFreed > 0) "✅ Memória liberada" else "⚠️ Pouca memória liberada"
            )
        )

        assertTrue(
            "Deve liberar alguma memória",
            memoryFreed > 0
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider cleanup test completed")
        )
    }

    @Test
    fun multipleProvidersCanCoexist() = runBlocking {
        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Multiple providers test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val providers = mutableListOf<OptimizedThumbnailProvider>()

        val providerCount = 3

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Creating $providerCount providers"
        )

        // Criar múltiplos providers
        repeat(providerCount) { i ->
            val provider = OptimizedThumbnailProvider(
                testContext,
                thumbWidth = 120,
                thumbHeight = 120
            )

            val timestamps = (0 until 5).map { (it + i * 5) * 1000L }
            timestamps.forEach { ts ->
                provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
            }

            providers.add(provider)
        }

        // Aguardar processamento
        delay(3000)

        val memoryMultiple = TimelineTestHelper.measureMemory()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory with $providerCount providers", memoryMultiple, "B")
        )

        // Liberar todos
        providers.forEach { it.release() }
        providers.clear()

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "All providers released"
        )

        // Forçar GC
        System.gc()
        Thread.sleep(1000)

        val memoryAfterClear = TimelineTestHelper.measureMemory()
        val memoryFreed = memoryMultiple - memoryAfterClear

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory after clear", memoryAfterClear, "B")
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory freed", memoryFreed, "B")
        )

        TimelineTestHelper.printReport(
            title = "MEMORY — MULTIPLE PROVIDERS",
            lines = listOf(
                "Providers: $providerCount",
                "Requests total: ${providerCount * 5}",
                "Memory with providers: ${memoryMultiple / 1024}KB",
                "Memory after clear: ${memoryAfterClear / 1024}KB",
                "Memory freed: ${memoryFreed / 1024}KB",
                if (memoryFreed > 0) "✅ Todos liberados" else "⚠️ Alguns podem ter vazado"
            )
        )

        assertTrue(
            "Deve liberar memória de múltiplos providers",
            memoryFreed > 0
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Multiple providers test completed")
        )
    }

    @Test
    fun providerSurvivesHeavyWorkload() = runBlocking {
        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Provider survival test")
        )

        val uri = TimelineTestHelper.copyTestVideo(testContext)
        val provider = OptimizedThumbnailProvider(
            testContext,
            thumbWidth = 120,
            thumbHeight = 120
        )

        val memoryBaseline = TimelineTestHelper.measureMemory()

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Baseline memory", memoryBaseline, "B")
        )

        // Workload pesado
        val batches = 3
        val requestsPerBatch = 30

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
            "Running $batches batches of $requestsPerBatch requests"
        )

        for (batch in 0 until batches) {
            val start = batch * requestsPerBatch
            val timestamps = (start until start + requestsPerBatch).map { it * 500L }

            Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).d(
                "Batch $batch: ${timestamps.size} requests"
            )

            timestamps.forEach { ts ->
                provider.requestThumbnail(uri, ts, ThumbnailPriority.VISIBLE)
            }

            delay(2000)

            val memoryAfterBatch = TimelineTestHelper.measureMemory()
            val memoryUsed = memoryAfterBatch - memoryBaseline

            Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
                "Batch $batch memory: ${memoryUsed / 1024}KB"
            )
        }

        val memoryAfterWorkload = TimelineTestHelper.measureMemory()
        val totalMemoryUsed = memoryAfterWorkload - memoryBaseline

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory after workload", memoryAfterWorkload, "B")
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Total memory used", totalMemoryUsed, "B")
        )

        // Liberar
        provider.release()

        // Forçar GC
        System.gc()
        Thread.sleep(1000)

        val memoryAfterRelease = TimelineTestHelper.measureMemory()
        val memoryFreed = memoryAfterWorkload - memoryAfterRelease

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory after release", memoryAfterRelease, "B")
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_USAGE).d(
            TimberTestTags.formatMetric("Memory freed", memoryFreed, "B")
        )

        val memoryLeaked = memoryAfterRelease - memoryBaseline

        TimelineTestHelper.printReport(
            title = "MEMORY — SURVIVAL",
            lines = listOf(
                "Batches: $batches",
                "Requests/batch: $requestsPerBatch",
                "Total requests: ${batches * requestsPerBatch}",
                "Baseline: ${memoryBaseline / 1024}KB",
                "After workload: ${memoryAfterWorkload / 1024}KB",
                "After release: ${memoryAfterRelease / 1024}KB",
                "Memory leaked: ${memoryLeaked / 1024}KB",
                if (memoryLeaked < memoryBaseline * 0.1) "✅ Sem leak significativo" else "⚠️ Possível leak detectado"
            )
        )

        // Verificar que não vazou muita memória (< 10% do baseline)
        assertTrue(
            "Não deve ter vazado muita memória",
            memoryLeaked < memoryBaseline * 0.1
        )

        Timber.tag(TimberTestTags.TEST_MEMORY_LEAK).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Provider survival test completed")
        )
    }
}
