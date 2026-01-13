# ChopCut - Backlog Simplificado

> **Editor de vídeo Android nativo** • MediaCodec + OpenGL ES • Jetpack Compose

---

## Visão Geral

App Android para edição de vídeo usando 100% APIs nativas (sem FFmpeg).

**Tech Stack:**
- UI: Jetpack Compose + Material3
- Vídeo: MediaCodec, MediaExtractor, MediaMuxer
- Graphics: OpenGL ES 3.0
- Preview: ExoPlayer
- Background: WorkManager, Foreground Service

---

## Features

| Feature | Descrição |
|---------|-----------|
| Trim | Cortar vídeos em partes |
| Join | Concatenar vídeos |
| Compress | Reduz tamanho/bitrate |
| Resize | Alterar resolução |
| Crop | Recortar área do vídeo |
| Extract Audio | Extrair trilha de áudio |
| SoundForm | Visualizar/identificar períodos de fala |
| Remux | Mudar container |

---

## Backlog por Fase

### FASE 1: Fundamentos

#### TAREFA 1.1: Setup do Projeto
- [x] Projeto Android com Kotlin DSL
- [x] Jetpack Compose + Material3
- [x] Timber para logging
- [x] DispatcherProvider

#### TAREFA 1.2: CodecCapabilities
Detectar codecs disponíveis no dispositivo.
```kotlin
// data/codec/CodecCapabilities.kt
fun hasEncoder(codec: VideoCodec): Boolean
fun selectBestCodec(): VideoCodec
```

#### TAREFA 1.3: VideoRepository
Gerenciar arquivos de vídeo e metadados.
```kotlin
// data/repository/VideoRepository.kt
suspend fun getMetadata(uri: Uri): VideoInfo
suspend fun createTempFile(): File
suspend fun saveToGallery(file: File): Uri
```

#### TAREFA 1.4: CopyPipeline - Trim
Recortar vídeo sem re-encode (fast path).
```kotlin
// data/pipeline/CopyPipeline.kt
suspend fun trim(uri: Uri, ranges: List<TimeRange>): Flow<Float>
```

#### TAREFA 1.5: CopyPipeline - Join
Concatenar múltiplos vídeos.
```kotlin
suspend fun concat(uris: List<Uri>): Flow<Float>
```

---

### FASE 2: Transcoding

#### TAREFA 2.1: SurfaceBridge
Contexto EGL para MediaCodec + OpenGL.
```kotlin
// graphics/SurfaceBridge.kt
fun createDecoderSurface(): Surface
fun createEncoderSurface(): Surface
```

#### TAREFA 2.2: GLRenderer
Renderizador OpenGL para transformações.
```kotlin
// graphics/GLRenderer.kt
fun setTransform(transform: Transform)
fun renderFrame()
```

#### TAREFA 2.3: TranscodePipeline
Pipeline completo com decode → GL → encode.
```kotlin
// data/pipeline/TranscodePipeline.kt
suspend fun process(uri: Uri, transform: Transform, config: ExportConfig): Flow<Float>
```

#### TAREFA 2.4: Compress
Re-encode com bitrate menor.
```kotlin
suspend fun compress(uri: Uri, bitrate: Int): Flow<Float>
```

#### TAREFA 2.5: Resize
Alterar resolução com GL scaling.
```kotlin
suspend fun resize(uri: Uri, size: Size): Flow<Float>
```

#### TAREFA 2.6: Crop
Recortar região com GL viewport.
```kotlin
suspend fun crop(uri: Uri, rect: RectF): Flow<Float>
```

---

### FASE 3: Preview + UI

#### TAREFA 3.1: PreviewManager
Gerenciar ExoPlayer para preview.
```kotlin
// ui/preview/PreviewManager.kt
fun attachSurface(surface: Surface)
fun setSource(uri: Uri)
fun play()/pause()/seekTo(ms: Long)
```

#### TAREFA 3.2: ThumbnailExtractor
Extrair thumbnails para timeline.
```kotlin
// data/thumbnail/ThumbnailExtractor.kt
suspend fun extractStrip(uri: Uri, count: Int): List<Bitmap>
```

#### TAREFA 3.3: VideoPreview Component
Composable com player e controles.
```kotlin
// ui/components/VideoPreview.kt
@Composable fun VideoPreview(uri: Uri, onSeek: (Long) -> Unit)
```

#### TAREFA 3.4: Timeline Component
Timeline com thumbnails e trim range.
```kotlin
// ui/components/VideoTimeline.kt
@Composable fun VideoTimeline(duration: Long, trimRange: TimeRange, onTrimChange: (TimeRange) -> Unit)
```

#### TAREFA 3.5: EditorScreen
Tela principal do editor integrando componentes.
```kotlin
// ui/screen/EditorScreen.kt
@Composable fun EditorScreen(videoUri: String)
```

---

### FASE 4: Polish

#### TAREFA 4.1: Extract Audio
Extrair trilha de áudio do vídeo (formato AAC).
```kotlin
// data/audio/AudioExtractor.kt
suspend fun extract(uri: Uri): Flow<Result<File>>
```

#### TAREFA 4.2: SoundForm - Audio Analysis
Analisar áudio para identificar períodos de fala e silêncio.
```kotlin
// data/audio/AudioAnalyzer.kt
suspend fun analyzeSpeechSegments(file: File): List<SpeechSegment>
data class SpeechSegment(val startMs: Long, val endMs: Long, val isSpeech: Boolean)
```

#### TAREFA 4.3: SettingsScreen
Tela de configurações (codec, qualidade, etc).
```kotlin
// ui/screen/SettingsScreen.kt
@Composable fun SettingsScreen()
```
- [x] ✅ COMPLETO

#### TAREFA 4.4: ExportForegroundService
Service para export em background com notificação.
```kotlin
// service/ExportService.kt
class ExportForegroundService : Service()
```

#### TAREFA 4.5: Error Handling
Tratamento de erros e recuperação.
```kotlin
// util/ErrorHandler.kt
fun handle(error: Throwable): ErrorState
fun recover(error: ErrorState): RecoveryStrategy
```

---

## Estrutura de Pastas

```
app/src/main/java/com/chopcut/
├── data/
│   ├── codec/          (CodecCapabilities)
│   ├── repository/     (VideoRepository)
│   ├── pipeline/       (CopyPipeline, TranscodePipeline)
│   ├── thumbnail/      (ThumbnailExtractor)
│   └── audio/          (AudioExtractor, AudioAnalyzer)
├── graphics/
│   ├── gl/             (GLRenderer, shaders)
│   └── egl/            (SurfaceBridge)
├── ui/
│   ├── components/     (VideoPreview, VideoTimeline, etc)
│   ├── screen/         (HomeScreen, EditorScreen, SettingsScreen)
│   └── preview/        (PreviewManager)
├── service/            (ExportService)
└── util/               (ErrorHandler, DispatcherProvider)
```

---

## Definição de Pronto

- [ ] Código compila sem warnings
- [ ] Testado em pelo menos 2 dispositivos
- [ ] Sem memory leaks óbvios
- [ ] Export funciona para as features implementadas

---

## Estimativas

| Fase | Tarefas | Horas |
|------|---------|-------|
| 1: Fundamentos | 5 | ~20h |
| 2: Transcoding | 6 | ~32h |
| 3: Preview + UI | 5 | ~24h |
| 4: Polish | 4 | ~16h |
| **Total** | **20** | **~92h** |

**Estimativa:** 2-3 semanas com 1 dev full-time
