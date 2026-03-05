# Log Mapper

Ferramenta em TypeScript (Bun) para analisar e categorizar logs do projeto ChopCut, mapear entidades da aplicação e monitorar logs em tempo real via ADB.

## 📋 Recursos

- ✅ Analisa múltiplos arquivos de log
- ✅ Categoriza logs por contexto (Graphics, UI, Cache, Media, Timeline, Error)
- ✅ Mapeia logs para entidades TypeScript tipadas
- ✅ **Monitoramento em tempo real via ADB**
- ✅ Gera relatórios detalhados com estatísticas
- ✅ Sistema de parsing extensível para novos padrões de log
- ✅ Suporta diferentes formatos de log (Timber, Log Android, etc.)
- ✅ Framework completo para criar scripts de monitoramento customizados

## 🚀 Instalação

```bash
bun install
```

## 📖 Uso

### Análise de Logs (Arquivos)

```bash
bun run start
```

Isso irá:
1. Escanear os arquivos de log configurados
2. Analisar e categorizar cada entrada
3. Imprimir um relatório no terminal
4. Salvar um relatório detalhado em `log-report.md`

### Demo de Entidades

```bash
bun run demo
```

Isso irá:
1. Parsear logs de exemplo
2. Mapear para entidades TypeScript
3. Imprimir o estado da aplicação
4. Salvar o estado em `app-state.json`

### 📱 Monitoramento em Tempo Real via ADB

#### Monitorar Todos os Logs

```bash
bun run monitor:all
```

Monitora todos os logs do ChopCut, atualiza estado em tempo real e salva em `chopcut-logs.txt`.

#### Monitorar Apenas Erros

```bash
bun run monitor:errors
```

Filtra apenas logs de erros e exceções. Salva em `chopcut-errors.txt`.

#### Monitorar Logs Gráficos

```bash
bun run monitor:graphics
```

Monitora OpenGL, GLRenderer, SurfaceBridge. Salva em `graphics-logs.txt`.

#### Monitorar Cache e Preload

```bash
bun run monitor:cache
```

Monitora operações de cache e pré-carregamento. Salva em `cache-logs.txt`.

#### Monitorar Timeline

```bash
bun run monitor:timeline
```

Monitora operações da timeline e editor. Salva em `timeline-logs.txt`.

#### Monitor Customizado

```bash
bun run monitor:custom
```

Template para criar seu próprio monitor com filtros personalizados.

## 📁 Estrutura

### Arquivos Principais

- `index.ts` - Ponto de entrada principal (análise de logs)
- `demo-entities.ts` - Demo de mapeamento de entidades
- `src/LogMapper.ts` - Classe principal para análise de logs
- `src/entities.ts` - Definições de todas as entidades TypeScript
- `src/EntityParser.ts` - Sistema de parsing de entidades
- `src/ADBMonitor.ts` - Framework para monitoramento via ADB
- `src/RealtimeMonitor.ts` - Monitor em tempo real com state management

### Scripts de Monitoramento

- `monitor-all.ts` - Monitor completo da aplicação
- `monitor-errors.ts` - Monitor apenas de erros
- `monitor-graphics.ts` - Monitor de gráficos
- `monitor-cache.ts` - Monitor de cache/preload
- `monitor-timeline.ts` - Monitor de timeline
- `monitor-custom.ts` - Template para monitor customizado

### Arquivos Gerados

- `log-report.md` - Relatório detalhado dos logs analisados
- `app-state.json` - Estado completo da aplicação em JSON
- `*-logs.txt` - Logs capturados pelos monitores

## 📊 Categorias de Log

- **Graphics** - OpenGL, GLRenderer, SurfaceBridge
- **UI Components** - Componentes da interface (BottomSheet, Timeline, etc.)
- **Cache** - Operações de cache e preload
- **Media** - Playback e processamento de mídia
- **Timeline** - Operações da timeline
- **Error** - Logs de erro e exceções

## 🏗️ Entidades Mapeadas

### Graphics
- `GLRendererState` - Estado do renderizador OpenGL
- `SurfaceBridgeState` - Estado da bridge de superfície
- `GraphicsContext` - Contexto gráfico completo

### UI
- `BottomSheetGalleryState` - Estado da galeria de vídeos
- `TimelineEditorState` - Estado do editor de timeline

### State Management
- `PreloadState` - Estado de pré-carregamento
- `CacheState` - Estado do cache
- `ThumbnailState` - Estado de thumbnails
- `ExoPlayerState` - Estado do player de vídeo

### Data Models
- `VideoInfo` - Informações de vídeos
- `Strip` - Informações de strips
- `TimelineSegment` - Segmentos da timeline

Veja `ENTITIES.md` para documentação completa das entidades.

## 🔧 Parsers Disponíveis

- `GLRendererParser` - Parseia logs de gráficos
- `BottomSheetGalleryParser` - Parseia logs da galeria
- `TimelineEditorParser` - Parseia logs da timeline
- `PreloadViewModelParser` - Parseia logs de preload
- `ExoPlayerParser` - Parseia logs do player
- `CacheParser` - Parseia logs de cache

## 📱 Framework ADB

### Classes Principais

#### `ADBCommand`

Executa comandos ADB síncronos e assíncronos.

```typescript
import { ADBCommand } from './src/ADBMonitor';

// Listar dispositivos
const devices = await ADBCommand.getDevices();

// Obter PID de um pacote
const pid = await ADBCommand.getPackagePid('com.chopcut.app');

// Limpar logcat
await ADBCommand.clearLogcat();

// Executar comando customizado
const result = await ADBCommand.execute('shell', 'ps');
```

#### `ADBMonitor`

Monitor de logs do Android com callbacks.

```typescript
import { ADBMonitor } from './src/ADBMonitor';

const monitor = new ADBMonitor({
  tag: 'ChopCut',
  level: 'D',
  package: 'com.chopcut.app',
  onLogLine: (line) => console.log(line),
  onError: (error) => console.error(error),
  onClose: (code) => console.log(`Closed: ${code}`)
});

await monitor.start();
monitor.stop();
```

#### `RealtimeLogMonitor`

Monitor em tempo real com atualização de estado e salvamento em arquivo.

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

const monitor = new RealtimeLogMonitor({
  tag: 'ChopCut',
  saveToFile: true,
  outputFile: 'my-logs.txt',
  updateState: true,
  statsInterval: 5,
  filterLogs: (line) => line.includes('GLRenderer'),
  onLogLine: (line) => console.log(line)
});

await monitor.start();

// Obter estado atual
const state = monitor.getState();
console.log(state.graphics.glRenderer.isInitialized);

// Obter logs capturados
const logs = monitor.getLogs();
```

### Criar Scripts Customizados

Use o framework para criar seus próprios scripts de monitoramento:

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

async function myCustomMonitor() {
  const monitor = new RealtimeLogMonitor({
    saveToFile: true,
    outputFile: 'my-custom-logs.txt',
    updateState: true,
    statsInterval: 3,
    
    filterLogs: (line) => {
      // Seus filtros customizados
      return line.includes('SuaTag') || line.includes('SuaPalavra');
    },
    
    onLogLine: (line) => {
      // Processamento customizado
      if (line.includes('ERROR')) {
        console.log(`🔴 ${line}`);
        // Enviar alerta, salvar no banco, etc.
      }
    }
  });

  await monitor.start();
}

myCustomMonitor().catch(console.error);
```

## 💻 Exemplo de Uso

```typescript
import { LogParser, createInitialState } from './src/EntityParser';

const parser = new LogParser();
let appState = createInitialState();

const log = '[LOG] Timber.d("GLRenderer initialized successfully")';
const stateUpdate = parser.parseState(log);

if (stateUpdate) {
  appState = parser.mergeState(appState, stateUpdate);
}

console.log(appState.graphics.glRenderer.isInitialized);
```

## 📦 APIs Disponíveis

### ADBMonitor

- `ADBCommand.execute()` - Executa comando síncrono
- `ADBCommand.executeAsync()` - Executa comando assíncrono
- `ADBCommand.getDevices()` - Lista dispositivos
- `ADBCommand.getPackagePid()` - Obtém PID do pacote
- `ADBCommand.clearLogcat()` - Limpa buffer
- `ADBCommand.getProperty()` - Obtém propriedade do sistema
- `ADBCommand.getAPILevel()` - Obtém nível de API
- `ADBCommand.isAvailable()` - Verifica disponibilidade

### RealtimeLogMonitor

- `start()` - Inicia monitoramento
- `stop()` - Para monitoramento
- `getState()` - Obtém estado atual
- `getLogs()` - Obtém logs capturados

## 🔍 Requisitos

- Bun runtime
- Android SDK Platform Tools (ADB)
- Dispositivo Android conectado com USB debugging habilitado
- Node.js types (para IDEs)

## 📄 Licença

ISC

Este projeto foi criado usando `bun init` em bun v1.3.6. [Bun](https://bun.com) é um runtime JavaScript rápido e tudo-em-um.
