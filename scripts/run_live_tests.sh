#!/bin/bash

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

# 1. Verifica conexão silenciosamente
./scripts/check_device.sh > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Erro: Dispositivo não encontrado.${NC}"
    exit 1
fi

# Determina classe (padrão Suite Central)
TEST_CLASS=${1:-"com.chopcut.AllAppTests"}

echo -e "${CYAN}🚀 Executando: ${YELLOW}$TEST_CLASS${NC}"
echo -e "${CYAN}📊 Filtrando apenas RESULTADOS...${NC}\n"

# 2. Limpa logs antigos
adb logcat -c

# 3. Inicia captura silenciosa
# Filtramos para mostrar apenas as bordas das tabelas e as palavras-chave de resultado
adb logcat -s "System.out:I" "TestRunner:I" | grep --line-buffered -E "(║|╠|╚|╔|═|─|DETAILED RESULTS|VIDEO:|Performance|Average|Total|Throughput|started:|finished:)" | sed -u 's/I\/System.out(//g; s/)//g; s/I\/TestRunner(//g' &
LOGCAT_PID=$!

# 4. Executa Gradle
# Mostramos o progresso normal do Gradle para que você saiba que está compilando e instalando o APK no celular
echo -e "${YELLOW}⏳ Compilando testes e instalando no dispositivo (isso pode levar 1-2 minutos)...${NC}"
./gradlew app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS
GRADLE_EXIT_CODE=$?

# 5. Finaliza
sleep 1
kill $LOGCAT_PID 2>/dev/null
wait $LOGCAT_PID 2>/dev/null

echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
if [ $GRADLE_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Testes concluídos com SUCESSO.${NC}"
else
    echo -e "${RED}❌ Testes FALHARAM. Verifique os logs acima para detalhes do erro.${NC}"
fi
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Retorna o código de erro real do teste para o Gradle/Terminal
exit $GRADLE_EXIT_CODE
