package com.chopcut.ui.screen.debug

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.AudioWaveForms
import com.chopcut.ui.components.AudioWaveFormsConfig
import com.chopcut.ui.theme.ChopCutSpacing

/**
 * Screen de teste para o componente AudioWaveForms
 *
 * @param testVideoUri URI do vídeo para teste (opcional)
 * @param viewModel ViewModel do componente
 */
@Composable
fun AudioWaveFormsTestScreen(
    testVideoUri: Uri? = null,
    viewModel: AudioWaveFormsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Controles de teste
    var barCount by remember { mutableIntStateOf(200) }
    var showGradient by remember { mutableStateOf(false) }
    var selectedBaseline by remember { mutableStateOf(0) }
    var useMinimalConfig by remember { mutableStateOf(false) }
    var animationEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChopCutSpacing.md)
    ) {
        Text(
            text = "AudioWaveForms Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(ChopCutSpacing.md))

        // Controles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(ChopCutSpacing.md)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Configurações",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Slider de barra count
                Text(text = "Número de barras: $barCount")
                Slider(
                    value = barCount.toFloat(),
                    onValueChange = { barCount = it.toInt() },
                    valueRange = 50f..500f,
                    steps = 9
                )

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Toggle gradiente
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = showGradient,
                        onCheckedChange = { showGradient = it }
                    )
                    Spacer(modifier = Modifier.width(ChopCutSpacing.xs))
                    Text("Usar gradiente")
                }

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Toggle minimal config
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = useMinimalConfig,
                        onCheckedChange = { useMinimalConfig = it }
                    )
                    Spacer(modifier = Modifier.width(ChopCutSpacing.xs))
                    Text("Config minimalista")
                }

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Toggle animação
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = animationEnabled,
                        onCheckedChange = { animationEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(ChopCutSpacing.xs))
                    Text("Animação")
                }

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Selector de baseline
                Text(text = "Baseline:")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.sm)
                ) {
                    listOf("Bottom", "Center", "Top").forEachIndexed { index, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedBaseline == index,
                                onClick = { selectedBaseline = index }
                            )
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ChopCutSpacing.sm))

                // Botão de reload
                Button(
                    onClick = {
                        testVideoUri?.let { viewModel.loadWaveform(it, barCount) }
                    },
                    enabled = testVideoUri != null && uiState !is AudioWaveFormsUiState.Loading
                ) {
                    Text("Recarregar")
                }

                Spacer(modifier = Modifier.height(ChopCutSpacing.xs))

                OutlinedButton(
                    onClick = { viewModel.reset() },
                    enabled = uiState !is AudioWaveFormsUiState.Loading
                ) {
                    Text("Reset")
                }

                OutlinedButton(
                    onClick = {
                        // Recarregar waveform
                        testVideoUri?.let { viewModel.loadWaveform(it, targetBarCount = barCount) }
                    },
                    enabled = testVideoUri != null && uiState !is AudioWaveFormsUiState.Loading
                ) {
                    Text("Recarregar")
                }
            }
        }

        Spacer(modifier = Modifier.height(ChopCutSpacing.md))

        // Área do waveform
        when (val state = uiState) {
            is AudioWaveFormsUiState.Idle -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(ChopCutSpacing.xl)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Selecione um vídeo para testar",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            is AudioWaveFormsUiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(ChopCutSpacing.xl)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(ChopCutSpacing.sm))
                        Text("Carregando waveform...")
                    }
                }
            }

            is AudioWaveFormsUiState.Ready -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(ChopCutSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Duration: ${state.durationMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Bars: ${state.barCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "SampleRate: ${state.sampleRate} Hz",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(ChopCutSpacing.md))

                        val baseline = when (selectedBaseline) {
                            0 -> AudioWaveFormsConfig.Baseline.Bottom
                            1 -> AudioWaveFormsConfig.Baseline.Center
                            else -> AudioWaveFormsConfig.Baseline.Top
                        }

                        val config = when {
                            useMinimalConfig -> AudioWaveFormsConfig.Minimal.copy(
                                baseline = baseline,
                                animationEnabled = animationEnabled
                            )
                            showGradient -> AudioWaveFormsConfig.Gradient.copy(
                                baseline = baseline,
                                animationEnabled = animationEnabled
                            )
                            else -> AudioWaveFormsConfig.Default.copy(
                                baseline = baseline,
                                animationEnabled = animationEnabled
                            )
                        }

                        AudioWaveForms(
                            amplitudes = state.amplitudes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            config = config
                        )
                    }
                }
            }

            is AudioWaveFormsUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(ChopCutSpacing.md)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Erro: ${state.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    // Auto-load se URI fornecida
    LaunchedEffect(testVideoUri, barCount) {
        testVideoUri?.let {
            viewModel.loadWaveform(it, targetBarCount = barCount)
        }
    }
}
