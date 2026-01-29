# Canvas vs Componentes Compose

Guia de quando substituir Canvas por componentes Compose padrão.

## Resumo Visual

```
┌─────────────────────────────────────────────────────────────────┐
│  CANVAS (Imperativo)              COMPOSE (Declarativo)         │
├─────────────────────────────────────────────────────────────────┤
│  drawLine()         ────────→    Divider() ou Box()             │
│  drawRect()         ────────→    Box() + background()           │
│  drawCircle()       ────────→    Box() + shape = CircleShape    │
│  drawPath()         ────────→    Shape customizado ou Icon()    │
│  drawText()         ────────→    Text()                         │
│  clipRect()         ────────→    Modifier.clip()                │
│  drawImage()        ────────→    Image()                        │
└─────────────────────────────────────────────────────────────────┘
```

## Tabela de Substituição

| Canvas API | Substituição Compose | Quando Usar |
|------------|---------------------|-------------|
| `drawLine()` | `Divider()`, `Box()` com width/height | Ticks, linhas simples |
| `drawRect()` | `Box()` com `background()` | Retângulos, ranges, cards |
| `drawCircle()` | `Box()` com `clip(CircleShape)` | Avatares, badges |
| `drawRoundRect()` | `Surface()` ou `Card()` | Botoes, containers |
| `drawPath()` | Shape customizado ou `Icon()` | Formas complexas |
| `drawText()` | `Text()` | Sempre prefira Text() |
| `clipRect()` | `Modifier.clip()` ou condicional | Mascaramento |
| `drawImage()` | `Image()`, `AsyncImage()` | Fotos, assets |

## Exemplo: Timeline

### ❌ ANTES (Canvas)
```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    // Ticks
    for (sec in start..end) {
        val x = calculateX(sec)
        drawLine(
            color = tickColor,
            start = Offset(x, 0f),
            end = Offset(x, tickHeight),
            strokeWidth = 2f
        )
    }
    
    // Range
    drawRect(
        color = rangeColor,
        topLeft = Offset(rangeStart, 20f),
        size = Size(rangeWidth, height - 40f)
    )
}
```

**Problemas:**
- 60+ linhas de código
- Cálculos manuais de coordenadas
- Recomposition recalcula tudo
- Sem acessibilidade automática

### ✅ DEPOIS (Compose)
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Ticks
    for (sec in visibleRange) {
        Divider(
            modifier = Modifier
                .offset(x = calculateX(sec).dp)
                .width(2.dp)
                .height(16.dp)
        )
    }
    
    // Range
    Box(
        modifier = Modifier
            .offset(x = rangeStart.dp, y = 20.dp)
            .width(rangeWidth.dp)
            .height(60.dp)
            .background(rangeColor)
    )
}
```

**Vantagens:**
- 20 linhas de código
- Layout engine faz os cálculos
- Recomposition inteligente
- Acessibilidade automática

## Quando MANTER Canvas

```
✅ USE CANVAS QUANDO:
   ├─ Waveforms de áudio (milhares de barras)
   ├─ Gráficos/charts complexos (line, bar, pie)
   ├─ Animações 2D/games
   ├─ Desenho livre (paint apps)
   └─ Performance crítica (>1000 elementos)

❌ EVITE CANVAS PARA:
   ├─ UI comum (botoes, cards, listas)
   ├─ Layouts estruturados
   ├─ Elementos que precisam de a11y
   └─ Menos de 100 elementos na tela
```

## Arquivos do Projeto que Podem Ser Refatorados

| Arquivo | Canvas? | Sugestão |
|---------|---------|----------|
| `WaveForm.kt` | ✅ Sim | Manter - muitas barras de áudio |
| `SimpleTimeline.kt` | ✅ Sim | Pode usar ComposeTimeline.kt |
| `TimelinePlayer.kt` | ❌ Não | Já usa componentes |
| `RangeOverlay.kt` | ✅ Sim | Avaliar - ranges podem ser Box() |

## Benchmark Rápido

```kotlin
// Teste de performance simples
@Composable
fun PerformanceTest() {
    // Compose: bom até ~200 elementos visíveis
    Column {
        repeat(200) { 
            Box(modifier = Modifier.size(10.dp).background(Color.Blue))
        }
    }
    
    // Canvas: bom até ~5000+ elementos
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(5000) { i ->
            drawCircle(Color.Blue, radius = 5f, center = Offset(i * 10f, 50f))
        }
    }
}
```

## Regra de Ouro

> **"Se você pode descrever como Row/Column/Box, use Compose. Se precisa de coordenadas matemáticas complexas, use Canvas."**
