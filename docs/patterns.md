# Padrões de Performance — ChopCut

Mapa de relações entre padrões críticos de performance do projeto.
Cada padrão tem uma tag `@pattern:nome` ou `@violation:nome` nos sites de implementação/violação,
indexável pelo codegraph via FTS5.

---

## Causa Raiz Compartilhada

```
"trabalho desnecessário por frame"
     │
     ├── GC pressure (60 objetos/s) ──────────► @pattern:canvas-prealloc
     │
     ├── recomposition storm (State animado) ──► @pattern:canvas-isolated
     │
     └── race condition (flow + gesto)  ───────► @pattern:scrubbing-guard
```

Os padrões `canvas-prealloc` e `canvas-isolated` compartilham a mesma causa raiz
(custo por frame excessivo) e frequentemente co-ocorrem nos mesmos composables.

---

## @pattern:scrubbing-guard

**Regra:** qualquer `Flow.collectLatest` que atualize o mesmo campo que um gesto do usuário
precisa de uma flag booleana no State que silencie o observer durante o gesto.

**Primitivas necessárias:**
- `MutableStateFlow` com campo `isScrubbing: Boolean` no State
- `startScrubbing()` — ativa a flag ao iniciar o gesto
- `stopScrubbing(finalValue)` — aplica o valor final UMA SÓ VEZ e reativa o observer

**Implementações corretas:**
- `EditorFeature.kt:643` — `collectLatest { if (!isScrubbing) ... }` ← observer silenciado
- `EditorFeature.kt:699` — `startScrubbing()` ← entrada do gesto
- `EditorFeature.kt:703` — `stopScrubbing()` ← saída do gesto

**Violações conhecidas:** nenhuma

**Relacionado com:** nenhum — padrão isolado

---

## @pattern:canvas-prealloc

**Regra:** NUNCA alocar objetos dentro do lambda `Canvas { }` ou `drawBehind { }`.
Objetos criados ali são alocados a cada frame (60×/s), pressionando o GC e causando jank.

**Objetos proibidos dentro do draw scope:**
- `Paint()`, `android.graphics.Paint()`
- `Rect()`, `android.graphics.Rect()`
- `CornerRadius()`, `Path()`
- `Color.copy(alpha = ...)` — cria novo objeto `Color`
- Qualquer `String` ou objeto não-primitivo

**Padrão correto:**
```kotlin
// Pré-alocar com remember FORA do Canvas
val myPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
val myRect  = remember { android.graphics.Rect() }

Canvas(Modifier.fillMaxSize()) {
    myRect.set(...)          // reutiliza o objeto, sem alocação
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(bitmap, null, myRect, myPaint)
    }
}
```

**Implementações corretas:** nenhuma ainda

**Violações conhecidas (a corrigir):**
- `TimelineUI.kt:397` — `paint` para texto de tick — `@violation:canvas-prealloc`
- `TimelineUI.kt:456` — `renderPaint` para thumbnails — `@violation:canvas-prealloc`
- `TimelineUI.kt:460` — `bgPaint` para background — `@violation:canvas-prealloc`
- `TimelineUI.kt:513` — `shimmerPaint` para gradiente shimmer — `@violation:canvas-prealloc`
- `TimelineV2Feature.kt:825` — `paint` para drawBitmap thumbnail — `@violation:canvas-prealloc`

**Relacionado com:** `canvas-isolated` (mesma causa raiz: custo por frame)

---

## @pattern:canvas-isolated

**Regra:** qualquer State animado (`InfiniteTransition`, `Animatable`) lido DENTRO de um
Canvas que já faz rendering pesado (thumbnails, régua, waveform) invalida TODO esse Canvas
a cada frame da animação — mesmo que a animação afete apenas uma parte pequena.

**Solução:** isolar a animação em um `Canvas` separado sobreposto via `Box` ou `BoxWithConstraints`,
para que apenas o Canvas da animação seja invalidado:

```kotlin
BoxWithConstraints {
    Canvas(Modifier.fillMaxSize()) {
        // @pattern:canvas-isolated:heavy — rendering pesado sem State animado
        drawThumbnails(...)
        drawRuler(...)
    }
    Canvas(Modifier.fillMaxSize()) {
        // @pattern:canvas-isolated:anim — APENAS a animação aqui
        drawPlayhead(color = playheadColor)  // State animado fica isolado
    }
}
```

**Implementações corretas:** nenhuma ainda

**Violações conhecidas (a corrigir):**
- `TimelineUI.kt:237` — `shimmerProgress` (InfiniteTransition) lido no Canvas de thumbnails — `@violation:canvas-isolated`
- `TimelineV2Feature.kt:733` — `playheadColor` (InfiniteTransition) lido no Canvas principal — `@violation:canvas-isolated`

**Relacionado com:** `canvas-prealloc` (mesma causa raiz: custo por frame)

---

## Índice de busca rápida

| Tag | Arquivo | Linha | Tipo |
|---|---|---|---|
| `@pattern:scrubbing-guard` | EditorFeature.kt | 643 | ✅ implementação |
| `@pattern:scrubbing-guard` | EditorFeature.kt | 699 | ✅ implementação |
| `@pattern:scrubbing-guard` | EditorFeature.kt | 703 | ✅ implementação |
| `@violation:canvas-prealloc` | TimelineUI.kt | 397 | ❌ violação |
| `@violation:canvas-prealloc` | TimelineUI.kt | 456 | ❌ violação |
| `@violation:canvas-prealloc` | TimelineUI.kt | 460 | ❌ violação |
| `@violation:canvas-prealloc` | TimelineUI.kt | 513 | ❌ violação |
| `@violation:canvas-prealloc` | TimelineV2Feature.kt | 825 | ❌ violação |
| `@violation:canvas-isolated` | TimelineUI.kt | 237 | ❌ violação |
| `@violation:canvas-isolated` | TimelineV2Feature.kt | 733 | ❌ violação |
