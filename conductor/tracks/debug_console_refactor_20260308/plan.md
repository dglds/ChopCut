# Implementation Plan: `debug_console_refactor_20260308`

**Phase 1: Setup and Centralized Logger**
- [x] Task: Criar testes unitários para o Logger centralizado (verificando retenção máxima de 1000 logs e níveis de log). [39994f7]
- [x] Task: Implementar o Logger centralizado desacoplado da UI. [39994f7]
- [x] Task: Refatorar o `ConsoleLineViewModel` para utilizar o novo Logger. [39994f7]
- [ ] Task: Conductor - User Manual Verification 'Setup and Centralized Logger' (Protocol in workflow.md)

**Phase 2: UI Transformation & Performance (Material 3 + LazyColumn)**
- [x] Task: Escrever testes para os novos estados de navegação, gestos e a ação de limpar logs por triplo toque no `ConsoleLineViewModel`. [0c1b2d6]
- [x] Task: Substituir a estrutura atual de `Column` + `verticalScroll` por `LazyColumn` no composable `ConsoleLine`. [0c1b2d6]
- [x] Task: Estilizar as linhas de log utilizando Material Design 3, com diferenciação visual para os níveis de log (ex: Error destacado). [0c1b2d6]
- [x] Task: Implementar os gestos de navegação: arrastar para cima (exibir/expandir) e arrastar para baixo (esconder/minimizar). [0c1b2d6]
- [x] Task: Implementar detector de gestos para **triplo toque (3 taps)** que aciona a limpeza imediata dos logs. [0c1b2d6]
- [ ] Task: Conductor - User Manual Verification 'UI Transformation & Performance' (Protocol in workflow.md)

**Phase 3: Debug Features & Polish**
- [x] Task: Implementar campo de texto para busca e filtragem em tempo real na UI e no ViewModel. [33d270e]
- [x] Task: Adicionar botões de Ações Rápidas (Limpar logs, Copiar para área de transferência, Alternar visibilidade de níveis). [33d270e]
- [x] Task: Implementar a lógica de Smart Auto-Scroll na `LazyColumn`. [33d270e]
- [ ] Task: Conductor - User Manual Verification 'Debug Features & Polish' (Protocol in workflow.md)
