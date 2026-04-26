# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All commands use the Gradle wrapper. ADB is at `~/Android/Sdk/platform-tools/adb`.

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run a single instrumented test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.timeline.FastFrameExtractorTest

# Push a video to emulator for testing
~/Android/Sdk/platform-tools/adb push ~/Videos/video.mp4 /sdcard/Movies/video.mp4
~/Android/Sdk/platform-tools/adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/video.mp4
```

## Architecture

### Tech Stack
- Kotlin + Jetpack Compose + Material Design 3
- Media3 (ExoPlayer) for playback, Media3 Transformer for export/trim
- Coroutines + StateFlow for async and reactive state
- No Hilt — ViewModels use manual `ViewModelProvider.Factory`

### Navigation & ViewModel Scoping
Navigation is handled by `ChopCutNavGraph` (single `NavHost`). Start destination is determined in `MainActivity` based on `isFirstRun` and `ACTION_VIEW` intent (supports launching directly into editor with a video URI).

ViewModels are **Activity-scoped** by design: `PreloadViewModel`, `ThumbnailViewModel`, and `AudioViewModel` are created in `MainActivity` and passed down through the nav graph as parameters. This is intentional — they must survive navigation between `HomeScreen` and `EditorScreen`.

Routes: `onboarding` → `home` → `editor?videoUri={uri}` | `preferences` | `audio_waveforms_test`

### Video Processing Pipeline
Two pipelines exist for trim operations:
- **`CopyPipeline`** — `MediaCodec` + `MediaExtractor` + `MediaMuxer` for lossless copy (fast, no re-encode)
- **`TransformerPipeline`** — Media3 Transformer for multi-range trim (re-encodes, supports clipping config per segment)

`VideoRepository` handles file I/O: temp files go to `context.cacheDir/video_processing/`, output goes to `Movies/ChopCut` (scoped storage, Android 10+) with fallbacks.

### Thumbnail System
`ThumbnailViewModel` → `ThumbnailStripManager` → `FastFrameExtractor` (v3).

`FastFrameExtractor` uses `MediaCodec` in ByteBuffer mode with hardware-accurate YUV layout metadata (stride/slice-height read from `getOutputFormat()`). Thumbnails are loaded **on-demand** as the user scrolls the timeline — preload is intentionally disabled. Cache lives at `context.cacheDir/thumbnail_strips/` and is cleared on app start.

### State Management Pattern
`EditorViewModel` owns `EditorState` (trim ranges, playback position, waveform, ExoPlayer instance) via `MutableStateFlow`. No MVI framework — direct method calls on the ViewModel. `TrimPosition` is the core model for managing multiple non-overlapping trim ranges.

`PreloadViewModel` coordinates between `ThumbnailViewModel` and `AudioViewModel`, exposing `isReadyFlow` which gates navigation into the editor.

### Constants
All magic numbers must use the centralized constants system at `app/src/main/java/com/chopcut/config/constants/`. Key files: `ThumbnailConfig.kt`, `AudioConfig.kt`. Never hardcode pixel dimensions, durations, or quality values inline.

### Debug Tooling
- `DebugConfig` (`util/debug/DebugConfig.kt`) — central flags for verbose logs, overlays, performance monitoring. Flags are hardcoded; change them here to toggle behavior.
- `DebugToast` — floating overlay in DEBUG builds, managed by `DebugViewModel`, rendered in `ChopCutNavGraph` above the NavHost.
- `ActivityLogger` — logs user actions to local file via `FileLoggingTree`.
- Perfetto tracing: `trace.sh` script for capturing system traces.

### Version Scheme
`versionCode = gitCommitCount * 1000 + buildNumber`. Build number auto-increments per build and resets on new commit. Managed by `version.properties` (gitignored).

## Key Files

| File | Purpose |
|------|---------|
| `ui/navigation/ChopCutNavGraph.kt` | All routes and nav logic |
| `ui/screen/EditorScreen.kt` | Main editor screen |
| `ui/viewmodel/EditorViewModel.kt` | Editor state + ExoPlayer |
| `ui/viewmodel/PreloadViewModel.kt` | Coordinates thumbnail/audio preload |
| `data/pipeline/TransformerPipeline.kt` | Multi-range trim via Media3 |
| `data/pipeline/CopyPipeline.kt` | Lossless trim via MediaCodec |
| `data/thumbnail/v3/FastFrameExtractor.kt` | Hardware-accelerated frame extraction |
| `ui/components/gallery/BottomSheetGallery.kt` | Video picker (MediaStore query) |
| `util/debug/DebugConfig.kt` | All debug flags |

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
