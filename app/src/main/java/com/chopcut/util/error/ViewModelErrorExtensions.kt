package com.chopcut.util.error

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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
            Timber.e(e, "ChopCutException in ViewModel")
            val errorState = ErrorHandler.handleChopCutException(e)
            errorFlow.value = UiError.from(errorState)
            onError?.invoke(errorState)
        } catch (e: Exception) {
            Timber.e(e, "Exception in ViewModel")
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
                Timber.e(e, "ChopCutException in ViewModel")
                _error.value = UiError.from(ErrorHandler.handleChopCutException(e))
            } catch (e: Exception) {
                Timber.e(e, "Exception in ViewModel")
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
        Timber.e(e, "ChopCutException in operation")
        ErrorResult.Error(ErrorHandler.handleChopCutException(e))
    } catch (e: Exception) {
        Timber.e(e, "Exception in operation")
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
