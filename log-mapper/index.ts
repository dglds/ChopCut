import { LogMapper } from './src/LogMapper';

async function main() {
  const mapper = new LogMapper();
  
  const logFiles = [
    '../chopcut_monitor.log',
    '../logs_por_contexto.txt',
    '../logs/thumbnail_monitor_20260227_130807.log',
    '../logs/thumbnail_monitor_20260227_131000.log',
    '../logs/thumbnail_monitor_20260227_131007.log',
    '../monitor.js',
    '../log.js'
  ];
  
  console.log('📂 Scanning for log files...');
  
  for (const file of logFiles) {
    try {
      console.log(`  → Loading: ${file}`);
      await mapper.parseFile(file);
    } catch (error) {
      console.log(`  ⚠ Skipped: ${file} (not found or error)`);
    }
  }
  
  console.log('\n');
  
  mapper.printReport();
  
  const outputPath = 'log-report.md';
  await mapper.saveReport(outputPath);
}

main().catch(console.error);