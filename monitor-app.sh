#!/bin/bash

# Definição de cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

PACKAGE_NAME="com.chopcut"

# Verifica se o ADB está instalado
if ! command -v adb &> /dev/null; then
    echo -e "${RED}Erro: ADB não encontrado. Por favor, instale o Android Debug Bridge.${NC}"
    exit 1
fi

echo -e "${CYAN}=== Iniciando Monitor ChopCut ===${NC}"
echo "Aguardando dispositivo..."
adb wait-for-device

while true; do
    # Tenta limpar a tela sem piscar (tput) ou fallback para clear
    if command -v tput &> /dev/null; then
        tput cup 0 0
        tput ed
    else
        clear
    fi

    echo -e "${CYAN}╔════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║       ChopCut Performance Monitor      ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════╝${NC}"
    echo "Data: $(date '+%H:%M:%S')"
    echo ""

    # Tenta obter PID de várias formas (pidof é mais rápido, grep é fallback)
    PID=$(adb shell pidof $PACKAGE_NAME 2>/dev/null)
    if [ -z "$PID" ]; then
        PID=$(adb shell "ps -A | grep $PACKAGE_NAME" | awk '{print $2}' | head -n 1)
    fi

    if [ -n "$PID" ]; then
        echo -e "Status:  ${GREEN}● RODANDO${NC} (PID: $PID)"
        
        # Coleta de métricas
        # Memória (PSS Total em MB)
        MEM_INFO=$(adb shell dumpsys meminfo $PID 2>/dev/null)
        MEM_KB=$(echo "$MEM_INFO" | grep "TOTAL" | head -1 | awk '{print $2}')
        
        if [ -n "$MEM_KB" ]; then
             MEM_MB=$(awk "BEGIN {printf \"%.2f\", $MEM_KB/1024}")
             echo -e "Memória: ${YELLOW}${MEM_MB} MB${NC}"
        fi

        # CPU (Simples)
        # Nota: O formato do top varia entre versões do Android. 
        # Tentando pegar a coluna que geralmente tem o %CPU (9 ou variada)
        CPU_RAW=$(adb shell top -n 1 -b 2>/dev/null | grep $PID | head -n 1)
        # Geralmente é a coluna antes do nome do pacote ou S (State). 
        # Vamos assumir a lógica anterior do usuário que funcionava (awk '{print $9}') mas ajustando se falhar
        CPU=$(echo "$CPU_RAW" | awk '{print $9}')
        
        if [ -z "$CPU" ]; then
             CPU="N/A"
        else
             CPU="${CPU}%"
        fi
        echo -e "CPU:     ${YELLOW}${CPU}${NC}"

        # Threads (contagem simples)
        THREADS=$(adb shell ls /proc/$PID/task 2>/dev/null | wc -l)
        echo -e "Threads: ${BLUE}${THREADS}${NC}"

    else
        echo -e "Status:  ${RED}● PARADO${NC}"
        echo -e "${YELLOW}Aguardando processo ${PACKAGE_NAME}...${NC}"
    fi

    echo ""
    echo "Pressione Ctrl+C para sair."
    sleep 1
done