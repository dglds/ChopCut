package com.chopcut.ui.components.trim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TrimButton(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isDraftMode) {
        // Botão de Confirmar (Check) - Laranja
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .background(Color(0xFFFF9800), androidx.compose.foundation.shape.CircleShape),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Confirmar")
        }
    } else if (!isInsideRange) {
        // Botão de Cortar (Tesoura) - Primary Color
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = "Iniciar Range")
        }
    } else {
        // Placeholder invisível para manter layout
        Spacer(modifier = modifier.size(64.dp))
    }
}

@Composable
fun DeleteButton(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Limpar")
        }
    } else {
        // Placeholder invisível
        Spacer(modifier = modifier.size(64.dp))
    }
}
