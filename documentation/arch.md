# CropSnap — Guia de Reestruturação Arquitetural

## Stack

- Kotlin + Jetpack Compose
- Coroutines + Flow
- FFmpeg (4MB minimal build) para copy operations
- MediaCodec para hardware-accelerated encoding
- Hilt para DI

---

## 1. Abordagem Híbrida: MVI + MVVM

Telas complexas usam **MVI** (unidirecional, Intent/State). Telas simples usam **MVVM** (métodos diretos). A base é compartilhada.

### BaseViewModel

```kotlin
abstract class BaseViewModel<S, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    private val _effects = Channel<E>()
    val effects = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    protected fun reduce(block: S.() -> S) {
        _state.update { it.block() }
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
```

### Critérios de escolha

| Critério                                    | MVI (`onIntent`) | MVVM (métodos diretos) |
| ------------------------------------------- | ----------------- | ---------------------- |
| Estado com muitas variáveis interdependentes | ✓                 |                        |
| Operações encadeadas (trim → export)         | ✓                 |                        |
| Precisa de log/replay de ações              | ✓                 |                        |
| Tela com 1-3 interações simples             |                   | ✓                      |
| CRUD / toggle / preferências                |                   | ✓                      |

### Mapeamento por tela

| Tela          | Estilo | Motivo                                         |
| ------------- | ------ | ---------------------------------------------- |
| Editor (Trim) | MVI    | Estado complexo (range + thumbnails + processing) |
| Timeline      | MVI    | Interações encadeadas (drag, seek, zoom, play) |
| Audio         | MVI    | Volume, mute, preview, export combinados       |
| Export        | MVI    | Pipeline com múltiplos estados                 |
| Home/Picker   | MVVM   | Só seleciona arquivo e navega                  |
| Settings      | MVVM   | Toggles independentes                          |

---

## 2. Estrutura de Pacotes (por feature)

```
com.cropsnap/
├── core/
│   ├── base/                  # BaseViewModel, BaseUseCase
│   ├── engine/                # VideoEngine interface + HybridVideoEngine
│   ├── media/                 # MediaCodec wrappers, FFmpeg wrapper
│   ├── model/                 # VideoInfo, CodecCapabilities, OperationProgress
│   └── di/                    # Hilt modules
│
├── editor/                    # Feature principal (MVI)
│   ├── EditorViewModel.kt
│   ├── EditorState.kt         # State + Intent + Effect
│   ├── EditorScreen.kt
│   └── usecase/
│       ├── TrimUseCase.kt
│       ├── JoinUseCase.kt
│       ├── CompressUseCase.kt
│       ├── ResizeUseCase.kt
│       ├── CropUseCase.kt
│       └── SpeedUseCase.kt
│
├── timeline/                  # Componente complexo = sub-feature (MVI)
│   ├── TimelineViewModel.kt
│   ├── TimelineState.kt       # State + Intent + Effect
│   ├── TimelineView.kt
│   └── ThumbnailLoader.kt
│
├── picker/                    # Tela simples (MVVM direto)
│   ├── PickerViewModel.kt
│   └── PickerScreen.kt
│
├── export/                    # Feature de exportação (MVI)
│   ├── ExportViewModel.kt
│   ├── ExportState.kt
│   └── ExportScreen.kt
│
├── settings/                  # Tela simples (MVVM direto)
│   ├── SettingsViewModel.kt
│   └── SettingsScreen.kt
│
└── navigation/
    └── AppCoordinator.kt      # NavHost centralizado
```

---

## 3. EditorViewModel Unificado (MVI)

Um único ViewModel substitui `EditorViewModel`, `AudioViewModel`, `PreloadViewModel`.

### State

```kotlin
data class EditorState(
    // Vídeo carregado
    val videoUri: String = "",
    val videoInfo: VideoInfo? = null,

    // Operação ativa
    val activeOperation: OperationType? = null,
    val progress: OperationProgress = OperationProgress.Idle,

    // Trim
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,

    // Audio
    val volume: Float = 1f,
    val isMuted: Boolean = false,

    // Export
    val exportConfig: ExportConfig? = null
)

enum class OperationType { TRIM, JOIN, COMPRESS, RESIZE, CROP, SPEED, REMUX, EXTRACT_AUDIO }
```

### Intent

```kotlin
sealed class EditorIntent {
    // Load
    data class LoadVideo(val uri: String) : EditorIntent()

    // Trim
    data class SetTrimRange(val startMs: Long, val endMs: Long) : EditorIntent()
    object ConfirmTrim : EditorIntent()

    // Audio
    data class SetVolume(val volume: Float) : EditorIntent()
    object ToggleMute : EditorIntent()

    // Compress
    data class Compress(val quality: Quality) : EditorIntent()

    // Resize
    data class Resize(val size: Size) : EditorIntent()

    // Export
    data class Export(val config: ExportConfig) : EditorIntent()

    // Geral
    object Cancel : EditorIntent()
    object DismissError : EditorIntent()
}
```

### Effect

```kotlin
sealed class EditorEffect {
    data class NavigateToExport(val outputUri: String) : EditorEffect()
    object NavigateBack : EditorEffect()
    data class ShowToast(val message: String) : EditorEffect()
    data class HapticFeedback(val type: HapticType) : EditorEffect()
}
```

### ViewModel

```kotlin
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val trimUC: TrimUseCase,
    private val joinUC: JoinUseCase,
    private val compressUC: CompressUseCase,
    private val resizeUC: ResizeUseCase,
    private val cropUC: CropUseCase,
    private val speedUC: SpeedUseCase,
    private val loadVideoInfoUC: LoadVideoInfoUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<EditorState, EditorEffect>(EditorState()) {

    private var operationJob: Job? = null

    fun onIntent(intent: EditorIntent) = when (intent) {
        is EditorIntent.LoadVideo -> handleLoad(intent.uri)
        is EditorIntent.SetTrimRange -> reduce {
            copy(trimStartMs = intent.startMs, trimEndMs = intent.endMs)
        }
        is EditorIntent.ConfirmTrim -> handleTrim()
        is EditorIntent.SetVolume -> reduce { copy(volume = intent.volume) }
        is EditorIntent.ToggleMute -> reduce { copy(isMuted = !isMuted) }
        is EditorIntent.Compress -> handleCompress(intent.quality)
        is EditorIntent.Resize -> handleResize(intent.size)
        is EditorIntent.Export -> handleExport(intent.config)
        is EditorIntent.Cancel -> handleCancel()
        is EditorIntent.DismissError -> reduce {
            copy(progress = OperationProgress.Idle)
        }
    }

    private fun handleLoad(uri: String) {
        viewModelScope.launch {
            reduce { copy(videoUri = uri) }
            val info = loadVideoInfoUC(uri)
            reduce { copy(videoInfo = info, trimEndMs = info.durationMs) }
        }
    }

    private fun handleTrim() {
        executeOperation(OperationType.TRIM) {
            trimUC(currentState.videoUri, currentState.trimStartMs, currentState.trimEndMs)
        }
    }

    private fun handleCompress(quality: Quality) {
        executeOperation(OperationType.COMPRESS) {
            compressUC(currentState.videoUri, quality)
        }
    }

    private fun handleResize(size: Size) {
        executeOperation(OperationType.RESIZE) {
            resizeUC(currentState.videoUri, size)
        }
    }

    private fun handleExport(config: ExportConfig) {
        reduce { copy(exportConfig = config) }
        // Delega pro ExportViewModel via navegação
        sendEffect(EditorEffect.NavigateToExport(currentState.videoUri))
    }

    private fun handleCancel() {
        operationJob?.cancel()
        reduce { copy(progress = OperationProgress.Idle, activeOperation = null) }
    }

    private fun executeOperation(
        type: OperationType,
        operation: suspend () -> Flow<OperationProgress>
    ) {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            reduce { copy(activeOperation = type, progress = OperationProgress.Idle) }
            try {
                operation().collect { progress ->
                    reduce { copy(progress = progress) }
                    if (progress is OperationProgress.Done) {
                        sendEffect(EditorEffect.ShowToast("$type concluído"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reduce {
                    copy(progress = OperationProgress.Failed(AppError.fromException(e)))
                }
            }
        }
    }
}
```

---

## 4. UseCases com `operator fun invoke`

Cada UseCase = 1 operação, 1 arquivo, testável isoladamente.

```kotlin
class TrimUseCase @Inject constructor(
    private val engine: VideoEngine
) {
    suspend operator fun invoke(
        uri: String, startMs: Long, endMs: Long
    ): Flow<OperationProgress> = engine.trim(uri, startMs, endMs)
}

class CompressUseCase @Inject constructor(
    private val engine: VideoEngine
) {
    suspend operator fun invoke(
        uri: String, quality: Quality
    ): Flow<OperationProgress> = engine.compress(uri, quality)
}

class ResizeUseCase @Inject constructor(
    private val engine: VideoEngine
) {
    suspend operator fun invoke(
        uri: String, size: Size
    ): Flow<OperationProgress> = engine.resize(uri, size)
}

class LoadVideoInfoUseCase @Inject constructor(
    private val engine: VideoEngine
) {
    suspend operator fun invoke(uri: String): VideoInfo = engine.getVideoInfo(uri)
}
```

Chamada no ViewModel fica natural:

```kotlin
trimUC(uri, start, end).collect { progress ->
    reduce { copy(progress = progress) }
}
```

---

## 5. VideoEngine — Abstração sobre MediaCodec/FFmpeg

Esconde a decisão híbrida (FFmpeg pra copy, MediaCodec pra encode) atrás de uma interface.

### Interface

```kotlin
interface VideoEngine {
    fun trim(uri: String, startMs: Long, endMs: Long): Flow<OperationProgress>
    fun join(uris: List<String>): Flow<OperationProgress>
    fun compress(uri: String, quality: Quality): Flow<OperationProgress>
    fun resize(uri: String, size: Size): Flow<OperationProgress>
    fun crop(uri: String, rect: CropRect): Flow<OperationProgress>
    fun changeSpeed(uri: String, speed: Float): Flow<OperationProgress>
    fun remux(uri: String, format: ContainerFormat): Flow<OperationProgress>
    fun extractAudio(uri: String): Flow<OperationProgress>
    suspend fun getVideoInfo(uri: String): VideoInfo
}
```

### Implementação Híbrida

```kotlin
class HybridVideoEngine @Inject constructor(
    private val ffmpeg: FFmpegExecutor,
    private val codec: MediaCodecPipeline
) : VideoEngine {

    // Copy operations → FFmpeg (rápido, sem re-encode)
    override fun trim(uri: String, startMs: Long, endMs: Long) = flow {
        emit(OperationProgress.Running(0f, "Cortando..."))
        ffmpeg.execute(buildTrimCommand(uri, startMs, endMs))
            .collect { emit(it) }
    }

    override fun join(uris: List<String>) = flow {
        emit(OperationProgress.Running(0f, "Concatenando..."))
        ffmpeg.execute(buildJoinCommand(uris))
            .collect { emit(it) }
    }

    override fun remux(uri: String, format: ContainerFormat) = flow {
        ffmpeg.execute(buildRemuxCommand(uri, format))
            .collect { emit(it) }
    }

    override fun extractAudio(uri: String) = flow {
        ffmpeg.execute(buildExtractAudioCommand(uri))
            .collect { emit(it) }
    }

    // Transcode operations → MediaCodec (hardware accelerated)
    override fun compress(uri: String, quality: Quality) = flow {
        emit(OperationProgress.Running(0f, "Comprimindo..."))
        codec.transcode(uri, quality.toBitrate())
            .collect { emit(it) }
    }

    override fun resize(uri: String, size: Size) = flow {
        codec.transcode(uri, targetSize = size)
            .collect { emit(it) }
    }

    override fun crop(uri: String, rect: CropRect) = flow {
        codec.transcodeWithGL(uri, cropRect = rect)
            .collect { emit(it) }
    }

    override fun changeSpeed(uri: String, speed: Float) = flow {
        codec.transcodeWithSpeed(uri, speed)
            .collect { emit(it) }
    }

    override suspend fun getVideoInfo(uri: String): VideoInfo {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(uri)
            VideoInfo(
                uri = uri,
                durationMs = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLong() ?: 0,
                width = retriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0,
                height = retriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0,
                codec = retriever.extractMetadata(METADATA_KEY_MIMETYPE) ?: "",
                bitrate = retriever.extractMetadata(METADATA_KEY_BITRATE)?.toInt() ?: 0,
                rotation = retriever.extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            )
        }
    }
}
```

Amanhã se trocar pra 100% nativo, só muda a implementação. UseCases e ViewModels permanecem intactos.

---

## 6. Estado de Operação Unificado

Um modelo genérico pra qualquer operação de vídeo.

```kotlin
sealed class OperationProgress {
    object Idle : OperationProgress()

    data class Running(
        val percent: Float,   // 0.0 - 1.0
        val stage: String     // "Decodificando...", "Encodando...", etc.
    ) : OperationProgress()

    data class Done(
        val outputUri: String,
        val durationMs: Long = 0
    ) : OperationProgress()

    data class Failed(val error: AppError) : OperationProgress()
}

sealed class AppError {
    abstract val message: String

    data class Codec(
        override val message: String,
        val codecName: String
    ) : AppError()

    data class IO(override val message: String) : AppError()

    object OutOfMemory : AppError() {
        override val message = "Memória insuficiente"
    }

    data class UnsupportedFormat(
        val format: String,
        override val message: String = "Formato não suportado: $format"
    ) : AppError()

    companion object {
        fun fromException(e: Exception): AppError = when (e) {
            is MediaCodec.CodecException -> Codec(e.message ?: "Erro de codec", e.diagnosticInfo)
            is OutOfMemoryError -> OutOfMemory
            is IOException -> IO(e.message ?: "Erro de IO")
            else -> IO(e.message ?: "Erro desconhecido")
        }
    }
}
```

---

## 7. Timeline (MVI)

A timeline é o componente mais complexo — estado altamente interdependente.

### State

```kotlin
data class TimelineState(
    val videoUri: String = "",
    val durationMs: Long = 0,

    // Thumbnails
    val thumbnails: List<Bitmap> = emptyList(),
    val isLoadingThumbs: Boolean = false,

    // Trim handles
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,

    // Playback
    val playbackPositionMs: Long = 0,
    val isPlaying: Boolean = false,

    // Zoom/scroll
    val zoomLevel: Float = 1f,
    val scrollOffsetMs: Long = 0
) {
    val trimDurationMs: Long get() = trimEndMs - trimStartMs
    val playbackProgress: Float
        get() = if (durationMs > 0) playbackPositionMs.toFloat() / durationMs else 0f
    val isPlayheadInRange: Boolean
        get() = playbackPositionMs in trimStartMs..trimEndMs
}
```

### Intent

```kotlin
sealed class TimelineIntent {
    data class Init(val uri: String, val durationMs: Long) : TimelineIntent()
    data class DragStartHandle(val positionMs: Long) : TimelineIntent()
    data class DragEndHandle(val positionMs: Long) : TimelineIntent()
    object HandleReleased : TimelineIntent()
    data class Seek(val positionMs: Long) : TimelineIntent()
    object TogglePlay : TimelineIntent()
    data class PlaybackTick(val positionMs: Long) : TimelineIntent()
    data class Zoom(val level: Float) : TimelineIntent()
    data class Scroll(val offsetMs: Long) : TimelineIntent()
}
```

### Effect

```kotlin
sealed class TimelineEffect {
    data class SeekPlayer(val positionMs: Long) : TimelineEffect()
    object PausePlayer : TimelineEffect()
    object ResumePlayer : TimelineEffect()
    data class TrimRangeChanged(val startMs: Long, val endMs: Long) : TimelineEffect()
    data class HapticFeedback(val type: HapticType) : TimelineEffect()
}

enum class HapticType { TICK, BOUNDARY_HIT }
```

---

## 8. Navegação — Coordinator

```kotlin
@Composable
fun AppCoordinator() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "picker") {

        composable("picker") {
            PickerScreen(
                onVideoSelected = { uri ->
                    navController.navigate("editor/${Uri.encode(uri)}")
                }
            )
        }

        composable("editor/{videoUri}") { backStack ->
            val uri = backStack.arguments?.getString("videoUri") ?: return@composable
            EditorScreen(
                videoUri = uri,
                onNavigateToExport = { outputUri ->
                    navController.navigate("export/${Uri.encode(outputUri)}")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("export/{videoUri}") { backStack ->
            val uri = backStack.arguments?.getString("videoUri") ?: return@composable
            ExportScreen(
                videoUri = uri,
                onDone = { navController.popBackStack("picker", false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## 9. Exemplo de Tela Simples (MVVM) — Settings

```kotlin
data class SettingsState(
    val exportQuality: Quality = Quality.HIGH,
    val darkMode: Boolean = false,
    val cacheSize: String = ""
)

sealed class SettingsEffect {
    object CacheCleared : SettingsEffect()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
) : BaseViewModel<SettingsState, SettingsEffect>(SettingsState()) {

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            reduce {
                copy(
                    exportQuality = prefs.getQuality(),
                    darkMode = prefs.getDarkMode(),
                    cacheSize = prefs.getCacheSize()
                )
            }
        }
    }

    fun setQuality(quality: Quality) {
        viewModelScope.launch {
            prefs.setQuality(quality)
            reduce { copy(exportQuality = quality) }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val new = !currentState.darkMode
            prefs.setDarkMode(new)
            reduce { copy(darkMode = new) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            prefs.clearCache()
            reduce { copy(cacheSize = "0 MB") }
            sendEffect(SettingsEffect.CacheCleared)
        }
    }
}
```

---

## 10. Resumo das Mudanças

| Antes                              | Depois                             | Ganho                  |
| ----------------------------------- | ---------------------------------- | ---------------------- |
| Múltiplos ViewModels se comunicando | EditorViewModel único (MVI)        | Zero sincronização     |
| Pacotes por tipo (viewmodel/, ui/)  | Pacotes por feature                | Coesão                 |
| Lógica no ViewModel                | UseCases com `invoke()`            | Testabilidade          |
| FFmpeg/MediaCodec espalhado         | `VideoEngine` interface            | Flexibilidade          |
| Progresso/erro por feature          | `OperationProgress` unificado      | Consistência           |
| `PreloadViewModel` + redundâncias  | Eliminados                         | Menos código           |
| Navegação no ViewModel             | Coordinator (NavHost) + Effects    | Desacoplamento         |

## Ordem de Execução

1. **BaseViewModel** — fundação compartilhada
2. **OperationProgress + AppError** — modelos unificados
3. **VideoEngine** — abstração sobre MediaCodec/FFmpeg
4. **UseCases** — extrair lógica dos ViewModels atuais
5. **EditorViewModel** — unificar Trim/Audio/Preview
6. **TimelineViewModel** — componente MVI isolado
7. **AppCoordinator** — navegação centralizada
8. **Migrar telas simples** — Picker, Settings como MVVM
