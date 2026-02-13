package com.chopcut.util.error

import android.net.Uri

/**
 * Base exception for all ChopCut errors.
 * Provides user-friendly messages and recovery suggestions.
 */
sealed class ChopCutException(
    message: String,
    cause: Throwable? = null,
    val userMessage: String,
    val recoveryStrategy: RecoveryStrategy
) : Exception(message, cause) {

    /**
     * Video-related errors (file not found, corrupt, unsupported format, etc.)
     */
    sealed class Video(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        companion object {
            private fun formatUri(uri: Uri?): String {
                return uri?.lastPathSegment ?: "unknown"
            }
        }

        class FileNotFound(uri: Uri?) : Video(
            message = "Video file not found: ${formatUri(uri)}",
            userMessage = "Vídeo não encontrado",
            recovery = RecoveryStrategy.SelectAnotherVideo
        )

        class InvalidUri(uri: String?) : Video(
            message = "Invalid video URI: $uri",
            userMessage = "URI de vídeo inválida",
            recovery = RecoveryStrategy.SelectAnotherVideo
        )

        class UnsupportedFormat(mimeType: String?) : Video(
            message = "Unsupported video format: $mimeType",
            userMessage = "Formato de vídeo não suportado",
            recovery = RecoveryStrategy.TryDifferentVideo
        )

        class CorruptFile(cause: Throwable? = null) : Video(
            message = "Video file is corrupt or unreadable",
            cause = cause,
            userMessage = "Arquivo de vídeo corrompido",
            recovery = RecoveryStrategy.TryDifferentVideo
        )

        class NoVideoTrack : Video(
            message = "No video track found in file",
            userMessage = "Arquivo não contém vídeo",
            recovery = RecoveryStrategy.TryDifferentVideo
        )

        class MetadataError(cause: Throwable? = null) : Video(
            message = "Failed to read video metadata",
            cause = cause,
            userMessage = "Erro ao ler metadados do vídeo",
            recovery = RecoveryStrategy.Retry
        )

        class InvalidDuration(durationMs: Long) : Video(
            message = "Invalid video duration: ${durationMs}ms",
            userMessage = "Duração de vídeo inválida",
            recovery = RecoveryStrategy.TryDifferentVideo
        )
    }

    /**
     * Codec-related errors (encoder/decoder failures, unsupported codecs)
     */
    sealed class Codec(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        class NoEncoder(codecName: String) : Codec(
            message = "No encoder available for codec: $codecName",
            userMessage = "Codificador não disponível: $codecName",
            recovery = RecoveryStrategy.ChangeCodec
        )

        class NoDecoder(codecName: String) : Codec(
            message = "No decoder available for codec: $codecName",
            userMessage = "Decodificador não disponível: $codecName",
            recovery = RecoveryStrategy.TryDifferentVideo
        )

        class EncoderError(cause: Throwable?) : Codec(
            message = "Encoder operation failed",
            cause = cause,
            userMessage = "Erro na codificação do vídeo",
            recovery = RecoveryStrategy.RetryWithLowerQuality
        )

        class DecoderError(cause: Throwable?) : Codec(
            message = "Decoder operation failed",
            cause = cause,
            userMessage = "Erro na decodificação do vídeo",
            recovery = RecoveryStrategy.Retry
        )

        class ConfigurationError(detail: String) : Codec(
            message = "Codec configuration failed: $detail",
            userMessage = "Erro ao configurar codificador",
            recovery = RecoveryStrategy.Retry
        )
    }

    /**
     * Export/processing errors (transcoding, trimming, etc.)
     */
    sealed class Export(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        class TranscodeFailed(cause: Throwable?) : Export(
            message = "Video transcoding failed",
            cause = cause,
            userMessage = "Falha ao processar vídeo",
            recovery = RecoveryStrategy.RetryWithLowerQuality
        )

        class TrimFailed(cause: Throwable?) : Export(
            message = "Video trim operation failed",
            cause = cause,
            userMessage = "Falha ao cortar vídeo",
            recovery = RecoveryStrategy.Retry
        )

        class ConcatFailed(cause: Throwable?) : Export(
            message = "Video concatenation failed",
            cause = cause,
            userMessage = "Falha ao juntar vídeos",
            recovery = RecoveryStrategy.CheckVideoCompatibility
        )

        class MuxerError(cause: Throwable?) : Export(
            message = "Media muxer failed",
            cause = cause,
            userMessage = "Erro ao gravar arquivo de saída",
            recovery = RecoveryStrategy.CheckStorageSpace
        )

        class StorageError(cause: Throwable?) : Export(
            message = "Storage operation failed",
            cause = cause,
            userMessage = "Erro ao salvar arquivo",
            recovery = RecoveryStrategy.CheckStorageSpace
        )

        class InsufficientSpace(requiredBytes: Long, availableBytes: Long) : Export(
            message = "Insufficient storage: required $requiredBytes, available $availableBytes",
            userMessage = "Espaço insuficiente no dispositivo",
            recovery = RecoveryStrategy.FreeUpSpace
        )

        class InvalidTimeRange(startMs: Long, endMs: Long, maxMs: Long) : Export(
            message = "Invalid time range: $startMs-$endMs (duration: $maxMs)",
            userMessage = "Intervalo de tempo inválido",
            recovery = RecoveryStrategy.AdjustTimeRange
        )
    }

    /**
     * Audio-related errors
     */
    sealed class Audio(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        class NoAudioTrack : Audio(
            message = "No audio track found in video",
            userMessage = "Vídeo não possui áudio",
            recovery = RecoveryStrategy.ContinueWithoutAudio
        )

        class ExtractionFailed(cause: Throwable?) : Audio(
            message = "Audio extraction failed",
            cause = cause,
            userMessage = "Falha ao extrair áudio",
            recovery = RecoveryStrategy.Retry
        )

        class AnalysisFailed(cause: Throwable?) : Audio(
            message = "Audio analysis failed",
            cause = cause,
            userMessage = "Falha ao analisar áudio",
            recovery = RecoveryStrategy.Retry
        )
    }

    /**
     * OpenGL/graphics errors
     */
    sealed class Graphics(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        class EGLInitFailed(cause: Throwable?) : Graphics(
            message = "Failed to initialize EGL context",
            cause = cause,
            userMessage = "Erro ao inicializar gráficos",
            recovery = RecoveryStrategy.Retry
        )

        class ShaderCompilationError(shaderType: String, cause: Throwable?) : Graphics(
            message = "Failed to compile $shaderType shader",
            cause = cause,
            userMessage = "Erro de renderização",
            recovery = RecoveryStrategy.Retry
        )

        class SurfaceError(cause: Throwable?) : Graphics(
            message = "Surface operation failed",
            cause = cause,
            userMessage = "Erro de renderização",
            recovery = RecoveryStrategy.Retry
        )
    }

    /**
     * Permission and security errors
     */
    sealed class Permission(
        message: String,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, null, userMessage, recovery) {

        class ReadDenied : Permission(
            message = "Read storage permission denied",
            userMessage = "Permissão de leitura negada",
            recovery = RecoveryStrategy.GrantReadPermission
        )

        class WriteDenied : Permission(
            message = "Write storage permission denied",
            userMessage = "Permissão de escrita negada",
            recovery = RecoveryStrategy.GrantWritePermission
        )
    }

    /**
     * Network errors (for future features)
     */
    sealed class Network(
        message: String,
        cause: Throwable? = null,
        userMessage: String,
        recovery: RecoveryStrategy
    ) : ChopCutException(message, cause, userMessage, recovery) {

        class NoConnection : Network(
            message = "No network connection available",
            userMessage = "Sem conexão com a internet",
            recovery = RecoveryStrategy.CheckConnection
        )

        class DownloadFailed(cause: Throwable?) : Network(
            message = "Failed to download resource",
            cause = cause,
            userMessage = "Falha ao baixar arquivo",
            recovery = RecoveryStrategy.Retry
        )
    }

    /**
     * Generic/unknown errors
     */
    class Unknown(
        message: String = "An unknown error occurred",
        cause: Throwable? = null,
        userMessage: String = "Erro desconhecido",
        recovery: RecoveryStrategy = RecoveryStrategy.Retry
    ) : ChopCutException(message, cause, userMessage, recovery)
}

/**
 * Recovery strategies suggest actions the user can take to resolve errors.
 */
sealed class RecoveryStrategy(
    val action: String,
    val canRetry: Boolean = false
) {
    // Video recovery strategies
    data object SelectAnotherVideo : RecoveryStrategy("Selecione outro vídeo")
    data object TryDifferentVideo : RecoveryStrategy("Tente um vídeo diferente")
    data object ContinueWithoutAudio : RecoveryStrategy("Continuar sem áudio", canRetry = false)

    // Codec recovery strategies
    data object ChangeCodec : RecoveryStrategy("Altere o codificador nas configurações")
    data object RetryWithLowerQuality : RecoveryStrategy("Tente com qualidade menor", canRetry = true)

    // Export recovery strategies
    data object CheckStorageSpace : RecoveryStrategy("Verifique o espaço disponível")
    data object FreeUpSpace : RecoveryStrategy("Libere espaço no dispositivo")
    data object CheckVideoCompatibility : RecoveryStrategy("Verifique se os vídeos são compatíveis")
    data object AdjustTimeRange : RecoveryStrategy("Ajuste o intervalo de tempo")

    // Permission recovery strategies
    data object GrantReadPermission : RecoveryStrategy("Conceda permissão de leitura")
    data object GrantWritePermission : RecoveryStrategy("Conceda permissão de escrita")

    // Network recovery strategies
    data object CheckConnection : RecoveryStrategy("Verifique sua conexão")

    // Generic strategies
    data object Retry : RecoveryStrategy("Tente novamente", canRetry = true)
    data object ContactSupport : RecoveryStrategy("Entre em contato com o suporte")

    // None - no recovery possible
    data object None : RecoveryStrategy("", canRetry = false)
}
