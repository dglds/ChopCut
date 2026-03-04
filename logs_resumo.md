# Logs por Contexto da Aplicação (Resumo)

## 🟡 EXTRAÇÃO DE THUMBS
**O que acontece:** Extração de frames individuais do vídeo para criar thumbnails

### Logs Principais
- `extractSegment STARTED` - Iniciou extração de um segmento
- `Extracting segment X: Y frames` - Extraindo X frames do segmento
- `Batch extraction returned X frames` - Extração em lote completada
- `extractBatch COMPLETED` - Extração de lote finalizada
- `Extraídos X/Y frames com sucesso` - Quantidade de frames extraídos
- `Extracting frame at XXXms` - Extraindo frame específico
- `Frame at XXXms extracted successfully` - Frame extraído com sucesso

### Arquivos
- ThumbnailExtractorBatch.kt
- ThumbnailStripManager.kt
- ThumbnailCacheManager.kt

---

## 🟣 MONTAGEM DAS STRIPS
**O que acontece:** Combinação dos frames extraídos em strips horizontais

### Logs Principais
- `ThumbnailStripManager.extractSegment STARTED` - Iniciou montagem
- `Batch extraction returned X frames` - Frames recebidos para montar
- `Segment X (Y frames, WxH, RGB_565) - BATCH MODE` - Montagem em processo
- `ThumbnailStripManager.extractSegment COMPLETED` - Strip montada com sucesso
- `Strip X loaded successfully` - Strip carregada
- `Strip X loaded, total strips: Y` - Progresso de carregamento
- `Strips carregadas COM CACHE: X/Y` - Strips em memória

### Arquivos
- ThumbnailStripManager.kt
- ThumbnailViewModel.kt
- ThumbnailAspectMonitor (monitoramento)

---

## 🔵 CACHE (Operações)
**O que acontece:** Gerenciamento de cache de memória (LRU) e disco

### INSERÇÃO (Salvar)
- `Cached segment X (Xms, XKB)` - Strip salva no disco
- `Saving segment X to cache...` - Salvando em disco
- `Cache PUT: key (size: X/Y)` - Adicionado ao cache de memória

### CONSULTA (Buscar)
- `Cache enabled: true/false` - Status do cache
- `Cache HIT: key` - Encontrado no cache de memória
- `Cache MISS: key` - Não encontrado, precisa extrair
- `Cache HIT (bitmap válido) for segment X` - Bitmap válido encontrado
- `Cache HIT mas bitmap está reciclado` - Bitmap reciclado, removendo
- `✓ Cache HIT for segment X (Xms)` - Lido do disco com sucesso

### RECUPERAÇÃO (Carregar)
- `Cache MISS for segment X, will extract` - Não encontrado, vai extrair
- `Extracting segment X (cache miss)` - Extraindo porque não estava em cache
- `Segment X extracted successfully, size=WxH` - Extraído com sucesso
- `loadFromCache` - Carregando do disco

### LIMPEZA
- `Cache CLEARED` - Cache de memória limpo
- `Cache REMOVING: key` - Removendo item específico
- `Cache FULL: removendo key` - Cheio, removendo item antigo (LRU)
- `Cache size XMB exceeds limit, trimming...` - Excedeu limite, limpando
- `Trimmed cache, deleted XKB` - Limpou XKB do cache

### Arquivos
- ThumbnailCacheManager.kt (memória LRU)
- ThumbnailStripManager.kt (disco)
- ThumbnailCache.kt (LRU)

---

## 📊 Estrutura do Cache

### Cache de Memória (LRU)
- **Capacidade:** 100 strips (~43MB)
- **Política:** Least Recently Used
- **Formato:** Bitmap na memória RAM
- **Logs:** `Cache PUT`, `Cache HIT`, `Cache MISS`, `Cache FULL`

### Cache de Disco
- **Capacidade:** 200MB
- **Política:** LRU baseado em lastModified
- **Formato:** WEBP (70% qualidade, RGB_565)
- **Logs:** `Cached segment`, `loadFromCache`, `SaveToCache`

---

## 🔄 Fluxo Completo

1. **Extração**
   ```
   extractSegment STARTED
   → Extracting segment X: Y frames
   → Batch extraction returned Y frames
   → Extraídos Y/Y frames com sucesso
   ```

2. **Montagem**
   ```
   Segment X (Y frames, WxH, RGB_565) - BATCH MODE
   → Stitch frames na strip (drawBitmap)
   → ThumbnailStripManager.extractSegment COMPLETED
   ```

3. **Cache**
   ```
   Cache MISS (primeira vez)
   → Extracting segment (cache miss)
   → Segment extracted successfully
   → Cached segment (disco)
   → Cache PUT (memória)
   
   Cache HIT (próximas vezes)
   → Cache HIT (bitmap válido)
   → Retorna imediatamente
   ```

---

## 🎯 Filtros para adb logcat

### Extração
```bash
adb logcat -v brief | grep --line-buffered -iE "(extractSegment|Extracting|Batch|extractBatch|Extraindo|extracted.*thumbnail)"
```

### Montagem
```bash
adb logcat -v brief | grep --line-buffered -iE "(COMPLETED|drawBitmap|Stitch|Strip.*loaded|frames.*extraídos|segment.*completed)"
```

### Cache
```bash
adb logcat -v brief | grep --line-buffered -iE "(Cache HIT|Cache MISS|Cached|saveToCache|Saving|Cache PUT|Cache.*cleared)"
```

### Todos (por processo ChopCut)
```bash
adb logcat -v brief --pid=$(adb shell pidof com.chopcut) | grep --line-buffered -iE "(extract|Cache|Strip.*loaded|frames.*extraídos)"
```
