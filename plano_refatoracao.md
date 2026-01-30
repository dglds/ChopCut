# Plano de Refatoração - ChopCut Editor

## 📋 Visão Geral

**Objetivo:** Consolidar arquitetura da timeline, eliminar duplicação e otimizar performance para Celeron N5095A.

**Escopo:** Módulo Editor (EditorScreen, Timeline, Ranges, Video Player)

**Estimativa:** 2-3 dias de trabalho

---

## 🔴 Problemas Atuais

### 1. Duplicação de Código
| Componente | Localização | Problema |
|------------|-------------|----------|
| `TimelinePlayer.kt` | `ui/components/` | 465 linhas, mistura vídeo+timeline+ranges |
| `TimelinePlayer.kt` | `ui/timeline/` | 384 linhas, versão diferente do mesmo |
| `TimelineStrip.kt` | `ui/timeline/` | 368 linhas, lógica de ranges duplicada |
| `Timeline.kt` | `ui/timeline/` | 560 linhas, lista de ranges redundante |

### 2. Estado Espalhado
```
Estado atual (FRAGMENTADO):
├── EditorScreen.ranges (mutableState)
├── EditorScreen.selectedRangeId
├── EditorScreen.fabState
├── TimelineViewModel.state (flux próprio)
├── EditorViewModel (outro flux)
└── Componentes com estado local (drag, etc)
```

### 3. Performance
- Recompositions excessivas durante scroll
- Cálculos de px→ms repetidos em múltiplos lugares
- Sem uso adequado de `derivedStateOf`

---

## 🟢 Arquitetura Alvo

### Estrutura de Pastas
```
ui/timeline/
├── EditorScreen.kt                 # 200 linhas (só layout)
├── EditorViewModel.kt              # Estado único consolidado
├── PreviewManager.kt               # Gerenciador do ExoPlayer
├── PlayerInteractionState.kt       # Estados de interação (renomeado)
├── components/
│   ├── VideoPlayer.kt              # ExoPlayer isolado
│   ├── TimelineScrubber.kt         # Faixa scrollável
│   ├── RangeOverlay.kt             # Overlays vermelhos
│   ├── PlayheadIndicator.kt        # Indicador central fixo
│   └── FabRangeController.kt       # Botão flutuante
├── model/
│   ├── EstadoEditor.kt             # ✅ Estado consolidado
│   ├── RangeCorte.kt               # ✅ Modelo de domínio unificado
│   └── EstadoCriacao.kt            # Estados do fluxo de criação
└── util/
    └── ConversoesTempo.kt          # ✅ px↔ms centralizado
```

### Fluxo de Dados (Unidirecional)
```
┌─────────────────────────────────────────────┐
│         User Interaction                    │
│   (touch, scroll, fab click)                │
└──────────────┬──────────────────────────────┘
               │
               ▼ Evento
┌─────────────────────────────────────────────┐
│      EditorViewModel                        │
│  - processa evento                          │
│  - atualiza state                           │
│  - valida regras de negócio                 │
└──────────────┬──────────────────────────────┘
               │ StateFlow<EstadoEditor>
               ▼
┌─────────────────────────────────────────────┐
│      EditorScreen (observa state)           │
│  - deriva valores computados                │
│  - repassa para componentes                 │
└──────────────┬──────────────────────────────┘
               │
    ┌──────────┼──────────┬──────────┐
    ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ Video  │ │Timeline│ │ Ranges │ │  FAB   │
│ Player │ │Scrubber│ │Overlay │ │        │
└────────┘ └────────┘ └────────┘ └────────┘
```

---

## 📝 Fases da Refatoração

### ✅ FASE 1: Modelos de Estado (CONCLUÍDA)
**Data:** 2026-01-30 | **Duração:** ~2 horas

**Arquivos Criados:**

1. **`model/RangeCorte.kt`** - Modelo unificado de range
   - Substitui `TrimRangeData` e `VideoRange`
   - Propriedades: `id`, `inicioMs`, `fimMs`, `emEdicao`, `confirmado`
   - Extensões úteis: `sobrepoe()`, `ajustarParaEvitarSobreposicao()`, `coercer()`
   - Helper functions: `noPlayhead()`, `anteriorA()`, `posteriorA()`

2. **`model/EstadoEditor.kt`** - Estado consolidado único
   - `EstadoEditor` - Estado completo do editor
   - `EstadoCriacao` - Fluxo de 2 cliques (OCIOSO, AguardandoFim)
   - `EstadoPlayer` - Estados do player (CARREGANDO, PRONTO, PAUSADO, etc)
   - `EstadoFab` - Estados do FAB (ADICIONAR, CONFIRMAR, DELETAR)
   - `EventoEditor` - Eventos de usuário (sealed class)

3. **`util/ConversoesTempo.kt`** - Conversões centralizadas
   - `ConfiguracaoTimeline` - Constantes de configuração
   - `ConversorTempoPx` - Classe de conversão tempo <-> pixels
   - Funções de formatação: `formatarTempo()`, `formatarTempoCurto()`
   - Cálculos de validação: `calcularInicioValido()`, `calcularFimValido()`

4. **Renomeado:** `TimelineState.kt` → `PlayerInteractionState.kt`
   - Evita conflito de nomes com `TimelineState` (data class)

**Commits:**
```
feat: implementa modelos de estado consolidados para timeline

- Cria RangeCorte.kt unificando TrimRangeData e VideoRange
- Cria EstadoEditor.kt com estado único do editor
- Cria ConversoesTempo.kt centralizando cálculos px↔ms
- Renomeia TimelineState.kt para PlayerInteractionState.kt
```

---

### ✅ FASE 2: ViewModel Consolidado (CONCLUÍDA)
**Data:** 2026-01-30 | **Duração:** ~3 horas

**Arquivo Criado:**

**`EditorTimelineViewModel.kt`** - ViewModel único consolidado

**Estrutura:**
```kotlin
class EditorTimelineViewModel(
    context: Context,
    previewManager: PreviewManager
) : ViewModel() {
    // Estado privado (fonte da verdade)
    val estadoEditor: StateFlow<EstadoEditor>
    
    // Estados derivados (calculados automaticamente)
    val estadoFab: StateFlow<EstadoFab>
    val rangeEmCriacao: StateFlow<RangeCorte?>
    val rangeNoPlayhead: StateFlow<RangeCorte?>
    
    // Processamento de eventos
    fun processarEvento(evento: EventoEditor)
}
```

**Funcionalidades implementadas:**
- ✅ Sincronização bidirecional com PreviewManager
- ✅ `estadoFab` calculado via `StateFlow` + `map` + `stateIn`
- ✅ `rangeEmCriacao` atualizado dinamicamente com playhead
- ✅ `rangeNoPlayhead` detectado automaticamente
- ✅ Handlers completos para todos os EventoEditor:
  - `PrepararVideo` - Inicialização com URI
  - `IniciarCriacaoRange` / `FinalizarCriacaoRange` - Fluxo 2 cliques
  - `AtualizarRange` - Com auto-ajuste de sobreposição
  - `DeletarRange` / `DeletarRangeNoPlayhead`
  - `AlternarReproducao` / `Parar` / `Seek`
  - `Arraste` - Gerenciamento de scrubbing

**Commit:** `feat: implementa EditorTimelineViewModel com estado consolidado`

**Risco:** Médio | **Rollback:** Moderado

---

### FASE 3: Componentização
**Objetivo:** Extrair componentes puros da TimelinePlayer.kt atual

- [ ] 3.1 `VideoPreview.kt`
  - Wrapper do ExoPlayer
  - Parâmetros: `videoUri`, `posicaoMs`, `isPlaying`
  - Callbacks: `onPlayPause`, `onSeek`

- [ ] 3.2 `TimelineScrubber.kt`
  - Faixa scrollável com frames
  - Parâmetros: `duracaoMs`, `posicaoMs`, `onPosicaoChange`

- [ ] 3.3 `RangeOverlay.kt`
  - Desenha ranges vermelhos sobre timeline
  - Parâmetros: `ranges: List<RangeCorte>`, `rangeEmCriacao: RangeCorte?`
  - Suporta drag de alças

- [ ] 3.4 `PlayheadIndicator.kt`
  - Indicador central fixo
  - Estados: `normal`, `relevo` (animação)

- [ ] 3.5 `FabRangeController.kt`
  - Botão flutuante com estados visuais
  - Parâmetros: `estadoFab: EstadoFab`, `onClick`

- [ ] 3.6 Commit: "feat: implementa componentes puros do editor"

**Risco:** Médio | **Rollback:** Complexo (muitos arquivos)

---

### FASE 4: Integração
**Objetivo:** Montar tela principal com novo fluxo

- [ ] 4.1 Atualizar `EditorScreen.kt`
  - Usar `EditorTimelineViewModel` novo
  - Remover estados locais (`ranges`, `selectedRangeId`, `fabState`, etc)
  - Observar `StateFlow<EstadoEditor>`

- [ ] 4.2 Conectar componentes ao fluxo unidirecional
  - Mapear eventos de UI para `EventoEditor`
  - Usar `derivedStateOf` para valores computados

- [ ] 4.3 Implementar sync Player ↔ Timeline
  - PreviewManager → EstadoEditor (posição do player)
  - EstadoEditor → PreviewManager (seek commands)

- [ ] 4.4 Testar fluxo completo de criação de range
  - 2 cliques no FAB
  - Range esticando dinamicamente
  - Confirmação e cancelamento

- [ ] 4.5 Commit: "feat: integra novo editor com arquitetura consolidada"

**Risco:** Alto | **Rollback:** Complexo

---

### FASE 5: Otimização
**Objetivo:** Performance no Celeron N5095A

- [ ] 5.1 Adicionar `remember` e `derivedStateOf` estratégico
- [ ] 5.2 Implementar lazy loading de thumbnails
- [ ] 5.3 Limitar FPS de atualização do playhead (16ms)
- [ ] 5.4 Reduzir alocações em scroll (reusar objetos)
- [ ] 5.5 Profile no Celeron: scroll deve estar < 16ms/frame
- [ ] 5.6 Commit: "perf: otimiza editor para Celeron N5095A"

**Risco:** Médio | **Rollback:** Moderado

---

## 🧪 Critérios de Aceitação

### Funcionais
- [ ] Fluxo de 2 cliques funciona (ADD → RANGE1 → RANGE2)
- [ ] Playhead em relevo durante criação
- [ ] Range estica dinamicamente durante scroll
- [ ] Auto-ajuste de sobreposição funciona
- [ ] Deletar range pelo FAB funciona
- [ ] Exportar vídeo com ranges aplicados

### Performance (Celeron N5095A)
- [ ] Scroll da timeline: 60 FPS consistente
- [ ] Recompositions < 10 por segundo durante scroll
- [ ] Seek de vídeo: < 100ms latency
- [ ] Memória estável (sem crescimento contínuo)

### Código
- [ ] Cobertura de testes > 60%
- [ ] Zero warnings do Kotlin/Compose
- [ ] Detekt passando
- [ ] Documentação KDoc em funções públicas

---

## 🚨 Plano de Rollback

### Se algo quebrar na FASE 2:
```bash
git revert HEAD  # Remove ViewModel novo
# Código antigo ainda funciona, estava paralelo
```

### Se algo quebrar na FASE 3 ou 4:
1. Manter branch `feature/refatoracao-timeline` separada
2. Feature flag: `USAR_EDITOR_NOVO = false` no código
3. Voltar para implementação antiga instantaneamente

---

## 📊 Métricas de Sucesso

| Métrica | Antes | Atual (Pós Fase 2) | Alvo | Como medir |
|---------|-------|-------------------|------|------------|
| Linhas de código | ~2500 | ~3200 (+ViewModel) | ~1500 | `find . -name "*.kt" -exec wc -l {} +` |
| Arquivos modelo | 3 (duplicados) | 5 (unificados) | 5 | Contagem |
| ViewModels timeline | 1 (antigo) | 2 (antigo + novo) | 1 (novo) | Contagem |
| Recompositions/scroll | 30-50 | 30-50 | 5-10 | Layout Inspector |
| FPS em scroll | 30-45 | 30-45 | 55-60 | Profile GPU |

---

## 🎯 Próximos Passos

1. ✅ **CONCLUÍDO:** Modelos de estado consolidados (Fase 1)
2. ✅ **CONCLUÍDO:** ViewModel consolidado (Fase 2)
3. **PRÓXIMO:** Extrair componentes puros (Fase 3)
   - VideoPreview.kt
   - TimelineScrubber.kt
   - RangeOverlay.kt
   - PlayheadIndicator.kt
   - FabRangeController.kt
4. Depois: Integrar na EditorScreen (Fase 4)
5. Depois: Otimizar performance (Fase 5)

---

*Documento atualizado em: 2026-01-30*
*Versão: 1.1*
*Autor: Assistente Claude*
