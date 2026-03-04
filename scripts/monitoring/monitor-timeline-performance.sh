#!/bin/bash
# ==============================================================================
# Script: monitor-timeline-performance.sh
#
# Descrição:
#   Monitora a performance de renderização do componente TimelineEditor em
#   tempo real. Filtra os logs para exibir apenas as métricas de performance
#   customizadas, como FPS, draw calls e tempos de frame.
#
# Foco:
#   - Performance de UI do Jetpack Compose
#   - Gargalos de renderização no scroll da timeline
#
# Tags ADB:
#   - TimelineEditor:I
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./monitor-timeline-performance.sh
#   3. Faça scroll na timeline do app para gerar os logs.
#
# ==============================================================================

# Limpa o logcat para uma nova sessão de monitoramento
adb logcat -c

echo "🚀 Iniciando monitoramento de performance da TimelineEditor..."
echo "    Faça scroll na timeline para ver as métricas de FPS, draw calls e tempo de frame."
echo "    Pressione Ctrl+C para parar."
echo "------------------------------------------------------------------"

# Filtra os logs para mostrar apenas as métricas de performance da TimelineEditor
adb logcat -s TimelineEditor:I | grep "TIMELINE PERFORMANCE LOG"
