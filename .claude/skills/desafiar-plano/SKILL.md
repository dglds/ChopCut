---
name: desafiar-plano
description: Sessão de interrogatório que estressa um plano de implementação contra as regras reais do ChopCut — estrutura de 16 arquivos, package único, nomes sem duplicata, padrões de performance de Canvas e o backlog da última sessão — antes de escrever código. Afina a terminologia para os nomes canônicos do projeto e atualiza a documentação (sessions/session#NN-objetivo-da-session.md, Regras da Arquitetura) conforme as decisões se firmam. Use ao testar um plano antes de implementar.
---

<what-to-do>

Me interrogue sem descanso sobre cada aspecto deste plano até chegarmos a um entendimento compartilhado e compatível com as regras do ChopCut. Percorra cada ramificação da árvore de design, resolvendo dependências entre decisões uma a uma. Para cada pergunta, dê a sua resposta recomendada.

Faça as perguntas uma por vez, esperando o feedback de cada uma antes de continuar.

Se uma pergunta pode ser respondida explorando o código, explore o código em vez de perguntar — use o **CodeGraph** (`codegraph_search`, `codegraph_context`, `codegraph_impact`) antes de grep/read, já que ele é o índice semântico do projeto.

</what-to-do>

<supporting-info>

## Fonte da verdade do projeto

Antes e durante o interrogatório, ancore o plano nestes documentos reais (o ChopCut **não usa** `CONTEXT.md` nem ADRs — não os crie):

- **[docs/ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md)** — a estrutura de 16 arquivos, package único, mapa de "onde adicionar cada coisa", conflitos de nome conhecidos.
- **`CLAUDE.md` → "Padrões críticos de performance"** — os 3 padrões de Canvas.
- **Última `sessions/session#NN-objetivo-da-session.md`** — o que foi feito por último e o backlog de pendências. Leia a de maior número antes de desafiar o plano; o plano pode colidir com algo já decidido ou já listado.
- **[SESSION_PROTOCOL.md](file:///home/diego/Android/ChopCut/SESSION_PROTOCOL.md)** — protocolo de início/execução/finalização.

## Os eixos do interrogatório

Desafie o plano contra cada um destes. Quando o plano violar um, **aponte na hora** com a regra concreta e a recomendação.

### 1. Estrutura de 16 arquivos — "onde isso mora?"

A regra é inegociável: **não se cria novo arquivo `.kt`**. Para qualquer peça nova no plano (modelo, util, componente, tela, pipeline), pergunte em qual dos 16 arquivos ela entra, usando o mapa das Regras da Arquitetura:

| Se for… | Mora em… |
|---------|----------|
| Modelo de dados | `core/Models.kt` |
| Função utilitária | `core/Utils.kt` |
| Componente UI reutilizável | `ui/SharedComponents.kt` |
| Tela/ViewModel do editor | `ui/editor/TimelineFeature.kt` |
| Tela inicial | `ui/home/HomeFeature.kt` |

Se o plano *exige* um arquivo novo, isso é uma mudança de arquitetura: force a justificativa e trate como decisão de peso (ver "Atualize os docs").

### 2. Package único & sem nomes duplicados

Tudo é `package com.chopcut`, sem subpacotes e **sem imports entre arquivos internos**. Como todos se enxergam, **não pode haver dois símbolos (classe/objeto/enum) com o mesmo nome**. Quando o plano introduzir um nome novo, verifique colisão pelo CodeGraph antes de aceitar: "Você quer criar `ExportState` — `codegraph_search` mostra que já existe em `core/Models.kt`. Reusar ou renomear?"

### 3. Padrões de performance de Canvas

Se o plano toca em `Canvas`/`drawBehind`/`DrawScope` (timeline, playhead, thumbnails, waveform), confronte-o com os 3 padrões: zero alocação no draw scope, flag `isScrubbing` para gesto vs. estado observado, isolamento de animação. Para uma auditoria completa do código resultante, encaminhe para a skill **`/revisar-canvas`**.

### 4. Afinе a terminologia para os nomes canônicos

Quando o usuário usar um termo vago ou divergente, fixe-o no nome que já existe no código. Os modelos canônicos vivem em `core/Models.kt` (`VideoInfo`, `VideoRange`, `TimeRange`, etc.) e os utilitários em `core/Utils.kt`. "Você falou 'corte' — é um `VideoRange` (intervalo a manter) ou um `TimeRange` genérico? São coisas diferentes no projeto." Não deixe o plano inventar um sinônimo para algo que já tem nome.

### 5. Cheque no código

Quando o usuário afirmar como algo funciona, verifique com CodeGraph se o código bate. Se houver contradição, traga à tona: "Você disse que o playhead lê a posição do ExoPlayer direto, mas `codegraph_context` mostra `isScrubbing` mediando isso — qual está certo?"

### 6. Análise de impacto antes de refatorar

Se o plano renomeia/remove um símbolo ou pipeline, rode `codegraph_impact`/`codegraph_callers` para cobertura total de referências antes de aceitar o escopo. Refatoração às cegas é proibida pelo SESSION_PROTOCOL.

## Atualize os docs na hora

Conforme as decisões se firmam, registre — não acumule:

- **Decisão de implementação, pendência ou escopo fechado** → anote na `sessions/session#NN-objetivo-da-session.md` da sessão corrente (backlog/decisões), seguindo o SESSION_PROTOCOL.
- **Mudança de estrutura de arquivos, navegação, ViewModels ou um novo conflito de nome** → atualize imediatamente a **Regras da Arquitetura.md** (a própria regra exige isso na finalização de sessão).

## Sinalize mudanças de arquitetura com moderação

Trate como decisão de peso (que merece justificativa explícita e registro nas Regras da Arquitetura) só quando os três forem verdadeiros:

1. **Difícil de reverter** — mexe na estrutura de 16 arquivos, no package único ou em um pipeline central (`VideoEngine`, `ThumbnailExtraction`).
2. **Surpreendente sem contexto** — um futuro leitor vai se perguntar "por que abriram exceção à regra aqui?".
3. **Resultado de um trade-off real** — havia alternativa dentro das regras e ela foi descartada por um motivo específico.

Se algum dos três faltar, não infle: encaixe nas regras existentes e siga.

</supporting-info>
