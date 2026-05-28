# ChopCut — O Que Não Fazer 🚫

> Guia prático de anti-padrões e erros comuns que devem ser evitados a todo custo para manter a performance de 60 FPS, estabilidade de build e a integridade da arquitetura de pacote único.

---

## 🏗️ 1. Arquitetura e Estrutura de Código

### ❌ NÃO crie subpacotes ou diretórios de pacotes adicionais
* **O Motivo:** O projeto ChopCut foi intencionalmente arquitetado para utilizar um **pacote único** (`package com.chopcut`). Todos os arquivos Kotlin enxergam uns aos outros automaticamente.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (Evite criar pacotes separados)
  package com.chopcut.ui.editor.utils
  ```

### ❌ NÃO faça imports internos de arquivos da mesma aplicação
* **O Motivo:** Como todos os arquivos estão no pacote `com.chopcut`, imports internos são redundantes e causam avisos de compilação ou erros de visibilidade.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (Evite importar coisas do mesmo package)
  import com.chopcut.core.VideoInfo
  import com.chopcut.ui.SharedComponents
  ```

### ❌ NÃO crie novos arquivos `.kt` sem extrema necessidade
* **O Motivo:** A base de código está travada em **20 arquivos de código-fonte estruturados**. Qualquer componente novo de UI, utilitário ou modelo deve ser acoplado em um dos arquivos existentes correspondentes (ex: novos modelos de dados em `core/Models.kt`, novos utilitários em `core/Utils.kt`).

---

## ⚡ 2. Performance e Desenho no Canvas (Evitando Jank/Stutter)

### ❌ NUNCA instancie objetos pesados dentro do escopo de desenho de um Canvas
* **O Motivo:** O método `Canvas` ou `onDraw` é chamado a cada frame (60 vezes por segundo). Instanciar objetos como `Paint()`, `Path()`, `Rect()`, `Brush.linearGradient()` ou `CornerRadius` dentro dele força o Garbage Collector (GC) a rodar constantemente, causando quedas bruscas de taxa de quadros (jank).
* **O que não fazer:**
  ```kotlin
  Canvas(modifier = modifier) {
      // 🚫 NUNCA FAÇA ISSO (Não instancie objetos de desenho dentro do Canvas)
      val paint = Paint().apply { color = Color.Red } 
      val rect = Rect(0f, 0f, 100f, 100f)
      
      // ✅ FAÇA ISSO: declare fora do Canvas usando `remember` e reutilize!
  }
  ```

### ❌ NÃO acesse leituras de estados de alta frequência (como progresso contínuo de milissegundos) diretamente fora de blocos controlados
* **O Motivo:** Atualizações a cada milissegundo disparam recomposições em cascata em toda a tela se não forem adequadamente isoladas ou encapsuladas. Use `rememberUpdatedState` ou isole em Canvas específicos de animação.

---

## 🛠️ 3. Sistema de Builds e Compilação

### ❌ NÃO execute `./gradlew assembleDebug` direto no terminal do sistema sem configurar o JDK
* **O Motivo:** A máquina host pode utilizar versões mais recentes do Java (ex: Java 25) por padrão. O projeto ChopCut exige o **JDK 17** local para compilar corretamente.
* **O que não fazer:**
  ```bash
  # 🚫 NUNCA FAÇA ISSO (Pode quebrar por incompatibilidade de versão de Java)
  ./gradlew assembleDebug
  
  # ✅ FAÇA ISSO: use o painel Go centralizador de tarefas ou defina a variável:
  ./gradle-menu
  # OU:
  JAVA_HOME=./jdk17 ./gradlew assembleDebug
  ```

### ❌ NÃO commite código sem rodar a verificação de compilação rápida antes
* **O Motivo:** Alterações em assinaturas de funções ou imports de terceiros podem quebrar o build silenciosamente.
* **O que não fazer:** Commitar direto na pressa. Sempre execute:
  ```bash
  JAVA_HOME=./jdk17 ./gradlew :app:compileDebugKotlin
  ```

---

## 📱 4. Gerenciamento de Estado e ViewModels (Jetpack Compose)

### ❌ NÃO recrie ViewModels Activity-scoped dentro de sub-composables
* **O Motivo:** Os ViewModels principais (`EditorViewModel`, `AudioViewModel`, `ThumbnailViewModel`) devem ser instanciados uma única vez na `MainActivity` ou no NavGraph de entrada para manter o estado unificado do player e da edição.
* **O que não fazer:**
  ```kotlin
  @Composable
  fun TimelineV2SubComponent() {
      // 🚫 NUNCA FAÇA ISSO (Cria uma nova instância órfã de ViewModel)
      val viewModel: EditorViewModel = viewModel() 
  }
  ```

### ❌ NÃO crie classes, enums ou objetos com nomes duplicados
* **O Motivo:** Devido ao escopo global do pacote único (`com.chopcut`), a existência de duas classes com o mesmo nome em arquivos diferentes resultará em erros graves de redefinição pelo compilador (`Redeclaration error`).

---

## 📝 5. Documentação e Boas Práticas de Trabalho

### ❌ NÃO finalize uma sessão sem atualizar o histórico de sessões
* **O Motivo:** A transparência e o histórico estruturado de sessões (arquivos `session#NN.md`) garantem que qualquer IA ou desenvolvedor parceiro saiba exatamente o estado em que o software foi deixado.

### ❌ NÃO esqueça de atualizar as Regras da Arquitetura ao fazer mudanças estruturais
* **O Motivo:** Conforme especificado nas regras do `README.md`, o arquivo `ChopCut - Regras da Arquitetura.md` deve ser mantido sempre 100% atualizado para servir de bússola para o desenvolvimento do projeto.
