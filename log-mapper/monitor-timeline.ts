import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitora logs da timeline e editor
 */
async function monitorTimeline() {
  console.log('⏱️  Monitoring Timeline & Editor logs...\n');

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'timeline-logs.txt',
    updateState: true,
    statsInterval: 2,
    filterLogs: (line) => {
      return line.includes('TimelineEditor') ||
             line.includes('Timeline') ||
             line.includes('Scroll') ||
             line.includes('scroll') ||
             line.includes('ExoPlayer');
    },
    onLogLine: (line) => {
      if (line.includes('Scroll velocity')) {
        console.log(`📜 ${line}`);
      } else if (line.includes('ExoPlayer error')) {
        console.log(`❌ ${line}`);
      } else if (line.includes('TimelineEditor')) {
        console.log(`⏱️  ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorTimeline().catch(console.error);
