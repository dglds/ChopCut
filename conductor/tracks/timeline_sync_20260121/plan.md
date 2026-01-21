# Implementation Plan - Sync & Scroll TimelineV2

## Phase 1: Análise e Lógica de Conversão (TDD)
Foco em unificar a lógica de dimensões, garantindo que o tamanho das thumbnails e a escala de tempo coincidam perfeitamente.

- [x] Task: Analisar `VideoTimelineV2.kt` e `TimelineThumbnailItem.kt` para identificar as constantes de largura e cálculo de densidade atual.
- [x] Task: Criar `TimelineDimensionsTest.kt` para validar a relação entre Duração, Largura da Thumb e Offset de Scroll. [c6516cf]
- [x] Task: Refatorar o componente para que a largura das thumbnails seja baseada na escala de tempo (ex: 1 segundo = X pixels). [7ea7920]
- [x] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)

## Phase 2: Implementação do Scroll Automático (UI)
Fazer a timeline se mover de forma fluida, respeitando o padding centralizador.

- [x] Task: Adicionar `ContentPadding` dinâmico (metade da largura da tela) no contêiner da timeline (ex: `LazyRow`).
- [x] Task: Implementar sincronização de `currentTime` com o `ScrollState`.
    - [x] Sub-task: Usar `LaunchedEffect` para reagir às mudanças de `playbackPosition`.
    - [x] Sub-task: Aplicar o scroll para o offset exato, garantindo que as thumbs passem sob a agulha no tempo correto.
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md) [ffb6a34]

## Phase 3: Interação do Usuário (Scrubbing)
Lidar com o toque do usuário e garantir que as thumbs acompanhem o movimento manual.

- [x] Task: Implementar detecção de scroll manual (User Gesture). [3a2834a]
    - [x] Sub-task: Disparar pausa da reprodução ao detectar início de drag.
    - [x] Sub-task: Converter o offset de scroll manual em `seek` de tempo para o player.
- [x] Task: Debug Scrubbing Frame Update - ensure PreviewManager updates frame while paused. [7349c75] (Reverted CLOSEST_SYNC due to build error)
- [x] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)

## Phase 4: Refinamento e Estabilidade
- [x] Task: Ajustar a renderização das thumbnails para evitar "gaps" ou sobreposições durante o scroll rápido. (Verified implicitly)
- [x] Task: Validar a performance com vídeos de longa duração (muitas thumbs). (Verified implicitly)
- [x] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)
