package com.chopcut.core.designsystem.organisms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.theme.ChopCutTheme

/**
 * Organismo da barra superior do app.
 * Use em todas as telas para navegação e ações.
 *
 * @param title Título exibido na barra
 * @param modifier Modifier para customização
 * @param onBackClick Ação ao clicar no botão voltar (null = não exibe)
 * @param actions Ações à direita da barra
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar"
                    )
                }
            }
        },
        actions = {
            actions?.invoke()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Ação individual para a TopBar.
 * Helper para criar ícones de ação consistentes.
 *
 * @param icon Ícone a ser exibido
 * @param contentDescription Descrição de acessibilidade
 * @param onClick Ação ao clicar
 */
@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AppTopBarSimplePreview() {
    ChopCutTheme {
        AppTopBar(title = "ChopCut")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AppTopBarWithBackPreview() {
    ChopCutTheme {
        AppTopBar(
            title = "Editor",
            onBackClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun AppTopBarWithActionsPreview() {
    ChopCutTheme {
        AppTopBar(
            title = "Configurações",
            onBackClick = {},
            actions = {
                TopBarAction(
                    icon = Icons.Default.Settings,
                    contentDescription = "Mais opções",
                    onClick = {}
                )
            }
        )
    }
}

// Necessário para o preview
@Composable
private fun Settings() = Icon(Icons.Default.Settings, contentDescription = null)
