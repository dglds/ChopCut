# Plano de Implementação: AudioWaveForms Component

## Fase 0: Documentação e APIs Permitidas

### Otimização do Serviço de Áudio (ALTERAÇÕES NECESSÁRIAS)

**Problema identificado**: O `AudioDataExtractor` está gerando 15-25 Hz (muito alto) e depois o `WaveFormGenerator` faz outro downsampling. Isso desperdiça memória e processamento.

**Solução**: Modificar `AudioDataExtractor` para:
1. Adicionar parâmetro `targetBarCount: Int` para extrair exatamente o número de barras necessário
2. Remover a lógica de `targetSampleRate` fixo (15-25 Hz)
3. Calcular `samplesPerBar` dinamicamente baseado no número de barras desejadas

### APIs Modificadas

#### AudioDataExtractor (ALTERAR)
**Arquivo**: `app/src/main/java/com/chopcut/data/audio/AudioDataExtractor.kt`

**Novo método principal**:
```kotlin
suspend fun extractRawPcmData(
    uri: Uri,
    targetBarCount: Int = 200  // Número desejado de barras (padrão reduzido)
): AudioRawData
```

**Lógica de downsampling otimizada** (linhas 64-74):
- Calcular `samplesPerBar` diretamente baseado em `targetBarCount`
- Processar e agrupar samples diretamente na extração (sem segundo processamento)
- Para um vídeo de 60s com 200 barras = ~3.3 samples/segundo (muito mais eficiente)

#### WaveFormGenerator (USAR OPCIONALMENTE)
- Para visualização de barras simples, o `AudioDataExtractor` já retorna o número correto de samples
- `WaveFormGenerator` só será necessário se quisermos re-processar com diferente qualidade

---

## Fase 1: Otimizar AudioDataExtractor

### O que implementar
Modificar `app/src/main/java/com/chopcut/data/audio/AudioDataExtractor.kt`:

1. **Adicionar parâmetro `targetBarCount`**:
```kotlin
suspend fun extractRawPcmData(
    uri: Uri,
    targetBarCount: Int = 200
): AudioRawData = withContext(Dispatchers.IO) {
```

2. **Remover lógica de targetSampleRate fixo** (linhas 64-74):
```kotlin
// REMOVER este bloco:
val targetSampleRate = when {
    expectedDurationMs < 30000 -> 25
    expectedDurationMs < 120000 -> 20
    else -> 15
}

// SUBSTITUIR por:
val samplesPerBar = if (expectedDurationMs > 0) {
    val totalFrames = (sampleRate * channelCount * expectedDurationMs / 1000).toInt()
    (totalFrames.toFloat() / targetBarCount).toInt().coerceAtLeast(1)
} else {
    1000  // fallback
}
```

3. **Atualizar o cache** para incluir targetBarCount na chave:
```kotlin
val cacheKey = "${uri.toString()}_$targetBarCount"
```

### Verificação
- [ ] Parâmetro `targetBarCount` adicionado
- [ ] Cálculo de `samplesPerBar` baseado em `targetBarCount`
- [ ] Cache key inclui `targetBarCount`
- [ ] Logs Timber mostram o número correto de pontos

---

## Fase 2: Criar AudioWaveFormsConfig

### O que implementar
Criar `app/src/main/java/com/chopcut/ui/components/AudioWaveFormsConfig.kt`:

```kotlin
package com.chopcut.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuração visual do componente AudioWaveForms
 *
 * @param barColor Cor das barras (usa Primary do tema por padrão)
 * @param barWidth Largura fixa de cada barra (null = auto baseado no espaço)
 * @param barGap Espaço entre barras (null = auto baseado no espaço)
 * @param barCornerRadius Raio dos cantos das barras
 * @param minHeight Altura mínima para barras de silêncio (0.0-1.0)
 * @param maxHeight Escala máxima de altura (0.0-1.0)
 * @param animationEnabled Habilita animação de entrada
 * @param animationDuration Duração da animação em ms
 * @param gradient Gradiente opcional para as barras
 * @param baseline Posição da linha de base
 */
data class AudioWaveFormsConfig(
    val barColor: Color = Color.Unspecified,  // Unspecified = usa Primary do tema
    val barWidth: Dp? = null,                 // null = calculado automaticamente
    val barGap: Dp? = null,                   // null = calculado automaticamente
    val barCornerRadius: Dp = 2.dp,
    val minHeight: Float = 0.02f,             // Reduzido para melhor visualização
    val maxHeight: Float = 1.0f,
    val animationEnabled: Boolean = true,
    val animationDuration: Int = 600,
    val gradient: Brush? = null,
    val baseline: Baseline = Baseline.Bottom
) {
    enum class Baseline {
        Top,      // Barras crescem para baixo
        Center,   // Barras crescem do centro
        Bottom    // Barras crescem para cima (padrão)
    }

    companion object {
        /**
         * Configuração minimalista - barras finas, sem animação
         */
        val Minimal = AudioWaveFormsConfig(
            barWidth = 1.dp,
            barGap = 1.dp,
            barCornerRadius = 0.dp,
            animationEnabled = false
        )

        /**
         * Configuração padrão do app
         */
        val Default = AudioWaveFormsConfig()

        /**
         * Configuração com gradiente
         */
        val Gradient = AudioWaveFormsConfig(
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF6B6B),
                    Color(0xFFFF8E53)
                )
            )
        )
    }
}
```

### Verificação
- [ ] Arquivo criado com data class e presets
- [ ] Enum Baseline definido
- [ ] Kdoc completa

---

## Fase 3: Criar AudioWaveForms Component

### O que implementar
Criar `app/src/main/java/com/chopcut/ui/components/AudioWaveForms.kt`:

```kotlin
package com.chopcut.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp

/**
 * Componente de visualização de áudio com barras verticais
 *
 * Utiliza os dados extraídos pelo AudioDataExtractor para renderizar
 * uma representação visual do áudio em forma de barras verticais.
 *
 * @param amplitudes Lista de amplitudes normalizadas (0.0 a 1.0)
 * @param modifier Modificador de layout
 * @param config Configuração visual do componente
 */
@Composable
fun AudioWaveForms(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    config: AudioWaveFormsConfig = AudioWaveFormsConfig()
) {
    if (amplitudes.isEmpty()) return

    // Animação de entrada
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(amplitudes) {
        if (config.animationEnabled) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = config.animationDuration)
            )
        } else {
            animatedProgress.snapTo(1f)
        }
    }

    // Cor das barras (usa Primary do tema se não especificado)
    val barColor = if (config.barColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        config.barColor
    }

    Canvas(
        modifier = modifier.fillMaxWidth()
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val availableWidth = size.width
        val availableHeight = size.height

        // Calcular dimensões das barras
        val totalBars = amplitudes.size
        val barSlotWidth = availableWidth / totalBars.coerceAtLeast(1)

        val actualBarWidth = config.barWidth?.toPx()
            ?: (barSlotWidth * 0.8f)  // 80% do slot se não especificado

        val gap = config.barGap?.toPx()
            ?: (barSlotWidth - actualBarWidth) / 2f  // Espaço restante dividido por 2

        // Posição Y da baseline
        val baselineY = when (config.baseline) {
            AudioWaveFormsConfig.Baseline.Top -> 0f
            AudioWaveFormsConfig.Baseline.Center -> availableHeight / 2f
            AudioWaveFormsConfig.Baseline.Bottom -> availableHeight
        }

        // Desenhar cada barra
        amplitudes.forEachIndexed { index, amplitude ->
            val x = index * barSlotWidth + (barSlotWidth - actualBarWidth) / 2

            // Normalizar amplitude com altura mínima
            val normalizedAmp = amplitude
                .coerceAtLeast(config.minHeight)
                .coerceAtMost(config.maxHeight)

            val barHeight = normalizedAmp * availableHeight * animatedProgress.value

            // Calcular posição Y baseado na baseline
            val (y, finalBarHeight) = when (config.baseline) {
                AudioWaveFormsConfig.Baseline.Top -> {
                    baselineY to barHeight
                }
                AudioWaveFormsConfig.Baseline.Center -> {
                    baselineY - barHeight / 2f to barHeight
                }
                AudioWaveFormsConfig.Baseline.Bottom -> {
                    baselineY - barHeight to barHeight
                }
            }

            // Desenhar barra
            if (config.gradient != null) {
                drawRoundRect(
                    brush = config.gradient,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(actualBarWidth, finalBarHeight),
                    cornerRadius = CornerRadius(
                        config.barCornerRadius.toPx(),
                        config.barCornerRadius.toPx()
                    )
                )
            } else {
                drawRoundRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(actualBarWidth, finalBarHeight),
                    cornerRadius = CornerRadius(
                        config.barCornerRadius.toPx(),
                        config.barCornerRadius.toPx()
                    )
                )
            }
        }
    }
}
```

### Verificação
- [ ] Componente renderiza barras verticais
- [ ] Animação funciona (e pode ser desabilitada)
- [ ] Gradiente funciona quando fornecido
- [ ] Usa cor Primary do tema por padrão
- [ ] Baseline funciona corretamente

---

## Fase 4: Criar AudioWaveFormsViewModel

### O que implementar
Criar `app/src/main/java/com/chopcut/ui/viewmodel/AudioWaveFormsViewModel.kt`:

```kotlin
package com.chopcut.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.data.audio.AudioDataExtractor
import com.chopcut.data.audio.WaveformQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para o componente AudioWaveForms
 *
 * Gerencia a extração de dados de áudio e o estado da UI
 */
@HiltViewModel
class AudioWaveFormsViewModel @Inject constructor(
    application: Application,
    private val audioDataExtractor: AudioDataExtractor
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AudioWaveFormsUiState>(AudioWaveFormsUiState.Idle)
    val uiState: StateFlow<AudioWaveFormsUiState> = _uiState.asStateFlow()

    /**
     * Carrega o waveform do vídeo
     *
     * @param uri URI do vídeo
     * @param targetBarCount Número desejado de barras (padrão: 200)
     * @param quality Qualidade para cálculo automático (se targetBarCount for null)
     */
    fun loadWaveform(
        uri: android.net.Uri,
        targetBarCount: Int? = null,
        quality: WaveformQuality = WaveformQuality.Medium
    ) {
        viewModelScope.launch {
            _uiState.value = AudioWaveFormsUiState.Loading

            try {
                val rawData = audioDataExtractor.extractRawPcmData(
                    uri = uri,
                    targetBarCount = targetBarCount ?: quality.calculateBarCount(
                        durationMs = 0,  // Será obtido do vídeo
                        screenWidthDp = 400f
                    )
                )

                if (rawData.pcmSamples.isEmpty()) {
                    _uiState.value = AudioWaveFormsUiState.Error("Nenhum dado de áudio encontrado")
                } else {
                    _uiState.value = AudioWaveFormsUiState.Ready(
                        amplitudes = rawData.pcmSamples.toList(),
                        durationMs = rawData.durationMs,
                        sampleRate = rawData.sampleRate,
                        barCount = rawData.pcmSamples.size
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AudioWaveFormsUiState.Error(e.message ?: "Erro ao carregar áudio")
            }
        }
    }

    /**
     * Reinicia o estado para Idle
     */
    fun reset() {
        _uiState.value = AudioWaveFormsUiState.Idle
    }
}

/**
 * Estados da UI do AudioWaveForms
 */
sealed class AudioWaveFormsUiState {
    /** Estado inicial */
    object Idle : AudioWaveFormsUiState()

    /** Carregando dados de áudio */
    object Loading : AudioWaveFormsUiState()

    /** Dados prontos para renderização */
    data class Ready(
        val amplitudes: List<Float>,
        val durationMs: Long,
        val sampleRate: Int,
        val barCount: Int
    ) : AudioWaveFormsUiState()

    /** Erro ao carregar */
    data class Error(val message: String) : AudioWaveFormsUiState()
}
```

### Verificação
- [ ] ViewModel criado com injeção de dependência
- [ ] Estados Idle, Loading, Ready, Error definidos
- [ ] Parâmetro `targetBarCount` suportado
- [ ] Integração com AudioDataExtractor otimizado

---

## Fase 5: Screen de Teste

### O que implementar
Criar `app/src/main/java/com/chopcut/ui/screen/debug/AudioWaveFormsTestScreen.kt`:

```kotlin
package com.chopcut.ui.screen.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chopcut.data.audio.WaveformQuality
import com.chopcut.ui.components.AudioWaveForms
import com.chopcut.ui.components.AudioWaveFormsConfig
import com.chopcut.ui.viewmodel.AudioWaveFormsViewModel
import timber.log.Timber

/**
 * Screen de teste para o componente AudioWaveForms
 */
@Composable
fun AudioWaveFormsTestScreen(
    viewModel: AudioWaveFormsViewModel = hiltViewModel(),
    testVideoUri: android.net.Uri? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Controles de teste
    var barCount by remember { mutableIntStateOf(200) }
    var showGradient by remember { mutableStateOf(false) }
    var selectedBaseline by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AudioWaveForms Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Controles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Configurações", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(8.dp))

                // Slider de barra count
                Text("Número de barras: $barCount")
                Slider(
                    value = barCount.toFloat(),
                    onValueChange = { barCount = it.toInt() },
                    valueRange = 50f..500f,
                    steps = 9
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle gradiente
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showGradient,
                        onCheckedChange = { showGradient = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usar gradiente")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selector de baseline
                Text("Baseline:")
                Row {
                    listOf("Bottom", "Center", "Top").forEachIndexed { index, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedBaseline == index,
                                onClick = { selectedBaseline = index }
                            )
                            Text(label)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Selecione um vídeo para testar")
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
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Carregando waveform...")
                    }
                }
            }

            is AudioWaveFormsUiState.Ready -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Duration: ${state.durationMs / 1000}s | Bars: ${state.barCount}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val baseline = when (selectedBaseline) {
                            0 -> AudioWaveFormsConfig.Baseline.Bottom
                            1 -> AudioWaveFormsConfig.Baseline.Center
                            else -> AudioWaveFormsConfig.Baseline.Top
                        }

                        val config = if (showGradient) {
                            AudioWaveFormsConfig.Gradient.copy(
                                baseline = baseline
                            )
                        } else {
                            AudioWaveFormsConfig.Default.copy(
                                baseline = baseline
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
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Erro: ${state.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer
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
```

### Verificação
- [ ] Screen de teste funcional
- [ ] Controles de barra count funcionam
- [ ] Toggle de gradiente funciona
- [ ] Selector de baseline funciona
- [ ] Estados renderizados corretamente

---

## Fase 6: Verificação Final

### Checklist
- [ ] AudioDataExtractor otimizado com `targetBarCount`
- [ ] AudioWaveFormsConfig criada com todos os parâmetros
- [ ] AudioWaveForms component renderiza barras corretamente
- [ ] AudioWaveFormsViewModel gerencia estados
- [ ] TestScreen funciona com vídeo real
- [ ] Número de samples minimizado (ex: 200 barras para vídeo de 60s)
- [ ] Cache funciona corretamente com nova chave
- [ ] Sem memory leaks

### Métricas de Sucesso
- **Antes**: Vídeo 60s → ~900-1500 samples (15-25 Hz)
- **Depois**: Vídeo 60s → ~200 samples (configurável via targetBarCount)
- **Redução**: ~85% menos samples

### Comandos de Verificação
```bash
# Verificar arquivos criados
ls -la app/src/main/java/com/chopcut/ui/components/AudioWaveForms*.kt
ls -la app/src/main/java/com/chopcut/ui/viewmodel/AudioWaveFormsViewModel.kt
ls -la app/src/main/java/com/chopcut/ui/screen/debug/AudioWaveFormsTestScreen.kt

# Compilar
./gradlew compileDebugKotlin

# Verificar otimização (logs devem mostrar ~200 points para 60s)
adb logcat | grep "audio_pcm_extract"
```

---

## Resumo da Estrutura

```
app/src/main/java/com/chopcut/
├── ui/
│   ├── components/
│   │   ├── AudioWaveFormsConfig.kt    [NOVO] - Configuração
│   │   └── AudioWaveForms.kt          [NOVO] - Componente principal
│   ├── viewmodel/
│   │   └── AudioWaveFormsViewModel.kt [NOVO] - ViewModel
│   └── screen/
│       └── debug/
│           └── AudioWaveFormsTestScreen.kt [NOVO] - Teste
└── data/
    └── audio/
        └── AudioDataExtractor.kt      [MODIFICAR] - Adicionar targetBarCount
```

## Parâmetros Otimizados

| Duração | Barras (Novo) | Samples (Antigo) | Redução |
|---------|---------------|------------------|---------|
| 30s     | 200           | ~450-750         | ~70%    |
| 60s     | 200           | ~900-1500        | ~85%    |
| 120s    | 200           | ~1800-3000       | ~90%    |
| 300s    | 200           | ~4500-7500       | ~95%    |
