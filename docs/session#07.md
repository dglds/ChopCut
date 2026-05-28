# Session #07 — Sincronização de Thumbnails: dp vs px, AR e Resolução

**Modelo:** Claude Sonnet 4.6 (`claude-sonnet-4-6`)  
**Data:** 2026-05-28  
**Objetivo:** Eliminar stretch nos thumbnails da TimelineV2 sincronizando tamanhos de extração com os frames da timeline, com suporte correto a qualquer aspect ratio e resolução de vídeo.

---

## O que foi feito

### 1. Diagnóstico do mismatch dp vs px

O pipeline de extração (`ThumbnailExtraction.kt`) usava `SizePreset.SMALL` gerando bitmaps em **pixels brutos** (80×45 px para landscape). A timeline exibia esses bitmaps em **dp** — em xxhdpi (density=3), 80×45dp correspondem a 240×135px físicos, causando stretch 3× e thumbnails borrados.

### 2. `ThumbnailSettings.explicitWidthPx / explicitHeightPx` (`Models.kt`)

Dois campos opcionais para passar dimensões exatas em pixels físicos ao pipeline de extração. Quando preenchidos, `computeDimensions()` retorna esses valores diretamente (backward-compatible — callers sem os campos continuam funcionando normalmente).

### 3. Fórmula de área constante (primeira iteração)

Substituídos blocos `when { isPortrait → ... }` por fórmula `w = BASE×√AR, h = BASE/√AR` (BASE=60dp). Dá 0% de erro de AR para qualquer vídeo — incluindo 4:5, 3:4 e outros ARs não-padrão que antes tinham 25-30% de distorção.

### 4. `ThumbnailConfig.TimelineV2Thumbs.computeDp()` — fator de resolução (`EditorFeature.kt`)

Evolução final: incorpora a **resolução real do vídeo** além do AR puro. Vídeos 4K recebem thumbnails maiores que 720p com o mesmo AR.

**Fórmula:**
```
resFactor = (max(videoWidth, videoHeight) / 1080)^0.3
base = 60 × resFactor
uncW = base × √AR,  uncH = base / √AR
capScale = min(100/uncW if uncW>100 else 1, 90/uncH if uncH>90 else 1)
thumbW = uncW × capScale,  thumbH = uncH × capScale  ← AR preservado!
```

### 5. `TimelineV2ViewModel` expõe dimensões reais

Adicionados `videoWidth` e `videoHeight` como StateFlows (default 1920/1080), preenchidos quando `getMetadata()` retorna.

### 6. `TimelineV2` recebe dimensões em vez de AR

Parâmetro `videoAr: Float` substituído por `videoWidth: Int, videoHeight: Int`. A assinatura fica mais explícita e a fórmula usa as dimensões reais.

### 7. Extração unificada em `HomeFeature.kt`

Substituídas as fórmulas inline pela chamada a `computeDp(videoInfo.width, videoInfo.height)`. Extração e exibição compartilham a mesma função — garantia de correspondência pixel-a-pixel.

---

## Resultados

| Vídeo | Antes | Depois | Erro AR |
|---|---|---|---|
| 720p landscape 16:9 | 80×45dp | **84×47dp** | 0% |
| 720p portrait 9:16 | 45×80dp | **47×84dp** | 0% |
| 1080p landscape 16:9 | 80×45dp | **95×54dp** | 0% |
| 1080p portrait 9:16 | 45×80dp | **51×90dp** | 0% |
| 4K landscape 16:9 | 80×45dp | **100×56dp** | 0% |
| 4K portrait 9:16 | 45×80dp | **51×90dp** | 0% |
| Portrait 4:5 (1080×1350) | ~54×67dp* | **57×72dp** | 0% |

*antes tinha 25-30% erro de AR por usar blocos `when` de categorias fixas

---

## Arquivos Modificados

| Arquivo | Mudança |
|---|---|
| `core/Models.kt` | `+explicitWidthPx`, `+explicitHeightPx` em `ThumbnailSettings` |
| `ui/editor/EditorFeature.kt` | `+computeDp()`, `+MAX_THUMB_WIDTH_DP` em `TimelineV2Thumbs`; imports math |
| `ui/editor/TimelineV2Feature.kt` | `+videoWidth/videoHeight` no ViewModel; `TimelineV2` recebe dims; formula → `computeDp` |
| `ui/home/HomeFeature.kt` | Extração usa `computeDp(videoInfo.width, videoInfo.height)` |

---

## Nota para re-extração

Vídeos já extraídos com configurações antigas precisam ser **re-extraídos** (botão "Extrair Frames" na Home) para obter thumbnails com o tamanho correto e sem stretch. Os bitmaps antigos continuam funcionando, apenas com qualidade inferior.

---

## Pendências

- Testar no dispositivo com vídeo portrait real após re-extração
- Considerar mostrar um indicador quando os frames extraídos estão "desatualizados" (tamanho diferente do esperado)
- Warnings de depreciação do Gradle 9.0 (não crítico)

---

## Comandos úteis desta sessão

```bash
# Build
JAVA_HOME=./jdk17 ./gradlew :app:assembleDebug

# Verificar tamanho dos frames extraídos no emulador
adb shell ls -la /sdcard/Android/data/com.chopcut/files/extracted_frames/<nome>/

# Copiar frames para o computador
adb pull "/sdcard/Android/data/com.chopcut/files/extracted_frames/<nome>" ./extracted_frames
```

---

## Uso de ferramentas

- **Read** — leitura de arquivos para entender estado atual antes de editar
- **Edit** — todas as modificações de código
- **Bash** — builds, greps, validação Python da fórmula
- **Write** — criação do walkthrough e deste arquivo de sessão

O maior consumo de tokens foi a leitura de `TimelineV2Feature.kt` e `HomeFeature.kt` (arquivos grandes), e a conversa de diagnóstico antes de definir a abordagem.

## Sugestões para economia de tokens em sessões futuras

- Usar `grep -n` antes de `Read` para localizar exatamente a seção a editar, evitando leituras de arquivos inteiros
- Para mudanças que afetam múltiplos arquivos com a mesma lógica, centralizar em uma função e chamar de todos os pontos (exatamente o que foi feito com `computeDp`)
