package com.chopcut.utils

object VideoConstraints {
    const val MAX_DURATION_MS = 15 * 60 * 1000L // 15 minutos
    const val MAX_DURATION_SECONDS = 900
    
    fun isDurationValid(durationMs: Long): Boolean {
        return durationMs <= MAX_DURATION_MS
    }
    
    fun getValidationMessage(durationMs: Long): String? {
        return if (!isDurationValid(durationMs)) {
            val durationMin = (durationMs / 60000).toInt()
            "Vídeo excede o limite de 15 minutos (${durationMin}min). " +
            "Por favor, selecione um vídeo menor."
        } else null
    }
}
