#!/bin/bash

# Wrapper para usar as funções básicas
# Usage: ./scripts/cc.sh <function_name> [args]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/basic_functions.sh"

if [ $# -eq 0 ]; then
    help
    exit 0
fi

FUNCTION_NAME="$1"
shift

# Executar função passada
if declare -f "$FUNCTION_NAME" > /dev/null; then
    "$FUNCTION_NAME" "$@"
else
    echo -e "${RED}❌ Function '$FUNCTION_NAME' not found${NC}"
    echo ""
    help
    exit 1
fi
