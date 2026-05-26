package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.chopcut.util.logging.ActivityLogger
import com.chopcut.util.logging.AppActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Provedor de thumbnails otimizado para a timeline do editor.
 * 
 * Implementa:
 * - Quantização de timestamp (500ms)
 * - Cache LRU baseado em timestamp
 * - Fila de extração com prioridade (VISIBLE > PREFETCH > DISTANT)
 * - Pool de threads limitado (2 threads)
 * - Cancelamento de requests obsoletos
 * - Batching de atualizações (via Flow)
 */
class OptimizedThumbnailProvider(
    private val context: Context,
    private val cache: ThumbnailCache = ThumbnailCache(200), // Cache aumentado para timeline longa
    private val thumbWidth: Int = 120,
    private val thumbHeight: Int = 120
) {
    companion object {
        private const val QUANTIZATION_INTERVAL_MS = 500L
        private const val THREAD_POOL_SIZE = 2
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val extractorBatch = ThumbnailExtractorBatch(context)
    private val requestQueue = PriorityBlockingQueue<ThumbnailRequest>()
    private val executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)

    // Flow para emitir atualizações de thumbnails (UI batching)
    private val _thumbnailUpdates = MutableSharedFlow<Pair<Long, Bitmap>>(
        extraBufferCapacity = 64,
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val thumbnailUpdates: SharedFlow<Pair<Long, Bitmap>> = _thumbnailUpdates

    init {
        Log.i("ChopCut", "[PROVIDER] init START, starting ${THREAD_POOL_SIZE} workers")
        repeat(THREAD_POOL_SIZE) {
            executor.execute {
                Log.i("ChopCut", "[PROVIDER] worker LOOP starting on ${Thread.currentThread().name}")
                workerLoop()
            }
        }
        Log.i("ChopCut", "[PROVIDER] init DONE, workers submitted")
    }

    /**
     * Solicita um thumbnail para uma posição específica.
     * Retorna do cache se disponível, caso contrário adiciona à fila.
     */
    fun requestThumbnail(uri: Uri, positionMs: Long, priority: ThumbnailPriority) {
        val quantizedTime = quantizeTimestamp(positionMs)

        val cached = cache.get(uri.toString(), quantizedTime)
        if (cached != null) {
            scope.launch { _thumbnailUpdates.emit(quantizedTime to cached) }
            return
        }

        val request = ThumbnailRequest(
            uri = uri,
            timestamp = quantizedTime,
            priority = priority,
            width = thumbWidth,
            height = thumbHeight,
            callback = { bitmap ->
                cache.put(uri.toString(), quantizedTime, bitmap)
                scope.launch { _thumbnailUpdates.emit(quantizedTime to bitmap) }
            }
        )
        if (!requestQueue.contains(request)) {
            requestQueue.add(request)
        }
    }

    /**
     * Realiza prefetch de uma janela de timestamps.
     */
    fun prefetch(uri: Uri, startMs: Long, endMs: Long) {
        var current = quantizeTimestamp(startMs)
        val limit = quantizeTimestamp(endMs)
        
        while (current <= limit) {
            requestThumbnail(uri, current, ThumbnailPriority.PREFETCH)
            current += QUANTIZATION_INTERVAL_MS
        }
    }

    /**
     * Limpa a fila de extração (útil em scroll rápido).
     */
    suspend fun clearQueue() {
        requestQueue.clear()
    }

    /**
     * Quantiza o timestamp para intervalos de 500ms para maximizar reuso.
     */
    private fun quantizeTimestamp(time: Long): Long {
        return (time / QUANTIZATION_INTERVAL_MS) * QUANTIZATION_INTERVAL_MS
    }

    /**
     * Loop principal de extração executado em threads do pool.
     */
    private fun workerLoop() {
        Log.i("ChopCut", "[PROVIDER] workerLoop started on ${Thread.currentThread().name}")
        while (!Thread.currentThread().isInterrupted) {
            try {
                val request = requestQueue.take()
                Log.i("ChopCut", "[PROVIDER] worker got request ts=${request.timestamp} prio=${request.priority.name}")
                
                val bitmap = runBlockingExtract(request)
                
                if (bitmap != null) {
                    val optimizedBitmap = if (bitmap.config != Bitmap.Config.RGB_565) {
                        val copy = bitmap.copy(Bitmap.Config.RGB_565, false)
                        bitmap.recycle()
                        copy
                    } else {
                        bitmap
                    }

                    request.callback(optimizedBitmap)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /**
     * Extração síncrona para o worker loop.
     */
    private fun runBlockingExtract(request: ThumbnailRequest): Bitmap? {
        return kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                extractorBatch.extractSingle(
                    uri = request.uri,
                    positionMs = request.timestamp,
                    width = request.width,
                    height = request.height
                )
            }
        }
    }

    /**
     * Encerra o provedor e libera recursos.
     */
    fun release() {
        executor.shutdownNow()
        scope.coroutineContext.cancelChildren()
        cache.clear()
        requestQueue.clear()
    }
}
