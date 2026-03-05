# ADB Monitor Framework - Guia de Uso

## 📱 Visão Geral

Framework completo para monitorar logs do Android em tempo real usando ADB e o sistema de parsing do ChopCut.

## 🔧 Classes Principais

### ADBCommand

Classe utilitária para executar comandos ADB.

```typescript
import { ADBCommand } from './src/ADBMonitor';

// Verificar se ADB está disponível
const available = await ADBCommand.isAvailable();
console.log(`ADB Available: ${available}`);

// Listar dispositivos conectados
const devices = await ADBCommand.getDevices();
devices.forEach(device => {
  console.log(`Device: ${device.id}`);
  console.log(`Model: ${device.model}`);
  console.log(`API Level: ${device.apiLevel}`);
});

// Obter PID de um pacote
const pid = await ADBCommand.getPackagePid('com.chopcut.app');
console.log(`PID: ${pid}`);

// Limpar buffer do logcat
await ADBCommand.clearLogcat();
console.log('Buffer cleared');

// Obter propriedades do sistema
const apiLevel = await ADBCommand.getAPILevel();
console.log(`Android API: ${apiLevel}`);

const version = await ADBCommand.getProperty('ro.build.version.release');
console.log(`Android Version: ${version}`);
```

### ADBMonitor

Monitor de logs com callbacks para processamento.

```typescript
import { ADBMonitor } from './src/ADBMonitor';

const monitor = new ADBMonitor({
  // Tag para filtrar
  tag: 'ChopCut',
  
  // Nível de log (V=Verbose, D=Debug, I=Info, W=Warn, E=Error)
  level: 'D',
  
  // Filtrar por pacote
  package: 'com.chopcut.app',
  
  // Filtrar por PID
  pid: '12345',
  
  // Buffer em KB
  buffer: 200,
  
  // Limpar buffer antes de começar
  clearBuffer: true,
  
  // Filtro regex
  regex: 'GLRenderer|TimelineEditor',
  
  // Formato de saída
  format: 'time',
  
  // Callback para cada linha
  onLogLine: (line) => {
    console.log(line);
    // Processar linha, enviar para servidor, etc.
  },
  
  // Callback de erro
  onError: (error) => {
    console.error('Monitor error:', error);
  },
  
  // Callback quando fecha
  onClose: (code) => {
    console.log(`Monitor closed with code: ${code}`);
  }
});

// Iniciar monitoramento
await monitor.start();

// Verificar se está rodando
if (monitor.isRunningMonitor()) {
  console.log('Monitor is running');
}

// Obter estatísticas
const stats = monitor.getStats();
console.log(`Lines: ${stats.linesProcessed}`);
console.log(`Errors: ${stats.errorsCaptured}`);
console.log(`Uptime: ${stats.uptime}s`);
console.log(`Rate: ${stats.linesPerSecond} lines/s`);

// Parar monitoramento
monitor.stop();
```

### RealtimeLogMonitor

Monitor em tempo real com atualização de estado e salvamento.

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

const monitor = new RealtimeLogMonitor({
  // Salvar logs em arquivo
  saveToFile: true,
  
  // Arquivo de saída
  outputFile: 'my-logs.txt',
  
  // Atualizar estado da aplicação
  updateState: true,
  
  // Intervalo de estatísticas (segundos)
  statsInterval: 5,
  
  // Filtro customizado
  filterLogs: (line) => {
    return line.includes('GLRenderer') || line.includes('TimelineEditor');
  },
  
  // Callback para cada linha
  onLogLine: (line) => {
    if (line.includes('ERROR')) {
      // Enviar alerta
      sendAlert(line);
    }
  }
});

// Iniciar
await monitor.start();

// Obter estado atual
const state = monitor.getState();
console.log('GLRenderer initialized:', state.graphics.glRenderer.isInitialized);
console.log('Selected video:', state.bottomSheetGallery.selectedVideo);
console.log('Scroll velocity:', state.timelineEditor.scrollVelocity);
console.log('Preload status:', state.preload.currentState);

// Obter logs capturados
const logs = monitor.getLogs();
console.log(`Total logs: ${logs.length}`);

// Parar
monitor.stop();
```

## 🚀 Scripts de Exemplo

### Monitor Completo

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

async function monitorAll() {
  const monitor = new RealtimeLogMonitor({
    tag: 'ChopCut',
    level: 'D',
    saveToFile: true,
    outputFile: 'chopcut-logs.txt',
    updateState: true,
    statsInterval: 5
  });

  await monitor.start();
}

monitorAll().catch(console.error);
```

### Monitor de Erros

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

async function monitorErrors() {
  const monitor = new RealtimeLogMonitor({
    tag: 'ChopCut',
    level: 'E',
    saveToFile: true,
    outputFile: 'errors.txt',
    updateState: false,
    statsInterval: 10,
    filterLogs: (line) => {
      return line.includes('ERROR') || 
             line.includes('Exception') ||
             line.includes('Failed');
    }
  });

  await monitor.start();
}

monitorErrors().catch(console.error);
```

### Monitor de Gráficos

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

async function monitorGraphics() {
  const monitor = new RealtimeLogMonitor({
    saveToFile: true,
    outputFile: 'graphics.txt',
    updateState: true,
    statsInterval: 3,
    filterLogs: (line) => {
      return line.includes('GLRenderer') ||
             line.includes('SurfaceBridge') ||
             line.includes('OpenGL');
    },
    onLogLine: (line) => {
      if (line.includes('error')) {
        console.log(`❌ ${line}`);
      } else {
        console.log(`🎨 ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorGraphics().catch(console.error);
```

### Monitor com Alertas

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

// Função simulada para enviar alerta
function sendAlert(message: string) {
  // Integrar com Slack, Discord, Email, etc.
  console.log(`🚨 ALERT: ${message}`);
}

async function monitorWithAlerts() {
  const monitor = new RealtimeLogMonitor({
    tag: 'ChopCut',
    level: 'D',
    saveToFile: true,
    outputFile: 'alerts.txt',
    updateState: true,
    statsInterval: 5,
    onLogLine: (line) => {
      if (line.includes('CRITICAL')) {
        sendAlert(`CRITICAL: ${line}`);
      } else if (line.includes('ERROR')) {
        sendAlert(`ERROR: ${line}`);
      }
    }
  });

  await monitor.start();
}

monitorWithAlerts().catch(console.error);
```

### Monitor com Contador

```typescript
import { RealtimeLogMonitor } from './src/RealtimeMonitor';

async function monitorWithCounters() {
  let errorCount = 0;
  let successCount = 0;

  const monitor = new RealtimeLogMonitor({
    tag: 'ChopCut',
    level: 'D',
    updateState: true,
    statsInterval: 5,
    onLogLine: (line) => {
      if (line.includes('ERROR')) {
        errorCount++;
        console.log(`Errors: ${errorCount}`);
      } else if (line.includes('initialized') || line.includes('ready=true')) {
        successCount++;
        console.log(`Successes: ${successCount}`);
      }
    }
  });

  await monitor.start();
}

monitorWithCounters().catch(console.error);
```

## 🔍 Filtros Avançados

### Por Múltiplas Tags

```typescript
filterLogs: (line) => {
  const tags = ['GLRenderer', 'TimelineEditor', 'PreloadViewModel'];
  return tags.some(tag => line.includes(tag));
}
```

### Por Expressão Regular

```typescript
filterLogs: (line) => {
  return /Error|Exception|Failed/i.test(line);
}
```

### Por Nível de Log

```typescript
filterLogs: (line) => {
  return line.includes('E/') || line.includes('D/');
}
```

### Combinando Filtros

```typescript
filterLogs: (line) => {
  const hasTag = line.includes('GLRenderer');
  const hasError = line.includes('ERROR');
  return hasTag && hasError;
}
```

## 📊 Exportar Dados

### Para JSON

```typescript
const state = monitor.getState();
await Bun.write('state.json', JSON.stringify(state, null, 2));
```

### Para CSV

```typescript
const logs = monitor.getLogs();
const csv = logs.map(l => l).join('\n');
await Bun.write('logs.csv', csv);
```

### Para Database

```typescript
const logs = monitor.getLogs();
for (const log of logs) {
  await db.insert('logs', {
    message: log,
    timestamp: new Date(),
    processed: true
  });
}
```

## 🎯 Casos de Uso

### 1. Debug de Crashes

```typescript
filterLogs: (line) => {
  return line.includes('FATAL') || line.includes('CRASH');
}
```

### 2. Performance Monitoring

```typescript
onLogLine: (line) => {
  if (line.includes('frame time')) {
    const frameTime = parseInt(line.match(/(\d+)ms/)?.[1] || '0');
    if (frameTime > 16) {
      console.log(`⚠️ Frame drop: ${frameTime}ms`);
    }
  }
}
```

### 3. Memory Leaks

```typescript
filterLogs: (line) => {
  return line.includes('GC') || line.includes('OOM');
}
```

### 4. State Reconciliation

```typescript
const monitor = new RealtimeLogMonitor({
  updateState: true,
  statsInterval: 10,
  onLogLine: (line) => {
    // Comparar estado atual com estado esperado
    const currentState = monitor.getState();
    if (!currentState.graphics.glRenderer.isInitialized) {
      console.log('⚠️ GLRenderer not initialized');
    }
  }
});
```

## 💡 Dicas

1. **Use `statsInterval` para relatórios periódicos** (5-10 segundos)
2. **Filtre linhas antes de processar** para melhor performance
3. **Use `updateState: false`** se não precisa do estado da aplicação
4. **Salve em arquivo** para análise posterior
5. **Combine múltiplos monitores** para diferentes aspectos

## 🔧 Solução de Problemas

### Dispositivo Não Encontrado

```typescript
const devices = await ADBCommand.getDevices();
if (devices.length === 0) {
  console.log('Nenhum dispositivo conectado');
  console.log('Ative o USB Debugging no dispositivo');
}
```

### Permissão Negada

```typescript
try {
  await monitor.start();
} catch (error) {
  if (error.message.includes('unauthorized')) {
    console.log('Autorize o dispositivo no celular');
  }
}
```

### Buffer Cheio

```typescript
const monitor = new RealtimeLogMonitor({
  buffer: 500, // Aumentar buffer
  clearBuffer: true // Limpar sempre
});
```

## 📚 Referência

- [ADB Documentation](https://developer.android.com/studio/command-line/logcat)
- [Bun Documentation](https://bun.sh)
- [TypeScript Documentation](https://www.typescriptlang.org/docs/)
