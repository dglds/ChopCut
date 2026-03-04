# Monitor ChopCut - Tela + Arquivo

## monitor.js

Monitor de atividade que mostra logs **NA TELA** e salva **EM ARQUIVO** simultaneamente.

### Uso Básico
```bash
node monitor.js
```
- Verbosidade: Média (padrão)
- Arquivo: chopcut_monitor.log

### Com Verbosidade
```bash
node monitor.js --1   # Alta - Críticos
node monitor.js --2   # Média - Importantes
node monitor.js --3   # Baixa - Todos
```

### Com Arquivo Personalizado
```bash
node monitor.js --2 --arquivo=debug.log
node monitor.js --3 --arquivo=logs/thumbnails.log
```

## Características

### Tela
- **Cabeçalho Fixo** (topo)
  ```
  ╔══════════════════════════════════════════════════════╗
  ║       MONITOR CHOPCUT - TELA + ARQUIVO               ║
  ╚══════════════════════════════════════════════════════╝
  
  Verbosidade: [2] MÉDIA - Importantes
  Arquivo: chopcut_monitor.log
  
  Status: ⚡ EXTRAÇÃO | 💾 CACHE
  ```

- **Indicadores Dinâmicos**
  - ⚡ **EXTRAÇÃO** (Amarelo) - Extraindo frames
  - 🖼️ **MONTAGEM** (Ciano) - Montando strips
  - 💾 **CACHE** (Verde) - Operações de cache
  - ⏳ **AGUARDANDO** (Amarelo) - Inativo (após 2s)

- **Logs Coloridos**
  ```
  03-04 13:44:30.997 ⚡ extractSegment STARTED
  03-04 13:44:31.000 🖼️  Segment 0 (10 frames, 1680x168, RGB_565)
  03-04 13:44:31.073 💾 Cache PUT: content://media/external/video/...
  ```

### Arquivo
- **Formato:** Texto plano (sem cores)
- **Estrutura:**
  ```
  ═══ MONITOR CHOPCUT ═══
  Iniciado: 2026-03-04T13:44:30.000Z
  Verbosidade: --2
  ═══ LOGS ═══
  
  03-04 13:44:30.997 [EXTRAÇÃO] extractSegment STARTED
  03-04 13:44:31.000 [MONTAGEM] Segment 0 (10 frames, 1680x168, RGB_565)
  03-04 13:44:31.073 [CACHE] Cache PUT: content://media/external/video/...
  
  ═══ ENCERRADO ═══
  Finalizado: 2026-03-04T13:45:30.000Z
  ```

- **Tipos de Logs:**
  - [EXTRAÇÃO] - Extração de frames
  - [MONTAGEM] - Montagem de strips
  - [CACHE] - Operações de cache
  - [INFO] - Outras informações
  - [ERRO] - Erros e falhas

## Níveis de Verbosidade

| Nível | Parâmetro | Descrição | Logs |
|-------|-----------|-----------|------|
| Alta | --1 | Apenas críticos | Cache HIT/MISS, Segmentos completos, Erros |
| Média | --2 | Importantes (PADRÃO) | Extração, Strips, Cache |
| Baixa | --3 | Debug completo | Todos os logs com detalhes |

## Encerramento

- **Ctrl+C:** Encerra monitoramento
  - Salva rodapé no arquivo (data/hora final)
  - Mostra caminho do arquivo

## Exemplos de Uso

### Debug Completo
```bash
node monitor.js --3 --arquivo=debug_completo.log
```

### Diagnóstico Rápido
```bash
node monitor.js --1 --arquivo=erros.log
```

### Monitoramento Normal
```bash
node monitor.js --2 --arquivo=thumbnails.log
```

## Comparação

| Feature | monitor.sh | monitor.js |
|---------|------------|------------|
| Cores na tela | ✅ | ✅ |
| Indicadores fixos | ❌ | ✅ |
| Salva em arquivo | ❌ | ✅ |
| Verbosidade | ❌ | ✅ |
| Auto-encerra | ❌ | ✅ |
| Ctrl+C | ✅ | ✅ |
