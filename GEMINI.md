# ChopCut — Guia de Desenvolvimento do Gemini / Antigravity

Este arquivo instrui os agentes de IA **Gemini** e **Antigravity** sobre como compilar, testar, depurar e seguir as diretrizes de design, arquitetura e gerenciamento de sessões deste projeto.

---

## 🛠️ Comandos de Compilação e Build

Este projeto requer o uso do **JDK 17** local (localizado na pasta `./jdk17`) e o Android SDK local. Sempre garanta a configuração de `JAVA_HOME=./jdk17` ao invocar comandos:

- **Compilação Rápida de Segurança:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
  ```
- **Compilação e Geração do APK Debug:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew assembleDebug
  ```
- **Instalar APK no Emulador/Celular Conectado:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew installDebug
  ```

---

## 🧪 Comandos de Testes

- **Executar Todos os Testes Locais:**
  ```bash
  JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest
  ```

---

## 📋 Protocolos de Sessão e Arquitetura

Antes de planejar ou realizar alterações estruturais, siga rigorosamente os seguintes protocolos:

1. **Protocolo de Sessão:** leia o arquivo [SESSION_PROTOCOL.md](file:///home/diego/Android/ChopCut/SESSION_PROTOCOL.md) para alinhar-se ao protocolo de inicialização de sessão (utilizando `/start-session`), execução e geração do arquivo de fechamento de sessão `sessions/session#NN-objetivo-da-session.md` (utilizando `/finish-session`).
2. **Regras da Arquitetura:** leia o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md) para garantir total conformidade com a estrutura enxuta de **16 arquivos** e a regra inegociável de **package único** (`com.chopcut`), que proíbe qualquer import interno.
3. **Estilo de Mensagem e Commits:** faça commits atômicos baseados em escopo (ex: `refactor(editor): ...`, `fix(home): ...`, `docs: ...`).

---

## ⚡ Diretrizes de Performance de Renderização

Evite quedas de taxa de quadros (frame drops) na régua de miniaturas da timeline:
- **Zero alocações no Canvas:** Nunca instancie objetos como `Paint()`, `RectF()`, ou cores dinâmicas no bloco `drawBehind` ou `Canvas { ... }`. Aloque-os fora e utilize a API `remember` para preservar referências estáveis.
- **Divisão de Responsabilidades:** Separe a renderização das miniaturas da timeline de elementos altamente dinâmicos (como o cursor da agulha de reprodução / playhead) usando Canvas sobrepostos independentes.
