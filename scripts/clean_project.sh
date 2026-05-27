#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Cores para saída estilizada
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # Sem cor

echo -e "${BLUE}==================================================${NC}"
echo -e "${BLUE}          CHOPCUT - LIMPEZA DE PROJETO            ${NC}"
echo -e "${BLUE}==================================================${NC}"

# 1. Limpeza padrão do Gradle
if [ -f "$DIR/../gradlew" ]; then
    echo -e "${YELLOW}[1/4] Executando limpeza padrão do Gradle...${NC}"
    chmod +x "$DIR/../gradlew"
    "$DIR/../gradlew" clean
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Limpeza do Gradle concluída com sucesso.${NC}"
    else
        echo -e "${RED}⚠ O comando gradlew clean falhou. Prosseguindo com limpeza manual...${NC}"
    fi
else
    echo -e "${YELLOW}⚠ gradlew não encontrado no diretório atual. Pulando etapa...${NC}"
fi

# 2. Remoção manual de diretórios de build (Garante que nada fique para trás)
echo -e "${YELLOW}[2/4] Removendo pastas de build remanescentes...${NC}"
find "$DIR/.." -type d -name "build" -exec rm -rf {} + 2>/dev/null
find "$DIR/.." -type d -name ".externalNativeBuild" -exec rm -rf {} + 2>/dev/null
find "$DIR/.." -type d -name ".cxx" -exec rm -rf {} + 2>/dev/null
echo -e "${GREEN}✓ Pastas 'build', '.cxx' e compilações NDK deletadas.${NC}"

# 3. Limpeza de cache local do Gradle do projeto
echo -e "${YELLOW}[3/4] Limpando caches e histórico locais do Gradle do projeto...${NC}"
if [ -d "$DIR/../.gradle" ]; then
    rm -rf "$DIR/../.gradle"
    echo -e "${GREEN}✓ Pasta '.gradle' excluída com sucesso.${NC}"
else
    echo -e "${GREEN}✓ Nenhuma pasta '.gradle' encontrada para excluir.${NC}"
fi

# 4. Limpeza de caches do Kotlin compiler
echo -e "${YELLOW}[4/4] Limpando caches de compilações do Kotlin...${NC}"
find "$DIR/.." -type d -name ".kotlin" -exec rm -rf {} + 2>/dev/null
echo -e "${GREEN}✓ Caches do Kotlin deletados.${NC}"

echo -e "${BLUE}==================================================${NC}"
echo -e "${GREEN}             PROJETO CHOPCUT LIMPO!               ${NC}"
echo -e "${BLUE}==================================================${NC}"
echo -e "${YELLOW}Dica: Da próxima vez que compilar, o Gradle fará o${NC}"
echo -e "${YELLOW}download e indexação do zero, o que pode demorar${NC}"
echo -e "${YELLOW}um pouco mais.${NC}"
echo -e "${BLUE}==================================================${NC}"
