# Plano de Implementação: Atomic Design no ChopCut

## 📋 Visão Geral

Este documento define a estratégia de migração para o **Atomic Design** no projeto ChopCut, organizando os componentes Jetpack Compose de forma escalável e manutenível.

---

## 🧬 O que é Atomic Design?

Atomic Design (Brad Frost) organiza UI em 5 níveis hierárquicos:

```
┌─────────────────────────────────────────────────────────┐
│  🏗️ PÁGINAS (Pages)           → Instâncias específicas  │
│     └─ HomeScreen, EditorScreen, ExportResultScreen    │
├─────────────────────────────────────────────────────────┤
│  🧩 TEMPLATES (Templates)     → Layouts de página      │
│     └─ EditorLayout, SettingsLayout                    │
├─────────────────────────────────────────────────────────┤
│  🧬 ORGANISMOS (Organisms)    → Componentes complexos  │
│     └─ VideoPlayer, Timeline, ToolPanel               │
├─────────────────────────────────────────────────────────┤
│  ⚛️ MOLÉCULAS (Molecules)     → Grupos de átomos       │
│     └─ VideoCard, FeatureItem, InfoRow                │
├─────────────────────────────────────────────────────────┤
│  🔬 ÁTOMOS (Atoms)            → Elementos básicos      │
│     └─ PrimaryButton, HeadlineText, SurfaceCard       │
└─────────────────────────────────────────────────────────┘
```

---

## 📁 Estrutura de Pastas Proposta

```
app/src/main/java/com/chopcut/
├── core/
│   └── designsystem/              # 🎨 Design System (Atomic Design)
│       ├── atoms/                 # Elementos mais básicos
│       │   ├── PrimaryButton.kt
│       │   ├── SecondaryButton.kt
│       │   ├── GhostButton.kt
│       │   ├── HeadlineText.kt
│       │   ├── BodyText.kt
│       │   ├── LabelText.kt
│       │   ├── SurfaceCard.kt
│       │   ├── OutlinedCard.kt
│       │   ├── DividerLine.kt
│       │   └── IconBox.kt
│       │
│       ├── molecules/             # Combinações de átomos
│       │   ├── VideoInfoCard.kt   (VideoInfoPreview atual)
│       │   ├── FeatureRow.kt      (FeatureItem atual)
│       │   ├── InfoRow.kt         (InfoRowPreview atual)
│       │   ├── ActionItem.kt
│       │   ├── StatItem.kt
│       │   └── ListTile.kt
│       │
│       ├── organisms/             # Componentes complexos
│       │   ├── AppTopBar.kt
│       │   ├── VideoSelector.kt
│       │   ├── EmptyState.kt
│       │   ├── ErrorState.kt
│       │   ├── LoadingState.kt
│       │   └── FeatureList.kt
│       │
│       ├── templates/             # Layouts reutilizáveis
│       │   ├── ScreenTemplate.kt
│       │   ├── SplitScreenTemplate.kt
│       │   └── BottomSheetTemplate.kt
│       │
│       ├── theme/                 # Tema do app (mover de ui/theme)
│       │   ├── Color.kt
│       │   ├── Theme.kt
│       │   ├── Type.kt
│       │   └── Shape.kt
│       │
│       └── tokens/                # 🔧 Design Tokens
│           ├── ColorTokens.kt
│           ├── TypographyTokens.kt
│           ├── SpacingTokens.kt
│           └── SizeTokens.kt
│
├── feature/                       # 🎯 Features do app
│   ├── home/
│   │   ├── ui/
│   │   │   └── HomeScreen.kt      # 🏗️ Página
│       └── ...
│   ├── editor/
│   │   ├── ui/
│   │   │   └── EditorScreen.kt    # 🏗️ Página
│   │   └── ...
│   ├── settings/
│   │   └── ...
│   └── export/
│       └── ...
│
└── MainActivity.kt
```

---

## 🎯 Mapeamento dos Componentes Atuais

### Componentes Existentes → Nova Estrutura

| Componente Atual | Localização Atual | Nova Localização | Categoria |
|------------------|-------------------|------------------|-----------|
| `WaveForm` | `ui/components/` | `core/designsystem/organisms/` | Organismo |
| `Timeline` | `ui/timeline/` | `feature/editor/ui/components/` | Feature-specific |
| `EditorSplitLayout` | `ui/components/` | `feature/editor/ui/components/` | Feature-specific |
| `FeatureItem` | `ui/screen/HomeScreen.kt` | `core/designsystem/molecules/FeatureRow.kt` | Molécula |
| `VideoInfoPreview` | `ui/screen/HomeScreen.kt` | `core/designsystem/molecules/VideoInfoCard.kt` | Molécula |
| `InfoRowPreview` | `ui/screen/HomeScreen.kt` | `core/designsystem/molecules/InfoRow.kt` | Molécula |
| TopAppBar | `ui/screen/HomeScreen.kt` | `core/designsystem/organisms/AppTopBar.kt` | Organismo |
| Cards | `ui/screen/HomeScreen.kt` | `core/designsystem/atoms/SurfaceCard.kt` | Átomo |
| Botões | `ui/screen/HomeScreen.kt` | `core/designsystem/atoms/PrimaryButton.kt` | Átomo |

---

## 🔧 Design Tokens

Os tokens são valores fundamentais do design system:

```kotlin
// core/designsystem/tokens/ColorTokens.kt
object ColorTokens {
    // Brand
    val brandPrimary = Color(0xFF6650a4)
    val brandSecondary = Color(0xFF625b71)
    
    // Semantic
    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFF9800)
    val error = Color(0xFFE53935)
    val info = Color(0xFF2196F3)
    
    // Neutral
    val surface = Color(0xFFFFFBFE)
    val onSurface = Color(0xFF1C1B1F)
}

// core/designsystem/tokens/SpacingTokens.kt
object SpacingTokens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

// core/designsystem/tokens/TypographyTokens.kt
object TypographyTokens {
    val headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    )
    // ... outros estilos
}
```

---

## 🚀 Plano de Migração

### Fase 1: Fundação (Tokens + Átomos)
**Objetivo:** Criar a base do design system

- [ ] Criar pacote `core.designsystem`
- [ ] Mover tema atual para `core.designsystem.theme`
- [ ] Criar Design Tokens (cores, espaçamento, tipografia)
- [ ] Criar átomos básicos:
  - `PrimaryButton`
  - `SecondaryButton`
  - `HeadlineText`
  - `BodyText`
  - `SurfaceCard`

### Fase 2: Moléculas
**Objetivo:** Agrupar átomos em componentes reutilizáveis

- [ ] `FeatureRow` (ícone + textos)
- [ ] `InfoRow` (label + valor)
- [ ] `VideoInfoCard` (card com informações do vídeo)
- [ ] `ActionItem` (ícone + label + ação)

### Fase 3: Organismos
**Objetivo:** Criar componentes complexos

- [ ] `AppTopBar` (título + ações)
- [ ] `VideoSelector` (botão + preview)
- [ ] Estados: `EmptyState`, `ErrorState`, `LoadingState`
- [ ] `FeatureList` (lista de recursos)

### Fase 4: Templates e Páginas
**Objetivo:** Refatorar telas para usar o design system

- [ ] `ScreenTemplate` (estrutura comum)
- [ ] Refatorar `HomeScreen`
- [ ] Refatorar `EditorScreen`
- [ ] Refatorar `SettingsScreen`

### Fase 5: Documentação e Cleanup
**Objetivo:** Finalizar e documentar

- [ ] Criar `@Preview` para todos os componentes
- [ ] Documentar uso dos componentes
- [ ] Remover componentes antigos
- [ ] Atualizar CLAUDE.md

---

## 📐 Convenções de Nomenclatura

### Componentes
```kotlin
// Átomos: nomes descritivos sem prefixo
@Composable
fun PrimaryButton(...)           // ✅
fun SecondaryButton(...)         // ✅
fun HeadlineText(...)            // ✅
fun BodyText(...)                // ✅
fun SurfaceCard(...)             // ✅

// Evite nomes genéricos demais que conflitem com Material3
fun Button(...)                  // ❌ conflito com Material3
fun Text(...)                    // ❌ conflito com Material3
fun Card(...)                    // ❌ conflito com Material3

// Moléculas: descrição do componente
@Composable
fun FeatureRow(...)              // ✅
fun InfoRow(...)                 // ✅
fun VideoInfoCard(...)           // ✅

// Organismos: nome descritivo
@Composable
fun VideoSelector(...)           // ✅
fun AppTopBar(...)               // ✅
fun EmptyState(...)              // ✅
```

### Arquivos
```
PrimaryButton.kt      → Um componente por arquivo
FeatureRow.kt         → Nome do componente = nome do arquivo
```

### Modificadores
```kotlin
// Sempre aceitar Modifier como parâmetro primeiro
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,  // ✅ padrão vazio
    // ... outros parâmetros
)

// Aplicar modifier primeiro na hierarquia
Box(modifier = modifier.fillMaxWidth())  // ✅
```

---

## 🧪 Previews

Cada componente deve ter previews:

```kotlin
@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    ChopCutTheme {
        PrimaryButton(
            onClick = {},
            text = "Primary Button"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SecondaryButtonPreview() {
    ChopCutTheme {
        SecondaryButton(
            onClick = {},
            text = "Secondary Button"
        )
    }
}
```

---

## 📦 Dependências

O design system deve ser autocontido. Dependências permitidas:
- `androidx.compose.material3` (Material 3)
- `androidx.compose.ui` (UI core)
- `androidx.compose.runtime` (Runtime)

**NÃO** deve depender de:
- ViewModels
- Navegação
- Data layer
- Feature-specific code

---

## ✅ Checklist de Qualidade

Antes de mergear qualquer componente:

- [ ] Componente tem `@Preview`
- [ ] Aceita `Modifier` como parâmetro
- [ ] Não depende de estados externos (stateless quando possível)
- [ ] Segue o tema do app (cores, tipografia)
- [ ] Documentação KDoc adicionada
- [ ] Nome segue convenções (sem prefixo, descritivo)
- [ ] Arquivo está na pasta correta

---

## 📚 Recursos

- [Atomic Design by Brad Frost](https://atomicdesign.bradfrost.com/)
- [Material Design 3](https://m3.material.io/)
- [Compose Component Guidelines](https://developer.android.com/jetpack/compose/guidelines)
