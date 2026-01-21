package com.chopcut.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Throttle de seek para ~30fps (CropSnap-style)
 *
 * Reduz o número de callbacks de seek durante o scrubbing,
 * limitando a aproximadamente 30fps (33ms entre seeks).
 * Isso melhora a performance e suavidade do scroll.
 *
 * @param delayMs Milissegundos entre seeks (padrão: 33ms = ~30fps)
 */
class SeekThrottler(
    private val delayMs: Long = 33L // ~30fps
) {
    private var lastSeekTime = 0L
    private var pendingSeek: Long? = null
    private var scope: CoroutineScope? = null
    private var pendingJob: kotlinx.coroutines.Job? = null

    /**
     * Throttle de posição para evitar callbacks excessivos
     *
     * @param positionMs Posição atual em milissegundos
     * @param onSeek Callback executado quando seek deve ocorrer
     */
    fun throttle(positionMs: Long, onSeek: (Long) -> Unit) {
        val now = System.currentTimeMillis()
        val timeSinceLastSeek = now - lastSeekTime

        if (timeSinceLastSeek >= delayMs) {
            // Tempo suficiente passou, executar seek imediatamente
            lastSeekTime = now
            pendingSeek = null
            pendingJob?.cancel()
            Timber.d("SeekThrottler: Executing seek to ${positionMs}ms")
            onSeek(positionMs)
        } else {
            // Throttle: aguardar antes de executar seek
            pendingSeek = positionMs
            val delayRemaining = delayMs - timeSinceLastSeek

            // Cancelar job anterior se existir
            pendingJob?.cancel()

            pendingJob = scope?.launch {
                delay(delayRemaining)
                pendingSeek?.let { pos ->
                    Timber.d("SeekThrottler: Delayed seek to ${pos}ms")
                    onSeek(pos)
                    lastSeekTime = System.currentTimeMillis()
                    pendingSeek = null
                }
            }
        }
    }

    /**
     * Cancela qualquer seek pendente
     */
    fun cancelPending() {
        pendingJob?.cancel()
        pendingSeek = null
    }

    /**
     * Define o coroutine scope para operações assíncronas
     */
    fun setScope(scope: CoroutineScope) {
        this.scope = scope
    }
}

/**
 * Remember composable para SeekThrottler
 *
 * Cria e mantém uma instância de SeekThrottler durante
 * o ciclo de vida do composable.
 *
 * @param delayMs Milissegundos entre seeks (padrão: 33ms)
 */
@Composable
fun rememberSeekThrottler(
    delayMs: Long = 33L
): SeekThrottler {
    val scope = rememberCoroutineScope()
    return remember(delayMs) {
        SeekThrottler(delayMs).also { it.setScope(scope) }
    }
}
