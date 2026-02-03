#!/bin/bash
# Script: deploy.sh
# Build, instala, mata e reinicia o app automaticamente

echo "🚀 Deploy ChopCut iniciado..."

# Verifica se tem dispositivo USB
USB=$(adb devices | grep -v 'List' | grep -v '^$' | grep 'device' | wc -l)

if [ "$USB" -eq 0 ]; then
    echo "⚠️  Nenhum dispositivo USB encontrado"
    echo "🔌 Tentando WiFi..."
    bash $(dirname $0)/wifi.sh || exit 1
    sleep 2
fi

# Instala
echo "📲 Instalando..."
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1

# Mata o app
echo "🛑 Parando app..."
adb shell am force-stop com.chopcut

# Inicia o app
echo "▶️  Iniciando app..."
adb shell am start -n com.chopcut/.MainActivity

echo "✅ Deploy completo!"