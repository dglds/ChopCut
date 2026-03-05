import { RealtimeLogMonitor } from './src/RealtimeMonitor';

/**
 * Monitora logs gráficos (OpenGL, GLRenderer, SurfaceBridge)
 */
async function monitorGraphics() {
  console.log('🎨 Monitoring Graphics logs...\n');

  const monitor = new RealtimeLogMonitor({
    package: 'com.chopcut',
    saveToFile: true,
    outputFile: 'graphics-logs.txt',
    updateState: true,
    statsInterval: 3,
    filterLogs: (line) => {
      return line.includes('GLRenderer') ||
             line.includes('SurfaceBridge') ||
             line.includes('OpenGL') ||
             line.includes('EGL') ||
             line.includes('Texture') ||
             line.includes('mvpMatrix');
    },
    onLogLine: (line) => {
      if (line.includes('initialized')) {
        console.log(`✅ ${line}`);
      } else if (line.includes('error') || line.includes('Error')) {
        console.log(`❌ ${line}`);
      } else {
        console.log(`🎨 ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorGraphics().catch(console.error);
