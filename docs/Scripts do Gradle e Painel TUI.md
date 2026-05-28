# 🚀 Scripts do Gradle e Painel Interativo (TUI Go)

Este documento descreve como utilizar os scripts localizados em `gradle/scripts/` e o painel de alta performance do Gradle criado para o projeto ChopCut.

---

## 📋 Visão Geral

Para simplificar a execução de tarefas do Gradle e garantir o uso correto do JDK do projeto, foi desenvolvido um painel TUI (Terminal User Interface) em Go usando as bibliotecas **Bubble Tea** e **Lipgloss** do Charmbracelet.

O painel é controlado pelo atalho principal na raiz do projeto:
* **`./gradle-menu`** (Script Bash wrapper que compila e executa o painel em Go)

---

## 🛠️ Configuração e Parâmetros (`gradle/scripts/gradle-params.sh`)

O comportamento das tarefas executadas pelo painel é controlado de forma centralizada pelo arquivo `gradle/scripts/gradle-params.sh`. Editando os valores para `true` ou `false`, você altera instantaneamente as flags passadas ao Gradle sem precisar digitar comandos longos no terminal.

### 🔍 Parâmetros Disponíveis:

| Grupo | Parâmetro | Valor Padrão | Descrição |
| :--- | :--- | :---: | :--- |
| **Verbosidade** | `GRADLE_QUIET` | `false` | Exibe apenas erros críticos (`-q`). |
| | `GRADLE_WARN` | `false` | Exibe avisos e erros (`-w`). |
| | `GRADLE_INFO` | `true` | Modo informativo com detalhes de ciclo de vida (`-i`). |
| | `GRADLE_DEBUG` | `false` | Depuração detalhada do Gradle daemon (`-d`). |
| **Rastreamento** | `GRADLE_STACKTRACE` | `false` | Exibe stacktrace simples para erros (`-s`). |
| | `GRADLE_FULL_STACKTRACE` | `true` | Exibe stacktrace completo do compilador (`-S`). |
| **Performance** | `GRADLE_PARALLEL` | `true` | Compilação paralela de módulos (`--parallel`). |
| | `GRADLE_BUILD_CACHE` | `true` | Reutiliza compilações estáveis (`--build-cache`). |
| | `GRADLE_CONFIGURE_ON_DEMAND` | `true` | Configura apenas módulos necessários (`--configure-on-demand`). |
| | `GRADLE_LIMIT_WORKERS` | `false` | Limita a 4 workers para poupar CPU (`--max-workers=4`). |
| **Modos Especiais**| `GRADLE_DAEMON` | `true` | Mantém o daemon ativo para builds subsequentes mais rápidos. |
| | `GRADLE_OFFLINE` | `false` | Usa apenas dependências já baixadas no cache local (`--offline`). |
| | `GRADLE_RERUN_TASKS` | `false` | Força a reexecução completa ignorando o cache (`--rerun-tasks`). |
| | `GRADLE_CONTINUE` | `false` | Continua executando tarefas mesmo em caso de falha de alguma (`--continue`). |
| | `GRADLE_DRY_RUN` | `false` | Apenas simula as tarefas sem executá-las (`-m`). |

---

## 🚀 Como Executar o Painel Interativo

Basta rodar o atalho a partir da raiz do projeto:

```bash
./gradle-menu
```

### O que o script faz por trás dos panos?
1. **Verificação de Dependência**: Verifica se o `go` está instalado no sistema.
2. **Compilação Automática**: Compila o código fonte em `gradle/scripts/menu-go/main.go` gerando o executável binário `menu-bin` se for a primeira vez ou se houver alterações no código-fonte.
3. **Ambiente Automático**: Injeta a variável `JAVA_HOME=./jdk17` garantindo compatibilidade com o JDK 17 do projeto, evitando falhas com a versão global da máquina host.
4. **Resolução de mDNS**: Tenta encontrar e parear com dispositivos na rede via mDNS automaticamente.

---

## 📱 Opções do Menu TUI

Ao abrir o painel, você terá acesso a uma interface dividida em duas colunas:
1. **Esquerda**: Lista de tarefas e Dispositivos Android conectados/autorizados no momento.
2. **Direita**: Saída em tempo real (logs) da tarefa em execução e status do último APK compilado.

### 🕹️ Atalhos do Teclado:
* **`Seta para Cima / k`**: Navegar para cima nas opções de tarefa.
* **`Seta para Baixo / j`**: Navegar para baixo nas opções de tarefa.
* **`Enter`**: Executar a tarefa selecionada.
* **`Teclas de 1 a 6`**: Executa diretamente a tarefa pelo número correspondente.
* **`Tecla 0 / q / Ctrl+C`**: Sair do painel.
* **`Esc`** (durante execução de tarefa): **Cancela / Aborta** a execução imediatamente.

### 📝 Lista de Tarefas Pré-configuradas:
1. **`build`**: Compila o APK de desenvolvimento (`./gradlew assembleDebug`).
2. **`build and install`**: Conecta ao dispositivo, realiza o build, instala o APK debug e inicia automaticamente o aplicativo na MainActivity.
3. **`clean build and install`**: Apaga a pasta `.gradle` e compilações anteriores, reconecta ao dispositivo, executa nova compilação limpa, instala e inicia o app.
4. **`clean build and cache`**: Limpa o cache físico do Gradle, limpa compilações antigas e reconstrói o cache de compilações gerando o APK debug limpo.
5. **`connect device`**: Tenta forçar a detecção de dispositivos USB/rede via comandos ADB (`wait-for-device`) e mDNS.
6. **`check syntax (lintDebug)`**: Executa a análise estática e sintática de erros do Kotlin compiler para garantir a integridade do código sem avisos críticos.

---

## 🛠️ Comandos Manuais Equivalentes

Se você precisar executar comandos diretamente de scripts em shell ou CI sem usar a TUI, sempre lembre de declarar o `JAVA_HOME` local na frente do comando:

```bash
# Compilar APK Debug manualmente
JAVA_HOME=./jdk17 ./gradlew assembleDebug

# Compilar e Instalar manualmente
JAVA_HOME=./jdk17 ./gradlew installDebug

# Rodar todos os testes instrumentados manualmente
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest
```
