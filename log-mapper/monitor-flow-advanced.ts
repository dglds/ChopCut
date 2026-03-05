import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Tipos de eventos no fluxo
 */
enum EventType {
  EXTRACTION_START = 'EXTRACTION_START',
  EXTRACTION_PROGRESS = 'EXTRACTION_PROGRESS',
  EXTRACTION_COMPLETE = 'EXTRACTION_COMPLETE',
  DECODING_START = 'DECODING_START',
  DECODING_PROGRESS = 'DECODING_PROGRESS',
  DECODING_COMPLETE = 'DECODING_COMPLETE',
  RENDERING_START = 'RENDERING_START',
  RENDERING_PROGRESS = 'RENDERING_PROGRESS',
  RENDERING_COMPLETE = 'RENDERING_COMPLETE',
  DISPLAY = 'DISPLAY',
  ERROR = 'ERROR'
}

/**
 * Evento no fluxo
 */
interface FlowEvent {
  type: EventType;
  timestamp: number;
  data?: any;
}

/**
 * Métricas de performance por etapa
 */
interface PerformanceMetrics {
  totalFrames: number;
  avgFrameTime: number;
  minFrameTime: number;
  maxFrameTime: number;
  droppedFrames: number;
  frameTimes: number[];
}

/**
 * Monitor avançado do fluxo com visualização gráfica
 */
class AdvancedFlowMonitor {
  private events: FlowEvent[] = [];
  private metrics: Map<string, PerformanceMetrics> = new Map();
  private lastFrameTime: number = 0;
  private totalFrames: number = 0;
  private droppedFrames: number = 0;
  private startTime: number = 0;

  constructor() {
    this.metrics.set('extraction', {
      totalFrames: 0,
      avgFrameTime: 0,
      minFrameTime: Infinity,
      maxFrameTime: 0,
      droppedFrames: 0,
      frameTimes: []
    });

    this.metrics.set('decoding', {
      totalFrames: 0,
      avgFrameTime: 0,
      minFrameTime: Infinity,
      maxFrameTime: 0,
      droppedFrames: 0,
      frameTimes: []
    });

    this.metrics.set('rendering', {
      totalFrames: 0,
      avgFrameTime: 0,
      minFrameTime: Infinity,
      maxFrameTime: 0,
      droppedFrames: 0,
      frameTimes: []
    });
  }

  /**
   * Processa uma linha de log
   */
  processLine(line: string): void {
    const timestamp = Date.now();
    const frameTime = timestamp - this.lastFrameTime;

    // MediaExtractor
    if (line.includes('MediaExtractor') && line.includes('start')) {
      this.addEvent(EventType.EXTRACTION_START, timestamp);
      if (this.startTime === 0) this.startTime = timestamp;
      console.log('\n🎬 [START] Extração iniciada');
    }

    if (line.includes('extracted') || line.includes('sample extracted')) {
      this.addEvent(EventType.EXTRACTION_PROGRESS, timestamp);
      this.updateMetrics('extraction', frameTime);
    }

    if (line.includes('extraction') && line.includes('complete')) {
      this.addEvent(EventType.EXTRACTION_COMPLETE, timestamp);
      console.log('✓ [COMPLETE] Extração finalizada');
    }

    // MediaCodec
    if (line.includes('MediaCodec') && line.includes('start')) {
      this.addEvent(EventType.DECODING_START, timestamp);
      console.log('\n🔊 [START] Decodificação iniciada');
    }

    if (line.includes('decoded') || line.includes('output buffer')) {
      this.addEvent(EventType.DECODING_PROGRESS, timestamp);
      this.updateMetrics('decoding', frameTime);
    }

    if (line.includes('decoding') && line.includes('complete')) {
      this.addEvent(EventType.DECODING_COMPLETE, timestamp);
      console.log('✓ [COMPLETE] Decodificação finalizada');
    }

    // GLRenderer
    if (line.includes('GLRenderer') && line.includes('render')) {
      this.addEvent(EventType.RENDERING_START, timestamp);
      console.log('\n🖼️  [START] Renderização iniciada');
    }

    if (line.includes('rendered') || line.includes('frame rendered')) {
      this.addEvent(EventType.RENDERING_PROGRESS, timestamp);
      this.updateMetrics('rendering', frameTime);
      this.lastFrameTime = timestamp;
      this.totalFrames++;
    }

    if (line.includes('rendering') && line.includes('complete')) {
      this.addEvent(EventType.RENDERING_COMPLETE, timestamp);
      console.log('✓ [COMPLETE] Renderização finalizada');
    }

    // Display
    if (line.includes('displayed') || line.includes('frame displayed') ||
        line.includes('onFrameAvailable') || line.includes('onBufferAvailable')) {
      this.addEvent(EventType.DISPLAY, timestamp);
      console.log('✓ [DISPLAY] Frame exibido na tela');
    }

    // Erros
    if (line.includes('error') || line.includes('Error') || line.includes('failed')) {
      this.addEvent(EventType.ERROR, timestamp, { message: line });
      console.log(`❌ [ERROR] ${line}`);
    }
  }

  /**
   * Adiciona um evento ao fluxo
   */
  private addEvent(type: EventType, timestamp: number, data?: any): void {
    this.events.push({ type, timestamp, data });
  }

  /**
   * Atualiza métricas de uma etapa
   */
  private updateMetrics(stage: string, frameTime: number): void {
    const metrics = this.metrics.get(stage);
    if (!metrics) return;

    metrics.totalFrames++;
    
    if (this.lastFrameTime > 0) {
      metrics.frameTimes.push(frameTime);
      metrics.avgFrameTime = metrics.frameTimes.reduce((a, b) => a + b, 0) / metrics.frameTimes.length;
      metrics.minFrameTime = Math.min(metrics.minFrameTime, frameTime);
      metrics.maxFrameTime = Math.max(metrics.maxFrameTime, frameTime);
    }

    // Detectar frames dropados (> 33ms = < 30fps)
    if (frameTime > 33) {
      metrics.droppedFrames++;
      this.droppedFrames++;
    }
  }

  /**
   * Imprime dashboard visual
   */
  printDashboard(): void {
    console.clear();
    console.log('═══════════════════════════════════════════════════════════');
    console.log('║              FLOW MONITOR DASHBOARD                      ║');
    console.log('╚═══════════════════════════════════════════════════════════\n');

    this.printProgress();
    this.printPipeline();
    this.printMetrics();
    this.printPerformance();
    this.printTimeline();
  }

  /**
   * Imprime barra de progresso
   */
  private printProgress(): void {
    const totalFrames = this.metrics.get('rendering')?.totalFrames || 0;
    const progress = Math.min(100, Math.floor(totalFrames / 10));
    const bar = '█'.repeat(Math.floor(progress / 5)) + '░'.repeat(20 - Math.floor(progress / 5));
    
    console.log(`📊 Progress: [${bar}] ${progress}% (${totalFrames} frames)`);
    
    if (this.startTime > 0) {
      const elapsed = ((Date.now() - this.startTime) / 1000).toFixed(1);
      const fps = totalFrames / parseFloat(elapsed);
      console.log(`⏱️  Elapsed: ${elapsed}s | FPS: ${fps.toFixed(1)} | Dropped: ${this.droppedFrames}`);
    }
    console.log('');
  }

  /**
   * Imprime visualização do pipeline
   */
  private printPipeline(): void {
    const stages = [
      { name: 'EXTRAÇÃO', stage: 'extraction', icon: '🎬' },
      { name: 'DECODIFICAÇÃO', stage: 'decoding', icon: '🔊' },
      { name: 'RENDERIZAÇÃO', stage: 'rendering', icon: '🖼️' },
      { name: 'TELA', stage: 'display', icon: '📺' }
    ];

    console.log('🔗 Pipeline:');
    let pipeline = '    ';
    
    for (let i = 0; i < stages.length; i++) {
      const s = stages[i];
      const metrics = this.metrics.get(s.stage);
      const isActive = metrics && metrics.totalFrames > 0;
      const isComplete = metrics && metrics.totalFrames > 5;
      
      const status = isComplete ? '✓' : (isActive ? '⟳' : '○');
      pipeline += `${s.icon} ${status} ${s.name}  `;
    }
    
    console.log(pipeline);
    console.log('');
  }

  /**
   * Imprime métricas por etapa
   */
  private printMetrics(): void {
    console.log('📈 Métricas por Etapa:');
    
    for (const [stage, metrics] of this.metrics) {
      if (metrics.totalFrames === 0) continue;
      
      const avg = metrics.avgFrameTime.toFixed(2);
      const min = metrics.minFrameTime === Infinity ? 'N/A' : metrics.minFrameTime.toFixed(2);
      const max = metrics.maxFrameTime.toFixed(2);
      const dropped = metrics.droppedFrames;
      
      console.log(`  ${stage.toUpperCase().padEnd(12)}: ${metrics.totalFrames} frames | Avg: ${avg}ms | Min: ${min}ms | Max: ${max}ms | Dropped: ${dropped}`);
    }
    console.log('');
  }

  /**
   * Imprime gráfico de performance
   */
  private printPerformance(): void {
    const rendering = this.metrics.get('rendering');
    if (!rendering || rendering.frameTimes.length === 0) return;

    console.log('📉 Performance (últimos 30 frames):');
    
    const recentFrames = rendering.frameTimes.slice(-30);
    const maxTime = Math.max(...recentFrames);
    
    for (const frameTime of recentFrames) {
      const barLength = Math.floor((frameTime / maxTime) * 30);
      const bar = '█'.repeat(barLength) + '░'.repeat(30 - barLength);
      const color = frameTime < 16 ? '🟢' : (frameTime < 33 ? '🟡' : '🔴');
      console.log(`  ${color} ${bar} ${frameTime.toFixed(1)}ms`);
    }
    console.log('');
  }

  /**
   * Imprime timeline de eventos
   */
  private printTimeline(): void {
    console.log('📅 Timeline Recente:');
    
    const recentEvents = this.events.slice(-10);
    
    for (const event of recentEvents) {
      const elapsed = this.startTime > 0 ? ((event.timestamp - this.startTime) / 1000).toFixed(2) : '0.00';
      
      const icons = {
        [EventType.EXTRACTION_START]: '🎬',
        [EventType.EXTRACTION_PROGRESS]: '📦',
        [EventType.EXTRACTION_COMPLETE]: '✓',
        [EventType.DECODING_START]: '🔊',
        [EventType.DECODING_PROGRESS]: '🎵',
        [EventType.DECODING_COMPLETE]: '✓',
        [EventType.RENDERING_START]: '🖼️',
        [EventType.RENDERING_PROGRESS]: '🖼️',
        [EventType.RENDERING_COMPLETE]: '✓',
        [EventType.DISPLAY]: '📺',
        [EventType.ERROR]: '❌'
      };
      
      console.log(`  [${elapsed}s] ${icons[event.type]} ${event.type}`);
    }
    console.log('');
  }

  /**
   * Imprime relatório final
   */
  printFinalReport(): void {
    console.clear();
    console.log('═══════════════════════════════════════════════════════════');
    console.log('║                 FLOW FINAL REPORT                        ║');
    console.log('╚═══════════════════════════════════════════════════════════\n');

    const totalTime = this.startTime > 0 ? ((Date.now() - this.startTime) / 1000).toFixed(2) : 'N/A';
    const totalFrames = this.totalFrames;
    const avgFps = parseFloat(totalTime) > 0 ? (totalFrames / parseFloat(totalTime)).toFixed(2) : 'N/A';

    console.log('📊 RESUMO:');
    console.log(`  Tempo Total: ${totalTime}s`);
    console.log(`  Total Frames: ${totalFrames}`);
    console.log(`  FPS Médio: ${avgFps}`);
    console.log(`  Frames Dropados: ${this.droppedFrames}`);
    console.log(`  Total Eventos: ${this.events.length}`);
    console.log('');

    console.log('📈 MÉTRICAS FINAIS:');
    for (const [stage, metrics] of this.metrics) {
      if (metrics.totalFrames === 0) continue;
      
      console.log(`\n  ${stage.toUpperCase()}:`);
      console.log(`    Total Frames: ${metrics.totalFrames}`);
      console.log(`    Tempo Médio: ${metrics.avgFrameTime.toFixed(2)}ms`);
      console.log(`    Tempo Mín: ${metrics.minFrameTime === Infinity ? 'N/A' : metrics.minFrameTime.toFixed(2)}ms`);
      console.log(`    Tempo Máx: ${metrics.maxFrameTime.toFixed(2)}ms`);
      console.log(`    Frames Dropados: ${metrics.droppedFrames}`);
    }

    const errors = this.events.filter(e => e.type === EventType.ERROR);
    if (errors.length > 0) {
      console.log('\n❌ ERROS:');
      errors.forEach((err, i) => {
        console.log(`  ${i + 1}. ${err.data?.message || err.type}`);
      });
    }

    console.log('\n═══════════════════════════════════════════════════════════\n');
  }

  /**
   * Obtém métricas
   */
  getMetrics(): Map<string, PerformanceMetrics> {
    return new Map(this.metrics);
  }

  /**
   * Obtém eventos
   */
  getEvents(): FlowEvent[] {
    return [...this.events];
  }
}

/**
 * Monitor do fluxo com dashboard avançado
 */
async function monitorFlowAdvanced() {
  const flowMonitor = new AdvancedFlowMonitor();

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'flow-advanced-logs.txt',
    updateState: false,
    clearBuffer: true,

    filterLogs: (line) => {
      return line.includes('MediaExtractor') ||
             line.includes('MediaCodec') ||
             line.includes('GLRenderer') ||
             line.includes('SurfaceBridge') ||
             line.includes('extract') ||
             line.includes('decode') ||
             line.includes('render') ||
             line.includes('display') ||
             line.includes('frame') ||
             line.includes('onFrame') ||
             line.includes('onBuffer');
    },

    onLogLine: (line) => {
      flowMonitor.processLine(line);
    }
  });

  // Atualizar dashboard periodicamente
  const dashboardInterval = setInterval(() => {
    flowMonitor.printDashboard();
  }, 1000);

  try {
    await monitor.start();
  } catch (error) {
    clearInterval(dashboardInterval);
    console.error(`❌ Erro: ${error}`);
    flowMonitor.printFinalReport();
  } finally {
    clearInterval(dashboardInterval);
  }
}

monitorFlowAdvanced().catch(console.error);
