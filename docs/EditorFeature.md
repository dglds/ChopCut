# Análise de Código: EditorFeature.kt

## Visão Geral
O arquivo `EditorFeature.kt` representa o verdadeiro "Motor" (Core App Experience) do ChopCut. É o maior e mais denso arquivo do projeto, e orquestra toda a suíte de edição não linear do usuário, contendo todas as dependências visuais, lógicas de mídia temporais e players em um super-módulo.

## Responsabilidades

### 1. ViewModels e Arquitetura do Estado (MVI)
- **`EditorViewModel` e `EditorState`**: Controla tudo, desde as ações de corte (trimming), divisão de clipes e estado macro do player.
- **ViewModels Específicas**: `TimelineViewModel` gerencia os zooms e tempo atual. `ThumbnailViewModel` orquestra quadros cacheáveis. `AudioViewModel` foca na análise de dB em tempo real.

### 2. UI e Layout do Editor
- **`EditorScreen`**: A interface pai, reunindo a viewport superior (Player) e a timeline inferior, com drag/drop limits e states.
- **Painéis (Tool Panels)**: `FormatToolPanel`, `TrimToolPanel`, entre outras réguas de ferramentas, aparecem contextualmente no bottom do app.

### 3. Engine de Thumbnails (Extração Caching)
- Classes de extração de altíssimo desempenho (`FastFrameExtractor`, `ThumbnailCacheManager`) leem um vídeo local frame-a-frame de maneira assíncrona, jogando os quadros cacheados de volta à Timeline para que o usuário veja a pista ao scrolar.

### 4. Engine de Áudio e Formas de Onda (Waveform)
- Inclui desde lógicas brutas (AudioFormat, WaveformAnalyzer) até seus equivalentes em Jetpack Compose Canvas (`WaveformRenderer`, `AudioWaveForms`), renderizando as frequências sonoras perfeitamente acopladas ao código de tempo (timecode) do player, para corte fino.

### 5. Timeline e Player (Recorte - Trim)
- Controles de reprodução encapsulam o ExoPlayer (`ExoPlayerV2`, `PlayerManager`).
- Os componentes de timeline lidam com multitoque, sliders com alças de trim (corte), e detecção fina de posição.

## Vantagens
Tudo que o Editor faz ou precisa vive aqui. Essa localização de domínios (Domain Cohesion) garante que mexer na lógica da timeline não esbarre nas definições de Home ou Preferências de App. Desenvolvedores visualizam o fluxo inteiro em um Outline unificado sem saltar em 50 arquivos.
