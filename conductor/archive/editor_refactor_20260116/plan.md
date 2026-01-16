# Implementation Plan - Editor UI Refactoring

## Phase 1: Foundation & Layout Scaffolding
- [ ] Task: Criar novos componentes de container para as áreas fixa e dinâmica.
- [ ] Task: Refatorar `EditorScreen.kt` para utilizar um Layout de coluna com pesos (`Modifier.weight`).
- [ ] Task: Implementar o estado `ActiveTool` no `EditorViewModel` para controlar o conteúdo da área inferior.

## Phase 2: Tool Components Integration
- [ ] Task: Migrar controles de **Trim** do diálogo para um componente incorporável.
- [ ] Task: Migrar controles de **Filtros** para um componente incorporável.
- [ ] Task: Migrar controles de **Volume/Áudio** para um componente incorporável.
- [ ] Task: Atualizar a `EditorBottomBar` para gerenciar a troca de estados das ferramentas.

## Phase 3: UX Refinement & Polish
- [ ] Task: Adicionar animações de transição ao alternar entre ferramentas.
- [ ] Task: Ajustar o dimensionamento dinâmico para garantir que o `VideoPreview` mantenha o aspect ratio correto.
- [ ] Task: Realizar testes de integração para garantir que as operações de edição continuam sendo aplicadas corretamente.

## Phase 4: Final Verification
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Foundation & Layout Scaffolding' (Protocol in workflow.md)
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Tool Components Integration' (Protocol in workflow.md)
- [ ] Task: Conductor - User Manual Verification 'Phase 3: UX Refinement & Polish' (Protocol in workflow.md)
