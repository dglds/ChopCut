package com.chopcut.data.model

/**
 * Qualidade da extração de thumbnails
 */
enum class ThumbnailQuality {
    LOW,    // Rápido, downsample agressivo
    HIGH    // Alta fidelidade, filtros de anti-aliasing
}

/**
 * Configurações para extração de thumbnails em massa
 */
data class ThumbnailSettings(
    val thumbsPerSecond: Int = 1,        // 1-10 thumbs por segundo
    val quality: Int = 85,                // 50-100 (JPEG)
    val format: ThumbnailFormat = ThumbnailFormat.JPEG,
    val dimensionPreset: DimensionPreset = DimensionPreset.MEDIUM,
    val extractionQuality: ThumbnailQuality = ThumbnailQuality.HIGH
)

/**
 * Formatos de imagem suportados para extração de thumbnails
 */
enum class ThumbnailFormat(val displayName: String, val description: String) {
    JPEG("JPEG", "Melhor compressão"),
    PNG("PNG", "Sem perdas, arquivos maiores"),
    WEBP("WebP", "Moderno, bom balanceamento")
}

/**
 * Presets de dimensão para thumbnails
 */
enum class DimensionPreset(
    val displayName: String,
    val width: Int,
    val height: Int
) {
    SMALL("Pequeno (240x135)", 240, 135),
    MEDIUM("Médio (320x180)", 320, 180),
    LARGE("Grande (480x270)", 480, 270),
    HD("HD (640x360)", 640, 360)
}

/**
 * Progresso da extração de thumbnails
 */
data class ThumbnailExtractionProgress(
    val currentIndex: Int,
    val total: Int,
    val currentPositionMs: Long,
    val isComplete: Boolean = false
)
