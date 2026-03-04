#!/usr/bin/env node
/**
 * Monitor de Atividade ChopCut - Node.js
 * Mostra na tela E salva em arquivo simultaneamente
 * 
 * Uso: node monitor.js [--1|--2|--3] [--arquivo=nome.log]
 * --1 = Alta (só críticos)
 * --2 = Média (eventos importantes)
 * --3 = Baixa (todos os logs)
 */

const { spawn } = require('child_process');
const { writeFileSync, appendFileSync } = require('fs');

// Cores ANSI
const GREEN = '\x1b[1;32m';
const YELLOW = '\x1b[1;33m';
const CYAN = '\x1b[1;36m';
const RED = '\x1b[1;31m';
const NC = '\x1b[0m';

// Nível de verbosidade
const VERBOSIDADE = process.argv[2] || '2';

// Arquivo de log
const ARQUIVO = process.argv.find(arg => arg.startsWith('--arquivo='))?.split('=')[1] || 'chopcut_monitor.log';

// Contadores de operações
let extracting = false;
let stripBuilding = false;
let cacheOps = false;
let extractingCount = 0;
let stripBuildingCount = 0;
let cacheOpsCount = 0;
let lastExtractTime = null;
let lastStripTime = null;
let lastCacheTime = null;

// Limpa terminal e mostra cabeçalho fixo
function showHeader() {
    process.stdout.write('\x1b[2J\x1b[H');
    
    let verbDesc;
    switch (VERBOSIDADE) {
        case '--1':
            verbDesc = '[1] ALTA - Críticos';
            break;
        case '--2':
            verbDesc = '[2] MÉDIA - Importantes';
            break;
        case '--3':
            verbDesc = '[3] BAIXA - Todos';
            break;
    }
    
    console.log('╔══════════════════════════════════════════════════════╗');
    console.log('║       MONITOR CHOPCUT - TELA + ARQUIVO               ║');
    console.log('╚══════════════════════════════════════════════════════╝');
    console.log('');
    console.log('Verbosidade: ' + verbDesc);
    console.log('Arquivo: ' + ARQUIVO);
    console.log('');
    
    // Indicadores fixos (linha 10)
    updateIndicators();
}

// Atualiza indicadores de operações
function updateIndicators() {
    process.stdout.write('\x1b[10;0H');
    
    let status = [];
    let fluxo = [];
    
    if (extracting) {
        status.push(YELLOW + '⚡ EXTRAÇÃO' + NC);
        fluxo.push(YELLOW + '⚡' + NC);
    }
    if (stripBuilding) {
        status.push(CYAN + '🖼️  MONTAGEM' + NC);
        fluxo.push(CYAN + '🖼️' + NC);
    }
    if (cacheOps) {
        status.push(GREEN + '💾 CACHE' + NC);
        fluxo.push(GREEN + '💾' + NC);
    }
    
    if (status.length === 0) {
        status.push(YELLOW + '⏳ AGUARDANDO...' + NC);
    }
    
    process.stdout.write('Status: ' + status.join(' | ') + '                    ');
    
    // Mostra fluxo de operações com setas
    process.stdout.write('\x1b[11;0H');
    if (fluxo.length > 0) {
        let fluxoStr = 'Fluxo: ' + fluxo.join(' → ');
        process.stdout.write(fluxoStr.padEnd(70));
    } else {
        process.stdout.write('Fluxo: -');
    }
    
    // Mostra contadores
    process.stdout.write('\x1b[12;0H');
    let counters = [
        YELLOW + '⚡ Extração: ' + extractingCount + NC,
        CYAN + '🖼️  Montagem: ' + stripBuildingCount + NC,
        GREEN + '💾 Cache: ' + cacheOpsCount + NC
    ];
    process.stdout.write(counters.join(' | '));
    
    process.stdout.write('\x1b[14;0H');
}

// Reseta indicadores
function resetIndicators() {
    const wasExtracting = extracting;
    const wasStripBuilding = stripBuilding;
    const wasCacheOps = cacheOps;
    
    extracting = false;
    stripBuilding = false;
    cacheOps = false;
    
    updateIndicators();
}

// Escreve no arquivo (sem cores)
function writeToFile(timestamp, msg, type) {
    const line = timestamp + ' [' + type + '] ' + msg;
    try {
        appendFileSync(ARQUIVO, line + '\n');
    } catch (err) {
        // Silencia erro de arquivo
    }
}

// Cria arquivo com cabeçalho
function initFile() {
    const header = '═══ MONITOR CHOPCUT ═══\n';
    header += 'Iniciado: ' + new Date().toISOString() + '\n';
    header += 'Verbosidade: ' + VERBOSIDADE + '\n';
    header += '═══ LOGS ═══\n';
    
    try {
        writeFileSync(ARQUIVO, header);
    } catch (err) {
        console.log('⚠ Erro ao criar arquivo de log');
    }
}

// Pega PID do ChopCut
function getPID() {
    return new Promise((resolve, reject) => {
        const proc = spawn('adb', ['shell', 'pidof', 'com.chopcut']);
        let output = '';
        
        proc.stdout.on('data', (data) => {
            output += data.toString();
        });
        
        proc.on('close', (code) => {
            const pid = output.trim();
            if (pid) {
                resolve(pid);
            } else {
                reject(new Error('ChopCut não está em execução'));
            }
        });
        
        proc.on('error', reject);
    });
}

// Filtro baseado na verbosidade
function getFilter() {
    switch (VERBOSIDADE) {
        case '--1':
            return '(Cache HIT|Cache MISS|Segment.*COMPLETED|Erro|FAILED)';
        case '--2':
            return '(extractSegment STARTED|Extracting segment|Segment.*COMPLETED|Strip.*loaded|Cache HIT|Cache MISS|Cached segment)';
        case '--3':
            return '(extract|Cache|Strip|loaded|Batch)';
        default:
            return '(extractSegment STARTED|Extracting segment|Segment.*COMPLETED|Strip.*loaded|Cache HIT|Cache MISS|Cached segment)';
    }
}

// Monitora logs
function monitorLogs(pid) {
    console.log('═══ LOGS ═══');
    console.log('');
    console.log('PID: ' + pid);
    console.log('Ctrl+C para encerrar');
    console.log('');
    
    // Inicializa arquivo
    initFile();
    
    // Limpa logcat anterior
    const clearLogcat = spawn('adb', ['logcat', '-c']);
    
    clearLogcat.on('close', () => {
        // Inicia monitoramento
        const logcat = spawn('adb', ['logcat', '-v', 'time', '--pid=' + pid]);
        
        // Timeout para resetar indicadores
        let timeoutTimer;
        
        function resetTimeout() {
            clearTimeout(timeoutTimer);
            timeoutTimer = setTimeout(() => {
                resetIndicators();
            }, 5000);
        }
        
        logcat.stdout.on('data', (data) => {
            const lines = data.toString().split('\n');
            
            lines.forEach(line => {
                if (!line.trim()) return;
                
                // Filtro baseado na verbosidade
                if (!new RegExp(getFilter(), 'i').test(line)) return;
                
                // Extrai timestamp e mensagem (formato: 03-04 13:44:30.997 TAG MSG)
                const lineMatch = line.match(/^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+\S+\/[^:]+:\s+(.*)$/);
                if (!lineMatch) return;
                
                const date = lineMatch[1];
                const time = lineMatch[2];
                const msg = lineMatch[4];
                const timestamp = date + ' ' + time;
                
                // Detecta tipo de operação
                let type = 'INFO';
                
                if (/extractSegment STARTED|Extracting segment|Batch|extractBatch/i.test(msg)) {
                    extracting = true;
                    extractingCount++;
                    lastExtractTime = timestamp;
                    type = 'EXTRAÇÃO';
                    console.log(YELLOW + timestamp + ' ⚡ ' + msg + NC);
                    writeToFile(timestamp, msg, type);
                    resetTimeout();
                } 
                else if (/COMPLETED|drawBitmap|Strip.*loaded|frames.*extraídos|Segment.*BATCH MODE/i.test(msg)) {
                    stripBuilding = true;
                    stripBuildingCount++;
                    lastStripTime = timestamp;
                    type = 'MONTAGEM';
                    console.log(CYAN + timestamp + ' 🖼️  ' + msg + NC);
                    writeToFile(timestamp, msg, type);
                    resetTimeout();
                }
                else if (/Cache HIT|Cache MISS|Cached|saveToCache|Saving|Cache PUT/i.test(msg)) {
                    cacheOps = true;
                    cacheOpsCount++;
                    lastCacheTime = timestamp;
                    type = 'CACHE';
                    console.log(GREEN + timestamp + ' 💾 ' + msg + NC);
                    writeToFile(timestamp, msg, type);
                    resetTimeout();
                }
                else if (/Erro|FAILED/i.test(msg)) {
                    type = 'ERRO';
                    console.log(RED + timestamp + ' ❌ ' + msg + NC);
                    writeToFile(timestamp, msg, type);
                    resetTimeout();
                }
                else {
                    console.log(timestamp + ' ▸ ' + msg);
                    writeToFile(timestamp, msg, 'INFO');
                }
                
                // Atualiza indicadores após cada log
                updateIndicators();
            });
        });
        
        logcat.stderr.on('data', (data) => {
            console.error(data.toString());
        });
        
        // Cleanup ao sair
        process.on('SIGINT', () => {
            logcat.kill();
            
            // Finaliza arquivo
            let footer = '\n═══ ENCERRADO ═══\n';
            footer += 'Finalizado: ' + new Date().toISOString() + '\n';
            try {
                appendFileSync(ARQUIVO, footer);
            } catch (err) {}
            
            process.stdout.write('\x1b[10;0H');
            console.log(YELLOW + '═══ ENCERRADO ═══' + NC);
            console.log('Logs salvos em: ' + ARQUIVO);
            process.exit(0);
        });
    });
}

// Inicia
showHeader();

getPID()
    .then(pid => {
        process.stdout.write('\x1b[13;0H');
        console.log(GREEN + '✓ ChopCut ativo (PID: ' + pid + ')' + NC);
        monitorLogs(pid);
    })
    .catch(err => {
        process.stdout.write('\x1b[13;0H');
        console.log(YELLOW + '⚠ ' + err.message + NC);
        console.log('');
        console.log('Verifique se o app ChopCut está em execução.');
        process.exit(1);
    });
