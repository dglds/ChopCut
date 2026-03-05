const { 
  AppState, 
  GraphicsContext, 
  BottomSheetGalleryState, 
  TimelineEditorState,
  PreloadState,
  ExoPlayerState,
  LogEntry
} = require('./entities');

/**
 * @file Sistema de parsing de entidades para logs
 * @module EntityParser
 * @description Contém classes para parsear logs e convertê-los em objetos de estado
 */

/**
 * Parser base para entidades
 * @abstract
 * @class EntityParser
 * @description Classe abstrata que define a interface para parsers de entidades
 */
class EntityParser {
  /**
   * Verifica se o log pode ser parseado por este parser
   * @abstract
   * @param {string} log - Linha de log
   * @returns {boolean} True se o parser pode processar este log
   * @throws {Error} Se não for implementado
   */
  canParse(log) {
    throw new Error('canParse must be implemented');
  }

  /**
   * Parseia o log e retorna atualização parcial do estado
   * @abstract
   * @param {string} log - Linha de log
   * @returns {Object|null} Objeto com atualização parcial do estado ou null
   * @throws {Error} Se não for implementado
   */
  parse(log) {
    throw new Error('parse must be implemented');
  }
}

/**
 * Parser para logs de GLRenderer
 * @class GLRendererParser
 * @extends EntityParser
 * @description Parseia logs relacionados ao renderizador OpenGL
 */
class GLRendererParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /GLRenderer/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto com atualização de graphics.glRenderer
   */
  parse(log) {
    const state = {
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

/**
 * Parser para logs de BottomSheetGallery
 * @class BottomSheetGalleryParser
 * @extends EntityParser
 * @description Parseia logs da galeria de vídeos
 */
class BottomSheetGalleryParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /BottomSheetGallery/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto com atualização de bottomSheetGallery
   */
  parse(log) {
    const state = {
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

/**
 * Parser para logs de TimelineEditor
 * @class TimelineEditorParser
 * @extends EntityParser
 * @description Parseia logs do editor de timeline
 */
class TimelineEditorParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /TimelineEditor/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto com atualização de timelineEditor
   */
  parse(log) {
    const state = {
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

/**
 * Parser para logs de PreloadViewModel
 * @class PreloadViewModelParser
 * @extends EntityParser
 * @description Parseia logs do sistema de pré-carregamento
 */
class PreloadViewModelParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /PreloadViewModel/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto com atualização de preload
   */
  parse(log) {
    const state = {
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

/**
 * Parser para logs de ExoPlayer
 * @class ExoPlayerParser
 * @extends EntityParser
 * @description Parseia logs do player de vídeo
 */
class ExoPlayerParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /ExoPlayer/i.test(log) || /MediaExtractor/i.test(log) || /MediaCodec/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto com atualização de exoPlayer
   */
  parse(log) {
    const state = {
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

/**
 * Parser para logs de Cache
 * @class CacheParser
 * @extends EntityParser
 * @description Parseia logs de cache e thumbnails
 */
class CacheParser extends EntityParser {
  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {boolean}
   */
  canParse(log) {
    return /cache/i.test(log) || /preload/i.test(log) || /thumbnail.*load/i.test(log);
  }

  /**
   * @inheritDoc
   * @param {string} log 
   * @returns {Object} Objeto vazio (parser placeholder)
   */
  parse(log) {
    return {};
  }
}

/**
 * Parser principal de logs
 * @class LogParser
 * @description Orquestra o parsing de logs usando múltiplos parsers específicos
 */
class LogParser {
  constructor() {
    /** @type {EntityParser[]} Lista de parsers registrados */
    this.parsers = [
      new GLRendererParser(),
      new BottomSheetGalleryParser(),
      new TimelineEditorParser(),
      new PreloadViewModelParser(),
      new ExoPlayerParser(),
      new CacheParser()
    ];
  }

  /**
   * Parseia um log e retorna um LogEntry
   * @param {string} log - Linha de log
   * @returns {LogEntry|null} Entrada de log parseada
   */
  parseLog(log) {
    const timestampMatch = log.match(/(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+)/);
    const timestamp = timestampMatch ? new Date(timestampMatch[1]) : new Date();

    let level = 'info';
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

    return new LogEntry(timestamp, level, tag, message, file, line);
  }

  /**
   * Parseia o log e retorna atualização de estado
   * @param {string} log - Linha de log
   * @returns {Object|null} Atualização parcial do estado ou null
   */
  parseState(log) {
    for (const parser of this.parsers) {
      if (parser.canParse(log)) {
        return parser.parse(log);
      }
    }
    return null;
  }

  /**
   * Mescla atualização parcial no estado completo
   * @param {AppState} appState - Estado atual da aplicação
   * @param {Object} partial - Atualização parcial
   * @returns {AppState} Estado mesclado
   */
  mergeState(appState, partial) {
    const result = { ...appState };

    for (const key in partial) {
      if (key === 'graphics') {
        result.graphics = { ...result.graphics, ...partial.graphics };
        if (partial.graphics.glRenderer) {
          result.graphics.glRenderer = { ...result.graphics.glRenderer, ...partial.graphics.glRenderer };
        }
        if (partial.graphics.surfaceBridge) {
          result.graphics.surfaceBridge = { ...result.graphics.surfaceBridge, ...partial.graphics.surfaceBridge };
        }
      } else if (result[key]) {
        result[key] = { ...result[key], ...partial[key] };
      } else {
        result[key] = partial[key];
      }
    }

    return result;
  }
}

/**
 * Cria estado inicial da aplicação
 * @returns {AppState} Nova instância de AppState com valores padrão
 */
function createInitialState() {
  return new AppState();
}

module.exports = {
  LogParser,
  createInitialState,
  GLRendererParser,
  BottomSheetGalleryParser,
  TimelineEditorParser,
  PreloadViewModelParser,
  ExoPlayerParser,
  CacheParser
};
