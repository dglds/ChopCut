package com.chopcut.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chopcut.util.error.RecoveryStrategy
import com.chopcut.util.error.UiError

/**
 * Simple error message component for inline display
 */
@Composable
fun InlineErrorMessage(
    error: UiError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = error.title,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = error.message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Full error dialog with recovery actions
 */
@Composable
fun ErrorDialog(
    error: UiError?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (error == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        icon = {
            Icon(
                imageVector = getIconForRecovery(error.recovery),
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = error.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(text = error.message)
                if (error.canRetry) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sugestão: ${error.recovery.action}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (error.canRetry && onRetry != null) {
                TextButton(onClick = {
                    onRetry()
                    onDismiss()
                }) {
                    Text("Tentar Novamente")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = if (error.canRetry && onRetry != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        } else null
    )
}

/**
 * Detailed error dialog with technical details (for debugging)
 */
@Composable
fun DetailedErrorDialog(
    error: UiError?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    showTechnicalDetails: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (error == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = androidx.compose.material3.MaterialTheme.shapes.large,
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = error.title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message
                Text(
                    text = error.message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Recovery suggestion
                Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                    shape = androidx.compose.material3.MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error.recovery.action,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Technical details (if enabled)
                if (showTechnicalDetails && error.technicalDetails != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Detalhes técnicos:",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = error.technicalDetails,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    if (error.canRetry && onRetry != null) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar")
                        }
                        TextButton(onClick = {
                            onRetry()
                            onDismiss()
                        }) {
                            Text("Tentar Novamente")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

private fun getIconForRecovery(recovery: RecoveryStrategy): ImageVector {
    return when (recovery) {
        is RecoveryStrategy.SelectAnotherVideo,
        is RecoveryStrategy.TryDifferentVideo,
        is RecoveryStrategy.ChangeCodec -> Icons.Default.Info
        else -> Icons.Default.Warning
    }
}
