# ChopCut — Instruções para as Próximas Sessões

**Data:** 2026-05-27
**Estado atual:** Pós-refatoração arquitetural — build passando, 20 arquivos

---

## Como começar uma sessão

### 1. Leia os documentos de referência
Sempre começe lendo estes arquivos na ordem:

- Qualquer diferennça ou dúvida confirmar com o usuário.

1. `CLAUDE.md` — Instruções gerais do projeto, padrões de performance, comandos
2. `docs/ChopCut - Regras da Arquitetura.md` — Onde cada coisa está, regras críticas
3. `docs/session#01.md` ou o último `session#NN.md` — O que foi feito na sessão anterior
4. `failedBuildCount.txt` — Quantas falhas de build desde o início

### 2. Atualize o failedBuildCount a cada falha
Sempre que um build falhar, incremente o número em `failedBuildCount.txt`.

### 3. Commits modulares
Siga o padrão de commits da Session #01:
- `refactor(escopo): mensagem` — Para refatorações
- `feat(escopo): mensagem` — Para novas funcionalidades
- `fix(escopo): mensagem` — Para correções
- `chore: mensagem` — Para tarefas gerais
- `docs: mensagem` — Para documentação

Commite em grupos lógicos e pequenos, não um único commit gigante.

### 4. No final de cada sessão
Crie um arquivo `docs/session-<session-id>.md` (onde o id pode ser qualquer identificador próprio como `session#03`):
- Identificação (nome do modelo, ou qualquer identificador de modelo de IA)
- Data e objetivo
- O que foi feito 
- Resultados (tabela antes/depois)
- Comandos úteis
- Pendências

> [!IMPORTANT]
> **Manutenção das Regras de Arquitetura:**
> Sempre que houver qualquer mudança estrutural na base de código (como criação de novos arquivos, novas rotas, novos ViewModels ou alteração de regras do projeto), **você DEVE atualizar imediatamente** o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md) para manter a documentação da arquitetura sincronizada e 100% íntegra.

---

## Regras que não podem ser violadas

### Package único
Todos os arquivos usam `package com.chopcut`. Não crie subpacotes.
Isso significa que **não existem imports entre arquivos internos** — tudo no mesmo package se enxerga automaticamente.

```kotlin
// ✅ CERTO — referência direta, sem import
ErrorState(...)
val info = VideoInfo(...)
```### Não crie novos arquivos .kt sem necessidade
Qualquer novo código deve ser adicionado a um dos 20 arquivos existentes.

| Novo código | Adicionar em |
|------------|--------------|
| Modelo de dados | `core/Models.kt` |
| Função utilitária | `core/Utils.kt` |
| Componente UI | `ui/SharedComponents.kt` |
| Editor | `ui/editor/EditorFeature.kt` |
| Timeline | `ui/editor/TimelineUI.kt` |
| Timeline V2 | `ui/editor/TimelineV2Feature.kt` |
| Corte | `ui/editor/TrimUI.kt` |
| Áudio/Waveform | `ui/editor/WaveformUI.kt` |
| Ferramentas | `ui/editor/EditorToolsUI.kt` |
| Tela inicial | `ui/home/HomeFeature.kt` |

### Nomes únicos
Como tudo está no mesmo package, não pode haver duas classes, objetos ou enums com o mesmo nome. Conflitos conhecidos:

| Nome | Onde está | Valores |
|------|-----------|---------|
| `ExtractionStage` | `core/Models.kt` | `DECODE, PROCESS, SAVE` |
| `PreloadStage` | `ui/home/HomeFeature.kt` | `Starting, Validating, ExtractingAudio, ExtractingThumbnails, Ready` |
| `WaveformData` | `core/Models.kt` | 2 params: `(amplitudes, durationMs)` |

### Build sempre com `./scripts/assembledebug`
O script na pasta `scripts/` configura `JAVA_HOME=jdk17` automaticamente.
Não use `./gradlew assembleDebug` diretamente ou falhará com Java 25 do sistema.

---## Arquitetura atual (20 arquivos)

```
app/src/main/java/com/chopcut/
├── ChopCutApplication.kt
├── MainActivity.kt
├── core/
│   ├── Errors.kt
│   ├── Models.kt
│   ├── Theme.kt
│   └── Utils.kt
├── data/
│   ├── AudioEngine.kt
│   ├── ThumbnailEngine.kt
│   └── VideoEngine.kt
├── graphics/
│   ├── egl/SurfaceBridge.kt
│   └── gl/GLRenderer.kt
├── ui/
│   ├── SharedComponents.kt
│   ├── home/HomeFeature.kt
│   ├── editor/EditorFeature.kt
│   ├── editor/EditorToolsUI.kt
│   ├── editor/TimelineUI.kt
│   ├── editor/TimelineV2Feature.kt
│   ├── editor/TrimUI.kt
│   ├── editor/WaveformUI.kt
│   └── navigation/ChopCutNavGraph.kt
```

---

## Roadmap de pendências

### Prioridade alta
- [ ] Corrigir 4 warnings de depreciação no build
- [ ] Verificar se `connectedAndroidTest` funciona

### Prioridade média
- [ ] Verificar performance com vídeos longos (>30 min)
- [ ] Testar em dispositivo mid-range (sem jank)

### Prioridade baixa
- [ ] Remover `refactor.py` e `refactor_plan.md` após confirmar estabilidade
- [ ] Adicionar `.gitignore` para `efeitos-sonoros-brasil-sil-sil-rede-globo.mp3`

---

## Comandos

```bash
./scripts/assembledebug                      # Build APK debug
./gradlew installDebug                       # Instalar no device
./gradlew connectedAndroidTest               # Rodar todos os testes
./gradlew connectedAndroidTest -Pclass=...   # Teste específico
~/Android/Sdk/platform-tools/adb logcat -s Timber:D  # Ver logs
```
