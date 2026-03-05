#!/usr/bin/env node
/**
 * strip-monitor.js — Monitor de extração, strips e cache (ChopCut)
 *
 * Foco: identificar o que está degradando performance e o que é redundante.
 *
 * Uso: node strip-monitor.js [--log=arquivo.log]
 *
 * Detecta:
 *  1. Logs duplicados via Log.d + Timber.d (mesmo evento, duas chamadas)
 *  2. Mensagens de alta frequência sem progresso (ex: isReadyFlow)
 *  3. Mesmo segmento extraído mais de uma vez
 *  4. Cache MISS repetido para o mesmo segmento
 *  5. Timing lento em extractBatch (> 300ms/frame = bottleneck)
 *  6. Extrações concorrentes além do semaphore esperado
 */

'use strict';

const { spawn } = require('child_process');
const fs = require('fs');

// ─── Config ───────────────────────────────────────────────────────────────────
const LOG_FILE = (() => {
    const arg = process.argv.find(a => a.startsWith('--log='));
    return arg ? arg.split('=')[1] : 'logs.log';
})();

// Janela de tempo (ms) para detectar duplicatas semânticas (Log.d + Timber.d)
const TWIN_WINDOW_MS = 300;
// Frequência que dispara alerta de log ruidoso (vezes em N segundos)
const NOISE_THRESHOLD = 8;
const NOISE_WINDOW_MS = 3000;
// Tempo máximo aceitável por frame em extração (ms)
const SLOW_FRAME_THRESHOLD_MS = 300;

// ─── ANSI ─────────────────────────────────────────────────────────────────────
const C = {
    reset:   '\x1b[0m',
    bold:    '\x1b[1m',
    dim:     '\x1b[2m',
    red:     '\x1b[1;31m',
    yellow:  '\x1b[1;33m',
    cyan:    '\x1b[1;36m',
    green:   '\x1b[1;32m',
    magenta: '\x1b[1;35m',
    blue:    '\x1b[1;34m',
    orange:  '\x1b[38;5;208m',
    gray:    '\x1b[90m',
    white:   '\x1b[97m',
};

// ─── Estado global ────────────────────────────────────────────────────────────

/** Deduplicação exata: chave normalizada → última vez visto (timestamp ms) */
const seenExact = new Map();   // key → timestamp

/**
 * Deduplicação semântica (twin): fragmento de mensagem normalizada → { ts, from }
 * Detecta quando Log.d e Timber.d imprimem a mesma coisa com ±300ms de diferença.
 */
const seenSemantic = new Map();

/** Estado por segmento: Map<number, SegState> */
const segs = new Map();

/** Frequência de logs ruidosos: chave normalizada → timestamps[] */
const freqMap = new Map();

// Extrações ativas (segmentos atualmente em extração)
const activeExtractions = new Set();

const stats = {
    totalLines: 0,
    printed: 0,
    suppressedExact: 0,
    suppressedTwin: 0,
    suppressedNoise: 0,
    cacheHits: 0,
    cacheMisses: 0,
    extractions: 0,
    cacheWrites: 0,
    errors: 0,
    anomalies: 0,
    slowFrames: 0,
};

const anomalies = [];  // lista de strings para o resumo

const startedAt = Date.now();
const logStream = fs.createWriteStream(LOG_FILE, { flags: 'a' });
logStream.write(`\n${'═'.repeat(72)}\nSESSÃO: ${new Date().toISOString()}\n${'═'.repeat(72)}\n`);

// ─── Helpers ──────────────────────────────────────────────────────────────────

function ts() { return new Date().toTimeString().slice(0, 12); }

function segOf(n) {
    if (!segs.has(n)) segs.set(n, {
        state: 'IDLE',   // IDLE | MISS | EXTRACTING | EXTRACTED | CACHED | HIT
        misses: 0, extractions: 0, hits: 0, writes: 0, warnings: []
    });
    return segs.get(n);
}

function segNum(msg) {
    const m = msg.match(/segment[s]?\s*[:#=]?\s*(\d+)/i)
           || msg.match(/\bseg(?:ment)?\s+(\d+)/i)
           || msg.match(/\bsegIdx\s*=\s*(\d+)/i);
    return m ? parseInt(m[1], 10) : null;
}

/**
 * Normaliza mensagem removendo partes dinâmicas.
 * Usado tanto para deduplicação exata quanto semântica.
 */
function normalize(msg) {
    return msg
        .replace(/\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}/g, 'TS')
        .replace(/\d+(\.\d+)?(ms|KB|MB|GB|fps|px|%)/gi, 'N$2')
        .replace(/\d+x\d+/g, 'NxN')
        .replace(/uri=content:\/\/[^\s,)]+/gi, 'URI')
        .replace(/strip_v\d+_[^\s]+/g, 'STRIP_KEY')
        .replace(/\b[0-9a-f]{8,}\b/gi, 'HASH')
        .replace(/\b\d{4,}\b/g, 'N')    // números longos (timestamps, sizes)
        .replace(/\b\d+\b/g, 'n')        // números curtos (índices, contadores)
        .replace(/\s{2,}/g, ' ')
        .trim();
}

/**
 * Fragmento semântico: extrai a "essência" de uma mensagem para detectar twins.
 * Ex: "=== startPreload CALLED ===" e "PreloadViewModel.startPreload CALLED" → mesmo evento
 */
function semanticKey(msg) {
    return msg
        .replace(/===\s*/g, '')
        .replace(/[A-Z][a-z]+\w+\.(startPreload|selectVideo|loadWaveform|preload|getStrip)/g, '$1')
        .replace(/[A-Z][a-z]+ViewModel/g, 'VM')
        .replace(/\d+/g, 'n')
        .replace(/[^a-zA-Z\s]/g, ' ')
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, 40);   // só os primeiros 40 chars
}

// ─── Impressão ────────────────────────────────────────────────────────────────

function print(color, icon, label, msg) {
    const line = `${C.gray}${ts()}${C.reset} ${color}${icon}${C.reset} ${C.bold}${color}[${label}]${C.reset} ${msg}`;
    console.log(line);
    stats.printed++;
    logStream.write(`${ts()} [${label}] ${msg}\n`);
}

function printAnomaly(msg, segN = null) {
    const line = `${C.gray}${ts()}${C.reset} ${C.red}⚠  ${C.bold}[ANOMALIA]${C.reset} ${C.red}${msg}${C.reset}`;
    console.log(line);
    stats.anomalies++;
    stats.printed++;
    anomalies.push(segN !== null ? `seg=${segN}: ${msg}` : msg);
    logStream.write(`${ts()} [ANOMALIA] ${msg}\n`);
    if (segN !== null) segOf(segN).warnings.push(msg);
}

function printSuppressed(reason, msg) {
    // Imprime em dim para não poluir, mas fica no log
    const line = `${C.dim}${ts()} ··· [${reason}] ${msg}${C.reset}`;
    // Não vai para console, só para arquivo
    logStream.write(`${ts()} [SUPRIMIDO:${reason}] ${msg}\n`);
}

// ─── Detecção de frequência (logs ruidosos) ────────────────────────────────────

function trackFrequency(key, msg) {
    const now = Date.now();
    if (!freqMap.has(key)) freqMap.set(key, []);
    const times = freqMap.get(key);
    times.push(now);

    // Remove entradas fora da janela
    const cutoff = now - NOISE_WINDOW_MS;
    while (times.length > 0 && times[0] < cutoff) times.shift();

    if (times.length === NOISE_THRESHOLD) {
        printAnomaly(`Log ruidoso (${NOISE_THRESHOLD}x em ${NOISE_WINDOW_MS}ms) — candidato a remoção: "${msg.slice(0, 80)}"`);
        return true; // é ruído
    }
    return times.length > NOISE_THRESHOLD;
}

// ─── Pipeline de filtragem ────────────────────────────────────────────────────

/**
 * Retorna false se a linha deve ser suprimida.
 * Aplica, em ordem:
 *   1. Deduplicação exata (mesma key normalizada dentro de 5s)
 *   2. Deduplicação semântica twin (Log.d + Timber.d do mesmo evento)
 *   3. Supressão de ruído (alta frequência sem progresso)
 */
function shouldPrint(key, semKey, rawMsg) {
    const now = Date.now();

    // 1. Exata: se a mesma key normalizada já apareceu nos últimos 5s → suprimir
    const lastSeen = seenExact.get(key);
    if (lastSeen && (now - lastSeen) < 5000) {
        stats.suppressedExact++;
        printSuppressed('DUP_EXATO', rawMsg);
        return false;
    }
    seenExact.set(key, now);

    // 2. Twin: outro log com mesma essência semântica veio de origem diferente <300ms atrás
    const twin = seenSemantic.get(semKey);
    if (twin && (now - twin.ts) < TWIN_WINDOW_MS && twin.msg !== rawMsg) {
        stats.suppressedTwin++;
        printSuppressed('TWIN', rawMsg);
        // Mas avisa uma única vez sobre o padrão twin
        if (!twin.warned) {
            printAnomaly(`Twin-log detectado: mesmo evento logado 2x em <${TWIN_WINDOW_MS}ms — remover Log.d ou Timber.d duplicado\n  A: "${twin.msg.slice(0, 90)}"\n  B: "${rawMsg.slice(0, 90)}"`);
            twin.warned = true;
        }
        return false;
    }
    seenSemantic.set(semKey, { ts: now, msg: rawMsg, warned: false });

    // 3. Ruído: alta frequência
    const isNoise = trackFrequency(key, rawMsg);
    if (isNoise) {
        stats.suppressedNoise++;
        printSuppressed('RUÍDO', rawMsg);
        return false;
    }

    return true;
}

// ─── Classificação e extração de métricas ────────────────────────────────────

/**
 * Classifica uma mensagem e extrai métricas de performance quando presentes.
 * Retorna { category, icon, color, label, perfNote } ou null se irrelevante.
 */
function classify(msg) {
    // Cache MISS (memória ou disco)
    if (/Cache MISS/i.test(msg))
        return { category: 'MISS', icon: '✗', color: C.orange, label: 'CACHE MISS' };

    // Cache HIT
    if (/Cache HIT/i.test(msg))
        return { category: 'HIT', icon: '✓', color: C.green, label: 'CACHE HIT' };

    // Início de extração de segmento
    if (/extractSegment STARTED|Extracting segment \d+/i.test(msg))
        return { category: 'EXTRACT_START', icon: '⚡', color: C.yellow, label: 'EXTRAÇÃO' };

    // Batch completado — contém timing
    if (/extractBatch: Completed/i.test(msg)) {
        const avgMatch = msg.match(/avg\s+(\d+)ms\/frame/i);
        const avgMs = avgMatch ? parseInt(avgMatch[1]) : null;
        const perfNote = avgMs !== null
            ? (avgMs > SLOW_FRAME_THRESHOLD_MS
                ? `${C.red}⚠ lento: ${avgMs}ms/frame (threshold=${SLOW_FRAME_THRESHOLD_MS}ms)${C.reset}`
                : `${C.green}✓ ${avgMs}ms/frame${C.reset}`)
            : '';
        if (avgMs !== null && avgMs > SLOW_FRAME_THRESHOLD_MS) stats.slowFrames++;
        return { category: 'EXTRACT_DONE', icon: '✦', color: C.cyan, label: 'BATCH DONE', perfNote };
    }

    // Segmento extraído com sucesso
    if (/ThumbnailStrip: Segment \d+|segment.*extracted successfully/i.test(msg))
        return { category: 'EXTRACT_DONE', icon: '✦', color: C.cyan, label: 'SEG DONE' };

    // Salvo em cache (disco ou memória)
    if (/Cached segment|Cache PUT\b|Saving segment/i.test(msg))
        return { category: 'CACHE_WRITE', icon: '💾', color: C.blue, label: 'CACHE WRITE' };

    // Preload
    if (/preload (CALLED|STARTED|COMPLETED|FAST-READY)|PARALLEL PRELOAD completed/i.test(msg))
        return { category: 'PRELOAD', icon: '▶', color: C.magenta, label: 'PRELOAD' };

    // isReadyFlow — log verbose muito frequente, candidato a remoção
    if (/isReadyFlow check/i.test(msg))
        return { category: 'READY_CHECK', icon: '~', color: C.gray, label: 'READY_CHECK' };

    // Cancelamentos
    if (/Cancell?ing|Cancelled/i.test(msg))
        return { category: 'CANCEL', icon: '✕', color: C.gray, label: 'CANCEL' };

    // Erros e falhas
    if (/FAILED|Fatal|EXCEÇÃO|Exception|Failed to|returned null|error extract/i.test(msg)) {
        stats.errors++;
        return { category: 'ERROR', icon: '✖', color: C.red, label: 'ERRO' };
    }

    return null;
}

// ─── Máquina de estado por segmento ──────────────────────────────────────────

function updateSeg(category, n, msg) {
    if (n === null) return;
    const s = segOf(n);

    switch (category) {
        case 'MISS':
            s.misses++;
            if (s.state === 'CACHED' || s.state === 'HIT') {
                printAnomaly(`Seg ${n}: MISS após já estar cacheado — cache inválido ou recriado`, n);
            }
            if (s.misses > 1 && s.state !== 'HIT') {
                printAnomaly(`Seg ${n}: MISS duplo (${s.misses}x) — segmento está sendo extraído mais de uma vez`, n);
            }
            s.state = 'MISS';
            break;

        case 'EXTRACT_START':
            s.extractions++;
            activeExtractions.add(n);
            if (s.extractions > 1) {
                printAnomaly(`Seg ${n}: extração iniciada ${s.extractions}x — possível race condition ou falta de guard`, n);
            }
            if (s.state === 'CACHED') {
                printAnomaly(`Seg ${n}: extração iniciada apesar de já estar em cache — bypass do guard`, n);
            }
            s.state = 'EXTRACTING';
            break;

        case 'EXTRACT_DONE':
            activeExtractions.delete(n);
            if (s.state !== 'EXTRACTING') {
                printAnomaly(`Seg ${n}: DONE sem EXTRACT_START correspondente (state=${s.state})`, n);
            }
            s.state = 'EXTRACTED';
            break;

        case 'CACHE_WRITE':
            s.writes++;
            if (s.writes > 1) {
                printAnomaly(`Seg ${n}: gravação em cache ${s.writes}x — possível escrita duplicada`, n);
            }
            s.state = 'CACHED';
            break;

        case 'HIT':
            s.hits++;
            if (s.state === 'EXTRACTING') {
                printAnomaly(`Seg ${n}: HIT enquanto extração estava ativa — race condition`, n);
            }
            s.state = 'HIT';
            break;
    }
}

// ─── Processamento de linha ───────────────────────────────────────────────────

/**
 * Logcat brief: "D/TAG(PID): mensagem"
 * Logcat time:  "MM-DD HH:MM:SS.mmm  PID  TID D/TAG: mensagem"
 */
function processLine(raw) {
    stats.totalLines++;

    // Extrai mensagem após o tag (ambos os formatos)
    const m = raw.match(/[VDIWEF]\/[^(:]+[^:]*:\s*(.*)/);
    if (!m) return;
    const msg = m[1].trim();
    if (!msg) return;

    const cls = classify(msg);
    if (!cls) return;

    // Contadores globais
    if (cls.category === 'HIT')           stats.cacheHits++;
    if (cls.category === 'MISS')          stats.cacheMisses++;
    if (cls.category === 'EXTRACT_START') stats.extractions++;
    if (cls.category === 'CACHE_WRITE')   stats.cacheWrites++;

    // Número do segmento (para rastreamento de estado)
    const n = segNum(msg);
    updateSeg(cls.category, n, msg);

    // Pipeline de filtragem — se suprimido, não imprime mas já atualizou estado acima
    const key = cls.category + ':' + normalize(msg);
    const semKey = semanticKey(msg);
    if (!shouldPrint(key, semKey, msg)) return;

    // Formata e imprime
    const segLabel = n !== null ? ` ${C.dim}seg=${n}${C.reset}` : '';
    const perf = cls.perfNote ? `  ${cls.perfNote}` : '';
    print(cls.color, cls.icon, cls.label, `${msg}${segLabel}${perf}`);
}

// ─── Resumo final ─────────────────────────────────────────────────────────────

function printSummary() {
    const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
    const sep = '─'.repeat(68);

    console.log('');
    console.log(`${C.bold}${sep}${C.reset}`);
    console.log(`${C.bold}  RESUMO — ${elapsed}s de sessão${C.reset}`);
    console.log(sep);
    console.log(`  Linhas brutas recebidas : ${stats.totalLines}`);
    console.log(`  ${C.white}Linhas impressas         : ${stats.printed}${C.reset}`);
    console.log(`  ${C.gray}Suprimidas (exato)       : ${stats.suppressedExact}${C.reset}`);
    console.log(`  ${C.gray}Suprimidas (twin/dup)    : ${stats.suppressedTwin}${C.reset}`);
    console.log(`  ${C.gray}Suprimidas (ruído)       : ${stats.suppressedNoise}${C.reset}`);
    console.log('');

    // Cache stats
    const total = stats.cacheHits + stats.cacheMisses;
    if (total > 0) {
        const rate = ((stats.cacheHits / total) * 100).toFixed(1);
        const hitColor = parseFloat(rate) >= 70 ? C.green : C.orange;
        console.log(`  ${hitColor}Cache hit rate : ${rate}% (${stats.cacheHits} hits / ${total} total)${C.reset}`);
    }
    console.log(`  ${C.yellow}Extrações      : ${stats.extractions}${C.reset}`);
    console.log(`  ${C.blue}Cache writes   : ${stats.cacheWrites}${C.reset}`);
    if (stats.slowFrames > 0)
        console.log(`  ${C.red}Frames lentos  : ${stats.slowFrames} (>${SLOW_FRAME_THRESHOLD_MS}ms)${C.reset}`);
    if (stats.errors > 0)
        console.log(`  ${C.red}Erros          : ${stats.errors}${C.reset}`);
    console.log('');

    // Anomalias
    const segsWithProblems = [...segs.entries()].filter(([, s]) => s.warnings.length > 0);

    if (anomalies.length > 0 || segsWithProblems.length > 0) {
        console.log(`${C.red}${C.bold}  ANOMALIAS DETECTADAS (${stats.anomalies}):${C.reset}`);
        const seen = new Set();
        for (const a of anomalies) {
            if (!seen.has(a)) { console.log(`  ${C.red}• ${a}${C.reset}`); seen.add(a); }
        }
        if (segsWithProblems.length > 0) {
            console.log('');
            console.log(`  ${C.red}Segmentos problemáticos:${C.reset}`);
            for (const [n, s] of segsWithProblems) {
                console.log(`    ${C.red}seg ${n}${C.reset}  misses=${s.misses} extractions=${s.extractions} writes=${s.writes} hits=${s.hits}`);
            }
        }
    } else {
        console.log(`  ${C.green}Nenhuma anomalia detectada.${C.reset}`);
    }

    // Sugestões de simplificação
    if (stats.suppressedTwin > 0) {
        console.log('');
        console.log(`${C.yellow}${C.bold}  SIMPLIFICAÇÕES SUGERIDAS:${C.reset}`);
        console.log(`  ${C.yellow}• Twin-logs: remover as chamadas Log.d() duplicadas — use só Timber.*${C.reset}`);
        console.log(`  ${C.yellow}  Arquivos: PreloadViewModel.kt, HomeScreen.kt, HomeViewModel.kt${C.reset}`);
    }
    if (stats.suppressedNoise > 0) {
        console.log(`  ${C.yellow}• Logs ruidosos: remover ou mover para Timber.v() com guard BuildConfig.DEBUG${C.reset}`);
        console.log(`  ${C.yellow}  Candidato principal: "isReadyFlow check" (PreloadViewModel.kt:59)${C.reset}`);
    }

    console.log(sep);
    console.log(`  ${C.gray}Logs completos em: ${LOG_FILE}${C.reset}`);
    console.log('');

    logStream.write(`\nRESUMO: ${JSON.stringify(stats, null, 2)}\nANOMALIAS:\n${anomalies.join('\n')}\n`);
    logStream.end();
}

// ─── Main ─────────────────────────────────────────────────────────────────────

function getPid() {
    return new Promise((resolve, reject) => {
        const p = spawn('adb', ['shell', 'pidof', 'com.chopcut']);
        let out = '';
        p.stdout.on('data', d => (out += d));
        p.on('close', () => {
            const pid = out.trim().split(/\s+/)[0];
            pid ? resolve(pid) : reject(new Error('ChopCut não está em execução'));
        });
        p.on('error', reject);
    });
}

async function main() {
    console.clear();
    console.log(`${C.bold}╔════════════════════════════════════════════════════════╗${C.reset}`);
    console.log(`${C.bold}║   STRIP MONITOR — extração · strips · cache            ║${C.reset}`);
    console.log(`${C.bold}║   Detecta redundâncias e gargalos de performance       ║${C.reset}`);
    console.log(`${C.bold}╚════════════════════════════════════════════════════════╝${C.reset}`);
    console.log(`  ${C.gray}Log: ${LOG_FILE}   |   Ctrl+C para encerrar${C.reset}`);
    console.log(`  ${C.green}✓ HIT${C.reset}  ${C.orange}✗ MISS${C.reset}  ${C.yellow}⚡ EXTRAÇÃO${C.reset}  ${C.cyan}✦ DONE${C.reset}  ${C.blue}💾 WRITE${C.reset}  ${C.magenta}▶ PRELOAD${C.reset}  ${C.red}⚠ ANOMALIA${C.reset}`);
    console.log('');

    let pid;
    try { pid = await getPid(); }
    catch (e) { console.error(`${C.red}✖ ${e.message}${C.reset}`); process.exit(1); }

    console.log(`${C.green}  ✓ ChopCut ativo  PID=${pid}${C.reset}`);
    console.log(`${'─'.repeat(60)}`);
    console.log('');

    await new Promise(r => spawn('adb', ['logcat', '-c']).on('close', r));

    // Monitora todos os tags relevantes + sistema de UI para detectar twins
    const logcat = spawn('adb', [
        'logcat', '-v', 'brief', '--pid', pid,
        '-s',
        'ThumbnailStrip:V',
        'ThumbnailAspectMonitor:V',
        'ThumbnailCacheManager:V',
        'ThumbnailViewModel:V',
        'ThumbnailExtractorBatch:V',
        'PreloadViewModel:V',
        'HomeScreen:V',
        'HomeViewModel:V',
        'TrimScreen:V',
    ]);

    logcat.stdout.on('data', chunk => {
        for (const line of chunk.toString().split('\n')) {
            if (line.trim()) processLine(line);
        }
    });

    logcat.stderr.on('data', d => {
        const s = d.toString().trim();
        if (s && !/^-+\s*beginning/.test(s))
            console.error(`${C.red}[adb] ${s}${C.reset}`);
    });

    logcat.on('close', () => { printSummary(); });

    process.on('SIGINT', () => {
        logcat.kill();
        printSummary();
        process.exit(0);
    });
}

main();
