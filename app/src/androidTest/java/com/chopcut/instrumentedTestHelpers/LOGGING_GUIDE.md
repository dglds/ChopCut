# Guia de Logging para Testes Instrumentados

## Padrão de Tags

Use as constantes definidas em `TimberTestTags.kt`:

```kotlin
import com.chopcut.instrumentedTestHelpers.TimberTestTags

import timber.log.Timber

// Logging básico
Timber.tag(TimberTestTags.TEST_TIMELINE).d("Message")

// Com prefixo
Timber.tag(TimberTestTags.TEST_CACHE).d(
    TimberTestTags.formatMessage(TimberTestTags.PREFIX_SUCCESS, "Cache HIT")
)

// Métricas
Timber.tag(TimberTestTags.TEST_PERFORMANCE).d(
    TimberTestTags.formatMetric("Extraction time", 250L, "ms")
)
```

## Filtragem no Logcat

```bash
# Todos os logs de teste
adb logcat -s TEST_*

# Apenas timeline
adb logcat -s TEST_TIMELINE:*

# Apenas cache e performance
adb logcat -s TEST_CACHE:* TEST_PERFORMANCE:*

# Subcategorias específicas
adb logcat -s TEST_EXTRACTION.PRIORITY:*
```

## Padrões de Mensagens

### Início de Teste
```kotlin
Timber.tag(TimberTestTags.TEST_TIMELINE).i(
    TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Test started")
)
```

### Sucesso
```kotlin
Timber.tag(TimberTestTags.TEST_CACHE).i(
    TimberTestTags.formatMessage(TimberTestTags.PREFIX_SUCCESS, "Cache HIT")
)
```

### Falha
```kotlin
Timber.tag(TimberTestTags.TEST_EXTRACTION).e(
    TimberTestTags.formatMessage(TimberTestTags.PREFIX_FAILURE, "Extraction failed")
)
```

### Métricas
```kotlin
Timber.tag(TimberTestTags.TEST_PERFORMANCE).d(
    TimberTestTags.formatMetric("FPS", 60L, "")
)
```

### Porcentagem
```kotlin
Timber.tag(TimberTestTags.TEST_CACHE).d(
    "Hit rate: ${TimberTestTags.formatPercentage(0.85f)}"
)
```

## Níveis de Log

- **d()**: Debug (informação detalhada)
- **i()**: Info (marcos importantes)
- **w()**: Warning (comportamento inesperado mas recuperável)
- **e()**: Error (falhas, exceções)

## Exemplo Completo

```kotlin
@Test
fun extractionTest() = runBlocking {
    Timber.tag(TimberTestTags.TEST_EXTRACTION).i(
        TimberTestTags.formatMessage(TimberTestTags.PREFIX_STARTED, "Extraction test")
    )

    val startTime = System.currentTimeMillis()

    try {
        // ... extração ...

        val elapsedTime = System.currentTimeMillis() - startTime

        Timber.tag(TimberTestTags.TEST_EXTRACTION).i(
            TimberTestTags.formatMessage(TimberTestTags.PREFIX_COMPLETED, "Extraction completed")
        )

        Timber.tag(TimberTestTags.TEST_PERFORMANCE).d(
            TimberTestTags.formatMetric("Extraction time", elapsedTime, "ms")
        )

        assertTrue("Extraction time must be < 500ms", elapsedTime < 500)

    } catch (e: Exception) {
        Timber.tag(TimberTestTags.TEST_EXTRACTION).e(e, "Extraction failed")
        throw e
    }
}
```

## Convenções

1. **Sempre use tags constantes**: Não crie strings literais
2. **Use prefixos**: Facilita visualização no logcat
3. **Logue marcos importantes**: Início, fim, sucesso, falha
4. **Logue métricas**: Tempo, memória, contagens
5. **Seja conciso**: Mensagens curtas e claras
6. **Não logue dados sensíveis**: URIs de produção, etc.
