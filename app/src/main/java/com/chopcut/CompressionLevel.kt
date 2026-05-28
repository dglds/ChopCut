package com.chopcut

enum class CompressionLevel(val label: String, val description: String) {
    ORIGINAL("Original", "Máxima qualidade, arquivo maior"),
    MEDIUM("Média", "Equilíbrio entre qualidade e tamanho"),
    LOW("Baixa", "Ideal para enviar rápido (WhatsApp)")
}
