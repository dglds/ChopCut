package com.chopcut.data.thumbnail

import android.graphics.Bitmap
import timber.log.Timber

/**
 * Cache LRU (Least Recently Used) para thumbnails de timeline
 *
 * Armazena bitmaps em memória com limite máximo de itens.
 * Quando o limite é atingido, os itens menos usados são removidos.
 * 
 * Implementa o padrão Cache-Aside para carregamento eficiente.
 */
class ThumbnailCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val cache = LinkedHashMap<String, Bitmap>(maxSize, 0.75f, true)
    
    private var cacheHits = 0
    private var cacheMisses = 0

    companion object {
        const val DEFAULT_MAX_SIZE = 50 // Número máximo de thumbnails em cache
    }

    /**
     * Gera uma chave única para o cache baseada na URI e posição
     */
    private fun generateKey(uri: String, positionMs: Long): String {
        return "${uri}_${positionMs}"
    }

    /**
     * Obtém um thumbnail do cache
     *
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @return Bitmap se encontrado no cache, null caso contrário
     */
    fun get(uri: String, positionMs: Long): Bitmap? {
        val key = generateKey(uri, positionMs)
        val bitmap = cache[key]

        if (bitmap != null) {
            cacheHits++
            Timber.d("Cache HIT: $key")
        } else {
            cacheMisses++
            Timber.d("Cache MISS: $key")
        }

        return bitmap
    }

    /**
     * Adiciona um thumbnail ao cache
     *
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @param bitmap Bitmap a ser armazenado
     */
    fun put(uri: String, positionMs: Long, bitmap: Bitmap) {
        val key = generateKey(uri, positionMs)

        if (cache.size >= maxSize && !cache.containsKey(key)) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
            Timber.d("Cache FULL: removendo $oldestKey")
        }

        cache[key] = bitmap
        Timber.d("Cache PUT: $key (size: ${cache.size}/$maxSize)")
    }

    /**
     * Adiciona múltiplos thumbnails ao cache de uma vez (batch insertion).
     * Útil para pré-carregamento de strips.
     * 
     * Resolve Problema 1: Batch insertion para pré-carregamento
     * 
     * @param items Mapa de chave -> bitmap para adicionar ao cache
     */
    fun putAll(items: Map<String, Bitmap>) {
        items.forEach { (key, bitmap) ->
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                val oldestKey = cache.keys.first()
                cache.remove(oldestKey)
            }
            cache[key] = bitmap
        }
        Timber.d("Cache PUTALL: ${items.size} items adicionados (size: ${cache.size}/$maxSize)")
    }

    /**
     * Obtém um thumbnail do cache ou executa o provider para gerar um novo.
     * Implementa o padrão Cache-Aside.
     * 
     * Resolve Problema 4: Pattern cache-aside para carregamento inteligente
     * 
     * @param uri URI do vídeo
     * @param positionMs Posição em milissegundos
     * @param provider Função para gerar o bitmap se não estiver no cache
     * @return Bitmap (do cache ou gerado pelo provider)
     */
    suspend fun getOrPut(uri: String, positionMs: Long, provider: suspend () -> Bitmap): Bitmap {
        val cached = get(uri, positionMs)
        if (cached != null) {
            return cached
        }
        
        val bitmap = provider()
        put(uri, positionMs, bitmap)
        return bitmap
    }

    /**
     * Obtém estatísticas do cache para debug e monitoramento.
     * 
     * Resolve Problema 4: Estatísticas de hit rate para monitoramento
     * 
     * @return CacheStats com estatísticas atuais
     */
    fun getStats(): CacheStats {
        val total = cacheHits + cacheMisses
        val hitRate = if (total > 0) (cacheHits.toFloat() / total * 100) else 0f
        
        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            hits = cacheHits,
            misses = cacheMisses,
            hitRate = hitRate
        )
    }

    /**
     * Limpa todos os itens do cache e estatísticas.
     */
    fun clear() {
        cache.clear()
        cacheHits = 0
        cacheMisses = 0
        Timber.d("Cache CLEARED")
    }

    /**
     * Retorna o número atual de itens no cache
     */
    fun size(): Int = cache.size

    /**
     * Verifica se o cache está vazio
     */
    fun isEmpty(): Boolean = cache.isEmpty()

    /**
     * Verifica se o cache contém uma chave específica
     */
    fun contains(uri: String, positionMs: Long): Boolean {
        return cache.containsKey(generateKey(uri, positionMs))
    }
    
    /**
     * Estatísticas do cache LRU.
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hits: Int,
        val misses: Int,
        val hitRate: Float
    ) {
        fun toLogString(): String {
            return """
                ╔═════════════════════════════════════════════════════════╗
                ║              THUMBNAIL CACHE LRU - STATS               ║
                ╚═════════════════════════════════════════════════════════╝
                
                📊 TAMANHO:
                   • Itens em cache: $size
                   • Limite máximo: $maxSize
                   • Utilização: ${size * 100 / maxSize}%
                
                🎯 PERFORMANCE:
                   • Cache hits: $hits
                   • Cache misses: $misses
                   • Hit rate: ${String.format("%.2f", hitRate)}%
                ╚═════════════════════════════════════════════════════════╝
            """.trimIndent()
        }
    }
}
