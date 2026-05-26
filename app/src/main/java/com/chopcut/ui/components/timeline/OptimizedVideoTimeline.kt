package com.chopcut.ui.components.timeline

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chopcut.data.thumbnail.OptimizedThumbnailProvider
import com.chopcut.data.thumbnail.ThumbnailPriority
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Timeline otimizada com foco EXCLUSIVO na rolagem e exibição das miniaturas do vídeo.
 *
 * Não renderiza Régua de Ticks ou Forma de Onda internas, mantendo-se como um componente leve
 * focado em performance, reuso de views e interação sob demanda via RecyclerView.
 *
 * @param uri URI do vídeo.
 * @param durationMs Duração total do vídeo em milissegundos.
 * @param currentPosition Posição de reprodução atual em milissegundos (para sincronismo programático).
 * @param onScrollProgress Callback acionado a cada pixel de rolagem, transmitindo o timestamp exato em ms.
 * @param onScrollChanged Callback acionado durante gestos de arrasto ativos para atualizar o playhead.
 * @param onScrollStart Callback acionado ao iniciar o arrasto.
 * @param onScrollEnd Callback acionado ao cessar a rolagem.
 * @param modifier Modificador Compose.
 * @param thumbnailHeight Altura de cada miniatura em dp (padrão 56.dp).
 * @param thumbnailWidth Largura de cada miniatura em dp (padrão 60.dp, onde 1s = 60dp).
 */
@Composable
fun OptimizedVideoTimeline(
    uri: Uri,
    durationMs: Long,
    currentPosition: Long,
    onScrollProgress: (Float) -> Unit,
    onScrollChanged: (Long) -> Unit,
    onScrollStart: () -> Unit = {},
    onScrollEnd: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    thumbnailHeight: Int = 56,
    thumbnailWidth: Int = 60
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thumbnailHeightPx = with(density) { thumbnailHeight.dp.roundToPx() }
    val thumbnailWidthPx = with(density) { thumbnailWidth.dp.roundToPx() }

    // Provedor e Adapter compartilhados
    val provider = remember {
        Log.i("ChopCut", "[TIMELINE_RV] Criando OptimizedThumbnailProvider...")
        OptimizedThumbnailProvider(
            context = context,
            thumbWidth = thumbnailWidthPx,
            thumbHeight = thumbnailHeightPx
        )
    }

    val adapter = remember {
        TimelineAdapter(
            uri = uri,
            durationMs = durationMs,
            provider = provider,
            thumbWidth = thumbnailWidthPx,
            thumbHeight = thumbnailHeightPx
        )
    }

    LaunchedEffect(provider, adapter) {
        provider.thumbnailUpdates.collectLatest { (timestamp, bitmap) ->
            adapter.onThumbnailLoaded(timestamp, bitmap)
        }
    }

    DisposableEffect(provider) {
        onDispose {
            provider.release()
        }
    }

    val recyclerViewRef = remember { mutableStateOf<RecyclerView?>(null) }
    val layoutManagerRef = remember { mutableStateOf<LinearLayoutManager?>(null) }

    // Sincronização programática a partir do ExoPlayer (apenas quando não arrastado pelo usuário)
    LaunchedEffect(currentPosition, recyclerViewRef.value, layoutManagerRef.value) {
        val recyclerView = recyclerViewRef.value
        val layoutManager = layoutManagerRef.value
        if (recyclerView == null || layoutManager == null || durationMs == 0L) return@LaunchedEffect
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) return@LaunchedEffect

        val targetSeconds = currentPosition.toFloat() / 1000f
        val scrollXPx = targetSeconds * thumbnailWidthPx

        layoutManager.scrollToPositionWithOffset(0, -(scrollXPx.toInt()))
        onScrollProgress(currentPosition.toFloat())
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbnailHeight.dp)
            .background(Color.Black)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val centerOffsetPx = screenWidthPx / 2
        val horizontalPaddingPx = centerOffsetPx - (thumbnailWidthPx / 2)

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    RecyclerView(ctx).apply {
                        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false).also { layoutManagerRef.value = it }
                        this.adapter = adapter
                        recyclerViewRef.value = this

                        setHasFixedSize(true)
                        setItemViewCacheSize(20)
                        itemAnimator = null

                        // Aplicar espaçamento de centralização flexível nas bordas do RecyclerView
                        clipToPadding = false
                        setPadding(horizontalPaddingPx.toInt(), 0, horizontalPaddingPx.toInt(), 0)

                        addOnScrollListener(object : RecyclerView.OnScrollListener() {
                            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                                    onScrollStart()
                                    scope.launch {
                                        provider.clearQueue()
                                    }
                                }
                                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                    val firstVisible = layoutManagerRef.value?.findFirstVisibleItemPosition() ?: 0
                                    val firstView = layoutManagerRef.value?.findViewByPosition(firstVisible)
                                    val left = firstView?.left ?: 0
                                    val scrollX = firstVisible * thumbnailWidthPx - (left - recyclerView.paddingLeft)
                                    val finalPositionMs = (scrollX.toFloat() / thumbnailWidthPx * 1000f).toLong()
                                    onScrollEnd(finalPositionMs.coerceIn(0L, durationMs))
                                }
                            }

                            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                val layoutManager = layoutManagerRef.value ?: return
                                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                                if (firstVisible == RecyclerView.NO_POSITION) return
                                
                                val firstView = layoutManager.findViewByPosition(firstVisible) ?: return
                                
                                // Cálculo exato de scroll por pixel em relação ao padding de início
                                val scrollX = firstVisible * thumbnailWidthPx - (firstView.left - paddingLeft)
                                val calculatedMs = (scrollX.toFloat() / thumbnailWidthPx * 1000f)
                                
                                // Propagar progresso instantâneo em tempo real
                                onScrollProgress(calculatedMs.coerceIn(0f, durationMs.toFloat()))

                                // Notificar alteração apenas quando o usuário arrasta manualmente
                                if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING || recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
                                    onScrollChanged(calculatedMs.toLong().coerceIn(0L, durationMs))
                                }

                                // Prefetch dinâmico
                                val lastVisible = layoutManager.findLastVisibleItemPosition()
                                val firstVisibleMs = firstVisible * 1000L
                                val lastVisibleMs = if (lastVisible != RecyclerView.NO_POSITION) lastVisible * 1000L else firstVisibleMs + 5000L
                                provider.prefetch(uri, firstVisibleMs, lastVisibleMs + 2000L)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay de Playhead fixo (Linha Vermelha central)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.Red)
                    .align(Alignment.Center)
            )
        }
    }
}
