# Log Mapper JS OO

Ferramenta em JavaScript Orientado a Objetos (com JSDoc) para analisar e categorizar logs do projeto ChopCut e mapear entidades da aplicação.

## 📋 Recursos

- ✅ Analisa múltiplos arquivos de log
- ✅ Categoriza logs por contexto (Graphics, UI, Cache, Media, Timeline, Error)
- ✅ Mapeia logs para entidades JavaScript tipadas (com JSDoc)
- ✅ Gera relatórios detalhados com estatísticas
- ✅ Sistema de parsing extensível para novos padrões de log
- ✅ Suporta diferentes formatos de log (Timber, Log Android, etc.)
- ✅ Documentação completa com JSDoc para melhor autocomplete e manutenção

## 🚀 Instalação

```bash
cd log-mapper-js
npm install  # (opcional, se necessário no futuro)
```

## 📖 Uso

### Análise de Logs

```bash
npm run start
# ou
node index.js
```

Isso irá:
1. Escanear os arquivos de log configurados
2. Analisar e categorizar cada entrada
3. Imprimir um relatório no terminal
4. Salvar um relatório detalhado em `log-report.md`

### Demo de Entidades

```bash
npm run demo
# ou
node demo-entities.js
```

Isso irá:
1. Parsear logs de exemplo
2. Mapear para entidades JavaScript
3. Imprimir o estado da aplicação
4. Salvar o estado em `app-state.json`

## 📁 Estrutura

### Arquivos Principais

- **`index.js`** - Ponto de entrada principal (análise de logs)
- **`demo-entities.js`** - Demo de mapeamento de entidades
- **`LogMapper.js`** - Classe principal para análise de logs
- **`entities.js`** - Definições de todas as entidades JavaScript (com JSDoc)
- **`EntityParser.js`** - Sistema de parsing de entidades (com JSDoc)

### Arquivos Gerados

- **`log-report.md`** - Relatório detalhado dos logs analisados
- **`app-state.json`** - Estado completo da aplicação em JSON

## 📊 Categorias de Log

- **Graphics** - OpenGL, GLRenderer, SurfaceBridge
- **UI Components** - Componentes da interface (BottomSheet, Timeline, etc.)
- **Cache** - Operações de cache e preload
- **Media** - Playback e processamento de mídia
- **Timeline** - Operações da timeline
- **Error** - Logs de erro e exceções

## 🏗️ Entidades Mapeadas

### Graphics

#### GLRendererState
```javascript
class GLRendererState {
  isInitialized: boolean
  positionHandle?: number
  textureCoordHandle?: number
  mvpMatrixHandle?: number
  textureMatrixHandle?: number
  textureSamplerHandle?: number
  externalTextureId?: number
  lastError?: string
}
```

#### SurfaceBridgeState
```javascript
class SurfaceBridgeState {
  isInitialized: boolean
  eglVersion?: string
  decoderSurfaceCreated?: boolean
  encoderSurfaceCreated?: boolean
  encoderWidth?: number
  encoderHeight?: number
  lastError?: string
}
```

### UI

#### BottomSheetGalleryState
```javascript
class BottomSheetGalleryState {
  selectedVideo?: VideoInfo
  isExpanded: boolean
  videos: VideoInfo[]
}
```

#### TimelineEditorState
```javascript
class TimelineEditorState {
  scrollVelocity: number
  exoPlayerError?: string
  currentTimestamp?: number
  isScrolling: boolean
}
```

### State Management

#### PreloadState
```javascript
class PreloadState {
  uri?: string
  stripsToPreload: number
  activeUri?: string
  currentState: string
  stripsLoaded: number
  totalSegments: number
  isReady: boolean
  threshold: number
}
```

#### CacheState
```javascript
class CacheState {
  entries: CacheEntry[]
  totalSize: number
  maxEntries: number
  maxSize: number
  hitRate: number
  missRate: number
}
```

#### ThumbnailState
```javascript
class ThumbnailState {
  strips: Strip[]
  totalSegments: number
  isLoading: boolean
  errors: Map<string, string>
}
```

#### ExoPlayerState
```javascript
class ExoPlayerState {
  isPlaying: boolean
  currentPosition: number
  duration: number
  isBuffering: boolean
  error?: ExoPlayerError
}
```

### Data Models

#### VideoInfo
```javascript
class VideoInfo {
  uri: string
  id: string
  duration?: number
  width?: number
  height?: number
}
```

#### Strip
```javascript
class Strip {
  id: string
  uri: string
  startIndex: number
  endIndex: number
  thumbnailUri?: string
  isLoaded: boolean
  isPreloading: boolean
}
```

## 🔧 Parsers Disponíveis

- **`LogParser`** - Parser principal
- **`GLRendererParser`** - Parseia logs de gráficos
- **`BottomSheetGalleryParser`** - Parseia logs da galeria
- **`TimelineEditorParser`** - Parseia logs da timeline
- **`PreloadViewModelParser`** - Parseia logs de preload
- **`ExoPlayerParser`** - Parseia logs do player
- **`CacheParser`** - Parseia logs de cache

## 📝 JSDoc

Todas as classes e métodos estão documentados com JSDoc, proporcionando:

- ✅ Autocomplete em IDEs (VS Code, WebStorm, etc.)
- ✅ IntelliSense melhorado
- ✅ Documentação inline
- ✅ Facilidade de manutenção

### Exemplo de JSDoc

```javascript
/**
 * Parser principal de logs
 * @class LogParser
 * @description Orquestra o parsing de logs usando múltiplos parsers específicos
 */
class LogParser {
  constructor() {
    /** @type {EntityParser[]} Lista de parsers registrados */
    this.parsers = [...];
  }

  /**
   * Parseia um log e retorna um LogEntry
   * @param {string} log - Linha de log
   * @returns {LogEntry|null} Entrada de log parseada
   */
  parseLog(log) { ... }
}
```

## 💻 Exemplo de Uso

```javascript
const { LogParser, createInitialState } = require('./EntityParser');

const parser = new LogParser();
let appState = createInitialState();

const log = '[LOG] Timber.d("GLRenderer initialized successfully")';
const stateUpdate = parser.parseState(log);

if (stateUpdate) {
  appState = parser.mergeState(appState, stateUpdate);
}

console.log(appState.graphics.glRenderer.isInitialized); // true
```

## 🔄 Extensão

Para adicionar novos parsers:

```javascript
/**
 * Parser para novos padrões de log
 * @class MyCustomParser
 * @extends EntityParser
 */
class MyCustomParser extends EntityParser {
  canParse(log) {
    return /MyPattern/.test(log);
  }

  parse(log) {
    return {
      minhaNovaEntidade: { ... }
    };
  }
}
```

Depois adicione ao array de parsers no `LogParser`:

```javascript
this.parsers.push(new MyCustomParser());
```

## 📦 Classes Exportadas

### entities.js
- `GLRendererState`
- `SurfaceBridgeState`
- `GraphicsContext`
- `VideoInfo`
- `BottomSheetGalleryState`
- `TimelineEditorState`
- `PreloadState`
- `Strip`
- `CacheEntry`
- `CacheState`
- `ThumbnailState`
- `ExoPlayerError`
- `ExoPlayerState`
- `TimelineSegment`
- `TimelineState`
- `LogEntry`
- `PerformanceMetrics`
- `MonitorConfig`
- `AppState`

### EntityParser.js
- `EntityParser` (base)
- `GLRendererParser`
- `BottomSheetGalleryParser`
- `TimelineEditorParser`
- `PreloadViewModelParser`
- `ExoPlayerParser`
- `CacheParser`
- `LogParser`

### LogMapper.js
- `LogMapper`

## 🔍 Padrões de Log Suportados

- `Timber.d/w/e/v/i(...)` - Timber logging
- `Log.d/w/e/v/i(...)` - Android Log
- `Classe.kt:linha:[LOG]` - Formato Kotlin específico
- Timestamps ISO 8601
- Múltiplos formatos de mensagem

## 📊 Relatório de Exemplo

```
═══════════════════════════════════════════════════════════
║                    LOG MAPPER REPORT                     ║
╚═══════════════════════════════════════════════════════════
Generated at: 2026-03-04T18:11:07.451Z

📊 SUMMARY
Total entries: 819
Files analyzed: 7

📈 BY LOG LEVEL
  debug   :     0 
  info    :   780 ██████████████████████████████████████████████████
  warn    :     0 
  error   :    39 ████████████████████
  verbose :     0 
```

## 🎯 Diferenças da Versão TypeScript

Esta versão JavaScript OO:
- Usa classes ES6 com JSDoc para documentação
- Não requer compilação
- Executa diretamente com Node.js
- Autocomplete funcionando via JSDoc
- Mesma funcionalidade que a versão TypeScript

## 📄 Licença

ISC

## 🤝 Contribuindo

Sinta-se à vontade para abrir issues e pull requests!

---

**Nota:** Esta versão usa JSDoc para documentação e tipagem, proporcionando melhor experiência de desenvolvimento sem a necessidade de TypeScript.
