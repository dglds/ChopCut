# Session #05 — Sistema de Extração de Thumbnails e Timeline com Bitmaps Reais

**Data:** 2026-05-28
**Objetivo:** Implementar extração direta de thumbnails no HomeFeature (sem ThumbnailEngine), exibir bitmaps reais na TimelineV2 com suporte a landscape, portrait e square, e documentar o comportamento do MediaMetadataRetriever com vídeos rotacionados.

---

## O que foi feito

### 1. Extração Direta no HomeFeature
- ThumbnailEngine.kt (3418 linhas) **não utilizado** — extração feita diretamente no `startExtraction` do HomeFeature com `MediaMetadataRetriever`.
- `ExtractionConfigBottomSheet` removido — extração inicia com defaults (sem config).
- `ThumbnailSettings` simplificado: removeu `aspectRatioPreset`, `scaleFactor`, `cropZoom`, manteve `sizePreset`, `scaleMode`, `format`, `quality`, `thumbsPerSecond`.
- `ThumbnailScaleMode.STRETCH` removido.

### 2. SizePreset (renomeado de DimensionPreset)
- `DimensionPreset` → `SizePreset` com typealias + getter `dimensionPreset` deprecated para backward compat no ThumbnailEngine.kt.
- Presets: `THUMBNAIL(36)`, `SMALL(45)`, `MEDIUM(60)`, `LARGE(90)`, `HD(120)`.
- `SizePreset.suggest(videoHeight)` — sugere preset baseado na altura do vídeo.
- **Default alterado para SMALL (45)** para extrair 80×45 — dimensão que bate com o tamanho de exibição na timeline.

### 3. AR Fixo 16:9 com Tratamento de Orientação
- `computeDimensions(videoAr)` sempre retorna um box 16:9 derivado do `baseHeight`.
- Portrait (AR < 1): retorna `(baseH, w)` — ex: (45, 80).
- Landscape (AR >= 1.1): retorna `(w, baseH)` — ex: (80, 45).
- Square (AR 0.9–1.1): retorna `(sqrt(w*baseH), sqrt(w*baseH))` — ex: (60, 60), preservando área de 80×45.

### 4. FILL vs FIT por Orientação
- **Landscape (AR >= 1.1):** `FILL` (center-crop) — preenche o box 16:9 cortando bordas.
- **Portrait (AR < 1):** `FIT` — não corta, mantém conteúdo vertical completo. Decisão após análise: center-crop 9:16→16:9 perderia ~68% do frame.
- **Square (AR ~1):** `FIT` — sem corte, dimensões quadradas 60×60.
- `computeExtractDimensions(videoAr)` retorna dimensões de extração ajustadas pelo scaleMode.

### 5. Correção de Rounding
- `.toInt()` trunca: `106.666 → 106`.
- Substituído por `.roundToInt()`: `106.666 → 107`.

### 6. TimelineV2 com Bitmaps Reais
- `TimelineV2ViewModel` carrega bitmaps do disco via `listFiles` + `BitmapFactory.decodeFile`.
- **Auto-detect** do diretório de frames extraídos: `extracted_frames/<nome_sanitizado>/`.
- Display dinâmico no Canvas:
  - Landscape: `80×45dp`
  - Portrait: `45×80dp`
  - Square: `60×60dp` (mesma área do landscape)
- Scroll linear invariante (largura fixa por vídeo).
- Fallback a gradients coloridos quando não há bitmaps.

### 7. Correção de Rotação para Vídeos 9:16
- **Problema:** Vídeos 9:16 são armazenados como landscape (1920×1080) com rotação 90°. `getScaledFrameAtTime` retorna o frame bruto sem aplicar rotação → thumbnail saía landscape.
- **Solução:** Detectar `videoInfo.rotation == 90 || 270`, trocar `extractW/extractH` antes da chamada ao retriever, e rotacionar o bitmap com `Matrix.postRotate` após extração.

### 8. Navegação Simplificada
- `ChopCutNavGraph`: rota `timelineV2?videoUri={videoUri}` sem parâmetros `outputDir` ou `videoAr`.
- `TimelineV2Screen` e `TimelineV2ViewModelFactory` sem `outputDir`/`videoAr` nos construtores.

---

## O que aprendemos

### Comportamento do MediaMetadataRetriever
| Aspecto | Comportamento |
|---------|--------------|
| `getScaledFrameAtTime(w, h)` | Mantém AR do source. Para source 1920×1080 pedindo 90×160, retorna 90×51 (ajusta altura ao width, mantendo 16:9) |
| Rotação | **Não aplica** rotação do metadado. Frame sai sempre na orientação de armazenamento |
| `OPTION_CLOSEST_SYNC` | Retorna o keyframe síncrono mais próximo, sem decodificar keyframes adicionais |

### VideoInfo vs Frame Real
- `VideoInfo` já corrige `width/height` com base na rotação (linhas 784–788 do VideoEngine.kt).
- `videoInfo.aspectRatio` é confiável para lógica de orientação.
- O frame bruto do `MediaMetadataRetriever` **não** tem essa correção.

### .toInt() vs roundToInt()
- `106.666.toInt()` = **106** (truncamento)
- `(106.666).roundToInt()` = **107** (arredondamento)
- Preferir `roundToInt()` em cálculos de dimensão.

### Square Video é um Caso Especial
- Não é landscape nem portrait — precisa de detecção explícita (AR 0.9–1.1).
- Tamanho ideal: preservar área dos outros modos. 80×45 = 3600 → √3600 = 60 → 60×60.

---

## Pontos de Atenção para Futuro Desenvolvimento

### Extração
- **Rotação sempre:** Qualquer extração com `MediaMetadataRetriever` precisa tratar `videoInfo.rotation`. Se ignorar, vídeos 9:16 viram landscape.
- **Orphan frames:** Se re-extrair com menos frames, arquivos `frame_00042.jpg` de extrações anteriores persistem. Considerar `outputDir.deleteRecursively()` antes de extrair.
- **Bitmap lifecycle:** Após rotacionar, `rawFrame` pode ser reciclado. `orientedFrame` substitui. Cuidado com duplo recycle.
- **Limite de 99999 frames:** Nomenclatura `frame_NNNNN.jpg` (5 dígitos). Vídeos muito longos com fps alto podem estourar.

### Timeline
- **Escala pixel → dp:** SMALL extrai 80×45px, timeline exibe 80×45dp. Em densidade 2x, display é 160×90px → bitmap parece soft. Se precisar de nitidez, subir preset ou aplicar scale factor.
- **Auto-detect frágil:** O diretório de frames é derivado do `fileName` sanitizado. Se a sanitização no ViewModel e no HomeFeature divergir, os frames não serão encontrados.
- **isSquare threshold `<= 1.1f`:** Vídeos ultra-wide (AR 2.39:1) são landscape. ARs entre 1.0 e 1.1 são square. Ajustar se necessário.

### Models
- `computeDimensions(videoAr)` **requer Float** — não tem overload sem args.
- `computeExtractDimensions(videoAr)` também requer Float.
- `ThumbnailSettings.sizePreset` default é `SMALL` — código que dependia de `MEDIUM` terá dimensões diferentes.

### Canvas Rendering
- `drawIntoCanvas { canvas.nativeCanvas.drawBitmap(...) }` requer:
  - `import androidx.compose.ui.graphics.nativeCanvas`
  - `import androidx.compose.ui.graphics.drawscope.drawIntoCanvas`

### Config Removidas
- `AspectRatioPreset` no ThumbnailSettings → removido (sempre 16:9)
- `scaleFactor` → removido (não usado)
- `cropZoom` → removido (não usado)
- `ExtractionConfigBottomSheet` → removido

---

## Erros de build encontrados

- **Nenhum erro de build nesta sessão.**
- 40 tarefas, ~7s de compilação estável.

---

## Resultado

| Métrica | Antes | Depois |
|---------|-------|--------|
| Extração de thumbnails | ThumbnailEngine.kt (3418 linhas) | Direta no HomeFeature (~50 linhas úteis) |
| Tamanho de extração | 107×60 (MEDIUM, .toInt truncava) | 80×45 (SMALL, roundToInt) |
| Vídeos 9:16 | Landscape (sem tratar rotação) | Portrait correto (Matrix.postRotate) |
| Timeline | Fallback a gradients | Bitmaps reais do disco |
| Tamanho timeline dp | Landscape fixo | Landscape 80×45, Portrait 45×80, Square 60×60 |
| Config de extração | Bottom sheet com várias opções | Default direto, sem config |
| Navegação | outputDir + videoAr como args | Apenas videoUri |

---

## Comandos usados

```bash
JAVA_HOME=./jdk17 ./gradlew :app:assembleDebug
```
