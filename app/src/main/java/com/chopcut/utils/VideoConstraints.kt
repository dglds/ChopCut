package com.chopcut.utils

object VideoConstraints {
    const val MAX_DURATION_MS = 15 * 60 * 1000L // 15 minutos
    const val MAX_DURATION_SECONDS = 900
    
    fun isDurationValid(durationMs: Long): Boolean {
        // Desativado para testes conforme solicitado
        return true
    }
    
    fun getValidationMessage(durationMs: Long): String? {
        // Sem restrição de tempo para testes
        return null
    }
}
