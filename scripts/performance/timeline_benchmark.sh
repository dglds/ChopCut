#!/bin/bash

echo "🎬 Iniciando Teste de Performance de Renderização da Timeline..."

# Limpar Logcat
adb logcat -c

# Iniciar aplicativo
echo "📱 Abrindo o app para teste na Timeline..."
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
sleep 5

# Para testar a timeline, precisamos garantir que tem um vídeo aberto.
# Em um Monkey Test cego, forçamos swipes horizontais num local da tela
# onde presumimos que a timeline está.

echo "👉 Simulando scrolls e zooms na Timeline..."

# Monitorar os recursos em background
adb shell "top -n 20 -d 1 | grep com.chopcut" > performance_reports/timeline_top.log &
TOP_PID=$!

(
  for i in {1..20}; do
    MEM=$(adb shell dumpsys meminfo com.chopcut | grep "TOTAL PSS:" | awk '{print $3}')
    if [ ! -z "$MEM" ]; then
      MEM_MB=$(echo "scale=2; $MEM / 1024" | bc)
      echo "⏱️ Render Timeline ($i) | Memória: $MEM_MB MB"
    fi
    
    # Simular scrolls rápidos na tela para forçar re-renderização de thumbnails
    # Arrastando do meio da tela (x=500) para o lado esquerdo (x=100) para simular scroll pra frente
    adb shell input swipe 800 1800 200 1800 100
    
    # A cada 3 scrolls, dá um tap para forçar o player a seekar
    if [ $((i % 3)) -eq 0 ]; then
       adb shell input tap 500 1800
    fi
    
    sleep 0.5
  done
)

# Coletar framerate do Compose / Choreographer
echo "📊 Coletando dados de fluidez de quadros (Jank stats)..."
adb shell dumpsys gfxinfo com.chopcut framestats > performance_reports/timeline_framestats.txt

kill $TOP_PID 2>/dev/null

echo "✅ Teste concluído!"
echo "   Verifique os Janks e Dropped Frames em: performance_reports/timeline_framestats.txt"

