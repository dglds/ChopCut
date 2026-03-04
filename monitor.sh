#!/bin/bash
# Monitor de Atividade ChopCut
# Ctrl+C para sair

GREEN='\033[1;32m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
NC='\033[0m'

# Cleanup ao sair
cleanup() {
    echo ''
    echo -e "${YELLOW}═══ ENCERRANDO MONITORAMENTO ═══${NC}"
    
    # Mata processo do monitor
    if [ -n "$MONITOR_PID" ]; then
        kill $MONITOR_PID 2>/dev/null
    fi
    
    # Encerra logcat
    adb logcat -c >/dev/null 2>&1
    
    echo -e "${GREEN}✓ Encerrado${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

clear

echo "═══ MONITOR CHOPCUT ═══"
echo ""

# PID do ChopCut
PID=$(adb shell pidof com.chopcut 2>/dev/null)

if [ -z "$PID" ]; then
    echo -e "${YELLOW}⚠ ChopCut não está em execução${NC}"
    echo -e "${YELLOW}Pressione Enter para sair...${NC}"
    read
    exit 1
fi

echo -e "${GREEN}✓ ChopCut ativo (PID: $PID)${NC}"
echo ""
echo -e "${YELLOW}Pressione Ctrl+C para encerrar${NC}"
echo ""
echo "═══ LOGS ═══"
echo ""

# Limpa logcat
adb logcat -c >/dev/null 2>&1

# Monitora logs em tempo real (sem loop de indicador para simplificar)
adb logcat -v time --pid=$PID 2>/dev/null | grep --line-buffered -iE "(extract|Cache|Strip)" | while IFS= read -r line; do
    # Extrai timestamp e mensagem
    TIMESTAMP=$(echo "$line" | awk '{print $1 " " $2}')
    MSG=$(echo "$line" | sed 's/^.*[VDIWEF]\/.*:[0-9]*: //')
    
    # Cores
    if echo "$MSG" | grep -qiE "(extractSegment|Extracting|Batch)"; then
        echo -e "${YELLOW}${TIMESTAMP} ▸ ${MSG}${NC}"
    elif echo "$MSG" | grep -qiE "(Cache HIT|Cache MISS)"; then
        echo -e "${CYAN}${TIMESTAMP} ▸ ${MSG}${NC}"
    else
        echo "${TIMESTAMP} ▸ ${MSG}"
    fi
done

# Cleanup final
cleanup
