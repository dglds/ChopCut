#!/bin/bash
# ==============================================================================
# Script: monitor-extraction-pipeline.sh
#
# Descrição:
#   Monitora o pipeline de extração de thumbnails, desde a extração de frames
#   individuais até a criação das strips. Ideal para diagnosticar lentidão
#   na geração de novas thumbnails.
#
# Foco:
#   - Eficiência da extração de frames (ThumbnailExtractorBatch)
#   - Tempo de criação e costura das strips (ThumbnailStripManager)
#
# Tags ADB:
#   - ThumbnailExtractorBatch:I
#   - ThumbnailStripManager:I
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./monitor-extraction-pipeline.sh
#   3. Selecione um vídeo novo no app para iniciar a extração.
#
# ==============================================================================

# Limpa o logcat
adb logcat -c

echo "🚀 Iniciando monitoramento do pipeline de extração de thumbnails..."
echo "    Selecione um vídeo para analisar os tempos de extração e criação de strips."
echo "    Pressione Ctrl+C para parar."
echo "------------------------------------------------------------------"

# Filtra os logs para mostrar informações relevantes da extração
adb logcat -s ThumbnailExtractorBatch:I ThumbnailStripManager:I
