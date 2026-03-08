# Implementation Plan: `debug_console_refactor_20260308`

**Phase 1: Setup and Centralized Logger**
- [x] Task: Criar testes unitários para o Logger centralizado (verificando retenção máxima de 1000 logs e níveis de log). [39994f7]
- [x] Task: Implementar o Logger centralizado desacoplado da UI. [39994f7]
- [x] Task: Refatorar o `ConsoleLineViewModel` para utilizar o novo Logger. [39994f7]
- [ ] Task: Conductor - User Manual Verification 'Setup and Centralized Logger' (Protocol in workflow.md)

**Phase 2: UI Transformation & Performance (Material 3 + LazyColumn)**
- [ ] Task: Escrever testes para os novos estados de navegação, gestos e a ação de limpar logs por triplo toque no `ConsoleLineViewModel`.
- [ ] Task: Substituir a estrutura atual de `Column` + `verticalScroll` por `LazyColumn` no composable `ConsoleLine`.
- [ ] Task: Estilizar as linhas de log utilizando Material Design 3, com diferenciação visual para os níveis de log (ex: Error destacado).
- [ ] Task: Implementar os gestos de navegação: arrastar para cima (exibir/expandir) e arrastar para baixo (esconder/minimizar).
- [ ] Task: Implementar detector de gestos para **triplo toque (3 taps)** que aciona a limpeza imediata dos logs.
- [ ] Task: Conductor - User Manual Verification 'UI Transformation & Performance' (Protocol in workflow.md)

**Phase 3: Debug Features & Polish**
- [ ] Task: Implementar campo de texto para busca e filtragem em tempo real na UI e no ViewModel.
- [ ] Task: Adicionar botões de Ações Rápidas (Limpar logs, Copiar para área de transferência, Alternar visibilidade de níveis).
- [ ] Task: Implementar a lógica de Smart Auto-Scroll na `LazyColumn`.
- [ ] Task: Conductor - User Manual Verification 'Debug Features & Polish' (Protocol in workflow.md)
