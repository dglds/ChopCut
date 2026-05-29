# ChopCut — Estado Atual

> **Único ponto de leitura de estado.** A IA lê este arquivo no início da sessão e o atualiza no fim. Histórico narrativo fica em `sessions/`; regras na [Regras da Arquitetura](docs/ChopCut%20-%20Regras%20da%20Arquitetura.md); inventário em `docs/STRUCTURE.generated.md`.

**Última atualização:** 2026-05-29

---

## 📋 Backlog (aberto)

- [ ] Corrigir os 3 warnings restantes de depreciação no build (detecção de cores e componentes UI)
- [ ] Validar o fluxo de corte no `TimelineScreen` com vídeos de aspect ratio vertical e horizontal

## ⚠️ Known issues / cuidados

- `TimelineFeature.kt` concentra a timeline inteira (Canvas de thumbnails + Canvas de playhead isolado). Mudanças aqui exigem validação visual (`/rodar-app`) — jank não tem teste automatizado.
- Sem emulador no SDK padrão (`~/Android/Sdk/emulator` ausente). Para rodar: Galaxy A15 (`SM-A156M`) já pareado via adb Wi-Fi — descobrir com `adb mdns services` e conectar no IP:porta do serviço `_adb-tls-connect._tcp` (ex.: `adb connect 192.168.1.10:PORTA`). Frames só são extraídos pelo fluxo Home → "Escolher Vídeo" → "Extrair Frames" (deep-link `ACTION_VIEW` pula essa etapa e mostra placeholders).

## 🧭 Decisões recentes

- **Jank da timeline corrigido (`dba4784`) e VALIDADO em device real (Galaxy A15, Android 16):** Rect de bitmap reutilizado via `.set()`, labels de tick pré-medidos, cores fixas em `remember`. Medição `gfxinfo` com thumbnails reais — reprodução (60Hz): **0,13% janky, 0 missed vsync**; scroll: **0,00% janky, 0 slow UI thread**. Caminho `drawBitmap`/`dstRect` confirmado fluido.

- **Tooling de IA adicionado:** skills de projeto `/desafiar-plano`, `/revisar-canvas`, `/rodar-app` (em `.claude/skills/`, versionadas); allowlist read-only (CodeGraph + adb + build) em `.claude/settings.json`.
- **Estrutura agora é auto-inventariada:** `gradle/scripts/scan-structure.sh` gera `docs/STRUCTURE.generated.md` e roda no pré-commit (`.githooks/`). Docs deixaram de manter a contagem de arquivos na mão (a antiga "16" estava defasada — real são 14, `graphics/` foi arquivado).
- **Protocolo de sessão consolidado (SSOT):** cada fato em um lugar; `SESSION_PROTOCOL.md` e `CLAUDE.md` apontam para a fonte em vez de repetir.
