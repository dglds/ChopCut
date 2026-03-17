# Implementation Plan: Performance Benchmark Suite

## Phase 1: Setup do Módulo Macrobenchmark
- [ ] Task: Criar o módulo `macrobenchmark` no projeto Gradle.
- [ ] Task: Configurar o `benchmark` build type em `app/build.gradle.kts`.
- [ ] Task: Escrever o primeiro teste básico de `StartupBenchmark` (Cold Start).
- [ ] Task: Executar o teste localmente e validar a geração do relatório em JSON no device.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Setup do Módulo Macrobenchmark' (Protocol in workflow.md)

## Phase 2: Benchmarks da Timeline e Tracing Customizado
- [ ] Task: Escrever teste `TimelineScrollBenchmark` para medir jank durante o scroll.
- [ ] Task: Adicionar marcações `android.os.Trace.beginSection` e `endSection` nos pontos críticos de extração de thumbnails (`FastFrameExtractor`, `ThumbnailStripManager`).
- [ ] Task: Executar os testes e capturar os traces do Perfetto contendo as seções customizadas.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Benchmarks da Timeline e Tracing Customizado' (Protocol in workflow.md)

## Phase 3: Integração com o Agente Performance Investigator (PKB)
- [ ] Task: Criar script Python `.agent/skills/perfetto-performance-investigator/scripts/parse_benchmark_results.py`.
- [ ] Task: Implementar no script a lógica para ler o JSON gerado pelo Macrobenchmark e extrair as métricas principais (p50, p90, p99).
- [ ] Task: Implementar no script a lógica para ler a estrutura da pasta `performance/` e criar/atualizar o arquivo `performance/performance_baseline.md`.
- [ ] Task: Implementar shell script `scripts/run_benchmarks_and_update_baseline.sh` que executa o gradle connectedCheck e chama o script Python.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Integração com o Agente Performance Investigator (PKB)' (Protocol in workflow.md)
