#!/bin/bash

echo "🚀 Iniciando Teste de UI Interativo do ChopCut"

# Verifica se o diretório de relatórios existe
mkdir -p performance_reports

# 1. Limpar logcat
adb logcat -c

# 2. Iniciar o app forçadamente
echo "📱 Abrindo aplicativo..."
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
sleep 3

# 3. Monitorar memória e CPU em background enquanto o teste roda
echo "📊 Iniciando monitoramento de recursos..."
adb shell "top -n 30 -d 1 | grep com.chopcut" > performance_reports/live_top.log &
TOP_PID=$!

# Exibir uso de memória em tempo real no terminal (processo isolado)
echo "----------------------------------------------------"
echo "  MONITORAMENTO AO VIVO (Memória: PSS)"
echo "----------------------------------------------------"
(
  for i in {1..30}; do
    MEM=$(adb shell dumpsys meminfo com.chopcut | grep "TOTAL PSS:" | awk '{print $3}')
    if [ ! -z "$MEM" ]; then
      # Converter KB para MB
      MEM_MB=$(echo "scale=2; $MEM / 1024" | bc)
      echo "⏱️ Seg $i | Memória: $MEM_MB MB"
    fi
    sleep 1
  done
) &
MEM_PID=$!

# 4. Simular interações do usuário (Monkey ou Inputs exatos)
echo "👉 Simulando interações e carregamento..."

# Simular toques genéricos na tela (Monkey test reduzido e controlado)
# O monkey enviará 500 eventos de toque/scroll focados apenas no seu app, ignorando botões do sistema
adb shell monkey -p com.chopcut -c android.intent.category.LAUNCHER --pct-touch 50 --pct-motion 50 --ignore-crashes --ignore-timeouts --ignore-security-exceptions -v 500 > /dev/null

echo "✅ Interações concluídas. Aguardando estabilização..."
sleep 2

# 5. Parar monitoramento
kill $TOP_PID 2>/dev/null
kill $MEM_PID 2>/dev/null

echo "----------------------------------------------------"
echo "🎉 Teste interativo finalizado!"
echo "   Os dados de CPU foram salvos em: performance_reports/live_top.log"
