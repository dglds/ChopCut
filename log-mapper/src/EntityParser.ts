import type { 
  LogEntry, 
  GraphicsContext, 
  VideoInfo, 
  BottomSheetGalleryState,
  TimelineEditorState,
  PreloadState,
  Strip,
  CacheEntry,
  ThumbnailState,
  ExoPlayerState,
  AppState,
  LogLevel
} from './entities';

export interface EntityParser {
  canParse(log: string): boolean;
  parse(log: string): Partial<AppState> | null;
}

export class GLRendererParser implements EntityParser {
  canParse(log: string): boolean {
    return /GLRenderer/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    const state: GraphicsContext = {
      glRenderer: {
        isInitialized: /initialized successfully/i.test(log)
      }
    };

    const positionMatch = log.match(/pos=(\d+)/);
    if (positionMatch) state.glRenderer.positionHandle = parseInt(positionMatch[1]);

    const texCoordMatch = log.match(/texCoord=(\d+)/);
    if (texCoordMatch) state.glRenderer.textureCoordHandle = parseInt(texCoordMatch[1]);

    const mvpMatch = log.match(/mvp=(\d+)/);
    if (mvpMatch) state.glRenderer.mvpMatrixHandle = parseInt(mvpMatch[1]);

    const texMatMatch = log.match(/texMat=(\d+)/);
    if (texMatMatch) state.glRenderer.textureMatrixHandle = parseInt(texMatMatch[1]);

    const samplerMatch = log.match(/sampler=(\d+)/);
    if (samplerMatch) state.glRenderer.textureSamplerHandle = parseInt(samplerMatch[1]);

    const textureIdMatch = log.match(/External texture ID:\s*(\d+)/);
    if (textureIdMatch) state.glRenderer.externalTextureId = parseInt(textureIdMatch[1]);

    const errorMatch = log.match(/error:\s*(.+)/i);
    if (errorMatch) state.glRenderer.lastError = errorMatch[1];

    return { graphics: state };
  }
}

export class BottomSheetGalleryParser implements EntityParser {
  canParse(log: string): boolean {
    return /BottomSheetGallery/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    const state: BottomSheetGalleryState = {
      isExpanded: true,
      videos: []
    };

    const videoSelectedMatch = log.match(/VIDEO SELECTED/i);
    if (videoSelectedMatch) {
      state.selectedVideo = { uri: '', id: '' };
    }

    const uriMatch = log.match(/uri:\s*(.+)/);
    if (uriMatch && state.selectedVideo) {
      state.selectedVideo.uri = uriMatch[1].trim();
    }

    const idMatch = log.match(/id:\s*(.+)/);
    if (idMatch && state.selectedVideo) {
      state.selectedVideo.id = idMatch[1].trim();
    }

    return { bottomSheetGallery: state };
  }
}

export class TimelineEditorParser implements EntityParser {
  canParse(log: string): boolean {
    return /TimelineEditor/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    const state: TimelineEditorState = {
      scrollVelocity: 0,
      isScrolling: false
    };

    const velocityMatch = log.match(/Scroll velocity:\s*(-?\d+)/);
    if (velocityMatch) {
      state.scrollVelocity = parseInt(velocityMatch[1]);
      state.isScrolling = Math.abs(state.scrollVelocity) > 0;
    }

    const errorMatch = log.match(/ExoPlayer error:\s*(.+)/);
    if (errorMatch) {
      state.exoPlayerError = errorMatch[1];
    }

    return { timelineEditor: state };
  }
}

export class PreloadViewModelParser implements EntityParser {
  canParse(log: string): boolean {
    return /PreloadViewModel/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    const state: PreloadState = {
      stripsToPreload: 0,
      currentState: '',
      stripsLoaded: 0,
      totalSegments: 0,
      isReady: false,
      threshold: 0
    };

    const uriMatch = log.match(/uri:\s*(.+)/);
    if (uriMatch) state.uri = uriMatch[1].trim();

    const stripsToPreloadMatch = log.match(/stripsToPreload:\s*(\d+)/);
    if (stripsToPreloadMatch) state.stripsToPreload = parseInt(stripsToPreloadMatch[1]);

    const activeUriMatch = log.match(/activeUri:\s*(.+)/);
    if (activeUriMatch) state.activeUri = activeUriMatch[1].trim();

    const currentStateMatch = log.match(/currentState:\s*(.+)/);
    if (currentStateMatch) state.currentState = currentStateMatch[1].trim();

    const stripsLoadedMatch = log.match(/strips loaded:\s*(\d+)/);
    if (stripsLoadedMatch) state.stripsLoaded = parseInt(stripsLoadedMatch[1]);

    const totalSegmentsMatch = log.match(/totalSegments:\s*(\d+)/);
    if (totalSegmentsMatch) state.totalSegments = parseInt(totalSegmentsMatch[1]);

    const readyMatch = log.match(/ready=(true|false)/);
    if (readyMatch) state.isReady = readyMatch[1] === 'true';

    const thresholdMatch = log.match(/threshold=(\d+)/);
    if (thresholdMatch) state.threshold = parseInt(thresholdMatch[1]);

    return { preload: state };
  }
}

export class ExoPlayerParser implements EntityParser {
  canParse(log: string): boolean {
    return /ExoPlayer/i.test(log) || /MediaExtractor/i.test(log) || /MediaCodec/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    const state: ExoPlayerState = {
      isPlaying: false,
      currentPosition: 0,
      duration: 0,
      isBuffering: false
    };

    const errorMatch = log.match(/error:\s*(.+)/i);
    if (errorMatch) {
      state.error = {
        code: 'UNKNOWN',
        message: errorMatch[1],
        timestamp: new Date()
      };
    }

    return { exoPlayer: state };
  }
}

export class CacheParser implements EntityParser {
  canParse(log: string): boolean {
    return /cache/i.test(log) || /preload/i.test(log) || /thumbnail.*load/i.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    return {};
  }
}

export class LogParser {
  private parsers: EntityParser[] = [
    new GLRendererParser(),
    new BottomSheetGalleryParser(),
    new TimelineEditorParser(),
    new PreloadViewModelParser(),
    new ExoPlayerParser(),
    new CacheParser()
  ];

  parseLog(log: string): LogEntry | null {
    const timestampMatch = log.match(/(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+)/);
    const timestamp = timestampMatch ? new Date(timestampMatch[1]) : new Date();

    let level: LogLevel = 'info';
    if (log.includes('error') || log.includes('ERROR') || log.includes('Timber.e')) level = 'error';
    else if (log.includes('warn') || log.includes('WARN') || log.includes('Timber.w')) level = 'warn';
    else if (log.includes('debug') || log.includes('DEBUG') || log.includes('Timber.d')) level = 'debug';
    else if (log.includes('verbose') || log.includes('VERBOSE') || log.includes('Timber.v')) level = 'verbose';

    const tagMatch = log.match(/Log\.(?:d|e|w|i|v)\("([^"]+)"/);
    const tag = tagMatch ? tagMatch[1] : '';

    let message = log;
    const messageMatch = log.match(/,\s*"([^"]+)"(?:,\s*([^)]+))?$/);
    if (messageMatch) {
      message = messageMatch[1];
    }

    const fileMatch = log.match(/(.+\.kt):(\d+):/);
    const file = fileMatch ? fileMatch[1] : undefined;
    const line = fileMatch ? parseInt(fileMatch[2]) : undefined;

    return {
      timestamp,
      level,
      tag,
      message,
      file,
      line
    };
  }

  parseState(log: string): Partial<AppState> | null {
    for (const parser of this.parsers) {
      if (parser.canParse(log)) {
        return parser.parse(log);
      }
    }
    return null;
  }

  mergeState(appState: AppState, partial: Partial<AppState>): AppState {
    return {
      ...appState,
      ...partial,
      graphics: {
        ...appState.graphics,
        ...partial.graphics,
        glRenderer: {
          ...appState.graphics.glRenderer,
          ...partial.graphics?.glRenderer
        },
        surfaceBridge: {
          ...appState.graphics.surfaceBridge,
          ...partial.graphics?.surfaceBridge
        }
      },
      bottomSheetGallery: {
        ...appState.bottomSheetGallery,
        ...partial.bottomSheetGallery
      },
      timelineEditor: {
        ...appState.timelineEditor,
        ...partial.timelineEditor
      },
      preload: {
        ...appState.preload,
        ...partial.preload
      },
      cache: {
        ...appState.cache,
        ...partial.cache
      },
      thumbnail: {
        ...appState.thumbnail,
        ...partial.thumbnail
      },
      exoPlayer: {
        ...appState.exoPlayer,
        ...partial.exoPlayer
      },
      timeline: {
        ...appState.timeline,
        ...partial.timeline
      }
    };
  }
}

export function createInitialState(): AppState {
  return {
    graphics: {
      glRenderer: {
        isInitialized: false
      },
      surfaceBridge: {
        isInitialized: false
      }
    },
    bottomSheetGallery: {
      isExpanded: false,
      videos: []
    },
    timelineEditor: {
      scrollVelocity: 0,
      isScrolling: false
    },
    preload: {
      stripsToPreload: 0,
      currentState: '',
      stripsLoaded: 0,
      totalSegments: 0,
      isReady: false,
      threshold: 0
    },
    cache: {
      entries: [],
      totalSize: 0,
      maxEntries: 100,
      maxSize: 104857600,
      hitRate: 0,
      missRate: 0
    },
    thumbnail: {
      strips: [],
      totalSegments: 0,
      isLoading: false,
      errors: new Map()
    },
    exoPlayer: {
      isPlaying: false,
      currentPosition: 0,
      duration: 0,
      isBuffering: false
    },
    timeline: {
      segments: [],
      zoomLevel: 1,
      scrollPosition: 0
    }
  };
}
