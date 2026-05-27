package com.chopcut

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


// --- Merged from ChopCutException.kt ---


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

// --- Merged from ErrorHandler.kt ---


/**
 * Centralized error handler for ChopCut.
 * Converts exceptions into user-friendly error states with recovery strategies.
 */
object ErrorHandler {

    /**
     * Error state that can be consumed by the UI
     */
    data class ErrorState(
        val title: String,
        val message: String,
        val recovery: RecoveryStrategy,
        val canRetry: Boolean = recovery.canRetry,
        val technicalDetails: String? = null
    )

    /**
     * Handle any throwable and convert to an ErrorState
     */
    fun handle(throwable: Throwable, context: Context? = null): ErrorState {
        return when (throwable) {
            is ChopCutException -> handleChopCutException(throwable)
            is SecurityException -> handleSecurityException(throwable)
            is IOException -> handleIOException(throwable, context)
            is IllegalArgumentException -> handleIllegalArgumentException(throwable)
            is IllegalStateException -> handleIllegalStateException(throwable)
            is OutOfMemoryError -> handleOutOfMemory()
            else -> handleUnknown(throwable)
        }
    }

    /**
     * Get a user-friendly message for a throwable
     */
    fun getUserMessage(throwable: Throwable, context: Context? = null): String {
        return handle(throwable, context).message
    }

    /**
     * Check if an error is retryable
     */
    fun isRetryable(throwable: Throwable): Boolean {
        return when (val error = handle(throwable)) {
            is ChopCutException -> error.recoveryStrategy.canRetry
            else -> error.canRetry
        }
    }

    /**
     * Handle ChopCut exceptions
     */
    fun handleChopCutException(exception: ChopCutException): ErrorState {
        return ErrorState(
            title = getTitleForException(exception),
            message = exception.userMessage,
            recovery = exception.recoveryStrategy,
            canRetry = exception.recoveryStrategy.canRetry,
            technicalDetails = exception.message
        )
    }

    /**
     * Handle SecurityException (permissions)
     */
    private fun handleSecurityException(exception: SecurityException): ErrorState {
        val message = exception.message?.lowercase() ?: ""

        return when {
            message.contains("read") || message.contains("access") -> {
                ErrorState(
                    title = "Permissão negada",
                    message = "O app precisa de permissão para acessar os arquivos",
                    recovery = RecoveryStrategy.GrantReadPermission,
                    technicalDetails = exception.message
                )
            }
            message.contains("write") -> {
                ErrorState(
                    title = "Permissão negada",
                    message = "O app precisa de permissão para salvar arquivos",
                    recovery = RecoveryStrategy.GrantWritePermission,
                    technicalDetails = exception.message
                )
            }
            else -> {
                ErrorState(
                    title = "Erro de permissão",
                    message = "Permissão necessária não foi concedida",
                    recovery = RecoveryStrategy.GrantReadPermission,
                    technicalDetails = exception.message
                )
            }
        }
    }

    /**
     * Handle IOException
     */
    private fun handleIOException(exception: IOException, context: Context?): ErrorState {
        val message = exception.message?.lowercase() ?: ""

        return when {
            message.contains("enoent") || message.contains("no such file") -> {
                ErrorState(
                    title = "Arquivo não encontrado",
                    message = "O arquivo de vídeo não existe mais",
                    recovery = RecoveryStrategy.SelectAnotherVideo,
                    technicalDetails = exception.message
                )
            }
            message.contains("eacces") || message.contains("permission denied") -> {
                ErrorState(
                    title = "Acesso negado",
                    message = "Sem permissão para acessar o arquivo",
                    recovery = RecoveryStrategy.SelectAnotherVideo,
                    technicalDetails = exception.message
                )
            }
            message.contains("enospc") -> {
                ErrorState(
                    title = "Sem espaço",
                    message = "Não há espaço suficiente no dispositivo",
                    recovery = RecoveryStrategy.FreeUpSpace,
                    technicalDetails = exception.message
                )
            }
            isStorageLow(context) -> {
                ErrorState(
                    title = "Espaço baixo",
                    message = "O espaço de armazenamento está acabando",
                    recovery = RecoveryStrategy.FreeUpSpace,
                    technicalDetails = exception.message
                )
            }
            else -> {
                ErrorState(
                    title = "Erro de leitura",
                    message = "Erro ao acessar o arquivo de vídeo",
                    recovery = RecoveryStrategy.Retry,
                    technicalDetails = exception.message
                )
            }
        }
    }

    /**
     * Handle IllegalArgumentException
     */
    private fun handleIllegalArgumentException(exception: IllegalArgumentException): ErrorState {
        val message = exception.message?.lowercase() ?: ""

        return when {
            message.contains("uri") -> {
                ErrorState(
                    title = "Arquivo inválido",
                    message = "O arquivo selecionado não é válido",
                    recovery = RecoveryStrategy.SelectAnotherVideo,
                    technicalDetails = exception.message
                )
            }
            message.contains("range") || message.contains("time") -> {
                ErrorState(
                    title = "Intervalo inválido",
                    message = "O intervalo de tempo selecionado é inválido",
                    recovery = RecoveryStrategy.AdjustTimeRange,
                    technicalDetails = exception.message
                )
            }
            message.contains("codec") -> {
                ErrorState(
                    title = "Codificador inválido",
                    message = "O codificador selecionado não é suportado",
                    recovery = RecoveryStrategy.ChangeCodec,
                    technicalDetails = exception.message
                )
            }
            else -> {
                ErrorState(
                    title = "Parâmetro inválido",
                    message = "Um parâmetro inválido foi fornecido",
                    recovery = RecoveryStrategy.Retry,
                    technicalDetails = exception.message
                )
            }
        }
    }

    /**
     * Handle IllegalStateException
     */
    private fun handleIllegalStateException(exception: IllegalStateException): ErrorState {
        val message = exception.message?.lowercase() ?: ""

        return when {
            message.contains("codec") || message.contains("encoder") || message.contains("decoder") -> {
                ErrorState(
                    title = "Erro de codificação",
                    message = "Ocorreu um erro ao processar o vídeo",
                    recovery = RecoveryStrategy.RetryWithLowerQuality,
                    technicalDetails = exception.message
                )
            }
            message.contains("not initialized") -> {
                ErrorState(
                    title = "Erro interno",
                    message = "O componente não foi inicializado corretamente",
                    recovery = RecoveryStrategy.Retry,
                    technicalDetails = exception.message
                )
            }
            else -> {
                ErrorState(
                    title = "Erro interno",
                    message = "Ocorreu um erro inesperado",
                    recovery = RecoveryStrategy.Retry,
                    technicalDetails = exception.message
                )
            }
        }
    }

    /**
     * Handle OutOfMemoryError
     */
    private fun handleOutOfMemory(): ErrorState {
        return ErrorState(
            title = "Memória insuficiente",
            message = "O dispositivo está sem memória suficiente",
            recovery = RecoveryStrategy.RetryWithLowerQuality,
            technicalDetails = "OutOfMemoryError"
        )
    }

    /**
     * Handle unknown exceptions
     */
    private fun handleUnknown(exception: Throwable): ErrorState {
        return ErrorState(
            title = "Erro desconhecido",
            message = "Ocorreu um erro inesperado: ${exception.javaClass.simpleName}",
            recovery = RecoveryStrategy.Retry,
            technicalDetails = "${exception.javaClass.name}: ${exception.message}"
        )
    }

    /**
     * Get a user-friendly title for each exception type
     */
    private fun getTitleForException(exception: ChopCutException): String {
        return when (exception) {
            is ChopCutException.Video -> "Erro de vídeo"
            is ChopCutException.Codec -> "Erro de codificação"
            is ChopCutException.Export -> "Erro de exportação"
            is ChopCutException.Audio -> "Erro de áudio"
            is ChopCutException.Graphics -> "Erro de gráficos"
            is ChopCutException.Permission -> "Erro de permissão"
            is ChopCutException.Network -> "Erro de rede"
            is ChopCutException.Unknown -> "Erro"
        }
    }

    /**
     * Check if storage is low
     */
    private fun isStorageLow(context: Context?): Boolean {
        if (context == null) return false

        return try {
            val state = Environment.getExternalStorageState()
            val dir = Environment.getExternalStorageDirectory()
            if (state == Environment.MEDIA_MOUNTED && dir != null) {
                val stat = android.os.StatFs(dir.path)
                val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                val oneHundredMB = 100L * 1024L * 1024L
                availableBytes < oneHundredMB
            } else false
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Extension functions for common error handling
 */

/**
 * Catch and convert exceptions to ChopCutException
 */
inline fun <T> catchChopCutException(
    action: () -> T,
    onFailure: (ChopCutException) -> T
): T {
    return try {
        action()
    } catch (e: ChopCutException) {
        onFailure(e)
    } catch (e: SecurityException) {
        onFailure(ChopCutException.Permission.WriteDenied())
    } catch (e: IOException) {
        onFailure(ChopCutException.Export.StorageError(e))
    } catch (e: Exception) {
        onFailure(ChopCutException.Unknown(cause = e))
    }
}

/**
 * Catch URI-related exceptions
 */
fun handleUriError(uri: Uri?, e: Exception): ChopCutException {
    return when (e) {
        is SecurityException -> ChopCutException.Permission.ReadDenied()
        is IOException -> ChopCutException.Video.FileNotFound(uri)
        is IllegalArgumentException -> ChopCutException.Video.InvalidUri(uri?.toString())
        else -> ChopCutException.Unknown(cause = e)
    }
}

/**
 * Validate video file and throw appropriate exceptions
 */
fun validateVideoFile(context: Context, uri: Uri) {
    // Check file type
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType?.startsWith("video/") != true) {
        throw ChopCutException.Video.UnsupportedFormat(mimeType)
    }

    // Check if URI is valid
    try {
        context.contentResolver.openInputStream(uri)?.close()
            ?: throw ChopCutException.Video.FileNotFound(uri)
    } catch (e: Exception) {
        throw ChopCutException.Video.FileNotFound(uri)
    }
}

/**
 * Get safe MIME type from URI
 */
fun getSafeMimeType(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.getType(uri)
    } catch (e: Exception) {
        // Fallback to extension-based mapping
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

// --- Merged from ErrorResult.kt ---


/**
 * Wrapper for operation results that can succeed or fail with a specific error type.
 * Similar to Kotlin's Result but with structured error information.
 */
sealed class ErrorResult<out T> {
    data class Success<T>(val data: T) : ErrorResult<T>()
    data class Error(val errorState: ErrorHandler.ErrorState) : ErrorResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getErrorOrNull(): ErrorHandler.ErrorState? = when (this) {
        is Success -> null
        is Error -> errorState
    }

    fun <R> map(transform: (T) -> R): ErrorResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    fun <R> flatMap(transform: (T) -> ErrorResult<R>): ErrorResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    fun onError(action: (ErrorHandler.ErrorState) -> Unit): ErrorResult<T> {
        if (this is Error) {
            action(errorState)
        }
        return this
    }

    fun onSuccess(action: (T) -> Unit): ErrorResult<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    companion object {
        fun <T> success(data: T): ErrorResult<T> = Success(data)
        fun error(errorState: ErrorHandler.ErrorState): ErrorResult<Nothing> = Error(errorState)

        fun <T> fromThrowable(throwable: Throwable): ErrorResult<T> {
            return Error(ErrorHandler.handle(throwable))
        }

        fun <T> fromThrowable(throwable: Throwable, context: android.content.Context): ErrorResult<T> {
            return Error(ErrorHandler.handle(throwable, context))
        }
    }
}

/**
 * Extended operation result with loading state
 */
sealed class OperationResult<out T> {
    data class Loading(val message: String? = null) : OperationResult<Nothing>()
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Error(val errorState: ErrorHandler.ErrorState) : OperationResult<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Extension to convert Result<T> to ErrorResult<T>
 */
fun <T> Result<T>.toErrorResult(context: android.content.Context? = null): ErrorResult<T> {
    return this.fold(
        onSuccess = { ErrorResult.success(it) },
        onFailure = { if (context != null) ErrorResult.fromThrowable(it, context) else ErrorResult.fromThrowable(it) }
    )
}

/**
 * Extension to convert ErrorResult<T> to Result<T>
 */
fun <T> ErrorResult<T>.toResult(): Result<T> {
    return when (this) {
        is ErrorResult.Success -> Result.success(data)
        is ErrorResult.Error -> Result.failure(
            RuntimeException(errorState.title + ": " + errorState.message)
        )
    }
}

/**
 * Safe execute helper that catches all exceptions
 */
inline fun <T> safeExecute(
    context: android.content.Context? = null,
    block: () -> T
): ErrorResult<T> = try {
    ErrorResult.success(block())
} catch (e: Exception) {
    if (context != null) {
        ErrorResult.fromThrowable(e, context)
    } else {
        ErrorResult.fromThrowable(e)
    }
}

/**
 * Safe execute for suspend functions
 */
suspend inline fun <T> safeExecuteSuspend(
    context: android.content.Context? = null,
    crossinline block: suspend () -> T
): ErrorResult<T> = try {
    ErrorResult.success(block())
} catch (e: Exception) {
    if (context != null) {
        ErrorResult.fromThrowable(e, context)
    } else {
        ErrorResult.fromThrowable(e)
    }
}

// --- Merged from ViewModelErrorExtensions.kt ---


/**
 * Error state container for ViewModels
 */
data class UiError(
    val title: String,
    val message: String,
    val recovery: RecoveryStrategy,
    val canRetry: Boolean = recovery.canRetry,
    val technicalDetails: String? = null
) {
    companion object {
        fun from(errorState: ErrorHandler.ErrorState): UiError {
            return UiError(
                title = errorState.title,
                message = errorState.message,
                recovery = errorState.recovery,
                canRetry = errorState.canRetry,
                technicalDetails = errorState.technicalDetails
            )
        }
    }
}

/**
 * Extension to launch operations with automatic error handling in ViewModels
 */
fun ViewModel.launchWithErrorHandling(
    errorFlow: MutableStateFlow<UiError?>,
    context: Context? = null,
    onError: ((ErrorHandler.ErrorState) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit
) {
    (this as androidx.lifecycle.ViewModel).viewModelScope.launch {
        try {
            block()
        } catch (e: ChopCutException) {
            val errorState = ErrorHandler.handleChopCutException(e)
            errorFlow.value = UiError.from(errorState)
            onError?.invoke(errorState)
        } catch (e: Exception) {
            val errorState = ErrorHandler.handle(e, context)
            errorFlow.value = UiError.from(errorState)
            onError?.invoke(errorState)
        }
    }
}

/**
 * Extension to handle operation results in ViewModels
 */
fun <T> ViewModel.handleOperationResult(
    result: ErrorResult<T>,
    errorFlow: MutableStateFlow<UiError?>,
    onSuccess: (T) -> Unit,
    onError: ((ErrorHandler.ErrorState) -> Unit)? = null
) {
    when (result) {
        is ErrorResult.Success -> onSuccess(result.data)
        is ErrorResult.Error -> {
            errorFlow.value = UiError.from(result.errorState)
            onError?.invoke(result.errorState)
        }
    }
}

/**
 * Extension to clear error state
 */
fun clearError(errorFlow: MutableStateFlow<UiError?>) {
    errorFlow.value = null
}

/**
 * Error-aware ViewModel base class
 */
abstract class ErrorAwareViewModel(
    protected val context: Context? = null
) : ViewModel() {

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * Launch operation with loading state and error handling
     */
    protected fun launchWithErrorHandling(
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                block()
            } catch (e: ChopCutException) {
                _error.value = UiError.from(ErrorHandler.handleChopCutException(e))
            } catch (e: Exception) {
                _error.value = UiError.from(ErrorHandler.handle(e, context))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Launch operation that returns ErrorResult
     */
    protected suspend fun <T> executeOperation(
        operation: suspend () -> T
    ): ErrorResult<T> = try {
        val result = operation()
        ErrorResult.success(result)
    } catch (e: ChopCutException) {
        ErrorResult.Error(ErrorHandler.handleChopCutException(e))
    } catch (e: Exception) {
        ErrorResult.Error(ErrorHandler.handle(e, context))
    }

    /**
     * Clear the current error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Show an error manually
     */
    fun showError(errorState: ErrorHandler.ErrorState) {
        _error.value = UiError.from(errorState)
    }

    /**
     * Show an error from a throwable
     */
    fun showError(throwable: Throwable) {
        _error.value = UiError.from(ErrorHandler.handle(throwable, context))
    }
}

/**
 * Extension to convert UiError to a user-readable display format
 */
fun UiError.toDisplayString(): String {
    return "$title\n$message\n\n${recovery.action}"
}

/**
 * Extension to check if error is recoverable
 */
fun UiError.isRecoverable(): Boolean {
    return recovery != RecoveryStrategy.None && recovery != RecoveryStrategy.ContactSupport
}

/**
 * Extension to get detailed error info (for debugging/developer mode)
 */
fun UiError.toDetailedString(): String {
    return buildString {
        appendLine("Title: $title")
        appendLine("Message: $message")
        appendLine("Recovery: ${recovery.action}")
        appendLine("Can Retry: $canRetry")
        technicalDetails?.let { appendLine("Details: $it") }
    }
}
