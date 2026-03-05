# Monitoramento de Fluxo: Extração → Carregamento na Tela

## 📋 Visão Geral

Monitoramento completo do fluxo de vídeo desde a extração até a exibição na tela, incluindo:
- 🎬 **Extração** - MediaExtractor
- 🔊 **Decodificação** - MediaCodec
- 🖼️ **Renderização** - GLRenderer
- 🌉 **Superfície** - SurfaceBridge
- 📺 **Exibição** - Display

## 🚀 Scripts Disponíveis

### 1. Monitor de Fluxo Básico

```bash
bun run monitor:flow
```

Monitor simples com:
- Visualização do progresso em tempo real
- Timeline de estados
- Estatísticas por etapa
- Detecção de erros

**Saída:**
```
[████████████░░] 60% ⟳○○
📅 Timeline:
  ⏸️ IDLE → 🎬 EXTRACTING → 🔊 DECODING → 🖼️ RENDERING

📊 Estatísticas do Fluxo:
  Estado: RENDERING
  Tempo decorrido: 2.34s
  Frames extraídos: 150
  Frames decodificados: 145
  Frames renderizados: 140
  FPS: 59.8

⏱️  Detalhes por Etapa:
  ✓ MediaExtractor: 845ms (150 frames, 0 erros)
  ✓ MediaCodec: 1123ms (145 frames, 0 erros)
  ⟳ GLRenderer: em progresso (140 frames, 0 erros)
```

### 2. Monitor de Fluxo Avançado

```bash
bun run monitor:flow-advanced
```

Monitor avançado com:
- Dashboard em tempo real
- Pipeline visual
- Gráficos de performance
- Métricas detalhadas por frame
- Detecção de frames dropados

**Saída:**
```
═══════════════════════════════════════════════════════════
║              FLOW MONITOR DASHBOARD                      ║
╚═══════════════════════════════════════════════════════════

📊 Progress: [████████████████] 100% (120 frames)
⏱️  Elapsed: 2.0s | FPS: 60.0 | Dropped: 0

🔗 Pipeline:
    🎬 ✓ EXTRAÇÃO  🔊 ✓ DECODIFICAÇÃO  🖼️ ✓ RENDERIZAÇÃO  📺 ✓ TELA

📈 Métricas por Etapa:
  EXTRACTION  : 120 frames | Avg: 7.03ms | Min: 6.21ms | Max: 8.45ms | Dropped: 0
  DECODING    : 120 frames | Avg: 9.35ms | Min: 8.12ms | Max: 11.23ms | Dropped: 0
  RENDERING   : 120 frames | Avg: 16.67ms | Min: 15.89ms | Max: 17.45ms | Dropped: 0

📉 Performance (últimos 30 frames):
  🟢 ███████████████████████░░░ 15.9ms
  🟢 ████████████████████████░░ 16.2ms
  🟢 ████████████████████████░░ 16.5ms
  🟡 █████████████████████████ 20.1ms
  🟢 ███████████████████████░░ 15.8ms
```

## 📊 Estados do Fluxo

### Estados Simples

1. **IDLE** - Aguardando início
2. **EXTRACTING** - Extraindo frames do vídeo
3. **DECODING** - Decodificando frames
4. **RENDERING** - Renderizando frames
5. **LOADED** - Vídeo carregado na tela
6. **ERROR** - Erro no fluxo

### Eventos Avançados

- `EXTRACTION_START` - Início da extração
- `EXTRACTION_PROGRESS` - Progresso da extração
- `EXTRACTION_COMPLETE` - Extração completa
- `DECODING_START` - Início da decodificação
- `DECODING_PROGRESS` - Progresso da decodificação
- `DECODING_COMPLETE` - Decodificação completa
- `RENDERING_START` - Início da renderização
- `RENDERING_PROGRESS` - Progresso da renderização
- `RENDERING_COMPLETE` - Renderização completa
- `DISPLAY` - Frame exibido na tela
- `ERROR` - Erro no fluxo

## 🔍 Métricas Coletadas

### Por Etapa

- **Total Frames** - Quantidade de frames processados
- **Tempo Médio** - Média de tempo por frame (ms)
- **Tempo Mínimo** - Frame mais rápido (ms)
- **Tempo Máximo** - Frame mais lento (ms)
- **Frames Dropados** - Frames > 33ms (< 30fps)

### Globais

- **Tempo Total** - Duração completa do fluxo (s)
- **FPS Médio** - Frames por segundo
- **Total Frames Dropados** - Soma de todos os dropados
- **Total Eventos** - Quantidade de eventos processados

## 🎯 Casos de Uso

### 1. Debug de Performance

```bash
bun run monitor:flow-advanced
```

Identifica:
- Bottlenecks no pipeline
- Frames dropados
- Anomalias de performance
- Etapas lentas

### 2. Validação de Pipeline

```bash
bun run monitor:flow
```

Verifica:
- Todas as etapas executaram
- Ordem correta do fluxo
- Ausência de erros
- Completitude do processo

### 3. Benchmark de Performance

Use o monitor avançado para comparar:
- Diferentes dispositivos
- Diferentes resoluções
- Diferentes codecs
- Otimizações de código

### 4. Monitoramento Contínuo

Combine com filtros para monitoramento em produção:

```typescript
filterLogs: (line) => {
  return line.includes('flow') || 
         line.includes('pipeline') ||
         line.includes('frame') ||
         line.includes('display');
}
```

## 📊 Interpretação dos Resultados

### Performance Satisfatória

- FPS ≥ 30 (≤ 33ms/frame)
- Frames dropados = 0
- Tempo médio consistente
- Pipeline completo sem erros

### Problemas Detectados

#### FPS Baixo (< 30)

```
FPS: 24.5 | Dropped: 15
```
- Analise qual etapa está lenta
- Verifique se há GC frequente
- Otimize algoritmos críticos

#### Frames Dropados

```
Dropped: 5 | Max: 45.2ms
```
- Identifique spikes de tempo
- Verifique se há bloqueios na thread
- Otimize caminho crítico

#### Erros no Pipeline

```
❌ Erro: Failed to initialize GLRenderer
```
- Verifique inicialização do OpenGL
- Confirme disponibilidade de recursos
- Teste em diferentes dispositivos

## 💡 Dicas de Uso

1. **Limpe o buffer antes de monitorar**
   ```typescript
   clearBuffer: true
   ```

2. **Use intervalos apropriados**
   ```typescript
   statsInterval: 2  // 2 segundos para monitor básico
   statsInterval: 1  // 1 segundo para monitor avançado
   ```

3. **Salve logs para análise posterior**
   ```typescript
   saveToFile: true
   outputFile: 'flow-logs.txt'
   ```

4. **Combine com outros monitores**
   ```bash
   # Terminal 1: Monitor de fluxo
   bun run monitor:flow
   
   # Terminal 2: Monitor de erros
   bun run monitor:errors
   ```

## 🔧 Criação de Monitores Customizados

### Monitor com Alertas

```typescript
import { AdvancedFlowMonitor } from './monitor-flow-advanced.ts';

class AlertFlowMonitor extends AdvancedFlowMonitor {
  private alertsThreshold = 30; // FPS mínimo
  private lastAlertTime = 0;

  processLine(line: string): void {
    super.processLine(line);
    
    const rendering = this.getMetrics().get('rendering');
    if (rendering && rendering.avgFrameTime > this.alertsThreshold) {
      const now = Date.now();
      if (now - this.lastAlertTime > 5000) { // Alerta a cada 5s
        console.log(`🚨 ALERTA: Performance baixa (${(1000/rendering.avgFrameTime).toFixed(1)} FPS)`);
        this.lastAlertTime = now;
      }
    }
  }
}
```

### Monitor com Exportação

```typescript
const monitor = new AdvancedFlowMonitor();

// Ao final
const metrics = monitor.getMetrics();
await Bun.write('metrics.json', JSON.stringify(Object.fromEntries(metrics), null, 2));
```

### Monitor com Comparação

```typescript
class CompareFlowMonitor extends AdvancedFlowMonitor {
  private baseline: any;

  setBaseline(metrics: any): void {
    this.baseline = metrics;
  }

  compare(): void {
    const current = this.getMetrics();
    const rendering = current.get('rendering');
    const baseline = this.baseline.get('rendering');

    console.log(`Diferença de FPS: ${(1000/rendering.avgFrameTime - 1000/baseline.avgFrameTime).toFixed(2)}`);
  }
}
```

## 📚 Referência Rápida

### Comandos

```bash
# Monitor básico
bun run monitor:flow

# Monitor avançado
bun run monitor:flow-advanced

# Combinar com outros
bun run monitor:flow-advanced &
bun run monitor:errors &
```

### Arquivos Gerados

- `flow-monitor-logs.txt` - Logs do monitor básico
- `flow-advanced-logs.txt` - Logs do monitor avançado
- `metrics.json` - Métricas exportadas (custom)

### Estrutura de Logs

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  ARQUIVO    │ →  │ EXTRACTOR   │ →  │  CODEC      │ →  │  RENDERER   │
│   VÍDEO     │    │ MediaExtr   │    │ MediaCodec  │    │  GLRenderer │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                         ↓                  ↓                  ↓
                     [Sample]          [Buffer]         [Texture]
                         ↓                  ↓                  ↓
                    [Demux]          [Decode]         [OpenGL]
                                               ↓
                                            ┌─────────────┐
                                            │   TELA     │
                                            │   Display   │
                                            └─────────────┘
```

## 🆚 Comparação: Básico vs Avançado

| Recurso | Básico | Avançado |
|---------|--------|----------|
| Progresso | ✓ | ✓ |
| Timeline | ✓ | ✓ |
| Estatísticas | ✓ | ✓ |
| Pipeline visual | ✗ | ✓ |
| Gráficos | ✗ | ✓ |
| Detecção de drops | ✗ | ✓ |
| Métricas por frame | ✗ | ✓ |
| Dashboard em tempo real | ✗ | ✓ |

## 🐛 Solução de Problemas

### Monitor não inicia

```bash
# Verifique se o dispositivo está conectado
adb devices

# Verifique se o app está rodando
adb shell ps | grep chopcut
```

### Logs não aparecem

```bash
# Verifique se o log está sendo gerado
adb logcat | grep MediaExtractor

# Limpe o buffer
adb logcat -c
```

### Performance baixa no monitor

```typescript
// Aumente o intervalo de atualização
statsInterval: 5  // 5 segundos

// Desabilite atualizações visuais se não necessário
```

## 📖 Documentação Relacionada

- [ADB Guide](./ADB_GUIDE.md) - Guia completo do framework ADB
- [README](./README.md) - Documentação geral do projeto
- [ENTITIES](./ENTITIES.md) - Documentação das entidades

---

**Desenvolvido para monitoramento do ChopCut** 🎬
