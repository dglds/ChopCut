# Análise de Código: SharedComponents.kt

## Visão Geral
O arquivo `SharedComponents.kt` serve como o *Design System Components* do aplicativo ChopCut. Qualquer componente de interface que precise ser reutilizado em mais de uma tela (Feature) habita aqui, evitando duplicação de código Compose.

## Responsabilidades
- **Componentes Atômicos**: Botões customizados (`ChopCutButton`, `ChopCutFab`), badges e labels de interface (`DurationLabel`).
- **Layouts Globais**: Templates padrão como overlay de loading (`LoadingOverlay`), efeitos de Shimmer e animações unificadas para dar feedback de espera ao usuário.
- **Componentes Compostos**: Elementos de UI mais robustos que se repetem, como `VideoCard` para listas, e `BottomSheetGallery` para buscar arquivos.

## Regras Arquiteturais
- **Burrice (Dumb Components)**: Os componentes aqui devem ser *stateless* (sem estado gerido internamente) na medida do possível. Eles devem receber seus dados, cores e callbacks como argumentos (via props do Compose) ao invés de injetar ViewModels aqui. 
- Apenas componentes que são agnósticos ao "estado do app" devem morar aqui. Se um componente precisa, por exemplo, consultar especificamente a ViewModel do Editor, ele deve ser movido para o `EditorFeature.kt`.

## Quando alterar este arquivo?
- Ao construir ou modificar componentes base usados no ecossistema global do app (como um novo design para o Botão Principal ou Loader de progresso).
