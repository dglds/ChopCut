# Backlog v2

---

## [BUG] ~~Barra de progresso dessincronizada durante scroll~~ ✅

**Implementado:** flag `isScrubbing` no `EditorState`, `startScrubbing()`/`stopScrubbing()` no `EditorViewModel`, `localPositionMs` local no `VideoTimeline`, callbacks `onScrubStart`/`onScrubStop` no `EditorScreen`. Poll do ExoPlayer suspenso durante arraste; seek único ao soltar.

---

## [UX] ~~Scroll da timeline precisa ser mais suave~~ ✅

**Implementado:** `rememberScrollableState` com fling nativo (`ScrollableDefaults.flingBehavior()`), desacoplado do ExoPlayer. Objetos pre-alocados com `remember` (`Paint`, `Rect`) para eliminar alocações por frame no draw scope.

---

## [UX] ~~Skeleton no carregamento das thumbnails~~ ✅

**Implementado:** `isReady` coletado do `VideoTimelineViewModel`, `InfiniteTransition` com `shimmerAlpha` oscilando entre 0.04f e 0.18f (800ms). Placeholder invisível quando todos os sprites estão carregados.

---

## [UX] ~~Barras de som (waveform) estão feias~~ ✅

**Implementado:** barras 3dp com gap 1dp, espelhamento vertical (barra inferior a 60% de alpha), alpha dinâmico por amplitude (0.4–1.0), normalização pelo pico da janela visível, iteração por posição em pixels para densidade uniforme independente da duração do vídeo.

---

## [PERF] Setup do AudioDataExtractor acima do KPI

**Arquivo:** `data/audio/AudioDataExtractor.kt`

**Contexto:** Medição via Perfetto mostrou setup em **104ms**, acima do KPI definido de < 80ms. O setup inclui instanciação e configuração do MediaCodec.

**Hipóteses:**
- Instanciar o codec a cada extração tem overhead fixo elevado
- Configuração do `MediaFormat` pode estar aguardando negociação com o hardware

**Steps:**
- [ ] Capturar trace detalhado do setup para identificar onde os 104ms são gastos (negociação de formato vs. alocação de buffers)
- [ ] Avaliar pool de codecs reutilizáveis entre extrações
- [ ] Avaliar lazy init diferido: configurar codec enquanto a UI ainda carrega

---

## [PERF] DecodeLoop não testado com vídeos longos

**Arquivo:** `data/audio/AudioDataExtractor.kt`

**Contexto:** O DecodeLoop levou 1.26s com `step 4` (processa 1 a cada 4 amostras). Vídeo testado tinha duração desconhecida. Para vídeos de 5–15 minutos, o tempo pode escalar para 10s+, bloqueando a entrada no editor.

**Referência:** `sample15min.mp4` existe em `app/src/androidTest/assets/` e pode ser usado para medir.

**Steps:**
- [ ] Capturar trace Perfetto com `sample15min.mp4` e registrar tempo do DecodeLoop
- [ ] Se > 5s: aumentar `step` dinamicamente por duração (ex: step 4 < 2min, step 8 2–10min, step 16 > 10min)
- [ ] Avaliar reduzir sample rate no `MediaFormat` para vídeos longos (menor fidelidade, menor volume de dados)
- [ ] Definir KPI: tempo máximo aceitável de extração de waveform por duração de vídeo

---

## [PERF] AudioExtractor (remux) ausente nas medições Perfetto

**Arquivo:** `data/audio/AudioExtractor.kt`

**Contexto:** O `AudioExtractor` faz remuxing do áudio para um arquivo temporário antes da decodificação pelo `AudioDataExtractor`. Esse passo **não apareceu** nos resultados do trace — ou o trace foi capturado sem cobrir essa etapa, ou os blocos de instrumentação não estão sendo emitidos corretamente.

Se o remux for um gargalo oculto, o tempo real de "abertura do editor" é maior do que os 1.4s medidos.

**Steps:**
- [ ] Verificar se os traces `AudioExtractor.Setup` e `AudioExtractor.CopyTrack` estão sendo emitidos no Perfetto
- [ ] Capturar trace cobrindo todo o fluxo desde a seleção do vídeo até o editor aberto
- [ ] Se remux > 200ms: avaliar eliminar o passo e passar o URI original diretamente ao `AudioDataExtractor`
