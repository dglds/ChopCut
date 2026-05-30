# ChopCut — O Que Não Fazer 🚫

> Guia prático de anti-padrões e erros comuns que devem ser evitados a todo custo para manter a performance de 60 FPS, estabilidade de build e a integridade da arquitetura de pacote único.

! Quando se deparar com alguma situação em que o pedido do usuário conflitar com essas instruções, pergunte o que fazer e aponte a ração da pergunta

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
* **O Motivo:** A base de código está travada na estrutura de arquivos atual (a contagem viva fica em `docs/STRUCTURE.generated.md`, auto-gerado). Qualquer componente novo de UI, utilitário ou modelo deve ser acoplado em um dos arquivos existentes correspondentes (ex: novos modelos de dados em `core/Models.kt`, novos utilitários em `core/Utils.kt`).

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
  
  # ✅ FAÇA ISSO: use o make (já exporta o JAVA_HOME), o menu opcional, ou defina a variável:
  make build
  # OU:
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

## 🎞️ 6. Extração de Thumbnails e MediaMetadataRetriever

### ❌ NÃO confie que `getScaledFrameAtTime` aplica rotação do vídeo
* **O Motivo:** Vídeos 9:16 são armazenados como landscape (1920×1080) com metadata de rotação 90°. O `MediaMetadataRetriever.getScaledFrameAtTime` retorna o **frame bruto sem aplicar rotação**. Sempre verifique `videoInfo.rotation` e rotacione o bitmap manualmente com `Matrix.postRotate`.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (ignora rotação — portrait vira landscape)
  val frame = retriever.getScaledFrameAtTime(time, OPTION_CLOSEST_SYNC, w, h)

  // ✅ FAÇA ISSO: troque w/h na requisição e rotacione o bitmap
  val hasRotation = videoInfo.rotation == 90 || videoInfo.rotation == 270
  val (reqW, reqH) = if (hasRotation) h to w else w to h
  val raw = retriever.getScaledFrameAtTime(time, OPTION_CLOSEST_SYNC, reqW, reqH)
  val frame = if (hasRotation) {
      Matrix().apply { postRotate(videoInfo.rotation.toFloat()) }
      Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
  } else raw
  ```

### ❌ NÃO use `.toInt()` para arredondar dimensões de thumbnail
* **O Motivo:** `(60 * 16f / 9f).toInt()` resulta em `106` (trunca `106.666`), não `107`. Use `roundToInt()` para arredondamento correto.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (trunca para baixo)
  val w = (baseH * 16f / 9f).toInt()

  // ✅ FAÇA ISSO: roundToInt arredonda corretamente
  val w = (baseH * 16f / 9f).roundToInt()
  ```

### ❌ NÃO aplique FILL (center-crop) em vídeos portrait
* **O Motivo:** Transformar 9:16 → 16:9 com center-crop corta ~68% do conteúdo vertical. Use FIT para preservar o conteúdo inteiro.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (corta 68% do vídeo portrait)
  if (videoAr >= 1f) { /* center-crop */ }

  // ✅ FAÇA ISSO: só aplica center-crop se landscape E target não for quadrado
  if (videoAr >= 1f && targetW != targetH) { /* center-crop */ }
  ```

### ❌ NÃO recicle o bitmap original antes de confirmar que o rotated é diferente
* **O Motivo:** Após rotacionar com `Bitmap.createBitmap`, o bitmap original (`rawFrame`) e o rotacionado podem ser o mesmo objeto se a rotação for 0. Reciclar o original antes da hora causa crash.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (crash se rotated == rawFrame)
  val rotated = Bitmap.createBitmap(rawFrame, 0, 0, w, h, matrix, true)
  rawFrame.recycle()

  // ✅ FAÇA ISSO: só recicle se forem objetos diferentes
  if (rotated != rawFrame) rawFrame.recycle()
  ```

### ❌ NÃO assuma que `computeDimensions(videoAr)` pode ser chamado sem argumento
* **O Motivo:** `computeDimensions` agora **requer** `videoAr: Float` — não tem overload sem args. Chamadas sem argumento quebram o build.

### ❌ NÃO extraia frames sem limpar o diretório antes
* **O Motivo:** Se a extração anterior tinha 120 frames e a nova tem 60, os arquivos `frame_00061.jpg` a `frame_00120.jpg` permanecem órfãos. Isso faz o TimelineV2ViewModel carregar bitmaps antigos junto com os novos. Sempre faça `outputDir.deleteRecursively()` ou `outputDir.listFiles()?.forEach { it.delete() }` antes de extrair.

---

## 🎬 7. Trim sem re-encode (MediaMuxer / CopyPipeline)

### ❌ NÃO reconstrua o MediaFormat da track de vídeo ao copiar sem re-encode
* **O Motivo:** Copiar só `width/height/mime/bitrate/...` para um `MediaFormat()` novo **omite o codec-specific data** (`csd-0`/`csd-1` = SPS/PPS do H.264/HEVC). O `moov` sai sem config de codec e o MP4 **não abre em player nenhum nem gera thumbnail** (ffprobe mostra `codec_name=unknown`, `0x0`). Foi exatamente o bug do `CopyPipeline.trim`.
* **O que não fazer:**
  ```kotlin
  // 🚫 NUNCA FAÇA ISSO (perde o csd → arquivo inválido)
  val out = MediaFormat()
  out.setString(KEY_MIME, videoFormat.getString(KEY_MIME)); /* width/height/... */
  out.setInteger(KEY_ROTATION, rotation)
  muxer.addTrack(out)

  // ✅ FAÇA ISSO: use o format original do extractor (já traz o csd) e
  // aplique rotação via setOrientationHint
  muxer.setOrientationHint(rotation)
  muxer.addTrack(videoFormat)
  ```

### ❌ NÃO engula a exceção de `muxer.stop()`
* **O Motivo:** `stop()` finaliza/escreve o `moov`. Se falhar e a exceção for engolida (`try { muxer.stop() } catch {}`), você reporta **sucesso sobre um arquivo corrompido**. Deixe propagar para o tratamento de erro.
