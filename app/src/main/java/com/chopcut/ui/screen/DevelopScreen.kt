package com.chopcut.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.components.PlayheadIndicator
import com.chopcut.ui.timeline.components.ProgressBar
import com.chopcut.ui.timeline.components.TimelineScrubber
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline

/**
 * Tela de desenvolvimento para testes de visualização de componentes.
 * 
 * Use esta tela para:
 * - Testar novos componentes isoladamente
 * - Verificar comportamentos de UI
 * - Validar estados e animações
 * - Comparar variações de design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopScreen(
    onNavigateBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Desenvolvimento") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Cabeçalho informativo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Área de Testes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Use esta tela para testar e visualizar componentes isoladamente.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // ============================================
            // SEÇÃO: Componentes de Timeline
            // ============================================
            SectionTitle("Componentes de Timeline")
            
            // PlayheadIndicator
            ComponentTestCard(
                title = "PlayheadIndicator",
                description = "Indicador central do playhead em dois estados"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Estado normal
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Normal:", modifier = Modifier.width(80.dp))
                        Box(
                            modifier = Modifier
                                .height(60.dp)
                                .width(2.dp)
                                .background(Color.Gray)
                        ) {
                            PlayheadIndicator(
                                isRelevo = false,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                    
                    // Estado em criação (relevo)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Em Criação:", modifier = Modifier.width(80.dp))
                        Box(
                            modifier = Modifier
                                .height(60.dp)
                                .width(2.dp)
                                .background(Color.Gray)
                        ) {
                            PlayheadIndicator(
                                isRelevo = true,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }
            }
            
            // ProgressBar
            ComponentTestCard(
                title = "ProgressBar",
                description = "Barra de progresso em diferentes estados"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressBar(
                        progress = 0.25f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProgressBar(
                        progress = 0.5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProgressBar(
                        progress = 0.75f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProgressBar(
                        progress = 1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // ============================================
            // SEÇÃO: Botões e Controles
            // ============================================
            SectionTitle("Botões e Controles")
            
            ComponentTestCard(
                title = "FAB Estados",
                description = "Botões flutuantes em diferentes estados"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(onClick = {}) {
                        Icon(Icons.Default.Build, contentDescription = null)
                    }
                    FloatingActionButton(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                    FloatingActionButton(
                        onClick = {},
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                }
            }
            
            // ============================================
            // SEÇÃO: Paleta de Cores
            // ============================================
            SectionTitle("Paleta de Cores")
            
            ComponentTestCard(
                title = "Cores do Tema",
                description = "Principais cores definidas no tema"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorRow("Primary", MaterialTheme.colorScheme.primary)
                    ColorRow("Secondary", MaterialTheme.colorScheme.secondary)
                    ColorRow("Tertiary", MaterialTheme.colorScheme.tertiary)
                    ColorRow("Error", MaterialTheme.colorScheme.error)
                    ColorRow("Background", MaterialTheme.colorScheme.background)
                    ColorRow("Surface", MaterialTheme.colorScheme.surface)
                }
            }
            
            // ============================================
            // SEÇÃO: Timelines (Comparação)
            // ============================================
            SectionTitle("Comparação de Timelines")
            
            val mockDurationMs = 60000L // 1 minuto mock
            val mockPositionMs = 15000L // 15 segundos mock
            
            // 1. TimelineScrubber (Nova Arquitetura)
            ComponentTestCard(
                title = "TimelineScrubber (Nova)",
                description = "Componente puro da nova arquitetura - otimizado para Celeron N5095A"
            ) {
                Column {
                    Text(
                        text = "Posição: ${mockPositionMs/1000}s / ${mockDurationMs/1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ConfiguracaoTimeline.ALTURA_FAIXA_DP)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        TimelineScrubber(
                            durationMs = mockDurationMs,
                            positionMs = mockPositionMs,
                            onPositionChange = { /* mock */ },
                            onScrollStart = { /* mock */ },
                            onScrollEnd = { /* mock */ },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // 2. Timeline.kt (Antiga)
            ComponentTestCard(
                title = "Timeline.kt (Antiga)",
                description = "Implementação monolítica - mantida para referência"
            ) {
                Column {
                    Text(
                        text = "Timeline unificada com player integrado",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "⚠️ Requer PreviewManager e vídeo real",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { /* Navegar para TimelineComparisonScreen */ },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ver em TimelineComparisonScreen")
                    }
                }
            }
            
            // ============================================
            // SEÇÃO: Espaço para Novos Testes
            // ============================================
            SectionTitle("Espaço para Testes")
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Área livre para testes\n(adicione seus componentes aqui)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ComponentTestCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ColorRow(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
