package com.chopcut.data.audio.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * An [AudioProcessor] that applies a volume factor to PCM audio.
 */
class VolumeAudioProcessor(private val volume: Float) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // We only support 16-bit PCM for simplicity and common compatibility.
        // Float PCM would require different handling.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        val size = inputBuffer.remaining()
        val buffer = replaceOutputBuffer(size)

        // Process 16-bit samples
        // Each sample is 2 bytes (short)
        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            
            // Apply volume
            var processedSample = (sample * volume).toInt()
            
            // Clip to 16-bit range
            if (processedSample > Short.MAX_VALUE) {
                processedSample = Short.MAX_VALUE.toInt()
            } else if (processedSample < Short.MIN_VALUE) {
                processedSample = Short.MIN_VALUE.toInt()
            }
            
            buffer.putShort(processedSample.toShort())
        }

        buffer.flip()
    }
}
