---
name: rodar-app
description: Compila, instala e abre o ChopCut num device/emulador Android conectado, com captura de tela e logcat para confirmar visualmente mudanças de UI (timeline, playhead, thumbnails). Use ao rodar/iniciar/screenshotar o app ou validar uma mudança rodando de verdade — a skill /run delega para cá neste projeto.
---

# rodar-app — subir e inspecionar o ChopCut

Fluxo para ver uma mudança funcionando no app real (não só `compileDebugKotlin`). Validação de jank/UI no ChopCut é **visual** — não há teste automatizado que pegue engasgo de scroll, então rodar de verdade é o critério de sucesso para qualquer mudança em `TimelineFeature.kt` e afins.

## Constantes do projeto

- **adb:** `~/Android/Sdk/platform-tools/adb`
- **applicationId / launcher:** `com.chopcut/.MainActivity`
- **Build sempre com JDK 17 local:** prefixe `JAVA_HOME=./jdk17`
- O app também aceita deep-link de vídeo via `ACTION_VIEW` (`content://` ou `file://` + mime `video/*`) — abre direto no editor.

## Passo 1 — Garantir um device

```bash
~/Android/Sdk/platform-tools/adb devices
```

Se a lista vier vazia (só o cabeçalho `List of devices attached`):

- **Não há AVD configurado no SDK padrão deste projeto** (`~/Android/Sdk/emulator` ausente). Não tente `emulator -avd` às cegas.
- Peça ao usuário para conectar um device USB (com depuração ativada) ou subir o emulador dele. Sugira que ele rode no chat: `! ~/Android/Sdk/platform-tools/adb devices` para confirmar.
- **Não prossiga** para build/instalar sem device — `installDebug` falha sem alvo.

## Passo 2 — Build + instalar

```bash
JAVA_HOME=./jdk17 ./gradlew installDebug
```

Isso compila e instala o debug APK no device conectado. Se houver múltiplos devices, escolha com `-PtargetDevice` ou exporte `ANDROID_SERIAL=<id>` antes do comando.

## Passo 3 — Abrir o app

App limpo (tela inicial / Home):
```bash
~/Android/Sdk/platform-tools/adb shell am start -n com.chopcut/.MainActivity
```

Abrir direto no editor com um vídeo (testar a timeline sem navegar na UI):
```bash
# empurra um vídeo e dispara o deep-link ACTION_VIEW
~/Android/Sdk/platform-tools/adb push ~/Videos/video.mp4 /sdcard/Movies/video.mp4
~/Android/Sdk/platform-tools/adb shell am start -a android.intent.action.VIEW \
  -t video/* -d file:///sdcard/Movies/video.mp4 com.chopcut/.MainActivity
```

## Passo 4 — Confirmar visualmente

Captura de tela (puxa o PNG para inspeção):
```bash
~/Android/Sdk/platform-tools/adb exec-out screencap -p > /tmp/chopcut.png
```
Depois leia `/tmp/chopcut.png` com a ferramenta Read para descrever/validar a UI. Para mudanças de timeline, capture **durante** scroll/reprodução se possível, já que o ponto é fluidez do playhead e das thumbnails.

Logs em tempo real (erros, jank, exceptions) — filtre pelo processo do app:
```bash
~/Android/Sdk/platform-tools/adb logcat --pid=$(~/Android/Sdk/platform-tools/adb shell pidof -s com.chopcut)
```
Ou só erros/warnings: `adb logcat *:W`.

## Como reportar

Diga objetivamente: device usado, se buildou/instalou, o que a tela mostra (com base no screenshot), e qualquer erro do logcat. Se a mudança era de performance (jank), declare que a validação é visual/qualitativa — não afirme "sem jank" sem ter observado scroll real. Se não havia device, pare no Passo 1 e peça um.
