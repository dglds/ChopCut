package com.chopcut

enum class CompressionLevel(
    val label: String,
    val description: String,
    val targetHeight: Int,
    val targetBitrateBps: Long
) {
    ORIGINAL("Original", "Máxima qualidade, arquivo maior", -1, -1L),
    MEDIUM("Média", "Equilíbrio entre qualidade e tamanho", 1080, 5_000_000L),
    LOW("Baixa", "Ideal para enviar rápido (WhatsApp)", 720, 2_500_000L);

    fun isViable(originalWidth: Int, originalHeight: Int, originalBitrateBps: Long): Boolean {
        if (this == ORIGINAL) return true
        val targetH = Math.min(targetHeight, originalHeight)
        if (originalBitrateBps <= 0L) {
            return targetH < originalHeight
        }
        val targetBitrate = Math.min(targetBitrateBps, originalBitrateBps)
        return targetH < originalHeight || targetBitrate < originalBitrateBps
    }
}
