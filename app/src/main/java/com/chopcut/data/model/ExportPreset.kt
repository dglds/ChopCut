package com.chopcut.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuration for a video export preset.
 */
@Entity(tableName = "export_presets")
data class ExportPreset(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Int = 30,
    val isCustom: Boolean = false
) {
    /**
     * Indica se este preset usa as configurações originais do vídeo.
     */
    val isOriginal: Boolean
        get() = id == ORIGINAL.id

    fun toExportConfig(): ExportConfig {
        return ExportConfig(
            width = width,
            height = height,
            bitrate = bitrate,
            frameRate = frameRate
        )
    }

    /**
     * Cria uma ExportConfig usando as dimensões originais do vídeo quando este preset for ORIGINAL.
     */
    fun toExportConfig(originalWidth: Int, originalHeight: Int, originalBitrate: Int): ExportConfig {
        return if (isOriginal) {
            ExportConfig(
                width = originalWidth,
                height = originalHeight,
                bitrate = originalBitrate,
                frameRate = frameRate
            )
        } else {
            toExportConfig()
        }
    }

    companion object {
        // Preset para manter configurações originais do vídeo
        val ORIGINAL = ExportPreset(
            id = "native_original",
            name = "Original",
            description = "Configurações originais do vídeo",
            width = 0,  // Será substituído pelas dimensões originais
            height = 0, // Será substituído pelas dimensões originais
            bitrate = 0 // Será substituído pelo bitrate original
        )

        // Native Presets
        val INSTAGRAM_REELS = ExportPreset(
            id = "native_insta_reels",
            name = "Instagram Reels",
            description = "1080x1920, Alta Qualidade",
            width = 1080,
            height = 1920,
            bitrate = 8_000_000
        )

        val TIKTOK = ExportPreset(
            id = "native_tiktok",
            name = "TikTok",
            description = "1080x1920, Otimizado",
            width = 1080,
            height = 1920,
            bitrate = 6_000_000
        )

        val WHATSAPP = ExportPreset(
            id = "native_whatsapp",
            name = "WhatsApp",
            description = "720p, Arquivo Pequeno",
            width = 720,
            height = 1280,
            bitrate = 2_000_000
        )

        val YOUTUBE_1080P = ExportPreset(
            id = "native_yt_1080p",
            name = "YouTube 1080p",
            description = "Full HD, 16:9",
            width = 1920,
            height = 1080,
            bitrate = 10_000_000
        )

        val DEFAULT_PRESETS = listOf(
            ORIGINAL,
            INSTAGRAM_REELS,
            TIKTOK,
            WHATSAPP,
            YOUTUBE_1080P
        )
    }
}
