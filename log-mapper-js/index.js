/**
 * @file Ponto de entrada principal do Log Mapper
 * @module index
 * @description Analisa múltiplos arquivos de log e gera relatórios
 */

const { LogMapper } = require('./LogMapper');

/**
 * Função principal do Log Mapper
 * @async
 * @description Escaneia, analisa e gera relatórios dos logs do ChopCut
 * @returns {Promise<void>}
 */
async function main() {
  const mapper = new LogMapper();
  
  /**
   * Lista de arquivos de log para analisar
   * @type {string[]}
   */
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
  
  /**
   * Processa cada arquivo de log
   */
  for (const file of logFiles) {
    try {
      console.log(`  → Loading: ${file}`);
      await mapper.parseFile(file);
    } catch (error) {
      console.log(`  ⚠ Skipped: ${file} (not found or error)`);
    }
  }
  
  console.log('\n');
  
  /**
   * Imprime relatório no console
   */
  mapper.printReport();
  
  /**
   * Salva relatório em arquivo Markdown
   * @type {string}
   */
  const outputPath = 'log-report.md';
  await mapper.saveReport(outputPath);
}

/**
 * Executa a função principal e captura erros
 */
main().catch(console.error);
