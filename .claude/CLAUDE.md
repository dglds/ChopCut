# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

O caminho canônico são os atalhos do `Makefile` (ou `./gradlew` direto). O JDK 17 do projeto (`./jdk17`) é exportado pelo Makefile; rodando `gradlew` na mão, defina `JAVA_HOME=./jdk17`.

```bash
make build      # APK de debug (assembleDebug)
make install    # instala no device/emulador conectado
make run        # instala e abre o app
make compile    # checagem rápida do Kotlin (compileDebugKotlin)
make lint       # lintDebug
make test       # testes instrumentados (requer device)
make help       # lista os alvos
```

Toggles pontuais de debug vão via `GRADLE_ARGS`, ex.: `make build GRADLE_ARGS="-i --rerun-tasks"`.

Equivalentes manuais com `gradlew`:

```bash
JAVA_HOME=./jdk17 ./gradlew assembleDebug
JAVA_HOME=./jdk17 ./gradlew installDebug
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest
# Teste de classe específico (troque pelo nome real da classe de teste):
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.<NomeDoTest>

# Enviar vídeo para o emulador para testes
~/Android/Sdk/platform-tools/adb push ~/Videos/video.mp4 /sdcard/Movies/video.mp4
~/Android/Sdk/platform-tools/adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/video.mp4
```

### Flags de performance e o painel TUI opcional

As flags de performance (parallel, caching, daemon) vivem em **`gradle.properties`** — sempre-ligadas, sem matriz de toggles em script. Existe ainda um menu interativo opcional (`./gradle-menu`) — um script bash com `select` (zero dependências) que lista as tarefas e delega ao `make`, mais atalhos de `connect device`/`pair Wi-Fi` via adb; **não é o caminho canônico** e não é necessário para nenhuma tarefa.

## Architecture

### Tech Stack
- Kotlin + Jetpack Compose + Material Design 3
- Media3 (ExoPlayer) for playback, Media3 Transformer for export/trim
- Coroutines + StateFlow for async and reactive state
- No Hilt — ViewModels use manual `ViewModelProvider.Factory`

### Navigation & ViewModel Scoping
Navigation is handled by `ChopCutNavGraph` (single `NavHost`). Start destination is determined in `MainActivity` based on `isFirstRun` and `ACTION_VIEW` intent (supports launching directly into the editor with a video URI).

Routes: `home` and `editor?videoUri={videoUri}`.

ViewModels use manual factories (no Hilt): `HomeViewModel` (in `HomeFeature.kt`) and `TimelineViewModel` (in `TimelineFeature.kt`), each defined inline within its feature file. `ErrorAwareViewModel` (in `core/Errors.kt`) is the shared base.

### Video Processing Pipeline
Two pipelines exist for trim operations (both defined in `VideoEngine.kt`):
- **`CopyPipeline`** — `MediaCodec` + `MediaExtractor` + `MediaMuxer` for lossless copy (fast, no re-encode)
- **`TransformerPipeline`** — Media3 Transformer for multi-range trim (re-encodes, supports clipping config per segment)

`VideoRepository` handles file I/O: temp files go to `context.cacheDir/video_processing/`, output goes to `Movies/ChopCut` (scoped storage, Android 10+) with fallbacks.

### Thumbnail System
Extraction lives in `data/ThumbnailExtraction.kt` (`ThumbnailExtraction`, `ExtractionProgressState`, `ExtractionResult`), using `MediaCodec` in ByteBuffer mode with hardware-accurate YUV layout metadata (stride/slice-height from `getOutputFormat()`). Dimensions and quality presets live in `ThumbnailConfig.kt`. Thumbnails are loaded **on-demand** as the user scrolls the timeline; cache lives under `context.cacheDir` and is cleared on app start.

### State Management Pattern
`TimelineViewModel` (in `TimelineFeature.kt`) owns the editor/timeline state (trim ranges, playback position, markers) via `MutableStateFlow`. No MVI framework — direct method calls on the ViewModel. See the **`isScrubbing`** performance pattern below for how user gestures coexist with the continuous ExoPlayer position poll without races.

### Constants
Magic numbers go in the centralized config objects: `ThumbnailConfig.kt` (dimensions, presets) and `CompressionLevel.kt`. Never hardcode pixel dimensions, durations, or quality values inline.

### Version Scheme
`versionCode = gitCommitCount * 1000 + buildNumber`. Build number auto-increments per build and resets on new commit. Managed by `version.properties` (gitignored).

## Key Files

Não mantenha uma tabela de arquivos aqui — ela defasa (esta defasou). As fontes únicas:

- **Estrutura canônica + "onde adicionar cada coisa":** [docs/ChopCut - Regras da Arquitetura.md](../docs/ChopCut%20-%20Regras%20da%20Arquitetura.md)
- **Inventário vivo (arquivos/tipos/funções, auto-gerado):** [docs/STRUCTURE.generated.md](../docs/STRUCTURE.generated.md)
- **Localizar um símbolo específico:** CodeGraph (`codegraph_search` / `codegraph_context`)

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

## Protocolo de Finalização de Sessão (`finalizar sessão`)

Quando o usuário disser "finalizar sessão" / "finalize a sessão", execute a skill **`/finish-session`** — fonte única do ritual de encerramento (validar build, capturar a lição **Memory-first**, atualizar `STATE.md`, criar a nota `sessions/session#NN-objetivo-da-session.md` e fazer commit modular por escopo). O passo a passo e o template vivem em [SESSION_PROTOCOL.md](../SESSION_PROTOCOL.md) §3 — não duplique aqui.
