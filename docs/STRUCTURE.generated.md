# ChopCut — Estrutura do Projeto (AUTO-GERADO)

> ⚠️ **NÃO EDITE À MÃO.** Este arquivo é regenerado por `gradle/scripts/scan-structure.sh`
> (automaticamente no commit via `.githooks/pre-commit`). Para mudar a estrutura,
> mude o código — o inventário se atualiza sozinho. A IA deve **conferir** este arquivo,
> não atualizá-lo. Para regras e "onde adicionar cada coisa", veja
> [Regras da Arquitetura](./ChopCut%20-%20Regras%20da%20Arquitetura.md);
> para análise de símbolos/dependências, use o **CodeGraph**.

## Totais

| Métrica | Valor |
|---|---|
| Arquivos `.kt` (com.chopcut) | **14** |
| Tipos (class/object/interface/enum) | **162** |
| Funções (`fun`) | **220** |

## Inventário por arquivo

| Arquivo | Tipos | Funções | Nomes dos tipos |
|---|---:|---:|---|
| `ChopCutApplication.kt` | 2 | 1 | ChopCutApplication |
| `CompressionLevel.kt` | 1 | 0 | CompressionLevel |
| `MainActivity.kt` | 1 | 1 | MainActivity |
| `ThumbnailConfig.kt` | 10 | 1 | Adaptive,Cache,Compression,Concurrency,Dimensions,FileFormats,Quality,ThumbnailConfig,TimelineThumbs,Timing |
| `core/Errors.kt` | 68 | 43 | AdjustTimeRange,AnalysisFailed,Audio,ChangeCodec,CheckConnection,CheckStorageSpace,CheckVideoCompatibility,ChopCutException,Codec,ConcatFailed,ConfigurationError,ContactSupport,ContinueWithoutAudio,CorruptFile,DecoderError,DownloadFailed,EGLInitFailed,EncoderError,Error,ErrorAwareViewModel,ErrorHandler,ErrorResult,ErrorState,Export,ExtractionFailed,FileNotFound,FreeUpSpace,GrantReadPermission,GrantWritePermission,Graphics,InsufficientSpace,InvalidDuration,InvalidTimeRange,InvalidUri,Loading,MetadataError,MuxerError,Network,NoAudioTrack,NoConnection,NoDecoder,NoEncoder,None,NoVideoTrack,OperationResult,Permission,ReadDenied,RecoveryStrategy,Retry,RetryWithLowerQuality,SelectAnotherVideo,ShaderCompilationError,StorageError,Success,SurfaceError,TranscodeFailed,TrimFailed,TryDifferentVideo,UiError,Unknown,UnsupportedFormat,Video,WriteDenied |
| `core/Models.kt` | 23 | 23 | AppliedCutsRegistry,AspectRatioPreset,ExtractionStage,FilterType,PerformanceEvent,PerformanceMetrics,SizePreset,ThumbnailExtractionProgress,ThumbnailFormat,ThumbnailQuality,ThumbnailScaleMode,ThumbnailSettings,TimeRange,Transform,VideoCodec,VideoInfo,VideoRange,VideoSize |
| `core/Theme.kt` | 5 | 11 | ChopCutAnimation,ChopCutAnimationSpec,ChopCutEasing,ChopCutSpacing,ChopCutTransition |
| `core/Utils.kt` | 18 | 44 | ActivityLogger,AppActivity,AudioExport,DispatcherProvider,FileLoggingTree,FileNameUtils,FormatUtils,LocalFileLoggingTree,RangeUtils,StripAssembly,ThumbnailExtraction,ThumbnailUtils,TimelineLogger,TimeToken,TimeTracker,TimeUtils,Trim,VideoConstraints |
| `data/ThumbnailExtraction.kt` | 3 | 3 | ExtractionProgressState,ExtractionResult,ThumbnailExtraction |
| `data/VideoEngine.kt` | 9 | 37 | CodecCapabilities,Completed,CopyPipeline,Failed,InProgress,TransformerPipeline,TrimProgress,VideoRepository,VideoSource |
| `ui/SharedComponents.kt` | 0 | 14 | — |
| `ui/editor/TimelineFeature.kt` | 9 | 24 | Error,Exporting,ExportUiState,Idle,MarkerInterval,Success,TimelineViewModel,TimelineViewModelFactory,VideoDetails |
| `ui/home/HomeFeature.kt` | 13 | 17 | Error,GallerySortOrder,GalleryVideo,HomeFeatureItem,HomeUiState,HomeViewModel,HomeViewModelFactory,Initial,Loading,Processing,Success,VideoLoaded |
| `ui/navigation/ChopCutNavGraph.kt` | 0 | 1 | — |

