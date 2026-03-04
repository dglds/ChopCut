#!/bin/bash
# ==============================================================================
# Script: monitor-full-flow.sh
#
# Descrição:
#   Um monitor "tudo-em-um" que oferece uma visão consolidada de todo o
#   fluxo de carregamento de thumbnails. Este script combina os logs mais
#   críticos de cada componente para rastrear uma thumbnail desde a
#   solicitação inicial até a renderização na tela.
#
# Foco:
#   - Visão End-to-End do pipeline de thumbnails
#   - Diagnóstico rápido de problemas em qualquer estágio do fluxo
#
# Tags ADB:
#   - TimelineEditor:I
#   - ThumbnailExtractorBatch:I
#   - ThumbnailStripManager:I
#   - ThumbnailCacheManager:D
#   - ThumbnailViewModel:D
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./monitor-full-flow.sh
#   3. Use a timeline no app para gerar um fluxo completo de logs.
#
# ==============================================================================

# Limpa o logcat
adb logcat -c

echo "🚀 Iniciando monitoramento do fluxo completo de thumbnails (End-to-End)..."
echo "    Use a timeline para rastrear o ciclo de vida de uma thumbnail."
echo "    Pressione Ctrl+C para parar."
echo "------------------------------------------------------------------"

# Filtra e combina os logs mais importantes de cada componente do fluxo
adb logcat -s TimelineEditor:I ThumbnailExtractorBatch:I ThumbnailStripManager:I ThumbnailCacheManager:D ThumbnailViewModel:D
