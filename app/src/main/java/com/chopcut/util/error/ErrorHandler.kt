package com.chopcut.util.error

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import timber.log.Timber
import java.io.File
import java.io.IOException

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
        Timber.e(throwable, "Error occurred: ${throwable.javaClass.simpleName}")

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
