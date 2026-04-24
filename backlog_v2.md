# Backlog v2

---

## [BUG] Barra de progresso dessincronizada durante scroll

**Arquivos:** `ui/screen/TrimScreen.kt:306`, `ui/components/timeline/VideoTimeline.kt:99`, `ui/viewmodel/TrimViewModel.kt:117`, `ui/components/player/PlayerManager.kt:51`

**Causa raiz:** `_state.currentPosition` é escrito por duas fontes simultâneas em conflito: o `currentPositionFlow` do `PlayerManager` (poll de 100ms sobre `exoPlayer.currentPosition`) e o `setCurrentPosition()` chamado pelo scroll. Durante o arraste, o poll do ExoPlayer sobrescreve a posição calculada pelo delta, causando saltos visuais na seekbar e na timeline.

### Fluxo de dados atual

```
PlayerManager.currentPositionFlow   →  emit(exoPlayer.currentPosition) a cada 100ms
        ↓ collectLatest (TrimViewModel.kt:117)
TrimViewModel._state.currentPosition  ←─────────────────────────────────────┐
        ↓                                                                     │
TrimScreen: state.currentPosition                                             │
        ├── SeekbarProgress(progress = currentPosition / duration)            │
        ├── VideoTimeline(currentPositionMs = currentPosition)                │
        │       └── onSeek(newPos) → viewModel.setCurrentPosition(newPos)    │
        └── VideoPreview(currentTimeMs = currentPosition)                     │
                                                                              │
setCurrentPosition(pos):                                                      │
    _state.currentPosition = pos  (imediato)                                  │
    exoPlayer.seekTo(pos)  (assíncrono) ──────────────────────────────────►─┘
```

### Pontos críticos identificados

**PC1 — Race condition entre dois escritores** (`TrimViewModel.kt:117` vs `:149`): `collectLatest` não para durante o scroll — continua emitindo a cada 100ms e sobrescreve a posição calculada pelo delta imediatamente.

**PC2 — ExoPlayer não reporta posição do seek imediatamente**: `seekTo()` é assíncrono. Por alguns frames após a chamada, `exoPlayer.currentPosition` retorna a posição anterior. O poll captura esse valor intermediário e o escreve no estado, causando o "snap" visual.

**PC3 — Delta calculado sobre valor instável** (`VideoTimeline.kt:99`): O `rememberScrollableState` calcula `newPos = (currentPositionMs - deltaMs)`. Como `currentPositionMs` vem de `state.currentPosition` — que pode ser sobrescrito pelo poll durante o gesto — o próximo delta é calculado relativo à posição errada, fazendo a timeline pular ao invés de seguir o dedo de forma linear.

**PC4 — Flood de seeks**: Em um scroll de 1s a 60fps, `exoPlayer.seekTo()` é chamado ~60 vezes. O ExoPlayer descarta seeks intermediários, e a interação entre a fila descartada e o poll de 100ms gera posições imprevisíveis visíveis na seekbar.

### Solução recomendada — flag `isScrubbing`

Adicionar `isScrubbing: Boolean = false` ao `TrimEditorState` e suspender o observer do ExoPlayer enquanto o usuário arrasta:

```kotlin
// TrimViewModel — suspender poll durante scroll
playerManager?.currentPositionFlow?.collectLatest { position ->
    if (!_state.value.isScrubbing) {
        _state.update { it.copy(currentPosition = position) }
    }
}

fun startScrubbing() = _state.update { it.copy(isScrubbing = true) }

fun stopScrubbing(finalPos: Long) {
    playerManager?.seekTo(finalPos)   // seek único ao soltar
    _state.update { it.copy(isScrubbing = false) }
}
```

`VideoTimeline` expõe `onScrubStart` / `onScrubStop` além do `onSeek` existente. `SeekbarProgress` e `CurrentTimeDisplay` continuam lendo `state.currentPosition` — que agora é estável durante o scroll porque o poll está suspenso.

### Steps

- [ ] Adicionar `isScrubbing: Boolean = false` a `TrimEditorState`
- [ ] Em `TrimViewModel`, condicionar o `collectLatest` de `currentPositionFlow` ao flag `!isScrubbing`
- [ ] Criar `startScrubbing()` e `stopScrubbing(finalPos)` no `TrimViewModel`
- [ ] Em `VideoTimeline`, adicionar parâmetros `onScrubStart: () -> Unit` e `onScrubStop: (Long) -> Unit`; chamar no início e fim do gesto no `rememberScrollableState`
- [ ] Em `TrimScreen:325`, passar os novos callbacks para `VideoTimeline`
- [ ] Manter um state local em `VideoTimeline` para posição de scroll durante o arraste, evitando dependência de `currentPositionMs` externo nos cálculos de delta (resolve PC3)
- [ ] Fazer seek único em `stopScrubbing` — remover o `seekTo` do `setCurrentPosition` ou guardá-lo atrás do flag

---

## [UX] Scroll da timeline precisa ser mais suave

**Arquivo principal:** `ui/components/timeline/VideoTimeline.kt:95-101`

**Causa raiz:** O `rememberScrollableState` entrega deltas crus sem fling/momentum. Cada delta chama `onSeek` diretamente acionando seeks no ExoPlayer a cada evento de toque.

**Steps:**
- [ ] Introduzir um `MutableState<Long>` local para posição de scroll visual, desacoplado do ExoPlayer
- [ ] Usar `detectHorizontalDragGestures` com `VelocityTracker` para capturar velocidade do gesto
- [ ] Implementar fling com `Animatable` para continuar o movimento após soltar o dedo
- [ ] Limitar seeks ao ExoPlayer com debounce, ou apenas ao final do fling
- [ ] Testar com vídeos curtos (<30s) e longos (>5min)

---

## [UX] Skeleton no carregamento das thumbnails

**Arquivo principal:** `ui/components/timeline/VideoTimeline.kt:155-165`

**Causa raiz:** Quando o sprite ainda não foi extraído (`sprite == null`), é desenhado um retângulo quase invisível (`Color.White.copy(alpha = 0.05f)`), dando impressão de timeline vazia ou quebrada.

**Steps:**
- [ ] Criar animação shimmer com `InfiniteTransition` + `Brush.linearGradient` com offset animado
- [ ] Substituir o `drawRect` atual pelo skeleton no bloco `else` da checagem de sprite
- [ ] O skeleton deve respeitar as mesmas dimensões do thumb (`thumbHeightPx` × `pxPerSecond`)
- [ ] Adicionar transição de fade ao exibir o sprite real substituindo o placeholder
- [ ] Opcional: expor `extractionProgress` do `VideoTimelineViewModel` para mostrar progresso geral abaixo da timeline

---

## [UX] Barras de som (waveform) estão feias

**Arquivo principal:** `ui/components/timeline/VideoTimeline.kt:178-225`

**Causa raiz:** Waveform renderizado inline com `drawRoundRect` simples — barras de 2dp fixas, cor ciano sólida sem variação de intensidade, sem gradiente e sem espelhamento. O componente `WaveForm.kt` com suporte a mirrored/smoothed/gradiente existe mas não é usado na timeline.

**Steps:**
- [ ] Aumentar largura das barras de 2dp para ~3dp com espaçamento de 1dp entre elas
- [ ] Aplicar gradiente vertical por barra: amplitude alta → ciano saturado (`#00E5FF`), amplitude baixa → ciano escuro com mais transparência
- [ ] Adicionar espelhamento vertical (desenhar barra também abaixo do centro), criando forma simétrica estilo osciloscópio
- [ ] Normalizar altura das barras pelo pico real da janela visível, não pelo pico global
- [ ] Avaliar substituir o rendering inline pelo componente `WaveForm.kt` que já tem smoothed, mirrored e animação de entrada
