# Plano — Feature de Compactação (re-encode com nível de qualidade)

> 📋 Plano consolidado e pronto para execução. Ancorado no código real em 2026-05-29 (pós session#15).
> ⚠️ Esta feature conecta a UI ao pipeline de re-encode existente e adiciona estimativa dinâmica, downscaling inteligente e seleção premium via Bottom Sheet.

---

## 🎯 1. Objetivo

Permitir que o usuário escolha o nível de compressão ao exportar os cortes de vídeo, trocando tamanho de arquivo por qualidade de imagem. Os perfis disponíveis serão:
1. **Original (Lossless):** Corte cirúrgico instantâneo e sem perda de qualidade, utilizando `CopyPipeline`.
2. **Média (HD - 1080p):** Re-encode com resolução limitada a 1080p e bitrate alvo de 5 Mbps, balanceando tamanho e fidelidade.
3. **Baixa (SD - 720p):** Re-encode com resolução limitada a 720p e bitrate alvo de 2.5 Mbps, ideal para compartilhamento leve (ex.: WhatsApp).

---

## 🧠 2. Algoritmos e Decisões de Engenharia

### A. Critério de Viabilidade (Anti-Upscale & Anti-Bloat)
Para evitar o desperdício de processamento, re-encode inútil ou upscale artificial de pixels (que deteriora a imagem), um nível de compressão é desabilitado caso o vídeo original já seja menor ou igual à sua especificação.
A viabilidade de um `level` para um vídeo com dimensões `(W, H)` e bitrate original `B` é definida por:

$$targetHeight = \min(level.targetHeight, H)$$
$$targetBitrate = \min(level.targetBitrateBps, B)$$

O nível é considerado **viável** apenas se:
$$\text{isViable} = (targetHeight < H) \lor (targetBitrate < B)$$

*Se o bitrate original não for detectável (igual a 0), o critério de decisão recai exclusivamente sobre a redução de resolução:* $\text{isViable} = (targetHeight < H)$.

### B. Algoritmo de Estimativa Dinâmica de Tamanho (Bytes)
A estimativa do tamanho final do arquivo baseia-se na soma das durações dos trechos cortados mantidos pelo usuário ($T_{\text{keep}}$) e no bitrate alvo, com adição de overhead fixo para a faixa de áudio (128 kbps).

1. **Original (Lossless):**
   $$Size_{\text{est}} = \frac{Size_{\text{original}} \times T_{\text{keep}}}{Duration_{\text{original}}}$$

2. **Média / Baixa (Re-encode):**
   $$Bitrate_{\text{alvo}} = \text{se } B > 0 \text{ então } \min(level.targetBitrateBps, B) \text{ senão } level.targetBitrateBps$$
   $$Bitrate_{\text{total}} = Bitrate_{\text{alvo}} + 128\,000 \text{ bps (áudio)}$$
   $$Size_{\text{est}} = \min\left( \frac{Bitrate_{\text{total}} \times T_{\text{keep}}}{8}, Size_{\text{original\_recortado}} \right)$$

### C. Downscale com Alinhamento de Hardware (Even-Dimensions Clamp)
Muitos encoders de hardware de celulares Android falham criticamente ao codificar vídeos com dimensões ímpares. O algoritmo de cálculo de dimensões de saída realiza o clamp para múltiplos de 2:

```kotlin
val targetH = Math.min(compressionLevel.targetHeight, originalHeight)
val targetW = (originalWidth * targetH) / originalHeight

// Clampar para múltiplos de 2 (par)
val evenHeight = (targetH / 2) * 2
val evenWidth = (targetW / 2) * 2
```

Se um `aspectRatio` de corte estiver ativo, o cálculo da largura adaptada ao crop é:
```kotlin
val croppedW = (evenHeight * aspectRatio).toInt()
val evenCroppedWidth = (croppedW / 2) * 2
```

### D. Codec de Saída Universal (H.264)
Para garantir compatibilidade absoluta de reprodução (inclusive no visualizador do WhatsApp e navegadores), o pipeline de re-encode forçará a exportação para o codec **H.264/AVC** via:
`transformerBuilder.setVideoMimeType("video/avc")`

---

## 🏗️ 3. Mapeamento de Símbolos & Arquitetura (Sem arquivos novos)

Esta feature será implementada estritamente dentro da estrutura atual de 14 arquivos do package `com.chopcut`, sem adicionar nenhum import interno:

| Peça | Onde mora | Propósito / Modificações |
|---|---|---|
| **`CompressionLevel`** | `CompressionLevel.kt` | Adicionar parâmetros `targetHeight` e `targetBitrateBps`, além do método `isViable()`. |
| **`FormatUtils`** | `core/Utils.kt` | Adicionar a função pura `estimateExportSize()` seguindo os algoritmos matemáticos detalhados. |
| **`TransformerPipeline`** | `data/VideoEngine.kt` | Atualizar o método `trim()` para receber as resoluções e bitrates dinâmicos, aplicar o redimensionamento de hardware (`even` dimensions) e forçar o codec `video/avc`. |
| **`TimelineViewModel`** | `ui/editor/TimelineFeature.kt` | Armazenar o estado da compressão selecionada, metadados detalhados de bitrate e tamanho do vídeo, expor a porcentagem real de progresso do `Transformer` no `ExportUiState` e realizar o roteamento `CopyPipeline` vs `TransformerPipeline` em `exportCuts()`. |
| **`TimelineScreen`** | `ui/editor/TimelineFeature.kt` | Roteamento visual da exportação: substituir o diálogo simples de confirmação por um **ModalBottomSheet** Material3 brutalista premium contendo as opções de compressão, estimativas em tempo real e badges de viabilidade. |

---

## 📝 4. Passo a Passo Detalhado (Blueprints de Código)

### Passo 1: Atualização de [CompressionLevel.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/CompressionLevel.kt)
Configuração estendida com os metadados físicos dos perfis:
```kotlin
package com.chopcut

enum class CompressionLevel(
    val label: String,
    val description: String,
    val targetHeight: Int,
    val targetBitrateBps: Long
) {
    ORIGINAL("Original", "Máxima qualidade, arquivo maior", -1, -1L),
    MEDIUM("Alta Compressão (HD)", "Equilíbrio entre qualidade e tamanho", 1080, 5_000_000L),
    LOW("WhatsApp / Leve (SD)", "Ideal para envio rápido", 720, 2_500_000L);

    fun isViable(originalWidth: Int, originalHeight: Int, originalBitrateBps: Long): Boolean {
        if (this == ORIGINAL) return true
        val targetH = Math.min(targetHeight, originalHeight)
        if (originalBitrateBps <= 0L) {
            // Se o bitrate não for legível, decide apenas pela resolução
            return targetH < originalHeight
        }
        val targetBitrate = Math.min(targetBitrateBps, originalBitrateBps)
        return targetH < originalHeight || targetBitrate < originalBitrateBps
    }
}
```

### Passo 2: Implementação de Estimativa em [Utils.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/core/Utils.kt)
Adicionar no singleton `FormatUtils`:
```kotlin
    fun estimateExportSize(
        level: CompressionLevel,
        keepRanges: List<TimeRange>,
        originalDurationUs: Long,
        originalSizeBytes: Long,
        originalWidth: Int,
        originalHeight: Int,
        originalBitrateBps: Long
    ): Long {
        val totalKeepDurationMs = keepRanges.sumOf { it.endMs - it.startMs }
        val originalDurationMs = originalDurationUs / 1000L
        if (originalDurationMs <= 0L) return 0L

        // Tamanho proporcional aproximado do original
        val originalProportionalSize = (originalSizeBytes * totalKeepDurationMs) / originalDurationMs

        if (level == CompressionLevel.ORIGINAL) {
            return originalProportionalSize
        }

        // Estimativa para re-encode: (bitrate + 128kbps áudio) * duração
        val targetBitrate = if (originalBitrateBps > 0L) {
            Math.min(level.targetBitrateBps, originalBitrateBps)
        } else {
            level.targetBitrateBps
        }
        
        val totalBitrateBps = targetBitrate + 128_000L
        val durationSeconds = totalKeepDurationMs / 1000.0
        val estimatedBytes = (totalBitrateBps * durationSeconds / 8.0).toLong()

        // Garante que a compressão não estime valores bizarros acima do original proporcional
        return Math.min(estimatedBytes, originalProportionalSize).coerceAtLeast(1024L)
    }
```

### Passo 3: Adaptação de `TransformerPipeline.trim` em [VideoEngine.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/data/VideoEngine.kt)
Modificar a configuração de redimensionamento e bitrate dinâmico na inicialização:
```kotlin
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun trim(
        uri: Uri, 
        ranges: List<TimeRange>, 
        aspectRatio: Float? = null, 
        compressionLevel: CompressionLevel = CompressionLevel.ORIGINAL
    ): Flow<TrimProgress> = callbackFlow {
        val outputFile = videoRepository.createTempFile(".mp4")
        
        var isFinished = false
        var transformerRef: Transformer? = null
        val progressHolder = ProgressHolder()

        try {
            val metadata = videoRepository.getMetadata(uri)
                ?: throw IllegalArgumentException("Unable to read video metadata")

            val sequenceBuilder = EditedMediaItemSequence.Builder()

            ranges.forEach { range ->
                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(clippingConfig)
                    .build()

                val videoEffects = mutableListOf<Effect>()
                
                // Downscale dinâmico com clamp par (requisito de hardware de encoders móveis)
                if (compressionLevel != CompressionLevel.ORIGINAL) {
                    val originalW = metadata.width
                    val originalH = metadata.height
                    
                    val targetH = Math.min(compressionLevel.targetHeight, originalH)
                    val targetW = (originalW * targetH) / originalH
                    
                    val evenHeight = (targetH / 2) * 2
                    val evenWidth = (targetW / 2) * 2
                    
                    if (aspectRatio != null) {
                        val croppedWidth = (evenHeight * aspectRatio).toInt()
                        val evenCroppedWidth = (croppedWidth / 2) * 2
                        videoEffects.add(Presentation.createForWidthAndHeight(
                            evenCroppedWidth,
                            evenHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        ))
                    } else {
                        videoEffects.add(Presentation.createForWidthAndHeight(
                            evenWidth,
                            evenHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT
                        ))
                    }
                } else if (aspectRatio != null) {
                    videoEffects.add(Presentation.createForAspectRatio(aspectRatio, Presentation.LAYOUT_SCALE_TO_FIT))
                }

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
                    .build()

                sequenceBuilder.addItem(editedItem)
            }

            val sequence = sequenceBuilder.build()
            val composition = Composition.Builder(sequence).build()
            val mainHandler = Handler(Looper.getMainLooper())

            val progressRunnable = object : Runnable {
                override fun run() {
                    if (isFinished) return
                    val transformer = transformerRef ?: return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(TrimProgress.InProgress(progressHolder.progress))
                    }
                    mainHandler.postDelayed(this, 250)
                }
            }

            val transformerListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    isFinished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    trySend(TrimProgress.Completed(outputFile))
                    channel.close()
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    isFinished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    trySend(TrimProgress.Failed(exception))
                    channel.close()
                }
            }

            trySend(TrimProgress.InProgress(0))

            mainHandler.post {
                try {
                    val transformerBuilder = Transformer.Builder(context)
                        .addListener(transformerListener)
                        .setVideoMimeType("video/avc") // H.264 universal

                    if (compressionLevel != CompressionLevel.ORIGINAL) {
                        val targetBitrate = if (metadata.bitrate > 0) {
                            Math.min(compressionLevel.targetBitrateBps, metadata.bitrate).toInt()
                        } else {
                            compressionLevel.targetBitrateBps.toInt()
                        }
                        
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(targetBitrate)
                                    .build()
                            )
                            .build()
                            
                        transformerBuilder.setEncoderFactory(encoderFactory)
                    }

                    val transformer = transformerBuilder.build()
                    transformerRef = transformer
                    transformer.start(composition, outputFile.absolutePath)
                    mainHandler.postDelayed(progressRunnable, 250)
                } catch (e: Exception) {
                    trySend(TrimProgress.Failed(e))
                    channel.close()
                }
            }

            awaitClose {
                isFinished = true
                mainHandler.removeCallbacks(progressRunnable)
                mainHandler.post { transformerRef?.cancel() }
            }
        } catch (e: Exception) {
            trySend(TrimProgress.Failed(e))
            channel.close()
            if (outputFile.exists()) outputFile.delete()
        }
    }
```

### Passo 4: Atualização dos Estados e Roteamento em `TimelineViewModel` de [TimelineFeature.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/ui/editor/TimelineFeature.kt)
Primeiramente, enriquecemos o estado `ExportUiState` para comportar a porcentagem real de exportação:
```kotlin
sealed class ExportUiState {
    data object Idle : ExportUiState()
    data class Exporting(val progress: Int = -1) : ExportUiState() // -1 = indeterminado (Copy)
    data class Success(val shareUri: Uri) : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}
```

Expor os dados de bitrate e bytes físicos do vídeo original na inicialização:
```kotlin
    private val _videoBitrate = MutableStateFlow(0L)
    val videoBitrate: StateFlow<Long> = _videoBitrate.asStateFlow()

    private val _videoSizeBytes = MutableStateFlow(0L)
    val videoSizeBytes: StateFlow<Long> = _videoSizeBytes.asStateFlow()
    
    // No bloco `init` de `TimelineViewModel`, carregar os campos junto com os metadados:
    _videoBitrate.value = info.bitrate
    _videoSizeBytes.value = info.sizeBytes
```

Adaptar o fluxo `exportCuts()` para aceitar o enum do usuário e rotear os pipelines:
```kotlin
    fun exportCuts(level: CompressionLevel = CompressionLevel.ORIGINAL) {
        val uri = videoUri ?: return
        val intervals = _markerIntervals.value
        if (intervals.isEmpty()) return

        val trimPairs = intervals.map { it.startMs to it.endMs }
        val keepRanges = RangeUtils.calculateKeepRanges(trimPairs, _durationMs.value)
        if (keepRanges.isEmpty()) {
            _exportState.value = ExportUiState.Error("Os cortes cobrem o vídeo inteiro — não sobrou nada para exportar.")
            return
        }

        _exportState.value = ExportUiState.Exporting(-1)
        viewModelScope.launch {
            if (level == CompressionLevel.ORIGINAL) {
                // Rota rápida por cópia sem perda de qualidade (lossless copy)
                val pipeline = CopyPipeline(getApplication<Application>(), videoRepository)
                pipeline.trim(uri, keepRanges).collect { result ->
                    result.fold(
                        onSuccess = { file -> saveAndFinishExport(file, uri) },
                        onFailure = { e ->
                            _exportState.value = ExportUiState.Error(
                                (e as? ChopCutException)?.userMessage ?: e.message ?: "Falha ao recortar o vídeo"
                            )
                        }
                    )
                }
            } else {
                // Rota de re-encode com compressão e redimensionamento dinâmico
                val pipeline = TransformerPipeline(getApplication<Application>(), videoRepository)
                pipeline.trim(uri, keepRanges, _videoAr.value, level).collect { progress ->
                    when (progress) {
                        is TrimProgress.InProgress -> {
                            _exportState.value = ExportUiState.Exporting(progress.percent)
                        }
                        is TrimProgress.Completed -> {
                            saveAndFinishExport(progress.file, uri)
                        }
                        is TrimProgress.Failed -> {
                            _exportState.value = ExportUiState.Error(
                                (progress.error as? ChopCutException)?.userMessage ?: progress.error.message ?: "Falha ao compactar o vídeo"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinishExport(file: File, uri: Uri) {
        try {
            val baseName = FileNameUtils.extractBaseNameFromUri(uri)
            val stamp = java.text.SimpleDateFormat("mmss", java.util.Locale.US)
                .format(java.util.Date())
            videoRepository.saveToGallery(file, "${baseName}_chopcut_$stamp.mp4")
            val shareUri = FileProvider.getUriForFile(
                getApplication<Application>(),
                "com.chopcut.fileprovider",
                file
            )
            _exportState.value = ExportUiState.Success(shareUri)
        } catch (e: Exception) {
            _exportState.value = ExportUiState.Error(e.message ?: "Falha ao salvar o vídeo")
        }
    }
```

### Passo 5: Bottom Sheet de Escolha de Qualidade na UI de [TimelineFeature.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/ui/editor/TimelineFeature.kt)
Roteamento visual: substituir o diálogo de confirmação antigo por um Bottom Sheet de alta fidelidade:

```kotlin
    val videoBitrate by viewModel.videoBitrate.collectAsStateWithLifecycle()
    val videoSizeBytes by viewModel.videoSizeBytes.collectAsStateWithLifecycle()
    var showExportBottomSheet by remember { mutableStateOf(false) }

    // No Scaffold TopAppBar actions, mudar de `showConfirmationDialog = true` para:
    showExportBottomSheet = true
```

#### Estrutura do ModalBottomSheet Composable:
```kotlin
    if (showExportBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportBottomSheet = false },
            containerColor = Color(0xFF121212),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Opções de Exportação",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecione o nível de compressão do vídeo recortado",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(24.dp))

                val levels = CompressionLevel.values()
                var selectedLevel by remember { mutableStateOf(CompressionLevel.ORIGINAL) }
                
                val trimPairs = markerIntervals.map { it.startMs to it.endMs }
                val keepRanges = RangeUtils.calculateKeepRanges(trimPairs, durationMs)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    levels.forEach { level ->
                        val isViable = level.isViable(videoWidth, videoHeight, videoBitrate)
                        val estimatedSize = FormatUtils.estimateExportSize(
                            level = level,
                            keepRanges = keepRanges,
                            originalDurationUs = durationMs * 1000L,
                            originalSizeBytes = videoSizeBytes,
                            originalWidth = videoWidth,
                            originalHeight = videoHeight,
                            originalBitrateBps = videoBitrate
                        )
                        
                        val sizeString = FormatUtils.formatFileSize(estimatedSize)
                        val targetResString = if (level != CompressionLevel.ORIGINAL) {
                            val targetH = Math.min(level.targetHeight, videoHeight)
                            val targetW = (videoWidth * targetH) / videoHeight
                            val evenW = (targetW / 2) * 2
                            val evenH = (targetH / 2) * 2
                            "Resolução de saída: ${evenW}x${evenH} (~${level.targetBitrateBps / 1_000_000} Mbps)"
                        } else ""

                        CompressionOptionCard(
                            level = level,
                            isSelected = selectedLevel == level,
                            isViable = isViable,
                            estimatedSize = sizeString,
                            targetResString = targetResString,
                            onClick = { selectedLevel = level }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Botão brutalista de exportação disparada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E5FF))
                        .clickable {
                            showExportBottomSheet = false
                            viewModel.setPreviewMode(false)
                            viewModel.exportCuts(selectedLevel)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "EXPORTAR VÍDEO",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = { showExportBottomSheet = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
```

E mudamos o visualizador de progresso de exportação para mostrar a porcentagem quando disponível:
```kotlin
        ExportUiState.Exporting(val pct) -> {
            Dialog(onDismissRequest = { }) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.height(16.dp))
                        val labelText = if (pct >= 0) "Processando ($pct%)…" else "Recortando…"
                        Text(
                            text = labelText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
```

---

## 🧪 5. Plano de Validação & Testes

Para garantir a total robustez da feature nos ambientes reais, seguiremos o protocolo de validação rigorosa:

### A. Testes Unitários de Estimativa e Viabilidade
1. Validar `CompressionLevel.isViable` com mock de metadados:
   - Entrada: 4k (3840x2160 @ 20 Mbps) -> `MEDIUM` e `LOW` devem ser `true`.
   - Entrada: 720p (1280x720 @ 1.5 Mbps) -> `MEDIUM` deve ser `false`; `LOW` deve ser `false` (bitrate/resolução inferiores).
2. Validar `FormatUtils.estimateExportSize` para assegurar que a estimativa da cópia original ($T_{\text{keep}}$ de metade de um arquivo de 100MB resulte em ~50MB) e os tamanhos compactados sejam inferiores e coerentes.

### B. Validação do Build Completo
Garantir JDK 17 e compilação limpa sem quebras de imports internos:
```bash
JAVA_HOME=./jdk17 ./gradlew assembleDebug
```

### C. Validação Visual & Funcional em Device Físico (`/rodar-app` + adb)
Fazer o deploy no Galaxy A15 e exportar um vídeo real recortado nas 3 opções:
1. **Teste Original (Lossless):** Tempo instantâneo, ffprobe deve reportar codec intacto (HEVC ou H.264 original).
2. **Teste Média (HD):** Deve exibir progresso incremental na UI (0% a 100%).
3. **Teste Baixa (SD):** Deve concluir com sucesso e salvar em `Movies/ChopCut`.

### D. Inspeção do arquivo de saída por FFProbe
Extrair o arquivo e executar no host:
```bash
ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,bit_rate -of default=noprint_wrappers=1 <video_exportado.mp4>
```
- Verificar se `codec_name` é `h264`.
- Verificar se `width` e `height` são múltiplos de 2 pares (ex: 1280x720).
- Verificar se o bitrate está clampado conforme o perfil de qualidade escolhido.

---

## 🛡️ 6. Refinamentos do `/desafiar-plano` (decisões de fechamento)

Pontos estressados contra as regras do projeto após o blueprint:

### A. Paleta do Bottom Sheet → **seguir o `Theme.kt`** ✅ DECIDIDO
Os snippets do Passo 5 hardcodam `Color(0xFF00E5FF)` (ciano), `Color(0xFF121212)`, `Color(0xFF1E1E1E)` — cores que **não existem** no app. O `core/Theme.kt` já define o design system (`Primary = 0xFF6366F1` indigo, `Background = 0xFF171717`, `Surface = 0xFF262626`).
- **Ação:** ao implementar, trocar todos os hex hardcoded por `MaterialTheme.colorScheme` (botão = `primary`, container = `surface`, scrim/fundo = `background`). Mantém o layout/estrutura do blueprint, zero cor fora do tema. Consistência com o resto do editor.

### B. Onde rodam os testes unitários → `testDebugUnitTest` ✅ ESCLARECIDO
Confirmado que **existe `app/src/test/`** (JVM puro, com `data/ui/util`). Logo os testes de `CompressionLevel.isViable` e `FormatUtils.estimateExportSize` (Seção 5.A) são **unitários JVM**:
```bash
JAVA_HOME=./jdk17 ./gradlew testDebugUnitTest
```
- **Não** dependem de device nem de `make test` / `connectedAndroidTest` — portanto **não** são bloqueados pelo `FastFrameExtractorTest.kt` órfão (known issue da session#15, que só quebra os instrumentados). A validação em device (5.C/5.D) continua manual via `/rodar-app` + `ffprobe`.

### C. Edge case do `isViable` (viável por resolução, sem ganho de tamanho) 🟡 ACEITO COM RESSALVA
Cenário raro: original `1080p @ 1 Mbps` (já muito comprimido). `LOW` é considerado viável por reduzir a resolução (720p), mas o bitrate é clampado em 1 Mbps → o arquivo **não encolhe**, só perde nitidez por re-encode.
- **Decisão:** aceitar por ora (a estimativa já clampa em `min(estimado, originalProporcional)`, então a UI nunca promete arquivo maior). *Se incomodar na validação*, refinar o critério para exigir ganho de tamanho real (ex.: `estimado < originalProporcional * 0.9`) em vez de só `resAlvo < originalRes`.

### D. Pontos menores (implementação, não travam o plano)
- **Memoização na UI:** `RangeUtils.calculateKeepRanges` + `estimateExportSize` rodam dentro do `levels.forEach` do sheet → recalculam a cada recomposição. Envolver em `remember(markerIntervals, durationMs)`. (Não é draw scope; padrões de Canvas não se aplicam.)
- **Áudio:** a estimativa assume 128 kbps fixos; o re-encode pode transcodar o áudio real para valor diferente. Aproximação aceitável para a estimativa.
