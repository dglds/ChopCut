# Scripts de Monitoramento de Performance da Timeline

Este diretório contém uma coleção de scripts `bash` projetados para ajudar no monitoramento e diagnóstico de performance do componente de timeline de thumbnails no aplicativo ChopCut. Eles filtram o `logcat` do Android para exibir informações relevantes de extração, cache e renderização.

## Como usar

1.  **Conecte seu dispositivo:** Certifique-se de que seu dispositivo Android esteja conectado e reconhecido pelo ADB. Você pode verificar com `adb devices`.
2.  **Limpe o logcat:** Antes de executar qualquer script, é uma boa prática limpar o `logcat` para garantir que você está vendo apenas os logs da sua sessão de monitoramento:
    ```bash
    adb logcat -c
    ```
3.  **Execute o script desejado:** Navegue até este diretório no seu terminal e execute o script.
    ```bash
    cd scripts/monitoring
    ./<nome_do_script>.sh
    ```
4.  **Interaja com o app:** Utilize o aplicativo, especialmente a timeline e as funcionalidades de carregamento de vídeo, para gerar os logs monitorados.
5.  **Pare o monitoramento:** Pressione `Ctrl+C` no terminal para parar a execução do script.

---

## Scripts Disponíveis

### 1. `monitor-timeline-performance.sh`

-   **Descrição:** Monitora a performance de renderização do componente `TimelineEditor`. Exibe métricas como FPS estimado, número de *draw calls* por frame e tempo de renderização de cada frame do scroll.
-   **Foco:** Otimização de UI e fluidez do scroll.
-   **Tags ADB:** `TimelineEditor:I`
-   **Exemplo de uso:**
    ```bash
    ./monitor-timeline-performance.sh
    ```
-   **Exemplo de saída:**
    ```
    🚀 Iniciando monitoramento de performance da TimelineEditor...
        Faça scroll na timeline do app para ver as métricas de FPS, draw calls e tempo de frame.
        Pressione Ctrl+C para parar.
    ------------------------------------------------------------------
    03-02 10:48:40.045 6098 6098 I TimelineEditor: TIMELINE PERFORMANCE LOG (Frame #1)
    03-02 10:48:40.045 6098 6098 I TimelineEditor: ─────────────────────────────────────────────────────────────────
    03-02 10:48:40.045 6098 6098 I TimelineEditor: 📊 MÉTRICAS DO FRAME:
    03-02 10:48:40.045 6098 6098 I TimelineEditor: • Draw calls: 10
    03-02 10:48:40.045 6098 6098 I TimelineEditor: • Strips visíveis: 5
    03-02 10:48:40.045 6098 6098 I TimelineEditor: • Frame time: 15ms
    03-02 10:48:40.045 6098 6098 I TimelineEditor: • FPS estimado: 66.66 fps
    ...
    ```

### 2. `monitor-extraction-pipeline.sh`

-   **Descrição:** Rastreia o fluxo de extração de thumbnails, desde o `ThumbnailExtractorBatch` (extração de frames) até o `ThumbnailStripManager` (criação de strips). Ajuda a identificar gargalos na geração de novas imagens.
-   **Foco:** Eficiência da lógica de extração.
-   **Tags ADB:** `ThumbnailExtractorBatch:I`, `ThumbnailStripManager:I`
-   **Exemplo de uso:**
    ```bash
    ./monitor-extraction-pipeline.sh
    ```
-   **Exemplo de saída:**
    ```
    🚀 Iniciando monitoramento do pipeline de extração de thumbnails...
        Selecione um vídeo para analisar os tempos de extração e criação de strips.
        Pressione Ctrl+C para parar.
    ------------------------------------------------------------------
    03-02 10:48:58.541 6098 6291 D ThumbnailExtractorBatch: === ThumbnailExtractorBatch.extractBatch STARTED ===
    03-02 10:48:58.541 6098 6291 D ThumbnailExtractorBatch: uri: content://media/external/video/media/1000041099
    03-02 10:48:58.625 6098 6291 D ThumbnailExtractorBatch: Extracting frame at 1000ms
    03-02 10:48:58.765 6098 6291 I ThumbnailExtractorBatch: extractBatch: Completed 10 thumbnails in 200ms (avg 20ms/frame)
    03-02 10:48:59.008 6098 6291 D ThumbnailStripManager: ThumbnailStrip: Segment 0 (10 frames, 1680x168, RGB_565) - BATCH MODE
    ...
    ```

### 3. `monitor-cache-performance.sh`

-   **Descrição:** Audita o desempenho do `ThumbnailCacheManager`, registrando hits e misses para o cache em memória (LRU) e em disco. Essencial para validar a eficácia da estratégia de cache e evitar re-extrações desnecessárias.
-   **Foco:** Eficiência do gerenciamento de cache.
-   **Tags ADB:** `ThumbnailCacheManager:D`, `ThumbnailStripManager:I`
-   **Exemplo de uso:**
    ```bash
    ./monitor-cache-performance.sh
    ```
-   **Exemplo de saída:**
    ```
    🚀 Iniciando monitoramento de performance do cache...
        Navegue pela timeline para analisar os hits e misses do cache.
        Pressione Ctrl+C para parar.
    ------------------------------------------------------------------
    03-02 10:49:05.120 6098 6291 D ThumbnailCacheManager: Cache HIT (bitmap válido) for segment 5
    03-02 10:49:05.177 6098 6291 D ThumbnailCacheManager: Cache MISS for segment 6, will extract
    03-02 10:49:05.516 6098 6291 D ThumbnailStripManager: ThumbnailStrip: CACHE HIT - Segment 7 loaded from disk
    ...
    ```

### 4. `monitor-full-flow.sh`

-   **Descrição:** Um script de monitoramento abrangente que combina os logs mais importantes de todos os componentes (`TimelineEditor`, `ThumbnailExtractorBatch`, `ThumbnailStripManager`, `ThumbnailCacheManager`, `ThumbnailViewModel`). Oferece uma visão de ponta a ponta do ciclo de vida de uma thumbnail, desde a solicitação inicial até a exibição na UI.
-   **Foco:** Visão holística e diagnóstico integrado.
-   **Tags ADB:** `TimelineEditor:I`, `ThumbnailExtractorBatch:I`, `ThumbnailStripManager:I`, `ThumbnailCacheManager:D`, `ThumbnailViewModel:D`
-   **Exemplo de uso:**
    ```bash
    ./monitor-full-flow.sh
    ```
-   **Exemplo de saída:** (Combinação das saídas dos scripts acima)
    ```
    🚀 Iniciando monitoramento do fluxo completo de thumbnails (End-to-End)...
        Use a timeline para rastrear o ciclo de vida de uma thumbnail.
        Pressione Ctrl+C para parar.
    ------------------------------------------------------------------
    03-02 10:49:05.120 6098 6291 D ThumbnailCacheManager: Cache HIT (bitmap válido) for segment 5
    03-02 10:49:05.177 6098 6291 D ThumbnailCacheManager: Cache MISS for segment 6, will extract
    03-02 10:49:05.516 6098 6291 D ThumbnailStripManager: ThumbnailStrip: CACHE HIT - Segment 7 loaded from disk
    03-02 10:49:05.857 6098 6291 D ThumbnailExtractorBatch: === ThumbnailExtractorBatch.extractBatch STARTED ===
    03-02 10:49:05.857 6098 6291 I TimelineEditor: TIMELINE PERFORMANCE LOG (Frame #5)
    ...
    ```
