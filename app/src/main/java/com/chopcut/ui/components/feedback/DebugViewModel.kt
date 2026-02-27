package com.chopcut.ui.components.feedback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebugEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

sealed interface DebugState {
    data object Idle : DebugState
    data class Active(val entries: List<DebugEntry>) : DebugState
}

class DebugViewModel(application: Application) : AndroidViewModel(application) {
    private val _debugState = MutableStateFlow<DebugState>(DebugState.Idle)
    val debugState: StateFlow<DebugState> = _debugState.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var maxEntries = 50

    fun log(message: String) {
        if (!_isEnabled.value) return

        viewModelScope.launch {
            val currentState = _debugState.value
            val currentEntries = if (currentState is DebugState.Active) {
                currentState.entries
            } else {
                emptyList()
            }

            val newEntry = DebugEntry(message = message)
            val updatedEntries = listOf(newEntry) + currentEntries.take(maxEntries - 1)

            _debugState.value = DebugState.Active(updatedEntries)
        }
    }

    fun clear() {
        _debugState.value = DebugState.Idle
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            clear()
        }
    }
}