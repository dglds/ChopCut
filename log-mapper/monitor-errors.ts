import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitora apenas logs de erros do ChopCut
 */
async function monitorErrors() {
  console.log('🔴 Monitoring ERROR logs only...\n');

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    level: 'E',
    saveToFile: true,
    outputFile: 'chopcut-errors.txt',
    updateState: false,
    statsInterval: 10,
    filterLogs: (line) => {
      return line.includes('ERROR') || 
             line.includes('error') || 
             line.includes('Exception') ||
             line.includes('Failed') ||
             line.includes('failed');
    },
    onLogLine: (line) => {
      console.log(`🔴 ${line}`);
    }
  });

  await monitor.start();
}

monitorErrors().catch(console.error);
