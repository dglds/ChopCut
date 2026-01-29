package com.chopcut.core.designsystem.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chopcut.core.designsystem.atoms.BodyText
import com.chopcut.core.designsystem.atoms.HeadlineText
import com.chopcut.core.designsystem.atoms.PrimaryButton
import com.chopcut.core.designsystem.organisms.AppTopBar
import com.chopcut.core.designsystem.organisms.FullScreenError
import com.chopcut.core.designsystem.organisms.FullScreenLoading
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Template padrão para telas do app.
 * Fornece estrutura comum com TopBar, padding e gerenciamento de estados.
 *
 * @param title Título da tela
 * @param modifier Modifier para customização
 * @param onBackClick Ação de voltar (null = não mostra botão)
 * @param actions Ações na TopBar
 * @param bottomBar Barra inferior (BottomNavigation, etc)
 * @param floatingActionButton FAB da tela
 * @param contentPadding Padding do conteúdo
 * @param content Conteúdo da tela
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTemplate(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SpacingTokens.lg),
    content: @Composable (padding: PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                actions = actions
            )
        },
        bottomBar = { bottomBar?.invoke() },
        floatingActionButton = { floatingActionButton?.invoke() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(innerPadding)
        }
    }
}

/**
 * Template para telas com conteúdo em LazyColumn.
 *
 * @param title Título da tela
 * @param modifier Modifier para customização
 * @param onBackClick Ação de voltar
 * @param actions Ações na TopBar
 * @param bottomBar Barra inferior
 * @param floatingActionButton FAB
 * @param contentPadding Padding do conteúdo
 * @param verticalArrangement Arranjo vertical dos itens
 * @param content Conteúdo da LazyColumn
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyScreenTemplate(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(SpacingTokens.lg),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SpacingTokens.lg),
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                actions = actions
            )
        },
        bottomBar = { bottomBar?.invoke() },
        floatingActionButton = { floatingActionButton?.invoke() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                end = contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

/**
 * Template para estados de loading.
 *
 * @param title Título da tela
 * @param loadingMessage Mensagem durante o loading
 * @param modifier Modifier para customização
 * @param onBackClick Ação de voltar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreenTemplate(
    title: String,
    loadingMessage: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    ScreenTemplate(
        title = title,
        modifier = modifier,
        onBackClick = onBackClick
    ) {
        FullScreenLoading(subtitle = loadingMessage)
    }
}

/**
 * Template para estados de erro.
 *
 * @param title Título da tela
 * @param errorMessage Mensagem de erro
 * @param onRetry Ação de tentar novamente
 * @param modifier Modifier para customização
 * @param onBackClick Ação de voltar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreenTemplate(
    title: String,
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    ScreenTemplate(
        title = title,
        modifier = modifier,
        onBackClick = onBackClick
    ) {
        FullScreenError(
            message = errorMessage,
            onRetry = onRetry
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ScreenTemplatePreview() {
    ChopCutTheme {
        ScreenTemplate(
            title = "Tela de Exemplo",
            onBackClick = {}
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(SpacingTokens.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeadlineText(text = "Conteúdo da Tela")
                Spacer(modifier = Modifier.height(SpacingTokens.lg))
                BodyText(text = "Este é um exemplo de conteúdo usando o ScreenTemplate.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun LazyScreenTemplatePreview() {
    ChopCutTheme {
        LazyScreenTemplate(
            title = "Lista de Exemplo",
            onBackClick = {}
        ) {
            items(5) { index ->
                BodyText(text = "Item ${index + 1}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun LoadingScreenTemplatePreview() {
    ChopCutTheme {
        LoadingScreenTemplate(
            title = "Carregando",
            loadingMessage = "Buscando dados..."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ErrorScreenTemplatePreview() {
    ChopCutTheme {
        ErrorScreenTemplate(
            title = "Erro",
            errorMessage = "Não foi possível carregar os dados.",
            onRetry = {}
        )
    }
}
