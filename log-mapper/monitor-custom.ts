import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitor personalizado com filtros customizados
 */
async function monitorCustom() {
  console.log('🔧 Custom Monitor - Configurar seus filtros abaixo\n');

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'custom-logs.txt',
    updateState: true,
    statsInterval: 5,
    
    // Filtros personalizados - modifique conforme necessário
    filterLogs: (line) => {
      const keywords = [
        'GLRenderer',
        'TimelineEditor',
        'PreloadViewModel',
        'Cache',
        'ExoPlayer'
      ];
      return keywords.some(kw => line.includes(kw));
    },
    
    onLogLine: (line) => {
      if (line.includes('ERROR') || line.includes('error')) {
        console.log(`🔴 ${line}`);
      } else if (line.includes('initialized') || line.includes('ready=true')) {
        console.log(`✅ ${line}`);
      } else {
        console.log(`📝 ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorCustom().catch(console.error);
