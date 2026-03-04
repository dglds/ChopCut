#!/bin/bash
# Monitor de Atividade ChopCut (versão timeout)
# Auto-encerra após inatividade

GREEN='\033[1;32m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
NC='\033[0m'

# Timeout em segundos sem logs (0 = sem timeout)
TIMEOUT=60  # Encerra após 60s sem logs

cleanup() {
    echo ''
    echo -e "${YELLOW}═══ ENCERRANDO ═══${NC}"
    adb logcat -c >/dev/null 2>&1
    echo -e "${GREEN}✓ Encerrado${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

clear

echo "═══ MONITOR CHOPCUT (Timeout: ${TIMEOUT}s) ═══"
echo ""

PID=$(adb shell pidof com.chopcut 2>/dev/null)

if [ -z "$PID" ]; then
    echo -e "${YELLOW}⚠ ChopCut não está em execução${NC}"
    read -p "Pressione Enter para sair..."
    exit 1
fi

echo -e "${GREEN}✓ ChopCut ativo (PID: $PID)${NC}"
echo ""
echo "═══ LOGS ═══ (Ctrl+C para sair, auto-encerra após ${TIMEOUT}s inativo)"
echo ""

adb logcat -c >/dev/null 2>&1

# Monitora com timeout
LAST_LOG_TIME=$(date +%s)

timeout ${TIMEOUT}s bash -c '
    adb logcat -v time --pid='$PID' 2>/dev/null | grep --line-buffered -iE "(extract|Cache|Strip)"
' | while IFS= read -r line; do
    LAST_LOG_TIME=$(date +%s)
    
    TIMESTAMP=$(echo "$line" | awk '{print $1 " " $2}')
    MSG=$(echo "$line" | sed 's/^.*[VDIWEF]\/.*:[0-9]*: //')
    
    if echo "$MSG" | grep -qiE "(extractSegment|Extracting|Batch)"; then
        echo -e "${YELLOW}${TIMESTAMP} ▸ ${MSG}${NC}"
    elif echo "$MSG" | grep -qiE "(Cache HIT|Cache MISS)"; then
        echo -e "${CYAN}${TIMESTAMP} ▸ ${MSG}${NC}"
    else
        echo "${TIMESTAMP} ▸ ${MSG}"
    fi
done || {
    echo ''
    echo -e "${YELLOW}⚠ Timeout (${TIMEOUT}s) ou processo encerrado${NC}"
}

cleanup
