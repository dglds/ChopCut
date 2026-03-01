# 📋 Plano de Otimização de Cache e Performance

---

## 🎯 Resumo Executivo

Este plano consolida **todos os problemas identificados** na sessão de análise e propõe uma solução integrada focada em **performance, arquitetura limpa e experiência do usuário**.

### Problemas Identificados (16 total)

| Categoria | Problemas | Prioridade |
|-----------|-----------|------------|
| **Arquitetura** | 1, 13, 14, 15 | 🔴 Alta |
| **Cache** | 4, 6, 7, 8, 9 | 🔴 Alta |
| **Performance** | 5, 10, 11 | 🟡 Média |
| **Cancelamento** | 2, 3, 12 | 🟡 Média |

---

## 🗺️ Mapa Completo de Problemas

```
┌─────────────────────────────────────────────────────────────────┐
│                    PROBLEMAS IDENTIFICADOS                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ARQUITETURA (4 problemas):                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. ViewModels não compartilham cache                     │   │
│  │ 13. PreloadViewModel não é singleton                    │   │
│  │ 14. PreloadDataStore é um workaround (não ideal)         │   │
│  │ 15. TimelineEditor usa HashMap sem controle de memória   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  CACHE (5 problemas):                                           │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 4. ThumbnailCache existe mas não é usado               │   │
│  │ 6. Navegação perde cache em memória                    │   │
│  │ 7. Escrita é sequencial (lock bloqueante)             │   │
│  │ 8. Apenas leitura é paralela                           │   │
│  │ 9. LRU não está implementado                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  PERFORMANCE (3 problemas):                                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 5. Semaphore fixo em 3 threads                         │   │
│  │ 10. Sem priorização inteligente                        │   │
│  │ 11. Pre-fetching não é adaptativo                     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  CANCELAMENTO (3 problemas):                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 2. Jobs cancelados ao navegar                          │   │
│  │ 3. Jobs antigos continuam ao pular scroll               │   │
│  │ 12. Estratégia radial não cancela jobs                │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Arquitetura Proposta

### Diagrama ANTES
```
HomeScreen
    ↓
HomeViewModel
    ↓
PreloadViewModel (instância A)
    ├── Jobs (cancelados ao navegar)
    ├── Map<Int, Bitmap> (perdido ao navegar)
    └── ↓ PreloadDataStore (compartilhado via object)
         ↓
         TrimScreen
         ↓
         TrimViewModel
         ↓
         PreloadViewModel (instância NOVA)
             ├── Jobs (reiniciados do zero)
             └── Map<Int, Bitmap> (NOVO, do zero)
```

### Diagrama DEPOIS
```
┌─────────────────────────────────────────────────────────────┐
│           ThumbnailCacheManager (Singleton)                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ThumbnailCache (LRU na RAM)                          │   │
│  │  - 100 strips máximo (~43MB)                        │   │
│  │  - Eviction automático                             │   │
│  │  - Hit rate tracking                               │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ThumbnailStripManager                               │   │
│  │  - Cache em disco (webp)                           │   │
│  │  - Threads dinâmicas (2-6)                        │   │
│  │  - Escrita paralela (ioSemaphore)                 │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ JobTracker                                           │   │
│  │  - Jobs ativos por vídeo                            │   │
│  │  - Jobs ativos por segmento                        │   │
│  │  - Cancelamento inteligente                        │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         ↑                              ↑
         │                              │
         │                              │
┌────────┴───────┐              ┌──────┴────────┐
│  HomeViewModel  │              │ TrimViewModel  │
│                 │              │                │
└─────────────────┘              └────────────────┘
```

---

## 📝 Plano Detalhado por Fase

---

## 🏗️ FASE 1: Infraestrutura de Cache Compartilhado

### Objetivos
Resolver problemas: **1, 4, 6, 9, 13, 14, 15**

### Fase 1.1: Melhorar ThumbnailCache LRU

**Arquivo:** `ThumbnailCache.kt`

**Mudanças:**
- Adicionar método `putAll()` para batch insertion
- Adicionar método `getOrPut()` para pattern cache-aside
- Adicionar método `getStats()` para debug
- Adicionar método `getHitRate()` para monitoramento
- Adicionar método `clear()` para limpar cache
- Melhorar logs para tracking hit/miss

**Código exemplo:**
```kotlin
// NOVOS métodos
fun putAll(items: Map<String, Bitmap>) { ... }
fun getOrPut(key: String, provider: () -> Bitmap): Bitmap { ... }
fun getStats(): CacheStats { ... }
data class CacheStats(val size: Int, val hitRate: Float, val misses: Int, val hits: Int)
```

### Fase 1.2: Criar ThumbnailCacheManager Singleton

**Arquivo:** `ThumbnailCacheManager.kt` (NOVO)

**Responsabilidades:**
- Gerenciar cache compartilhado entre ViewModels
- Coordenar ThumbnailCache LRU e ThumbnailStripManager
- Rastrear jobs ativos por vídeo e por segmento
- Implementar cancelamento inteligente
- Fornecer estatísticas de uso

**Código principal:**
```kotlin
object ThumbnailCacheManager {
    private var appContext: Context? = null

    // Cache em memória (LRU)
    private val memoryCache = ThumbnailCache(maxSize = 100)

    // Gerenciador de strips (cache em disco)
    private var stripManager: ThumbnailStripManager? = null

    // Tracking de jobs
    private val videoJobs = mutableMapOf<String, Job>()
    private val segmentJobs = mutableMapOf<String, Job>()

    fun init(context: Context) { ... }
    suspend fun getStrip(...): Bitmap? { ... }
    fun cancelJobsForUri(uri: Uri) { ... }
    fun cancelFarJobs(uri: Uri, currentSegment: Int, threshold: Int) { ... }
    fun getStats(): CacheManagerStats { ... }
    fun clearMemoryCache() { ... }
    fun clearAll() { ... }
}
```

### Fase 1.3: Modificar ThumbnailStripManager

**Arquivo:** `ThumbnailStripManager.kt`

**Mudanças:**
- Transformar propriedades em `var` para permitir reconfiguração:
  ```kotlin
  var thumbWidth: Int
  var thumbHeight: Int
  var thumbsPerStrip: Int
  ```
- Isso permite reutilizar a mesma instância com diferentes dimensões

### Fase 1.4: Inicializar ThumbnailCacheManager

**Arquivo:** `MainActivity.kt` ou `ChopCutApplication.kt`

**Mudanças:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ThumbnailCacheManager.init(applicationContext)
}
```

**Benefícios desta fase:**
- ✅ Cache compartilhado entre ViewModels (resolve 1)
- ✅ ThumbnailCache LRU implementado (resolve 4, 9)
- ✅ Cache persiste ao navegar (resolve 6)
- ✅ PreloadDataStore pode ser removido (resolve 14)
- ✅ HashMap substituído por LRU (resolve 15)
- ✅ Infraestrutura singleton pronta (resolve 13)

---

## ⚡ FASE 2: Threads Dinâmicas e Otimização de I/O

### Objetivos
Resolver problemas: **5, 7, 8**

### Fase 2.1: Implementar Threads Dinâmicas

**Arquivo:** `ThumbnailStripManager.kt`

**Mudanças:**
```kotlin
companion object {
    private fun calculateOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores <= 2 -> 2      // Baixo custo
            cores <= 6 -> cores.coerceAtMost(4)  // Médio
            else -> 6            // High-end
        }
    }

    private val extractSemaphore = Semaphore(calculateOptimalThreadCount())

    init {
        Timber.i("ThumbnailStrip: Using ${calculateOptimalThreadCount()} threads " +
                 "(${Runtime.getRuntime().availableProcessors()} cores)")
    }
}
```

**Benefícios:**
- ✅ Aproveita melhor hardware (resolve 5)
- ✅ 2-6 threads dinâmicas baseadas no device

### Fase 2.2: Implementar Escrita Paralela

**Arquivo:** `ThumbnailStripManager.kt`

**Mudanças:**
```kotlin
// Adicionar novo semaphore para I/O
private val ioSemaphore = Semaphore(3)

// Modificar saveToCache() para usar ioSemaphore
private suspend fun saveToCache(...): Boolean = withContext(Dispatchers.IO) {
    // Compressão continua fora do lock (já é assim)

    ioSemaphore.withPermit {
        synchronized(cacheLock) {
            // Apenas a operação de rename está no lock
            if (tempFile.renameTo(finalFile)) {
                trimCacheIfNeeded()
                return@withPermit true
            }
        }
        tempFile.delete()
        return@withPermit false
    }
}
```

**Benefícios:**
- ✅ Escrita paralela (resolve 7, 8)
- ✅ Até 3 strips podem ser salvas simultaneamente
- ✅ Threads de extração não ficam ociosas

### Fase 2.3: Implementar Strips Adaptativas

**Arquivo:** `ThumbnailStripManager.kt`

**Mudanças:**
```kotlin
class ThumbnailStripManager(
    private val context: Context,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val thumbsPerStrip: Int = 10,
    val adaptiveStrips: Boolean = true  // NOVO
) {
    private val minThumbsPerStrip = 5  // Mínimo para início rápido
    
    companion object {
        /**
         * Calcula thumbsPerStrip para um segmento específico usando estratégia adaptativa.
         * 
         * Estratégia: Começa pequena (5) para carregar rápido o início, cresce suavemente
         * até o limite máximo usando curva de potência (exponencial suave).
         */
        fun calculateAdaptiveThumbsPerStrip(
            segmentIndex: Int,
            totalSegments: Int,
            maxThumbsPerStrip: Int,
            minThumbsPerStrip: Int = 5
        ): Int {
            if (totalSegments <= 1) return maxThumbsPerStrip
            
            val progress = segmentIndex.toFloat() / (totalSegments - 1).toFloat()
            val power = 0.5f
            val adjustedProgress = progress.coerceIn(0f, 1f).pow(power)
            
            val range = maxThumbsPerStrip - minThumbsPerStrip
            val thumbsForSegment = (minThumbsPerStrip + (range * adjustedProgress)).toInt()
            
            return thumbsForSegment.coerceIn(minThumbsPerStrip, maxThumbsPerStrip)
        }
    }
    
    fun getThumbsPerStripForSegment(segmentIndex: Int, totalSegments: Int): Int {
        return if (adaptiveStrips) {
            calculateAdaptiveThumbsPerStrip(segmentIndex, totalSegments, thumbsPerStrip, minThumbsPerStrip)
        } else {
            thumbsPerStrip
        }
    }
}
```

**Exemplo de uso:**
```kotlin
// Vídeo de 15 minutos (900 segundos) com thumbsPerStrip=20:
// - Segmento 0 (0-5s): 5 thumbs   (instantâneo)
// - Segmento 10 (50s): 7 thumbs   (rápido)
// - Segmento 20 (100s): 11 thumbs  (médio)
// - Segmento 40 (800s): 20 thumbs (eficiente no cache)
```

**Benefícios:**
- ✅ Carregamento inicial instantâneo (primeiros segundos em ~1s)
- ✅ Cache mais eficiente no meio/fim (menos arquivos)
- ✅ Melhor UX em vídeos longos

### Fase 2.4: Extração Inteligente e Adaptativa

**Arquivo:** `PreloadViewModel.kt`

**Mudanças:**
```kotlin
// AO INVÉS de extrair apenas 3 segmentos fixos:
val preloadSegments = 3.coerceAtMost(totalSegments)

// IMPLEMENTAR extração baseada em percentual do vídeo:
val preloadPercent = 0.05  // 5% do vídeo
val preloadSeconds = ((durationMs / 1000) * preloadPercent).toLong()
val minPreloadSeconds = 30L  // Mínimo de 30 segundos
val effectivePreloadSeconds = maxOf(preloadSeconds, minPreloadSeconds)

// Calcular segmentos baseado em thumbs/strip inicial (5)
val thumbsPerStripInitial = 5
val preloadSegments = (effectivePreloadSeconds / thumbsPerStripInitial).toInt()
    .coerceAtLeast(3)
    .coerceAtMost(totalSegments)
```

**Exemplo de uso:**
```kotlin
// Vídeos CURTOS (1 minuto):
// - 5% = 3 segundos (muito pouco)
// - Usa mínimo: 30 segundos = 6 segmentos

// Vídeos MÉDIOS (5 minutos):
// - 5% = 15 segundos
// - Usa mínimo: 30 segundos = 6 segmentos

// Vídeos LONGOS (15 minutos):
// - 5% = 45 segundos
// - Usa calculado: 45 segundos = 9 segmentos
```

**Benefícios:**
- ✅ Vídeos curtos: carrega mais segmentos (5% pode ser pouco)
- ✅ Vídeos longos: carrega segmentos suficientes sem exagerar
- ✅ Sempre garante UX inicial rápida

---

## 🎯 FASE 3: Cancelamento Inteligente

### Objetivos
Resolver problemas: **2, 3, 12**

### Fase 3.1: Implementar Cancelamento por Vídeo

**Arquivo:** `ThumbnailCacheManager.kt`

**Método:**
```kotlin
fun cancelJobsForUri(uri: Uri) {
    synchronized(jobsLock) {
        val uriKey = uri.toString()
        videoJobs[uriKey]?.cancel()
        videoJobs.remove(uriKey)
        Timber.d("Cancelled video job for $uri")
    }
}
```

**Uso em ViewModels:**
```kotlin
// REMOVER de HomeViewModel e TrimViewModel:
override fun onCleared() {
    super.onCleared()
    // ❌ REMOVIDO: preloadViewModel.cancelPreload()
}
```

**Benefícios:**
- ✅ Jobs persistem ao navegar (resolve 2)
- ✅ Cache em memória não é perdido

### Fase 3.2: Implementar Cancelamento por Segmento

**Arquivo:** `ThumbnailCacheManager.kt`

**Método:**
```kotlin
suspend fun loadStripWithTracking(
    uri: Uri,
    segmentIndex: Int,
    durationMs: Long,
    thumbWidth: Int,
    thumbHeight: Int,
    thumbsPerStrip: Int,
    onResult: (Bitmap?) -> Unit
) {
    val jobKey = "${uri}_${segmentIndex}"

    // Cancelar job anterior para o mesmo segmento
    synchronized(jobsLock) {
        segmentJobs[jobKey]?.cancel()
    }

    val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            val strip = getStrip(uri, segmentIndex, durationMs, thumbWidth, thumbHeight, thumbsPerStrip)
            if (isActive) {
                onResult(strip)
            }
        } catch (e: CancellationException) {
            Timber.d("Segment job cancelled: $jobKey")
        } finally {
            synchronized(jobsLock) {
                segmentJobs.remove(jobKey)
            }
        }
    }

    synchronized(jobsLock) {
        segmentJobs[jobKey] = job
    }
}

fun cancelFarJobs(uri: Uri, currentSegment: Int, threshold: Int = 5) {
    synchronized(jobsLock) {
        val uriPrefix = "${uri}_"

        segmentJobs.keys
            .filter { it.startsWith(uriPrefix) }
            .mapNotNull { key ->
                key.removePrefix(uriPrefix).toIntOrNull()?.let { idx -> key to idx }
            }
            .filter { (_, segIdx) -> abs(segIdx - currentSegment) > threshold }
            .forEach { (key, _) ->
                segmentJobs[key]?.cancel()
                segmentJobs.remove(key)
                Timber.d("Cancelled far job: $key (distance > $threshold)")
            }
    }
}
```

**Benefícios:**
- ✅ Cancela jobs distantes ao scroll rápido (resolve 3, 12)
- ✅ Economiza recursos

### Fase 3.3: Atualizar Background Loading em TimelineEditor

**Arquivo:** `TimelineEditor.kt`

**Mudança no LaunchedEffect radial:**
```kotlin
// ANTES (linhas 186-218): Carrega TODOS os segmentos
// DEPOIS: Adicionar cancelamento inteligente

LaunchedEffect(videoUri, videoDurationMs, scrollOffsetPx) {
    if (videoDurationMs == 0L) return@LaunchedEffect

    val totalSegments = stripManager.getSegmentCount(videoDurationMs)
    val currentSecond = (scrollOffsetPx / pxPerSecond).toInt()
    val centerSegment = currentSecond / thumbsPerStrip

    // NOVO: Cancelar jobs distantes antes de começar novos
    ThumbnailCacheManager.cancelFarJobs(
        uri = videoUri,
        currentSegment = centerSegment,
        threshold = 10  // Mais permissivo para background
    )

    val segmentsByPriority = (0 until totalSegments)
        .sortedBy { abs(it - centerSegment) }
        .take(maxStrips)

    for (segIdx in segmentsByPriority) {
        if (strips.size >= maxStrips) break
        if (strips.containsKey(segIdx) || loadingStrips.containsKey(segIdx)) continue

        // Usar ThumbnailCacheManager com tracking
        ThumbnailCacheManager.loadStripWithTracking(
            uri = videoUri,
            segmentIndex = segIdx,
            durationMs = videoDurationMs,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            thumbsPerStrip = thumbsPerStrip
        ) { strip ->
            if (isActive) {
                withContext(Dispatchers.Main) {
                    strips[segIdx] = strip
                }
            }
        }
    }
}
```

---

## 🎨 FASE 4: Integrar nos ViewModels

### Objetivos
Resolver problemas: **1, 2, 6, 13, 14**

### Fase 4.1: Modificar HomeViewModel

**Arquivo:** `HomeViewModel.kt`

**Mudanças:**
```kotlin
// REMOVER:
private val preloadViewModel = PreloadViewModel(application, videoRepository)

// MANTER:
val videoRepository = VideoRepository(application)
val audioDataExtractor = AudioDataExtractor(application)

// ADICIONAR:
fun selectVideo(uri: Uri) {
    _selectedVideoUri.value = uri
    loadVideoMetadata(uri)
}

// Modificar loadVideoMetadata para usar ThumbnailCacheManager
private fun startPreloadInBackground(uri: Uri, durationMs: Long, aspectRatio: Float) {
    viewModelScope.launch(DispatcherProvider.io) {
        val density = getApplication<Application>().resources.displayMetrics.density
        val thumbWidth = (60 * density).toInt()
        val thumbHeight = (thumbWidth / aspectRatio).toInt()
        val thumbsPerStrip = PreferencesManager(getApplication()).thumbsPerStrip

        val totalSegments = ((durationMs + 999) / 1000 / thumbsPerStrip)

        // Usar ThumbnailCacheManager para pré-carregar
        val strips = ThumbnailCacheManager.startPreload(
            uri = uri,
            durationMs = durationMs,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            thumbsPerStrip = thumbsPerStrip,
            segmentCount = totalSegments,
            initialSegments = 5
        )

        val preloadedData = PreloadedData(
            videoInfo = videoInfo,
            audioAmplitudes = audioAmplitudes,
            preloadedStrips = strips,
            totalSegments = totalSegments,
            preloadPercentage = 0.5f
        )

        _uiState.value = PreloadUiState.Ready(preloadedData)
    }
}

// REMOVER:
override fun onCleared() {
    super.onCleared()
    // ❌ REMOVIDO: preloadViewModel.cancelPreload()
}
```

### Fase 4.2: Modificar TrimViewModel

**Arquivo:** `TrimViewModel.kt`

**Mudanças:**
```kotlin
// REMOVER:
private val preloadViewModel = PreloadViewModel(application, videoRepository)

// ADICIONAR:
init {
    if (initialAudioAmplitudes == null && initialPreloadedStrips == null) {
        videoUri?.let { uri ->
            val videoInfo = videoRepository.getMetadata(uri) ?: return@let
            val density = getApplication<Application>().resources.displayMetrics.density
            val thumbWidth = (60 * density).toInt()
            val thumbHeight = (thumbWidth / videoInfo.aspectRatio).toInt()
            val thumbsPerStrip = PreferencesManager(getApplication()).thumbsPerStrip

            val totalSegments = ((videoInfo.durationMs + 999) / 1000 / thumbsPerStrip)

            // Usar ThumbnailCacheManager
            val strips = ThumbnailCacheManager.startPreload(
                uri = uri,
                durationMs = videoInfo.durationMs,
                thumbWidth = thumbWidth,
                thumbHeight = thumbHeight,
                thumbsPerStrip = thumbsPerStrip,
                segmentCount = totalSegments,
                initialSegments = 5
            )

            // Atualizar estado com strips
        }
    }
}

// REMOVER:
fun cancelPreload() {
    preloadViewModel.cancelPreload()
}

override fun onCleared() {
    super.onCleared()
    // ❌ REMOVIDO: preloadViewModel.cancelPreload()
}
```

---

## 🎭 FASE 5: Integrar no TimelineEditor

### Objetivos
Resolver problemas: **1, 2, 3, 10, 12**

### Fase 5.1: Substituir HashMap por ThumbnailCache

**Arquivo:** `TimelineEditor.kt`

**Mudanças:**
```kotlin
// ANTES (linhas 122-126):
val strips = remember {
    mutableStateMapOf<Int, Bitmap>().apply {
        putAll(preloadedStrips)
    }
}

// DEPOIS:
val strips = remember(thumbWidth, thumbHeight) {
    ThumbnailCache(maxSize = 100).apply {
        // Adicionar strips preloaded
        preloadedStrips.forEach { (segIdx, bitmap) ->
            put(videoUri.toString(), segIdx.toLong(), bitmap)
        }
    }
}
```

### Fase 5.2: Usar ThumbnailCacheManager em LaunchedEffects

**Arquivo:** `TimelineEditor.kt`

**Mudança no carregamento imediato (linhas 147-183):**
```kotlin
LaunchedEffect(scrollOffsetPx, videoDurationMs) {
    if (videoDurationMs == 0L) return@LaunchedEffect

    val totalSegments = stripManager.getSegmentCount(videoDurationMs)
    val currentSecond = (scrollOffsetPx / pxPerSecond).toInt()
    val visibleSegment = currentSecond / thumbsPerStrip

    // NOVO: Cancelar jobs distantes
    ThumbnailCacheManager.cancelFarJobs(
        uri = videoUri,
        currentSegment = visibleSegment,
        threshold = 5
    )

    val segmentsToLoad = listOf(
        visibleSegment,
        visibleSegment - 1,
        visibleSegment + 1,
        visibleSegment - 2,
        visibleSegment + 2
    ).filter { it in 0 until totalSegments }
     .filter { !strips.contains(videoUri.toString(), it.toLong()) && loadingStrips[it] != true }

    segmentsToLoad.forEach { segIdx ->
        ThumbnailCacheManager.loadStripWithTracking(
            uri = videoUri,
            segmentIndex = segIdx,
            durationMs = videoDurationMs,
            thumbWidth = thumbWidth,
            thumbHeight = thumbHeight,
            thumbsPerStrip = thumbsPerStrip
        ) { strip ->
            if (isActive) {
                withContext(Dispatchers.Main) {
                    strips.put(videoUri.toString(), segIdx.toLong(), strip)
                }
            }
        }
    }
}
```

---

## 📊 FASE 6: Pre-fetching Adaptativo e Priorização

### Objetivos
Resolver problemas: **10, 11**

### Fase 6.1: Detectar Velocidade do Scroll

**Arquivo:** `TimelineEditor.kt`

**Adicionar:**
```kotlin
var scrollVelocity by remember { mutableFloatStateOf(0f) }
var lastScrollOffset by remember { mutableFloatStateOf(0f) }
var lastScrollTime by remember { mutableLongStateOf(0L) }

LaunchedEffect(scrollOffsetPx) {
    val now = System.currentTimeMillis()
    val deltaTime = (now - lastScrollTime).toFloat().coerceAtLeast(1f)
    val deltaOffset = scrollOffsetPx - lastScrollOffset

    scrollVelocity = abs(deltaOffset / deltaTime)
    lastScrollOffset = scrollOffsetPx
    lastScrollTime = now

    Timber.v("Scroll velocity: $scrollVelocity px/ms")
}
```

### Fase 6.2: Pre-fetching Adaptativo

**Arquivo:** `TimelineEditor.kt`

**Mudança no número de segmentos a carregar:**
```kotlin
// Calcular prefetch baseado na velocidade
val prefetchDistance = when {
    scrollVelocity < 500f -> 3  // Scroll lento: carregar 3 strips à frente
    scrollVelocity < 2000f -> 2 // Scroll médio: carregar 2 strips
    else -> 1  // Scroll rápido: carregar apenas o visível
}

val segmentsToLoad = listOf(
    visibleSegment,
    *(1..prefetchDistance).flatMap { i ->
        listOf(visibleSegment - i, visibleSegment + i)
    }.toTypedArray()
).filter { it in 0 until totalSegments }
 .filter { !strips.contains(videoUri.toString(), it.toLong()) && loadingStrips[it] != true }
```

### Fase 6.3: Priorização de Carregamento

**Arquivo:** `ThumbnailCacheManager.kt`

**Adicionar método de priorização:**
```kotlin
suspend fun loadStripsWithPriority(
    uri: Uri,
    segments: List<Int>,
    durationMs: Long,
    thumbWidth: Int,
    thumbHeight: Int,
    thumbsPerStrip: Int,
    onProgress: (Int, Bitmap) -> Unit
) {
    // Carregar segmentos em ordem de prioridade
    segments.forEachIndexed { priority, segIdx ->
        launch(Dispatchers.IO) {
            val strip = getStrip(uri, segIdx, durationMs, thumbWidth, thumbHeight, thumbsPerStrip)
            strip?.let { onProgress(priority, it) }
        }
    }
}
```

**Benefícios:**
- ✅ Pre-fetching adaptativo (resolve 11)
- ✅ Carrega menos em scroll rápido (economiza recursos)
- ✅ Carrega mais em scroll lento (melhor UX)
- ✅ Priorização inteligente (resolve 10)

---

## 🧪 FASE 7: Testes e Validação

### Fase 7.1: Testes de Performance

**Cenário 1: Scroll Suave**
- Vídeo: 15 minutos, 1080p
- Ação: Scroll do início ao fim lentamente
- Métricas:
  - FPS médio (> 60)
  - Cache hit rate (> 70%)
  - Tempo para carregar primeiras strips (< 2s)

**Cenário 2: Scroll Rápido**
- Vídeo: 15 minutos
- Ação: Scroll do início ao fim rapidamente (flick)
- Métricas:
  - Jobs cancelados (> 10)
  - FPS médio (> 30)
  - Memória pico (< 100MB)

**Cenário 3: Navegação entre Telas**
- Ação: Home → Trim → Home → Trim
- Métricas:
  - Cache hit rate na segunda entrada (> 90%)
  - Tempo até mostrar timeline (< 1s)
  - Jobs não reiniciados

### Fase 7.2: Testes de Funcionalidade

**Teste 1: LRU Eviction**
- Carregar 120 strips em vídeo longo
- Verificar se strips antigas foram removidas
- Confirmar tamanho do cache ≤ 100

**Teste 2: Threads Dinâmicas**
- Testar em devices com 2, 4, 8 cores
- Verificar número de threads usadas
- Confirmar logs de inicialização

**Teste 3: Cancelamento Inteligente**
- Scroll rápido do início ao fim
- Verificar logs de jobs cancelados
- Confirmar que jobs distantes foram cancelados

**Teste 4: Escrita Paralela**
- Carregar 10 strips simultaneamente
- Verificar logs de "saveToCache"
- Confirmar que múltiplas escritas ocorrem em paralelo

### Fase 7.3: Testes de Memória

**Métricas:**
- Memória pico com 100 strips (~43MB)
- Memória pico em vídeo de 15 minutos (< 100MB)
- Verificar se não há memory leaks
- Verificar se bitmaps são reciclados corretamente

---

## 📊 Resumo de Mudanças por Arquivo

| Arquivo | Mudanças | Linhas | Problemas Resolvidos |
|---------|-----------|--------|---------------------|
| **ThumbnailCache.kt** | Melhorar, adicionar métodos | +30 | 4, 9 |
| **ThumbnailCacheManager.kt** (NOVO) | Criar singleton completo | +300 | 1, 2, 3, 4, 6, 9, 12, 13 |
| **ThumbnailStripManager.kt** | Threads dinâmicas, I/O paralelo, props var | +40 | 5, 7, 8 |
| **MainActivity.kt** | Inicializar ThumbnailCacheManager | +5 | 13 |
| **TimelineEditor.kt** | Usar ThumbnailCache, cancelamento, adaptativo | -80, +120 | 1, 2, 3, 10, 11, 12, 15 |
| **HomeViewModel.kt** | Remover PreloadViewModel, usar ThumbnailCacheManager | -60, +40 | 1, 2, 6, 13, 14 |
| **TrimViewModel.kt** | Remover PreloadViewModel, usar ThumbnailCacheManager | -50, +35 | 1, 2, 6, 13 |
| **PreloadDataStore.kt** | Pode ser removido | -23 | 14 |

**Total estimado:**
- Linhas adicionadas: ~570
- Linhas removidas: ~213
- Líquido: +357 linhas

---

## ✅ Checklist de Validação Final

### Arquitetura
- [ ] ThumbnailCacheManager é singleton
- [ ] Cache compartilhado entre ViewModels
- [ ] PreloadDataStore removido ou deprecado
- [ ] HashMap substituído por ThumbnailCache LRU

### Cache
- [ ] ThumbnailCache LRU implementado
- [ ] Cache em memória persiste ao navegar
- [ ] Escrita paralela implementada (ioSemaphore)
- [ ] Threads dinâmicas baseadas no hardware
- [ ] LRU eviction funciona automaticamente

### Performance
- [ ] 2-6 threads dinâmicas
- [ ] Pre-fetching adaptativo implementado
- [ ] Priorização de carregamento

### Cancelamento
- [ ] Jobs não cancelados ao navegar
- [ ] Jobs distantes cancelados ao scroll rápido
- [ ] Background loading cancela jobs antigos

### Métricas
- [ ] FPS > 60 ao scroll suave
- [ ] FPS > 30 ao scroll rápido
- [ ] Cache hit rate > 70% após primeira extração
- [ ] Cache hit rate > 90% ao navegar de volta
- [ ] Memória pico < 100MB
- [ ] Threads dinâmicas logadas

---

## ⏱️ Estimativa de Tempo

| Fase | Descrição | Estimativa |
|------|-----------|------------|
| 1 | Infraestrutura de cache | 3-4 horas |
| 2 | Threads e I/O | 2-3 horas |
| 3 | Cancelamento inteligente | 2-3 horas |
| 4 | Integrar ViewModels | 2-3 horas |
| 5 | Integrar TimelineEditor | 2-3 horas |
| 6 | Pre-fetching adaptativo | 1-2 horas |
| 7 | Testes e validação | 3-4 horas |

**Total: 15-22 horas**

---

## 🎯 Resumo dos 16 Problemas Resolvidos

| # | Problema | Fase | Status |
|---|----------|------|--------|
| 1 | ViewModels não compartilham cache | 1, 4, 5 | ⏳ |
| 2 | Jobs cancelados ao navegar | 3, 4 | ⏳ |
| 3 | Jobs antigos continuam ao pular scroll | 3, 5 | ⏳ |
| 4 | ThumbnailCache não é usado | 1, 5 | ⏳ |
| 5 | Semaphore fixo em 3 threads | 2 | ⏳ |
| 6 | Navegação perde cache em memória | 1, 4 | ⏳ |
| 7 | Escrita é sequencial (lock) | 2 | ⏳ |
| 8 | Apenas leitura é paralela | 2 | ⏳ |
| 9 | LRU não está implementado | 1, 5 | ⏳ |
| 10 | Sem priorização inteligente | 6 | ⏳ |
| 11 | Pre-fetching não é adaptativo | 6 | ⏳ |
| 12 | Estratégia radial não cancela jobs | 3, 5 | ⏳ |
| 13 | PreloadViewModel não é singleton | 1, 4 | ⏳ |
| 14 | PreloadDataStore é workaround | 1, 4 | ⏳ |
| 15 | TimelineEditor usa HashMap | 5 | ⏳ |
| 16 | Tracking de jobs não existe | 1, 3 | ⏳ |

---

## 📚 Referências

### Arquivos Modificados

**Novos:**
- `ThumbnailCacheManager.kt` - Singleton centralizador de cache

**Modificados:**
- `ThumbnailCache.kt` - Melhorias em LRU
- `ThumbnailStripManager.kt` - Threads dinâmicas, I/O paralelo
- `MainActivity.kt` - Inicialização do singleton
- `TimelineEditor.kt` - Integração com cache manager
- `HomeViewModel.kt` - Uso de ThumbnailCacheManager
- `TrimViewModel.kt` - Uso de ThumbnailCacheManager
- `PreloadDataStore.kt` - Pode ser removido

### Conceitos Implementados

- **Singleton Pattern** - ThumbnailCacheManager
- **Cache-Aside Pattern** - getOrPut()
- **LRU (Least Recently Used)** - Eviction automático
- **Adaptive Prefetching** - Baseado na velocidade do scroll
- **Smart Cancellation** - Jobs distantes cancelados
- **Dynamic Threading** - 2-6 threads baseadas no hardware
- **Parallel I/O** - Escrita paralela com semaphore

---

**Última atualização:** 01/03/2026
**Autor:** AI Assistant
**Versão:** 1.0
