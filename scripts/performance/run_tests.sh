#!/bin/bash

# Define paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
DATA_FILE="$SCRIPT_DIR/../reports/performance_data.js"
REPORT_FILE="$SCRIPT_DIR/../reports/report.html"

echo "🚀 Iniciando testes de performance..."

# Limpar o logcat para evitar sujeira de execuções anteriores
echo "🧹 Limpando logs anteriores..."
adb logcat -c
adb logcat -G 16M # Aumenta o buffer do logcat para não perder JSONs grandes
sleep 2

# Executar os testes
echo "⏳ Executando MultiVideoPerformanceTest no dispositivo..."
# Usamos -Pandroid.testInstrumentationRunnerArguments.class para focar apenas neste teste
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.MultiVideoPerformanceTest

echo "📥 Extraindo resultados..."
sleep 2 # Espera os últimos logs serem gravados

# Extrair as linhas entre ===JSON_START=== e ===JSON_END=== do logcat
TEMP_JSON="$SCRIPT_DIR/temp_results.json"
adb logcat -d -v raw | \
  awk '/===JSON_START===/{flag=1; next} /===JSON_END===/{flag=0; print ","; next} flag' > "$TEMP_JSON"

# Conta quantos JSONs foram encontrados
JSON_COUNT=$(grep -c "{" "$TEMP_JSON" || echo "0")
echo "📊 Encontrados dados de $JSON_COUNT vídeo(s)."

# Verifica se capturou algo
if [ "$JSON_COUNT" -eq "0" ]; then
    echo "⚠️  Nenhum JSON encontrado no formato esperado."
    echo "   Verifique se o teste realmente rodou e imprimiu os logs."
fi

CURRENT_DATE=$(date "+%d/%m/%Y %H:%M:%S")

echo "// Arquivo gerado automaticamente em $CURRENT_DATE" > "$DATA_FILE"
echo "const lastTestDate = '$CURRENT_DATE';" >> "$DATA_FILE"
echo "const performanceData = [" >> "$DATA_FILE"
# Remover a última vírgula extra, se houver
sed '$ s/,$//' "$TEMP_JSON" >> "$DATA_FILE"
echo "];" >> "$DATA_FILE"

rm "$TEMP_JSON"

echo "✅ Extração concluída! Dados salvos em $DATA_FILE"
echo "🌐 Abrindo o relatório no navegador..."

# Abrir no navegador e desacoplar do terminal (roda em background)
if command -v xdg-open > /dev/null; then
  xdg-open "$REPORT_FILE" &> /dev/null &
elif command -v open > /dev/null; then
  open "$REPORT_FILE" &> /dev/null &
fi

# Desacopla qualquer processo em background gerado no script
disown -a 2>/dev/null || true

echo "🎉 Tudo pronto! Você pode continuar usando o terminal."