# Specification - Editor Split UI with Integrated Tool Controls

## Overview
Reorganizar a tela principal do Editor (`EditorScreen`) para implementar um layout de visualização dividida. A metade superior da tela será fixa, exibindo o vídeo e a linha do tempo, enquanto a metade inferior será uma área de controle dinâmica que exibe as ferramentas de edição selecionadas na barra inferior. Isso substitui o comportamento atual de janelas sobrepostas (dialogs/bottom sheets).

## Functional Requirements
- **Layout de Tela Dividida:**
    - **Área Superior (Fixa):** Video Preview Player + Timeline com Waveform (SoundForm) integrada.
    - **Área Inferior (Dinâmica):** Painel para exibir os controles de cada funcionalidade (Trim, Áudio, Filtros, etc.).
- **Area de Controle Dinâmica:**
    - Se nenhuma ferramenta estiver selecionada, exibir um espaço vazio ou placeholder discreto.
    - Ao selecionar uma ferramenta na Bottom Bar, o painel correspondente deve aparecer instantaneamente na Área Inferior.
- **Navegação Persistente:**
    - A Bottom Bar com os botões das funcionalidades deve permanecer sempre visível, permitindo alternar rapidamente entre ferramentas.
- **Fluxo de Confirmação e Undo:**
    - Cada painel de ferramenta deve ter um botão de "Aplicar" ou "Confirmar" para consolidar a edição.
    - **Não haverá botão de "Cancelar" nos painéis.** O usuário pode utilizar o sistema global de Undo/Redo para reverter quaisquer alterações aplicadas indesejadas.
- **Integração de Áudio:**
    - A Waveform (SoundForm) deve ser exibida como uma camada ou trilha integrada à Timeline de vídeo na Área Superior.

## Non-Functional Requirements
- **Responsividade:** O layout deve se ajustar para garantir que o preview do vídeo não seja excessivamente reduzido em telas menores.
- **Performance:** A alternância entre painéis de ferramentas deve ser fluida e sem atrasos perceptíveis no player.

## Acceptance Criteria
- [ ] A tela do editor é visualmente dividida entre preview/timeline (topo) e controles (base).
- [ ] Clicar em "Filtro", "Trim" ou "Áudio" na bottom bar abre o painel correto na base da tela sem abrir janelas flutuantes.
- [ ] O painel de controles possui ação para Aplicar mudanças.
- [ ] Botões de desfazer/refazer (Undo/Redo) funcionam corretamente para reverter ações aplicadas pelos novos painéis.
- [ ] A Waveform é visível na área de timeline fixa no topo.
