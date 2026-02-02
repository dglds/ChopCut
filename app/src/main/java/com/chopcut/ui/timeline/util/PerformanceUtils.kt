package com.chopcut.ui.timeline.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Utilitários de performance para otimização no Celeron N5095A.
 * 
 * Estas funções ajudam a:
 * - Limitar taxa de atualização (throttling)
 * - Memoizar valores computados
 * - Reduzir alocações em loops críticos
 */

/**
 * Constantes de performance.
 */
object PerformanceConfig {
    /**
     * Intervalo de throttling padrão para 60 FPS (16.67ms)
     */
    const val THROTTLE_60FPS_MS = 16L
    
    /**
     * Intervalo de throttling para 30 FPS (33.33ms)
     */
    const val THROTTLE_30FPS_MS = 33L
    
    /**
     * Intervalo mínimo entre updates de scroll (ms)
     */
    const val SCROLL_THROTTLE_MS = 16L
    
    /**
     * Tamanho do pool de objetos reutilizáveis
     */
    const val OBJECT_POOL_SIZE = 32
}

/**
 * Throttler simples baseado em tempo.
 * 
 * Uso:
 * ```
 * val throttler = rememberThrottler(16)
 * 
 * onScroll = { value ->
 *     throttler.throttle {
 *         updatePosition(value)
 *     }
 * }
 * ```
 */
class Throttler(private val intervalMs: Long) {
    private var lastExecution = 0L
    private var pendingAction: (() -> Unit)? = null
    
    /**
     * Executa a ação respeitando o intervalo mínimo.
     * Se chamado antes do intervalo, a ação é descartada.
     */
    fun throttle(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastExecution >= intervalMs) {
            lastExecution = now
            action()
        }
    }
    
    /**
     * Executa a ação respeitando o intervalo mínimo.
     * Se chamado antes do intervalo, a última ação pendente é armazenada
     * e executada quando o intervalo expirar.
     */
    fun throttleLatest(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastExecution >= intervalMs) {
            lastExecution = now
            action()
        } else {
            pendingAction = action
        }
    }
    
    /**
     * Força a execução da ação pendente, se houver.
     */
    fun flushPending() {
        pendingAction?.let {
            it()
            pendingAction = null
            lastExecution = System.currentTimeMillis()
        }
    }
}

/**
 * Cria e lembra um Throttler.
 */
@Composable
fun rememberThrottler(intervalMs: Long = PerformanceConfig.THROTTLE_60FPS_MS): Throttler {
    return remember(intervalMs) { Throttler(intervalMs) }
}

/**
 * Throttle um StateFlow/Flow para no máximo 60 FPS.
 */
fun <T> Flow<T>.throttleLatest(intervalMs: Long = PerformanceConfig.THROTTLE_60FPS_MS): Flow<T> = flow {
    var lastEmission = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmission >= intervalMs) {
            lastEmission = now
            emit(value)
        }
    }
}

/**
 * ProduceState com throttling integrado.
 * Útil para valores que atualizam rapidamente (ex: posição do playhead).
 */
@Composable
fun <T> produceThrottledState(
    initialValue: T,
    intervalMs: Long = PerformanceConfig.THROTTLE_60FPS_MS,
    producer: suspend () -> T
): State<T> {
    return produceState(initialValue = initialValue, intervalMs) {
        while (true) {
            value = producer()
            delay(intervalMs)
        }
    }
}

/**
 * Pool simples de objetos reutilizáveis.
 * Evita alocações frequentes em GC-sensitive contexts.
 */
class ObjectPool<T>(
    private val size: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit = {}
) {
    private val pool = ArrayDeque<T>(size)
    private val inUse = mutableSetOf<T>()
    
    init {
        repeat(size) {
            pool.addLast(factory())
        }
    }
    
    /**
     * Obtém um objeto do pool ou cria um novo se o pool estiver vazio.
     */
    fun acquire(): T {
        val obj = pool.removeFirstOrNull() ?: factory()
        inUse.add(obj)
        return obj
    }
    
    /**
     * Devolve um objeto ao pool.
     */
    fun release(obj: T) {
        if (inUse.remove(obj)) {
            reset(obj)
            if (pool.size < size) {
                pool.addLast(obj)
            }
        }
    }
}

/**
 * Medidor de performance para debug.
 * 
 * Uso:
 * ```
 * val tracker = rememberPerformanceTracker("scroll")
 * 
 * LaunchedEffect(position) {
 *     tracker.track {
 *         // código a ser medido
 *     }
 * }
 * ```
 */
class PerformanceTracker(
    private val name: String,
    private val logIntervalMs: Long = 5000
) {
    private var frameCount = 0
    private var totalTimeMs = 0L
    private var lastLog = 0L
    private var maxTimeMs = 0L
    private var minTimeMs = Long.MAX_VALUE
    
    /**
     * Executa e mede o tempo de uma operação.
     */
    fun <T> track(block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000 // ns -> ms
        
        frameCount++
        totalTimeMs += elapsed
        maxTimeMs = maxOf(maxTimeMs, elapsed)
        minTimeMs = minOf(minTimeMs, elapsed)
        
        val now = System.currentTimeMillis()
        if (now - lastLog >= logIntervalMs) {
            logStats()
            lastLog = now
        }
        
        return result
    }
    
    /**
     * Reseta as estatísticas.
     */
    fun reset() {
        frameCount = 0
        totalTimeMs = 0
        maxTimeMs = 0
        minTimeMs = Long.MAX_VALUE
    }
    
    private fun logStats() {
        if (frameCount > 0) {
            val avg = totalTimeMs / frameCount
            android.util.Log.d(
                "Performance",
                "[$name] Frames: $frameCount, Média: ${avg}ms, Min: ${minTimeMs}ms, Max: ${maxTimeMs}ms"
            )
        }
    }
}

/**
 * Cria e lembra um PerformanceTracker.
 */
@Composable
fun rememberPerformanceTracker(
    name: String,
    logIntervalMs: Long = 5000
): PerformanceTracker {
    return remember(name, logIntervalMs) { PerformanceTracker(name, logIntervalMs) }
}

/**
 * Helper para criar um timer de frame de 16ms (60 FPS).
 * Retorna true a cada 16ms, útil para controlar updates.
 */
@Composable
fun rememberFrameTimer(enabled: Boolean = true): Boolean {
    val frameState = remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        
        while (true) {
            delay(PerformanceConfig.THROTTLE_60FPS_MS)
            frameState.longValue = System.currentTimeMillis()
        }
    }
    
    return enabled
}

/**
 * Extensão para coletar um StateFlow com throttling.
 * Limita atualizações para no máximo 60 FPS.
 */
fun <T> kotlinx.coroutines.flow.StateFlow<T>.throttled(
    intervalMs: Long = PerformanceConfig.THROTTLE_60FPS_MS
): kotlinx.coroutines.flow.Flow<T> = flow {
    var lastEmission = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmission >= intervalMs) {
            lastEmission = now
            emit(value)
        }
    }
}
