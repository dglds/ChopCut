package com.chopcut.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons.Outlined
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chopcut.ui.theme.ChopCutTypography
import com.chopcut.ui.theme.OnBackground
import com.chopcut.ui.theme.OnSurface
import com.chopcut.ui.theme.Surface
import com.chopcut.ui.theme.primaryColor

/**
 * Estado de carregamento circular
 *
 * Usado durante operações assíncronas
 *
 * @param modifier Modificador
 * @param color Cor do indicador (padrão: primary)
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = primaryColor()
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = color,
            modifier = Modifier.size(48.dp)
        )
    }
}

/**
 * Estado vazio (sem dados)
 *
 * Usado quando uma lista está vazia ou não há projetos
 *
 * @param icon Ícone representativo
 * @param title Título principal
 * @param message Mensagem descritiva
 * @param actionLabel Texto da ação (opcional)
 * @param onAction Ação ao clicar no botão (opcional)
 * @param modifier Modificador
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = ChopCutTypography.titleMedium,
                color = OnSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                style = ChopCutTypography.bodyMedium,
                color = OnSurface.copy(alpha = 0.7f)
            )

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(24.dp))

                com.chopcut.ui.components.buttons.ChopCutPrimaryButton(
                    text = actionLabel,
                    onClick = onAction
                )
            }
        }
    }
}

/**
 * Estado de erro
 *
 * Usado para exibir mensagens de erro com opção de retry
 *
 * @param icon Ícone de erro (padrão: error)
 * @param title Título do erro
 * @param message Mensagem descritiva
 * @param actionLabel Texto da ação (padrão: "Tentar novamente")
 * @param onAction Ação ao clicar no botão
 * @param modifier Modificador
 */
@Composable
fun ErrorState(
    icon: ImageVector = Outlined.ErrorOutline,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "Tentar novamente",
    onAction: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = com.chopcut.ui.theme.ErrorDark,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = ChopCutTypography.titleMedium,
                color = OnSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = message,
                style = ChopCutTypography.bodyMedium,
                color = OnSurface.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(24.dp))

            com.chopcut.ui.components.buttons.ChopCutPrimaryButton(
                text = actionLabel,
                onClick = onAction
            )
        }
    }
}

/**
 * Inline loading (pequeno, dentro de conteúdo)
 *
 * @param modifier Modificador
 */
@Composable
fun InlineLoading(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        modifier = modifier.size(24.dp),
        strokeWidth = 2.dp,
        color = primaryColor()
    )
}
