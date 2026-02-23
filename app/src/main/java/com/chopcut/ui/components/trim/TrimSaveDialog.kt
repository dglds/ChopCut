package com.chopcut.ui.components.trim

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SaveDialogState(
    val isSaving: Boolean = false,
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val error: String? = null
) {
    val canDismiss: Boolean
        get() = !isSaving

    val title: String
        get() = when {
            isCompleted -> "Vídeo Salvo!"
            error != null -> "Erro ao Salvar"
            isSaving -> "Exportando..."
            else -> "Remover Trechos"
        }
}

@Composable
fun TrimSaveDialog(
    state: SaveDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state.canDismiss) {
                onDismiss()
            }
        },
        title = {
            Text(
                state.title,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    state.isCompleted -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Vídeo salvo com sucesso!")
                    }
                    state.error != null -> {
                        Text(state.error)
                    }
                    state.isSaving -> {
                        if (state.progress > 0) {
                            CircularProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${state.progress}%")
                    }
                    else -> {
                        Text("Deseja remover os trechos selecionados e salvar o vídeo?")
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.isCompleted -> {
                    TextButton(onClick = {
                        onDismiss()
                        onNavigateBack()
                    }) {
                        Text("Ir para Início")
                    }
                }
                state.error != null -> {
                    TextButton(onClick = {
                        onDismiss()
                    }) {
                        Text("Fechar")
                    }
                }
                state.isSaving -> {
                }
                else -> {
                    TextButton(onClick = onSave) {
                        Text("Salvar")
                    }
                }
            }
        },
        dismissButton = {
            if (!state.isSaving && !state.isCompleted && state.error == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
