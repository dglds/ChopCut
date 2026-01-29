# Backlog V5: Range Editing Modes & Safety

## Objetivo
Implementar um sistema de seleção explícito para os Ranges, onde apenas o range "selecionado" possui alças editáveis. Ranges "salvos" tornam-se fixos para prevenir alterações acidentais. Aumentar a ergonomia das alças.

## 1. Estado de Seleção
- [ ] Adicionar campo `isSelected` (ou gerenciar um `selectedRangeId` no estado pai).
- [ ] **Comportamento:**
    - **Criação:** Range novo nasce `SELECIONADO`.
    - **Toque no Range:** Seleciona aquele range (e deseleciona outros).
    - **Toque Fora (Vazio/Scroll):** Deseleciona todos (confirma/salva).
    - **Visual:** Ranges não-selecionados perdem as alças (Handles) e bordas de destaque. Apenas o retângulo colorido permanece.

## 2. Lógica de Deleção Refinada
- [ ] Botão de Lixeira só aparece/ativa se houver um range **SELECIONADO** E o playhead estiver sobre ele (ou apenas se estiver selecionado, independente do playhead, para simplificar).
- [ ] *Decisão de Design:* Vamos manter a dependência do Playhead para consistência com o botão "+", ou a seleção basta?
    - *Proposta:* Se tem Range Selecionado -> Botão vira Lixeira (apaga o selecionado). Se não tem -> Botão vira "+" (cria novo).

## 3. Ergonomia das Alças (Handles)
- [ ] **Visual:** Adicionar um elemento gráfico maior nas pontas das alças (ex: Círculo ou Pílula 8x20dp) para indicar onde tocar.
- [ ] **Hitbox:** Aumentar a área de detecção de toque das alças (ex: 40dp de largura invisível).

## 4. Prevenção de Sobreposição (Overlap)
- [ ] Reforçar a lógica de `Resize`: Ao arrastar uma alça, ela deve travar rigidamente (hard stop) ao encostar no início/fim de um range vizinho.

## 5. Visual "Salvo" vs "Editando"
- [ ] **Editando:** Cor translúcida, Borda Forte, Alças Visíveis.
- [ ] **Salvo:** Cor Sólida (ou translúcida sem borda), Sem Alças.
