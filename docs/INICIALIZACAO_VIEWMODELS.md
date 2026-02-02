# Inicialização de ViewModels - ChopCut

Análise detalhada da criação e ciclo de vida dos ViewModels.

---

## 🏭 Criação de ViewModels

### Padrão Atual: Factory Manual

Como não há DI (Hilt/Koin), cada ViewModel com parâmetros precisa de uma Factory.

```kotlin
// Exemplo: EditorViewModel
class EditorViewModel(
    private val context: Context,
    private val videoUri: Uri,
    private val projectId: String?
) : ViewModel()

// Factory correspondente
class EditorViewModelFactory(
    private val context: Context,
    private val videoUri: Uri,
    private val projectId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditorViewModel(context, videoUri, projectId) as T
    }
}
```

#### Uso na UI

```kotlin
@Composable
fun EditorScreen(...) {
    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModelFactory(
            context = context,
            videoUri = videoUri,
            projectId = projectId
        )
    )
}
```

---

## 📊 Árvore de Dependências

### EditorViewModel

```
EditorViewModel (context, videoUri, projectId)
├── ContentResolver (do context)
├── VideoRepository (context) 
│   └── ProjectDatabase.getDatabase(context)
├── ProjectRepository (context)
│   └── ProjectDatabase.getDatabase(context)
├── PresetRepository (context)
│   └── ProjectDatabase.getDatabase(context)
├── AudioDataExtractor (context)
├── TranscodePipeline (context, videoRepository)
│   └── Usa VideoRepository internamente
├── ThumbnailExtractor (context)
├── UndoManager (sem dependências externas)
├── ExportServiceManager (context)
└── ExportWorkScheduler (implícito via ServiceManager)
```

**Problema:** Múltiplos repositories, cada um criando sua própria conexão com database.

---

### TimelineViewModel

```
TimelineViewModel (initialDurationMs: Long)
└── StateFlow<TimelineEditorState>
    ├── totalDurationMs
    ├── playheadPositionMs
    └── ranges: List<TrimRangeData>
```

**Nota:** Não depende de Context. É o ViewModel mais simples e "puro".

---

### ProjectsViewModel

```
ProjectsViewModel (context)
└── ProjectRepository (context)
    └── ProjectDatabase
```

---

## ⚠️ Problemas de Inicialização

### 1. Context em ViewModel

**Código problemático:**
```kotlin
class EditorViewModel(
    private val context: Context,  // ❌ Problema: pode vazar memória
    ...
) : ViewModel()
```

**Por que é problema:**
- ViewModel sobrevive a rotação de tela
- Activity/Fragment pode ser destruído e recriado
- Context antigo fica retido na memória

**Mitigação atual:**
```kotlin
// Usar context.applicationContext quando possível
private val safeContext = context.applicationContext
```

**Solução ideal:**
```kotlin
// Com Hilt
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,  // Injetado
    ...
) : ViewModel()
```

---

### 2. Inicialização Lazy vs Eager

**Comportamento atual:** Eager (tudo criado no `init`)

```kotlin
class EditorViewModel(...) : ViewModel() {
    // Tudo criado imediatamente
    private val videoRepository = VideoRepository(context)
    private val audioDataExtractor = AudioDataExtractor(context)
    private val thumbnailExtractor = ThumbnailExtractor(context)
    // ...
}
```

**Problema:** Mesmo que não use certas features, recursos são alocados.

**Sugestão:** Lazy initialization
```kotlin
class EditorViewModel(...) : ViewModel() {
    private val videoRepository by lazy { VideoRepository(context) }
    private val audioDataExtractor by lazy { AudioDataExtractor(context) }
    // Só cria quando usar
}
```

---

### 3. Múltiplas Instâncias de Repository

**Cenário:**
```kotlin
// EditorViewModel cria:
private val videoRepository = VideoRepository(context)
private val projectRepository = ProjectRepository(context) 
private val presetRepository = PresetRepository(context)

// Cada um faz:
ProjectDatabase.getDatabase(context) // Mesma instância (singleton), mas padrão confuso
```

**Problema:** Não há controle centralizado de dependências.

**Solução:** Service Locator simples (sem DI framework)
```kotlin
object ServiceLocator {
    private var database: ProjectDatabase? = null
    
    fun provideDatabase(context: Context): ProjectDatabase {
        return database ?: ProjectDatabase.getDatabase(context).also {
            database = it
        }
    }
    
    fun provideVideoRepository(context: Context): VideoRepository {
        return VideoRepository(provideDatabase(context))
    }
    // ...
}
```

---

## 🔄 Ciclo de Vida

### Criação (onCreate da Activity)

```
MainActivity.onCreate()
├── setContent { ChopCutTheme { ... } }
├── NavHost
│   └── composable("editor") {
│       └── EditorScreen()
│           ├── viewModel { EditorViewModelFactory(...) }
│           │   └── EditorViewModel criado
│           └── UI observa StateFlows
```

### Durante Execução

```
EditorViewModel
├── StateFlows emitem atualizações
│   ├── _videoInfo -> UI atualiza info do vídeo
│   ├── _currentVideoUri -> Player carrega vídeo
│   ├── edits (via UndoManager) -> UI mostra estado de undo/redo
│   └── saveStatus -> UI mostra "Salvando..."
└── Coroutines em viewModelScope
    ├── Carregamento de metadados do vídeo
    ├── Extração de thumbnails
    └── Auto-save periódico
```

### Destruição (onCleared)

```kotlin
override fun onCleared() {
    super.onCleared()
    // Cleanup automático pelo viewModelScope
    // Coroutines são canceladas
    // Mas recursos externos (extractors) podem precisar cleanup manual
}
```

**Nota:** Atualmente `onCleared()` não está sobrescrito em nenhum ViewModel para fazer cleanup explícito.

---

## 📈 Estados de Inicialização

### EditorViewModel - Sequência de Setup

```
1. Construtor
   └── Cria todas as dependências
   
2. init (bloco de inicialização)
   ├── Carrega projeto (se projectId != null)
   ├── Inicializa videoUri atual
   └── Inicia coletores de estado

3. Primeira coleta de StateFlow
   ├── videoInfo é carregado assincronamente
   ├── Thumbnails são extraídas
   └── Waveform é gerada (áudio)
```

---

## 💡 Recomendações

### Imediato (Sem mudança arquitetural)

1. **Sobrescrever onCleared()**
   ```kotlin
   override fun onCleared() {
       super.onCleared()
       // Cancelar jobs pendentes
       autoSaveJob?.cancel()
       // Liberar recursos
       audioDataExtractor.release()
   }
   ```

2. **Usar lazy para dependências pesadas**
   ```kotlin
   private val audioDataExtractor by lazy { AudioDataExtractor(context) }
   ```

3. **Trocar Context por Application**
   ```kotlin
   class EditorViewModel(
       private val application: Application,  // ✅ Seguro
       ...
   ) : AndroidViewModel(application)
   ```

### Médio Prazo (Com DI)

1. **Adicionar Hilt**
   ```kotlin
   @HiltViewModel
   class EditorViewModel @Inject constructor(
       private val videoRepository: VideoRepository,
       private val projectRepository: ProjectRepository,
       @Assisted private val videoUri: Uri,
       @Assisted private val projectId: String?
   ) : ViewModel()
   ```

2. **Módulos de Provider**
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object DatabaseModule {
       @Provides
       @Singleton
       fun provideDatabase(@ApplicationContext context: Context): ProjectDatabase
   }
   ```

---

## 📋 Resumo de ViewModels

| ViewModel | Parâmetros | Factory | Singletons Criados |
|-----------|------------|---------|-------------------|
| EditorViewModel | Context, Uri, String? | ✅ Sim | 5+ repositories |
| TimelineViewModel | Long (duration) | ❌ Não | State interno |
| ProjectsViewModel | Context | ❌ Não | 1 repository |
| HomeViewModel | Nenhum | ❌ Não | Nenhum |
| SettingsViewModel | Context | ❌ Não | 1 repository |

**Legenda:**
- Factory = Precisa de factory manual para receber parâmetros
- Singletons = Quantidade de objetos criados no construtor

---

*Documento criado em: 2026-01-30*  
*Versão: 1.0*  
*Foco: Análise de criação e ciclo de vida de ViewModels*
