package com.chopcut

import android.graphics.RectF
import android.net.Uri
import android.os.Parcelable
import android.util.Size
import androidx.compose.ui.graphics.Color
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.parcelize.Parcelize


// --- Merged from FilterType.kt ---

enum class FilterType {
    NONE,
    GRAYSCALE,
    SEPIA,
    BRIGHTNESS,
    CONTRAST,
    SATURATION
}

// --- Merged from PerformanceTelemetry.kt ---

/**
 * Estágios do pipeline de extração de thumbnails
 */
enum class ExtractionStage {
    DECODE,    // Extração do frame bruto do vídeo
    PROCESS,   // Crop, resize, filtros
    SAVE       // Compressão e escrita em disco/cache
}

/**
 * Evento de telemetria para uma única tarefa em um estágio
 */
data class PerformanceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val stage: ExtractionStage,
    val taskId: String,         // Identificador (ex: positionMs ou nome do arquivo)
    val durationMs: Long,
    val queueSize: Int = 0,
    val workerId: String = Thread.currentThread().name
)

/**
 * Métricas consolidadas de performance
 */
data class PerformanceMetrics(
    val throughput: Float,      // Tarefas por segundo (frames/s)
    val avgDurationMs: Map<ExtractionStage, Float>,
    val maxDurationMs: Map<ExtractionStage, Long>,
    val bottleneckStage: ExtractionStage? = null
)

// --- Merged from Size.kt ---


/**
 * Video size wrapper with helper functions
 */
data class VideoSize(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    val isPortrait: Boolean get() = height > width
    val isLandscape: Boolean get() = width > height
    val isSquare: Boolean get() = width == height

    fun toAndroidSize(): Size = Size(width, height)

    /**
     * Scale to fit within bounds while preserving aspect ratio
     */
    fun scaleToFit(maxWidth: Int, maxHeight: Int): VideoSize {
        val widthRatio = maxWidth.toFloat() / width
        val heightRatio = maxHeight.toFloat() / height
        val scale = minOf(widthRatio, heightRatio)

        return VideoSize(
            width = (width * scale).toInt(),
            height = (height * scale).toInt()
        )
    }

    /**
     * Scale to fill bounds while preserving aspect ratio (may crop)
     */
    fun scaleToFill(minWidth: Int, minHeight: Int): VideoSize {
        val widthRatio = minWidth.toFloat() / width
        val heightRatio = minHeight.toFloat() / height
        val scale = maxOf(widthRatio, heightRatio)

        return VideoSize(
            width = (width * scale).toInt(),
            height = (height * scale).toInt()
        )
    }

    /**
     * Rotate dimensions
     */
    fun rotate(): VideoSize = VideoSize(width = height, height = width)

    companion object {
        fun from(size: Size): VideoSize {
            return VideoSize(size.width, size.height)
        }
    }
}

// --- Merged from ThumbnailSettings.kt ---

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
    val thumbsPerSecond: Int = 1,
    val quality: Int = 85,
    val format: ThumbnailFormat = ThumbnailFormat.JPEG,
    val sizePreset: SizePreset = SizePreset.SMALL,
    val extractionQuality: ThumbnailQuality = ThumbnailQuality.HIGH,
    val scaleMode: ThumbnailScaleMode = ThumbnailScaleMode.FIT
) {
    /** Backward compat para ThumbnailEngine.kt */
    @Deprecated("Use sizePreset")
    val dimensionPreset: DimensionPreset get() = sizePreset
    @Deprecated("No longer used")
    val aspectRatioPreset: AspectRatioPreset get() = AspectRatioPreset.ORIGINAL
    @Deprecated("No longer used")
    val cropZoom: Float get() = 1f
    @Deprecated("No longer used")
    val scaleFactor: Float get() = 1f

    fun computeDimensions(videoAr: Float): Pair<Int, Int> {
        val baseH = sizePreset.baseHeight.coerceAtLeast(16)
        val w = (baseH * 16f / 9f).roundToInt().coerceAtLeast(16)
        return when {
            videoAr in 0.9f..1.1f -> {
                val sq = sqrt((w * baseH).toFloat()).roundToInt()
                sq to sq
            }
            videoAr < 1f -> baseH to w
            else -> w to baseH
        }
    }

    fun computeExtractDimensions(videoAr: Float): Pair<Int, Int> {
        val (tw, th) = computeDimensions(videoAr)
        if (tw == th) return tw to th
        val landscape = videoAr >= 1f
        return when (scaleMode) {
            ThumbnailScaleMode.FIT -> tw to th
            ThumbnailScaleMode.FILL -> {
                if (!landscape) {
                    tw to th
                } else {
                    val boxAr = 16f / 9f
                    if (videoAr > boxAr) {
                        (th * videoAr).toInt() to th
                    } else {
                        tw to (tw / videoAr).toInt()
                    }
                }
            }
        }
    }
}

/**
 * Formatos de imagem suportados para extração de thumbnails
 */
enum class ThumbnailFormat(val displayName: String, val description: String) {
    JPEG("JPEG", "Melhor compressão"),
    PNG("PNG", "Sem perdas, arquivos maiores"),
    WEBP("WebP", "Moderno, bom balanceamento")
}

/**
 * Modo de escala para mapear o frame do vídeo nas dimensões alvo do thumbnail
 */
enum class ThumbnailScaleMode(
    val displayName: String,
    val description: String
) {
    FIT("Scale to Fit", "Mantém AR, encaixa dentro (letterbox)"),
    FILL("Scale to Fill", "Mantém AR, preenche cortando (center-crop)")
}

/**
 * Presets de aspect ratio para thumbnails
 */
enum class AspectRatioPreset(
    val displayName: String,
    val ratio: Float?
) {
    ORIGINAL("Original", null),
    R16_9("16:9", 16f / 9f),
    R1_1("1:1", 1f),
    R4_3("4:3", 4f / 3f),
    R9_16("9:16", 9f / 16f),
    R21_9("21:9", 21f / 9f)
}

/**
 * Presets de tamanho para thumbnails, baseados em altura.
 * A largura é sempre derivada: width = baseHeight × aspectRatioPreset.ratio
 */
enum class SizePreset(
    val displayName: String,
    val baseHeight: Int
) {
    THUMBNAIL("Thumbnail", 36),
    SMALL("Small", 45),
    MEDIUM("Medium", 60),
    LARGE("Large", 90),
    HD("HD", 120);

    /** Backward compat: largura assumindo 16:9 */
    val width get() = (baseHeight * 16f / 9f).toInt()
    val height get() = baseHeight

    companion object {
        /** Sugere um preset adequado com base na altura do vídeo */
        fun suggest(videoHeight: Int): SizePreset = when {
            videoHeight >= 2160 -> HD
            videoHeight >= 1440 -> LARGE
            videoHeight >= 1080 -> MEDIUM
            videoHeight >= 720  -> SMALL
            else                -> THUMBNAIL
        }
    }
}

/** Backward compat para ThumbnailEngine.kt */
typealias DimensionPreset = SizePreset

/**
 * Progresso da extração de thumbnails
 */
data class ThumbnailExtractionProgress(
    val currentIndex: Int,
    val total: Int,
    val currentPositionMs: Long,
    val isComplete: Boolean = false
)

// --- Merged from TimeRange.kt ---


@Parcelize
data class TimeRange(
    val startMs: Long,
    val endMs: Long
) : Parcelable {
    init {
        require(startMs >= 0) { "startMs must be >= 0" }
        require(endMs > startMs) { "endMs must be > startMs" }
    }

    val durationMs: Long get() = endMs - startMs

    fun contains(timeMs: Long): Boolean {
        return timeMs in startMs..endMs
    }

    fun overlaps(other: TimeRange): Boolean {
        return startMs < other.endMs && endMs > other.startMs
    }

    companion object {
        fun fromUs(startUs: Long, endUs: Long): TimeRange {
            return TimeRange(
                startMs = startUs / 1000,
                endMs = endUs / 1000
            )
        }
    }
}

// --- Merged from Transform.kt ---


/**
 * Transform configuration for video processing
 */
data class Transform(
    val rotation: Float = 0f,        // Rotation in degrees
    val scaleX: Float = 1f,          // Horizontal scale
    val scaleY: Float = 1f,          // Vertical scale
    val cropRect: RectF? = null,     // Crop region (normalized 0-1)
    val translationX: Float = 0f,    // X translation (normalized)
    val translationY: Float = 0f,    // Y translation (normalized)
    val volume: Float = 1.0f,        // Audio volume multiplier
    val filter: FilterType = FilterType.NONE, // Video filter
    val filterIntensity: Float = 1.0f, // Filter intensity
    val fadeInMs: Long = 0L,         // Audio Fade In duration in ms
    val fadeOutMs: Long = 0L         // Audio Fade Out duration in ms
) {
    companion object {
        val IDENTITY = Transform()
    }

    fun hasCrop(): Boolean = cropRect != null

    fun hasRotation(): Boolean = rotation != 0f

    fun hasScale(): Boolean = scaleX != 1f || scaleY != 1f

    fun hasTransform(): Boolean = hasRotation() || hasScale() || hasCrop()
}

// --- Merged from VideoCodec.kt ---

enum class VideoCodec(
    val mimeType: String,
    val displayName: String
) {
    H264("video/avc", "H.264 (AVC)"),
    H265("video/hevc", "H.265 (HEVC)"),
    VP8("video/x-vnd.on2.vp8", "VP8"),
    VP9("video/x-vnd.on2.vp9", "VP9"),
    AV1("video/av01", "AV1"),
    MPEG4("video/mp4v-es", "MPEG-4");

    companion object {
        fun fromMimeType(mimeType: String?): VideoCodec? {
            return entries.find { it.mimeType == mimeType }
        }
    }
}

// --- Merged from VideoInfo.kt ---


data class VideoInfo(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val durationUs: Long, // Duration in microseconds
    val width: Int,
    val height: Int,
    val rotation: Int,
    val bitrate: Long,
    val frameRate: Int,
    val videoCodec: String?,
    val audioCodec: String?,
    val hasAudio: Boolean,
    val sizeBytes: Long
) {
    val durationMs: Long get() = durationUs / 1000
    val widthF: Float get() = width.toFloat()
    val heightF: Float get() = height.toFloat()

    val aspectRatio: Float
        get() {
            if (width == 0 || height == 0) return 16f / 9f // Fallback
            val isPortrait = rotation == 90 || rotation == 270
            val displayWidth = if (isPortrait) height else width
            val displayHeight = if (isPortrait) width else height
            return displayWidth.toFloat() / displayHeight.toFloat()
        }
}

// --- Merged from VideoRange.kt ---


/**
 * Representa um intervalo de tempo selecionável em um vídeo.
 *
 * @property id Identificador único do range
 * @property startMs Posição inicial do range em milissegundos
 * @property endMs Posição final do range em milissegundos
 * @property color Cor do range para visualização
 * @property isSelected Indica se o range está selecionado
 */
data class VideoRange(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val color: Color = Color(0xFF2196F3),
    val isSelected: Boolean = false
) {
    /**
     * Duração do range em milissegundos.
     */
    val durationMs: Long get() = endMs - startMs

    /**
     * Verifica se este range sobrepõe outro range.
     *
     * @param other O outro range para verificar sobreposição
     * @return true se os ranges se sobrepõem
     */
    fun overlapsWith(other: VideoRange): Boolean {
        return startMs < other.endMs && endMs > other.startMs
    }

    /**
     * Verifica se este range sobrepõe um intervalo específico.
     *
     * @param startMs Início do intervalo
     * @param endMs Fim do intervalo
     * @return true se há sobreposição
     */
    fun overlapsWith(startMs: Long, endMs: Long): Boolean {
        return this.startMs < endMs && this.endMs > startMs
    }

    /**
     * Verifica se uma posição está dentro deste range.
     *
     * @param positionMs Posição em milissegundos
     * @return true se a posição está dentro do range
     */
    fun contains(positionMs: Long): Boolean {
        return positionMs in startMs..endMs
    }

    /**
     * Retorna uma cópia deste range com nova posição.
     *
     * @param newStartMs Nova posição inicial
     * @param newEndMs Nova posição final
     * @return Nova instância de VideoRange com as posições atualizadas
     */
    fun withPosition(newStartMs: Long, newEndMs: Long): VideoRange {
        return copy(startMs = newStartMs, endMs = newEndMs)
    }

    /**
     * Retorna uma cópia deste range com o estado de seleção alterado.
     *
     * @param selected Novo estado de seleção
     * @return Nova instância de VideoRange com seleção atualizada
     */
    fun withSelection(selected: Boolean): VideoRange {
        return copy(isSelected = selected)
    }
}

// --- Merged from AudioFormat.kt ---

/**
 * Supported audio formats for extraction
 */
enum class AudioFormat(
    val extension: String,
    val mimeType: String,
    val containerFormat: Int
) {
    AAC(
        extension = ".m4a",
        mimeType = "audio/mp4",
        containerFormat = 0  // MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )
    // MP3(".mp3", "audio/mpeg", 1)  // Future - requires re-encoding
}

// --- Merged from AudioInfo.kt ---

/**
 * Audio track metadata extracted from video
 */
data class AudioInfo(
    val codec: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Long,
    val durationUs: Long,
    val mimeType: String,
    val language: String? = null
) {
    val durationMs: Long
        get() = durationUs / 1000

    val bitrateKbps: Long
        get() = bitrate / 1000

    val isStereo: Boolean
        get() = channelCount == 2
}

// --- Merged from WaveformData.kt ---

/**
 * Modelo único de dados de waveform
 * 
 * Contém os dados finais prontos para renderização,
 * já com downsampling e threshold aplicados.
 */
data class WaveformData(
    val amplitudes: FloatArray,  // Dados finais, prontos para renderizar
    val durationMs: Long
) {
    val barCount: Int get() = amplitudes.size
    val isEmpty: Boolean get() = amplitudes.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformData
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (durationMs != other.durationMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = amplitudes.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }

    companion object {
        fun empty() = WaveformData(floatArrayOf(), 0)
    }
}
