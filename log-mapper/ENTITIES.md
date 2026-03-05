# Entidades TypeScript do ChopCut

Este projeto define todas as entidades mapeadas do ChopCut baseadas na análise de logs.

## Estrutura

### `src/entities.ts`
Define todas as interfaces TypeScript que representam o estado da aplicação:

- **GraphicsContext** - Estado do OpenGL/GLRenderer/SurfaceBridge
- **VideoInfo** - Informações de vídeos
- **BottomSheetGalleryState** - Estado da galeria de vídeos
- **TimelineEditorState** - Estado do editor de timeline
- **PreloadState** - Estado de pré-carregamento
- **Strip** - Informações de strips
- **CacheEntry** - Entradas de cache
- **CacheState** - Estado do cache
- **ThumbnailState** - Estado de thumbnails
- **ExoPlayerState** - Estado do player
- **TimelineState** - Estado da timeline
- **AppState** - Estado completo da aplicação

### `src/EntityParser.ts`
Sistema de parsing que converte logs de texto em objetos TypeScript:

- **LogParser** - Parser principal de logs
- **GLRendererParser** - Parser específico para logs de gráficos
- **BottomSheetGalleryParser** - Parser para galeria de vídeos
- **TimelineEditorParser** - Parser para timeline
- **PreloadViewModelParser** - Parser para pré-carregamento
- **ExoPlayerParser** - Parser para player de vídeo

## Uso

### Exemplo Básico

```typescript
import { LogParser, createInitialState } from './src/EntityParser';

const parser = new LogParser();
let appState = createInitialState();

const log = '[LOG] Timber.d("GLRenderer initialized successfully")';
const stateUpdate = parser.parseState(log);

if (stateUpdate) {
  appState = parser.mergeState(appState, stateUpdate);
}

console.log(appState.graphics.glRenderer.isInitialized); // true
```

### Executar Demo

```bash
bun run demo-entities.ts
```

### Parse de Logs Reais

```typescript
import { LogParser } from './src/EntityParser';
import { LogMapper } from './src/LogMapper';

const parser = new LogParser();
const mapper = new LogMapper();

await mapper.parseFile('../logs/thumbnail_monitor.log');

const report = mapper.generateReport();
console.log(report);
```

## Entidades Mapeadas

### Graphics
```typescript
interface GLRendererState {
  isInitialized: boolean;
  positionHandle?: number;
  textureCoordHandle?: number;
  mvpMatrixHandle?: number;
  textureMatrixHandle?: number;
  textureSamplerHandle?: number;
  externalTextureId?: number;
  lastError?: string;
}
```

### BottomSheetGallery
```typescript
interface BottomSheetGalleryState {
  selectedVideo?: VideoInfo;
  isExpanded: boolean;
  videos: VideoInfo[];
}
```

### TimelineEditor
```typescript
interface TimelineEditorState {
  scrollVelocity: number;
  exoPlayerError?: string;
  currentTimestamp?: number;
  isScrolling: boolean;
}
```

### PreloadViewModel
```typescript
interface PreloadState {
  uri?: string;
  stripsToPreload: number;
  activeUri?: string;
  currentState: string;
  stripsLoaded: number;
  totalSegments: number;
  isReady: boolean;
  threshold: number;
}
```

## Arquivos Gerados

- `app-state.json` - Estado completo da aplicação em JSON
- `log-report.md` - Relatório detalhado dos logs analisados

## Padrões de Log Suportados

- `Timber.d/w/e/v/i(...)` - Timber logging
- `Log.d/w/e/v/i(...)` - Android Log
- `Classe.kt:linha:[LOG]` - Formato Kotlin específico
- Timestamps ISO 8601
- Múltiplos formatos de mensagem

## Extensão

Para adicionar novos parsers:

```typescript
export class MyCustomParser implements EntityParser {
  canParse(log: string): boolean {
    return /MyPattern/.test(log);
  }

  parse(log: string): Partial<AppState> | null {
    // Extrair estado do log
    return {
      minhaNovaEntidade: { ... }
    };
  }
}
```

Depois adicione ao array de parsers no `LogParser`.
