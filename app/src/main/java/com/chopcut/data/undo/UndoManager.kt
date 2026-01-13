package com.chopcut.data.undo

import com.chopcut.data.model.EditOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Stack

/**
 * Manages the history of edit operations for Undo/Redo functionality.
 */
class UndoManager {

    // The list of currently applied operations (the "present")
    private val _currentEdits = MutableStateFlow<List<EditOperation>>(emptyList())
    val currentEdits: StateFlow<List<EditOperation>> = _currentEdits.asStateFlow()

    // Stack for Redo operations (the "future")
    private val redoStack = Stack<EditOperation>()

    // State to notify UI about availability
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /**
     * Load initial state (e.g., from saved project)
     */
    fun loadInitialState(edits: List<EditOperation>) {
        _currentEdits.value = edits
        redoStack.clear()
        updateAvailability()
    }

    /**
     * Add a new operation. This clears the Redo stack.
     */
    fun addOperation(operation: EditOperation) {
        val currentList = _currentEdits.value.toMutableList()
        currentList.add(operation)
        _currentEdits.value = currentList
        
        redoStack.clear()
        updateAvailability()
    }

    /**
     * Undo the last operation.
     */
    fun undo() {
        val currentList = _currentEdits.value.toMutableList()
        if (currentList.isNotEmpty()) {
            val opToUndo = currentList.removeAt(currentList.lastIndex)
            redoStack.push(opToUndo)
            _currentEdits.value = currentList
            updateAvailability()
        }
    }

    /**
     * Redo the last undone operation.
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val opToRedo = redoStack.pop()
            val currentList = _currentEdits.value.toMutableList()
            currentList.add(opToRedo)
            _currentEdits.value = currentList
            updateAvailability()
        }
    }

    private fun updateAvailability() {
        _canUndo.value = _currentEdits.value.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
