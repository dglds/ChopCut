# 🚀 Scripts do Gradle e Menu Interativo

Este documento descreve o `Makefile` (caminho canônico) e o menu interativo opcional `./gradle-menu` do projeto ChopCut.

---

## 📋 Visão geral

O caminho canônico de build são os atalhos do **`Makefile`**, que já exporta o `JAVA_HOME=./jdk17` do projeto. Não há matriz de toggles em script: as flags de performance (parallel, caching, daemon) vivem em **`gradle.properties`**, sempre ligadas.

Como conveniência opcional há dois menus interativos. São scripts bash que usam o `select` nativo da shell (**zero dependências** — sem Go, sem binário, sem compilação); a lista de opções reaparece após cada ação, até você escolher `sair`:

- **`./gradle-menu`** — tarefas de build, delegando ao `make`.
- **`./adb-menu`** — conexão de dispositivos Android via `adb` (connect, pair Wi-Fi, listar, desconectar).

---

## 🛠️ Atalhos do `Makefile`

```bash
make build      # APK de debug (assembleDebug)
make install    # instala no device/emulador conectado
make run        # instala e abre o app
make compile    # checagem rápida do Kotlin (compileDebugKotlin)
make lint       # lintDebug
make test       # testes instrumentados (requer device)
make clean      # limpa os outputs de build
make help       # lista os alvos
```

Toggles pontuais de debug vão via `GRADLE_ARGS`, ex.: `make build GRADLE_ARGS="-i --rerun-tasks"`.

---

## 🕹️ Menu de build (`./gradle-menu`)

```bash
./gradle-menu
```

O script imprime a lista numerada de tarefas; você digita o **número** da opção e tecla Enter. A lista reaparece após cada tarefa até você escolher `sair`.

1. **build (APK debug)** — `make build`.
2. **install + abrir app** — `make run` (instala e inicia a MainActivity).
3. **clean + build + install** — `make clean` seguido de `make run`.
4. **clean cache + build** — apaga a pasta `.gradle` e roda `make clean build`.
5. **lint** — `make lint`.
6. **sair** — encerra o menu.

## 📱 Menu de devices (`./adb-menu`)

```bash
./adb-menu
```

1. **connect device** — tenta parear devices de depuração Wi-Fi anunciados via mDNS (`avahi-browse`, se disponível) e lista os devices com `adb devices`.
2. **pair Wi-Fi** — pede `IP:PORT` e o código de pareamento, e roda `adb pair` + `adb connect`.
3. **listar devices** — `adb devices`.
4. **desconectar todos** — `adb disconnect`.
5. **sair** — encerra o menu.

O caminho do `adb` é resolvido a partir de `$ANDROID_HOME/platform-tools/adb` (padrão `~/Android/Sdk`), com fallback para o `adb` do `PATH`.

---

## 🛠️ Comandos manuais equivalentes

Se precisar rodar `gradlew` direto (CI, scripts) sem o `make`, declare o `JAVA_HOME` local na frente do comando:

```bash
JAVA_HOME=./jdk17 ./gradlew assembleDebug          # build do APK debug
JAVA_HOME=./jdk17 ./gradlew installDebug           # instalar no device
JAVA_HOME=./jdk17 ./gradlew connectedAndroidTest   # testes instrumentados
```
