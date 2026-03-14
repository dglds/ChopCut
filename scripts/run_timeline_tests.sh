#!/bin/bash

# Script para rodar todos os testes de timeline

set -e

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  🚀 RODANDO TODOS OS TESTES DE TIMELINE                     ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Verificar dispositivo
echo "📱 Verificando dispositivo..."
adb devices | grep -q "device$" || {
  echo "❌ Nenhum dispositivo Android conectado!"
  echo "   Conecte um dispositivo ou inicie um emulador."
  exit 1
}
echo "✅ Dispositivo conectado"
echo ""

# Limpar logcat
echo "🧹 Limpando logcat..."
adb logcat -c
echo "✅ Logcat limpo"
echo ""

# Rodar testes
echo "▶️ Iniciando testes..."
echo ""
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.timeline.*

RESULT=$?

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
if [ $RESULT -eq 0 ]; then
  echo "║  ✅ TESTES CONCLUÍDOS COM SUCESSO!                        ║"
else
  echo "║  ❌ TESTES FALHARAM!                                        ║"
fi
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "📋 Ver logs com:"
echo "   adb logcat -s TEST_*"
echo ""
echo "📋 Ver apenas resultados com:"
echo "   adb logcat -s TEST_* | grep -E '(✅|❌|▶️|🏁)'"
echo ""

exit $RESULT
