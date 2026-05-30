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

- **Feature de Compactação implementada (session #16):** ao confirmar os cortes, o export abre um `ModalBottomSheet` (Material3, `colorScheme`) com 3 níveis — **Original** (lossless via `CopyPipeline`), **Média** (HD 1080p / 5 Mbps) e **Baixa** (SD 720p / 2,5 Mbps), os dois últimos re-encodando via `TransformerPipeline`. `CompressionLevel` ganhou `targetHeight`/`targetBitrateBps`/`isViable()`; `FormatUtils.estimateExportSize()` mostra tamanho estimado em tempo real e o badge "Indisponível" desabilita nível sem ganho real. Re-encode força **H.264** (`video/avc`) e **dimensões pares**, com bitrate clampado ao original. Roteamento por nível em `TimelineViewModel.exportCuts(level)`. Validado em device (Galaxy A15): Baixa de 16:9 → 3,25 MB, % real no overlay. Gotchas do re-encode em Memory [[compactacao-reencode-h264-pares]].

- **Export validado nos 3 aspect ratios (session #15):** teste manual em device (Galaxy A15) exportando vídeos reais de `Movies/ChopCut/teste/` (`16_9`, `9_16`, `1-1`) pelo Recortar, com 2 keep ranges (remove o meio → concat). `ffprobe` confirmou resolução/rotação idênticas à origem nos 3 (16:9 = 1920×1080; 9:16 = 1920×1080 rot90; 1:1 = 1440×1440 rot90), HEVC com csd válido, áudio AAC preservado, leitura íntegra de todos os pacotes. **Lossless preserva resolução** — só a duração muda. Gotchas de validação local (ffmpeg-free/VLC sem HEVC; player Android fiel = Just Player) em Memory [[baixar-resultado-export-ao-testar]].
- **Recortar end-to-end (exportação real):** `TimelineViewModel.exportCuts()` converte os marcadores (trechos a remover) via `RangeUtils.calculateKeepRanges` → `CopyPipeline.trim` (lossless) → `saveToGallery` (`${base}_chopcut_mmss.mp4`). UI: CONFIRMAR → "Exportar" → overlay "Recortando…" → dialog de sucesso (Compartilhar via FileProvider/`ACTION_SEND` + Concluir). Estado via `ExportUiState` (Idle/Exporting/Success/Error). Decisão de arquitetura: **edição não-destrutiva** (estado em memória; arquivo só na exportação) e **fluxos separados por ferramenta**. Validado em device (Galaxy A15) com ffprobe.
- **Correção crítica do `CopyPipeline` (csd):** o pipeline reconstruía o `MediaFormat` da track de vídeo sem o `csd-0`/`csd-1` → MP4 inválido (não abria em player, sem thumbnail). Agora usa `addTrack(videoFormat)` original + `setOrientationHint`, e `muxer.stop()` não engole mais exceção. → registrado em `O que não fazer.md` §7.
- **Extração de Componentes de Vídeo:** Os seletores horizontais enxutos `VideoPickerEmpty`, `VideoPickerLoading` e `VideoPickerLoaded` foram movidos com sucesso de `HomeFeature.kt` para `SharedComponents.kt`, organizando melhor os componentes compartilhados da aplicação e enxugando `HomeFeature.kt`.
- **Grade de Recursos e Ações (`HomeScreen`):** Adicionado cabeçalho "Ferramentas" e grid de 2 colunas com cards brutalistas de 130dp de altura para navegação rápida de recursos (Recortar, Mesclar, Compactar, Extrair Áudio) no `HomeFeature.kt`. Os recursos sem tela no momento disparam um Toast explicativo moderno.
- **Botão de Ação "Recortar":** No componente de vídeo carregado (`VideoPickerLoaded`), o botão principal original "Editar" (com ícone Play) foi substituído por "Recortar" (com o ícone de tesoura `ContentCut`) para condizer melhor com o fluxo de corte cirúrgico do aplicativo.
- **Componente de Seleção de Vídeo Redesenhado:** Reformulada a UI dos estados vazio, em carregamento e carregado (`HomeFeature.kt`). O layout foi compactado verticalmente de `280.dp` para `100.dp`/`130.dp` (redução de ~64% de altura) através de uma estrutura horizontal premium e de alta densidade de metadados, respeitando a estética brutalista.
- **Jank da timeline corrigido (`dba4784`) e VALIDADO em device real (Galaxy A15, Android 16):** Rect de bitmap reutilizado via `.set()`, labels de tick pré-medidos, cores fixas em `remember`. Medição `gfxinfo` com thumbnails reais (reprodução, 60Hz): **~0,15% janky, 0 missed vsync**. Caminho `drawBitmap`/`dstRect` confirmado fluido.
- **Teste de jank automatizado:** `gradle/scripts/run-jank-test.sh` faz o fluxo inteiro (adb Wi-Fi → install → navega a UI por texto/uiautomator → "Extrair Frames" → mede gfxinfo na reprodução → PASS/FAIL). A skill `/rodar-app` aponta para ele. Regressão de fluidez agora é checável por comando, não só por instrução visual.
- **Tooling de IA adicionado:** skills de projeto `/desafiar-plano`, `/revisar-canvas`, `/rodar-app` (em `.claude/skills/`, versionadas); allowlist read-only (CodeGraph + adb + build) em `.claude/settings.json`.
- **Modo Preview com Pulo de Intervalos:** Adicionado um toggle switch na timeline ("Modo Preview") que faz a reprodução e o scroll do vídeo pularem recursivamente os trechos cortados (marcados em amarelo).
- **Hiding Interactive Edits:** Elementos de edição (como os botões de excluir marcações da timeline) são ocultados automaticamente no Canvas quando o Modo Preview está ativo para uma experiência limpa de reprodução.
- **Indicação Visual da Feature Utilizada:** Implementação de uma bolinha/badge neon ciano (`0xFF00E5FF`) no canto superior direito do card "Recortar Vídeo" na HomeScreen para indicar que cortes foram aplicados/confirmados pelo usuário para aquele vídeo, integrando o `AppliedCutsRegistry` compartilhado.
- **Estrutura agora é auto-inventariada:** `gradle/scripts/scan-structure.sh` gera `docs/STRUCTURE.generated.md` e roda no pré-commit (`.githooks/`). Docs deixaram de manter a contagem de arquivos na mão (a antiga "16" estava defasada — real são 14, `graphics/` foi arquivado).
- **Protocolo de sessão consolidado (SSOT):** cada fato em um lugar; `SESSION_PROTOCOL.md` e `CLAUDE.md` apontam para a fonte em vez de repetir.

