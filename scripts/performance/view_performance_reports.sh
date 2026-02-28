#!/bin/bash

# Script para visualizar relatórios de performance do ChopCut
# Uso: ./scripts/view_performance_reports.sh [json|md|csv]

set -e

REPORT_DIR="/storage/emulated/0/Android/data/com.chopcut/files/performance_reports"
FORMAT="${1:-md}"  # Default: markdown

echo "═══════════════════════════════════════════════════════════"
echo "  ChopCut Performance Reports Viewer"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Verifica se há dispositivo conectado
if ! adb devices | grep -q "device$"; then
    echo "❌ Nenhum dispositivo conectado!"
    echo "   Conecte um dispositivo ou inicie um emulador."
    exit 1
fi

echo "📱 Dispositivo conectado!"
echo ""

# Lista relatórios disponíveis
echo "📊 Relatórios disponíveis (.$FORMAT):"
echo ""

adb shell "ls -lh $REPORT_DIR/*.$FORMAT 2>/dev/null" | grep -v "^total" || {
    echo "❌ Nenhum relatório .$FORMAT encontrado!"
    echo ""
    echo "💡 Execute os testes primeiro:"
    echo "   ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.PerformanceTestSuite"
    exit 1
}

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Pergunta qual relatório abrir
read -p "Digite o nome do arquivo (ou 'latest' para o mais recente): " FILENAME

if [ "$FILENAME" = "latest" ] || [ -z "$FILENAME" ]; then
    # Pega o mais recente
    LATEST=$(adb shell "ls -t $REPORT_DIR/*.$FORMAT 2>/dev/null | head -n 1" | tr -d '\r')

    if [ -z "$LATEST" ]; then
        echo "❌ Nenhum relatório encontrado!"
        exit 1
    fi

    echo ""
    echo "📄 Abrindo relatório mais recente: $(basename "$LATEST")"
else
    LATEST="$REPORT_DIR/$FILENAME"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Exibe o conteúdo
case "$FORMAT" in
    md|markdown)
        # Se tiver glow instalado, usa ele (melhor visualização de markdown)
        if command -v glow &> /dev/null; then
            adb shell "cat '$LATEST'" | glow -
        # Se tiver bat instalado, usa ele (syntax highlighting)
        elif command -v bat &> /dev/null; then
            adb shell "cat '$LATEST'" | bat --language markdown -
        else
            adb shell "cat '$LATEST'"
        fi
        ;;
    json)
        # Se tiver jq instalado, formata JSON
        if command -v jq &> /dev/null; then
            adb shell "cat '$LATEST'" | jq .
        else
            adb shell "cat '$LATEST'"
        fi
        ;;
    csv)
        # Se tiver column instalado, formata tabela
        if command -v column &> /dev/null; then
            adb shell "cat '$LATEST'" | column -t -s,
        else
            adb shell "cat '$LATEST'"
        fi
        ;;
    *)
        adb shell "cat '$LATEST'"
        ;;
esac

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💾 Para baixar o relatório:"
echo "   adb pull '$LATEST' ./"
echo ""
echo "📂 Localização no dispositivo:"
echo "   $LATEST"
echo ""
echo "═══════════════════════════════════════════════════════════"
