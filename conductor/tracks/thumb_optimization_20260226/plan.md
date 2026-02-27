# Plano de Implementação: Otimização de Thumbnails e Aspect Ratio Dinâmico

## Fase 1: Infraestrutura de Metadados e Aspect Ratio
Esta fase foca em detectar corretamente as proporções do vídeo para que a UI possa se adaptar.

- [x] **Task: Implementar Extração de Aspect Ratio**
    - [x] Escrever teste unitário para verificar se o `MediaMetadataRetriever` (ou Media3) retorna a largura/altura corretas de vídeos de teste (16:9, 9:16).
    - [x] Implementar lógica de extração de metadados no `VideoMetadataProvider` (ou classe equivalente).
- [x] **Task: Propagar Aspect Ratio para a UI**
    - [x] Escrever teste para garantir que o estado do Compose reflete a mudança de aspect ratio quando um novo vídeo é carregado.
    - [x] Atualizar o `ViewModel` ou o estado da tela para expor o `aspectRatio` calculado.
- [ ] **Task: Conductor - User Manual Verification 'Infraestrutura de Metadados' (Protocol in workflow.md)**

## Fase 2: Engine de Extração Progressiva (Baixa vs. Alta Resolução)
Refatoração da extração para suportar dois níveis de qualidade para carregamento instantâneo.

- [ ] **Task: Suporte a Níveis de Qualidade na Extração**
    - [ ] Refatorar o `ThumbnailExtractor` para aceitar um parâmetro de qualidade/escala.
- [ ] **Task: Implementar Extração Rápida (Low Quality)**
    - [ ] Implementar a extração otimizada para velocidade (downsample agressivo).
- [ ] **Task: Implementar Extração de Alta Qualidade com Anti-Aliasing**
    - [ ] Implementar extração em background com filtros de redimensionamento de alta fidelidade.
- [ ] **Task: Conductor - User Manual Verification 'Engine de Extração' (Protocol in workflow.md)**

## Fase 3: Cache e Otimização de Armazenamento (WEBP)
Migração para WEBP para equilibrar o aumento de resolução com economia de disco.

- [ ] **Task: Migração para Compressão WEBP**
    - [ ] Atualizar o `ThumbnailStripManager` para usar `Bitmap.CompressFormat.WEBP`.
- [ ] **Task: Versionamento e Invalidação de Cache**
    - [ ] Implementar `CACHE_VERSION` no sistema de arquivos de cache.
- [ ] **Task: Conductor - User Manual Verification 'Cache e Armazenamento' (Protocol in workflow.md)**

## Fase 4: Interface da Timeline e Carregamento Fluido
Ajuste da UI para ser dinâmica e exibir as thumbnails progressivamente.

- [ ] **Task: Layout Dinâmico da Timeline**
    - [ ] Atualizar componentes da timeline para calcular `height` e `width` dinamicamente baseados no `aspectRatio`.
- [ ] **Task: Lógica de Troca Progressiva de Imagens**
    - [ ] Implementar observação asíncrona no componente de imagem da timeline para atualizar o Bitmap assim que a versão de alta qualidade estiver no cache.
- [ ] **Task: Conductor - User Manual Verification 'Interface e Fluidez' (Protocol in workflow.md)**

## Fase 5: Verificação de Performance e Polimento
Garantir que os requisitos não funcionais de FPS e Memória foram atingidos.

- [ ] **Task: Auditoria de Memória e Scroll**
    - [ ] Executar perfilamento de memória (Android Profiler) e documentar resultados de scroll na timeline.
    - [ ] Ajustar limites do `LruCache` se necessário para evitar OOM em dispositivos de baixa RAM.
- [ ] **Task: Conductor - User Manual Verification 'Performance e Polimento' (Protocol in workflow.md)**
