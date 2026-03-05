import { ADBMonitor, ADBCommand, ADBMonitorOptions } from './ADBMonitor';
import { LogMapper } from './LogMapper';
import { LogParser, createInitialState } from './EntityParser';
import type { AppState } from './entities';
import { writeFileSync } from 'node:fs';

/**
 * Script para monitorar logs em tempo real via ADB e processar com o framework
 */

interface RealtimeMonitorOptions extends ADBMonitorOptions {
  /**
   * Salva logs em arquivo
   */
  saveToFile?: boolean;

  /**
   * Arquivo para salvar logs
   */
  outputFile?: string;

  /**
   * Atualiza estado da aplicação em tempo real
   */
  updateState?: boolean;

  /**
   * Intervalo para imprimir estatísticas (em segundos)
   */
  statsInterval?: number;

  /**
   * Filtra logs que devem ser processados
   */
  filterLogs?: (line: string) => boolean;
}

export class RealtimeLogMonitor {
  private mapper: LogMapper;
  private parser: LogParser;
  private appState: AppState;
  private logBuffer: string[] = [];
  private statsTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private options: RealtimeMonitorOptions = {}) {
    this.mapper = new LogMapper();
    this.parser = new LogParser();
    this.appState = createInitialState();
  }

  /**
   * Inicia o monitoramento em tempo real
   */
  async start(): Promise<void> {
    console.log('═══════════════════════════════════════════════════════════');
    console.log('║          REALTIME LOG MONITOR - CHOPCUT                 ║');
    console.log('╚═══════════════════════════════════════════════════════════\n');

    const monitor = new ADBMonitor({
      ...this.options,
      onLogLine: (line) => this.onLogLine(line),
      onError: (error) => {
        console.error(`❌ Error: ${error.message}`);
      },
      onClose: (code) => {
        this.stop();
      }
    });

    try {
      await monitor.start();

      if (this.options.statsInterval) {
        this.statsTimer = setInterval(() => {
          monitor.printStats();
          this.printStateSummary();
        }, this.options.statsInterval * 1000);
      }

      console.log('✓ Monitoring started. Press Ctrl+C to stop.\n');

    } catch (error) {
      console.error(`❌ Failed to start monitor: ${error}`);
      process.exit(1);
    }
  }

  /**
   * Para o monitoramento
   */
  stop(): void {
    if (this.statsTimer) {
      clearInterval(this.statsTimer);
      this.statsTimer = null;
    }

    if (this.options.saveToFile && this.logBuffer.length > 0) {
      const outputFile = this.options.outputFile || 'realtime-logs.txt';
      writeFileSync(outputFile, this.logBuffer.join('\n'), 'utf-8');
      console.log(`\n✓ Logs saved to: ${outputFile}`);
    }

    this.printFinalReport();
  }

  /**
   * Callback para cada linha de log
   */
  private onLogLine(line: string): void {
    if (this.options.filterLogs && !this.options.filterLogs(line)) {
      return;
    }

    this.logBuffer.push(line);

    if (this.options.updateState) {
      const stateUpdate = this.parser.parseState(line);
      if (stateUpdate) {
        this.appState = this.parser.mergeState(this.appState, stateUpdate);
      }
    }

    const logEntry = this.parser.parseLog(line);
    if (logEntry) {
      this.printLogEntry(logEntry);
    }

    if (this.options.onLogLine) {
      this.options.onLogLine(line);
    }
  }

  /**
   * Imprime uma entrada de log formatada
   */
  private printLogEntry(entry: any): void {
    const level = entry.level.padEnd(7);
    const tag = entry.tag?.padEnd(20) || 'N/A'.padEnd(20);
    const message = entry.message?.substring(0, 100) || '';
    
    console.log(`[${level}] ${tag} ${message}`);
  }

  /**
   * Imprime resumo do estado atual
   */
  private printStateSummary(): void {
    console.log('\n📱 CURRENT STATE:');
    
    if (this.appState.bottomSheetGallery.selectedVideo) {
      console.log(`  Video: ${this.appState.bottomSheetGallery.selectedVideo.id}`);
    }
    
    if (this.appState.timelineEditor.scrollVelocity !== 0) {
      console.log(`  Scroll Velocity: ${this.appState.timelineEditor.scrollVelocity} px/ms`);
    }
    
    if (this.appState.preload.uri) {
      console.log(`  Preload: ${this.appState.preload.stripsLoaded}/${this.appState.preload.totalSegments} strips`);
      console.log(`  Status: ${this.appState.preload.currentState}`);
    }
    
    if (this.appState.graphics.glRenderer.isInitialized) {
      console.log(`  GLRenderer: Initialized ✓`);
    }
  }

  /**
   * Imprime relatório final
   */
  private printFinalReport(): void {
    console.log('\n═══════════════════════════════════════════════════════════');
    console.log('║                    FINAL REPORT                         ║');
    console.log('╚═══════════════════════════════════════════════════════════\n');

    console.log(`📊 Statistics:`);
    console.log(`  Total logs captured: ${this.logBuffer.length}`);

    console.log('\n🎨 Graphics:');
    console.log(`  GLRenderer Initialized: ${this.appState.graphics.glRenderer.isInitialized}`);
    console.log(`  External Texture ID: ${this.appState.graphics.glRenderer.externalTextureId ?? 'N/A'}`);

    console.log('\n📱 BottomSheetGallery:');
    console.log(`  Selected Video: ${this.appState.bottomSheetGallery.selectedVideo?.id ?? 'None'}`);

    console.log('\n⏱️ TimelineEditor:');
    console.log(`  Scroll Velocity: ${this.appState.timelineEditor.scrollVelocity} px/ms`);
    console.log(`  Error: ${this.appState.timelineEditor.exoPlayerError ?? 'None'}`);

    console.log('\n🔄 Preload:');
    console.log(`  Strips Loaded: ${this.appState.preload.stripsLoaded}/${this.appState.preload.totalSegments}`);
    console.log(`  Current State: ${this.appState.preload.currentState}`);
    console.log(`  Is Ready: ${this.appState.preload.isReady}`);

    console.log('\n═══════════════════════════════════════════════════════════');
  }

  /**
   * Obtém o estado atual da aplicação
   */
  getState(): AppState {
    return this.appState;
  }

  /**
   * Obtém o buffer de logs
   */
  getLogs(): string[] {
    return this.logBuffer;
  }
}

export default RealtimeLogMonitor;
