#!/bin/bash

# Cores para formatação do output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "🔍 Verificando conexão com dispositivo/emulador Android..."

# Executa o comando adb devices e ignora a primeira linha ("List of devices attached")
# Filtra por linhas que contêm a palavra "device" (ignorando "offline" ou "unauthorized")
DEVICES=$(adb devices | grep -v "List of devices attached" | grep -w "device")

if [ -z "$DEVICES" ]; then
    echo -e "${RED}❌ ERRO: Nenhum dispositivo Android ou emulador conectado.${NC}"
    echo -e "${YELLOW}👉 Por favor, conecte seu celular via USB (com Depuração USB ativada) ou inicie um emulador.${NC}"
    echo -e "${YELLOW}👉 Dica: Execute 'adb devices' para ver o status dos seus dispositivos.${NC}"
    exit 1
else
    # Conta quantos dispositivos estão conectados
    DEVICE_COUNT=$(echo "$DEVICES" | wc -l)
    
    echo -e "${GREEN}✅ SUCESSO: Dispositivo conectado! ($DEVICE_COUNT encontrado/s)${NC}"
    
    # Lista os dispositivos conectados
    echo "$DEVICES" | while read -r line; do
        DEVICE_ID=$(echo "$line" | awk '{print $1}')
        echo -e "  📱 ID do Dispositivo: ${GREEN}$DEVICE_ID${NC}"
    done
    
    exit 0
fi
