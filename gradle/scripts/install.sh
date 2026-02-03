#!/bin/bash
# Script: install.sh
# Instala o APK debug no dispositivo conectado

echo "📲 Instalando ChopCut..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✅ Instalação concluída!"
else
    echo "❌ Falha na instalação"
    exit 1
fi