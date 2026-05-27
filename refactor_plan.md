# Plano de Refatoração do ChopCut (Arquitetura em 15 Arquivos)

Este documento detalha o plano de refatoração para simplificar a estrutura do projeto ChopCut, reduzindo de cerca de 140 arquivos para ~15 arquivos baseados em **Domínios (Features)**. A refatoração visa melhorar a velocidade de build e simplificar a manutenção sem criar um monólito instável.

> [!WARNING]
> Devido às dificuldades com importações resolvidas incorretamente na compilação do Kotlin, a refatoração deve aplicar os passos na ordem exata definida aqui para evitar falhas de inferência de tipos.

## Objetivo
Unir múltiplos arquivos pequenos em grandes arquivos consolidados, categorizados por funcionalidade lógica (ex: `Core`, `VideoEngine`, `EditorFeature`), enquanto removemos lixo acumulado e dependências legadas.

## Passo 1: Limpeza de Código Inativo
Antes de unir qualquer coisa, removeremos completamente funcionalidades antigas ou indesejadas:
- `PreferencesScreen`, `PreferencesManager` e `PreferencesViewModel` (limpar chamadas no `HomeFeature` e `ChopCutApplication`).
- `OnboardingScreen`.
- Todos os arquivos do console de **Debug** (`ui/components/console`, `ui/components/feedback`, `ui/screen/debug`, `util/debug`).
- O `ChopCutNavGraph.kt` será limpo e atualizado para iniciar diretamente em `home`.

## Passo 2: Flattening de Pacotes (Remover sufixos)
Para evitar que as importações quebrem ao mudarmos os arquivos de pasta, transformaremos todos os pacotes em um pacote raiz:
```kotlin
package com.chopcut
```
Todas as declarações internas do tipo `com.chopcut.ui.components.player.PlayerManager` serão normalizadas via script para remover o caminho completo.

## Passo 3: Fusão por Domínios
Os arquivos do projeto serão mesclados nos seguintes destinos:

### Fundação Core
- `core/Models.kt`: Modelos de dados e áudio.
- `core/Utils.kt`: Funções utilitárias.
- `core/Theme.kt`: Temas, cores, animações e tipografia.
- `core/Errors.kt`: Classes de manipulação de erro (`ErrorResult`, `ErrorHandler`).

### Motores de Processamento (Engines)
- `data/VideoEngine.kt`: Pipeline, repositório e codecs de vídeo.
- `data/ThumbnailEngine.kt`: Cache e geração de miniaturas.
- `data/AudioEngine.kt`: Extração e processamento de áudio.

### Componentes de Interface
- `ui/SharedComponents.kt`: Botões, cards, overlays e elementos reutilizáveis.

### Features
- `ui/home/HomeFeature.kt`: Telas iniciais, viewmodels associados e galeria.
- `ui/editor/TimelineUI.kt`: Componentes da linha do tempo e Player.
- `ui/editor/TrimUI.kt`: Componentes de corte do vídeo.
- `ui/editor/WaveformUI.kt`: Renderização de áudio visual.
- `ui/editor/EditorToolsUI.kt`: Painéis de ferramentas do editor.
- `ui/editor/EditorFeature.kt`: Tela principal do editor e ViewModels (`EditorViewModel`, `AudioViewModel`).

## Passo 4: Resolução de Conflitos Finais
Durante a mesclagem, podem ocorrer alguns pequenos conflitos documentados na nossa última tentativa:
1. **Redeclarações Duplas:** Como o `ExtractionStage` e o `WaveformData` existem em vários lugares, a versão presente em `core/Models.kt` deve ser mantida, deletando as versões nos arquivos da UI.
2. **Uso de Variáveis Compartilhadas:** As chamadas à `FormatUtils` e `ThumbnailCacheManager` podem perder referências, precisando do prefixo explícito nas chamadas (ex: `ThumbnailCacheManager.getStrip`).
3. **Erros no Enum de Ferramentas:** Constantes como `NONE`, `TRIM` e `AUDIO` precisam ser explicitadas com `EditorTool.NONE`, etc.

## Ferramenta de Automação
O refactor será executado por um script Python robusto (`refactor.py`) que já está na raiz do projeto. Este script lida com a união dos arquivos e limpeza de dependências `import com.chopcut.*`. Em seguida executaremos um parser adicional para os pequenos conflitos (importações estáticas).

## Avaliação de Sucesso
- O número de arquivos no diretório `com.chopcut` reduz para menos de 20.
- Execução limpa e com sucesso através do comando `./assembledebug` configurado.
