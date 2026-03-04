#!/bin/bash
# Log de extração, strips e cache via adb logcat

echo "=== EXTRAÇÃO DE THUMBS (Amarelo) ==="
echo "====================================="

adb logcat -c && adb logcat -v brief -s "ThumbnailStrip:*" "ThumbnailAspectMonitor:*" "ThumbnailCacheManager:*" "ThumbnailViewModel:*" "ThumbnailExtractorBatch:*" | grep --line-buffered -E "(extractSegment|Extracting segment|Batch extraction|extractBatch|Extraindo)" | sed --unbuffered $'s/.*extractSegment.*/\x1b[33m&\x1b[0m/g; s/.*Extracting segment.*/\x1b[33m&\x1b[0m/g; s/.*Batch extraction.*/\x1b[33m&\x1b[0m/g; s/.*extractBatch.*/\x1b[33m&\x1b[0m/g; s/.*Extraindo.*/\x1b[33m&\x1b[0m/g' &

PID_EXTRACTION=$!

sleep 1

echo "=== MONTAGEM DAS STRIPS (Roxo) ==="
echo "====================================="

adb logcat -v brief -s "ThumbnailStrip:*" "ThumbnailAspectMonitor:*" "ThumbnailCacheManager:*" "ThumbnailViewModel:*" | grep --line-buffered -E "(COMPLETED|drawBitmap|Stitch|Strip.*loaded|Strip.*carregada|frames.*extraídos)" | sed --unbuffered $'s/.*COMPLETED.*/\x1b[35m&\x1b[0m/g; s/.*drawBitmap.*/\x1b[35m&\x1b[0m/g; s/.*Stitch.*/\x1b[35m&\x1b[0m/g; s/.*Strip.*loaded.*/\x1b[35m&\x1b[0m/g; s/.*Strip.*carregada.*/\x1b[35m&\x1b[0m/g; s/.*frames.*extraídos.*/\x1b[35m&\x1b[0m/g' &

PID_STRIPS=$!

sleep 1

echo "=== OPERAÇÕES DE CACHE (Ciano) ==="
echo "====================================="

adb logcat -v brief -s "ThumbnailStrip:*" "ThumbnailCacheManager:*" "ThumbnailCache:*" | grep --line-buffered -E "(Cache HIT|Cache MISS|Cached segment|loadFromCache|saveToCache|Saving segment|Cache.*cleared|Cache.*removendo)" | sed --unbuffered $'s/.*Cache HIT.*/\x1b[36m&\x1b[0m/g; s/.*Cache MISS.*/\x1b[36m&\x1b[0m/g; s/.*Cached segment.*/\x1b[36m&\x1b[0m/g; s/.*loadFromCache.*/\x1b[36m&\x1b[0m/g; s/.*saveToCache.*/\x1b[36m&\x1b[0m/g; s/.*Saving segment.*/\x1b[36m&\x1b[0m/g; s/.*Cache.*cleared.*/\x1b[36m&\x1b[0m/g; s/.*Cache.*removendo.*/\x1b[36m&\x1b[0m/g'

trap "kill $PID_EXTRACTION $PID_STRIPS 2>/dev/null" EXIT
