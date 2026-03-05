/**
 * @file Entidades TypeScript/JavaScript para mapeamento de logs do ChopCut
 * @module entities
 * @description Define todas as classes e interfaces que representam o estado da aplicação
 */

/**
 * Estado do GLRenderer (renderizador OpenGL)
 * @class GLRendererState
 */
class GLRendererState {
  constructor() {
    /** @type {boolean} Indica se o renderizador está inicializado */
    this.isInitialized = false;
    /** @type {number|undefined} Handle da posição */
    this.positionHandle = undefined;
    /** @type {number|undefined} Handle das coordenadas de textura */
    this.textureCoordHandle = undefined;
    /** @type {number|undefined} Handle da matriz MVP */
    this.mvpMatrixHandle = undefined;
    /** @type {number|undefined} Handle da matriz de textura */
    this.textureMatrixHandle = undefined;
    /** @type {number|undefined} Handle do sampler de textura */
    this.textureSamplerHandle = undefined;
    /** @type {number|undefined} ID da textura externa */
    this.externalTextureId = undefined;
    /** @type {string|undefined} Último erro ocorrido */
    this.lastError = undefined;
  }
}

/**
 * Estado do SurfaceBridge (bridge de superfície EGL)
 * @class SurfaceBridgeState
 */
class SurfaceBridgeState {
  constructor() {
    /** @type {boolean} Indica se a bridge está inicializada */
    this.isInitialized = false;
    /** @type {string|undefined} Versão do EGL */
    this.eglVersion = undefined;
    /** @type {boolean|undefined} Indica se a superfície do decoder foi criada */
    this.decoderSurfaceCreated = undefined;
    /** @type {boolean|undefined} Indica se a superfície do encoder foi criada */
    this.encoderSurfaceCreated = undefined;
    /** @type {number|undefined} Largura da superfície do encoder */
    this.encoderWidth = undefined;
    /** @type {number|undefined} Altura da superfície do encoder */
    this.encoderHeight = undefined;
    /** @type {string|undefined} Último erro ocorrido */
    this.lastError = undefined;
  }
}

/**
 * Contexto gráfico completo
 * @class GraphicsContext
 */
class GraphicsContext {
  constructor() {
    /** @type {GLRendererState} Estado do renderizador OpenGL */
    this.glRenderer = new GLRendererState();
    /** @type {SurfaceBridgeState} Estado da bridge de superfície */
    this.surfaceBridge = new SurfaceBridgeState();
  }
}

/**
 * Informações de um vídeo
 * @class VideoInfo
 */
class VideoInfo {
  /**
   * Cria uma nova instância de VideoInfo
   * @param {string} [uri=''] - URI do vídeo
   * @param {string} [id=''] - ID do vídeo
   */
  constructor(uri = '', id = '') {
    /** @type {string} URI do vídeo */
    this.uri = uri;
    /** @type {string} ID do vídeo */
    this.id = id;
    /** @type {number|undefined} Duração em milissegundos */
    this.duration = undefined;
    /** @type {number|undefined} Largura em pixels */
    this.width = undefined;
    /** @type {number|undefined} Altura em pixels */
    this.height = undefined;
  }
}

/**
 * Estado da BottomSheetGallery (galeria de vídeos)
 * @class BottomSheetGalleryState
 */
class BottomSheetGalleryState {
  constructor() {
    /** @type {VideoInfo|undefined} Vídeo selecionado */
    this.selectedVideo = undefined;
    /** @type {boolean} Indica se está expandido */
    this.isExpanded = false;
    /** @type {VideoInfo[]} Lista de vídeos disponíveis */
    this.videos = [];
  }
}

/**
 * Estado do TimelineEditor (editor de timeline)
 * @class TimelineEditorState
 */
class TimelineEditorState {
  constructor() {
    /** @type {number} Velocidade de scroll em px/ms */
    this.scrollVelocity = 0;
    /** @type {string|undefined} Erro do ExoPlayer */
    this.exoPlayerError = undefined;
    /** @type {number|undefined} Timestamp atual */
    this.currentTimestamp = undefined;
    /** @type {boolean} Indica se está em scroll */
    this.isScrolling = false;
  }
}

/**
 * Estado de pré-carregamento
 * @class PreloadState
 */
class PreloadState {
  constructor() {
    /** @type {string|undefined} URI atual */
    this.uri = undefined;
    /** @type {number} Quantidade de strips para pré-carregar */
    this.stripsToPreload = 0;
    /** @type {string|undefined} URI ativa */
    this.activeUri = undefined;
    /** @type {string} Estado atual */
    this.currentState = '';
    /** @type {number} Strips carregados */
    this.stripsLoaded = 0;
    /** @type {number} Total de segmentos */
    this.totalSegments = 0;
    /** @type {boolean} Indica se está pronto */
    this.isReady = false;
    /** @type {number} Limiar para considerar pronto */
    this.threshold = 0;
  }
}

/**
 * Representa uma strip (segmento de vídeo)
 * @class Strip
 */
class Strip {
  /**
   * Cria uma nova Strip
   * @param {string} id - ID da strip
   * @param {string} uri - URI do vídeo
   * @param {number} startIndex - Índice inicial
   * @param {number} endIndex - Índice final
   */
  constructor(id, uri, startIndex, endIndex) {
    /** @type {string} ID da strip */
    this.id = id;
    /** @type {string} URI do vídeo */
    this.uri = uri;
    /** @type {number} Índice inicial */
    this.startIndex = startIndex;
    /** @type {number} Índice final */
    this.endIndex = endIndex;
    /** @type {string|undefined} URI do thumbnail */
    this.thumbnailUri = undefined;
    /** @type {boolean} Indica se está carregado */
    this.isLoaded = false;
    /** @type {boolean} Indica se está em pré-carregamento */
    this.isPreloading = false;
  }
}

/**
 * Entrada de cache
 * @class CacheEntry
 */
class CacheEntry {
  /**
   * Cria uma nova entrada de cache
   * @param {string} key - Chave da entrada
   * @param {*} data - Dados armazenados
   * @param {number} [size=0] - Tamanho em bytes
   */
  constructor(key, data, size = 0) {
    /** @type {string} Chave da entrada */
    this.key = key;
    /** @type {*} Dados armazenados */
    this.data = data;
    /** @type {Date} Timestamp de criação */
    this.timestamp = new Date();
    /** @type {number} Quantidade de acessos */
    this.accessCount = 0;
    /** @type {Date} Último acesso */
    this.lastAccessed = new Date();
    /** @type {number} Tamanho em bytes */
    this.size = size;
  }
}

/**
 * Estado do cache
 * @class CacheState
 */
class CacheState {
  constructor() {
    /** @type {CacheEntry[]} Entradas do cache */
    this.entries = [];
    /** @type {number} Tamanho total em bytes */
    this.totalSize = 0;
    /** @type {number} Máximo de entradas */
    this.maxEntries = 100;
    /** @type {number} Tamanho máximo em bytes (100MB) */
    this.maxSize = 104857600;
    /** @type {number} Taxa de cache hit */
    this.hitRate = 0;
    /** @type {number} Taxa de cache miss */
    this.missRate = 0;
  }
}

/**
 * Estado de thumbnails
 * @class ThumbnailState
 */
class ThumbnailState {
  constructor() {
    /** @type {Strip[]} Lista de strips */
    this.strips = [];
    /** @type {number} Total de segmentos */
    this.totalSegments = 0;
    /** @type {boolean} Indica se está carregando */
    this.isLoading = false;
    /** @type {Map<string, string>} Mapa de erros por ID */
    this.errors = new Map();
  }
}

/**
 * Erro do ExoPlayer
 * @class ExoPlayerError
 */
class ExoPlayerError {
  /**
   * Cria um novo erro do ExoPlayer
   * @param {string} code - Código do erro
   * @param {string} message - Mensagem do erro
   */
  constructor(code, message) {
    /** @type {string} Código do erro */
    this.code = code;
    /** @type {string} Mensagem do erro */
    this.message = message;
    /** @type {Date} Timestamp do erro */
    this.timestamp = new Date();
  }
}

/**
 * Estado do ExoPlayer (player de vídeo)
 * @class ExoPlayerState
 */
class ExoPlayerState {
  constructor() {
    /** @type {boolean} Indica se está reproduzindo */
    this.isPlaying = false;
    /** @type {number} Posição atual em milissegundos */
    this.currentPosition = 0;
    /** @type {number} Duração em milissegundos */
    this.duration = 0;
    /** @type {boolean} Indica se está em buffer */
    this.isBuffering = false;
    /** @type {ExoPlayerError|undefined} Erro atual */
    this.error = undefined;
  }
}

/**
 * Segmento da timeline
 * @class TimelineSegment
 */
class TimelineSegment {
  /**
   * Cria um novo segmento da timeline
   * @param {string} id - ID do segmento
   * @param {number} startTime - Tempo de início em milissegundos
   * @param {number} endTime - Tempo de fim em milissegundos
   * @param {Strip} strip - Strip associada
   */
  constructor(id, startTime, endTime, strip) {
    /** @type {string} ID do segmento */
    this.id = id;
    /** @type {number} Tempo de início em milissegundos */
    this.startTime = startTime;
    /** @type {number} Tempo de fim em milissegundos */
    this.endTime = endTime;
    /** @type {Strip} Strip associada */
    this.strip = strip;
    /** @type {boolean} Indica se está selecionado */
    this.isSelected = false;
  }
}

/**
 * Estado da timeline
 * @class TimelineState
 */
class TimelineState {
  constructor() {
    /** @type {TimelineSegment[]} Segmentos da timeline */
    this.segments = [];
    /** @type {TimelineSegment|undefined} Segmento selecionado */
    this.selectedSegment = undefined;
    /** @type {number} Nível de zoom */
    this.zoomLevel = 1;
    /** @type {number} Posição do scroll */
    this.scrollPosition = 0;
  }
}

/**
 * Entrada de log parseada
 * @class LogEntry
 */
class LogEntry {
  /**
   * Cria uma nova entrada de log
   * @param {Date} [timestamp] - Timestamp do log
   * @param {'debug'|'info'|'warn'|'error'|'verbose'} [level] - Nível do log
   * @param {string} [tag] - Tag do log
   * @param {string} [message] - Mensagem do log
   * @param {string} [file] - Arquivo de origem
   * @param {number} [line] - Linha do arquivo
   */
  constructor(timestamp, level, tag, message, file, line) {
    /** @type {Date} Timestamp do log */
    this.timestamp = timestamp || new Date();
    /** @type {'debug'|'info'|'warn'|'error'|'verbose'} Nível do log */
    this.level = level || 'info';
    /** @type {string} Tag do log */
    this.tag = tag || '';
    /** @type {string} Mensagem do log */
    this.message = message || '';
    /** @type {string|undefined} Arquivo de origem */
    this.file = file;
    /** @type {number|undefined} Linha do arquivo */
    this.line = line;
  }
}

/**
 * Métricas de performance
 * @class PerformanceMetrics
 */
class PerformanceMetrics {
  constructor() {
    /** @type {number} Frames por segundo */
    this.fps = 0;
    /** @type {number} Tempo de frame em milissegundos */
    this.frameTime = 0;
    /** @type {number} Uso de memória em bytes */
    this.memoryUsage = 0;
    /** @type {number} Uso de CPU em porcentagem */
    this.cpuUsage = 0;
    /** @type {number} Uso de GPU em porcentagem */
    this.gpuUsage = 0;
  }
}

/**
 * Configuração do monitor
 * @class MonitorConfig
 */
class MonitorConfig {
  constructor() {
    /** @type {number} Nível de verbosidade */
    this.verbosityLevel = 2;
    /** @type {string} Caminho do arquivo de log */
    this.logFilePath = './logs';
    /** @type {boolean} Habilita log no console */
    this.enableConsole = true;
    /** @type {boolean} Habilita log em arquivo */
    this.enableFileLogging = true;
    /** @type {number} Tamanho máximo do arquivo em bytes (10MB) */
    this.maxLogFileSize = 10485760;
    /** @type {number} Número de rotações de log */
    this.logRotationCount = 5;
  }
}

/**
 * Estado completo da aplicação
 * @class AppState
 * @description Contém todos os estados da aplicação ChopCut
 */
class AppState {
  constructor() {
    /** @type {GraphicsContext} Contexto gráfico */
    this.graphics = new GraphicsContext();
    /** @type {BottomSheetGalleryState} Estado da galeria */
    this.bottomSheetGallery = new BottomSheetGalleryState();
    /** @type {TimelineEditorState} Estado do editor */
    this.timelineEditor = new TimelineEditorState();
    /** @type {PreloadState} Estado de pré-carregamento */
    this.preload = new PreloadState();
    /** @type {CacheState} Estado do cache */
    this.cache = new CacheState();
    /** @type {ThumbnailState} Estado de thumbnails */
    this.thumbnail = new ThumbnailState();
    /** @type {ExoPlayerState} Estado do player */
    this.exoPlayer = new ExoPlayerState();
    /** @type {TimelineState} Estado da timeline */
    this.timeline = new TimelineState();
  }
}

module.exports = {
  GLRendererState,
  SurfaceBridgeState,
  GraphicsContext,
  VideoInfo,
  BottomSheetGalleryState,
  TimelineEditorState,
  PreloadState,
  Strip,
  CacheEntry,
  CacheState,
  ThumbnailState,
  ExoPlayerError,
  ExoPlayerState,
  TimelineSegment,
  TimelineState,
  LogEntry,
  PerformanceMetrics,
  MonitorConfig,
  AppState
};
