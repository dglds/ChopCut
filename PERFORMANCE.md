# Performance Architecture Decisions (ADR) - ChopCut

Este documento registra as decisões de arquitetura tomadas para otimizar o throughput de processamento de vídeo e thumbnails.

## 1. Controle de Concorrência (Workers)

### Decisão
Utilizar um `Semaphore` global para limitar o número de threads simultâneas de extração.

### Racional
O `MediaMetadataRetriever` do Android é intensivo em CPU e recursos de hardware. Abrir threads ilimitadas causa contenção de recursos e pode travar a UI (Main Thread). O uso de semáforo permite um controle fino sobre quantos decoders estão ativos.

### Configuração Atual (`CPU - 1`)
- **Regra**: `max(1, availableProcessors - 1)`
- **Objetivo**: Garantir que pelo menos um núcleo de CPU esteja livre para processar eventos de UI e manter a fluidez do scroll na timeline.
- **Limite**: Cap de 8 threads para evitar exaustão de memória em dispositivos high-end.

## 2. Telemetria de Pipeline

### Decisão
Implementar telemetria baseada em eventos (`PerformanceEvent`) capturados em pontos críticos (`DECODE`, `PROCESS`, `SAVE`).

### Racional
Sem métricas, qualquer otimização é "adivinhação". Dividir o pipeline em estágios permite identificar se o gargalo é I/O (SAVE), CPU (PROCESS) ou Decoder de Hardware (DECODE).

### Estrutura de Dados
- **Timestamp**: Instante do evento.
- **Stage**: DECODE, PROCESS, SAVE.
- **Duration**: Tempo gasto no estágio.
- **QueueSize**: Quantos itens restam no lote (para detectar saturação).

## 3. Persistência de Métricas

### Decisão
Exportação dupla: Logcat estruturado (`TPUT_LOG`) para depuração em tempo real e CSV local para análise histórica.

### Racional
CSV é universal e permite que o desenvolvedor baixe o arquivo via Device File Explorer e analise em Python/Excel sem precisar de uma infraestrutura de backend complexa durante a fase de desenvolvimento.

---

## Próximos Passos (Backlog)
- [ ] Implementar ajuste dinâmico de semáforos baseado em `QueueSize`.
- [ ] Integrar métricas agregadas diretamente no DuckDB centralizado do projeto.
