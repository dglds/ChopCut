# Plano de Otimização de Thumbnails para Vídeos Longos

## 📋 Resumo Executivo

**Problema:** Extração de thumbnails em vídeos longos (10min+) está demorando muito para carregar, impactando a experiência do usuário.

**Causa Raiz Identificada:**
- `MediaMetadataRetriever` é síncrono e lento para múltiplas extrações
- Cada thumbnail requer um novo seek no vídeo
- Cache de memória existe (ThumbnailCache) mas não está sendo utilizado no TimelineEditor
- Sem pre-fetching inteligente
- Sem cache em disco

**Meta de Performance:**
- Vídeo de 10min: < 3 segundos para primeira renderização
- Scroll suave com thumbnails carregando progressivamente
- Cache eficiente para reabrir vídeos

---

## FASE 0: Documentação e Descoberta de APIs ✅

### APIs Disponíveis e Padrões Identificados

**Fontes Consultadas:**
- `app/src/main/java/com/chopcut/data/thumbnail/ThumbnailExtractor.kt` (linhas 35-71)
- `app/src/main/java/com/chopcut/ui/components/TimelineEditor.kt` (linhas 104-136)
- `app/src/main/java/com/chopcut/data/thumbnail/ThumbnailCache.kt` (completo)
- `PLANO_MIGRACAO_VIDEO_PROCESSING.md` (estratégia Media3)

### APIs Permitidas e Recomendadas

✅ **MediaMetadataRetriever** (Android nativo)
- `getScaledFrameAtTime()` - Extração de frame (uso atual)
- ⚠️ **Limitação:** Síncrono e lento para múltiplas chamadas

✅ **ThumbnailCache** (já implementado em `/app/src/main/java/com/chopcut/data/thumbnail/ThumbnailCache.kt`)
- `get(uri, positionMs)` - Recupera do cache
- `put(uri, positionMs, bitmap)` - Adiciona ao cache
- LRU com maxSize = 50 thumbnails
- ⚠️ **Problema:** Não está sendo usado no TimelineEditor

✅ **ThumbnailUtils** (alternativa em `/app/src/main/java/com/chopcut/ui/components/ThumbnailUtils.kt`)
- `getThumbnail(context, uri, timeMs)` - Extração simples
- Cache baseado em LruCache (1/8 da memória disponível)
- ⚠️ **Problema:** Cache duplicado/paralelo ao ThumbnailCache

❌ **Media3 Transformer** (futuro)
- Menção em `PLANO_MIGRACAO_VIDEO_PROCESSING.md`
- Migração planejada mas não implementada
- **Fora do escopo desta otimização**

### Anti-padrões Identificados

❌ **NÃO FAZER:**
- Criar novos `MediaMetadataRetriever` para cada thumbnail (já criado a cada chamada)
- Chamar `getScaledFrameAtTime()` na thread principal
- Ignorar o ThumbnailCache existente
- Extração sequencial sem paralelização

---

## FASE 1: Integração do Cache Existente (Prioridade ALTA)

### Objetivo
Integrar o `ThumbnailCache` existente no `TimelineEditor` para evitar re-extração de thumbnails já processados.

### O Que Implementar

**1. Injetar ThumbnailCache no TimelineEditor**
```kotlin
// Fonte: Criar novo parâmetro no TimelineEditor
// Local: app/src/main/java/com/chopcut/ui/components/TimelineEditor.kt

@Composable
fun TimelineEditor(
    // ... parâmetros existentes
    thumbnailCache: ThumbnailCache = remember { ThumbnailCache() } // NOVO
)
```

**2. Substituir chamadas diretas ao ThumbnailUtils**
```kotlin
// ANTES (linha 126):
val bmp = ThumbnailUtils.getThumbnail(context, videoUri, timeMs)

// DEPOIS:
val bmp = thumbnailCache.get(videoUri.toString(), timeMs)
    ?: ThumbnailUtils.getThumbnail(context, videoUri, timeMs)?.also {
        thumbnailCache.put(videoUri.toString(), timeMs, it)
    }
```

**3. Adicionar flag para desativar cache (requisito do usuário)**
```kotlin
// Adicionar parâmetro:
enableCache: Boolean = true,

// Usar no código:
if (enableCache) {
    val cached = thumbnailCache.get(videoUri.toString(), timeMs)
    if (cached != null) {
        withContext(Dispatchers.Main) { thumbnails[timeMs] = cached }
        return@launch
    }
}
```

### Verificação
- [ ] Cache HIT registrado no Logcat para thumbnails já extraídos
- [ ] Segunda abertura do mesmo vídeo carrega instantaneamente
- [ ] Flag `enableCache = false` desativa o cache completamente

### Referências de Código
- **Cache implementation:** `ThumbnailCache.kt:35-46` (método `get`)
- **Cache insertion:** `ThumbnailCache.kt:55-67` (método `put`)
- **Current usage:** `TimelineEditor.kt:104-136` (LaunchedEffect)

---

## FASE 2: Pre-fetching Inteligente (Prioridade ALTA)

### Objetivo
Carregar thumbnails antes que eles sejam visíveis na tela, criando a ilusão de carregamento instantâneo.

### O Que Implementar

**1. Aumentar janela de pre-fetching**
```kotlin
// Fonte: Modificar TimelineEditor.kt:112-116
// ATUAL:
val visibleDurationPx = with(density) { 1000.dp.toPx() }

// OTIMIZADO:
val prefetchBuffer = with(density) { 2000.dp.toPx() } // 2x a tela
val visibleDurationPx = prefetchBuffer
```

**2. Adicionar pre-fetching agressivo na inicialização**
```kotlin
// NOVO: Carregar primeiros N thumbnails imediatamente
LaunchedEffect(videoUri, videoDurationMs) {
    if (videoDurationMs == 0L) return@LaunchedEffect

    val initialThumbsToLoad = minOf(20, (videoDurationMs / 1000))
    coroutineScope {
        for (sec in 0..initialThumbsToLoad) {
            launch(Dispatchers.IO) {
                loadThumbnail(sec * 1000)
            }
        }
    }
}

private suspend fun loadThumbnail(timeMs: Long) {
    // Mesma lógica de extração com cache
}
```

**3. Priorizar thumbnails visíveis primeiro**
```kotlin
// Implementar fila de prioridades
val thumbnailPriority = when {
    timeMs in visibleRange -> Priority.HIGH
    timeMs in prefetchRange -> Priority.MEDIUM
    else -> Priority.LOW
}
```

### Verificação
- [ ] Thumbnails aparecem instantaneamente ao abrir vídeo
- [ ] Scroll suave sem "placeholders" vazios
- [ ] Logcat mostra carregamento em ordem de prioridade

### Referências de Código
- **Current fetch logic:** `TimelineEditor.kt:109-136` (LaunchedEffect)
- **Scroll-based loading:** `TimelineEditor.kt:118-119` (cálculo de range)

---

## FASE 3: Paralelização de Extração (Prioridade MÉDIA)

### Objetivo
Extrair múltiplos thumbnails simultaneamente usando coroutines, em vez de sequencialmente.

### O Que Implementar

**1. Usar CoroutineScope com múltiplas threads**
```kotlin
// Fonte: Modificar TimelineEditor.kt:121-134
// ATUAL (sequencial):
for (sec in startTimeSec..endTimeSec) {
    launch(Dispatchers.IO) { ... }
}

// OTIMIZADO (paralelo com limite):
val parallelism = 4 // Máximo 4 extrações simultâneas
val chunks = (startTimeSec..endTimeSec).chunked(parallelism)

chunks.forEach { chunk ->
    coroutineScope {
        chunk.forEach { sec ->
            launch(Dispatchers.IO) {
                loadThumbnail(sec * 1000)
            }
        }
    }
    // Esperar chunk completar antes do próximo
}
```

**2. Usar Dispatchers.IO limitado**
```kotlin
// Limitar para evitar sobrecarregar o disco/CPU
val limitedDispatcher = Dispatchers.IO.limitedParallelism(4)
```

### Verificação
- [ ] 4 thumbnails sendo extraídos simultaneamente (verificar no profiler)
- [ ] Tempo de extração reduzido em ~60% para vídeos longos
- [ ] Sem ANR (Application Not Responding)

### Referências de Código
- **Current extraction:** `TimelineEditor.kt:121-134`
- **Dispatcher docs:** https://kotlinlang.org/api/kotlinx-coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html

---

## FASE 4: Cache em Disk (Prioridade BAIXA - Futuro)

### Objetivo
Persistir thumbnails em disco para survived app restarts.

### O Que Implementar

**1. Criar DiskThumbnailCache**
```kotlin
// Fonte: NOVO arquivo app/src/main/java/com/chopcut/data/thumbnail/DiskThumbnailCache.kt

class DiskThumbnailCache(
    private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "thumbnails")

    fun get(uri: String, positionMs: Long): Bitmap? {
        val file = getFile(uri, positionMs)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    fun put(uri: String, positionMs: Long, bitmap: Bitmap) {
        val file = getFile(uri, positionMs)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }

    private fun getFile(uri: String, positionMs: Long): File {
        val hash = uri.hashCode().toString(16)
        return File(cacheDir, "${hash}_${positionMs}.jpg")
    }
}
```

**2. Implementar cache hierárquico**
```kotlin
// Memory -> Disk -> Network (extract)
suspend fun getThumbnail(uri: String, positionMs: Long): Bitmap? {
    // 1. Tentar memória
    memoryCache.get(uri, positionMs)?.let { return it }

    // 2. Tentar disco
    diskCache.get(uri, positionMs)?.let { bmp ->
        memoryCache.put(uri, positionMs, bmp)
        return bmp
    }

    // 3. Extrair do vídeo
    return extractFromVideo(uri, positionMs)?.also { bmp ->
        diskCache.put(uri, positionMs, bmp)
        memoryCache.put(uri, positionMs, bmp)
    }
}
```

### Verificação
- [ ] Thumbnails persistem após fechar app
- [ ] Cache em disk respeita limite de espaço (ex: 50MB)
- [ ] Limpeza automática de cache antigo

### Referências
- **Android cache best practices:** https://developer.android.com/training/data-storage/app-specific#kotlin
- **Bitmap compression:** Bitmap.CompressFormat.JPEG

---

## FASE 5: Extração Baseada em Keyframes (Prioridade BAIXA - Experimental)

### Objetivo
Extrair apenas keyframes (I-frames) em vez de frames arbitrários, reduzindo seeks no vídeo.

### O Que Implementar

**1. Detectar keyframes usando MediaExtractor**
```kotlin
// Fonte: NOVO método em ThumbnailExtractor.kt

suspend fun extractAtKeyframes(
    uri: Uri,
    durationMs: Long,
    targetCount: Int
): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri)

    val videoTrack = findVideoTrack(extractor)
    extractor.selectTrack(videoTrack)

    val keyframes = mutableListOf<Long>()
    val thumbnailInterval = durationMs / targetCount

    for (i in 0 until targetCount) {
        val targetTime = i * thumbnailInterval
        extractor.seekTo(targetTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        keyframes.add(extractor.sampleTime / 1000) // Convert to ms
    }

    // Extrair apenas nos keyframes detectados
    keyframes.map { timeMs ->
        Pair(timeMs, extractAt(uri, timeMs, 320, 180))
    }.filter { it.second != null }
}
```

### Verificação
- [ ] Extração 30-50% mais rápida (depende da codificação do vídeo)
- [ ] Thumbnails podem não estar exatamente em cada segundo
- [ ] Funciona melhor em vídeos com muitos keyframes

### Referências
- **MediaExtractor docs:** https://developer.android.com/reference/android/media/MediaExtractor
- **SEEK_TO_PREVIOUS_SYNC:** Garante seek para keyframe mais próximo

---

## FASE 6: Verificação e Testes

### Checklist Final

**Performance:**
- [ ] Vídeo de 1min: < 1s para carregar thumbnails iniciais
- [ ] Vídeo de 10min: < 3s para carregar thumbnails iniciais
- [ ] Scroll suave (60fps) com thumbnails carregando
- [ ] Sem ANRs ou travamentos

**Cache:**
- [ ] Flag `enableCache = false` funciona corretamente
- [ ] Cache HIT registrado no Logcat
- [ ] Cache respeita limite de 50 thumbnails
- [ ] Memória dentro dos limites (verificar no Memory Profiler)

**UX:**
- [ ] Indicador de carregamento visível durante extração
- [ ] Thumbnails aparecem progressivamente (não bloqueia UI)
- [ ] Reabrir vídeo carrega instantaneamente do cache

**Testes Manuais:**
```bash
# 1. Testar com vídeo curto (1min)
# Tempo esperado: < 1s

# 2. Testar com vídeo longo (10min)
# Tempo esperado: < 3s

# 3. Testar scroll rápido
# Esperado: Sem travamentos, carregamento progressivo

# 4. Testar cache desativado
# Esperado: Re-extração a cada scroll

# 5. Testar memória (Memory Profiler)
# Esperado: < 50MB para thumbnails
```

### Comandos de Verificação

```bash
# Verificar uso do ThumbnailCache
grep -r "ThumbnailCache" app/src/main/java/com/chopcut/ui/

# Verificar logs de cache
adb logcat | grep "Cache"

# Verificar performance com Systrace
adb shell am start -n com.chopcut/.MainActivity
python systrace.py --time=10 -o trace.html sched freq idle am wm gfx view binder_driver hal dalvik camera input res
```

### Anti-padrões para Evitar

❌ **NÃO FAZER:**
- Extrair thumbnails na thread principal
- Criar mais de 4 coroutines paralelas (sobrecarga)
- Ignorar erros de extração (tratar logmente)
- Usar cache em disk sem limite de tamanho
- Implementar Media3 agora (fora do escopo, usar plano existente)

---

## Cronograma Sugerido

| Fase | Tempo Estimado | Prioridade | Impacto |
|------|----------------|------------|---------|
| Fase 1: Cache Existente | 2h | ALTA | ⭐⭐⭐⭐⭐ |
| Fase 2: Pre-fetching | 3h | ALTA | ⭐⭐⭐⭐ |
| Fase 3: Paralelização | 2h | MÉDIA | ⭐⭐⭐ |
| Fase 4: Cache Disk | 4h | BAIXA | ⭐⭐ |
| Fase 5: Keyframes | 6h | BAIXA | ⭐ |

**Total Estimado:** 17 horas de desenvolvimento

**Quick Win (Fases 1+2):** 5 horas para 80% do benefício

---

## Notas Importantes

1. **Desativar Cache Temporariamente:** Conforme solicitado, adicionar flag `enableCache = false` durante testes
2. **Migração Media3:** Usar `PLANO_MIGRACAO_VIDEO_PROCESSING.md` para migração futura
3. **Compatibilidade:** Verificar Android API levels (ThumbnailExtractor suporta API 27+)
4. **Testes:** Testar com vídeos reais de diferentes durações e codificações

---

## Referências

- **Código atual:** `app/src/main/java/com/chopcut/data/thumbnail/`
- **Timeline:** `app/src/main/java/com/chopcut/ui/components/TimelineEditor.kt:104-136`
- **Cache:** `app/src/main/java/com/chopcut/data/thumbnail/ThumbnailCache.kt`
- **Plano Media3:** `PLANO_MIGRACAO_VIDEO_PROCESSING.md`
- **Android Media API:** https://developer.android.com/guide/topics/media/mediaplayer
