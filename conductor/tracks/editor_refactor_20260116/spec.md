# Specification - Editor UI Refactoring

## Overview
Reorganizar a estrutura visual da tela `EditorScreen` para implementar um layout de visualização dividida. O objetivo é manter os elementos de visualização (Vídeo, Timeline, Waveform) fixos no topo, enquanto a parte inferior da tela é dedicada exclusivamente aos controles da ferramenta ativa selecionada na Bottom Bar.

## Functional Requirements
- **Layout Dividido:** A tela deve ser dividida em duas áreas principais:
    - **Área 1 (Topo - Fixa):** Video Preview, Timeline e Waveform.
    - **Área 2 (Base - Dinâmica):** Painel de controle da ferramenta ativa.
- **Bottom Bar Integrada:** Os botões da Bottom Bar (Trim, Filtro, Velocidade, Volume) devem alternar o conteúdo da Área 2 em vez de abrir diálogos ou bottom sheets sobrepostos.
- **Ferramentas Dinâmicas:**
    - Ao selecionar **Trim**, mostrar controles de corte na Área 2.
    - Ao selecionar **Filtro**, mostrar seletores de filtro na Área 2.
    - Ao selecionar **Áudio/Volume**, mostrar controles de áudio na Área 2.
- **Persistência de Estado:** A Área 1 deve continuar exibindo o progresso e estado do vídeo independentemente da ferramenta aberta na Área 2.

## Non-Functional Requirements
- **Responsividade:** O layout deve se ajustar a diferentes tamanhos de tela, garantindo que a Área 1 tenha espaço suficiente para o vídeo.
- **Transições Suaves:** Alternar entre ferramentas na Bottom Bar deve ser visualmente fluido.

## Acceptance Criteria
- [ ] O layout exibe Video, Timeline e Waveform na parte superior.
- [ ] Clicar em uma ferramenta na Bottom Bar abre seus controles na parte inferior da tela, sem sobrepor o vídeo.
- [ ] É possível fechar a ferramenta ativa para retornar ao estado padrão (apenas Bottom Bar).
- [ ] Todas as funcionalidades existentes (Trim, Filtros, Volume) continuam operacionais no novo layout.
