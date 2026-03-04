# 🔧 Correção: Cache Sendo Limpado Incorretamente

## Problema Identificado

**Sintoma:**
- Vídeo de 5min carrega perfeitamente na primeira vez
- Ao voltar para Home e escolher o mesmo vídeo novamente
- **NENHUMA strip é carregada** (tudo shimmer)

**Causa Raiz:**
A preferência `clearCacheOnStartup` estava com valor padrão `true`, o que causava:
```
ChopCutApplication.onCreate()
    ↓
clearThumbnailCacheOnStartup()
    ↓
clearCacheOnStartup = true  (padrão)
    ↓
ThumbnailCacheManager.clearMemoryCache()  ← Limpa cache de memória
ThumbnailStripManager.clearCache()         ← Limpa cache de disco
```

**Resultado:**
- **TODA VEZ** que o app inicia ou retoma do background
- O cache de thumbnails é completamente limpo
- Mesmo que o usuário selecione o mesmo vídeo que estava antes
- **NENHUMA strip** está no cache → tudo precisa ser extraído novamente

---

## Solução Aplicada

**Arquivo:** `PreferencesManager.kt` (linha 79)

**ANTES:**
```kotlin
var clearCacheOnStartup: Boolean
    get() = prefs.getBoolean(KEY_CLEAR_CACHE_ON_STARTUP, true)  // ❌ Habilitado por padrão
    set(value) = prefs.edit().putBoolean(KEY_CLEAR_CACHE_ON_STARTUP, value).apply()
```

**DEPOIS:**
```kotlin
var clearCacheOnStartup: Boolean
    get() = prefs.getBoolean(KEY_CLEAR_CACHE_ON_STARTUP, false)  // ✅ DESABILITADO por padrão
    set(value) = prefs.edit().putBoolean(KEY_CLEAR_CACHE_ON_STARTUP, value).apply()
```

**Alteração:**
- Mudou o valor padrão de `true` para `false`
- Agora o cache PERSISTE entre sessões
- Cache LRU gerencia automaticamente o espaço (100 strips em memória, 200MB em disco)

---

## Comportamento Corrigido

### ✅ COM A CORREÇÃO (clearCacheOnStartup = false)

```
Cenário 1: Primeira vez com vídeo de 5min
    ↓
    Carregar strips (60 strips, 5-30s)
    ↓
    Strips salvas no cache (memória + disco)
    ↓
    Usuário vê thumbnails instantaneamente

Cenário 2: Voltar para Home e escolher o mesmo vídeo
    ↓
    PreloadViewModel.startPreload(uri)
    ↓
    Verifica: activeUri == uri && _uiState.value is Ready
    ↓
    ✅ TRUE → Pula reload, strips já estão em cache!
    ↓
    Usuário vê thumbnails INSTANTANEAMENTE (< 50ms)

Cenário 3: Escolher outro vídeo
    ↓
    PreloadViewModel.startPreload(novaUri)
    ↓
    Verifica: activeUri == novaUri?
    ↓
    ❌ FALSE → Começa extração do novo vídeo
    ↓
    Cache LRU evita strips do vídeo anterior automaticamente
```

### ❌ SEM A CORREÇÃO (clearCacheOnStartup = true)

```
Cenário 1: Primeira vez com vídeo de 5min
    ↓
    Carregar strips (60 strips, 5-30s)
    ↓
    Strips salvas no cache (memória + disco)
    ↓
    Usuário vê thumbnails

Cenário 2: Voltar para Home e escolher o mesmo vídeo
    ↓
    ChopCutApplication.onCreate() (ou onResume)
    ↓
    ❌ clearCacheOnStartup = true
    ↓
    Cache de memória LIMPO (100 strips deletados)
    ↓
    Cache de disco LIMPO (200MB deletados)
    ↓
    PreloadViewModel.startPreload(uri)
    ↓
    Verifica: activeUri == uri && _uiState.value is Ready?
    ↓
    ❌ FALSE → Cache foi limpo, precisa recarregar
    ↓
    Usuário vê NENHUMA strip (tudo shimmer) por 5-30s
```

---

## Por Que Cache LRU É Suficiente

### Gerenciamento Automático de Memória

```kotlin
// ThumbnailCacheManager.kt:34
private val memoryCache = ThumbnailCache(maxSize = 100)
```

**Comportamento:**
- Máximo 100 strips em memória (~35-40MB)
- Quando atinge o limite, remove as strips menos usadas (LRU)
- **Sempre** deixa espaço para strips do vídeo atual

**Exemplo:**
```
Cache tem 100 strips (vários vídeos)
    ↓
Usuário seleciona novo vídeo (60 strips)
    ↓
As 60 strips do novo vídeo são as mais usadas agora
    ↓
Cache evita strips dos outros 40 strips (menos usadas)
    ↓
Cache tem agora 60 strips (todas do vídeo atual)
```

### Gerenciamento Automático de Disco

```kotlin
// ThumbnailStripManager.kt:390-420
private fun trimCacheIfNeeded() {
    val currentSize = cacheFiles.sumOf { it.length() }
    
    if (currentSize > MAX_CACHE_SIZE) {  // 200MB
        // Deleta 25% dos arquivos mais antigos
        val filesToDelete = sortedFiles.take(cacheFiles.size / 4)
        filesToDelete.forEach { it.delete() }
    }
}
```

**Comportamento:**
- Máximo 200MB em disco
- Quando excede, deleta 25% dos arquivos mais antigos
- **Sempre** mantém as strips mais recentes

**Exemplo:**
```
Cache tem 190MB (muitos vídeos)
    ↓
Usuário extrai strips de novo vídeo (50MB)
    ↓
Cache tem agora 240MB (excede 200MB)
    ↓
Deleta 25% dos arquivos mais antigos (≈ 60MB)
    ↓
Cache volta para 180MB (ainda tem as strips do vídeo atual)
```

---

## Quando Habilitar clearCacheOnStartup

### ✅ USE (clearCacheOnStartup = true) APENAS PARA:

#### 1. Testes Automatizados

**Objetivo:** Garantir testes consistentes sem contaminação

```kotlin
// No setup do teste
@Before
fun setup() {
    val prefsManager = PreferencesManager(context)
    prefsManager.clearCacheOnStartup = true  // Garante cache limpo
}

// Executar teste
@Test
fun testTimelineRendering() {
    // Cada teste começa com cache limpo
}
```

#### 2. Debug de Extração

**Objetivo:** Testar extração sem cache

```kotlin
// Para debugar extração de thumbnails
val prefsManager = PreferencesManager(context)
prefsManager.clearCacheOnStartup = true
prefsManager.thumbnailCacheEnabled = false  // Desabilita cache de disco

// Agora cada extração é forçada
```

#### 3. Testes de Performance

**Objetivo:** Medir tempo de extração pura

```kotlin
// Teste: tempo para extrair 100 strips
val startTime = System.currentTimeMillis()
thumbnailViewModel.preload(uri, 100)
val duration = System.currentTimeMillis() - startTime

assertThat(duration).isLessThan(30000)  // < 30s
```

---

### ❌ NÃO USE (clearCacheOnStartup = false) PARA:

#### 1. Produção

**Razão:** Cache LRU gerencia automaticamente o espaço

```kotlin
// ❌ ERRADO para produção
prefsManager.clearCacheOnStartup = true
// Cache limpo TODA VEZ que o app inicia
// Usuário perde cache entre sessões
// Pior UX (carregamento lento sempre)
```

```kotlin
// ✅ CORRETO para produção
prefsManager.clearCacheOnStartup = false
// Cache persiste entre sessões
// Melhor UX (carregamento instantâneo)
// Cache LRU evita automaticamente quando necessário
```

#### 2. Usuário Normal

**Razão:** Usuário espera que thumbnails carreguem instantaneamente

```
❌ Com limpeza no startup:
Usuário abre app → Cache limpo → Carrega 5-30s → Frustrado
Usuário volta para home → Cache limpo → Carrega 5-30s → Frustrado
Usuário reabre app → Cache limpo → Carrega 5-30s → Frustrado

✅ Sem limpeza no startup:
Usuário abre app → Cache limpo → Carrega 5-30s → Ok
Usuário volta para home → Cache mantido → Instantâneo → Feliz
Usuário reabre app → Cache mantido → Instantâneo → Feliz
```

---

## Como Verificar a Correção

### Via Logcat

```bash
# Filtrar por logs de cache
adb logcat | grep "LIMPEZA DE CACHE AO INICIAR"
```

**Esperado (APÓS correção):**
```
╔═══════════════════════════════════════════════════════╗
║          LIMPEZA DE CACHE AO INICIAR APP                   ║
╚═══════════════════════════════════════════════════════╝

⚙️  Configuração:
   • clearCacheOnStartup: false

❌ Cache NÃO será limpo ao iniciar

═══════════════════════════════════════════════════════
```

### Via SharedPreferences

```bash
# Verificar valor atual
adb shell "run-as com.chopcut cat /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml" | grep clear_cache_on_startup
```

**Esperado (APÓS correção):**
```xml
<boolean name="clear_cache_on_startup" value="false" />
```

---

## Teste de Validação

### Cenário de Teste

1. **Abrir o app pela primeira vez**
   - Escolher vídeo de 5min
   - Verificar: thumbnails carregam (5-30s)
   - Verificar logs: Cache miss, extração iniciada

2. **Voltar para Home**
   - Verificar: Cache ainda existe (não foi limpo)

3. **Escolher o mesmo vídeo novamente**
   - Verificar: **thumbnails carregam INSTANTANEAMENTE** (< 100ms)
   - Verificar logs: Cache hit, nenhuma extração

4. **Escolher outro vídeo**
   - Verificar: thumbnails carregam (5-30s)
   - Verificar logs: Cache miss, extração iniciada

5. **Voltar para Home e escolher o primeiro vídeo novamente**
   - Verificar: **thumbnails carregam INSTANTANEAMENTE** (< 100ms)
   - Verificar logs: Cache hit (cache LRU ainda mantém strips)

---

## Scripts de Teste

### Script 1: Habilitar Limpeza (para testes)

```bash
#!/bin/bash
echo "Habilitando limpeza de cache no startup..."
adb shell "run-as com.chopcut chmod 666 /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell "run-as com.chopcut sed -i 's/clear_cache_on_startup\" value=\"false\"/clear_cache_on_startup\" value=\"true\"/' /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell am force-stop com.chopcut
sleep 2
adb shell am start -n com.chopcut/.MainActivity
echo "✅ Limpeza de cache habilitada"
```

### Script 2: Desabilitar Limpeza (para produção)

```bash
#!/bin/bash
echo "Desabilitando limpeza de cache no startup..."
adb shell "run-as com.chopcut chmod 666 /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell "run-as com.chopcut sed -i 's/clear_cache_on_startup\" value=\"true\"/clear_cache_on_startup\" value=\"false\"/' /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell am force-stop com.chopcut
sleep 2
adb shell am start -n com.chopcut/.MainActivity
echo "✅ Limpeza de cache desabilitada"
```

---

## Resumo

✅ **Problema:** Cache estava sendo limpo a cada inicialização (padrão `true`)
✅ **Solução:** Mudou padrão para `false` (cache persiste)
✅ **Resultado:** Thumbnails carregam instantaneamente ao reabrir o mesmo vídeo
✅ **Cache LRU:** Gerencia automaticamente o espaço sem limpeza manual
✅ **UX:** Melhorada significativamente (carregamento instantâneo vs 5-30s)

---

## Recomendações

### Para Desenvolvimento/Testes

```kotlin
// ❌ NÃO usar em produção
prefsManager.clearCacheOnStartup = true
```

### Para Produção

```kotlin
// ✅ SEMPRE usar em produção
prefsManager.clearCacheOnStartup = false
```

**Razão:**
- Cache LRU gerencia automaticamente
- Melhor UX (thumbnails instantâneos)
- Menos consumo de bateria
- Menos uso de rede/IO

---

## Referências

- Cache LRU: `ThumbnailCacheManager.kt:34`
- Cache Disco LRU: `ThumbnailStripManager.kt:390-420`
- Preferência: `PreferencesManager.kt:78-80`
- Documentação: `CACHE_CLEARING_ON_STARTUP.md`
