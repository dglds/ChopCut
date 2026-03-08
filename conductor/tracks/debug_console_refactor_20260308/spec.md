# Specification: `debug_console_refactor_20260308`

**Overview**
Refatorar o componente `ConsoleLine` para transformá-lo em uma ferramenta de debug robusta, performática e com uma interface moderna (Material Design 3). O objetivo é substituir a implementação atual, que possui limitações de performance e usabilidade, por uma solução escalável baseada em `LazyColumn` e um sistema de logs desacoplado.

**Requisitos Funcionais**
- **UI/UX (Material 3):**
  - Implementar uma interface limpa usando componentes M3.
  - Diferenciação visual por nível de log (Verbose, Debug, Info, Warn, Error).
  - **Gestos de Navegação:** O console deve aparecer na tela quando o usuário arrastar de baixo para cima (swipe up) e deve ser escondido/minimizado quando o usuário arrastar de cima para baixo (swipe down).
  - Adicionar suporte a um modo "Tela Cheia" ou "Expandido".
- **Performance:**
  - Migrar de `Column` + `verticalScroll` para `LazyColumn` para suportar milhares de entradas sem lag.
  - Implementar uma política de retenção de logs (ex: manter apenas os últimos 1000 logs para evitar consumo excessivo de memória).
- **Recursos de Debug:**
  - **Filtro e Busca:** Campo de busca em tempo real para filtrar logs por mensagem ou tag.
  - **Ações Rápidas:** Botões para Limpar Logs, Copiar para Área de Transferência e Alternar Níveis de Visibilidade.
  - **Auto-Scroll Inteligente:** Otimizar o comportamento de scroll para seguir novos logs apenas se o usuário não estiver explorando logs antigos.
  - **Limpeza Rápida:** Implementar o gesto de **triplo toque (3 taps)** para limpar todos os logs instantaneamente.
- **Arquitetura:**
  - Separar a lógica de captura de logs da UI (Logger centralizado).

**Requisitos Não-Funcionais**
- Performance fluida (60fps) durante o scroll, mesmo sob carga intensa de logs.
- Overhead mínimo no ciclo de vida da aplicação principal.

**Critérios de Aceite**
- O console abre (arrastando para cima) e fecha (arrastando para baixo) suavemente com os gestos definidos.
- O filtro de busca reflete as mudanças instantaneamente na lista.
- Logs com nível "Error" são visualmente distintos e fáceis de identificar.
- A aplicação não apresenta queda de frames ao receber logs em alta frequência.
- Triplo toque na área do console limpa os logs com sucesso.
