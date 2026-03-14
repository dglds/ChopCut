package com.chopcut.instrumentedTestHelpers

/**
 * Constantes de tags Timber para testes instrumentados.
 *
 * Padrão de naming:
 * - Prefixo "TEST_" para facilitar filtragem no logcat
 * - Sufixo por categoria (TIMELINE, EXTRACTION, CACHE, UI, MEMORY)
 * - Hierarquia com "." para subcategorias
 *
 * Uso no logcat:
 * adb logcat -s TEST_TIMELINE:*
 * adb logcat -s TEST_TIMELINE.EXTRACTION:*
 * adb logcat TEST_TIMELINE:* TEST_CACHE:*
 */
object TimberTestTags {

    // ============================================================================
    // TAGS PRINCIPAIS
    // ============================================================================

    const val TEST_TIMELINE = "TEST_TIMELINE"
    const val TEST_EXTRACTION = "TEST_EXTRACTION"
    const val TEST_CACHE = "TEST_CACHE"
    const val TEST_UI = "TEST_UI"
    const val TEST_MEMORY = "TEST_MEMORY"
    const val TEST_PERFORMANCE = "TEST_PERFORMANCE"

    // ============================================================================
    // SUBCATEGORIAS DE TIMELINE
    // ============================================================================

    const val TEST_TIMELINE_SCROLL = "TEST_TIMELINE.SCROLL"
    const val TEST_TIMELINE_PREFETCH = "TEST_TIMELINE.PREFETCH"
    const val TEST_TIMELINE_PROGRAMMATIC = "TEST_TIMELINE.PROGRAMMATIC"
    const val TEST_TIMELINE_WINDOW = "TEST_TIMELINE.WINDOW"

    // ============================================================================
    // SUBCATEGORIAS DE EXTRACTION
    // ============================================================================

    const val TEST_EXTRACTION_PRIORITY = "TEST_EXTRACTION.PRIORITY"
    const val TEST_EXTRACTION_POOL = "TEST_EXTRACTION.POOL"
    const val TEST_EXTRACTION_QUEUE = "TEST_EXTRACTION.QUEUE"
    const val TEST_EXTRACTION_CANCELLATION = "TEST_EXTRACTION.CANCELLATION"
    const val TEST_EXTRACTION_CONCURRENCY = "TEST_EXTRACTION.CONCURRENCY"

    // ============================================================================
    // SUBCATEGORIAS DE CACHE
    // ============================================================================

    const val TEST_CACHE_HIT = "TEST_CACHE.HIT"
    const val TEST_CACHE_MISS = "TEST_CACHE.MISS"
    const val TEST_CACHE_EVICTION = "TEST_CACHE.EVICTION"
    const val TEST_CACHE_WINDOW = "TEST_CACHE.WINDOW"
    const val TEST_CACHE_LRU = "TEST_CACHE.LRU"

    // ============================================================================
    // SUBCATEGORIAS DE UI
    // ============================================================================

    const val TEST_UI_ADAPTER = "TEST_UI.ADAPTER"
    const val TEST_UI_VIEWHOLDER = "TEST_UI.VIEWHOLDER"
    const val TEST_UI_BINDING = "TEST_UI.BINDING"
    const val TEST_UI_PAYLOAD = "TEST_UI.PAYLOAD"
    const val TEST_UI_BATCH = "TEST_UI.BATCH"

    // ============================================================================
    // SUBCATEGORIAS DE MEMORY
    // ============================================================================

    const val TEST_MEMORY_LEAK = "TEST_MEMORY.LEAK"
    const val TEST_MEMORY_USAGE = "TEST_MEMORY.USAGE"
    const val TEST_MEMORY_RECYCLE = "TEST_MEMORY.RECYCLE"
    const val TEST_MEMORY_GC = "TEST_MEMORY.GC"

    // ============================================================================
    // SUBCATEGORIAS DE PERFORMANCE
    // ============================================================================

    const val TEST_PERFORMANCE_FPS = "TEST_PERFORMANCE.FPS"
    const val TEST_PERFORMANCE_LATENCY = "TEST_PERFORMANCE.LATENCY"
    const val TEST_PERFORMANCE_THROUGHPUT = "TEST_PERFORMANCE.THROUGHPUT"

    // ============================================================================
    // PREFIXOS PARA MENSAGENS
    // ============================================================================

    const val PREFIX_SUCCESS = "✅"
    const val PREFIX_FAILURE = "❌"
    const val PREFIX_WARNING = "⚠️"
    const val PREFIX_INFO = "ℹ️"
    const val PREFIX_STARTED = "▶️"
    const val PREFIX_COMPLETED = "🏁"
    const val PREFIX_MEASURED = "📏"
    const val PREFIX_WAITING = "⏳"

    // ============================================================================
    // FUNÇÕES DE CONVENIÊNCIA
    // ============================================================================

    /**
     * Obtém todas as tags principais.
     */
    fun allMainTags(): List<String> = listOf(
        TEST_TIMELINE,
        TEST_EXTRACTION,
        TEST_CACHE,
        TEST_UI,
        TEST_MEMORY,
        TEST_PERFORMANCE
    )

    /**
     * Obtém todas as tags de uma categoria.
     */
    fun getTagsForCategory(category: String): List<String> {
        return when (category) {
            "TIMELINE" -> listOf(
                TEST_TIMELINE,
                TEST_TIMELINE_SCROLL,
                TEST_TIMELINE_PREFETCH,
                TEST_TIMELINE_PROGRAMMATIC,
                TEST_TIMELINE_WINDOW
            )
            "EXTRACTION" -> listOf(
                TEST_EXTRACTION,
                TEST_EXTRACTION_PRIORITY,
                TEST_EXTRACTION_POOL,
                TEST_EXTRACTION_QUEUE,
                TEST_EXTRACTION_CANCELLATION,
                TEST_EXTRACTION_CONCURRENCY
            )
            "CACHE" -> listOf(
                TEST_CACHE,
                TEST_CACHE_HIT,
                TEST_CACHE_MISS,
                TEST_CACHE_EVICTION,
                TEST_CACHE_WINDOW,
                TEST_CACHE_LRU
            )
            "UI" -> listOf(
                TEST_UI,
                TEST_UI_ADAPTER,
                TEST_UI_VIEWHOLDER,
                TEST_UI_BINDING,
                TEST_UI_PAYLOAD,
                TEST_UI_BATCH
            )
            "MEMORY" -> listOf(
                TEST_MEMORY,
                TEST_MEMORY_LEAK,
                TEST_MEMORY_USAGE,
                TEST_MEMORY_RECYCLE,
                TEST_MEMORY_GC
            )
            "PERFORMANCE" -> listOf(
                TEST_PERFORMANCE,
                TEST_PERFORMANCE_FPS,
                TEST_PERFORMANCE_LATENCY,
                TEST_PERFORMANCE_THROUGHPUT
            )
            else -> emptyList()
        }
    }

    /**
     * Formata mensagem com prefixo.
     */
    fun formatMessage(prefix: String, message: String): String {
        return "$prefix $message"
    }

    /**
     * Formata métrica de performance.
     */
    fun formatMetric(name: String, value: Long, unit: String): String {
        return "$name: ${value}$unit"
    }

    /**
     * Formata porcentagem.
     */
    fun formatPercentage(value: Float, decimals: Int = 1): String {
        return "${String.format("%.${decimals}f", value)}%"
    }
}
