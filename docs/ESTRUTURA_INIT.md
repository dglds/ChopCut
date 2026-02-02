# Estrutura de Inicialização - ChopCut

Documentação da arquitetura de inicialização e ciclo de vida da aplicação.

---

## 🚀 Fluxo de Inicialização

```
┌─────────────────────────────────────────────────────────────┐
│                    SISTEMA ANDROID                          │
│                   (ChopCutApplication)                      │
├─────────────────────────────────────────────────────────────┤
│  1. onCreate()                                              │
│     ├── Timber (Logging)                                    │
│     ├── FileLoggingTree (Logs em arquivo)                   │
│     └── ExceptionHandlerGlobal                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼ Intent
┌─────────────────────────────────────────────────────────────┐
│                   MAIN ACTIVITY                              │
│                  (MainActivity.kt)                           │
├─────────────────────────────────────────────────────────────┤
│  2. onCreate()                                              │
│     ├── enableEdgeToEdge()                                  │
│     ├── ChopCutTheme                                        │
│     ├── NavHost (Navigation Compose)                        │
│     └── Determina startDestination                          │
│         ├── "onboarding" (primeira execução)                │
│         └── "projects" (execuções normais)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 Componentes de Inicialização

### 1. ChopCutApplication

**Arquivo:** `app/src/main/java/com/chopcut/ChopCutApplication.kt`

Responsabilidades:
- Configuração global de logging (Timber)
- Captura de exceções não tratadas
- Inicialização de serviços de background (quando houver)

```kotlin
class ChopCutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Debug logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 2. File logging (para debug remoto)
        Timber.plant(FileLoggingTree(this))
        
        // 3. Exception handler global
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "FATAL: ${thread.name}")
            // Propaga para handler padrão
        }
    }
}
```

**Nota:** Não há injeção de dependências (DI) configurada. Todos os componentes são criados manualmente.

---

### 2. MainActivity

**Arquivo:** `app/src/main/java/com/chopcut/MainActivity.kt`

Responsabilidades:
- Configuração da UI (Jetpack Compose)
- Configuração de navegação (Navigation Compose)
- Determinação da tela inicial

#### Navegação

| Rota | Tela | Descrição |
|------|------|-----------|
| `onboarding` | OnboardingScreen | Primeira execução do app |
| `projects` | ProjectsScreen | Lista de projetos (destino padrão) |
| `home` | HomeScreen | Tela inicial legada |
| `editor?videoUri={}&projectId={}` | EditorScreen | Editor de vídeo |
| `settings` | SettingsScreen | Configurações |

#### Decisão de Start Destination

```kotlin
val startDestination = if (preferencesManager.isFirstRun) {
    "onboarding"
} else {
    "projects"
}
```

---

## 🗄️ Inicialização de Dados

### Database (Room)

**Arquivo:** `app/src/main/java/com/chopcut/data/local/ProjectDatabase.kt`

Padrão: Singleton com Double-Checked Locking

```kotlin
@Database(
    entities = [Project, EditOperationEntity, ExportPreset],
    version = 3
)
abstract class ProjectDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getDatabase(context: Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(...)
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

**Migrações:**
- `MIGRATION_2_3`: Adiciona colunas `fadeInMs` e `fadeOutMs`

**Observação:** `fallbackToDestructiveMigration()` reseta o banco se migração falhar (aceitável para app em desenvolvimento).

---

### PreferencesManager

**Arquivo:** `app/src/main/java/com/chopcut/data/local/PreferencesManager.kt`

Padrão: Instância por Context (não singleton)

```kotlin
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("chopcut_prefs", MODE_PRIVATE)
}
```

**Chaves armazenadas:**

| Chave | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `is_first_run` | Boolean | `true` | Primeira execução |
| `theme_mode` | Int | `0` | Tema (0=Sistema, 1=Claro, 2=Escuro) |
| `thumbnail_cache_enabled` | Boolean | `false` | Cache de thumbnails (inativo) |

---

### Repositories

Padrão: Instância por Context, criados sob demanda

```kotlin
// Criação típica em ViewModel
class EditorViewModel(
    private val context: Context
) : ViewModel() {
    
    private val projectRepository = ProjectRepository(context)
    private val videoRepository = VideoRepository(context)
    private val presetRepository = PresetRepository(context)
}
```

**Problema:** Múltiplas instâncias de Repository podem ser criadas. Cada uma cria sua própria instância de Database (mas Room gerencia singleton internamente).

---

## ⚠️ Problemas de Arquitetura Identificados

### 1. Ausência de DI (Dependency Injection)

**Situação:** Não há Hilt, Koin ou outro framework de injeção.

**Impacto:**
- Código de criação espalhado
- Difícil trocar implementações
- Testes unitários mais complexos

**Onde aparece:**
```kotlin
// Em MainActivity
val preferencesManager = remember { PreferencesManager(context) }

// Em ViewModels
private val repository = ProjectRepository(context) // Criado diretamente
```

---

### 2. Contexto Passado para ViewModel

**Situação:** ViewModels recebem `Context` no construtor.

**Exemplo:**
```kotlin
class EditorViewModel(
    context: Context,  // ❌ Anti-padrão
    videoUri: Uri
) : ViewModel()
```

**Problema:** ViewModel sobrevive a mudanças de configuração, mas Context pode ficar stale.

**Solução recomendada:** Usar `Application` context ou injeção de dependências.

---

### 3. Múltiplas Instâncias de Database

**Situação:** Cada Repository cria sua própria referência:

```kotlin
// ProjectRepository
private val database = ProjectDatabase.getDatabase(context)

// VideoRepository (possivelmente)
private val database = ProjectDatabase.getDatabase(context) // Outra chamada
```

**Mitigação:** Room internamente retorna singleton, mas padrão é confuso.

---

### 4. Inicialização de Theme

**Situação:** Theme é estático, não reage a mudanças de preferência.

```kotlin
// MainActivity
ChopCutTheme { // Não recebe parâmetro de tema
    // Conteúdo
}
```

**Observação:** `PreferencesManager.themeMode` existe mas não é utilizado.

---

## 📋 Recomendações de Melhoria

### Curto Prazo (Baixo Risco)

1. **Unificar criação de Repository**
   ```kotlin
   // Criar factory ou holder
   object RepositoryProvider {
       fun provideProjectRepository(context: Context): ProjectRepository
   }
   ```

2. **Adicionar Theme reativo**
   ```kotlin
   val themeMode by preferencesManager.themeModeFlow.collectAsState()
   ChopCutTheme(themeMode = themeMode) { ... }
   ```

### Médio Prazo

1. **Adicionar Hilt para DI**
   - Reduz boilerplate
   - Facilita testes
   - Gerencia ciclo de vida

2. **Separar Context de ViewModel**
   - Usar `SavedStateHandle` para argumentos
   - Injetar repositories

### Longo Prazo

1. **Modularização**
   - Módulo `core` (database, preferences)
   - Módulo `feature:editor`
   - Módulo `feature:projects`

---

## 🔍 Pontos de Inicialização por Módulo

### Editor

```
EditorScreen
├── EditorViewModel (criado via factory)
│   ├── ProjectRepository
│   ├── VideoRepository  
│   └── ExportServiceManager
├── PreviewManager
└── TimelineViewModel
```

### Projetos

```
ProjectsScreen
├── ProjectsViewModel
│   └── ProjectRepository
└── Export presets (via PresetRepository)
```

---

## 📝 Notas de Implementação

1. **Não há Service em foreground** configurado para exportação (usa WorkManager)
2. **Não há BroadcastReceiver** registrado dinamicamente
3. **Logging em arquivo** está ativo mas sem política de rotação
4. **Cache de thumbnails** existe nas preferências mas está desativado

---

*Documento criado em: 2026-01-30*  
*Versão: 1.0*  
*Foco: Análise de estrutura de inicialização (sem testes)*
