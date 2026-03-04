# Guia de Constantes - ChopCut

## 📋 Visão Geral

Este projeto utiliza um sistema centralizado de constantes para garantir consistência e fácil manutenção. **Todas as futuras implementações DEVEM respeitar e utilizar este sistema de constantes.**

## 📁 Estrutura de Arquivos

```
app/src/main/java/com/chopcut/config/constants/
├── ThumbnailConstants.kt      - Dimensões, qualidade e cache de thumbnails
├── AudioConstants.kt          - Extração de áudio e waveform
├── TimelineConstants.kt       - Dimensões e constraints do timeline
├── CacheConstants.kt          - Configurações de cache
├── PerformanceConstants.kt    - Threads, thresholds e otimizações
├── QualityConstants.kt        - Compressão, aspect ratios e qualidade
├── FileFormatConstants.kt     - Extensões e formatos de arquivo
├── AnimationConstants.kt      - Durações e timing de animações
└── ConstantsIndex.kt          - Index central para acesso fácil
```

## 🎯 Regras de Ouro

### 1. **NUNCA use valores hardcoded**
```kotlin
// ❌ ERRADO
val width = 320
val height = 180
val quality = 80

// ✅ CORRETO
val width = ThumbnailConstants.Dimensions.DEFAULT_WIDTH
val height = ThumbnailConstants.Dimensions.DEFAULT_HEIGHT
val quality = ThumbnailConstants.Quality.JPEG_COMPRESSION_QUALITY
```

### 2. **Sempre verifique se a constante já existe**
Antes de criar um novo valor hardcoded, verifique nas constantes existentes:
- `ThumbnailConstants` para tudo relacionado a thumbnails
- `AudioConstants` para áudio e waveform
- `TimelineConstants` para timeline e trim
- `CacheConstants` para cache
- `PerformanceConstants` para threads e performance
- `QualityConstants` para qualidade e aspect ratios
- `FileFormatConstants` para formatos de arquivo
- `AnimationConstants` para animações

### 3. **Se não existir, crie a constante**
```kotlin
// No arquivo apropriado (ex: ThumbnailConstants.kt)
object Dimensions {
    const val NEW_VALUE = 123 // Usar const val para primitivos
}
```

### 4. **Use const val sempre que possível**
```kotlin
// ✅ CORRETO - Valores primitivos em tempo de compilação
const val DEFAULT_WIDTH = 320
const val SAMPLE_RATE = 44100

// ⚠️ Apenas quando necessário - Objetos complexos
val PRESETS = listOf(...)
```

## 📖 Exemplos de Uso

### Thumbnails
```kotlin
import com.chopcut.config.constants.ThumbnailConstants

// Dimensões
val width = ThumbnailConstants.Dimensions.DEFAULT_WIDTH
val height = ThumbnailConstants.Dimensions.DEFAULT_HEIGHT

// Qualidade
val quality = ThumbnailConstants.Quality.JPEG_COMPRESSION_QUALITY
val highQualityFactor = ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR

// Cache
val maxSize = ThumbnailConstants.Cache.MAX_CACHE_SIZE
val compression = ThumbnailConstants.Quality.STRIP_COMPRESSION_QUALITY
```

### Audio
```kotlin
import com.chopcut.config.constants.AudioConstants

// Extração
val sampleRate = AudioConstants.Extraction.DEFAULT_SAMPLE_RATE
val bufferSize = AudioConstants.Extraction.BUFFER_SIZE

// Qualidade
val silenceThreshold = AudioConstants.Quality.SILENCE_THRESHOLD
val voiceBoost = AudioConstants.Quality.VOICE_BOOST_FACTOR
```

### Timeline
```kotlin
import com.chopcut.config.constants.TimelineConstants

// Dimensões
val timelineHeight = TimelineConstants.Dimensions.TIMELINE_HEIGHT
val trimHandleSize = TimelineConstants.Dimensions.TRIM_HANDLE_SIZE

// Constraints
val minTrimDuration = TimelineConstants.Constraints.MIN_TRIM_DURATION_MS
val minTrimGap = TimelineConstants.Constraints.MIN_TRIM_GAP_RATIO
```

### Performance
```kotlin
import com.chopcut.config.constants.PerformanceConstants

// Threads
val optimalThreads = PerformanceConstants.ThreadCounts.calculateOptimalThreads(cores)

// Thresholds
val pollingInterval = PerformanceConstants.Thresholds.PROGRESS_POLLING_INTERVAL_MS
```

## 🔍 Como Adicionar Novas Constantes

1. **Identifique a categoria correta**
   - Se relaciona a thumbnail → `ThumbnailConstants.kt`
   - Se relaciona a áudio → `AudioConstants.kt`
   - Se relaciona a timeline → `TimelineConstants.kt`
   - Etc.

2. **Adicione no objeto apropriado**
```kotlin
object ThumbnailConstants {
    object CategoryName {
        const val NEW_CONSTANT = value
        const val ANOTHER_CONSTANT = value
    }
}
```

3. **Documente com KDoc**
```kotlin
/**
 * Descrição do que a constante faz e por que tem este valor
 */
const val NEW_CONSTANT = 123
```

4. **Atualize este guia** (se a constante for importante para desenvolvedores)

## ⚠️ Erros Comuns

### ❌ Criar constantes duplicadas
```kotlin
// Já existe em ThumbnailConstants.Dimensions
const val DEFAULT_WIDTH = 320  // DUPLICADA!
```

### ❌ Misturar unidades de medida
```kotlin
// ❌ ERRADO - Mistura segundos e milissegundos
val timeout1 = 5000  // ms?
val timeout2 = 5      // segundos?

// ✅ CORRETO - Tudo em milissegundos
const val TIMEOUT_MS = 5000
const val ANOTHER_TIMEOUT_MS = 10000
```

### ❌ Não usar constantes em código novo
```kotlin
// ❌ ERRADO - Código novo com hardcoded
val maxSize = 200 * 1024 * 1024

// ✅ CORRETO - Usar constante existente
val maxSize = ThumbnailConstants.Cache.MAX_CACHE_SIZE
```

### ❌ Valores "mágicos" sem explicação
```kotlin
// ❌ ERRADO - O que é 0.95f?
val maxValue = 0.95f

// ✅ CORRETO - Constante com nome descritivo
const val PROGRESS_BAR_MAX_VALUE = 0.95f
val maxValue = AnimationConstants.Loading.PROGRESS_BAR_MAX_VALUE
```

## 📚 Referência Rápida

| Categoria | Arquivo | Uso Principal |
|-----------|----------|---------------|
| Thumbnails | `ThumbnailConstants.kt` | Dimensões, qualidade, cache |
| Audio | `AudioConstants.kt` | Sample rates, thresholds, waveform |
| Timeline | `TimelineConstants.kt` | Dimensões UI, constraints |
| Cache | `CacheConstants.kt` | Tamanhos de cache, limits |
| Performance | `PerformanceConstants.kt` | Threads, timeouts |
| Qualidade | `QualityConstants.kt` | Compression, aspect ratios |
| Formatos | `FileFormatConstants.kt` | Extensões, MIME types |
| Animações | `AnimationConstants.kt` | Durações, easing |

## ✅ Checklist para Novo Código

- [ ] Nenhum valor hardcoded no código
- [ ] Valores constantes estão nos arquivos de constantes apropriados
- [ ] Constantes têm nomes descritivos e em MAIÚSCULAS
- [ ] Unidades de medida estão documentadas (ms, dp, etc.)
- [ ] Valores críticos (thresholds, limits) têm comentários explicando por que
- [ ] Se criar nova constante, atualizou a categoria apropriada

## 🎓 Perguntas Frequentes

**Q: Posso usar valores hardcoded em testes?**
R: Sim, testes podem ter valores específicos para cada caso de teste, mas use constantes quando possível para consistência.

**Q: E valores de UI Compose (dp, sp)?**
R: Valores de UI que dependem de densidade de tela devem ficar em `Spacing.kt` ou usar `val` com `.dp`. Use constantes apenas para lógica de negócio.

**Q: Posso mudar valores de constantes?**
R: **CUIDADO!** Constantes definem comportamentos críticos do sistema. Se precisar mudar:
1. Teste exaustivamente
2. Documente a mudança
3. Verifique se não quebra nada relacionado

**Q: Como faço para migrar código antigo?**
R: Substitua gradualmente os valores hardcoded por constantes. Um arquivo por vez, testando a cada mudança.

## 📞 Suporte

Se tiver dúvidas sobre qual constante usar ou onde adicionar uma nova:
1. Consulte este guia
2. Verifique os arquivos de constantes existentes
3. Pergunte ao time em caso de dúvida

---

**Lembre-se:** A consistência nas constantes garante código mais maintainável, menos bugs e performance previsível. **Sempre use constantes!**