package com.chopcut.ui.components.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chopcut.ui.theme.ChopCutTypography
import com.chopcut.ui.theme.PressedAlpha
import com.chopcut.ui.theme.primaryColor

/**
 * Botão primário do Design System ChopCut
 *
 * Usado para ações principais: Exportar, Salvar, Avançar
 *
 * @param text Texto do botão
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param enabled Se habilitado
 * @param icon Ícone opcional (ficará à esquerda do texto)
 */
@Composable
fun ChopCutPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Feedback visual ao pressionar (opacidade)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                // Aqui poderia adicionar feedback háptico
                // hapticConfirm(LocalContext.current)
            }
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor(),
            contentColor = com.chopcut.ui.theme.OnPrimary,
            disabledContainerColor = primaryColor().copy(alpha = PressedAlpha),
            disabledContentColor = com.chopcut.ui.theme.OnPrimary.copy(alpha = PressedAlpha)
        ),
        contentPadding = PaddingValues(
            horizontal = 24.dp,
            vertical = 12.dp
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = ChopCutTypography.labelLarge
        )
    }
}

/**
 * Botão secundário do Design System ChopCut
 *
 * Usado para ações secundárias: Cancelar, Voltar
 *
 * @param text Texto do botão
 * @param onClick Ação ao clicar
 * @param modifier Modificador
 * @param enabled Se habilitado
 */
@Composable
fun ChopCutSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = com.chopcut.ui.theme.OnSurface
        ),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 10.dp
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = ChopCutTypography.labelMedium
        )
    }
}
