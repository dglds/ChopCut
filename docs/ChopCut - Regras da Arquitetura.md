# ChopCut - Regras da Arquitetura

> Documento de referência rápida para manter o projeto funcionando sem quebrar.

## 🏗️ Estrutura de Arquivos (20 no total)

Não crie novos arquivos .kt sem necessidade. Tudo deve caber nos 20 arquivos existentes.

```
com.chopcut/                          # package único para todos os arquivos
├── ChopCutApplication.kt             # Application class
├── MainActivity.kt                   # Entry point, cria ViewModels Activity-scoped
├── core/
│   ├── Models.kt                     # VideoInfo, AudioInfo, WaveformData, TrimPosition, etc.
│   ├── Utils.kt                      # TimeFormatter, FormatUtils, FileNameUtils, RangeUtils, etc.
│   ├── Theme.kt                      # Cores, tipografia, tema Material3, animações
│   └── Errors.kt                     # ErrorHandler, ChopCutException, ErrorResult
├── data/
│   ├── AudioEngine.kt                # WaveformExtractor, WaveformCache, WaveformAnalyzer, AudioRawData
│   ├── ThumbnailEngine.kt            # FastFrameExtractor, ThumbnailCacheManager, ThumbnailStripManager
│   └── VideoEngine.kt                # TransformerPipeline, CopyPipeline, VideoRepository
├── graphics/
│   ├── egl/SurfaceBridge.kt          # (standalone)
│   └── gl/GLRenderer.kt              # (standalone)
├── ui/
│   ├── SharedComponents.kt           # Botões, FABs, Cards, Loading, ErrorState, Overlays
│   ├── home/
│   │   └── HomeFeature.kt            # HomeScreen + HomeViewModel + BottomSheetGallery + Preload
│   ├── editor/
│   │   ├── EditorFeature.kt          # EditorScreen + EditorViewModel + AudioViewModel + ThumbnailViewModel + Configs
│   │   ├── EditorToolsUI.kt          # MainToolBar, TrimToolPanel, FormatToolPanel, CompressToolPanel
│   │   ├── TimelineUI.kt             # VideoTimeline, TimelineEditor, VideoPreview, PlayerManager, Seekbar
│   │   ├── TimelineV2Feature.kt      # TimelineV2Screen, TimelineV2ViewModel, TimelineV2 Canvas (Demo)
│   │   ├── TrimUI.kt                 # TrimPosition, TrimRange, TrimSaveDialog, RangeManager
│   │   └── WaveformUI.kt             # WaveformRenderer, AudioWaveForms, WaveformConfig
│   └── navigation/
│       └── ChopCutNavGraph.kt        # NavHost com rotas home e editor
```

## ⚠️ Regras Críticas

### 1. Package é sempre `com.chopcut`

Todos os arquivos usam `package com.chopcut`. Sem subpacotes. Isso significa que **não há imports entre arquivos internos** — tudo no mesmo package se enxerga automaticamente.

```kotlin
// ✅ CERTO - package único
package com.chopcut

// ✅ CERTO - referência direta sem import
val info = VideoInfo(...)    // definido em core/Models.kt
ErrorState(...)              // definido em ui/SharedComponents.kt
```

### 2. Só adicione código novo dentro dos 20 arquivos

Qualquer nova funcionalidade deve ser adicionada a um dos arquivos existentes. Não crie novos arquivos .kt.

| Se for... | Adicione em... |
|-----------|---------------|
| Modelo de dados | `core/Models.kt` |
| Função utilitária | `core/Utils.kt` |
| Componente UI reutilizável | `ui/SharedComponents.kt` |
| Tela/ViewModel do editor | `ui/editor/EditorFeature.kt` |
| Componente da timeline | `ui/editor/TimelineUI.kt` |
| Componente da Timeline V2 | `ui/editor/TimelineV2Feature.kt` |
| Componente de corte | `ui/editor/TrimUI.kt` |
| Componente de áudio | `ui/editor/WaveformUI.kt` |
| Barra de ferramentas | `ui/editor/EditorToolsUI.kt` |
| Tela inicial | `ui/home/HomeFeature.kt` |

### 3. EditorViewModel e AudioViewModel são Activity-scoped

Criados em `MainActivity.kt` e **passados como parâmetro** no NavGraph. Não recrie dentro de um Composable.

```kotlin
// ✅ CERTO - factory no MainActivity
val thumbnailViewModel: ThumbnailViewModel = viewModel(
    factory = ThumbnailViewModel.ThumbnailViewModelFactory(application)
)
val audioViewModel: AudioViewModel = viewModel(
    factory = AudioViewModel.AudioViewModelFactory(application)
)
val preloadViewModel: PreloadViewModel = viewModel(
    factory = PreloadViewModel.PreloadViewModelFactory(application, thumbnailViewModel, audioViewModel)
)
```

### 4. Não duplique nomes de classes/objetos

Como tudo está no mesmo package, **não pode haver duas classes, objetos ou enums com o mesmo nome**.

Conflitos conhecidos e resolvidos:

| Nome | Onde está | Uso |
|------|-----------|-----|
| `ExtractionStage` | `core/Models.kt` (via `PerformanceTelemetry`) | Estágios de thumbnails: `DECODE, PROCESS, SAVE` |
| `PreloadStage` | `ui/home/HomeFeature.kt` | Estágios de preload: `Starting, Validating, ExtractingAudio, ExtractingThumbnails, Ready` |
| `WaveformData` | `core/Models.kt` | Apenas 2 params: `(amplitudes: FloatArray, durationMs: Long)` |
| `AudioInfo` | `core/Models.kt` | Metadata de áudio |
| `VideoInfo` | `core/Models.kt` | Metadata de vídeo |

### 5. Build sempre com `./scripts/assembledebug`

O script `assembledebug` na pasta `scripts/` configura o `JAVA_HOME=jdk17` automaticamente. Não use `./gradlew assembleDebug` diretamente ou pode falhar com Java 25.

> [!TIP]
> **Registro de Erros Automatizado (`errors.json`):**
> Sempre que uma compilação ou script de build falhar, o script de captura grava automaticamente os detalhes da falha, a tarefa e o timestamp no arquivo `errors.json` na raiz do projeto. Isso elimina a necessidade de contagens ou anotações manuais de falhas.

```bash
./scripts/assembledebug     # ✅ CERTO
```

### 6. Performance: 3 padrões para evitar jank

SHA-1 dos padrões (ver CLAUDE.md para detalhes completos):

1. **Nunca alocar objetos dentro do draw scope de Canvas** — `Paint()`, `Rect()`, `CornerRadius()`, etc. Use `remember` e reutilize.
2. **Flag `isScrubbing` para gestos** — Quando um StateFlow contínuo e um gesto escrevem no mesmo campo, use flag para silenciar o observer durante o gesto.
3. **Isolar animações em Canvas separado** — Animações que invalidam Canvas devem ficar em `Canvas` sobreposto, não dentro do Canvas principal.

## 🧪 Testes

```bash
# Build APK
./scripts/assembledebug

# Testes instrumentados
./gradlew connectedAndroidTest

# Teste específico
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.timeline.FastFrameExtractorTest
```

Assets de teste: `app/src/androidTest/assets/sample.mp4`

## 🔍 Localização de Componentes Comuns

| O que procura | Onde encontrar |
|---|---|
| `ErrorState`, `LoadingState`, `EmptyState` | `ui/SharedComponents.kt` |
| `ChopCutPrimaryButton`, `ChopCutSecondaryButton` | `ui/SharedComponents.kt` |
| `ChopCutTypography`, `primaryColor()`, `OnSurface`, `ErrorDark` | `core/Theme.kt` |
| `ChopCutSpacing`, `ChopCutAnimation` | `core/Theme.kt` |
| `FormatUtils`, `FileNameUtils`, `RangeUtils` | `core/Utils.kt` |
| `PreloadViewModel`, `PreloadUiState`, `PreloadStage`, `PreloadProgress` | `ui/home/HomeFeature.kt` |
| `ThumbnailViewModel`, `AudioViewModel`, `EditorViewModel` | `ui/editor/EditorFeature.kt` |
| `TimelineV2Screen`, `TimelineV2ViewModel`, `TimelineV2` (Canvas) | `ui/editor/TimelineV2Feature.kt` |
| `EditorState`, `EditorTool`, `CompressionLevel` | `ui/editor/EditorFeature.kt` (config) ou `ui/editor/EditorToolsUI.kt` (state) |
| `TrimPosition`, `TrimRange`, `SaveDialogState` | `ui/editor/TrimUI.kt` |
| `ThumbnailConfig`, `AudioConfig` | `ui/editor/EditorFeature.kt` |
| `ThumbnailCacheManager`, `FastFrameExtractor`, `ThumbnailStripManager` | `data/ThumbnailEngine.kt` |
| `TransformerPipeline`, `CopyPipeline`, `VideoRepository` | `data/VideoEngine.kt` |
| `WaveformExtractor`, `WaveformCache`, `WaveformConfig` | `data/AudioEngine.kt` |
| `ErrorHandler`, `ChopCutException` | `core/Errors.kt` |
