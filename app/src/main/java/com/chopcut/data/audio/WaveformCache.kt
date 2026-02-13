package com.chopcut.data.audio

/**
 * Cache LRU para waveforms de áudio
 * Armazena os últimos 5 waveforms extraídos para evitar re-extração
 */
class WaveformCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE
) {
    private val cache = LinkedHashMap<String, AudioRawData>(maxSize, 0.75f, true)

    companion object {
        const val DEFAULT_MAX_SIZE = 5
    }

    private fun generateKey(uri: String): String = uri.hashCode().toString()

    fun get(uri: String): AudioRawData? {
        return cache[generateKey(uri)]
    }

    fun put(uri: String, data: AudioRawData, targetSampleRate: Int) {
        val key = generateKey(uri)
        if (cache.size >= maxSize && !cache.containsKey(key)) {
            cache.remove(cache.keys.first())
        }
        cache[key] = data
    }

    fun clear() {
        cache.clear()
    }
}
