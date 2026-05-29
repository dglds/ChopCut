# ChopCut — Guia de Desenvolvimento do Claude

Este arquivo instrui o agente de IA **Claude** sobre como compilar, testar, depurar e seguir as diretrizes de design e arquitetura deste projeto.

---

## 🛠️ Comandos de Compilação e Build

O projeto utiliza um SDK do Android local e exige o **JDK 17** fornecido na pasta `./jdk17`. Sempre execute comandos configurando a variável de ambiente `JAVA_HOME=./jdk17`:

- **Compilar Código Kotlin:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
  ```
- **Gerar APK de Debug:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew assembleDebug
  ```
- **Instalar no Dispositivo/Emulador Conectado:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew installDebug
  ```

> **Skill `/rodar-app`:** sobe o ChopCut de ponta a ponta (checa device → `installDebug` → abre `com.chopcut/.MainActivity` ou deep-link de vídeo no editor → screenshot + logcat). Use para validar visualmente mudanças de UI, já que jank não é coberto por testes. A skill built-in `/run` delega para ela.

---

## 🧪 Comandos de Testes

- **Executar Testes de Instrumentação:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest
  ```
- **Executar Teste Específico:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.timeline.TimelineTest
  ```

---

## 📋 Protocolos de Sessão e Arquitetura

Antes de realizar qualquer alteração, você **DEVE** seguir o protocolo oficial de gerenciamento de sessões:

1. **Protocolo de Sessão:** consulte o arquivo [SESSION_PROTOCOL.md](file:///home/diego/Android/ChopCut/SESSION_PROTOCOL.md) para saber como iniciar, executar e finalizar sua sessão de trabalho (incluindo o preenchimento obrigatório da nota de progresso `sessions/session#NN.md`).
2. **Regras da Arquitetura:** consulte o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md) para respeitar a estrutura de 16 arquivos e a inegociável regra de **package único** (`com.chopcut`), que proíbe imports internos.
3. **Padrão de Commits:** crie commits modulares separados por escopo (ex: `refactor(editor): ...`, `fix(home): ...`, `docs: ...`).

> **Skill `/desafiar-plano`:** antes de implementar uma mudança não trivial, estressa o plano contra estas regras (16 arquivos, package único, nomes sem duplicata, padrões de Canvas) e contra o backlog da última `sessions/session#NN.md`, afinando a terminologia para os nomes canônicos do projeto.

---

## ⚡ Diretrizes Críticas de Performance

Para manter a renderização do editor suave e sem engasgos (jank):
- **Não aloque objetos** (como `Paint`, `RectF`, `Path` ou `BorderStroke`) dentro do escopo de desenho de Canvas. Aloque-os no composable e use `remember` para reutilização.
- **Isolamento de Canvas:** Mantenha animações contínuas (como o playhead do cursor de reprodução) em um Canvas sobreposto independente do Canvas estático de miniaturas, evitando redesenhar os bitmaps do vídeo a cada frame.

> **Skill `/revisar-canvas`:** audita estes padrões (alocação no draw scope, race de gesto vs. estado observado, isolamento de animação) em todo código de UI com `Canvas`/`drawBehind`. Rode antes de commitar mudanças na timeline.
