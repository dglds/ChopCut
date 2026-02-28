#!/bin/bash

# Script para extrair relatórios JSON do logcat
# Uso: ./scripts/extract_performance_json.sh [output_dir]

OUTPUT_DIR="${1:-./performance_reports}"
mkdir -p "$OUTPUT_DIR"

echo "📊 Extraindo relatórios de performance do logcat..."

# Limpar logcat primeiro
adb logcat -c

# Executar testes
echo "🚀 Executando testes..."
./gradlew connectedDebugAndroidTest

# Extrair JSON do logcat
echo "📥 Extraindo JSONs..."

adb logcat -d -s "System.out:I" | \
  awk '/📊 JSON REPORT:/{flag=1; next} /────────────────/{flag=0} flag' | \
  grep -v "^$" > "$OUTPUT_DIR/combined_report.json"

# Contar relatórios
REPORT_COUNT=$(grep -c '"device":' "$OUTPUT_DIR/combined_report.json" || echo "0")

echo ""
echo "✅ Concluído!"
echo "📁 Relatórios salvos em: $OUTPUT_DIR/combined_report.json"
echo "📊 Total de relatórios: $REPORT_COUNT"
echo ""
echo "💡 Para ver:"
echo "   cat $OUTPUT_DIR/combined_report.json | jq ."
