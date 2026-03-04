#!/bin/bash
# ==============================================================================
# Script: monitor-cache-performance.sh
#
# Descrição:
#   Audita a performance do cache de thumbnails em memória e em disco.
#   Este script é essencial para verificar a eficácia da estratégia de cache,
#   identificando se as strips estão sendo reutilizadas ou re-extraídas.
#
# Foco:
#   - Taxa de acerto (HIT) e erro (MISS) do cache
#   - Comportamento do cache LRU (Least Recently Used)
#
# Tags ADB:
#   - ThumbnailCacheManager:D
#   - ThumbnailStripManager:I
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./monitor-cache-performance.sh
#   3. Navegue pela timeline para frente e para trás para testar o cache.
#
# ==============================================================================

# Limpa o logcat
adb logcat -c

echo "🚀 Iniciando monitoramento de performance do cache..."
echo "    Navegue pela timeline para analisar os hits e misses do cache."
echo "    Pressione Ctrl+C para parar."
echo "------------------------------------------------------------------"

# Filtra os logs para mostrar apenas eventos de cache HIT e MISS
adb logcat -s ThumbnailCacheManager:D ThumbnailStripManager:I | grep -E "Cache HIT|Cache MISS"
