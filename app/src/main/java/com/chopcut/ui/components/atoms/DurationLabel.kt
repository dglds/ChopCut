package com.chopcut.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.ui.theme.DurationTextStyle

/**
 * Label de duração com fonte monoespaçada
 *
 * Usado para exibir timestamps de vídeo de forma alinhada
 *
 * @param duration Duração formatada (ex: "01:23:45")
 * @param modifier Modificador
 * @param backgroundColor Cor de fundo (opcional)
 * @param textColor Cor do texto (opcional)
 */
@Composable
fun DurationLabel(
    duration: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    textColor: Color = Color.White
) {
    Text(
        text = duration,
        style = DurationTextStyle.copy(
            fontFamily = ChopCutMonoFont,
            color = textColor
        ),
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Pair de duração (atual / total)
 *
 * Ex: "01:23 / 05:00"
 *
 * @param current Duração atual
 * @param total Duração total
 * @param modifier Modificador
 */
@Composable
fun DurationPairLabel(
    current: String,
    total: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = current,
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White
            )
        )
        Text(
            text = " / ",
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
        Text(
            text = total,
            style = DurationTextStyle.copy(
                fontFamily = ChopCutMonoFont,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}

/**
 * Formata milissegundos para string de duração
 *
 * @param millis Duração em milissegundos
 * @param showHours Se deve incluir horas (true para vídeos > 1 hora)
 * @return String formatada (ex: "01:23:45" ou "01:23")
 */
fun formatDuration(millis: Long, showHours: Boolean = millis >= 3600000): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (showHours) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
