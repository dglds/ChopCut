package com.chopcut.data.audio

import timber.log.Timber

/**
 * Cache LRU para waveforms de áudio
 *
 * Armazena waveforms extraídos para evitar re-extração do mesmo vídeo
 * Usa resolução adaptiva baseada na duração do vídeo
 */
class WaveformCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val cache = LinkedHashMap<String, CachedWaveform>(maxSize, 0.75f, true)

    companion object {
        const val DEFAULT_MAX_SIZE = 5
    }

    private data class CachedWaveform(
        val data: AudioRawData,
        val targetSampleRate: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Gera uma chave única para o cache
     */
    private fun generateKey(uri: String): String {
        return uri.hashCode().toString()
    }

    /**
     * Obtém um waveform do cache
     */
    fun get(uri: String): AudioRawData? {
        val key = generateKey(uri)
        val cached = cache[key]

        if (cached != null) {
            Timber.d("WaveformCache HIT: $key (${cached.data.durationMs}ms)")
            return cached.data
        }

        Timber.d("WaveformCache MISS: $key")
        return null
    }

    /**
     * Adiciona um waveform ao cache
     */
    fun put(uri: String, data: AudioRawData, targetSampleRate: Int) {
        val key = generateKey(uri)

        if (cache.size >= maxSize && !cache.containsKey(key)) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
            Timber.d("WaveformCache FULL: removendo $oldestKey")
        }

        cache[key] = CachedWaveform(data, targetSampleRate)
        Timber.d("WaveformCache PUT: $key (size: ${cache.size}/$maxSize, ${data.pcmSamples.size} pts, ${targetSampleRate}Hz)")
    }

    /**
     * Verifica se um waveform está no cache
     */
    fun contains(uri: String): Boolean {
        return cache.containsKey(generateKey(uri))
    }

    /**
     * Limpa todos os itens do cache
     */
    fun clear() {
        cache.clear()
        Timber.d("WaveformCache CLEARED")
    }

    /**
     * Retorna o número atual de itens no cache
     */
    fun size(): Int = cache.size
}
