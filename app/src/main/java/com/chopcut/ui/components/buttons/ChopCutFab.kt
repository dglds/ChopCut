package com.chopcut.ui.components.buttons

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.theme.primaryColor

/**
 * FAB (Floating Action Button) do Design System ChopCut
 *
 * Usado para ação principal na tela: Adicionar projeto, Novo vídeo
 *
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param icon Ícone a ser exibido
 * @param contentDescription Descrição para acessibilidade
 */
@Composable
fun ChopCutFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String?
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = primaryColor(),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 10.dp,
            focusedElevation = 8.dp
        ),
        shape = RectangleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Small FAB para ações secundárias flutuantes
 *
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param icon Ícone a ser exibido
 * @param contentDescription Descrição para acessibilidade
 */
@Composable
fun ChopCutSmallFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String?
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = primaryColor(),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
            hoveredElevation = 6.dp,
            focusedElevation = 6.dp
        ),
        shape = RectangleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}
