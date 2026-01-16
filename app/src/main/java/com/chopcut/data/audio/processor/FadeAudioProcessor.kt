package com.chopcut.data.audio.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that applies fade in and fade out effects.
 */
class FadeAudioProcessor(
    private val fadeInDurationMs: Long,
    private val fadeOutDurationMs: Long,
    private val totalDurationMs: Long // Duração total do áudio APÓS o trim
) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channelCount = 2
    private var bytesPerFrame = 4 // 2 channels * 16-bit (2 bytes)
    private var totalBytesProcessed = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        bytesPerFrame = channelCount * 2 // 16-bit = 2 bytes per sample
        return inputAudioFormat
    }

    override fun onFlush() {
        totalBytesProcessed = 0L
        super.onFlush()
    }

    override fun onReset() {
        totalBytesProcessed = 0L
        super.onReset()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        val size = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(size)

        // Calcular limites em bytes
        val fadeInEndBytes = (fadeInDurationMs * sampleRate * bytesPerFrame) / 1000
        val fadeOutStartBytes = ((totalDurationMs - fadeOutDurationMs) * sampleRate * bytesPerFrame) / 1000
        val totalBytes = (totalDurationMs * sampleRate * bytesPerFrame) / 1000

        while (inputBuffer.hasRemaining()) {
            // Processar por frame (conjunto de canais)
            for (i in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                
                val sample = inputBuffer.short
                var processedSample = sample.toFloat()

                // Calcular posição atual em bytes (aproximada para este sample)
                // Note: totalBytesProcessed is updated after the full buffer or block, 
                // but for precision we should add offset. 
                // To keep it simple inside the loop without massive overhead:
                // We use the current position relative to this buffer batch + totalBytesProcessed.
                // Mas, como processamos short por short, o tracking exato aqui é complexo.
                // Vamos simplificar: Aplicar o ganho baseado na posição do frame atual.
                
                // O tracking deve ser por FRAME, não por sample individual de canal.
                // Então só incrementamos o contador de tempo quando todos os canais do frame forem processados.
                // Mas aqui estamos num loop de canais.
                
                val currentFrameBytes = totalBytesProcessed
                
                // Lógica de Fade
                var gain = 1.0f

                if (currentFrameBytes < fadeInEndBytes) {
                    // Fade In
                    gain = currentFrameBytes.toFloat() / fadeInEndBytes.toFloat()
                } else if (currentFrameBytes > fadeOutStartBytes) {
                    // Fade Out
                    val remaining = totalBytes - currentFrameBytes
                    val fadeDurationBytes = totalBytes - fadeOutStartBytes
                    if (fadeDurationBytes > 0) {
                        gain = remaining.toFloat() / fadeDurationBytes.toFloat()
                    } else {
                        gain = 0f
                    }
                }
                
                gain = gain.coerceIn(0f, 1f)
                
                processedSample *= gain

                // Clip
                if (processedSample > Short.MAX_VALUE) processedSample = Short.MAX_VALUE.toFloat()
                if (processedSample < Short.MIN_VALUE) processedSample = Short.MIN_VALUE.toFloat()

                buffer.putShort(processedSample.toInt().toShort())
            }
            
            // Increment frame count after processing all channels for one sample time
            // Wait, standard interleaved PCM: L, R, L, R...
            // The loop above iterates channelCount times per "frame".
            // So we increment bytes by bytesPerFrame after the inner loop.
            // EXCEPT: inputBuffer.short reads sequentially.
            // My loop structure above assumes I can read channelCount shorts safely.
            // Need to be careful about buffer boundaries.
        }
        
        // CORRECTION: The loop structure above is risky if buffer ends mid-frame.
        // Better approach: Iterate by bytes or shorts and track channel index.
        
        inputBuffer.position(inputBuffer.position() - size) // Rewind to re-read correctly with new logic
        
        var bytesInThisBatch = 0
        
        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            
            // Qual frame estamos?
            val currentBytePos = totalBytesProcessed + bytesInThisBatch
            // Frame index = floor(bytePos / bytesPerFrame)
            // Mas só precisamos saber onde estamos no tempo.
            
            // Simplificação: Calcular ganho a cada Short. 
            // Como L e R estão muito próximos, o ganho será quase idêntico.
            
            var gain = 1.0f
            
            if (fadeInDurationMs > 0 && currentBytePos < fadeInEndBytes) {
                gain = currentBytePos.toFloat() / fadeInEndBytes.toFloat()
            } else if (fadeOutDurationMs > 0 && currentBytePos > fadeOutStartBytes) {
                val offsetFromStartFade = currentBytePos - fadeOutStartBytes
                val fadeLen = totalBytes - fadeOutStartBytes
                if (fadeLen > 0) {
                    gain = 1.0f - (offsetFromStartFade.toFloat() / fadeLen.toFloat())
                } else {
                    gain = 0f
                }
            }
            
            gain = gain.coerceIn(0f, 1f)
            
            var processed = (sample * gain).toInt()
            if (processed > Short.MAX_VALUE) processed = Short.MAX_VALUE.toInt()
            if (processed < Short.MIN_VALUE) processed = Short.MIN_VALUE.toInt()
            
            buffer.putShort(processed.toShort())
            bytesInThisBatch += 2
        }

        totalBytesProcessed += size
        buffer.flip()
    }
}
