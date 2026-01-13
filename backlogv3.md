# ChopCut - Backlog de Desenvolvimento

## Visão Geral

Transformar o ChopCut em um app profissional de edição de vídeo com foco principal em sistema de projetos completo.

## Estado Atual (Baseline)

- ✅ UI completa com navegação (Home, Editor, Settings, Test)
- ✅ Video preview com ExoPlayer
- ✅ Timeline com thumbnails
- ✅ Waveform de áudio
- ✅ Export em background (ForegroundService + WorkManager)
- ✅ Pipelines: CopyPipeline (trim/concat), TranscodePipeline (resize/crop/rotation)
- ✅ Extração de áudio e thumbnails
- ✅ Sistema de erros estruturado
- ✅ Material 3 design

---

## FASE 1: Sistema de Projetos (Prioridade #1)

### 1.1 Modelo de Dados de Projeto

Arquivo: `app/src/main/java/com/chopcut/data/model/Project.kt`
- Criar modelo Project contendo:
  - id: String (UUID)
  - name: String
  - createdAt: Long
  - modifiedAt: Long
  - sourceVideoUri: String
  - duration: Long
  - thumbnail: String? (path para thumbnail)
  - exportConfig: ExportConfig
  - edits: List<EditOperation> (stack de operações)
  - exportPath: String? (última exportação)

### 1.2 Operações de Edição (Undo/Redo)

Arquivo: `app/src/main/java/com/chopcut/data/model/EditOperation.kt`
- Criar sealed interface EditOperation
  - TrimEdit(startTime: Long, endTime: Long)
  - CropEdit(rect: Rect)
  - RotationEdit(degrees: Int)
  - ResizeEdit(width: Int, height: Int)
  - FilterEdit(filterType: FilterType, intensity: Float)
  - SpeedEdit(speed: Float)
  - VolumeEdit(volume: Float)
- Criar EditStack para gerenciar undo/redo

### 1.3 Repository de Projetos

Arquivo: `app/src/main/java/com/chopcut/data/repository/ProjectRepository.kt`
- CRUD de projetos
- Listar projetos (ordenados por modifiedAt)
- Salvar projeto (overwrite)
- Deletar projeto
- Exportar/importar projeto (backup)

### 1.4 Persistência Local

Arquivo: `app/src/main/java/com/chopcut/data/local/ProjectDatabase.kt`
- Room database para projetos
- Tabela projects com todos os metadados
- Tabela edit_operations para histórico de edições
- DAOs para queries

### 1.5 Tela de Projetos

Arquivo: `app/src/main/java/com/chopcut/ui/project/ProjectsScreen.kt`
- Grid de projetos com thumbnails
- Informações: nome, data de modificação, duração
- Opções: abrir, renomear, duplicar, deletar
- FAB para novo projeto
- Search e filtros

### 1.6 ViewModel de Projetos

Arquivo: `app/src/main/java/com/chopcut/ui/project/ProjectsViewModel.kt`
- Carregar lista de projetos
- Gerenciar ações (delete, rename, duplicate)
- Navegação para editor

### 1.7 Integração com Editor

Arquivos existentes: `EditorViewModel.kt`, `EditorScreen.kt`
- Carregar estado do projeto ao abrir
- Auto-save periódico (debounce)
- Indicador de "não salvo"
- Botão de save manual

---

## FASE 2: Undo/Redo e Preview em Tempo Real

### 2.1 Sistema de Undo/Redo

Arquivo: `app/src/main/java/com/chopcut/data/undo/UndoManager.kt`
- Gerenciar stack de operações
- Comandos: undo(), redo(), canUndo(), canRedo()
- Limitar tamanho do stack (configurável)
- Persistir stack no projeto

### 2.2 Preview em Tempo Real

Arquivos: `EditorViewModel.kt`, `VideoPreview.kt`
- Pipeline de preview rápido (baixa resolução)
- Cache de frames processados
- Atualização ao modificar parâmetros
- Toggle preview original/editado

### 2.3 Controles de Undo/Redo na UI

Arquivo: `EditorScreen.kt`
- Botões na toolbar
- Keyboard shortcuts (quando disponível)
- Indicador visual de modificações pendentes

---

## FASE 3: Presets de Exportação

### 3.1 Modelo de Presets

Arquivo: `app/src/main/java/com/chopcut/data/model/ExportPreset.kt`
- Presets nativos:
  - Instagram Reels (1080x1920, 30fps, H.264)
  - YouTube (1080p, 30fps, H.264)
  - TikTok (1080x1920, 30fps, H.264)
  - Twitter (720p, 30fps, H.264)
  - WhatsApp (720p, 30fps, H.264)
- Presets customizados pelo usuário

### 3.2 Repository de Presets

Arquivo: `app/src/main/java/com/chopcut/data/repository/PresetRepository.kt`
- Presets padrão (read-only)
- Presets do usuário (CRUD)
- Exportar/importar presets

### 3.3 UI de Seleção de Preset

Arquivo: `app/src/main/java/com/chopcut/ui/export/ExportDialog.kt`
- Lista de presets com preview das configs
- Criar preset customizado
- Editar preset existente
- Mostrar tamanho estimado do arquivo

---

## FASE 4: Ferramentas de Edição Avançadas

### 4.1 Sistema de Filtros

Arquivos:
- `app/src/main/java/com/chopcut/data/filter/VideoFilter.kt`
- `app/src/main/java/com/chopcut/ui/filter/FilterScreen.kt`
- Filtros básicos (OpenGL ES) - escopo aprovado: 5-6 filtros:
  - None, Grayscale, Sepia, Brightness, Contrast, Saturation
- Intensidade ajustável
- Preview em tempo real

### 4.2 Ajustes de Velocidade

Arquivo: `app/src/main/java/com/chopcut/data/pipeline/SpeedPipeline.kt`
- Slow motion (0.25x - 0.75x)
- Normal (1x)
- Fast forward (1.25x - 4x)
- Reverse (reprodução reversa)

### 4.3 Ajustes de Áudio

Arquivo: `app/src/main/java/com/chopcut/data/pipeline/AudioPipeline.kt`
- Volume (0-200%)
- Fade in/out
- Mute
- Extração de áudio (já existe, integrar no editor)

### 4.4 Transições

Arquivo: `app/src/main/java/com/chopcut/data/transition/Transition.kt`
- Fade in/out
- Dissolve
- Wipe (horizontal, vertical)
- (Futuro: Mais transições complexas)

---

## FASE 5: Compartilhamento e Integrações

### 5.1 Compartilhamento Direto

Arquivo: `app/src/main/java/com/chopcut/ui/share/ShareManager.kt`
- Intent ACTION_SEND para apps
- Compartilhar para Instagram/TikTok/YouTube (quando possível)
- Compartilhar para outros apps do ChopCut (futuro)

### 5.2 Notificações de Export Completo

Arquivo: `ExportNotificationManager.kt` (já existe, estender)
- Botão de compartilhar na notificação
- Botão de abrir no player

### 5.3 Informações de Export

Arquivo: `app/src/main/java/com/chopcut/ui/export/ExportResultScreen.kt`
- Tela de sucesso com:
  - Preview do resultado
  - Informações (tamanho, duração, codec)
  - Ações: compartilhar, abrir, deletar, voltar ao editor

---

## FASE 6: Polimento e UX

### 6.1 Onboarding

Arquivo: `app/src/main/java/com/chopcut/ui/onboarding/OnboardingScreen.kt`
- Primeira vez: tutorial das funcionalidades
- Screens: bem-vindo, selecionar vídeo, editar, exportar

### 6.2 Melhorias na UX do Editor

Arquivo: `EditorScreen.kt`
- Snapping na timeline (a cada segundo)
- Zoom na timeline
- Marcadores (in/out points)
- Atalhos gestuais

### 6.3 Indicadores de Progresso

Arquivos: vários
- Skeleton loading
- Progress indicators consistentes
- Cancelamento de operações longas

---

## FASE 7: Testes e Qualidade

### 7.1 Testes Unitários

- ViewModels
- Repository
- Pipelines (mock MediaExtractor/MediaMuxer)

### 7.2 Testes de UI

- Compose UI tests
- Navigation tests

### 7.3 Testes de Integração

- Fluxo completo: criar projeto, editar, exportar
- Export em background

---

## Ordem de Implementação Sugerida

### Sprint 1 (Fundação do Sistema de Projetos) - ✅ CONCLUÍDO

- ✅ Modelo de Project (`Project.kt`)
- ✅ Room database (`ProjectDatabase`, `ProjectDao`, `EditOperationDao`)
- ✅ ProjectRepository com suporte a CRUD
- ✅ ProjectsScreen (Tela Inicial) com grid de projetos
- ✅ Integração básica com Editor (save/load project)
- ✅ **Extra:** Integração de Waveform na tela de edição
- ✅ **Extra:** Botões de operações de teste (Trim, Rotate, Resize, Crop)
- ✅ **Extra:** Ajuste no salvamento para pasta raiz `ChopCut` (com fallback)

### Sprint 2 (Undo/Redo + Auto-save) - ✅ CONCLUÍDO

- ✅ Sistema de Undo/Redo (`UndoManager`)
- ✅ Integração de Undo/Redo no Editor (ViewModel + UI)
- ✅ Auto-save com debounce (3 segundos)
- ✅ Indicador de status de salvamento (Salvo/Salvando/Não salvo)
- ✅ **Extra:** Correção crítica na persistência de projetos (cópia de vídeo para armazenamento interno)
- ✅ **Extra:** Melhorias na UI do Editor (Layout reorganizado, ícones, scroll)
- ✅ **Extra:** Melhorias na Tela de Projetos (Thumbnails reais, novo design de card)
- ✅ **Extra:** Ajustes de usabilidade (Pausa na timeline, Toast no topo, prefixo de arquivo)

### Sprint 3 (Preview em Tempo Real)

1. Pipeline de preview rápido
2. Cache de frames
3. Integração com VideoPreview
4. Toggle original/editado

### Sprint 4 (Presets de Exportação)

1. ExportPreset model
2. PresetRepository
3. ExportDialog com presets
4. Presets customizados

### Sprint 5 (Ferramentas Básicas)

1. Filtros (grayscale, sepia, etc.)
2. Ajustes de velocidade
3. Ajustes de volume
4. Fade in/out

### Sprint 6 (Compartilhamento)

1. ShareManager
2. ExportResultScreen
3. Notificações estendidas

### Sprint 7 (Polimento)

1. Onboarding
2. Melhorias UX
3. Indicadores de progresso

---

## Arquivos Críticos a Modificar/Criar

### Novos Arquivos Principais

- `data/model/Project.kt`
- `data/model/EditOperation.kt`
- `data/model/ExportPreset.kt`
- `data/local/ProjectDatabase.kt`
- `data/repository/ProjectRepository.kt`
- `data/repository/PresetRepository.kt`
- `data/undo/UndoManager.kt`
- `data/filter/VideoFilter.kt`
- `data/pipeline/SpeedPipeline.kt`
- `data/pipeline/AudioPipeline.kt`
- `ui/project/ProjectsScreen.kt`
- `ui/project/ProjectsViewModel.kt`
- `ui/export/ExportDialog.kt`
- `ui/export/ExportResultScreen.kt`
- `ui/filter/FilterScreen.kt`
- `ui/share/ShareManager.kt`

### Arquivos a Modificar

- `ui/editor/EditorViewModel.kt` - Integrar com ProjectRepository, UndoManager
- `ui/editor/EditorScreen.kt` - Adicionar controles de undo/redo, indicadores
- `ui/navigation/AppNavigation.kt` - Adicionar rota para ProjectsScreen
- `MainActivity.kt` - Atualizar ponto de entrada

---

## Dependências Necessárias

Adicionar em `gradle/libs.versions.toml`:

```toml
[versions]
room = "2.6.1"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

---

## Critérios de Sucesso

### Sprint 1-2 (Sistema de Projetos)

- Criar novo projeto a partir de vídeo
- Salvar edições automaticamente
- Listar todos os projetos
- Reabrir projeto e continuar editando
- Undo/Redo funcionando para todas as operações

### Sprint 3-4 (Preview + Presets)

- Preview em tempo real das modificações
- Selecionar preset de exportação
- Criar preset customizado
- Exportar com preset

### Sprint 5-6 (Ferramentas + Share)

- Aplicar filtros de vídeo
- Alterar velocidade
- Ajustar volume e fade
- Compartilhar vídeo exportado

### Sprint 7 (Polimento)

- Onboarding para novos usuários
- UX refinada com feedback visual
- Todos os fluxos com loading e erros tratados

---

## Como Testar End-to-End

1. Criar projeto: Home → Selecionar vídeo → Editor → Salvar projeto
2. Reabrir projeto: Home → Projetos → Selecionar projeto
3. Editar com undo: Fazer trim → Undo → Redo → Verificar estado
4. Preview: Aplicar filtro → Ver preview em tempo real
5. Exportar: Selecionar preset → Exportar → Compartilhar

---

Próximo passo: Começar pela Sprint 1 - Fundação do Sistema de Projetos
