package com.chopcut.data.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
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
     * @param intensity Intensidade do filtro (varia por tipo)
     * @return O efeito correspondente ou null se não aplicável
     */
    fun createFilterEffect(filterType: FilterType, intensity: Float): Effect? {
        return when (filterType) {
            FilterType.NONE -> null
            
            FilterType.GRAYSCALE -> RgbFilter.createGrayscaleFilter()
            
            FilterType.SEPIA -> {
                // Simulação simples de Sépia (Tint)
                // R=1.2, G=1.0, B=0.8
                RgbAdjustment.Builder()
                    .setRedScale(1.2f)
                    .setGreenScale(1.0f)
                    .setBlueScale(0.8f)
                    .build()
            }
            
            FilterType.BRIGHTNESS -> {
                // Simulação de Exposição
                // intensity: -1.0 a 1.0
                // scale: 0.5x a 2.0x
                val scale = if (intensity >= 0) {
                    1.0f + intensity // 1.0 a 2.0
                } else {
                    1.0f / (1.0f - intensity) // 1.0 a 0.5
                }
                val finalScale = scale.coerceIn(0.1f, 3.0f)
                
                RgbAdjustment.Builder()
                    .setRedScale(finalScale)
                    .setGreenScale(finalScale)
                    .setBlueScale(finalScale)
                    .build()
            }
            
            // Contrast e Saturation requerem matrizes complexas ou APIs específicas
            // que podem não estar disponíveis ou estáveis.
            // Implementação futura.
            FilterType.CONTRAST -> null 
            FilterType.SATURATION -> null
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
