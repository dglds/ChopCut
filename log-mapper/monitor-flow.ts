import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Estados do fluxo de extração e carregamento
 */
enum FlowState {
  IDLE = 'IDLE',
  EXTRACTING = 'EXTRACTING',
  DECODING = 'DECODING',
  RENDERING = 'RENDERING',
  LOADED = 'LOADED',
  ERROR = 'ERROR'
}

/**
 * Estatísticas do fluxo
 */
interface FlowStats {
  startTime: number;
  endTime: number;
  state: FlowState;
  framesExtracted: number;
  framesDecoded: number;
  framesRendered: number;
  durationMs: number;
  fps: number;
  errors: string[];
  timeline: FlowState[];
}

/**
 * Estatísticas por etapa
 */
interface StageStats {
  name: string;
  startTime: number;
  endTime: number;
  duration: number;
  success: boolean;
  errors: number;
  frames: number;
}

/**
 * Monitor do fluxo completo: extração → carregamento
 */
class FlowMonitor {
  private stats: FlowStats;
  private stages: Map<string, StageStats> = new Map();
  private currentStage: string | null = null;

  constructor() {
    this.stats = {
      startTime: 0,
      endTime: 0,
      state: FlowState.IDLE,
      framesExtracted: 0,
      framesDecoded: 0,
      framesRendered: 0,
      durationMs: 0,
      fps: 0,
      errors: [],
      timeline: [FlowState.IDLE]
    };
  }

  /**
   * Processa uma linha de log e atualiza o fluxo
   */
  processLine(line: string): void {
    // Início da extração
    if (line.includes('MediaExtractor') && line.includes('start') || 
        line.includes('begin extraction')) {
      this.setStage('extraction', 'MediaExtractor');
      this.stats.startTime = Date.now();
      this.stats.state = FlowState.EXTRACTING;
      this.addToTimeline(FlowState.EXTRACTING);
      console.log('🎬 Extração iniciada');
    }

    // Progresso da extração
    if (line.includes('extracted') || line.includes('sample extracted')) {
      this.stats.framesExtracted++;
      this.updateStageFrames('extraction', 1);
    }

    // Início da decodificação
    if (line.includes('MediaCodec') && line.includes('start') ||
        line.includes('begin decoding')) {
      this.setStage('decoding', 'MediaCodec');
      this.stats.state = FlowState.DECODING;
      this.addToTimeline(FlowState.DECODING);
      console.log('🔊 Decodificação iniciada');
    }

    // Progresso da decodificação
    if (line.includes('decoded') || line.includes('output buffer')) {
      this.stats.framesDecoded++;
      this.updateStageFrames('decoding', 1);
    }

    // Início da renderização
    if (line.includes('GLRenderer') && line.includes('render') ||
        line.includes('begin render')) {
      this.setStage('rendering', 'GLRenderer');
      this.stats.state = FlowState.RENDERING;
      this.addToTimeline(FlowState.RENDERING);
      console.log('🖼️  Renderização iniciada');
    }

    // Progresso da renderização
    if (line.includes('rendered') || line.includes('frame rendered')) {
      this.stats.framesRendered++;
      this.updateStageFrames('rendering', 1);
      
      // Calcular FPS
      if (this.stats.framesRendered > 0) {
        const elapsed = (Date.now() - this.stats.startTime) / 1000;
        this.stats.fps = this.stats.framesRendered / elapsed;
      }
    }

    // SurfaceBridge inicializado
    if (line.includes('SurfaceBridge') && line.includes('initialized')) {
      this.setStage('surface', 'SurfaceBridge');
      console.log('🌉 SurfaceBridge inicializado');
    }

    // Textura externa pronta
    if (line.includes('External texture') && line.includes('created')) {
      console.log('📷 Textura externa criada');
    }

    // Vídeo carregado na tela
    if (line.includes('video') && line.includes('loaded') ||
        line.includes('ready') && line.includes('display') ||
        line.includes('frame displayed')) {
      this.finishStage('rendering');
      this.stats.state = FlowState.LOADED;
      this.stats.endTime = Date.now();
      this.stats.durationMs = this.stats.endTime - this.stats.startTime;
      this.addToTimeline(FlowState.LOADED);
      console.log('✅ Vídeo carregado na tela!');
    }

    // Erros
    if (line.includes('error') || line.includes('Error') || line.includes('failed') || line.includes('Failed')) {
      this.stats.errors.push(line);
      this.stats.state = FlowState.ERROR;
      
      if (this.currentStage) {
        const stage = this.stages.get(this.currentStage);
        if (stage) {
          stage.errors++;
          stage.success = false;
        }
      }
      
      console.log(`❌ Erro: ${line}`);
    }
  }

  /**
   * Define uma nova etapa
   */
  private setStage(id: string, name: string): void {
    if (this.currentStage && this.currentStage !== id) {
      this.finishStage(this.currentStage);
    }
    
    this.currentStage = id;
    
    if (!this.stages.has(id)) {
      this.stages.set(id, {
        name,
        startTime: Date.now(),
        endTime: 0,
        duration: 0,
        success: true,
        errors: 0,
        frames: 0
      });
    }
  }

  /**
   * Finaliza uma etapa
   */
  private finishStage(id: string): void {
    const stage = this.stages.get(id);
    if (stage && stage.endTime === 0) {
      stage.endTime = Date.now();
      stage.duration = stage.endTime - stage.startTime;
      console.log(`✓ ${stage.name} completada em ${stage.duration}ms (${stage.frames} frames)`);
    }
  }

  /**
   * Atualiza frames de uma etapa
   */
  private updateStageFrames(id: string, count: number): void {
    const stage = this.stages.get(id);
    if (stage) {
      stage.frames += count;
    }
  }

  /**
   * Adiciona estado à timeline
   */
  private addToTimeline(state: FlowState): void {
    if (this.stats.timeline[this.stats.timeline.length - 1] !== state) {
      this.stats.timeline.push(state);
    }
  }

  /**
   * Imprime barra de progresso
   */
  printProgress(): void {
    const stages = ['extraction', 'decoding', 'rendering'];
    let progress = 0;
    let progressText = '';

    for (const stageId of stages) {
      const stage = this.stages.get(stageId);
      if (stage) {
        progress += 25;
        progressText += stage.endTime > 0 ? '✓' : (stage.startTime > 0 ? '⟳' : '○');
      } else {
        progressText += '○';
      }
    }

    if (this.stats.state === FlowState.LOADED) {
      progress = 100;
    }

    const bar = '█'.repeat(Math.floor(progress / 5)) + '░'.repeat(20 - Math.floor(progress / 5));
    console.log(`\n[${bar}] ${progress}% ${progressText}`);
  }

  /**
   * Imprime estatísticas detalhadas
   */
  printStats(): void {
    console.log('\n📊 Estatísticas do Fluxo:');
    console.log(`  Estado: ${this.stats.state}`);
    
    if (this.stats.startTime > 0) {
      const elapsed = (Date.now() - this.stats.startTime) / 1000;
      console.log(`  Tempo decorrido: ${elapsed.toFixed(2)}s`);
    }
    
    if (this.stats.endTime > 0) {
      console.log(`  Tempo total: ${(this.stats.durationMs / 1000).toFixed(2)}s`);
    }
    
    console.log(`  Frames extraídos: ${this.stats.framesExtracted}`);
    console.log(`  Frames decodificados: ${this.stats.framesDecoded}`);
    console.log(`  Frames renderizados: ${this.stats.framesRendered}`);
    
    if (this.stats.fps > 0) {
      console.log(`  FPS: ${this.stats.fps.toFixed(1)}`);
    }
    
    console.log(`  Erros: ${this.stats.errors.length}`);
    
    console.log('\n⏱️  Detalhes por Etapa:');
    for (const [id, stage] of this.stages) {
      const duration = stage.endTime > 0 ? `${stage.duration}ms` : 'em progresso';
      const status = stage.success ? '✓' : '✗';
      console.log(`  ${status} ${stage.name}: ${duration} (${stage.frames} frames, ${stage.errors} erros)`);
    }

    if (this.stats.errors.length > 0) {
      console.log('\n❌ Erros:');
      this.stats.errors.forEach((err, i) => {
        console.log(`  ${i + 1}. ${err}`);
      });
    }
  }

  /**
   * Imprime timeline visual
   */
  printTimeline(): void {
    console.log('\n📅 Timeline:');
    const timeline = this.stats.timeline;
    let output = '';
    
    for (let i = 0; i < timeline.length; i++) {
      const state = timeline[i];
      const isLast = i === timeline.length - 1;
      const arrow = isLast ? '' : ' → ';
      
      const icons = {
        [FlowState.IDLE]: '⏸️',
        [FlowState.EXTRACTING]: '🎬',
        [FlowState.DECODING]: '🔊',
        [FlowState.RENDERING]: '🖼️',
        [FlowState.LOADED]: '✅',
        [FlowState.ERROR]: '❌'
      };
      
      output += `${icons[state]} ${state}${arrow}`;
    }
    
    console.log(`  ${output}`);
  }

  /**
   * Obtém estatísticas
   */
  getStats(): FlowStats {
    return { ...this.stats };
  }

  /**
   * Obtém estatísticas por etapa
   */
  getStageStats(): Map<string, StageStats> {
    return new Map(this.stages);
  }

  /**
   * Verifica se o fluxo está completo
   */
  isComplete(): boolean {
    return this.stats.state === FlowState.LOADED;
  }

  /**
   * Verifica se há erros
   */
  hasErrors(): boolean {
    return this.stats.errors.length > 0;
  }
}

/**
 * Monitor do fluxo completo: extração → decodificação → renderização → tela
 */
async function monitorFlow() {
  console.log('═══════════════════════════════════════════════════════════');
  console.log('║          FLOW MONITOR - EXTRAÇÃO → TELA                  ║');
  console.log('╚═════════════════════════════════════════════════════════\n');

  const flowMonitor = new FlowMonitor();

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'flow-monitor-logs.txt',
    updateState: true,
    statsInterval: 2,
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
             line.includes('texture') ||
             line.toLowerCase().includes('video');
    },

    onLogLine: (line) => {
      flowMonitor.processLine(line);
    }
  });

  // Atualizar progresso periodicamente
  const progressInterval = setInterval(() => {
    flowMonitor.printProgress();
    flowMonitor.printTimeline();
    
    if (flowMonitor.isComplete()) {
      console.log('\n✅ Fluxo completado!');
      flowMonitor.printStats();
      clearInterval(progressInterval);
      monitor.stop();
    } else if (flowMonitor.hasErrors()) {
      console.log('\n⚠️  Erros detectados, continuando...');
      flowMonitor.printStats();
    }
  }, 2000);

  try {
    await monitor.start();
  } catch (error) {
    clearInterval(progressInterval);
    console.error(`❌ Erro no monitor: ${error}`);
    flowMonitor.printStats();
    process.exit(1);
  }
}

monitorFlow().catch(console.error);
