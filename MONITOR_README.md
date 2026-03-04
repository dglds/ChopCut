# Monitor ChopCut

## Uso

```bash
./monitor.sh [--1|--2|--3]
```

## Níveis de Verbosidade

### --1 ALTA - Apenas críticos
Diagnóstico rápido, ver problemas

Mostra:
- Cache HIT/MISS
- Segmentos completados
- Erros/Falhas

### --2 MÉDIA - Eventos importantes (PADRÃO)
Monitoramento normal, desenvolvimento

Mostra:
- Extração iniciada
- Segmentos em extração
- Strips carregadas
- Cache HIT/MISS
- Strips salvas em cache

### --3 BAIXA - Todos os logs
Debug completo, ver tudo

Mostra:
- Todos os logs acima
- Detalhes de progresso
- Frames individuais
- Informações de tempo

## Cores

As cores só funcionam quando executado diretamente no terminal (TTY).

```bash
# Cores ativadas
./monitor.sh --2

# Cores desativadas (redirecionado)
./monitor.sh --2 > arquivo.txt
```

## One-Liners

### Alta (críticos)
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(Cache HIT|Cache MISS|Segment.*COMPLETED|Erro|FAILED)"
```

### Média (importante)
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(extractSegment STARTED|Extracting segment|Segment.*COMPLETED|Strip.*loaded|Cache HIT|Cache MISS|Cached segment)"
```

### Baixa (todos)
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(extract|Cache|Strip|loaded|Batch)"
```

## Filtros por Contexto

### Extração
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(extractSegment|Extracting|Batch|extractBatch)"
```

### Montagem
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(Segment.*COMPLETED|Strip.*loaded|frames.*extraídos)"
```

### Cache
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep -iE "(Cache HIT|Cache MISS|Cached|Cache PUT)"
```
