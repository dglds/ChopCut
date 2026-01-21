package com.chopcut.data.thumbnail

import android.graphics.Bitmap
import timber.log.Timber

/**
 * Cache LRU (Least Recently Used) para thumbnails de timeline
 *
 * Armazena bitmaps em memória com limite máximo de itens.
 * Quando o limite é atingido, os itens menos usados são removidos.
 */
class ThumbnailCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val cache = LinkedHashMap<String, Bitmap>(maxSize, 0.75f, true)

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
            Timber.d("Cache HIT: $key")
        } else {
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

        // Remover o item mais antigo se o cache estiver cheio
        if (cache.size >= maxSize && !cache.containsKey(key)) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
            Timber.d("Cache FULL: removendo $oldestKey")
        }

        cache[key] = bitmap
        Timber.d("Cache PUT: $key (size: ${cache.size}/$maxSize)")
    }

    /**
     * Limpa todos os itens do cache
     */
    fun clear() {
        cache.clear()
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
}
