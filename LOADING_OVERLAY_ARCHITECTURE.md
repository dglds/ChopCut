# LoadingOverlay - Arquitetura e Implementação

## 📋 Visão Geral

Sistema de loading inteligente para a TrimScreen que oferece navegação instantânea da HomeScreen e transição suave com feedback visual consistente.

## 🎯 Objetivos Alcançados

1. ✅ **Navegação instantânea** - HomeScreen → TrimScreen sem espera
2. ✅ **Loading contextualizado** - Overlay dentro da TrimScreen
3. ✅ **Duração inteligente** - 5-10 segundos baseado em progresso real
4. ✅ **Transições suaves** - Cross-fade de 500ms entre overlay e conteúdo
5. ✅ **Feedback visual** - Barra de progresso falsa + mensagens genéricas

## 📁 Estrutura de Arquivos

```
app/src/main/java/com/chopcut/
├── ui/
│   ├── components/
│   │   └── loading/
│   │       ├── LoadingConstants.kt       ← Constantes centralizadas
│   │       ├── LoadingOverlay.kt         ← Componente principal
│   │       ├── LoadingAnimation.kt       ← Animações e progressos
│   │       └── ShimmerEffect.kt          ← Efeitos visuais
│   └── screen/
│       ├── TrimScreen.kt                 ← Integração do overlay
│       ├── TrimViewModel.kt              ← Gerenciamento de preload
│       ├── PreloadViewModel.kt           ← Lógica de preload
│       └── PreloadUiState.kt             ← Estados de preload
└── res/
    └── raw/
        └── loading_video.json            ← Animação Lottie
```

## 🔧 Componentes Principais

### 1. LoadingConstants.kt

Centraliza todas as constantes de configuração:

```kotlin
object LoadingConstants {
    // Duração
    const val MIN_LOADING_DURATION_MS = 5_000L
    const val MAX_LOADING_DURATION_MS = 10_000L
    const val TARGET_DURATION_MS = 7_000L

    // Progresso
    const val MINIMUM_THUMBNAIL_PROGRESS = 50f

    // Animações
    const val OVERLAY_FADE_OUT_DURATION_MS = 700
    const val TRIM_FADE_IN_DURATION_MS = 600
    const val CROSS_FADE_START_DELAY_MS = 200

    // Outros...
}
```

### 2. TrimScreen.kt

**Responsabilidades:**
- Monitorar estado de preload
- Calcular progresso de thumbnails
- Determinar quando esconder overlay
- Gerenciar transições

**Funções Auxiliares:**
- `calculateThumbnailProgress()` - Calcula % de thumbnails carregadas
- `shouldHideLoadingOverlay()` - Decide quando esconder overlay
- `getHideReason()` - Gera mensagem de log

**Lógica de Duração:**
```kotlin
// Esconde quando:
1. Ready + thumbnails > 50%           → Imediato (< 5s possível)
2. Loading + 5s + thumbnails > 50%    → Após mínimo de 5s
3. Timeout 10s                        → Sempre
4. Error/Cancelled                    → Imediato
```

### 3. LoadingOverlay.kt

**Estrutura:**
```
┌─────────────────────────────────────┐
│         LoadingOverlay              │
│  ┌───────────────────────────────┐  │
│  │      LoadingCard              │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  LottieAnimation        │  │  │
│  │  └─────────────────────────┘  │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  CircularProgress       │  │  │
│  │  └─────────────────────────┘  │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  StageMessage           │  │  │
│  │  └─────────────────────────┘  │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  FakeProgressBar        │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Animações:**
- **Fade Out**: 700ms (suave e lento)
- **Scale Out**: 1.0 → 0.97 (bem sutil)

### 4. LoadingAnimation.kt

**Componentes:**

#### LottieLoadingAnimation
- Animação principal em Lottie
- Loop infinito com pulsação sutil (0.95 → 1.05)

#### CircularProgressWithPercentage
- Indicador circular visual (sem texto)
- Rotação contínua (2000ms)
- 80dp × 4dp stroke

#### StageMessage
- Mensagens genéricas amigáveis
- Animação de pulsação de opacidade
- Transição suave entre mensagens

#### FakeProgressBar
- Barra baseada em tempo decorrido
- Progressão não-linear (0-50-80-90%)
- Completa para 100% quando pronto

**Progressão Não-Linear:**
```
Tempo    Progresso   Fase
0-2.1s   0% → 50%   Rápido (mostra atividade)
2.1-4.9s 50% → 80%  Médio (progresso estável)
4.9-7s   80% → 90%  Lento (aproximando fim)
7-10s    90% → 95%  Muito lento (aguardando)
Ready    → 100%     Completa (300ms)
```

### 5. TrimViewModel.kt

**Responsabilidades:**
- Gerenciar PreloadViewModel interno
- Expor estados de preload
- Iniciar preload se dados não disponíveis
- Permitir cancelamento

**Estados Expostos:**
```kotlin
val preloadState: StateFlow<PreloadUiState>
val preloadedDataFlow: StateFlow<PreloadedData?>
```

## 🎬 Fluxo de Execução

### Timeline Completa

```
HomeScreen (usuário clica "Editar")
    ↓ 0ms
Navegação IMEDIATA para TrimScreen
    ↓ 0ms
┌─────────────────────────────────────────────┐
│ TrimScreen renderiza LoadingOverlay         │
│ (conteúdo da TrimScreen NÃO renderizado)    │
└─────────────────────────────────────────────┘
    ↓
PreloadViewModel carrega em background
    ↓ (verificação a cada 100ms)
    ↓
┌─────────────────────────────────────────────┐
│ Aguarda condição de saída:                  │
│ - Ready + thumbnails > 50%      (imediato)  │
│ - Loading + 5s + thumbnails > 50% (mínimo)  │
│ - Timeout 10s                   (máximo)    │
└─────────────────────────────────────────────┘
    ↓
isReadyToHide = true
    ↓ delay 400ms (barra → 100%)
    ↓
showLoadingOverlay = false
    ↓ delay 200ms (cross-fade start)
    ↓
┌──────────────────────────────────────────┐
│         CROSS-FADE (500ms)               │
│                                          │
│  LoadingOverlay: 100% → 0% (700ms)       │
│  TrimScreen:     0% → 100% (600ms)       │
│                                          │
│  Sobreposição: 200-700ms                 │
└──────────────────────────────────────────┘
    ↓ 900ms total
    ↓
TrimScreen 100% visível e funcional
```

### Diagrama de Estados

```
┌─────────┐
│  Idle   │
└────┬────┘
     │ startPreload()
     ↓
┌─────────┐     thumbnails > 50%      ┌─────────┐
│ Loading │────────────────────────────>│  Ready  │
└────┬────┘     OR timeout 10s         └─────────┘
     │
     │ error
     ↓
┌─────────┐
│  Error  │
└─────────┘
```

## 📊 Cenários de Uso

| Cenário | Tempo Real | Comportamento |
|---------|------------|---------------|
| **Vídeo em cache** | < 1s | Ready imediato → Overlay desaparece |
| **Vídeo curto** | ~3s | Aguarda 5s mínimo → Overlay desaparece |
| **Vídeo médio** | ~6s | 6s → Overlay desaparece (5s+ condições OK) |
| **Vídeo longo** | ~8s | 8s → Overlay desaparece |
| **Timeout** | 10s | Força exibição da TrimScreen |
| **Erro** | Qualquer | Imediato → Mostra erro na TrimScreen |

## 🎨 Detalhes de Animação

### Entrada do Overlay (quando TrimScreen aparece)
```kotlin
fadeIn(250ms, Emphasized) + scaleIn(0.85 → 1.0, 250ms)
```

### Saída do Overlay (quando pronto)
```kotlin
fadeOut(700ms, FastOutSlowInEasing) + scaleOut(1.0 → 0.97, 700ms)
```

### Entrada da TrimScreen (cross-fade)
```kotlin
fadeIn(600ms, FastOutSlowInEasing) + slideInVertically(5%, 600ms)
```

### Cross-Fade Timing
```
0ms          200ms         500ms        900ms
│             │             │            │
│ Overlay     │  Cross-Fade │   Trim     │
│ 100%        │   (ambos    │   100%     │
│             │   visíveis) │            │
│             │             │            │
└─ Fade Out ──────────────→ │            │
(700ms)       │             │            │
              │             │            │
      delay   └── Fade In ──────────────→│
      200ms       (600ms)                │
```

## 🔍 Logs de Debug

```kotlin
// Início do processo
"TrimViewModel: Iniciando preload (dados não fornecidos)"

// Condições de saída
"LoadingOverlay pronto para esconder: Ready (2s, 100% thumbnails)"
"LoadingOverlay pronto para esconder: Min time (5s) + 65% thumbnails"
"LoadingOverlay pronto para esconder: Timeout (10s)"

// Confirmação
"LoadingOverlay escondido: Ready (2s, 100% thumbnails)"
```

## 💡 Decisões de Design

### Por que 5-10 segundos?
- **Mínimo 5s**: Evita "piscadas" em vídeos muito rápidos
- **Máximo 10s**: Garante que usuário nunca espera muito
- **Otimista 7s**: Progressão natural da barra

### Por que thumbnails > 50%?
- Mínimo para timeline funcional
- Usuário pode começar a editar
- Resto carrega em background (radial loading)

### Por que cross-fade 500ms?
- Tempo suficiente para transição suave
- Não muito lento (tediosa)
- Não muito rápido (brusca)

### Por que barra falsa?
- Feedback visual constante
- Evita "travamentos" perceptíveis
- Completa para 100% antes de fechar

## 🚀 Benefícios

1. **Performance percebida**: Navegação instantânea
2. **UX consistente**: Sempre mostra progresso
3. **Código organizado**: Constantes centralizadas
4. **Manutenibilidade**: Funções auxiliares bem definidas
5. **Flexibilidade**: Fácil ajustar timings e condições

## 📝 Manutenção Futura

### Para ajustar duração:
```kotlin
// LoadingConstants.kt
const val MIN_LOADING_DURATION_MS = 5_000L  // Alterar aqui
const val MAX_LOADING_DURATION_MS = 10_000L // Alterar aqui
```

### Para ajustar progresso mínimo:
```kotlin
// LoadingConstants.kt
const val MINIMUM_THUMBNAIL_PROGRESS = 50f  // Alterar aqui
```

### Para ajustar animações:
```kotlin
// LoadingConstants.kt
const val OVERLAY_FADE_OUT_DURATION_MS = 700  // Fade do overlay
const val TRIM_FADE_IN_DURATION_MS = 600      // Fade da TrimScreen
```

## ⚠️ Pontos de Atenção

1. **Não modificar delays sem ajustar animações** - Cross-fade depende de timing preciso
2. **Mínimo deve ser < máximo** - Validação não implementada
3. **Progress > 50% necessário** - Timeline precisa de dados mínimos
4. **Barra nunca passa de 95%** - Aguarda confirmação real

---

**Última atualização**: 2026-03-01
**Autor**: Claude Code (Sonnet 4.5)
**Status**: ✅ Implementado e testado
