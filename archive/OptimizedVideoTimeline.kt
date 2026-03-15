// ARCHIVED — superseded by VideoTimeline (ex-TimelineV3) + FastFrameExtractor
package com.chopcut.archive

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.chopcut.archive.OptimizedThumbnailProvider
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight

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
    currentPosition: Long, // Nova posição para scroll programático
    onScrollChanged: (Long) -> Unit, // Callback para scroll do usuário
    modifier: Modifier = Modifier,
    itemCount: Int = 900,
    thumbnailHeight: Int = 120,
    thumbnailWidth: Int = 120
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Provedor e Adapter são lembrados durante a vida do Composable
    val provider = remember {
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
        provider.thumbnailUpdates.collectLatest { (timestamp, bitmap) ->
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

    // Scroll programático para a posição atual
    LaunchedEffect(currentPosition, recyclerViewRef.value, layoutManagerRef.value) {
        val recyclerView = recyclerViewRef.value
        val layoutManager = layoutManagerRef.value
        if (recyclerView == null || layoutManager == null || durationMs == 0L) return@LaunchedEffect

        // Calcular a posição do item e o offset para centralizar o playhead
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

                    // Scroll listener apenas para feedback de posição — NÃO influencia carregamento
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            if (durationMs > 0) {
                                val centerItemPosition = layoutManagerRef.value?.findFirstCompletelyVisibleItemPosition()?.let { firstCompletelyVisible ->
                                    val lastCompletelyVisible = layoutManagerRef.value?.findLastCompletelyVisibleItemPosition() ?: firstCompletelyVisible
                                    (firstCompletelyVisible + lastCompletelyVisible) / 2
                                } ?: 0

                                val newPositionMs = (centerItemPosition.toLong() * durationMs) / adapter.itemCount
                                if (newPositionMs != currentPosition) {
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

