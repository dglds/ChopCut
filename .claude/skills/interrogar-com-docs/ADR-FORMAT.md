# Formato de ADR

ADRs ficam em `docs/adr/` e usam numeração sequencial: `0001-slug.md`, `0002-slug.md`, etc.

Crie o diretório `docs/adr/` sob demanda — somente quando o primeiro ADR for necessário.

## Template

```md
# {Título curto da decisão}

{1-3 frases: qual é o contexto, o que decidimos e por quê.}
```

É só isso. Um ADR pode ser um único parágrafo. O valor está em registrar *que* uma decisão foi tomada e *por quê* — não em preencher seções.

## Seções opcionais

Inclua estas somente quando agregarem valor real. A maioria dos ADRs não vai precisar delas.

- **Status** no frontmatter (`proposto | aceito | obsoleto | substituído pelo ADR-NNNN`) — útil quando decisões são revisitadas
- **Opções consideradas** — somente quando as alternativas rejeitadas valem ser lembradas
- **Consequências** — somente quando efeitos downstream não óbvios precisam ser destacados

## Numeração

Verifique `docs/adr/` pelo maior número existente e incremente em um.

## Quando oferecer um ADR

Os três critérios devem ser verdadeiros:

1. **Difícil de reverter** — o custo de mudar de ideia depois é significativo
2. **Surpreendente sem contexto** — um leitor futuro vai olhar o código e se perguntar "por que diabos fizeram assim?"
3. **Resultado de um trade-off real** — havia alternativas genuínas e você escolheu uma por razões específicas

Se uma decisão é fácil de reverter, pule — você simplesmente vai revertê-la. Se não é surpreendente, ninguém vai se perguntar por quê. Se não havia alternativa real, não há nada a registrar além de "fizemos o óbvio".

### O que se qualifica

- **Formato arquitetural.** "Estamos usando um monorepo." "O write model é event-sourced, o read model é projetado no Postgres."
- **Padrões de integração entre contextos.** "Ordering e Billing se comunicam via domain events, não HTTP síncrono."
- **Escolhas tecnológicas com lock-in.** Banco de dados, message bus, provedor de autenticação, destino de deploy. Não toda biblioteca — só as que levariam um trimestre para trocar.
- **Decisões de fronteira e escopo.** "Dados do cliente pertencem ao contexto Customer; outros contextos referenciam apenas pelo ID." Os nãos explícitos são tão valiosos quanto os sins.
- **Desvios deliberados do caminho óbvio.** "Estamos usando SQL direto em vez de ORM por causa de X." Qualquer coisa onde um leitor razoável assumiria o oposto. Isso impede o próximo desenvolvedor de "corrigir" algo que foi intencional.
- **Restrições não visíveis no código.** "Não podemos usar AWS por requisitos de compliance." "Tempos de resposta devem ser abaixo de 200ms por causa do contrato com a API parceira."
- **Alternativas rejeitadas quando a rejeição não é óbvia.** Se você considerou GraphQL e escolheu REST por razões sutis, registre — caso contrário alguém vai sugerir GraphQL de novo em seis meses.
