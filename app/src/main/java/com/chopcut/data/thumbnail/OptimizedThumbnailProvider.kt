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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors

/**
 * Provedor de thumbnails otimizado para a timeline do editor.
 *
 * Implementa:
 * - Quantização de timestamp (500ms)
 * - Cache LRU baseado em timestamp
 * - Fila FIFO de extração (ordem sequencial, scroll não influencia)
 * - Pool de threads limitado (2 threads)
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
    private val requestQueue = LinkedBlockingQueue<ThumbnailRequest>()
    private val executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    
    // Mutex para proteger a fila de requests pendentes
    private val queueMutex = Mutex()
    // Map de timestamps pendentes para evitar duplicidade na fila
    private val pendingTimestamps = mutableSetOf<Long>()

    // Flow para emitir atualizações de thumbnails (UI batching)
    private val _thumbnailUpdates = MutableSharedFlow<Pair<Long, Bitmap>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val thumbnailUpdates: SharedFlow<Pair<Long, Bitmap>> = _thumbnailUpdates

    init {
        // Iniciar workers de extração
        repeat(THREAD_POOL_SIZE) {
            executor.execute {
                workerLoop()
            }
        }
    }

    /**
     * Solicita um thumbnail para uma posição específica.
     * Retorna do cache se disponível, caso contrário adiciona à fila.
     */
    fun requestThumbnail(uri: Uri, positionMs: Long, priority: ThumbnailPriority) {
        val quantizedTime = quantizeTimestamp(positionMs)
        
        // 1. Verificar cache
        val cached = cache.get(uri.toString(), quantizedTime)
        if (cached != null) {
            scope.launch { _thumbnailUpdates.emit(quantizedTime to cached) }
            return
        }

        // 2. Adicionar à fila se não estiver pendente
        scope.launch {
            queueMutex.withLock {
                if (pendingTimestamps.contains(quantizedTime)) return@withLock
                
                pendingTimestamps.add(quantizedTime)
                requestQueue.add(ThumbnailRequest(
                    uri = uri,
                    timestamp = quantizedTime,
                    priority = priority,
                    width = thumbWidth,
                    height = thumbHeight,
                    callback = { bitmap ->
                        cache.put(uri.toString(), quantizedTime, bitmap)
                        scope.launch { _thumbnailUpdates.emit(quantizedTime to bitmap) }
                    }
                ))
            }
        }
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
        while (!Thread.currentThread().isInterrupted) {
            try {
                // take() bloqueia até que um item esteja disponível
                val request = requestQueue.take()
                
                // Extrair thumbnail
                val bitmap = runBlockingExtract(request)
                
                if (bitmap != null) {
                    // Configurar bitmap para RGB_565 para economizar memória
                    val optimizedBitmap = if (bitmap.config != Bitmap.Config.RGB_565) {
                        val copy = bitmap.copy(Bitmap.Config.RGB_565, false)
                        bitmap.recycle()
                        copy
                    } else {
                        bitmap
                    }
                    
                    request.callback(optimizedBitmap)
                }

                // Remover do set de pendentes
                scope.launch {
                    queueMutex.withLock {
                        pendingTimestamps.remove(request.timestamp)
                    }
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
