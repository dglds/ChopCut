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
        return synchronized(cache) {
            val bitmap = cache[key]
            when {
                bitmap == null -> { cacheMisses++; null }
                bitmap.isRecycled -> { cache.remove(key); cacheMisses++; null }
                else -> { cacheHits++; bitmap }
            }
        }
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
        synchronized(cache) {
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                cache.remove(cache.keys.first())
            }
            cache[key] = bitmap
        }
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
        synchronized(cache) {
            items.forEach { (key, bitmap) ->
                if (cache.size >= maxSize && !cache.containsKey(key)) {
                    cache.remove(cache.keys.first())
                }
                cache[key] = bitmap
            }
        }
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
        return synchronized(cache) {
            val total = cacheHits + cacheMisses
            val hitRate = if (total > 0) (cacheHits.toFloat() / total * 100) else 0f
            CacheStats(size = cache.size, maxSize = maxSize, hits = cacheHits, misses = cacheMisses, hitRate = hitRate)
        }
    }

    /**
     * Limpa todos os itens do cache e estatísticas.
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
            cacheHits = 0
            cacheMisses = 0
        }
    }

    /**
     * Retorna o número atual de itens no cache
     */
    fun size(): Int = synchronized(cache) { cache.size }

    /**
     * Verifica se o cache está vazio
     */
    fun isEmpty(): Boolean = synchronized(cache) { cache.isEmpty() }

    /**
     * Verifica se o cache contém uma chave específica
     */
    fun contains(uri: String, positionMs: Long): Boolean {
        return synchronized(cache) { cache.containsKey(generateKey(uri, positionMs)) }
    }

    /**
     * Remove um item específico do cache
     */
    fun remove(uri: String, positionMs: Long) {
        synchronized(cache) { cache.remove(generateKey(uri, positionMs)) }
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
