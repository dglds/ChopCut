package com.chopcut.data.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Cache em disco para AudioRawData extraído.
 *
 * Chave: SHA-256(uri + size). Vídeos com mesmo URI mas tamanho diferente
 * (re-encode, edição) invalidam automaticamente.
 *
 * Formato binário versionado em cacheDir/waveforms/<hash>.bin.
 */
object WaveformCache {
    private const val CACHE_DIR = "waveforms"
    private const val FORMAT_VERSION = 1

    fun fileFor(context: Context, uri: Uri): File? {
        val key = computeKey(context, uri) ?: return null
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        return File(dir, "$key.bin")
    }

    fun read(file: File): AudioRawData? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val version = input.readInt()
                if (version != FORMAT_VERSION) return null
                val durationMs = input.readLong()
                val sampleRate = input.readInt()
                val count = input.readInt()
                if (count <= 0 || count > 10_000_000) return null
                val samples = FloatArray(count)
                for (i in 0 until count) samples[i] = input.readFloat()
                AudioRawData(samples, sampleRate, durationMs)
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun write(file: File, data: AudioRawData) {
        try {
            DataOutputStream(file.outputStream().buffered()).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeLong(data.durationMs)
                output.writeInt(data.sampleRate)
                output.writeInt(data.pcmSamples.size)
                for (sample in data.pcmSamples) output.writeFloat(sample)
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun computeKey(context: Context, uri: Uri): String? {
        val size = querySize(context, uri) ?: return null
        val raw = "$uri|$size"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val sb = StringBuilder(32)
        for (i in 0 until 16) {
            val b = digest[i].toInt() and 0xff
            sb.append(HEX[b ushr 4])
            sb.append(HEX[b and 0x0f])
        }
        return sb.toString()
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private val HEX = charArrayOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')
}
