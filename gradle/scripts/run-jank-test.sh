#!/usr/bin/env bash
#
# run-jank-test.sh — Teste de fluidez (jank) da timeline em device real, automatizado.
#
# Faz tudo sem a IA precisar raciocinar sobre coordenadas: conecta via adb Wi-Fi
# (mDNS), instala, navega a UI por TEXTO (uiautomator — robusto a resolução),
# extrai os frames pelo fluxo real e mede o jank com gfxinfo. Imprime o veredito.
#
# Uso:
#   gradle/scripts/run-jank-test.sh              # fluxo completo (connect→install→extrair→medir)
#   gradle/scripts/run-jank-test.sh --no-install # pula o build/install (app já instalado)
#   gradle/scripts/run-jank-test.sh --connect    # só garante a conexão adb e sai
#
# Requisitos: device pareado por adb Wi-Fi na mesma rede (Galaxy A15 do projeto já
# costuma estar) OU USB. Pareamento de 1ª vez exige código do aparelho (manual).
#
set -uo pipefail

ADB="$HOME/Android/Sdk/platform-tools/adb"
PKG="com.chopcut"
ACT="$PKG/.MainActivity"
THRESH="${JANK_THRESHOLD:-1.0}"   # % de frames janky tolerada (default 1%)

log()  { printf '\033[36m▶ %s\033[0m\n' "$*" >&2; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$*" >&2; }
err()  { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }

ensure_device() {
  if $ADB get-state >/dev/null 2>&1; then ok "device já conectado: $($ADB get-serialno)"; return; fi
  log "Nenhum device. Procurando via mDNS (adb Wi-Fi)..."
  local svc
  svc=$($ADB mdns services 2>/dev/null | awk '/_adb-tls-connect/{print $NF; exit}')
  if [ -z "$svc" ]; then
    err "Sem device e nenhum serviço _adb-tls-connect no mDNS."
    err "Ligue 'Depuração sem fio' no aparelho (mesma rede) ou conecte USB. Pareamento de 1ª vez: adb pair <ip:porta> <codigo>."
    exit 2
  fi
  log "Conectando em $svc ..."
  $ADB connect "$svc" >&2
  $ADB wait-for-device
  ok "conectado: $svc"
}

# tap_text "Rótulo exato" — encontra o elemento por texto via uiautomator e toca no centro.
tap_text() {
  local label="$1" tries="${2:-8}" xml line nums x1 y1 x2 y2
  for ((t=1; t<=tries; t++)); do
    $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    xml=$($ADB shell cat /sdcard/ui.xml 2>/dev/null)
    line=$(printf '%s' "$xml" | tr '<' '\n' | grep -F "text=\"$label\"" | head -1)
    if [ -n "$line" ]; then
      nums=$(printf '%s' "$line" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
      if [ -n "$nums" ]; then
        read -r x1 y1 x2 y2 <<<"$nums"
        $ADB shell input tap $(((x1+x2)/2)) $(((y1+y2)/2))
        ok "tap \"$label\""
        return 0
      fi
    fi
    sleep 1
  done
  err "não encontrei o elemento \"$label\" (uiautomator)"
  return 1
}

wait_text() {  # espera um texto aparecer (ex.: fim da extração). $2 = nº de tentativas (~2s cada)
  local label="$1" tries="${2:-40}"
  for ((s=0; s<tries; s++)); do
    $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    if $ADB shell cat /sdcard/ui.xml 2>/dev/null | grep -qF "text=\"$label\""; then return 0; fi
    sleep 1
  done
  return 1
}

navigate_and_extract() {
  log "Abrindo Home"
  $ADB shell am force-stop "$PKG"; $ADB shell input keyevent KEYCODE_WAKEUP
  $ADB shell am start -n "$ACT" >/dev/null; sleep 3
  tap_text "Escolher Vídeo" || tap_text "Selecionar Vídeo" || return 1
  sleep 2
  log "Ordenando por menor e selecionando o vídeo mais curto (extração rápida e determinística)"
  tap_text "Menor" || true; sleep 1   # menor arquivo primeiro = vídeo curto = poucos frames
  read -r W H < <($ADB shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
  $ADB shell input tap $((W*20/100)) $((H*22/100)); sleep 2   # 1ª miniatura do grid
  tap_text "Extrair Frames" || return 1
  log "Aguardando extração concluir..."
  wait_text "Extração Finalizada" 120 || { err "extração não concluiu no tempo"; return 1; }
  ok "extração concluída"
  tap_text "Fechar" || true; sleep 1
  tap_text "Editar" || return 1
  sleep 4
  ok "editor aberto com thumbnails reais"
}

measure_jank() {
  read -r W H < <($ADB shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
  # Botão play: canto inf. esquerdo sob o preview (~7,4% x, ~64% y). Reprodução é o
  # caminho de 60Hz mais LIMPO — scroll via `input swipe` infla jank artificialmente.
  local px=$((W*74/1000)) py=$((H*643/1000))
  log "Resetando gfxinfo e reproduzindo ~6s (timeline redesenha a 60Hz)"
  $ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  $ADB shell input tap $px $py
  sleep 6
  local out total janky vsync slowui
  out=$($ADB shell dumpsys gfxinfo "$PKG" 2>/dev/null)
  $ADB shell input tap $px $py >/dev/null 2>&1   # pausa (cleanup)
  total=$(printf '%s' "$out" | sed -n 's/.*Total frames rendered: \([0-9]*\).*/\1/p' | head -1)
  janky=$(printf '%s' "$out" | grep -m1 'Janky frames:' | sed -n 's/.*Janky frames: [0-9]* (\([0-9.]*\)%).*/\1/p')
  vsync=$(printf '%s' "$out" | sed -n 's/.*Number Missed Vsync: \([0-9]*\).*/\1/p' | head -1)
  slowui=$(printf '%s' "$out" | sed -n 's/.*Number Slow UI thread: \([0-9]*\).*/\1/p' | head -1)
  echo "" >&2
  echo "════════ VEREDITO DE JANK (reprodução, ${total:-?} frames) ════════" >&2
  printf '  Janky frames : %s%%\n  Missed Vsync : %s\n  Slow UI thread: %s\n' "${janky:-?}" "${vsync:-?}" "${slowui:-?}" >&2
  echo "  (logcat de crashes:)" >&2
  $ADB logcat -d --pid=$($ADB shell pidof -s "$PKG" 2>/dev/null) 2>/dev/null | grep -iE "exception|fatal|crash" | head -3 >&2 || true
  # PASS/FAIL pela % de janky
  awk -v j="${janky:-100}" -v t="$THRESH" 'BEGIN{ if (j+0 <= t+0) exit 0; else exit 1 }' \
    && ok "PASS — ${janky}% ≤ ${THRESH}% janky" \
    || { err "FAIL — ${janky:-?}% > ${THRESH}% janky"; return 1; }
}

# ── main ──
case "${1:-}" in
  --connect) ensure_device; exit 0 ;;
  --no-install) ensure_device ;;
  *) ensure_device
     log "Build + install (JDK17)"
     JAVA_HOME=./jdk17 ./gradlew installDebug -q || { err "installDebug falhou"; exit 1; } ;;
esac

navigate_and_extract || { err "navegação/extração falhou — rode a skill /rodar-app manualmente para depurar"; exit 1; }
measure_jank
