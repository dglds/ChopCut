# Specification: Performance Benchmark Suite

## Overview
Implementar uma suíte de benchmark de performance automatizada e estruturada para o aplicativo Android, com foco em métricas de rendering, memória e CPU. Esta track visa estabelecer o baseline de performance da `TimelineV3` e outras áreas críticas usando o Perfetto e Macrobenchmark.

## Functional Requirements
1.  **Macrobenchmark Setup:**
    -   Configurar o módulo `macrobenchmark` no projeto Android.
    -   Criar testes de inicialização (Cold Start, Warm Start).
    -   Criar testes de scroll para a `Timeline` medindo frame timing (Jank).
2.  **Integração com Perfetto:**
    -   Garantir que os testes do Macrobenchmark gerem traces do Perfetto.
    -   Definir métricas customizadas via `TraceSection` no código fonte para medir etapas específicas de processamento de mídia (ex: extração de thumbnails).
3.  **Sistema de Armazenamento de Métricas (PKB):**
    -   Integrar com o agente `performance-investigator` para que os resultados das execuções de benchmark sejam salvos estruturadamente na `Performance Knowledge Base (PKB)` no sistema de arquivos local (`performance/metrics/`).
4.  **Relatórios de Baseline:**
    -   Gerar o arquivo `performance_baseline.md` inicial com os resultados da primeira execução estável na branch principal.

## Non-Functional Requirements
-   Os testes devem ser repetíveis e consistentes.
-   A execução do benchmark não deve interferir no build padrão de debug/release (deve usar um build type de `benchmark`).

## Acceptance Criteria
- [ ] Módulo `macrobenchmark` compila e executa com sucesso em um dispositivo físico.
- [ ] Teste de scroll da Timeline gera métricas de `frameDurationCpuMs`.
- [ ] Traces do Perfetto são salvos e podem ser abertos na UI web do Perfetto.
- [ ] Script Python criado para extrair as métricas geradas pelo benchmark e atualizar o `performance_baseline.md`.

## Out of Scope
-   Otimizações de código nesta track (o objetivo é apenas medir e estabelecer o baseline).
-   Integração com CI/CD remoto nesta fase (foco local primeiro).
