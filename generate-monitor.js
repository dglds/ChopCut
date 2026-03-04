/**
 * Gera comando bash para monitorar ChopCut
 * 
 * Uso:
 *   node generate-monitor.js [--1|--2|--3]
 * 
 * Copie e cole o comando gerado no terminal
 */

const VERBOSIDADE = process.argv[2] || '2';

let FILTER;
let DESC;

switch (VERBOSIDADE) {
    case '--1':
        FILTER = '(Cache HIT|Cache MISS|Segment.*COMPLETED|Erro|FAILED)';
        DESC = 'Alta - Críticos';
        break;
    case '--2':
        FILTER = '(extractSegment STARTED|Extracting segment|Segment.*COMPLETED|Strip.*loaded|Cache HIT|Cache MISS|Cached segment)';
        DESC = 'Média - Importantes';
        break;
    case '--3':
        FILTER = '(extract|Cache|Strip|loaded|Batch)';
        DESC = 'Baixa - Todos';
        break;
}

const COMMAND = `\
adb logcat -c && \
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | \
grep --line-buffered -iE "${FILTER}"`;

console.log('═══ COMANDO PARA MONITORAR CHOPCUT ═══');
console.log('');
console.log('Verbosidade: ' + DESC);
console.log('');
console.log('─────────────────────────────────────────────');
console.log('');
console.log(COMMAND);
console.log('');
console.log('─────────────────────────────────────────────');
