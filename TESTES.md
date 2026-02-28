# 🎯 Testes de Performance - Guia Completo

## ⚡ Quick Start

```bash
# 1. Rodar testes
./gradlew connectedDebugAndroidTest

# 2. Ver resultados (HTML - mais fácil)
open app/build/reports/androidTests/connected/debug/index.html

# 3. OU ver estatísticas no terminal
adb logcat -d -s "System.out:I" | grep -A 15 "PERFORMANCE STATISTICS"
```

---

## 📊 O Que é Testado

Para cada vídeo:
- ✅ **Metadados** (resolução, duração, codec)
- ✅ **5 thumbnails** em diferentes posições
- ✅ **Batch de 10 thumbnails** (extração eficiente)
- ✅ **3 resoluções** (160x90, 320x180, 640x360)

**Métricas coletadas:**
- ⏱️ Tempo de execução (ms)
- 💾 Uso de memória (KB)
- ✅ Sucesso/falha

---

## 🎬 Usar Seus Próprios Vídeos

### Adicionar vídeos
```bash
# Colocar vídeos na pasta
adb push seu_video.mp4 /sdcard/Movies/ChopCut/

# Verificar
adb shell ls /sdcard/Movies/ChopCut/
```

### Rodar testes
```bash
./gradlew connectedDebugAndroidTest
```

**Sem vídeo?** Os testes criam um vídeo sintético automaticamente (1920x1080, 30s).

---

## 📈 Ver Resultados

### Opção 1: Relatório HTML (👈 RECOMENDADO)
```bash
open app/build/reports/androidTests/connected/debug/index.html
```

Mostra:
- Lista de todos os testes
- Tempo de execução
- Status (passou/falhou)
- Detalhes por classe

### Opção 2: Relatório Detalhado por Vídeo (Terminal)
```bash
adb logcat -d -s "System.out:I" | grep -A 50 "DETAILED RESULTS"
```

Exemplo de saída:
```
╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔
║ VIDEO: meu_video.mp4
╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠
║ 📐 Resolution: 1920x1080
║ ⏱️  Duration: 30.5s
║ 📦 Size: 15.23MB
║ 🔄 Rotation: 0°
╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚

╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔
║ DETAILED RESULTS - meu_video.mp4
╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠

📋 METADATA EXTRACTION
────────────────────────────────────────────────────────────
  Video Metadata Extraction      18ms
  Audio Metadata Extraction      15ms

📸 SINGLE THUMBNAIL EXTRACTION
────────────────────────────────────────────────────────────
  Position 0         ms  →  28ms
  Position 7625      ms  →  27ms
  Position 15250     ms  →  29ms
  Position 22875     ms  →  28ms
  Position 29500     ms  →  27ms
  Average                →  27.8ms

🎬 BATCH EXTRACTION
────────────────────────────────────────────────────────────
  10 thumbnails  →  98ms total  (9.8ms/thumb)

🎨 RESOLUTION COMPARISON
────────────────────────────────────────────────────────────
  160x90       (Low)     →  22ms
  320x180      (Medium)  →  27ms
  640x360      (High)    →  45ms

╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚

╔═════════════════════════════════════════════════════════════
║ PERFORMANCE ANALYSIS
╠═════════════════════════════════════════════════════════════
║ Batch vs Individual:
║   • Individual: 27.8ms per thumbnail
║   • Batch (10): 9.8ms per thumbnail
║   • Improvement: 2.8x faster ⚡
║
║ Throughput: 68.4 operations/second
╚═════════════════════════════════════════════════════════════
```

### Opção 3: Comparação entre Vídeos
Quando você testa múltiplos vídeos, o relatório inclui uma comparação:
```
╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔╔
║ VIDEO COMPARISON SUMMARY
╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠╠

📊 Performance by Video:
────────────────────────────────────────────────────────────
1. video_720p.mp4
   Resolution: 1280x720  |  Duration: 45.2s
   Avg operation: 62.3ms  |  Total: 1184ms

2. video_1080p.mp4
   Resolution: 1920x1080  |  Duration: 30.5s
   Avg operation: 68.7ms  |  Total: 1305ms

3. video_4k.mp4
   Resolution: 3840x2160  |  Duration: 15.0s
   Avg operation: 125.4ms  |  Total: 2383ms

🏆 Performance Winner:
   Fastest: video_720p.mp4 (62.3ms avg)
   Slowest: video_4k.mp4 (125.4ms avg)
   Difference: 2.0x

╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚╚
```

### Opção 4: Extrair JSON do Logcat
```bash
# Extrair relatórios JSON
./scripts/extract_performance_json.sh

# Vai criar: ./performance_reports/combined_report.json
```

---

## 🔍 Como Interpretar os Relatórios

### Por Vídeo

Cada vídeo testado gera um relatório com:

1. **Informações do Vídeo**
   - Resolução, duração, tamanho, rotação

2. **Metadata Extraction** (📋)
   - Tempo para extrair informações do vídeo/áudio
   - **Esperado**: 15-30ms

3. **Single Thumbnail** (📸)
   - Tempo para extrair cada thumbnail individualmente
   - Mostra 5 posições diferentes + média
   - **Esperado**: 25-35ms por thumbnail

4. **Batch Extraction** (🎬)
   - Tempo para extrair 10 thumbnails de uma vez
   - Mostra tempo total e por thumbnail
   - **Esperado**: 80-120ms total (~8-12ms/thumb)
   - **Deve ser 2-3x mais rápido que individual!**

5. **Resolution Comparison** (🎨)
   - Compara 3 resoluções: Low, Medium, High
   - **Esperado**: Aumenta com a resolução
   - Low: ~20-30ms
   - Medium: ~25-35ms
   - High: ~40-60ms

6. **Performance Analysis**
   - Compara batch vs individual
   - Mostra throughput (operações/segundo)
   - **Meta**: Batch deve ser >2x mais rápido

### Entre Vídeos

Quando testa múltiplos vídeos:
- Compara performance média de cada vídeo
- Identifica o mais rápido/lento
- **Normal**: Vídeos maiores/4K são mais lentos

---

## 🎯 Exemplos de Uso

### Testar um vídeo específico
```bash
adb push meu_video.mp4 /sdcard/Movies/ChopCut/
./gradlew connectedDebugAndroidTest
open app/build/reports/androidTests/connected/debug/index.html
```

### Testar vários vídeos
```bash
# Adicionar vários vídeos
adb push video1.mp4 /sdcard/Movies/ChopCut/
adb push video2.mp4 /sdcard/Movies/ChopCut/
adb push video3.mp4 /sdcard/Movies/ChopCut/

# Rodar testes (testa TODOS automaticamente)
./gradlew connectedDebugAndroidTest

# Ver resultados
open app/build/reports/androidTests/connected/debug/index.html
```

### Comparar performance antes/depois de mudanças
```bash
# Antes
./gradlew connectedDebugAndroidTest
./scripts/extract_performance_json.sh
mv performance_reports/combined_report.json before.json

# Fazer mudanças no código...

# Depois
./gradlew connectedDebugAndroidTest
./scripts/extract_performance_json.sh
mv performance_reports/combined_report.json after.json

# Comparar
diff before.json after.json
```

---

## 📁 Estrutura de Arquivos

### Testes criados
```
app/src/androidTest/java/com/chopcut/performance/
├── PerformanceMetrics.kt              # Sistema de métricas
├── PerformanceReporter.kt             # Gerador de relatórios
├── TestVideoProvider.kt               # Provider de vídeos
├── MultiVideoPerformanceTest.kt       # Testa todos os vídeos
├── PerformanceTestSuite.kt            # Suite completa
├── ThumbnailExtractionPerformanceTest.kt
└── VideoOperationsPerformanceTest.kt
```

### Scripts úteis
```
scripts/
├── extract_performance_json.sh        # Extrair JSON do logcat
├── view_performance_reports.sh        # Ver relatórios
└── compare_performance_reports.sh     # Comparar 2 relatórios
```

---

## ⚠️ Problemas Comuns

### "Permission denied"
```bash
adb shell pm grant com.chopcut android.permission.READ_EXTERNAL_STORAGE
```

### "No videos found"
Normal! O teste cria um vídeo sintético automaticamente.

### Relatórios não aparecem em arquivos
**Esperado!** Por questões de permissão do Android 16, os relatórios não são salvos em arquivos.

**Use:**
- Relatório HTML: `app/build/reports/androidTests/connected/debug/index.html`
- Ou extraia do logcat: `./scripts/extract_performance_json.sh`

### Testes falhando
```bash
# Ver detalhes do erro
open app/build/reports/androidTests/connected/debug/index.html

# Ou verificar logcat
adb logcat -d | grep -i "error\|exception"
```

---

## 📊 Resultados Esperados

### Operações rápidas
- Video Metadata: **~18ms**
- Audio Metadata: **~15ms**
- Single Thumbnail: **~27-33ms**

### Batch extraction
- 10 thumbnails: **~982ms** (~98ms/thumbnail)
- 50 thumbnails: **~4289ms** (~86ms/thumbnail)
- 200 thumbnails: **~1162ms** (~6ms/thumbnail) ⚡

**Observação:** Batch é 3-5x mais eficiente que extração individual!

---

## 🎓 Dicas

1. **Use o relatório HTML** - é a forma mais fácil de ver os resultados
2. **Teste com vídeos reais** do seu app para resultados precisos
3. **Rode em dispositivo real** - emulador é mais lento
4. **Compare antes/depois** de otimizações
5. **Use batch extraction** sempre que possível

---

## 🚀 Comandos Úteis

```bash
# Rodar todos os testes
./gradlew connectedDebugAndroidTest

# Ver resultados HTML
open app/build/reports/androidTests/connected/debug/index.html

# Extrair JSONs
./scripts/extract_performance_json.sh

# Ver estatísticas
adb logcat -d -s "System.out:I" | grep -A 15 "PERFORMANCE STATISTICS"

# Listar vídeos de teste
adb shell ls /sdcard/Movies/ChopCut/

# Adicionar vídeo
adb push video.mp4 /sdcard/Movies/ChopCut/
```

---

## 📚 Referências

- [Documentação técnica](app/src/androidTest/java/com/chopcut/performance/README.md)
- [Android Testing Guide](https://developer.android.com/training/testing)
- [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)

---

**Pronto para começar? Rode:** `./gradlew connectedDebugAndroidTest` 🚀
