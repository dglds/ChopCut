# Plano de Implementação: Otimização de Thumbnails e Aspect Ratio Dinâmico

## Fase 1: Infraestrutura de Metadados e Aspect Ratio [checkpoint: 2d4c99e]
Esta fase foca em detectar corretamente as proporções do vídeo para que a UI possa se adaptar.

- [x] **Task: Implementar Extração de Aspect Ratio**
    - [x] Escrever teste unitário para verificar se o `MediaMetadataRetriever` (ou Media3) retorna a largura/altura corretas de vídeos de teste (16:9, 9:16).
    - [x] Implementar lógica de extração de metadados no `VideoMetadataProvider` (ou classe equivalente).
- [x] **Task: Propagar Aspect Ratio para a UI**
    - [x] Escrever teste para garantir que o estado do Compose reflete a mudança de aspect ratio quando um novo vídeo é carregado.
    - [x] Atualizar o `ViewModel` ou o estado da tela para expor o `aspectRatio` calculado.
- [x] **Task: Conductor - User Manual Verification 'Infraestrutura de Metadados' (Protocol in workflow.md)**

## Fase 2: Engine de Extração Progressiva (Baixa vs. Alta Resolução) [checkpoint: 21c5d0d]
Refatoração da extração para suportar dois níveis de qualidade para carregamento instantâneo.

- [x] **Task: Suporte a Níveis de Qualidade na Extração**
    - [x] Escrever teste para a nova interface de extração que solicita resoluções específicas.
    - [x] Refatorar o `ThumbnailExtractor` para aceitar um parâmetro de qualidade/escala.
- [x] **Task: Implementar Extração Rápida (Low Quality)**
    - [x] Escrever teste garantindo que a extração "Fast" é significativamente mais rápida que a padrão.
    - [x] Implementar a extração otimizada para velocidade (downsample agressivo).
- [x] **Task: Implementar Extração de Alta Qualidade com Anti-Aliasing**
    - [x] Escrever teste visual ou de integridade de bitmap para verificar a ausência de serrilhado (aliasing).
    - [x] Implementar extração em background com filtros de redimensionamento de alta fidelidade.

## Fase 3: Cache e Otimização de Armazenamento (WEBP) [checkpoint: 804f544]
Migração para WEBP para equilibrar o aumento de resolução com economia de disco.

- [x] **Task: Migração para Compressão WEBP**
    - [x] Escrever teste para validar que imagens salvas em WEBP são lidas corretamente e ocupam menos espaço que o JPEG equivalente.
    - [x] Atualizar o `ThumbnailStripManager` para usar `Bitmap.CompressFormat.WEBP`.
- [x] **Task: Versionamento e Invalidação de Cache**
    - [x] Escrever teste para garantir que o cache antigo (JPEG/RGB_565) é invalidado ou ignorado com segurança.
    - [x] Implementar `CACHE_VERSION` no sistema de arquivos de cache.
- [x] **Task: Conductor - User Manual Verification 'Cache e Armazenamento' (Protocol in workflow.md)**

## Fase 4: Interface da Timeline e Carregamento Fluido
Ajuste da UI para ser dinâmica e exibir as thumbnails progressivamente.

- [x] **Task: Layout Dinâmico da Timeline**
    - [x] Criar teste de UI (Compose Preview/Screenshot test) para verificar o layout com diferentes aspect ratios.
    - [x] Atualizar componentes da timeline para calcular `height` e `width` dinamicamente baseados no `aspectRatio`.
- [x] **Task: Lógica de Troca Progressiva de Imagens**
    - [x] Escrever teste para verificar a transição de estado entre Placeholder -> Low Res -> High Res.
    - [x] Implementar observação asíncrona no componente de imagem da timeline para atualizar o Bitmap assim que a versão de alta qualidade estiver no cache.
- [x] **Task: Conductor - User Manual Verification 'Interface e Fluidez' (Protocol in workflow.md)**

## Fase 5: Verificação de Performance e Polimento
Garantir que os requisitos não funcionais de FPS e Memória foram atingidos.

- [x] **Task: Auditoria de Memória e Scroll**
    - [x] Executar perfilamento de memória (Android Profiler) e documentar resultados de scroll na timeline.
    - [x] Ajustar limites do `LruCache` se necessário para evitar OOM em dispositivos de baixa RAM.
- [x] **Task: Conductor - User Manual Verification 'Performance e Polimento' (Protocol in workflow.md)**

## Fase 6: Correções de Distorção e Polimento de UI
Ajustes finais baseados no feedback do usuário sobre aspect ratio e centralização.

- [x] **Task: Centralização Vertical na Timeline**
    - [x] Implementar centralização vertical para vídeos 9:16 e 1:1 na timeline (48dp).
- [x] **Task: Fix de Distorção no Extrator**
    - [x] Garantir que o extrator respeite as dimensões reais do vídeo para evitar "stretching".
- [x] **Task: Indicador de Zoom nas Preferências**
    - [x] Adicionar nível de zoom (escala relativa a 1080p) na tela de Preferences.
- [x] **Task: Conductor - User Manual Verification 'Distorção e Polimento'**

## Fase 7: Sistema de Monitoramento de Aspect Ratio [checkpoint: b7ffd34]
Implementação de instrumentação detalhada para diagnosticar distorções em produção.

- [x] **Task: Instrumentação do Pipeline de Extração**
    - [x] Adicionar logs detalhados em `ThumbnailExtractorBatch` para capturar:
        - Dimensões originais do vídeo (largura x altura)
        - Rotação e orientação (portrait/landscape)
        - Aspect ratio real vs esperado vs final
        - Dimensões em cada etapa de transformação
- [x] **Task: Instrumentação da Criação de Strips**
    - [x] Adicionar logs em `ThumbnailStripManager` para monitorar:
        - Dimensões da strip horizontal completa
        - Processo de stitching frame por frame
        - Validação de dimensões esperadas vs reais
- [x] **Task: Instrumentação da Renderização**
    - [x] Adicionar logs em `TimelineEditor` para capturar:
        - SrcRect e DstRect de cada strip renderizada
        - Aspect ratios de display
        - Offsets de centralização vertical
- [x] **Task: Sistema de Monitoramento via ADB**
    - [x] Tag dedicada: `ThumbnailAspectMonitor`
    - [x] Comando para captura em tempo real com salvamento em arquivo local
