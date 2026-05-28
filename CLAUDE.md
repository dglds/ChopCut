# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

Todas as tarefas principais de build, instalação e testes podem ser controladas usando o painel interativo de alta performance (TUI em Go):

```bash
# Iniciar o painel interativo (TUI Go)
./gradle-menu
```

O painel configurará o JDK 17 do projeto automaticamente e lerá as flags configuradas no arquivo `gradle/scripts/gradle-params.sh`.

### Comandos Manuais (se necessário)

Caso queira executar os comandos manualmente sem usar o painel TUI, configure `JAVA_HOME` com o JDK 17 local (`./jdk17`):

```bash
# Build debug APK
JAVA_HOME=./jdk17 ./gradlew assembleDebug

# Instalar no dispositivo ou emulador conectado
JAVA_HOME=./jdk17 ./gradlew installDebug

# Rodar testes instrumentados (requer dispositivo/emulador)
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest

# Rodar um teste de classe específico
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.timeline.FastFrameExtractorTest

# Enviar vídeo para o emulador para testes
~/Android/Sdk/platform-tools/adb push ~/Videos/video.mp4 /sdcard/Movies/video.mp4
~/Android/Sdk/platform-tools/adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/video.mp4
```

### Configurações de Scripts em `gradle/scripts/`

As configurações de parâmetros do Gradle são declaradas em `gradle/scripts/gradle-params.sh`. Você pode ativar/desativar flags (ex: `GRADLE_PARALLEL`, `GRADLE_BUILD_CACHE`, nível de logs de stacktrace) editando esse arquivo como `true` ou `false`. O painel `./gradle-menu` lerá esse arquivo dinamicamente antes de cada tarefa.

## Architecture

### Tech Stack
- Kotlin + Jetpack Compose + Material Design 3
- Media3 (ExoPlayer) for playback, Media3 Transformer for export/trim
- Coroutines + StateFlow for async and reactive state
- No Hilt — ViewModels use manual `ViewModelProvider.Factory`

### Navigation & ViewModel Scoping
Navigation is handled by `ChopCutNavGraph` (single `NavHost`). Start destination is determined in `MainActivity` based on `isFirstRun` and `ACTION_VIEW` intent (supports launching directly into editor with a video URI).

ViewModels are **Activity-scoped** by design: `PreloadViewModel`, `ThumbnailViewModel`, and `AudioViewModel` are created in `MainActivity` and passed down through the nav graph as parameters. ViewModels specific to screen features (e.g. `EditorViewModel` and `HomeViewModel`) are defined inline within feature files.

Routes: `home` → `editor?videoUri={uri}` | `timelineV2?videoUri={uri}`

### Video Processing Pipeline
Two pipelines exist for trim operations (both defined in `VideoEngine.kt`):
- **`CopyPipeline`** — `MediaCodec` + `MediaExtractor` + `MediaMuxer` for lossless copy (fast, no re-encode)
- **`TransformerPipeline`** — Media3 Transformer for multi-range trim (re-encodes, supports clipping config per segment)

`VideoRepository` handles file I/O: temp files go to `context.cacheDir/video_processing/`, output goes to `Movies/ChopCut` (scoped storage, Android 10+) with fallbacks.

### Thumbnail System
`ThumbnailViewModel` → `ThumbnailStripManager` → `FastFrameExtractor` (v3).

`FastFrameExtractor` (defined in `ThumbnailEngine.kt`) uses `MediaCodec` in ByteBuffer mode with hardware-accurate YUV layout metadata (stride/slice-height read from `getOutputFormat()`). Thumbnails are loaded **on-demand** as the user scrolls the timeline — preload is intentionally disabled. Cache lives at `context.cacheDir/thumbnail_strips/` and is cleared on app start.

### State Management Pattern
`EditorViewModel` owns `EditorState` (trim ranges, playback position, waveform, ExoPlayer instance) via `MutableStateFlow`. No MVI framework — direct method calls on the ViewModel. `TrimPosition` is the core model for managing multiple non-overlapping trim ranges.

`PreloadViewModel` coordinates between `ThumbnailViewModel` and `AudioViewModel`, exposing `isReadyFlow` which gates navigation into the editor.

### Constants
All magic numbers must use the centralized constants system at `app/src/main/java/com/chopcut/config/constants/`. Key files: `ThumbnailConfig.kt`, `AudioConfig.kt`. Never hardcode pixel dimensions, durations, or quality values inline.

### Version Scheme
`versionCode = gitCommitCount * 1000 + buildNumber`. Build number auto-increments per build and resets on new commit. Managed by `version.properties` (gitignored).

## Key Files

| File | Purpose |
|------|---------|
| `ui/navigation/ChopCutNavGraph.kt` | All routes and nav logic |
| `ui/home/HomeFeature.kt` | Home screen UI, HomeViewModel, BottomSheetGallery, and PreloadViewModel |
| `ui/editor/EditorFeature.kt` | Unified Editor screen UI, EditorViewModel, and AudioViewModel |
| `ui/editor/TimelineUI.kt` | TimelineEditor drawing/scrolling, PlayerManager, VideoPreview component |
| `ui/editor/TimelineV2Feature.kt` | Calibrated high-fluidity TimelineV2 custom gesture UI, custom playhead, active markers |
| `data/VideoEngine.kt` | Unified lossless CopyPipeline, Media3 TransformerPipeline, and VideoRepository |
| `data/ThumbnailEngine.kt` | FastFrameExtractor v3 and ThumbnailStripManager for YUV frame extraction |
| `data/AudioEngine.kt` | WaveformExtractor and raw PCM decoding engine |
| `core/Models.kt` | Unified data models (VideoInfo, AudioInfo, WaveformData, etc.) |
| `core/Utils.kt` | Unified helpers (FormatUtils, TimeUtils, FileNameUtils, RangeUtils) |

## Padrões críticos de performance

Três padrões aprendidos com bugs reais neste projeto. Violar qualquer um deles causa jank visível em dispositivos mid-range.

### 1. Nunca alocar objetos dentro do draw scope de Canvas

Qualquer objeto criado dentro do lambda de `Canvas { }` ou `drawBehind { }` é alocado a cada frame (60x/s). O GC coleta essas alocações e causa jank durante scroll.

**Proibido dentro do draw scope:**
- `Paint()`, `Rect()`, `CornerRadius()`, `Path()`
- `Color.copy(alpha = ...)` — cria novo objeto `Color`
- `.subList(...).max()` — aloca uma `List`
- Qualquer `String` ou objeto que não seja primitivo

**Padrão correto:** pre-alocar com `remember` no escopo composable e capturar por referência no lambda:
```kotlin
val myPaint = remember { Paint().apply { ... } }
val myRect = remember { Rect() }
Canvas(...) {
    myRect.set(...)  // reutiliza, sem alocação
    drawIntoCanvas { it.nativeCanvas.drawBitmap(..., myRect, myPaint) }
}
```

### 2. Flag `isScrubbing` para gestos sobre estado observado continuamente

Quando um flow contínuo (poll de ExoPlayer, sensor, timer) e um gesto do usuário escrevem no mesmo campo de State, há race condition — o flow sobrescreve a posição calculada pelo gesto a cada 100ms.

**Padrão correto:** flag no State que silencia o observer durante o gesto:
```kotlin
// ViewModel
flow.collectLatest { value ->
    if (!_state.value.isScrubbing) _state.update { it.copy(field = value) }
}
fun startScrubbing() = _state.update { it.copy(isScrubbing = true) }
fun stopScrubbing(final: T) {
    applyFinal(final)  // ação única ao soltar
    _state.update { it.copy(isScrubbing = false) }
}
```
O componente mantém um `localState` próprio durante o gesto e só propaga o valor final via `onGestureEnd`.

### 3. Isolar animações de Canvas com muito trabalho de rendering

Ler um `State` animado (ex: `InfiniteTransition`, `Animatable`) dentro de um Canvas que já faz loops de rendering (régua, thumbnails, waveform) invalida **todo** o Canvas a cada frame da animação — mesmo que a animação afete apenas uma pequena parte.

**Padrão correto:** colocar a animação em um `Canvas` separado sobreposto via `Box`, para que apenas esse Canvas seja invalidado pela animação:
```kotlin
BoxWithConstraints {
    Canvas(Modifier.fillMaxSize()) { /* rendering pesado sem state animado */ }
    Canvas(Modifier.fillMaxSize()) { /* só a animação: skeleton, highlight, etc */ }
}
```

## Testing

Instrumented tests live in `app/src/androidTest/`. Test assets (`sample.mp4`, `sample15min.mp4`) are in `app/src/androidTest/assets/`. `TimelineTestHelper.copyTestVideo()` copies assets to `cacheDir` for use in tests. Custom test runner: `ChopCutTestRunner`.

## Protocolo de Finalização de Sessão (`finalizar sessao`)

Sempre que a instrução "finalizar sessão" ou "finalize a sessão" for dada, o agente deve seguir rigorosamente estes passos em ordem:
1. **Compilação e Validação Completa:**
   - Garantir que o projeto compila sem erros executando o comando de build debug com o JDK 17 do projeto:
     ```bash
     JAVA_HOME=./jdk17 ./gradlew :app:assembleDebug
     ```
2. **Atualização do Walkthrough de Artefatos:**
   - Registrar e detalhar todas as alterações de arquitetura, novos componentes, modelos de dados e mudanças visuais no arquivo de walkthrough na pasta de artefatos: `<appDataDir>/brain/<conversation-id>/walkthrough.md`.
3. **Commit Automático das Alterações:**
   - Adicionar todas as modificações, criações e exclusões ao controle de versão com `git add -A`.
   - Realizar o commit utilizando mensagens claras e concisas seguindo a especificação de Commits Semânticos (ex: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`).
