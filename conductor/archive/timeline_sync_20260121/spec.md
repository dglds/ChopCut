# Track Spec: Sincronização e Scroll da TimelineV2

## Visão Geral
Atualmente, a `VideoTimelineV2` permanece estática durante a reprodução do vídeo. Este track visa implementar o scroll automático sincronizado com o tempo de reprodução, garantindo que a agulha (playhead) permaneça centralizada enquanto a timeline se desloca.

## Requisitos Funcionais
- **Agulha Centralizada:** A agulha de reprodução deve ficar fixa no centro horizontal da timeline.
- **Scroll Sincronizado:** A timeline deve rolar automaticamente para a esquerda conforme o vídeo avança, mantendo a correspondência exata entre a posição visual e o timestamp do vídeo.
- **Tamanho das Thumbnails:** O tamanho das miniaturas deve ser calculado de forma que a largura total da timeline represente exatamente a duração do vídeo na escala de pixels por segundo definida.
- **Padding de Extremidade:** Adicionar um `HorizontalPadding` (ou `Spacer`) equivalente a 50% da largura da tela no início e no fim da lista da timeline, para permitir que o início (0s) e o fim do vídeo alcancem o centro da tela.
- **Interação por Scrubbing:**
    - Ao tocar/arrastar a timeline, a reprodução do vídeo deve ser pausada.
    - O scroll manual do usuário deve atualizar o tempo atual do vídeo (seek).

## Requisitos Não Funcionais
- **Performance:** O scroll deve ser suave (60 FPS), utilizando `LazyListState` ou similar para gerenciar o deslocamento sem recomposições excessivas.
- **Precisão:** A posição da timeline deve ser atualizada em tempo real com base no `playbackPosition` vindo do ViewModel/Player.

## Critérios de Aceite
- [ ] O vídeo começa com a agulha no centro e o tempo 00:00 da timeline alinhado a ela.
- [ ] Ao dar Play, a timeline se move de forma fluida.
- [ ] As thumbnails mantêm o tamanho correto proporcional à escala de tempo.
- [ ] Ao terminar o vídeo, o marcador de fim da timeline está exatamente sob a agulha central.
- [ ] Arrastar a timeline pausa o vídeo e faz o "seek" para o tempo correspondente.
