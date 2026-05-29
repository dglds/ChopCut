---
name: rodar-app
description: Compila, instala e abre o ChopCut num device/emulador Android conectado, com captura de tela e logcat para confirmar visualmente mudanças de UI (timeline, playhead, thumbnails). Use ao rodar/iniciar/screenshotar o app ou validar uma mudança rodando de verdade — a skill /run delega para cá neste projeto.
---

# rodar-app — subir e inspecionar o ChopCut

Fluxo para ver uma mudança funcionando no app real (não só `compileDebugKotlin`). Validação de jank/UI no ChopCut precisa rodar de verdade — é o critério de sucesso para qualquer mudança em `TimelineFeature.kt` e afins.

## ⚡ Atalho automatizado (preferir isto)

Para o teste de jank da timeline, **não navegue na mão** — rode o script, que faz tudo (conecta adb Wi-Fi → instala → navega a UI por texto → extrai frames → mede gfxinfo durante a reprodução → imprime PASS/FAIL):

```bash
gradle/scripts/run-jank-test.sh              # fluxo completo
gradle/scripts/run-jank-test.sh --no-install # app já instalado
gradle/scripts/run-jank-test.sh --connect    # só garantir conexão adb
```

O verdito sai em `Janky frames %` (mira < 1% na reprodução; threshold via `JANK_THRESHOLD`). Os passos manuais abaixo são para **depurar quando o script falha** ou para inspeções fora do teste de jank.

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

- **Não há AVD no SDK padrão** (`~/Android/Sdk/emulator` ausente). Não tente `emulator -avd` às cegas.
- **Tente adb Wi-Fi via mDNS primeiro** — o Galaxy A15 do projeto (`SM-A156M`) costuma já estar pareado, então reconecta sem código:
  ```bash
  ~/Android/Sdk/platform-tools/adb mdns services        # descobre IP:porta do serviço _adb-tls-connect._tcp
  ~/Android/Sdk/platform-tools/adb connect <IP>:<PORTA>  # ex.: 192.168.1.10:39015
  ```
  A porta é dinâmica — sempre leia do `mdns services`, não fixe. Requer device e PC na mesma rede, com "Depuração sem fio" ligada no aparelho.
- **Pareamento (só na 1ª vez, exige código do aparelho):** o código de 6 dígitos é exibido no celular e é obrigatório (handshake de segurança — não dá para automatizar). `adb pair <IP>:<PORTA_PAREAMENTO> <CODIGO>`, depois `connect`.
- **USB** como alternativa: cabo de dados + "Transferência de arquivos (MTP)" + autorizar o popup de depuração.
- **Não prossiga** para build/instalar sem device — `installDebug` falha sem alvo. Sugira `! ~/Android/Sdk/platform-tools/adb devices` no chat para o usuário confirmar.

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

Abrir direto no editor com um vídeo (entrada rápida, mas **sem thumbnails reais**):
```bash
~/Android/Sdk/platform-tools/adb shell am start -a android.intent.action.VIEW \
  -t video/* -d file:///sdcard/DCIM/Camera/<video>.mp4 com.chopcut/.MainActivity
```

> ⚠️ **Gotcha — thumbnails:** via deep-link `ACTION_VIEW` a régua mostra **placeholders gradiente**; a extração de frames NÃO é disparada nesse fluxo. Para testar o caminho real de `drawBitmap`/`dstRect`, entre pela UI:
> **Home → "Escolher Vídeo" → galeria (escolher vídeo) → "Extrair Frames"** (aguardar 100%, ~7 fps) **→ "Editar"**.
> Conduza pela UI com `adb shell input tap <x> <y>` (descubra coordenadas via screenshot + `adb shell wm size`).

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

## Passo 5 — Medir jank objetivamente (`gfxinfo`)

Para mudanças de performance, screenshot não basta — meça. O `gfxinfo` dá frames travados sem depender de percepção:

```bash
ADB=~/Android/Sdk/platform-tools/adb
$ADB shell dumpsys gfxinfo com.chopcut reset          # zera as métricas
# ... exercite o caminho pesado por alguns segundos ...
$ADB shell dumpsys gfxinfo com.chopcut | grep -iE "Total frames|Janky frames:|Missed Vsync|Slow UI thread|Slow bitmap"
```

Como exercitar o **Canvas da timeline** (caminho de 60Hz):
- **Reprodução** (mais limpo): dê play — o poll do ExoPlayer atualiza a posição a cada 16ms e o Canvas redesenha a 60Hz. Sem ruído de input sintético.
- **Scroll**: `adb shell input swipe` na faixa da régua (só funciona pausado — o gesto exige `!isPlaying`).

Leitura das métricas:
- **`Janky frames: N (X%)`** é o número que importa — mire **< 1%** no caminho otimizado. (Ignore `Janky frames (legacy)`, que dá ~100% em telas de alta taxa — é artefato.)
- **`Number Missed Vsync`** e **`Slow UI thread`** baixos confirmam que a thread de UI (onde o draw scope roda) não está estourando o deadline.
- **`Number High input latency`** infla com `input swipe` sintético — ignore; não reflete uso real.

Baseline validado (Galaxy A15, thumbnails reais, pós-otimização): reprodução **0,13% janky / 0 missed vsync**; scroll **0,00% janky / 0 slow UI thread**.

## Como reportar

Diga objetivamente: device usado, se buildou/instalou, o que a tela mostra (com base no screenshot), e — para performance — os números do `gfxinfo` (janky %, missed vsync). Não afirme "sem jank" sem ter medido ou observado o caminho real (com thumbnails extraídos, não placeholders). Se não havia device, pare no Passo 1 e peça um.
