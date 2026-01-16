# Tech Stack - ChopCut

## Core
- **Linguagem Principal:** Kotlin (JVM 17) - Linguagem moderna e concisa, padrão para desenvolvimento Android.
- **Plataforma:** Android Nativo - Foco em performance e integração profunda com as APIs do sistema operacional.

## Frontend / UI
- **Framework:** Jetpack Compose - Toolkit moderno para construção de interfaces declarativas.
- **Design System:** Material Design 3 (M3) - Última evolução do sistema de design do Google para Android.
- **Navegação:** Jetpack Compose Navigation - Gerenciamento de rotas e navegação entre telas de forma tipada.

## Media Processing
- **Engine:** Media3 (ExoPlayer) - Player de vídeo de alta performance.
- **Transcoding/Editing:** Media3 Transformer - API para exportação, corte e aplicação de transformações em arquivos de mídia.
- **Effects:** Media3 Effects - Aplicação de efeitos visuais e de áudio via shaders e processadores de sinal.

## Storage & Data
- **Banco de Dados Local:** Room - Camada de abstração sobre SQLite para persistência de metadados de projetos e operações.
- **Preferências:** SharedPreferences / PreferencesManager - Armazenamento de configurações simples e estado de primeira execução.

## Architecture & Background
- **Background Tasks:** WorkManager - Garantia de execução de tarefas de exportação mesmo se o app for fechado.
- **Services:** Foreground Services - Para feedback em tempo real e prevenção de encerramento do processo durante exportações ativas.
- **Dependency Management:** Gradle Kotlin DSL com Version Catalogs (`libs.versions.toml`) - Gerenciamento centralizado de dependências.

## Logging & Utilities
- **Logging:** Timber - Utilitário de log extensível.
- **Asynchrony:** Kotlin Coroutines & Flow - Gerenciamento de concorrência e fluxos de dados reativos.
