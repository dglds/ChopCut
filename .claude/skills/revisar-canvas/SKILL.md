---
name: revisar-canvas
description: Audita código de UI do ChopCut (Jetpack Compose Canvas/DrawScope) contra os 3 padrões críticos de performance do projeto — alocação no draw scope, race do gesto vs. estado observado, e isolamento de animação de Canvas. Use ao escrever, revisar ou refatorar qualquer Canvas, drawBehind ou DrawScope, especialmente na timeline.
---

# revisar-canvas — auditor de performance de Canvas do ChopCut

Varre o código de UI procurando os 3 padrões que causam jank visível em dispositivos mid-range. Estas regras vêm de bugs reais deste projeto (documentadas em `CLAUDE.md` → "Padrões críticos de performance"). Cada frame de scroll/animação roda a 60Hz; qualquer alocação ou invalidação supérflua multiplica por 60.

## Onde olhar

Arquivos que contêm `Canvas(`, `drawBehind`, `drawWithContent` ou `DrawScope`. Hoje:

- `app/src/main/java/com/chopcut/ui/editor/TimelineFeature.kt` ← alvo principal (timeline, thumbnails, playhead)
- `app/src/main/java/com/chopcut/ui/SharedComponents.kt`
- `app/src/main/java/com/chopcut/ui/home/HomeFeature.kt`
- `app/src/main/java/com/chopcut/data/ThumbnailExtraction.kt`

Reconfirme a lista com: `grep -rln "Canvas(\|drawBehind\|drawWithContent\|DrawScope" --include="*.kt" app/src/main/java/com/chopcut`

## Convenção de anotação do projeto

O código marca pontos conhecidos com comentários `// @violation:<tag>`. Respeite-os:

- Se já existe `@violation:` num trecho, **não duplique** o achado — confira se ainda é válido. Se o código foi corrigido, sinalize que a anotação está obsoleta e deve ser removida.
- Ao reportar uma violação nova que o autor talvez queira deixar para depois, sugira a anotação correspondente (`@violation:draw-alloc`, `@violation:scrub-race`, `@violation:canvas-isolated`).

## Regra 1 — `@violation:draw-alloc` · zero alocação dentro do draw scope

Qualquer objeto construído dentro do lambda de `Canvas { }` / `drawBehind { }` (incluindo dentro de loops e `forEach` lá dentro) é alocado a cada frame e vira pressão de GC.

**Sinalize quando, dentro do draw scope, encontrar:**

| Padrão | Por quê | Correção |
|--------|---------|----------|
| `android.graphics.Rect(...)`, `RectF(...)`, `Rect(...)` | novo objeto por frame (e por item, se em loop) | pré-alocar 1 `Rect` com `remember` e usar `.set(l,t,r,b)` antes de desenhar |
| `Color.X.copy(alpha = ...)` | cria novo `Color` por chamada | pré-computar a cor final em `remember` (ou `val` no escopo composable) |
| `Brush.linearGradient(...)`, `Path(...)`, `Paint(...)`, `CornerRadius(...)` | alocação por frame | `remember` no composable; para brushes que mudam de posição, avalie cor sólida |
| `textMeasurer.measure(...)` dentro de loop/draw | aloca `TextLayoutResult` por tick por frame | medir 1x fora do loop quando o texto é estável, ou cachear por chave |
| `.subList(...)`, `.map{}`, `.filter{}`, `listOf(...)` | aloca `List` | mover cálculo para fora ou usar índices |
| qualquer `String` interpolada (`"${i}s"`) recalculada por frame | aloca `String` | aceitável só se o resultado for memoizado |

**Padrão correto (já presente em `TimelineFeature.kt:754` para o `Paint`):**
```kotlin
val thumbnailPaint = remember { android.graphics.Paint(FILTER_BITMAP_FLAG).apply { isAntiAlias = true } }
val dstRect = remember { android.graphics.Rect() }   // pré-alocar TAMBÉM o Rect
Canvas(...) {
    dstRect.set(left, top, right, bottom)            // reutiliza, sem alocação
    drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, null, dstRect, thumbnailPaint) }
}
```

> Nota de calibração: o `Paint` já é pré-alocado corretamente, mas o `Rect` de destino do bitmap ainda é construído dentro do loop de thumbnails — esse é o tipo exato de achado esperado.

## Regra 2 — `@violation:scrub-race` · flag durante gestos sobre estado observado

Quando um flow contínuo (poll do ExoPlayer, timer, sensor) **e** um gesto escrevem no mesmo campo de State, o flow sobrescreve o valor do gesto a cada poll → playhead "pula" durante o scrub.

**Sinalize quando:** um `collect`/`collectLatest`/`onEach` no ViewModel escreve em `_state` **sem** checar uma flag, e existe um gesto (`detectHorizontalDragGestures`, `detectDragGestures`, `pointerInput`) que mexe no mesmo campo.

**Padrão correto:**
```kotlin
flow.collectLatest { value -> if (!_state.value.isScrubbing) _state.update { it.copy(field = value) } }
fun startScrubbing() = _state.update { it.copy(isScrubbing = true) }
fun stopScrubbing(final: T) { applyFinal(final); _state.update { it.copy(isScrubbing = false) } }
```
O componente segura um `localState` durante o gesto e só propaga o valor final no `onGestureEnd`. Confira também se o gesto chama `change.consume()`.

## Regra 3 — `@violation:canvas-isolated` · isolar animação de Canvas pesado

Ler um State animado (`InfiniteTransition`, `Animatable`, `animate*AsState`) dentro de um Canvas que já faz loops de rendering (régua, thumbnails, waveform) invalida **todo** o Canvas a cada frame da animação.

**Sinalize quando:** o valor animado é lido dentro do mesmo `Canvas {}` que percorre listas/desenha bitmaps.

**Padrão correto (já aplicado em `TimelineFeature.kt` — playhead no Canvas separado da linha ~1000):**
```kotlin
BoxWithConstraints {
    Canvas(Modifier.fillMaxSize()) { /* rendering pesado, SEM ler state animado */ }
    Canvas(Modifier.fillMaxSize()) { /* só o animado: playhead, skeleton, highlight */ }
}
```
> Calibração: o ChopCut já separa o playhead corretamente. Se encontrar um comentário `@violation:canvas-isolated` num trecho onde a animação **já** está isolada, a anotação está obsoleta — recomende removê-la.

## Como reportar

Não edite nada sem ser pedido. Produza um relatório enxuto:

1. **Resumo:** nº de achados por regra (1/2/3) e por severidade.
2. **Por achado:** `arquivo:linha` clicável · regra violada · trecho · correção concreta (com o `remember`/flag já escrito). Severidade:
   - 🔴 **alta** — dentro de loop de draw ou em flow de 60/Hz (ex: `Rect` por thumbnail por frame)
   - 🟡 **média** — alocação no draw scope mas fora de loop apertado (ex: 1 `Color.copy` por frame)
   - ⚪ **baixa** — anotação obsoleta, micro-otimização
3. **Encerramento:** se quiser, ofereça aplicar as correções 🔴 com `remember`/flag. Validar sempre com `JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin` e, para confirmar fluidez, checagem visual no app (não há teste automatizado de jank).

Não confunda alocação em **escopo composable** (ok — roda na recomposição, não por frame) com alocação no **draw scope** (proibido). A linha divisória é a abertura do lambda `Canvas { ... }` / `drawBehind { ... }`.
