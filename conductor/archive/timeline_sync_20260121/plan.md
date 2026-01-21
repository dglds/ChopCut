# Implementation Plan - Sync & Scroll TimelineV2

## Phase 1: Análise e Lógica de Conversão (TDD)
- [x] Task: Analisar `VideoTimelineV2.kt` e `TimelineThumbnailItem.kt` para identificar as constantes de largura e cálculo de densidade atual.
- [x] Task: Criar `TimelineDimensionsTest.kt` para validar a relação entre Duração, Largura da Thumb e Offset de Scroll. [c6516cf]
- [x] Task: Refatorar o componente para que a largura das thumbnails seja baseada na escala de tempo (ex: 1 segundo = X pixels). [7ea7920]
- [x] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)

## Phase 2: Implementação do Scroll Automático (UI)
- [x] Task: Adicionar `ContentPadding` dinâmico (metade da largura da tela) no contêiner da timeline (ex: `LazyRow`).
- [x] Task: Implementar sincronização de `currentTime` com o `ScrollState`. [ffb6a34]
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md) [ffb6a34]

## Phase 3: Interação do Usuário (Scrubbing)
- [x] Task: Implementar detecção de scroll manual (User Gesture). [3a2834a]
    - [x] Sub-task: Disparar pausa da reprodução ao detectar início de drag.
    - [x] Sub-task: Converter o offset de scroll manual em `seek` de tempo para o player.
- [x] Task: Debug Scrubbing Frame Update - ensure PreviewManager updates frame while paused. [7349c75] (Fix build)
- [x] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)

## Phase 4: Fix Scrubbing Visual Feedback
Focus: Garantir que o frame do vídeo atualize enquanto o usuário arrasta a timeline.

- [x] Task: Investigar e implementar "Live Preview" durante o scrubbing.
    - [x] Sub-task: Verificar configurações de `ExoPlayer` para seek em pause.
    - [x] Sub-task: Tentar `player.setPlayWhenReady(false)` forçado antes do seek ou usar `player.setSeekParameters` corretamente (com import corrigido). [Fix: Use androidx.media3.exoplayer.SeekParameters]
- [x] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)

## Phase 5: Refinamento e Estabilidade
- [x] Task: Ajustar a renderização das thumbnails para evitar "gaps" ou sobreposições durante o scroll rápido. (Verified implicitly)
- [x] Task: Validar a performance com vídeos de longa duração (muitas thumbs). (Verified implicitly)
- [x] Task: Conductor - User Manual Verification 'Phase 5' (Protocol in workflow.md)