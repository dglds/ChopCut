#!/bin/bash
# Log de extração, strips e cache usando ripgrep

rg -i "(extractSegment|Extracting segment|Batch extraction|extractBatch|extracted|Extraindo)" logs_por_contexto.txt
echo ""
echo "--- STRIPS (Montagem) ---"
rg -i "(COMPLETED|drawBitmap|Stitch|Strip.*loaded|Strip.*carregada|frames.*extraídos)" logs_por_contexto.txt
echo ""
echo "--- CACHE (Operações) ---"
rg -i "(Cache HIT|Cache MISS|Cached segment|loadFromCache|saveToCache|Saving segment|Cache.*cleared|Cache.*removendo)" logs_por_contexto.txt
