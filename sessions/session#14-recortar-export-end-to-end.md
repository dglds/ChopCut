# Session #14 — Recortar end-to-end (exportação) + fix do CopyPipeline

**Modelo:** Claude Opus 4.8  **Data:** 2026-05-29
**Objetivo:** Fechar o fluxo de Recortar de ponta a ponta — marcar cortes → exportar arquivo real → salvar/compartilhar — e corrigir o `CopyPipeline`, que gerava MP4 inválido.

## O que mudou
- **Exportação real do Recortar (`TimelineFeature.kt`)**: `ExportUiState` (Idle/Exporting/Success/Error) + `exportCuts()` na VM — converte os `MarkerInterval` (trechos a remover) via `RangeUtils.calculateKeepRanges` → `CopyPipeline.trim` (lossless) → `saveToGallery`. UI: CONFIRMAR → "Exportar" → overlay "Recortando…" → dialog de sucesso (Compartilhar + Concluir) / dialog de erro.
- **Compartilhar via FileProvider**: `ACTION_SEND` usa `content://` gerado do temp file (`cacheDir/video_processing`, coberto pelo `cache-path`) — evita o `file://` do `saveToGallery`, que o share rejeita.
- **Nome com short timestamp**: saída `${base}_chopcut_mmss.mp4` (`SimpleDateFormat("mmss")`).
- **Fix crítico `CopyPipeline.trim` (`VideoEngine.kt`)**: `addTrack(videoFormat)` original (preserva `csd-0`/`csd-1`) em vez de reconstruir o `MediaFormat`; rotação via `setOrientationHint`; `muxer.stop()` sem `try/catch` vazio.

## Decisões / lições
- **Edição não-destrutiva + fluxos separados por ferramenta** (interrogatório `/desafiar-plano`): edição vive em memória, arquivo só na exportação; cada ferramenta (Recortar/Compactar/Mesclar/Extrair Áudio) terá sua tela. Recortar usa `CopyPipeline` (rápido/lossless); Compactar usará `TransformerPipeline`.
- **MediaMuxer sem csd → MP4 inválido** → `O que não fazer.md` §7. Confirmado por ffprobe: quebrado = `codec_name=unknown 0x0`; corrigido = `hevc 1920x1080`, `extradata_size=105`.
- **Validar export baixando o arquivo** (adb pull + ffprobe), não só pelo player → Memory [[baixar-resultado-export-ao-testar]]. VLC ignora o flag de rotação (mostra deitado); arquivo está correto.

## Backlog (delta)
- Fechado: [Recortar end-to-end com exportação real + correção do CopyPipeline]  ·  Novo: [export horizontal a validar; export das demais ferramentas; progresso incremental opcional]  → refletido no STATE.md
