# Backlog V4: Advanced Timeline & Ranges

## Objetivo
Implementar sistema de múltiplos "Ranges" (segmentos de corte/seleção) na Timeline, com manipulação visual direta, lógica de não-sobreposição e polimento visual da régua.

## 1. Visual & Layout (Polimento)
- [ ] **Playhead:** Alterar cor para **Vermelho** (destaque total).
- [ ] **Timeline Background:** Adicionar textura (padrão sutil ou ruído) para que não fique apenas uma cor sólida chapada.
- [ ] **Layout:**
    - Remover paddings laterais/verticais do componente `TimelinePlayer` para "colar" na UI.
    - Diminuir a altura da `BottomBar` (painel de ferramentas) para ganhar área útil.

## 2. Estrutura de Dados (Ranges)
- [ ] Criar modelo de dados `TimelineRange(id, startMs, endMs)`.
- [ ] Adicionar gerenciamento de estado (Lista de Ranges) no `TimelinePlayer` (ou ViewModel associado).

## 3. Comportamento do Botão de Ação (Contextual)
- [ ] **Botão Fixo:** Tamanho fixo, posicionado estrategicamente (ex: canto direito superior da timeline ou floating).
- [ ] **Lógica de Estado:**
    - Se Playhead está **DENTRO** de um range: Botão vira "Remover Range" (Ícone Lixeira/X).
    - Se Playhead está **FORA** de um range: Botão vira "Adicionar Range" (Ícone +).
- [ ] **Lógica de Criação:**
    - Ao clicar em "+", criar um range começando *à direita* do playhead (ex: `start = current + 100ms`).
    - Definir duração padrão (ex: 2 segundos) ou até o próximo range/final do vídeo.
    - Garantir que não sobrescreva (overlap) ranges existentes.

## 4. Renderização dos Ranges (Canvas)
- [ ] Desenhar retângulos sobre a régua (mas sob o Playhead).
- [ ] **Estilo:**
    - Bordas definidas.
    - Alças (Handles) nas extremidades (esquerda/direita) para indicar "agarrável".
- [ ] **Reatividade de Cor:**
    - Cor Normal: Padrão (ex: Azul/Roxo).
    - Cor Ativa: Quando o Playhead passa por cima ("toca"), o range deve ficar **Vermelho** (ou visual de alerta).

## 5. Interação de Toque (Drag & Drop)
- [ ] Implementar lógica de toque complexa no Canvas:
    - Detectar se tocou numa "Alça" -> Redimensionar (Resize).
    - Detectar se tocou no "Corpo" -> (Opcional: Selecionar ou Mover?).
    - Se tocar no fundo -> Scroll (comportamento atual).
- [ ] **Regras de Redimensionamento:**
    - Alça Esquerda: Não pode passar do `start` anterior ou 0.
    - Alça Direita: Não pode passar do `end` do próximo range ou duração total.
