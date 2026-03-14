#!/bin/bash

# Script para rodar um teste específico de timeline

set -e

TEST_CLASS=$1

if [ -z "$TEST_CLASS" ]; then
  echo "╔══════════════════════════════════════════════════════════╗"
  echo "║  Uso: ./run_single_timeline_test.sh <NomeDoTeste>          ║"
  echo "╚══════════════════════════════════════════════════════════╝"
  echo ""
  echo "Testes disponíveis:"
  echo "  - ThreadPoolBoundaryTest"
  echo "  - ExtractionPriorityTest"
  echo "  - QueueCancellationTest"
  echo "  - CacheWindowTest"
  echo "  - MemoryLeakTest"
  echo ""
  echo "Exemplo:"
  echo "  ./run_single_timeline_test.sh ThreadPoolBoundaryTest"
  echo ""
  exit 1
fi

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  🚀 RODANDO TESTE: $TEST_CLASS                       ║"
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

# Rodar teste
echo "▶️ Iniciando teste: $TEST_CLASS..."
echo ""

FULL_CLASS="com.chopcut.timeline.$TEST_CLASS"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=$FULL_CLASS

RESULT=$?

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
if [ $RESULT -eq 0 ]; then
  echo "║  ✅ TESTE $TEST_CLASS PASSOU!                             ║"
else
  echo "║  ❌ TESTE $TEST_CLASS FALHOU!                            ║"
fi
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "📋 Ver logs com:"
echo "   adb logcat -s TEST_*"
echo ""
echo "📋 Ver logs específicos com:"
echo "   adb logcat -s TEST_EXTRACTION.POOL:* | grep -E '(✅|❌|▶️|🏁)'"
echo ""

exit $RESULT
