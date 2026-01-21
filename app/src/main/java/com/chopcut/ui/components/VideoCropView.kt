package com.chopcut.ui.components

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.max
import kotlin.math.min

/**
 * Estado do crop de vídeo
 */
data class CropState(
    val cropRect: RectF = RectF(0f, 0f, 1f, 1f), // Coordenadas normalizadas (0-1)
    val videoAspectRatio: Float = 16f / 9f
) {
    companion object {
        fun fromVideoSize(width: Int, height: Int): CropState {
            return CropState(
                cropRect = RectF(0f, 0f, 1f, 1f),
                videoAspectRatio = width.toFloat() / height.toFloat()
            )
        }
    }
}

/**
 * Handle do crop (canto arrastável)
 */
enum class CropHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

/**
 * Configurações visuais do componente de crop
 */
object CropViewConfig {
    val HANDLE_SIZE = 20f // Tamanho do handle em pixels
    val HANDLE_COLOR = Color.White
    val BORDER_COLOR = Color.White
    val BORDER_WIDTH = 2f
    val DIMMED_COLOR = Color.Black.copy(alpha = 0.5f)
    val MIN_CROP_SIZE = 0.1f // Tamanho mínimo do crop (10% do vídeo)
}

/**
 * Componente visual de crop de vídeo com handles arrastáveis
 *
 * @param cropRect Retângulo de crop normalizado (0-1)
 * @param videoAspectRatio Aspect ratio do vídeo original
 * @param onCropChanged Callback quando o crop é modificado
 * @param modifier Modifier
 */
@Composable
fun VideoCropView(
    cropRect: RectF,
    videoAspectRatio: Float,
    onCropChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentCrop by remember { mutableStateOf(cropRect) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Detectar qual handle foi tocado
                        activeHandle = findHandleAtPosition(
                            offset = offset,
                            cropRect = currentCrop,
                            containerSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                        )
                    },
                    onDragEnd = {
                        activeHandle = null
                    },
                    onDragCancel = {
                        activeHandle = null
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        if (activeHandle != null) {
                            change.consume()

                            // Converter dragAmount para coordenadas normalizadas
                            val normalizedDeltaX = dragAmount.x / containerSize.width
                            val normalizedDeltaY = dragAmount.y / containerSize.height

                            currentCrop = updateCropRect(
                                currentCrop = currentCrop,
                                handle = activeHandle!!,
                                deltaX = normalizedDeltaX,
                                deltaY = normalizedDeltaY,
                                aspectRatio = videoAspectRatio
                            )

                            onCropChanged(currentCrop)
                        }
                    }
                )
            }
    ) {
        containerSize = size

        // Dimensões do vídeo mantendo aspect ratio
        val videoSize = calculateVideoSize(containerSize, videoAspectRatio)
        val videoOffset = Offset(
            x = (containerSize.width - videoSize.width) / 2,
            y = (containerSize.height - videoSize.height) / 2
        )

        // Converter cropRect normalizado para pixels
        val cropRectPixels = RectF(
            videoOffset.x + currentCrop.left * videoSize.width,
            videoOffset.y + currentCrop.top * videoSize.height,
            videoOffset.x + currentCrop.right * videoSize.width,
            videoOffset.y + currentCrop.bottom * videoSize.height
        )

        // 1. Desenhar área fora do crop (escurecida)
        drawDimmedArea(containerSize, cropRectPixels)

        // 2. Desenhar borda do crop
        drawRoundRect(
            color = CropViewConfig.BORDER_COLOR,
            topLeft = Offset(cropRectPixels.left, cropRectPixels.top),
            size = androidx.compose.ui.geometry.Size(
                width = cropRectPixels.width(),
                height = cropRectPixels.height()
            ),
            style = Stroke(width = CropViewConfig.BORDER_WIDTH)
        )

        // 3. Desenhar linhas de grade (terço)
        drawGrid(cropRectPixels)

        // 4. Desenhar handles
        drawHandles(cropRectPixels)
    }
}

/**
 * Encontra qual handle foi tocado
 */
private fun findHandleAtPosition(
    offset: Offset,
    cropRect: RectF,
    containerSize: androidx.compose.ui.geometry.Size
): CropHandle? {
    val videoSize = calculateVideoSize(containerSize, 16f / 9f)
    val videoOffset = Offset(
        x = (containerSize.width - videoSize.width) / 2,
        y = (containerSize.height - videoSize.height) / 2
    )

    val cropRectPixels = RectF(
        videoOffset.x + cropRect.left * videoSize.width,
        videoOffset.y + cropRect.top * videoSize.height,
        videoOffset.x + cropRect.right * videoSize.width,
        videoOffset.y + cropRect.bottom * videoSize.height
    )

    val handleRadius = CropViewConfig.HANDLE_SIZE * 1.5f

    return when {
        // Cantos
        isNearPoint(offset, Offset(cropRectPixels.left, cropRectPixels.top), handleRadius) -> CropHandle.TOP_LEFT
        isNearPoint(offset, Offset(cropRectPixels.right, cropRectPixels.top), handleRadius) -> CropHandle.TOP_RIGHT
        isNearPoint(offset, Offset(cropRectPixels.left, cropRectPixels.bottom), handleRadius) -> CropHandle.BOTTOM_LEFT
        isNearPoint(offset, Offset(cropRectPixels.right, cropRectPixels.bottom), handleRadius) -> CropHandle.BOTTOM_RIGHT

        // Bordas
        offset.x >= cropRectPixels.left && offset.x <= cropRectPixels.right &&
                isNearPoint(offset, Offset(offset.x, cropRectPixels.top), handleRadius) -> CropHandle.TOP

        offset.x >= cropRectPixels.left && offset.x <= cropRectPixels.right &&
                isNearPoint(offset, Offset(offset.x, cropRectPixels.bottom), handleRadius) -> CropHandle.BOTTOM

        offset.y >= cropRectPixels.top && offset.y <= cropRectPixels.bottom &&
                isNearPoint(offset, Offset(cropRectPixels.left, offset.y), handleRadius) -> CropHandle.LEFT

        offset.y >= cropRectPixels.top && offset.y <= cropRectPixels.bottom &&
                isNearPoint(offset, Offset(cropRectPixels.right, offset.y), handleRadius) -> CropHandle.RIGHT

        else -> null
    }
}

/**
 * Verifica se um ponto está próximo de outro
 */
private fun isNearPoint(point: Offset, target: Offset, threshold: Float): Boolean {
    val dx = point.x - target.x
    val dy = point.y - target.y
    return (dx * dx + dy * dy) <= (threshold * threshold)
}

/**
 * Atualiza o retângulo de crop baseado no handle sendo arrastado
 */
private fun updateCropRect(
    currentCrop: RectF,
    handle: CropHandle,
    deltaX: Float,
    deltaY: Float,
    aspectRatio: Float
): RectF {
    val newCrop = RectF(currentCrop)

    when (handle) {
        CropHandle.TOP_LEFT -> {
            newCrop.left = (currentCrop.left + deltaX).coerceAtLeast(0f)
            newCrop.top = (currentCrop.top + deltaY).coerceAtLeast(0f)
        }
        CropHandle.TOP_RIGHT -> {
            newCrop.right = (currentCrop.right + deltaX).coerceAtMost(1f)
            newCrop.top = (currentCrop.top + deltaY).coerceAtLeast(0f)
        }
        CropHandle.BOTTOM_LEFT -> {
            newCrop.left = (currentCrop.left + deltaX).coerceAtLeast(0f)
            newCrop.bottom = (currentCrop.bottom + deltaY).coerceAtMost(1f)
        }
        CropHandle.BOTTOM_RIGHT -> {
            newCrop.right = (currentCrop.right + deltaX).coerceAtMost(1f)
            newCrop.bottom = (currentCrop.bottom + deltaY).coerceAtMost(1f)
        }
        CropHandle.TOP -> {
            newCrop.top = (currentCrop.top + deltaY).coerceAtLeast(0f)
        }
        CropHandle.BOTTOM -> {
            newCrop.bottom = (currentCrop.bottom + deltaY).coerceAtMost(1f)
        }
        CropHandle.LEFT -> {
            newCrop.left = (currentCrop.left + deltaX).coerceAtLeast(0f)
        }
        CropHandle.RIGHT -> {
            newCrop.right = (currentCrop.right + deltaX).coerceAtMost(1f)
        }
    }

    // Aplicar tamanho mínimo
    val minWidth = CropViewConfig.MIN_CROP_SIZE
    val minHeight = CropViewConfig.MIN_CROP_SIZE

    if (newCrop.width() < minWidth) {
        when (handle) {
            CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT -> newCrop.left = newCrop.right - minWidth
            CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT -> newCrop.right = newCrop.left + minWidth
            else -> {}
        }
    }

    if (newCrop.height() < minHeight) {
        when (handle) {
            CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT -> newCrop.top = newCrop.bottom - minHeight
            CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT -> newCrop.bottom = newCrop.top + minHeight
            else -> {}
        }
    }

    // Garantir que left < right e top < bottom
    newCrop.sort()

    return newCrop
}

/**
 * Calcula o tamanho do vídeo mantendo aspect ratio
 */
private fun calculateVideoSize(
    containerSize: androidx.compose.ui.geometry.Size,
    aspectRatio: Float
): androidx.compose.ui.geometry.Size {
    val containerAspectRatio = containerSize.width / containerSize.height

    return if (containerAspectRatio > aspectRatio) {
        // Container é mais largo que o vídeo
        val height = containerSize.height
        val width = height * aspectRatio
        androidx.compose.ui.geometry.Size(width, height)
    } else {
        // Vídeo é mais largo que o container
        val width = containerSize.width
        val height = width / aspectRatio
        androidx.compose.ui.geometry.Size(width, height)
    }
}

/**
 * Desenha a área fora do crop escurecida
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDimmedArea(
    containerSize: androidx.compose.ui.geometry.Size,
    cropRect: RectF
) {
    val path = Path().apply {
        // Área total
        addRect(
            Rect(
                left = 0f,
                top = 0f,
                right = containerSize.width,
                bottom = containerSize.height
            )
        )

        // Subtrair área do crop (hole)
        val holePath = Path().apply {
            addRect(
                Rect(
                    left = cropRect.left,
                    top = cropRect.top,
                    right = cropRect.right,
                    bottom = cropRect.bottom
                )
            )
        }

        // Usar nativeCanvas para operação de diferença
        val nativePath = android.graphics.Path().apply {
            addRect(
                0f, 0f,
                containerSize.width, containerSize.height,
                android.graphics.Path.Direction.CW
            )
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(
                cropRect.left, cropRect.top,
                cropRect.right, cropRect.bottom,
                android.graphics.Path.Direction.CW
            )
        }

        drawContext.canvas.nativeCanvas.drawPath(
            nativePath,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                alpha = (255 * CropViewConfig.DIMMED_COLOR.alpha).toInt()
                style = android.graphics.Paint.Style.FILL
            }
        )
    }
}

/**
 * Desenha a grade de terços
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    cropRect: RectF
) {
    val gridColor = Color.White.copy(alpha = 0.3f)

    // Linha vertical 1/3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + cropRect.width() / 3, cropRect.top),
        end = Offset(cropRect.left + cropRect.width() / 3, cropRect.bottom),
        strokeWidth = 1f
    )

    // Linha vertical 2/3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left + 2 * cropRect.width() / 3, cropRect.top),
        end = Offset(cropRect.left + 2 * cropRect.width() / 3, cropRect.bottom),
        strokeWidth = 1f
    )

    // Linha horizontal 1/3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + cropRect.height() / 3),
        end = Offset(cropRect.right, cropRect.top + cropRect.height() / 3),
        strokeWidth = 1f
    )

    // Linha horizontal 2/3
    drawLine(
        color = gridColor,
        start = Offset(cropRect.left, cropRect.top + 2 * cropRect.height() / 3),
        end = Offset(cropRect.right, cropRect.top + 2 * cropRect.height() / 3),
        strokeWidth = 1f
    )
}

/**
 * Desenha os handles do crop
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandles(
    cropRect: RectF
) {
    val handleSize = CropViewConfig.HANDLE_SIZE
    val halfSize = handleSize / 2

    // Cantos
    val corners = listOf(
        Offset(cropRect.left, cropRect.top), // Top-left
        Offset(cropRect.right, cropRect.top), // Top-right
        Offset(cropRect.left, cropRect.bottom), // Bottom-left
        Offset(cropRect.right, cropRect.bottom) // Bottom-right
    )

    corners.forEach { corner ->
        drawRoundRect(
            color = CropViewConfig.HANDLE_COLOR,
            topLeft = Offset(corner.x - halfSize, corner.y - halfSize),
            size = androidx.compose.ui.geometry.Size(handleSize, handleSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
    }

    // Bordas (centro)
    val topCenter = Offset(cropRect.left + cropRect.width() / 2, cropRect.top)
    val bottomCenter = Offset(cropRect.left + cropRect.width() / 2, cropRect.bottom)
    val leftCenter = Offset(cropRect.left, cropRect.top + cropRect.height() / 2)
    val rightCenter = Offset(cropRect.right, cropRect.top + cropRect.height() / 2)

    listOf(topCenter, bottomCenter, leftCenter, rightCenter).forEach { center ->
        drawCircle(
            color = CropViewConfig.HANDLE_COLOR,
            radius = halfSize,
            center = center
        )
    }
}
