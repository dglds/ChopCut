# 📊 Performance Profiling - Guia Rápido

## Resposta à Pergunta: Perfetto depende do Timber?

**NÃO!** Perfetto e Timber são ferramentas independentes:

| Ferramenta | Propósito | Nível |
|------------|----------|--------|
| **Perfetto** | Tracing do sistema (CPU, GPU, scheduler) | Kernel/Sistema |
| **Timber** | Logging do app | Aplicação |

Eles operam separadamente - capturam dados diferentes sem dependência entre si.

---

## Capturar Performance (Método Principal)

Execute UM comando e tudo acontece automaticamente:

```bash
./scripts/capture_performance.sh           # Modo infinito (Ctrl+C para parar)
./scripts/capture_performance.sh 10        # Captura por 10s
./scripts/capture_performance.sh 5         # Captura por 5s
./scripts/capture_performance.sh 15        # Captura por 15s
```

### O que acontece automaticamente:
1. ✅ Verifica dispositivo conectado
2. ✅ Verifica se Perfetto existe
3. ✅ **LIMPA o logcat** antes da captura (remove logs antigos)
4. ✅ Mostra contador de tempo em tempo real
5. ✅ Captura Perfetto (tracing de baixo nível do sistema)
6. ✅ Captura logs Timber (métricas do app)
7. ✅ Gera resumo das métricas principais
8. ✅ Salva tudo em `./logs/` (na raiz do projeto)

### Arquivos gerados:
```
logs/
├── perfetto_trace_<timestamp>.perfetto-trace  # Trace completo do sistema
├── app_logs_<timestamp>.txt                   # Logs detalhados do app
└── summary_<timestamp>.txt                       # Resumo das métricas principais
```

---

## Analisar Resultados

### 🎨 Perfetto (Performance do sistema)
1. Abra https://ui.perfetto.dev
2. Arraste o arquivo `.perfetto-trace`
3. Use o Track Manager (painel esquerdo) para visualizar:
   - **GPU frames** - Identificar jank (frames > 16.6ms)
   - **CPU usage** - Verificar carga por thread
   - **Input events** - Analisar latência de toque/scroll
   - **Custom trace sections** - FastFrameExtractor.getFrameAt, convertFrame

### 📝 Logs (Métricas do app)
Abra `app_logs_<timestamp>.txt` ou `summary_<timestamp>.txt`

**Principais métricas:**
- `FastFrameExtractor` - Extração de frames
- `VideoTimeline` - Cache LRU, evicção de frames
- `ThumbnailViewModel` - Carregamento de strips
- `ThumbnailPreload` - Metadados de pré-carregamento

---

## Referência Rápida

| Quer medir | Comando | Duração |
|------------|---------|---------|
| Workflow completo (Ctrl+C para parar) | `./scripts/capture_performance.sh` | Indeterminado |
| Uma ação específica (scroll, tap) | `./scripts/capture_performance.sh 5` | 5s |
| Timeline completa (scroll longo) | `./scripts/capture_performance.sh 15` | 15s |
| Análise rápida | `./scripts/capture_performance.sh 10` | 10s |

---

## Dicas

✅ Certifique-se de que o app está aberto e na tela de timeline/trim ANTES de começar
✅ Interaja com o app durante "CAPTURANDO!" no countdown
✅ No modo infinito, use Ctrl+C para parar a captura
✅ O script limpa o logcat automaticamente antes de capturar
✅ Resultados mais consistentes com bateria > 20%

---

## Troubleshooting

**Logs vazios ou incompletos:**
→ Certifique-se de que o app está aberto e ativo durante a captura
→ Certifique-se de estar na tela correta (timeline/trim)
→ O app precisa estar gerando logs nas tags específicas

**Erro: "Nenhum dispositivo Android conectado"**
→ Conecte um dispositivo via USB ou inicie um emulador

**Erro: "Perfetto não encontrado"**
→ Dispositivo não suporta Perfetto (é raro)

**Trace muito pequeno**
→ Aumente a duração com `./scripts/capture_performance.sh 15`

---

## Pasta de Saída

Todos os resultados são salvos em:
```
./logs/
├── perfetto_trace_*.perfetto-trace
├── app_logs_*.txt
└── summary_*.txt
```

Para limpar: `rm -rf logs/`

---

## Opção A: Perfetto Only (Timeline FPS/Jank)

### Uso
```bash
./perfetto/capture_perfetto_only.sh 10  # 10 segundos
./perfetto/capture_perfetto_only.sh    # Infinito (Ctrl+C para parar)
```

### O que captura
- GPU frames (jank detection)
- Input events (touch/scroll latency)
- Compositor threads
- Sistema de janelas
- Filtrado apenas pelo PID do app ChopCut

### Vantagens
- ✅ Verifica se app está rodando antes de capturar
- ✅ Filtra apenas pelo PID do app ChopCut
- ✅ Mais leve e focado em renderização
- ✅ Ideal para debug rápido de jank

### Arquivos gerados
```
perfetto/
└── timeline_trace_*.perfetto-trace
```

### Análise
Abra o arquivo em https://ui.perfetto.dev e use:
- GPU frames para identificar jank (frames > 16.6ms)
- Input events para analisar latência de touch/scroll
- Compositor threads para ver carga de renderização
