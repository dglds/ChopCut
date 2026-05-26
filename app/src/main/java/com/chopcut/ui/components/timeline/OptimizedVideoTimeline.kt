package com.chopcut.ui.components.timeline

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chopcut.data.thumbnail.OptimizedThumbnailProvider
import kotlinx.coroutines.flow.collectLatest
import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * Timeline de thumbnails de vídeo otimizada usando RecyclerView dentro do Compose.
 *
 * Integra o `OptimizedThumbnailProvider` e `TimelineAdapter` para
 * renderizar uma timeline com até 900 posições de forma performática.
 *
 * @param uri URI do vídeo.
 * @param durationMs Duração total do vídeo em milissegundos.
 * @param currentPosition Posição atual do playhead em milissegundos (para scroll programático).
 * @param onScrollChanged Callback para notificar a nova posição de scroll (em ms) ao rolar manualmente.
 * @param modifier Modificador para o componente.
 * @param itemCount Total de thumbnails a serem exibidos (padrão 900).
 * @param thumbnailHeight Altura de cada thumbnail em pixels.
 * @param thumbnailWidth Largura de cada thumbnail em pixels.
 */
@Composable
fun OptimizedVideoTimeline(
    uri: Uri,
    durationMs: Long,
    currentPosition: Long,
    onScrollChanged: (Long) -> Unit,
    onScrollStart: () -> Unit = {},
    onScrollEnd: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    itemCount: Int = 900,
    thumbnailHeight: Int = 120,
    thumbnailWidth: Int = 120
) {
    val context = LocalContext.current
    Log.i("ChopCut", "[TIMELINE_RV] OptimizedVideoTimeline COMPOSING... uri=$uri duration=$durationMs")
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Provedor e Adapter são lembrados durante a vida do Composable
    val provider = remember {
        Log.i("ChopCut", "[TIMELINE_RV] Criando OptimizedThumbnailProvider...")
        OptimizedThumbnailProvider(
            context = context,
            thumbWidth = thumbnailWidth,
            thumbHeight = thumbnailHeight
        )
    }

    val adapter = remember {
        TimelineAdapter(
            uri = uri,
            durationMs = durationMs,
            itemCountLimit = itemCount,
            provider = provider,
            thumbWidth = thumbnailWidth,
            thumbHeight = thumbnailHeight
        )
    }

    // Coleta as atualizações do provedor e notifica o adapter
    LaunchedEffect(provider, adapter) {
        Log.i("ChopCut", "[TIMELINE_RV] LaunchedEffect started, waiting for thumbnail updates...")
        provider.thumbnailUpdates.collectLatest { (timestamp, bitmap) ->
            Log.i("ChopCut", "[TIMELINE_RV] received thumbnail ts=$timestamp bmp=${bitmap.width}x${bitmap.height}")
            adapter.onThumbnailLoaded(timestamp, bitmap)
        }
    }

    // Limpeza de recursos quando o Composable é removido da árvore
    DisposableEffect(provider) {
        onDispose {
            provider.release()
        }
    }

    val recyclerViewRef = remember { mutableStateOf<RecyclerView?>(null) }
    val layoutManagerRef = remember { mutableStateOf<LinearLayoutManager?>(null) }

    // Scroll programático para a posição atual (apenas quando não está sendo arrastado pelo usuário)
    LaunchedEffect(currentPosition, recyclerViewRef.value, layoutManagerRef.value) {
        val recyclerView = recyclerViewRef.value
        val layoutManager = layoutManagerRef.value
        if (recyclerView == null || layoutManager == null || durationMs == 0L) return@LaunchedEffect
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) return@LaunchedEffect

        val targetPosition = (currentPosition.toFloat() / durationMs.toFloat() * adapter.itemCount).toInt().coerceIn(0, adapter.itemCount - 1)
        val centerOffsetPx = recyclerView.width / 2

        layoutManager.scrollToPositionWithOffset(targetPosition, centerOffsetPx - thumbnailWidth / 2)
    }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbnailHeight.dp)
            .background(Color.Black) // Background para visualização da timeline
    ) {
        // AndroidView para hospedar o RecyclerView
        AndroidView(
            factory = { ctx ->
                RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false).also { layoutManagerRef.value = it }
                    this.adapter = adapter
                    recyclerViewRef.value = this // Guardar referência para scroll programático

                    // Otimizações do RecyclerView
                    setHasFixedSize(true)
                    setItemViewCacheSize(40) // Manter mais views em cache
                    itemAnimator = null // Desabilitar animações para performance

                    // Lógica de cancelamento de requests em scroll rápido e notificação de scroll
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                                onScrollStart()
                                scope.launch {
                                    provider.clearQueue()
                                }
                            }
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                val centerItemPosition = layoutManagerRef.value?.findFirstCompletelyVisibleItemPosition()?.let { first ->
                                    val last = layoutManagerRef.value?.findLastCompletelyVisibleItemPosition() ?: first
                                    (first + last) / 2
                                } ?: 0
                                val finalPositionMs = (centerItemPosition.toLong() * durationMs) / adapter.itemCount
                                onScrollEnd(finalPositionMs)
                            }
                        }

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            val firstVisibleItemPosition = layoutManagerRef.value?.findFirstVisibleItemPosition() ?: 0
                            val lastVisibleItemPosition = layoutManagerRef.value?.findLastVisibleItemPosition() ?: 0
                            
                            // Calculate visible range for prefetching
                            if (durationMs > 0) {
                                val firstVisibleMs = (firstVisibleItemPosition.toLong() * durationMs) / adapter.itemCount
                                val lastVisibleMs = (lastVisibleItemPosition.toLong() * durationMs) / adapter.itemCount
                                provider.prefetch(uri, firstVisibleMs, lastVisibleMs + 2000L) // Add some buffer

                                // Calculate new current position at the center of the RecyclerView for user scroll feedback
                                val centerItemPosition = layoutManagerRef.value?.findFirstCompletelyVisibleItemPosition()?.let { firstCompletelyVisible ->
                                    val lastCompletelyVisible = layoutManagerRef.value?.findLastCompletelyVisibleItemPosition() ?: firstCompletelyVisible
                                    (firstCompletelyVisible + lastCompletelyVisible) / 2
                                } ?: 0
                                
                                val newPositionMs = (centerItemPosition.toLong() * durationMs) / adapter.itemCount
                                if (newPositionMs != currentPosition) {
                                    // Notify only if user is actively scrolling or on a significant change
                                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING || recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
                                        onScrollChanged(newPositionMs)
                                    }
                                }
                            }
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay do Playhead (linha vertical no centro)
        val playheadOffset = with(density) { (thumbnailWidth / 2).toDp() }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(androidx.compose.ui.graphics.Color.Red)
                .align(Alignment.Center)
        )
    }
}

