# Performance Tests - ChopCut

Suite completa de testes de performance para operações de vídeo e extração de thumbnails.

## 📋 Estrutura

```
performance/
├── PerformanceMetrics.kt              # Classes para coleta de métricas
├── PerformanceReporter.kt             # Gerador de relatórios (JSON, MD, CSV)
├── TestVideoProvider.kt               # Provider de vídeos de teste
├── ThumbnailExtractionPerformanceTest.kt  # Testes de thumbnails
├── VideoOperationsPerformanceTest.kt      # Testes de operações de vídeo
├── PerformanceTestSuite.kt            # Suite completa de testes
└── README.md                          # Este arquivo
```

## 🚀 Como Executar

### Via Android Studio

1. Conecte um dispositivo ou inicie um emulador
2. Navegue até `app/src/androidTest/java/com/chopcut/performance/`
3. Clique direito na classe de teste desejada
4. Selecione "Run 'NomeDoTest'"

### Via Linha de Comando

```bash
# Executar todos os testes de performance
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.PerformanceTestSuite

# Executar teste específico
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.ThumbnailExtractionPerformanceTest

# Stress test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.PerformanceTestSuite#runStressTest
```

### Via rtk (otimizado)

```bash
# Executar suite completa
rtk ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.PerformanceTestSuite
```

## 📊 Testes Disponíveis

### 1. ThumbnailExtractionPerformanceTest

Testa extração de thumbnails com diferentes configurações:

- **testSingleThumbnailExtraction**: Extração de thumbnails únicos em diferentes posições
- **testBatchThumbnailExtraction**: Extração em batch com diferentes quantidades
- **testThumbnailExtractionComparison**: Compara método individual vs batch
- **testDifferentThumbnailSizes**: Testa diferentes resoluções (160x90 até 1280x720)
- **testStressTest**: Extrai 100 thumbnails em batch

### 2. VideoOperationsPerformanceTest

Testa operações de vídeo:

- **testMetadataExtraction**: Extração de metadados de vídeo
- **testAudioMetadataExtraction**: Extração de metadados de áudio
- **testCopyToTempFile**: Cópia de vídeo para arquivo temporário
- **testFileOperations**: Operações de criação de arquivos
- **testCompleteWorkflow**: Workflow completo de processamento

### 3. PerformanceTestSuite

Suite completa que executa todos os testes de forma integrada:

- **runCompletePerformanceTestSuite**: Executa bateria completa de testes
- **runStressTest**: Teste de estresse com 200 thumbnails

## 📈 Relatórios Gerados

Os relatórios são salvos em `/storage/emulated/0/Android/data/com.chopcut/files/performance_reports/`

### Formatos:

1. **JSON** (`{testName}_{timestamp}.json`)
   - Dados estruturados para processamento
   - Inclui informações do dispositivo
   - Métricas detalhadas por operação

2. **Markdown** (`{testName}_{timestamp}.md`)
   - Relatório legível para humanos
   - Tabelas formatadas
   - Gráfico ASCII de barras

3. **CSV** (`{testName}_{timestamp}.csv`)
   - Importável para Excel/Google Sheets
   - Análise de dados em planilhas

### Exemplo de Relatório Markdown:

```markdown
# Performance Report: CompletePerformanceTestSuite

**Timestamp:** 2026-02-28_14-30-45

## Device Information

| Property | Value |
|----------|-------|
| Manufacturer | Google |
| Model | Pixel 6 |
| Android Version | 13 |
| SDK Level | 33 |

## Summary Statistics

| Metric | Value |
|--------|-------|
| Total Operations | 50 |
| Successful | 48 |
| Failed | 2 |
| Total Duration | 12500ms (12.50s) |
| Average Duration | 250.00ms |
| Min Duration | 45ms |
| Max Duration | 1200ms |

## Detailed Metrics

| Operation | Duration (ms) | Memory (KB) | Success | Metadata |
|-----------|---------------|-------------|---------|----------|
| Extract Single Thumbnail | 245 | 1024 | ✅ | position=0, width=320, height=180 |
| Batch Extraction (10 thumbnails) | 980 | 5120 | ✅ | batchSize=10 |
...
```

## 📱 Acessando Relatórios

### Via ADB

```bash
# Listar relatórios
adb shell ls /storage/emulated/0/Android/data/com.chopcut/files/performance_reports/

# Baixar relatório específico
adb pull /storage/emulated/0/Android/data/com.chopcut/files/performance_reports/CompletePerformanceTestSuite_2026-02-28_14-30-45.md ./
```

### Via Device File Explorer (Android Studio)

1. View → Tool Windows → Device File Explorer
2. Navegue até `/storage/emulated/0/Android/data/com.chopcut/files/performance_reports/`
3. Clique direito no arquivo → Save As...

## 🎯 Métricas Coletadas

Cada teste coleta as seguintes métricas:

- **Duration (ms)**: Tempo de execução da operação
- **Memory (KB)**: Memória alocada durante a operação
- **Success**: Se a operação foi bem-sucedida
- **Metadata**: Informações adicionais (posição, tamanho, etc.)

## 🔧 Configuração

### Vídeos de Teste

O `TestVideoProvider` tenta usar vídeos existentes no dispositivo. Se não houver, cria vídeos sintéticos.

Para usar vídeo específico:

```kotlin
// Em vez de usar getSampleVideoUri()
val videoUri = Uri.parse("content://...")
```

### Customizar Testes

```kotlin
@Test
fun testCustomScenario() = runBlocking {
    val videoUri = testVideoProvider.createTestVideo(
        durationSeconds = 20,  // Duração desejada
        width = 1920,
        height = 1080,
        frameRate = 30
    )

    performanceMeasurer.measure(
        operationName = "My Custom Test",
        metadata = mapOf("customParam" to "value")
    ) {
        // Sua operação aqui
    }
}
```

## 📊 Análise de Resultados

### Comparação Individual vs Batch

Espera-se que extração em batch seja **3-5x mais rápida** que individual:

- **Individual (10 thumbnails)**: ~3000-5000ms
- **Batch (10 thumbnails)**: ~600-1500ms

### Impacto da Resolução

Tempo de extração aumenta com a resolução:

- **160x90**: ~50-100ms
- **320x180**: ~100-200ms
- **640x360**: ~200-400ms
- **1280x720**: ~400-800ms

## 🐛 Troubleshooting

### Erro: "No video found"

- Conecte o dispositivo e coloque um vídeo na galeria
- Ou deixe o teste criar um vídeo sintético (pode demorar)

### Erro: "Permission denied"

- Verifique permissões de storage no AndroidManifest.xml
- Execute `adb shell pm grant com.chopcut android.permission.WRITE_EXTERNAL_STORAGE`

### Testes muito lentos

- Use vídeos mais curtos
- Reduza quantidade de thumbnails nos testes
- Execute em dispositivo real (emulador é mais lento)

## 📝 Notas

- Testes instrumentados rodam no dispositivo/emulador real
- Primeira execução pode ser mais lenta (criação de vídeos de teste)
- Resultados variam por dispositivo (CPU, RAM, storage)
- Sempre execute em modo release para métricas realistas

## 🎓 Referências

- [Android Testing Guide](https://developer.android.com/training/testing)
- [Jetpack Benchmark](https://developer.android.com/topic/performance/benchmarking)
- [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
