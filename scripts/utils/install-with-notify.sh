#!/bin/bash
# Script wrapper para ./gradlew :app:installDebug com notificação

cd "$(dirname "$0")/.."

echo "🔨 Iniciando build e instalação..."
./gradlew :app:installDebug "$@"

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Build e instalação concluídos com sucesso!"
    zenity --notification \
        --text="✅ ChopCut instalado com sucesso!" \
        --timeout=3 2>/dev/null || true
else
    echo "❌ Falha no build ou instalação"
    zenity --notification \
        --text="❌ Falha ao instalar ChopCut" \
        --timeout=5 2>/dev/null || true
fi

exit $EXIT_CODE
