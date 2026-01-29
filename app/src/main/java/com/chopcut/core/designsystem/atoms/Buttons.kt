package com.chopcut.core.designsystem.atoms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Botão primário do app.
 * Use para ações principais e calls-to-action.
 *
 * @param onClick Ação ao clicar
 * @param text Texto do botão
 * @param modifier Modifier para customização
 * @param icon Ícone opcional (será exibido à esquerda do texto)
 * @param enabled Se o botão está habilitado
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = if (icon != null) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SizeTokens.iconMd)
            )
            Spacer(Modifier.width(SpacingTokens.md))
        }
        Text(text = text)
    }
}

/**
 * Botão secundário do app.
 * Use para ações secundárias ou alternativas.
 *
 * @param onClick Ação ao clicar
 * @param text Texto do botão
 * @param modifier Modifier para customização
 * @param icon Ícone opcional (será exibido à esquerda do texto)
 * @param enabled Se o botão está habilitado
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = if (icon != null) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SizeTokens.iconMd)
            )
            Spacer(Modifier.width(SpacingTokens.md))
        }
        Text(text = text)
    }
}

/**
 * Botão outlined do app.
 * Use para ações terciárias ou menos importantes.
 *
 * @param onClick Ação ao clicar
 * @param text Texto do botão
 * @param modifier Modifier para customização
 * @param icon Ícone opcional (será exibido à esquerda do texto)
 * @param enabled Se o botão está habilitado
 */
@Composable
fun OutlinedButtonAtom(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = if (icon != null) {
            ButtonDefaults.ButtonWithIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SizeTokens.iconMd)
            )
            Spacer(Modifier.width(SpacingTokens.md))
        }
        Text(text = text)
    }
}

/**
 * Botão texto (ghost) do app.
 * Use para ações de baixa ênfase ou links.
 *
 * @param onClick Ação ao clicar
 * @param text Texto do botão
 * @param modifier Modifier para customização
 * @param icon Ícone opcional (será exibido à esquerda do texto)
 * @param enabled Se o botão está habilitado
 */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SizeTokens.iconMd)
            )
            Spacer(Modifier.width(SpacingTokens.md))
        }
        Text(text = text)
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    ChopCutTheme {
        PrimaryButton(
            onClick = {},
            text = "Primary Button"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SecondaryButtonPreview() {
    ChopCutTheme {
        SecondaryButton(
            onClick = {},
            text = "Secondary Button"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OutlinedButtonPreview() {
    ChopCutTheme {
        OutlinedButtonAtom(
            onClick = {},
            text = "Outlined Button"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GhostButtonPreview() {
    ChopCutTheme {
        GhostButton(
            onClick = {},
            text = "Ghost Button"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonsDisabledPreview() {
    ChopCutTheme {
        PrimaryButton(
            onClick = {},
            text = "Disabled",
            enabled = false
        )
    }
}
