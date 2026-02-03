#!/bin/bash
# Script: wifi.sh
# Conecta ao dispositivo via WiFi para debug

IP="192.168.0.193"
PORT="38361"

echo "🔌 Conectando via WiFi ($IP:$PORT)..."
adb connect $IP:$PORT

if [ $? -eq 0 ]; then
    echo "✅ Conectado!"
    adb devices
else
    echo "❌ Falha na conexão"
    echo "💡 Dica: Certifique-se de que o celular está na mesma rede WiFi"
    exit 1
fi