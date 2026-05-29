---
name: interrogar-com-docs
description: Sessão de interrogatório que desafia seu plano contra o modelo de domínio existente, afina a terminologia e atualiza a documentação (CONTEXT.md, ADRs) inline conforme as decisões se cristalizam. Use quando quiser testar um plano contra a linguagem e as decisões documentadas do projeto.
---

<what-to-do>

Me interrogue sem descanso sobre cada aspecto deste plano até chegarmos a um entendimento compartilhado. Percorra cada ramificação da árvore de design, resolvendo dependências entre decisões uma a uma. Para cada pergunta, dê a sua resposta recomendada.

Faça as perguntas uma por vez, esperando o feedback de cada uma antes de continuar.

Se uma pergunta pode ser respondida explorando o código, explore o código em vez de perguntar.

</what-to-do>

<supporting-info>

## Conhecimento do domínio

Durante a exploração do código, procure também pela documentação existente:

### Estrutura de arquivos

A maioria dos repositórios tem um único contexto:

```
/
├── CONTEXT.md
├── docs/
│   └── adr/
│       ├── 0001-event-sourced-orders.md
│       └── 0002-postgres-for-write-model.md
└── src/
```

Se existir um `CONTEXT-MAP.md` na raiz, o repositório tem múltiplos contextos. O mapa aponta onde cada um vive:

```
/
├── CONTEXT-MAP.md
├── docs/
│   └── adr/                          ← decisões do sistema como um todo
├── src/
│   ├── ordering/
│   │   ├── CONTEXT.md
│   │   └── docs/adr/                 ← decisões específicas do contexto
│   └── billing/
│       ├── CONTEXT.md
│       └── docs/adr/
```

Crie arquivos sob demanda — somente quando tiver algo para escrever. Se não existir `CONTEXT.md`, crie um quando o primeiro termo for definido. Se não existir `docs/adr/`, crie quando o primeiro ADR for necessário.

## Durante a sessão

### Desafie contra o glossário

Quando o usuário usar um termo que conflita com a linguagem existente no `CONTEXT.md`, aponte imediatamente. "Seu glossário define 'cancelamento' como X, mas você parece querer dizer Y — qual é o correto?"

### Refine a linguagem imprecisa

Quando o usuário usar termos vagos ou sobrecarregados, proponha um termo canônico preciso. "Você está falando 'conta' — você quer dizer o Cliente ou o Usuário? São coisas diferentes."

### Discuta cenários concretos

Quando relações de domínio estiverem sendo discutidas, teste-as com cenários específicos. Invente cenários que explorem edge cases e forcem o usuário a ser preciso sobre os limites entre conceitos.

### Verifique no código

Quando o usuário afirmar como algo funciona, verifique se o código bate com isso. Se encontrar uma contradição, traga à tona: "Seu código cancela Orders inteiras, mas você acabou de dizer que cancelamento parcial é possível — qual está certo?"

### Atualize o CONTEXT.md na hora

Quando um termo for definido, atualize o `CONTEXT.md` na hora. Não acumule essas atualizações — registre conforme acontecem. Use o formato em [CONTEXT-FORMAT.md](./CONTEXT-FORMAT.md).

O `CONTEXT.md` deve ser completamente livre de detalhes de implementação. Não trate o `CONTEXT.md` como spec, rascunho ou repositório de decisões técnicas. É um glossário e nada mais.

### Ofereça ADRs com moderação

Só ofereça criar um ADR quando os três critérios forem verdadeiros:

1. **Difícil de reverter** — o custo de mudar de ideia depois é significativo
2. **Surpreendente sem contexto** — um leitor futuro vai se perguntar "por que fizeram dessa forma?"
3. **Resultado de um trade-off real** — havia alternativas genuínas e você escolheu uma por razões específicas

Se qualquer um dos três estiver ausente, pule o ADR. Use o formato em [ADR-FORMAT.md](./ADR-FORMAT.md).

</supporting-info>
