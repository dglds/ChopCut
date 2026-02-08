# Design System ChopCut

> **Video Editor Mobile** | Jetpack Compose | Material 3

Última atualização: 2026-02-08

---

## 🎨 Identidade Visual

### Personalidade
- **Criativo** - Ferramenta de expressão visual
- **Moderno** - Tecnologia de ponta em edição móvel
- **Intuitivo** - Uso simples e direto
- **Preciso** - Controles exatos para edição de vídeo

### Palavras-chave
- Video-first
- Dark mode cinematográfico
- Acento vibrante (play red)
- Alto contraste

---

## 🎨 Paleta de Cores

### Primária (Cinema Dark)
```
Nome        | Uso              | Light  | Dark
------------|------------------|--------|------------------------
Background  | Fundo principal  | #FFFFFF | #0F0F23 (23,15,35)
Surface     | Cards/Paneis     | #F8FAFC | #1E1B4B (30,27,75)
Surface Var | Elevação         | #F1F5F9 | #2D2B55
```

### Acento (Play Red)
```
Nome        | Uso              | Light  | Dark
------------|------------------|--------|------------------------
Primary     | CTA, Play        | #E11D48 | #E11D48 (225,29,72)
On Primary  | Texto sobre primá| #FFFFFF | #FFFFFF
Container   | BG do botão      | #FFDDE5 | #9F1239
```

### Funcional
```
Nome        | Uso              | Light  | Dark
------------|------------------|--------|------------------------
Success     | Export ok        | #10B981 | #059669
Warning     | Alertas          | #F59E0B | #D97706
Error       | Erros            | #EF4444 | #DC2626
Info        | Informações      | #3B82F6 | #2563EB
```

### Timeline (cores específicas)
```
Nome        | Uso              | Dark
------------|------------------|------------------------
Timeline BG | Fundo timeline   | #0A0A1A (10,10,26)
Track       | Trilha de vídeo  | #2A2A4A (42,42,74)
Playhead    | Indicador play   | #E11D48 (225,29,72)
Selection   | Seleção ativa    | #E11D48 + 30% alpha
Trim Handle | Alças de corte   | #FFFFFF
Waveform    | Forma de onda    | #6366F1 (99,102,241)
```

### Texto
```
Nome        | Uso              | Light  | Dark
------------|------------------|--------|------------------------
On BG       | Texto principal  | #0F172A | #F8FAFC
On Surface  | Texto secundário | #475569 | #94A3B8
Disabled    | Texto desabilitado| #CBD5E1| #475569
```

### Contraste (WCAG AA)
- Todos os textos têm contraste mínimo de 4.5:1
- Elementos interativos têm contraste mínimo de 3:1

---

## ✏️ Tipografia

### Fontes
```
Role        | Fonte    | Peso           | Uso
------------|----------|----------------|------------------------
Heading     | Fredoka  | 600, 700       | Títulos grandes
Body        | Nunito   | 400, 500, 600  | Texto corrido
Mono        | Roboto Mono | 400       | Duração, timestamps
```

### Escala (sp)
```
Estilo      | Tamanho | Peso  | Line Height
------------|---------|-------|-------------
Display     | 57      | 700   | 64
Headline    | 32      | 700   | 40
Title Large | 22      | 600   | 28
Title Medium| 16      | 600   | 24
Title Small | 14      | 600   | 20
Body Large  | 16      | 400   | 24
Body Medium | 14      | 400   | 20
Body Small  | 12      | 400   | 16
Label       | 11      | 500   | 16
```

### Padrões de Uso
- **Títulos de tela**: Headline (32sp)
- **Títulos de seção**: Title Large (22sp)
- **Botões**: Label (11sp) ou Title Medium (16sp)
- **Duração de vídeo**: Mono, Body Small (12sp)

---

## 📐 Espaçamento

### Escala (dp)
```
Nome    | Valor | Uso
--------|-------|------------------------
xxs     | 4     | Gap entre ícones
xs      | 8     | Padding pequeno
sm      | 12    | Gap entre elementos relacionados
md      | 16    | Padding padrão
lg      | 24    | Margem de seção
xl      | 32    | Espaçamento entre seções
xxl     | 48    | Margem de tela
```

### Padding padrão
- **Telas**: 16dp (md) horizontal, 16dp vertical
- **Cards**: 16dp interno
- **Botões**: 16dp horizontal, 12dp vertical

---

## 🔘 Componentes

### Botão Primary
```kotlin
@Composable
fun ChopCutButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
)
```
- Background: Primary (#E11D48)
- Texto: On Primary (#FFFFFF)
- Altura mínima: 48dp
- Border radius: 12dp
- Elevação: 0dp (estilo plano)

### Botão Secondary
```kotlin
@Composable
fun ChopCutSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Background: Surface + borda
- Texto: On Surface
- Altura mínima: 48dp
- Border: 1dp

### Botão Icon (FAB)
```kotlin
@Composable
fun ChopCutFab(
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- Tamanho: 56dp
- Background: Primary
- Icon: 24dp
- Sombra: 8dp

### Card
```kotlin
@Composable
fun ChopCutCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
```
- Background: Surface
- Border radius: 16dp
- Elevação: 2dp (sombra sutil)
- Padding: 16dp

### Video Thumbnail Card
```kotlin
@Composable
fun VideoCard(
    thumbnail: Painter,
    title: String,
    duration: String,
    onClick: () -> Unit
)
```
- Aspect ratio: 16:9
- Overlay: gradient bottom-to-top
- Título: Body Medium
- Duração: Label Mono, badge top-right

---

## ⚡ Animações

### Durações
```kotlin
object ChopCutAnimation {
    val Fast = 150 ms      // Micro-interações
    val Normal = 250 ms    // Transições padrão
    val Slow = 350 ms      // Transições complexas
}
```

### Easings
```kotlin
val StandardEasing = FastOutSlowInEasing
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
```

### Padrões
- **Fade in/out**: Usar `animateFloatAsState`
- **Slide**: Usar `animateDpAsState` com offset
- **Scale**: Evitar em botões (causa layout shift)
- **Press**: Opacidade para feedback (0.7f)

---

## 👆 Touch & Interação

### Touch Targets
- **Mínimo**: 48dp × 48dp
- **Recomendado**: 56dp × 56dp
- **Gap entre targets**: 8dp mínimo

### Feedback Visual
```
Estado     | Ação
-----------|------------------------
Normal     | Opacidade 1.0
Pressed    | Opacidade 0.7
Hover      | Sutil overlay
Disabled   | Opacidade 0.5
Focused    | Borda 2dp Primary
```

### Haptics
```kotlin
// Feedback para ações importantes
fun hapticConfirm(context: Context) {
    val vibrator = context.getSystemService<Vibrator>()
    vibrator?.vibrate(VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK)
        .compose())
}

// Feedback para conclusão longa (export)
fun hapticSuccess(context: Context) {
    // Pattern: [100ms wait, 50ms vibrate, 100ms wait, 50ms vibrate]
}
```

---

## 🎬 Componentes de Edição

### Timeline
```kotlin
@Composable
fun VideoTimeline(
    duration: Duration,
    ranges: List<TimeRange>,
    selectedRange: TimeRange,
    onRangeChange: (TimeRange) -> Unit
)
```
- Altura: 72dp
- Fundo: Timeline BG (#0A0A1A)
- Trilha: Track (#2A2A4A)
- Playhead: linha 2dp Primary, com "handle" circular
- Seleção: overlay Primary 30%
- Trim handles: círculos 24dp brancos

### Trim Slider
```kotlin
@Composable
fun TrimSlider(
    startPosition: Float, // 0-1
    endPosition: Float,   // 0-1
    onPositionChange: (start: Float, end: Float) -> Unit
)
```
- Touch area expandida: 48dp ao redor de cada handle
- Visual: linha com círculos nas pontas
- Feedback: haptic ao soltar

### Waveform
```kotlin
@Composable
fun WaveformView(
    samples: List<Float>,
    highlightedRange: ClosedRange<Float>
)
```
- Cor base: Waveform (#6366F1)
- Cor destacada: Primary
- Altura: 48dp
- Linhas: 1dp de largura

---

## ♿ Acessibilidade

### Contraste
- Texto normal: mínimo 4.5:1
- Texto grande (>18sp): mínimo 3:1
- Elementos interativos: mínimo 3:1

### Tamanhos
- Texto corpo: mínimo 16sp (configurável pelo usuário)
- Touch targets: mínimo 48dp

### Semântica
```kotlin
Modifier.semantics {
    this.contentDescription = "Botão de exportar vídeo"
    this.role = Role.Button
    this.stateDescription = "Habilitado"
}
```

### Reduzir Movimento
```kotlin
val animationSpec by remember {
    derivedStateOf {
        if (LocalAccessibilityManager.current?.isMotionReduced == true)
            SpringSpec(dampingRatio = 1f, stiffness = 100f)
        else
            tween(durationMillis = ChopCutAnimation.Normal)
    }
}
```

---

## 🚀 Anti-patterns (EVITAR)

| ❌ Não faça | ✅ Faça |
|-------------|---------|
| Cores roxas padrão do Material | Paleta "Cinema Dark + Play Red" |
| Emojis como ícones | SVG icons (ImageVector) |
| Touch targets <48dp | Mínimo 48dp |
| Animações de scale em hover | Mudança de cor/opacidade |
- Texto cinza claro em light mode | Contraste mínimo 4.5:1 |
| Um estado disabled (apenas cinza) | Disabled + ícone opaco |
| Botões sem feedback visual | Opacidade 0.7 no pressed |

---

## 📁 Estrutura de Código

```
com.chopcut.ui/
├── theme/
│   ├── Color.kt           # Paleta completa
│   ├── Type.kt            # Tipografia
│   └── Theme.kt           # Tema Compose
├── components/
│   ├── buttons/           # Botões (Primary, Secondary, FAB)
│   ├── cards/             # Cards (VideoCard, ProjectCard)
│   ├── timeline/          # Timeline, Waveform, TrimSlider
│   └── feedback/          # Loading, Error, Empty states
├── atoms/                 # Componentes reutilizáveis pequenos
│   ├── DurationLabel.kt
│   └── ProgressBar.kt
└── screen/                # Telas compostas de componentes
```

---

## 📚 Referências

- [Material 3 Design Tokens](https://m3.material.io/styles)
- [Compose Accessibility](https://developer.android.com/jetpack/compose/accessibility)
- [Android Touch Guidelines](https://developer.android.com/develop/ui/views/touch-and-input)
