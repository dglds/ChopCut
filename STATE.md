# ChopCut — Estado Atual

> **Único ponto de leitura de estado.** A IA lê este arquivo no início da sessão e o atualiza no fim. Histórico narrativo fica em `sessions/`; regras na [Regras da Arquitetura](docs/ChopCut%20-%20Regras%20da%20Arquitetura.md); inventário em `docs/STRUCTURE.generated.md`.

**Última atualização:** 2026-05-29

---

## 📋 Backlog (aberto)

- [ ] Corrigir os 3 warnings restantes de depreciação no build (detecção de cores e componentes UI)
- [ ] Validar o fluxo de corte no `TimelineScreen` com vídeos de aspect ratio vertical e horizontal
- [ ] Testar em dispositivo mid-range (sem frames lentos na renderização das miniaturas da régua do Canvas)
- [ ] Aplicar os achados de jank do `/revisar-canvas` em `TimelineFeature.kt` (Rect alocado por thumbnail/frame ~linha 853; `Color.copy` e `Brush`/`textMeasurer` no draw scope)

## ⚠️ Known issues / cuidados

- `TimelineFeature.kt` concentra a timeline inteira (Canvas de thumbnails + Canvas de playhead isolado). Mudanças aqui exigem validação visual (`/rodar-app`) — jank não tem teste automatizado.
- Sem device/emulador configurado no SDK padrão (`~/Android/Sdk/emulator` ausente): rodar o app exige conectar device físico ou subir emulador à parte.

## 🧭 Decisões recentes

- **Tooling de IA adicionado:** skills de projeto `/desafiar-plano`, `/revisar-canvas`, `/rodar-app` (em `.claude/skills/`, versionadas); allowlist read-only (CodeGraph + adb + build) em `.claude/settings.json`.
- **Estrutura agora é auto-inventariada:** `gradle/scripts/scan-structure.sh` gera `docs/STRUCTURE.generated.md` e roda no pré-commit (`.githooks/`). Docs deixaram de manter a contagem de arquivos na mão (a antiga "16" estava defasada — real são 14, `graphics/` foi arquivado).
- **Protocolo de sessão consolidado (SSOT):** cada fato em um lugar; `SESSION_PROTOCOL.md` e `CLAUDE.md` apontam para a fonte em vez de repetir.
