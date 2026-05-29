# ChopCut - Regras da Arquitetura

> Documento de referência rápida para manter o projeto funcionando sem quebrar.

## 🏗️ Estrutura de Arquivos

> **Inventário ao vivo (contagem de arquivos/tipos/funções):** [STRUCTURE.generated.md](./STRUCTURE.generated.md) — gerado automaticamente por `gradle/scripts/scan-structure.sh` no commit. **Não conte arquivos na mão; confira o gerado.**
>
> O mapa abaixo é a referência *curada* (o que cada arquivo é PARA) e o "onde adicionar cada coisa". Mudou um arquivo? O inventário gerado se atualiza sozinho; só ajuste este mapa se o *propósito* de um arquivo mudar. Para análise de símbolos/dependências use o **CodeGraph**.

```
com.chopcut/                          # package único para todos os arquivos
├── ChopCutApplication.kt             # Application class
├── MainActivity.kt                   # Entry point, instanciador do NavGraph
├── ThumbnailConfig.kt                # Configurações de dimensão e presets de miniaturas
├── CompressionLevel.kt               # Níveis de compressão de qualidade de vídeo
├── core/
│   ├── Models.kt                     # VideoInfo, VideoRange, TimeRange, SizePreset, etc.
│   ├── Utils.kt                      # TimeFormatter, FormatUtils, FileNameUtils, RangeUtils, etc.
│   ├── Theme.kt                      # Cores, tipografia, tema Material3, animações
│   └── Errors.kt                     # ErrorHandler, ChopCutException, ErrorResult
├── data/
│   ├── ThumbnailExtraction.kt        # ThumbnailExtraction, ExtractionProgressState, ExtractionResult
│   └── VideoEngine.kt                # TransformerPipeline, CopyPipeline, VideoRepository
├── ui/
│   ├── SharedComponents.kt           # Botões, FABs, Cards, Loading, ErrorState, Overlays
│   ├── home/
│   │   └── HomeFeature.kt            # HomeScreen + HomeViewModel + BottomSheetGallery
│   ├── editor/
│   │   └── TimelineFeature.kt        # TimelineScreen + TimelineViewModel + Timeline Canvas
│   └── navigation/
│       └── ChopCutNavGraph.kt        # NavHost com rotas home e editor
```

## ⚠️ Regras Críticas

### 1. Package é sempre `com.chopcut`

Todos os arquivos usam `package com.chopcut`. Sem subpacotes. Isso significa que **não há imports entre arquivos internos** — tudo no mesmo package se enxerga automaticamente.

```kotlin
// ✅ CERTO - package único
package com.chopcut

// ✅ CERTO - referência direta sem import
val info = VideoInfo(...)    // definido em core/Models.kt
ErrorState(...)              // definido em ui/SharedComponents.kt
```

### 2. Só adicione código novo dentro dos arquivos existentes

Qualquer nova funcionalidade deve ser adicionada a um dos arquivos existentes. Não crie novos arquivos .kt (a contagem atual está em [STRUCTURE.generated.md](./STRUCTURE.generated.md)).

| Se for... | Adicione em... |
|-----------|---------------|
| Modelo de dados | `core/Models.kt` |
| Função utilitária | `core/Utils.kt` |
| Componente UI reutilizável | `ui/SharedComponents.kt` |
| Tela/ViewModel do editor | `ui/editor/TimelineFeature.kt` |
| Tela inicial | `ui/home/HomeFeature.kt` |

### 3. TimelineViewModel é Activity-scoped / Route-scoped

Instanciado na navegação ou resgatado na navegação do Compose de acordo com as necessidades.

### 4. Não duplique nomes de classes/objetos

Como tudo está no mesmo package, **não pode haver duas classes, objetos ou enums com o mesmo nome**.

Conflitos conhecidos e resolvidos:

| Nome | Onde está | Uso |
|------|-----------|-----|
| `ExtractionStage` | `core/Models.kt` | Estágios de thumbnails: `DECODE, PROCESS, SAVE` (telemetria do teste) |
| `VideoInfo` | `core/Models.kt` | Metadata de vídeo |

### 5. Build e Execução via `make` (ou `./gradle-menu`)

O caminho canônico são os atalhos do `Makefile`, que já exporta o `JAVA_HOME=./jdk17` do projeto:

```bash
make build      # APK debug (assembleDebug)
make install    # instala no device
make run        # instala e abre o app
make lint       # lintDebug
make test       # testes instrumentados
```

Como conveniência opcional há o `./gradle-menu` — um script bash com `select` (zero dependências) que lista as tarefas e delega ao `make`, com atalhos extras de `connect device` e `pair Wi-Fi` via adb. Para rodar `gradlew` na mão, defina o `JAVA_HOME` explicitamente:

```bash
JAVA_HOME=./jdk17 ./gradlew assembleDebug
```

> [!TIP]
> **Registro de Erros Automatizado (`errors.json`):**
> Sempre que uma compilação ou script de build falhar, o sistema grava automaticamente os detalhes da falha, a tarefa e o timestamp no arquivo `errors.json` na raiz do projeto. Isso elimina a necessidade de contagens ou anotações manuais de falhas.

### 6. Performance: 3 padrões para evitar jank

SHA-1 dos padrões (ver CLAUDE.md para detalhes completos):

1. **Nunca alocar objetos dentro do draw scope de Canvas** — `Paint()`, `Rect()`, `CornerRadius()`, etc. Use `remember` e reutilize.
2. **Flag `isScrubbing` para gestos** — Quando um StateFlow contínuo e um gesto escrevem no mesmo campo, use flag para silenciar o observer durante o gesto.
3. **Isolar animações em Canvas separado** — Animações que invalidam Canvas devem ficar em `Canvas` sobreposto, não dentro do Canvas principal.

## 🧪 Testes

```bash
# Menu interativo de tarefas (bash select, opcional)
./gradle-menu

# Ou build manual do APK debug
JAVA_HOME=./jdk17 ./gradlew assembleDebug
```

## 🔍 Localização de Componentes Comuns

| O que procura | Onde encontrar |
|---|---|
| `ErrorState`, `LoadingState`, `EmptyState` | `ui/SharedComponents.kt` |
| `ChopCutPrimaryButton`, `ChopCutSecondaryButton` | `ui/SharedComponents.kt` |
| `ChopCutTypography`, `primaryColor()`, `OnSurface`, `ErrorDark` | `core/Theme.kt` |
| `ChopCutSpacing`, `ChopCutAnimation` | `core/Theme.kt` |
| `FormatUtils`, `FileNameUtils`, `RangeUtils` | `core/Utils.kt` |
| `HomeScreen`, `HomeViewModel` | `ui/home/HomeFeature.kt` |
| `TimelineScreen`, `TimelineViewModel`, `Timeline` (Canvas) | `ui/editor/TimelineFeature.kt` |
| `ThumbnailConfig` | `ThumbnailConfig.kt` |
| `CompressionLevel` | `CompressionLevel.kt` |
| `ThumbnailExtraction`, `ExtractionProgressState`, `ExtractionResult` | `data/ThumbnailExtraction.kt` |
| `TransformerPipeline`, `CopyPipeline`, `VideoRepository` | `data/VideoEngine.kt` |
| `ErrorHandler`, `ChopCutException` | `core/Errors.kt` |
