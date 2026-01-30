#!/bin/bash

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PACKAGE_NAME="com.chopcut"

echo -e "${BLUE}🔍 Buscando processo do app: $PACKAGE_NAME...${NC}"

# 1. Obter PID do app
PID=$(adb shell pidof -s $PACKAGE_NAME)

if [ -z "$PID" ]; then
    echo -e "${RED}❌ O app não está rodando. Abra o ChopCut no dispositivo e tente novamente.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ App encontrado (PID: $PID)${NC}"
echo -e "${BLUE}📊 Analisando logs recentes para encontrar tags...${NC}"

# 2. Dump dos logs recentes para extrair Tags e seus níveis
# Formato do logcat brief: P/Tag(PID): Msg
# Usamos awk para pegar o Nível (P) e a Tag
RAW_TAGS=$(adb logcat -d --pid=$PID -v brief -t 2000 *:W 2>/dev/null | grep -E "^[WE]/" | awk -F'[/(]' '{print $1 " " $2}' | sort | uniq)

if [ -z "$RAW_TAGS" ]; then
    echo -e "${YELLOW}⚠️ Nenhuma tag de Erro ou Warning encontrada nos logs recentes.${NC}"
    echo "Deseja monitorar TUDO do app? (s/n)"
    read -r response
    if [[ "$response" =~ ^([sS][iI]|[sS])$ ]]; then
        adb logcat --pid=$PID -v color
    fi
    exit 0
fi

# Arrays para armazenar opções
declare -a TAG_LIST
declare -a LEVEL_LIST

echo ""
echo -e "=================================================="
echo -e "   SELECIONE AS TAGS PARA MONITORAR (Ex: 1 3 5)"
echo -e "=================================================="

i=0
while IFS= read -r line; do
    LEVEL=$(echo $line | cut -d' ' -f1)
    TAG=$(echo $line | cut -d' ' -f2)
    
    # Ignorar tags do sistema Android comuns que poluem
    if [[ "$TAG" == "AndroidRuntime" || "$TAG" == "ViewRootImpl" ]]; then
        continue
    fi

    TAG_LIST[$i]=$TAG
    LEVEL_LIST[$i]=$LEVEL
    
    DISPLAY_INDEX=$((i+1))
    
    if [ "$LEVEL" == "E" ]; then
        echo -e "${RED}[$DISPLAY_INDEX] $TAG (Erro Recente)${NC}"
    else
        echo -e "${YELLOW}[$DISPLAY_INDEX] $TAG (Warning Recente)${NC}"
    fi
    
    ((i++))
done <<< "$RAW_TAGS"

echo -e "${BLUE}[0] Monitorar TUDO (Todas as tags acima)${NC}"
echo ""
echo -n "Digite os números separados por espaço: "
read -r SELECTION

if [ -z "$SELECTION" ]; then
    echo "Nenhuma seleção feita. Saindo."
    exit 0
fi

# 3. Construir o comando de filtro
FILTER_CMD=""

if [[ "$SELECTION" == *"0"* ]]; then
    # Se escolheu 0, monitora tudo do PID com nível Warning+
    echo -e "${GREEN}🚀 Monitorando TODOS os Erros/Warnings do app...${NC}"
    adb logcat --pid=$PID -v color *:W
    exit 0
fi

# Construir filtros específicos: "TAG:W" ou "TAG:E"
SELECTED_TAGS_DISPLAY=""
for index in $SELECTION; do
    # Ajustar índice (user input 1-based -> array 0-based)
    arr_index=$((index-1))
    
    if [ ! -z "${TAG_LIST[$arr_index]}" ]; then
        TAG="${TAG_LIST[$arr_index]}"
        # Adiciona ao filtro. Usamos V (Verbose) para a tag selecionada, 
        # mas o logcat geral vai silenciar o resto.
        # Na verdade, para focar, vamos usar grep para filtrar a saída visualmente
        # pois o logcat filter as vezes vaza coisas.
        FILTER_CMD="$FILTER_CMD|$TAG"
        SELECTED_TAGS_DISPLAY="$SELECTED_TAGS_DISPLAY $TAG"
    fi
done

# Remover o primeiro pipe
FILTER_CMD="${FILTER_CMD:1}"

echo -e "${GREEN}🚀 Monitorando tags:${SELECTED_TAGS_DISPLAY}${NC}"
echo -e "${BLUE}(Pressione Ctrl+C para parar)${NC}"
echo ""

# Comando final:
# 1. Pega logs do PID
# 2. Filtra tags escolhidas com grep (colorindo a saída)
# 3. Exibe
adb logcat --pid=$PID -v color *:W | grep --color=always -E "$FILTER_CMD"
