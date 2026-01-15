package com.chopcut.data.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.FilterType
import timber.log.Timber

/**
 * Factory para criar efeitos do Media3 a partir de EditOperations.
 *
 * Os efeitos são aplicados na ordem das operações. Operações do mesmo tipo
 * são combinadas quando possível (rotação total, escala acumulada).
 */
@UnstableApi
object EffectFactory {

    /**
     * Cria uma lista de efeitos do Media3 a partir das operações de edição.
     *
     * @param operations Lista de operações de edição
     * @return Lista de efeitos para aplicar no vídeo
     */
    fun createEffects(operations: List<EditOperation>): List<Effect> {
        var totalRotation = 0f
        var scaleX = 1f
        var scaleY = 1f

        val effects = mutableListOf<Effect>()

        operations.forEach { op ->
            when (op) {
                is EditOperation.Rotation -> {
                    totalRotation += op.degrees
                }
                is EditOperation.Resize -> {
                    // Resize aplica escala (0.5 = metade do tamanho)
                    scaleX *= 0.5f
                    scaleY *= 0.5f
                }
                is EditOperation.Filter -> {
                    createFilterEffect(op.filterType, op.intensity)?.let { effects.add(it) }
                }
                else -> {
                    // Speed e Volume são tratados separadamente no pipeline
                }
            }
        }

        // Adiciona transformação de rotação/escala se necessário
        if (totalRotation % 360 != 0f || scaleX != 1f || scaleY != 1f) {
            val transform = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(totalRotation)
                .setScale(scaleX, scaleY)
                .build()
            effects.add(transform)
        }

        Timber.d("EffectFactory: ${effects.size} efeitos criados (rot=${totalRotation}°, scale=${scaleX}x${scaleY})")
        return effects
    }

    /**
     * Cria um efeito de filtro baseado no tipo e intensidade.
     *
     * @param filterType Tipo do filtro
     * @param intensity Intensidade do filtro (0.0 a 1.0+, depende do filtro)
     * @return O efeito correspondente ou null se não aplicável
     */
    private fun createFilterEffect(filterType: FilterType, intensity: Float): Effect? {
        return when (filterType) {
            FilterType.NONE -> null
            FilterType.BRIGHTNESS -> {
                // Intensidade: -1.0 (escuro) a 1.0 (claro), 0.0 = normal
                // Mapeamos de 0-1 para -0.5 a 0.5 para não ser extremo
                Brightness(intensity * 0.5f)
            }
            FilterType.CONTRAST -> {
                // Intensidade: 0.0 (cinza) a 2.0+ (alto contraste), 1.0 = normal
                // Mapeamos de 0-1 para 0.5 a 1.5
                val contrastValue = 0.5f + intensity
                Contrast(contrastValue)
            }
            FilterType.GRAYSCALE -> {
                // Grayscale: igualar os canais RGB
                // Intensidade controla a força do efeito (0 = original, 1 = grayscale completo)
                RgbAdjustment.Builder()
                    .setRedScale(1f - intensity * 0.5f)
                    .setGreenScale(1f - intensity * 0.2f)
                    .setBlueScale(1f - intensity * 0.5f)
                    .build()
            }
            FilterType.SEPIA -> {
                // Matriz sépia clássica: R=1.2, G=1.0, B=0.8
                // Intensidade controla a força
                val baseRed = 1.2f * intensity
                val baseGreen = 1.0f * intensity
                val baseBlue = 0.8f * intensity
                RgbAdjustment.Builder()
                    .setRedScale(1f - intensity + baseRed)
                    .setGreenScale(1f - intensity + baseGreen)
                    .setBlueScale(1f - intensity + baseBlue)
                    .build()
            }
            FilterType.SATURATION -> {
                // Saturação usando RgbAdjustment
                // Intensidade: 0.0 (cinza/PB) a 1.0 (saturado), 0.5 = normal
                // Mapeamos de 0-1 para saturação
                // < 0.5: dessaturar, > 0.5: saturar
                val saturation = intensity * 2f // 0.0 a 2.0
                if (saturation < 1f) {
                    // Dessaturar: reduzir diferença entre RGB
                    val factor = saturation // 0.0 a 1.0
                    val gray = 1f - factor
                    RgbAdjustment.Builder()
                        .setRedScale(factor + gray * 0.299f)
                        .setGreenScale(factor + gray * 0.587f)
                        .setBlueScale(factor + gray * 0.114f)
                        .build()
                } else {
                    // Saturar: aumentar escala de cores
                    val factor = saturation // 1.0 a 2.0
                    RgbAdjustment.Builder()
                        .setRedScale(factor)
                        .setGreenScale(factor)
                        .setBlueScale(factor)
                        .build()
                }
            }
        }
    }

    /**
     * Retorna a rotação total acumulada das operações.
     */
    fun getTotalRotation(operations: List<EditOperation>): Float {
        return operations.filterIsInstance<EditOperation.Rotation>()
            .sumOf { it.degrees }
            .toFloat()
    }

    /**
     * Retorna o valor de velocidade mais recente das operações.
     */
    fun getSpeed(operations: List<EditOperation>): Float {
        return operations.filterIsInstance<EditOperation.Speed>()
            .lastOrNull()?.speed ?: 1.0f
    }

    /**
     * Retorna o valor de volume mais recente das operações.
     */
    fun getVolume(operations: List<EditOperation>): Float {
        return operations.filterIsInstance<EditOperation.Volume>()
            .lastOrNull()?.volume ?: 1.0f
    }
}