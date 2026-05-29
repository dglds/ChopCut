# Session #11 — Tooling de IA, consolidação SSOT e validação de jank em device real

**Modelo:** Claude (Opus 4.8)
**Data:** 2026-05-29
**Objetivo:** Recomendar e criar skills úteis para o projeto; consolidar a documentação para evitar drift; corrigir e validar o jank da timeline.

## O que foi feito

- **Skills de projeto criadas/adaptadas** (`.claude/skills/`, versionadas):
  - `/revisar-canvas` — auditor dos 3 padrões de performance de Canvas, calibrado com o código real.
  - `/rodar-app` — fluxo de build/install/launch + screenshot/logcat (a `/run` built-in delega para ela).
  - `/desafiar-plano` — substitui a antiga `interrogar-com-docs` (DDD) por interrogatório contra as regras reais do ChopCut.
- **Allowlist read-only** (CodeGraph + adb + build) em `.claude/settings.json`; `.gitignore` ajustado para versionar `skills/`, `settings.json` e `.claude/CLAUDE.md`.
- **Auto-inventário de estrutura:** `gradle/scripts/scan-structure.sh` gera `docs/STRUCTURE.generated.md`; roda no pré-commit (`.githooks/`). Acabou a contagem manual de arquivos.
- **Consolidação SSOT:** `SESSION_PROTOCOL.md` reescrito para delegar; `STATE.md` criado como estado vivo; corrigido drift real nas Regras e nos `CLAUDE.md` (Key Files apontava para arquivos inexistentes; "16 arquivos" → 14, `graphics/` arquivado).
- **Jank da timeline corrigido** em `TimelineFeature.kt` (achados do `/revisar-canvas`): `dstRect` reutilizado, labels pré-medidos, cores em `remember`; anotação `@violation` obsoleta removida.
- **Validado em device real** (Galaxy A15 via adb Wi-Fi) e **automatizado** em `gradle/scripts/run-jank-test.sh`.

## Decisões (e por quê)

- **Estrutura gerada por script, não mantida à mão** — a manutenção manual já tinha falhado (docs diziam "16", real era 14). Gerar a partir do código elimina a classe de erro. Salvo procedimento de device real em Memory como [[rodar-app-device-real]].
- **Teste de jank mede na reprodução, não no scroll** — `input swipe` sintético infla o jank (~7%); a reprodução é o caminho de 60Hz limpo e representativo (~0,15%).

## Delta do backlog

- Fechado: aplicar e validar os achados de jank do `/revisar-canvas`.
- Novo: nada crítico. Backlog vivo no `STATE.md` (warnings de depreciação, validar corte com AR vertical/horizontal).

## Resultado da validação (gfxinfo, Galaxy A15, thumbnails reais)

| Caminho | Janky | Missed Vsync |
|---|---|---|
| Reprodução (60Hz) | ~0,15% | 0 |
| Scroll (input sintético) | ~7% (ruído de injeção) | — |
