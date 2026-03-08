package com.chopcut.util.error

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

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
