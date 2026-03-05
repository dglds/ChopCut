#!/bin/bash

# Cores
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🎬 Rodando APENAS Testes de Extração de Thumbnails${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Chama o nosso executor de logs live passando APENAS o teste de assertiva dinâmica
./scripts/run_live_tests.sh "com.chopcut.performance.ThumbnailExtractionPerformanceTest#testExtractionCountAccuracy"
