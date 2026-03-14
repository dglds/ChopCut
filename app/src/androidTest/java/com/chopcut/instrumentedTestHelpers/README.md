# TimelineTestHelper

Helper compartilhado para testes instrumentados da timeline de thumbnails.

## Visão Geral

Fornece utilitários reutilizáveis para:
- Setup comum de testes
- Criação de vídeos de teste
- Validação de strips carregados
- Medições de memória
- Espera assíncrona com timeout
- Relatórios formatados

## Funções Principais

### Setup

```kotlin
// Criar RecyclerView para testes
val recyclerView = TimelineTestHelper.createRecyclerView(context, width = 1200, height = 120)

// Copiar vídeo de teste dos assets
val uri = TimelineTestHelper.copyTestVideo(context, assetName = "sample.mp4")

// Obter contextos
val testContext = TimelineTestHelper.getTestContext()
val targetContext = TimelineTestHelper.getTargetContext()
```

### Validação

```kotlin
// Verificar se strip foi carregado
val isValid = TimelineTestHelper.assertStripLoaded(
    bitmap = strip,
    expectedWidth = 120,
    expectedHeight = 120,
    expectedConfig = Bitmap.Config.RGB_565
)

// Asserção de performance
TimelineTestHelper.assertPerformance(
    testName = "Extraction Time",
    actual = 250L,
    expectedMax = 500L,
    unit = "ms"
)
```

### Memória

```kotlin
// Medir uso de memória
val memoryBefore = TimelineTestHelper.measureMemory()
// ... executar teste ...
val memoryAfter = TimelineTestHelper.measureMemory()
val memoryUsed = memoryAfter - memoryBefore
```

### Espera Assíncrona

```kotlin
// Aguardar condição com timeout
val success = TimelineTestHelper.waitForCondition(
    condition = { adapter.itemCount == expectedCount },
    timeoutMs = 5000,
    checkIntervalMs = 100
)

// Aguardar strip específico ser carregado
val loaded = TimelineTestHelper.waitForStrip(
    loadedSet = loadedTimestamps,
    timestamp = 1000L,
    timeoutMs = 5000
)
```

### Relatórios

```kotlin
// Imprimir relatório formatado
TimelineTestHelper.printReport(
    title = "TEST RESULTS",
    lines = listOf(
        "Test 1: ✅ PASSOU",
        "Test 2: ❌ FALHOU",
        "Total: 2/1"
    )
)
```

### Utilitários

```kotlin
// Criar bitmap de teste
val bitmap = TimelineTestHelper.createTestBitmap(
    width = 120,
    height = 120,
    config = Bitmap.Config.RGB_565
)

// Limpar cache de testes
TimelineTestHelper.clearTestCache(context)
```

## Padrão de Uso

```kotlin
@Test
fun exampleTest() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().context
    
    // Setup
    val uri = TimelineTestHelper.copyTestVideo(context)
    val provider = OptimizedThumbnailProvider(context)
    
    val loadedTimestamps = mutableSetOf<Long>()
    provider.thumbnailUpdates.collect { (ts, _) ->
        loadedTimestamps.add(ts)
    }
    
    // Ação
    provider.requestThumbnail(uri, 1000L, ThumbnailPriority.VISIBLE)
    
    // Validação
    val loaded = TimelineTestHelper.waitForStrip(
        loadedTimestamps,
        1000L
    )
    
    TimelineTestHelper.printReport(
        title = "EXEMPLE TEST",
        lines = listOf(
            "URI: $uri",
            "Timestamp: 1000ms",
            "Loaded: $loaded"
        )
    )
    
    assertTrue("Deve carregar strip", loaded)
}
```

## Convenções

- **Tempo de espera:** Use `waitForCondition()` ou `waitForStrip()` em vez de `delay()` fixo
- **Relatórios:** Use `printReport()` para saída formatada no logcat
- **Performance:** Use `assertPerformance()` para validar thresholds
- **Limpeza:** Sempre chame `clearTestCache()` no `@After`
- **Contextos:** Use `getTestContext()` para resources de teste, `getTargetContext()` para app

## Notas de Implementação

- Bitmaps de teste são criados com `RGB_565` por padrão (50% menos memória)
- Timeout padrão é 5000ms para `waitForCondition()` e `waitForStrip()`
- Relatórios têm largura fixa de 62 caracteres para legibilidade
- Arquivos de teste começam com prefixo `test_` para fácil limpeza
