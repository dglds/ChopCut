#!/bin/bash

# test_timber.sh - Gera logs de teste no logcat

echo "🧪 Gerando logs de teste..."
echo ""

# Injeta logs simulados do Timber/com.chopcut
adb shell "log -t com.chopcut 'Timber.e: TESTE - Erro simulado'" &
sleep 0.2
adb shell "log -t com.chopcut 'Timber.w: TESTE - Warning simulado'" &
sleep 0.2
adb shell "log -t com.chopcut 'Timber.d: TESTE - Debug simulado'" &
sleep 0.2
adb shell "log -t com.chopcut 'Timber.i: TESTE - Info simulada'" &
sleep 0.2

echo "✅ Logs injetados!"
echo ""
echo "Execute em outro terminal:"
echo "  ./timber_log.sh -e    # para ver errors"
echo "  ./timber_log.sh -d    # para ver debug"
echo "  ./timber_log.sh       # para ver todos"
