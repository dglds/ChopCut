package com.chopcut.ui.filter

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chopcut.data.model.EditOperation

/**
 * Painel de controle de crop de vídeo
 *
 * @param initialCrop Retângulo de crop inicial (null = sem crop)
 * @param videoWidth Largura original do vídeo
 * @param videoHeight Altura original do vídeo
 * @param onConfirm Callback quando o crop é confirmado (null remove o crop)
 * @param onDismiss Callback para fechar o diálogo
 */
@Composable
fun CropContent(
    initialCrop: RectF?,
    videoWidth: Int,
    videoHeight: Int,
    onConfirm: (EditOperation.Crop?) -> Unit,
    onDismiss: () -> Unit
) {
    // Estado do crop (normalizado 0-1)
    var cropRect by remember {
        mutableStateOf(
            initialCrop ?: RectF(0f, 0f, 1f, 1f)
        )
    }

    val aspectRatio = if (videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f
    }

    // Aspect ratios predefinidos
    val aspectRatios = listOf(
        "Original" to aspectRatio,
        "1:1" to 1f,
        "16:9" to (16f / 9f),
        "9:16" to (9f / 16f),
        "4:3" to (4f / 3f),
        "3:4" to (3f / 4f)
    )

    var selectedRatio by remember { mutableStateOf("Original") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cortar Vídeo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botão Reset
                        IconButton(
                            onClick = {
                                cropRect = RectF(0f, 0f, 1f, 1f)
                                selectedRatio = "Original"
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        // Botão Confirmar
                        IconButton(
                            onClick = {
                                // Verificar se o crop é diferente de 100%
                                val isFullCrop = cropRect.left == 0f &&
                                        cropRect.top == 0f &&
                                        cropRect.right == 1f &&
                                        cropRect.bottom == 1f

                                if (isFullCrop) {
                                    // Remove o crop (null)
                                    onConfirm(null)
                                } else {
                                    // Converte de coordenadas normalizadas para pixels
                                    val left = (cropRect.left * videoWidth).toInt()
                                    val top = (cropRect.top * videoHeight).toInt()
                                    val right = (cropRect.right * videoWidth).toInt()
                                    val bottom = (cropRect.bottom * videoHeight).toInt()

                                    onConfirm(EditOperation.Crop(left, top, right, bottom))
                                }
                                onDismiss()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirmar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Preview do crop (visualização simplificada)
                CropPreview(
                    cropRect = cropRect,
                    aspectRatio = aspectRatio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Spacer(Modifier.height(16.dp))

                // Info do Crop
                CropInfoDisplay(
                    cropRect = cropRect,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight
                )

                Spacer(Modifier.height(16.dp))

                // Aspect Ratio Presets
                Text(
                    text = "Proporção",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aspectRatios.forEach { (label, ratio) ->
                        FilterChip(
                            selected = selectedRatio == label,
                            onClick = {
                                selectedRatio = label
                                cropRect = calculateCropForAspectRatio(ratio, aspectRatio)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Botões de ajuste fino
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Expande crop em 5%
                            val expandAmount = 0.05f
                            cropRect = RectF(
                                (cropRect.left - expandAmount).coerceAtLeast(0f),
                                (cropRect.top - expandAmount).coerceAtLeast(0f),
                                (cropRect.right + expandAmount).coerceAtMost(1f),
                                (cropRect.bottom + expandAmount).coerceAtMost(1f)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Expandir", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            // Recolhe crop em 5%
                            val shrinkAmount = 0.05f
                            cropRect = RectF(
                                (cropRect.left + shrinkAmount).coerceAtMost(0.45f),
                                (cropRect.top + shrinkAmount).coerceAtMost(0.45f),
                                (cropRect.right - shrinkAmount).coerceAtLeast(0.55f),
                                (cropRect.bottom - shrinkAmount).coerceAtLeast(0.55f)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Recolher", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Preview visual simplificado do crop
 */
@Composable
private fun CropPreview(
    cropRect: RectF,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        // Background (vídeo)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Área do crop (mais clara)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = ((cropRect.left - 0.5f) * 100).dp,
                        y = ((cropRect.top - 0.5f) * 100).dp
                    )
                    .fillMaxWidth(cropRect.right - cropRect.left)
                    .fillMaxHeight(cropRect.bottom - cropRect.top)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

/**
 * Display das informações do crop
 */
@Composable
private fun CropInfoDisplay(
    cropRect: RectF,
    videoWidth: Int,
    videoHeight: Int
) {
    val pixelWidth = ((cropRect.right - cropRect.left) * videoWidth).toInt()
    val pixelHeight = ((cropRect.bottom - cropRect.top) * videoHeight).toInt()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${pixelWidth} × ${pixelHeight}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tamanho do crop",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Calcula o retângulo de crop para um aspect ratio específico
 */
private fun calculateCropForAspectRatio(
    targetRatio: Float,
    videoRatio: Float
): RectF {
    return if (targetRatio == videoRatio) {
        // Mantém original
        RectF(0f, 0f, 1f, 1f)
    } else if (targetRatio > videoRatio) {
        // Crop mais largo que o vídeo (corta altura)
        val newHeight = 1f / targetRatio * videoRatio
        val topMargin = (1f - newHeight) / 2
        RectF(0f, topMargin, 1f, topMargin + newHeight)
    } else {
        // Crop mais estreito que o vídeo (corta largura)
        val newWidth = targetRatio / videoRatio
        val leftMargin = (1f - newWidth) / 2
        RectF(leftMargin, 0f, leftMargin + newWidth, 1f)
    }
}
