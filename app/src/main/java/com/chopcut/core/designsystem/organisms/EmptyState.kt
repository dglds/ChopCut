package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.GhostButton
import com.chopcut.core.designsystem.atoms.HeadlineText
import com.chopcut.core.designsystem.atoms.SmallText
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Estado vazio para telas sem conteúdo.
 * Use quando não há dados para exibir.
 *
 * @param title Título do estado vazio
 * @param modifier Modifier para customização
 * @param description Descrição opcional
 * @param icon Ícone representativo
 * @param actionLabel Label do botão de ação (null = sem botão)
 * @param onAction Ação do botão
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(SizeTokens.iconHero),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(SpacingTokens.xl))

        HeadlineText(
            text = title,
            textAlign = TextAlign.Center
        )

        if (description != null) {
            Spacer(modifier = Modifier.height(SpacingTokens.md))
            BodyText(
                text = description,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(SpacingTokens.xxl))
            GhostButton(
                onClick = onAction,
                text = actionLabel
            )
        }
    }
}

/**
 * Estado vazio específico para projetos.
 */
@Composable
fun NoProjectsState(
    modifier: Modifier = Modifier,
    onCreateProject: (() -> Unit)? = null
) {
    EmptyState(
        title = "Nenhum projeto",
        description = "Você ainda não criou nenhum projeto. Comece selecionando um vídeo.",
        icon = Icons.Default.FolderOpen,
        actionLabel = "Criar projeto",
        onAction = onCreateProject,
        modifier = modifier
    )
}

/**
 * Estado vazio para resultados de busca.
 */
@Composable
fun NoResultsState(
    query: String,
    modifier: Modifier = Modifier,
    onClearSearch: (() -> Unit)? = null
) {
    EmptyState(
        title = "Nenhum resultado",
        description = "Não encontramos nada para \"$query\". Tente outros termos.",
        icon = Icons.Default.Search,
        actionLabel = if (onClearSearch != null) "Limpar busca" else null,
        onAction = onClearSearch,
        modifier = modifier
    )
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    ChopCutTheme {
        EmptyState(
            title = "Nada aqui",
            description = "Este é um estado vazio genérico.",
            actionLabel = "Criar algo",
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NoProjectsStatePreview() {
    ChopCutTheme {
        NoProjectsState(onCreateProject = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun NoResultsStatePreview() {
    ChopCutTheme {
        NoResultsState(
            query = "viagem praia",
            onClearSearch = {}
        )
    }
}
