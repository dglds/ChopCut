export interface GLRendererState {
  isInitialized: boolean;
  positionHandle?: number;
  textureCoordHandle?: number;
  mvpMatrixHandle?: number;
  textureMatrixHandle?: number;
  textureSamplerHandle?: number;
  externalTextureId?: number;
  lastError?: string;
}

export interface SurfaceBridgeState {
  isInitialized: boolean;
  eglVersion?: string;
  decoderSurfaceCreated?: boolean;
  encoderSurfaceCreated?: boolean;
  encoderWidth?: number;
  encoderHeight?: number;
  lastError?: string;
}

export interface GraphicsContext {
  glRenderer: GLRendererState;
  surfaceBridge: SurfaceBridgeState;
}

export interface VideoInfo {
  uri: string;
  id: string;
  duration?: number;
  width?: number;
  height?: number;
}

export interface BottomSheetGalleryState {
  selectedVideo?: VideoInfo;
  isExpanded: boolean;
  videos: VideoInfo[];
}

export interface TimelineEditorState {
  scrollVelocity: number;
  exoPlayerError?: string;
  currentTimestamp?: number;
  isScrolling: boolean;
}

export interface PreloadState {
  uri?: string;
  stripsToPreload: number;
  activeUri?: string;
  currentState: string;
  stripsLoaded: number;
  totalSegments: number;
  isReady: boolean;
  threshold: number;
}

export interface Strip {
  id: string;
  uri: string;
  startIndex: number;
  endIndex: number;
  thumbnailUri?: string;
  isLoaded: boolean;
  isPreloading: boolean;
}

export interface CacheEntry {
  key: string;
  data: unknown;
  timestamp: Date;
  accessCount: number;
  lastAccessed: Date;
  size: number;
}

export interface CacheState {
  entries: CacheEntry[];
  totalSize: number;
  maxEntries: number;
  maxSize: number;
  hitRate: number;
  missRate: number;
}

export interface ThumbnailState {
  strips: Strip[];
  totalSegments: number;
  isLoading: boolean;
  errors: Map<string, string>;
}

export interface ExoPlayerState {
  isPlaying: boolean;
  currentPosition: number;
  duration: number;
  isBuffering: boolean;
  error?: ExoPlayerError;
}

export interface ExoPlayerError {
  code: string;
  message: string;
  timestamp: Date;
}

export interface TimelineSegment {
  id: string;
  startTime: number;
  endTime: number;
  strip: Strip;
  isSelected: boolean;
}

export interface TimelineState {
  segments: TimelineSegment[];
  selectedSegment?: TimelineSegment;
  zoomLevel: number;
  scrollPosition: number;
}

export interface LogEntry {
  timestamp: Date;
  level: LogLevel;
  tag: string;
  message: string;
  file?: string;
  line?: number;
}

export type LogLevel = 'verbose' | 'debug' | 'info' | 'warn' | 'error';

export interface AppState {
  graphics: GraphicsContext;
  bottomSheetGallery: BottomSheetGalleryState;
  timelineEditor: TimelineEditorState;
  preload: PreloadState;
  cache: CacheState;
  thumbnail: ThumbnailState;
  exoPlayer: ExoPlayerState;
  timeline: TimelineState;
}

export interface PerformanceMetrics {
  fps: number;
  frameTime: number;
  memoryUsage: number;
  cpuUsage: number;
  gpuUsage: number;
}

export interface MonitorConfig {
  verbosityLevel: number;
  logFilePath: string;
  enableConsole: boolean;
  enableFileLogging: boolean;
  maxLogFileSize: number;
  logRotationCount: number;
}
