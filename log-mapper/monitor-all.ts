import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitora todos os logs do ChopCut em tempo real
 */
async function monitorAll() {
  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    level: 'D',
    saveToFile: true,
    outputFile: 'chopcut-logs.txt',
    updateState: true,
    statsInterval: 5,
    onLogLine: (line) => {
      if (line.includes('ERROR') || line.includes('error')) {
        console.log(`🔴 ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorAll().catch(console.error);
