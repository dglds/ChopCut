package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chopcut.ui.timeline.PreviewManager
import com.chopcut.ui.timeline.TimelineViewModel
import com.chopcut.ui.timeline.EditorTimelineViewModel
import com.chopcut.ui.timeline.model.*
import com.chopcut.ui.timeline.components.*
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import timber.log.Timber

/**
 * Tela de comparação entre as duas implementações de Timeline.
 * 
 * TOPO: Timeline.kt (implementação antiga/unificada)
 * BAIXO: TimelineScrubber.kt (componente puro - nova arquitetura)
 * 
 * Use esta tela para identificar qual implementação manter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineComparisonScreen(
    videoUri: Uri,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // ViewModel compartilhado
    val previewManager = remember { PreviewManager(context) }
    val viewModel = remember { EditorTimelineViewModel(context, previewManager) }
    
    val estado by viewModel.estadoEditor.collectAsStateWithLifecycle()
    val estadoFab by viewModel.estadoFab.collectAsStateWithLifecycle()
    val rangeEmCriacao by viewModel.rangeEmCriacao.collectAsStateWithLifecycle()
    
    // Prepara vídeo
    LaunchedEffect(videoUri) {
        viewModel.prepararVideo(videoUri)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparação de Timelines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ============================================
            // SEÇÃO 1: Timeline.kt (Antiga/Unificada)
            // ============================================
            TimelineSection(
                titulo = "1. Timeline.kt (Antiga/Unificada)",
                descricao = "Implementação monolítica com player integrado. " +
                        "Local: com.chopcut.ui.timeline.Timeline",
                corBorda = MaterialTheme.colorScheme.primary
            ) {
                // Usa a Timeline antiga (com seu próprio ViewModel interno)
                val timelineViewModelOld = remember(estado.duracaoTotalMs) {
                    TimelineViewModel(initialDurationMs = estado.duracaoTotalMs)
                }
                com.chopcut.ui.timeline.Timeline(
                    uri = videoUri,
                    previewManager = previewManager
                )
            }
            
            Divider(thickness = 2.dp)
            
            // ============================================
            // SEÇÃO 2: Nova Arquitetura (Componentes)
            // ============================================
            TimelineSection(
                titulo = "2. Nova Arquitetura (Componentes Puros)",
                descricao = "VideoPreview + TimelineScrubber + RangeOverlay separados. " +
                        "Local: com.chopcut.ui.timeline.components.*",
                corBorda = MaterialTheme.colorScheme.secondary
            ) {
                Column {
                    // VideoPreview
                    VideoPreview(
                        videoUri = estado.videoUri,
                        exoPlayer = previewManager.exoPlayer,
                        isReady = estado.estadoPlayer != EstadoPlayer.CARREGANDO,
                        isPlaying = estado.estadoPlayer == EstadoPlayer.REPRODUZINDO,
                        currentPosition = estado.posicaoPlayheadMs,
                        duration = estado.duracaoTotalMs,
                        onTogglePlayPause = {
                            viewModel.processarEvento(EventoEditor.AlternarReproducao)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // TimelineScrubber
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ConfiguracaoTimeline.ALTURA_FAIXA_DP)
                    ) {
                        TimelineScrubber(
                            durationMs = estado.duracaoTotalMs,
                            positionMs = estado.posicaoPlayheadMs,
                            onPositionChange = { novaPosicaoMs ->
                                viewModel.atualizarPosicao(novaPosicaoMs, deArraste = true)
                            },
                            onScrollStart = {
                                viewModel.setEmArraste(true)
                            },
                            onScrollEnd = {
                                viewModel.setEmArraste(false)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // RangeOverlay
                        if (estado.duracaoTotalMs > 0) {
                            val containerWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
                            val pxPorSegundo = 60f // Simplificado
                            
                            RangeOverlay(
                                ranges = estado.ranges,
                                rangeEmCriacao = rangeEmCriacao,
                                rangeSelecionadoId = estado.rangeSelecionadoId,
                                posicaoPlayheadMs = estado.posicaoPlayheadMs,
                                duracaoMs = estado.duracaoTotalMs,
                                scrollOffsetPx = 0f, // Simplificado
                                containerWidthPx = containerWidthPx,
                                pxPorSegundo = pxPorSegundo,
                                onRangeSelect = { rangeId ->
                                    viewModel.processarEvento(EventoEditor.SelecionarRange(rangeId))
                                },
                                onRangeUpdate = { id, inicio, fim ->
                                    viewModel.processarEvento(EventoEditor.AtualizarRange(id, inicio, fim))
                                },
                                onRangeDelete = { id ->
                                    viewModel.processarEvento(EventoEditor.DeletarRange(id))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        // Playhead
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayheadIndicator(
                                isRelevo = estado.emCriacaoRange,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }
            }
            
            Divider(thickness = 2.dp)
            
            // ============================================
            // ANÁLISE/RECOMENDAÇÃO
            // ============================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recomendação",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Timeline.kt (antiga): Componente monolítico, mais difícil de manter\n" +
                                "• Nova arquitetura: Componentes puros, mais testável e flexível\n\n" +
                                "MANTER: Nova arquitetura (componentes separados)\n" +
                                "REMOVER: Timeline.kt (apos migrar funcionalidades faltantes)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Botões de teste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { viewModel.processarEvento(EventoEditor.IniciarCriacaoRange) }
                ) {
                    Text("Testar Add Range")
                }
                
                Button(
                    onClick = { viewModel.processarEvento(EventoEditor.AlternarReproducao) }
                ) {
                    Text("Play/Pause")
                }
            }
            
            // Info de debug
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Debug Info:", fontWeight = FontWeight.Bold)
                    Text("Posição: ${estado.posicaoPlayheadMs}ms")
                    Text("Duração: ${estado.duracaoTotalMs}ms")
                    Text("Ranges: ${estado.ranges.size}")
                    Text("Estado FAB: $estadoFab")
                    Text("Em Criação: ${rangeEmCriacao != null}")
                }
            }
        }
    }
}

@Composable
private fun TimelineSection(
    titulo: String,
    descricao: String,
    corBorda: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, corBorda)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = corBorda
            )
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
