# Session #15 — Validar export do Recortar em 3 aspect ratios

**Modelo:** Claude Opus 4.8  **Data:** 2026-05-29
**Objetivo:** Validar em device real que o export do Recortar preserva resolução/rotação em horizontal (16:9), vertical (9:16) e quadrado (1:1) — fechando o item de backlog do export horizontal.

## O que mudou
- Nenhuma mudança de código de app — sessão de **validação**. Docs: `STATE.md` (item fechado + known issue novo) e Memory [[baixar-resultado-export-ao-testar]] (gotchas de playback HEVC local + player Android fiel).
- Teste manual via UI no Galaxy A15: exportados 3 vídeos reais de `Movies/ChopCut/teste/` pelo Recortar (2 keep ranges, remove o meio → concat lossless).

## Decisões / lições
- **Lossless preserva resolução/rotação nas 3 orientações** (ffprobe): 16:9 = 1920×1080; 9:16 = 1920×1080 rot90; 1:1 = 1440×1440 rot90. HEVC com csd válido, áudio AAC ok, todos os pacotes lidos sem erro de container. Só a duração muda. → confirma que o fix do `CopyPipeline` (csd, session #14) segura além do vertical.
- **Validação local de HEVC tem armadilha:** o `ffmpeg-free` do Fedora e o VLC desktop **não decodam HEVC** (`no decoder found` / `Codec not supported`) — não é defeito do arquivo. Usar `ffprobe -count_packets` p/ integridade sem decoder; RPM Fusion (`dnf swap ffmpeg-free ffmpeg`) ou VLC Flatpak p/ decodar; no device, **Just Player** (Media3/ExoPlayer) ou Galeria respeitam rotação (VLC não). → salvo em [[baixar-resultado-export-ao-testar]].
- **Achado lateral:** `FastFrameExtractorTest.kt` é órfão (classe `FastFrameExtractor` deletada em `fc4e50d`) e quebra a compilação de todo o `androidTest` → `make test` não roda. Restaurado sem alterar (fora do escopo); registrado como known issue no STATE.

## Backlog (delta)
- Fechado: [validar export horizontal] → na verdade validados os 3 aspect ratios.  ·  Novo: [decidir destino do `FastFrameExtractorTest.kt` órfão que quebra `make test`].  → refletido no STATE.md
