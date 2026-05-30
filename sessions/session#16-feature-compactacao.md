# Session #16 — Feature de Compactação (re-encode com nível de qualidade)

**Modelo:** Claude Opus 4.8  **Data:** 2026-05-29
**Objetivo:** Conectar a UI ao pipeline de re-encode e deixar o usuário escolher o nível de compressão no export (Original/Média/Baixa), com estimativa dinâmica de tamanho.

## O que mudou
- `CompressionLevel`: enum ganhou `targetHeight`/`targetBitrateBps` + `isViable()` (anti-upscale / anti-bloat) — define os 3 perfis e quando cada um faz sentido.
- `FormatUtils.estimateExportSize()`: estimativa pura de bytes a partir dos keep-ranges, bitrate alvo (+128 kbps de áudio) e clamp pela proporção do original.
- `VideoEngine.TransformerPipeline.trim`: bitrate e resolução **dinâmicos** por `CompressionLevel` (antes hardcoded), downscale com dimensões pares e `setVideoMimeType("video/avc")`.
- `TimelineFeature.kt`: `exportCuts(level)` roteia ORIGINAL→`CopyPipeline` / Média·Baixa→`TransformerPipeline`; `ExportUiState.Exporting` carrega `progress`; a confirmação trocou o `AlertDialog` por um `ModalBottomSheet` com cards de nível, badge de viabilidade e estimativa em tempo real.

## Decisões / lições
- Re-encode **força H.264 + dimensões pares + clamp de bitrate** — sem isso crasha o encoder, gera HEVC que não abre fora do Android, ou infla o arquivo. → Memory [[compactacao-reencode-h264-pares]] (liga em [[baixar-resultado-export-ao-testar]]).
- Compactar **não virou tela própria** (como o backlog da #15 supunha): mora dentro do fluxo de export do Recortar, conforme o plano consolidado (`docs/plano-feature-compactacao.md`). → STATE atualizado.
- O `TransformerPipeline` emite **% real** de progresso (overlay "Processando (N%)…"); o `CopyPipeline` segue indeterminado.

## Backlog (delta)
- Fechado: [Compactar — export com níveis de qualidade].  ·  Novo/aberto: [Mesclar e Extrair Áudio seguem pendentes; progresso real ainda falta só no lossless].  → refletido no STATE.md
