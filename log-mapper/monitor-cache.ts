import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitora logs de cache e pré-carregamento
 */
async function monitorCache() {
  console.log('🔄 Monitoring Cache & Preload logs...\n');

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'cache-logs.txt',
    updateState: true,
    statsInterval: 2,
    filterLogs: (line) => {
      return line.includes('PreloadViewModel') ||
             line.includes('ThumbnailViewModel') ||
             line.includes('cache') ||
             line.includes('Cache') ||
             line.includes('preload') ||
             line.includes('thumbnail') ||
             line.includes('strip');
    },
    onLogLine: (line) => {
      if (line.includes('ready=true')) {
        console.log(`✅ ${line}`);
      } else if (line.includes('error') || line.includes('Error')) {
        console.log(`❌ ${line}`);
      } else if (line.includes('startPreload')) {
        console.log(`🚀 ${line}`);
      } else if (line.includes('strips loaded')) {
        console.log(`📊 ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorCache().catch(console.error);
