# ChopCut — Estado Atual

> **Único ponto de leitura de estado.** A IA lê este arquivo no início da sessão e o atualiza no fim. Histórico narrativo fica em `sessions/`; regras na [Regras da Arquitetura](docs/ChopCut%20-%20Regras%20da%20Arquitetura.md); inventário em `docs/STRUCTURE.generated.md`.

**Última atualização:** 2026-05-29 (session #16)

---

## 📋 Backlog (aberto)

- [ ] Corrigir os 3 warnings restantes de depreciação no build (detecção de cores e componentes UI)
- [ ] Decidir o destino do `FastFrameExtractorTest.kt` órfão (apagar ou portar p/ `ThumbnailExtraction`) — hoje quebra `make test` / `connectedAndroidTest`
- [x] ~~Compactar~~ → implementado **dentro do fluxo de export do Recortar** (ModalBottomSheet, níveis Original/Média/Baixa), não como tela própria — `TransformerPipeline` + `CompressionLevel` (session #16)
- [ ] Implementar export das demais ferramentas reusando o padrão de `exportCuts`: Mesclar (`concat`), Extrair Áudio — ver follow-up do plano
- [ ] (opcional) Progresso real na exportação **lossless** — `CopyPipeline.trim` não emite incremental (overlay indeterminado). O re-encode (Compactar via `TransformerPipeline`) **já emite % real** (overlay "Processando (N%)…")
- [x] ~~Validar o fluxo de export (Recortar) com aspect ratio horizontal~~ → validado em device nos **3** aspect ratios (16:9, 9:16, 1:1), resolução/rotação preservadas (session #15)

## ⚠️ Known issues / cuidados

- **`make test` / `connectedAndroidTest` não compila:** `app/src/androidTest/.../FastFrameExtractorTest.kt` referencia `FastFrameExtractor`, classe deletada no commit `fc4e50d`. O source set `androidTest` inteiro falha a compilação. `CLAUDE.md` e `.claude/CLAUDE.md` ainda citam esse teste como exemplo (exemplo morto).

- `TimelineFeature.kt` concentra a timeline inteira (Canvas de thumbnails + Canvas de playhead isolado). Mudanças aqui exigem validação visual (`/rodar-app`) — jank não tem teste automatizado.
- `CopyPipeline` corta por keyframe (`SEEK_TO_PREVIOUS_SYNC`): o início de cada trecho pode incluir alguns frames antes do ponto marcado. Trade-off lossless aceito; precisão fina seria via Transformer (re-encode).
- Vídeos com flag de rotação (9:16 = 1920×1080 + rotation −90) aparecem deitados em players que **ignoram o flag** (ex.: VLC); na galeria/Fotos e no editor aparecem em pé. Não é defeito do recorte — o export é lossless, idêntico ao original.
- Sem emulador no SDK padrão (`~/Android/Sdk/emulator` ausente). Para rodar: Galaxy A15 (`SM-A156M`) já pareado via adb Wi-Fi — descobrir com `adb mdns services` e conectar no IP:porta do serviço `_adb-tls-connect._tcp` (ex.: `adb connect 192.168.1.10:PORTA`). Frames só são extraídos pelo fluxo Home → "Escolher Vídeo" → "Extrair Frames" (deep-link `ACTION_VIEW` pula essa etapa e mostra placeholders).

## 🧭 Decisões recentes

> Foto das **últimas 1–2 sessões**. Decisão durável vive na Memory (carrega sozinha no boot); o histórico completo fica nas notas de `sessions/`.

- **Compactação no export (session #16):** ao confirmar os cortes, o export abre um `ModalBottomSheet` com 3 níveis — **Original** (lossless via `CopyPipeline`), **Média** (1080p / 5 Mbps) e **Baixa** (720p / 2,5 Mbps), os dois últimos re-encodando via `TransformerPipeline`. Roteamento por nível em `TimelineViewModel.exportCuts(level)`; estimativa em tempo real via `FormatUtils.estimateExportSize()`. Validado em device. Gotchas do re-encode em Memory [[compactacao-reencode-h264-pares]].
- **Export validado nos 3 aspect ratios (session #15):** em device, 16:9 / 9:16 / 1:1 mantiveram resolução e rotação idênticas à origem (lossless preserva resolução; só a duração muda). Validação local em Memory [[baixar-resultado-export-ao-testar]].

> Decisões duráveis migradas para a Memory: arquitetura de edição não-destrutiva + fluxos separados por ferramenta [[edicao-nao-destrutiva-fluxos-separados]].

