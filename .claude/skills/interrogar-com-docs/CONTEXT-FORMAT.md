# Formato do CONTEXT.md

## Estrutura

```md
# {Nome do Contexto}

{Uma ou duas frases descrevendo o que é este contexto e por que existe.}

## Linguagem

**Order**:
{Uma ou duas frases descrevendo o termo}
_Evitar_: Purchase, transaction

**Invoice**:
Uma solicitação de pagamento enviada ao cliente após a entrega.
_Evitar_: Bill, payment request

**Customer**:
Uma pessoa ou organização que realiza pedidos.
_Evitar_: Client, buyer, account
```

## Regras

- **Seja opinativo.** Quando existirem múltiplas palavras para o mesmo conceito, escolha a melhor e liste as outras como aliases a evitar.
- **Sinalize conflitos explicitamente.** Se um termo é usado de forma ambígua, destaque-o em "Ambiguidades sinalizadas" com uma resolução clara.
- **Mantenha as definições concisas.** Uma ou duas frases no máximo. Defina o que É, não o que faz.
- **Mostre relacionamentos.** Use nomes de termos em negrito e expresse cardinalidade quando óbvio.
- **Inclua apenas termos específicos ao contexto deste projeto.** Conceitos gerais de programação (timeouts, tipos de erro, padrões utilitários) não pertencem aqui mesmo que o projeto os use muito. Antes de adicionar um termo, pergunte: este conceito é único a este contexto, ou é um conceito geral de programação? Apenas o primeiro pertence.
- **Agrupe termos em subtítulos** quando clusters naturais emergirem. Se todos os termos pertencem a uma área coesa única, uma lista plana é adequada.
- **Escreva um exemplo de diálogo.** Uma conversa entre um dev e um domain expert que demonstra como os termos interagem naturalmente e esclarece os limites entre conceitos relacionados.

## Repositório de contexto único vs múltiplos contextos

**Contexto único (maioria dos repos):** Um `CONTEXT.md` na raiz do repositório.

**Múltiplos contextos:** Um `CONTEXT-MAP.md` na raiz lista os contextos, onde vivem e como se relacionam entre si:

```md
# Context Map

## Contextos

- [Ordering](./src/ordering/CONTEXT.md) — recebe e acompanha pedidos de clientes
- [Billing](./src/billing/CONTEXT.md) — gera invoices e processa pagamentos
- [Fulfillment](./src/fulfillment/CONTEXT.md) — gerencia separação e envio no depósito

## Relacionamentos

- **Ordering → Fulfillment**: Ordering emite eventos `OrderPlaced`; Fulfillment os consome para iniciar a separação
- **Fulfillment → Billing**: Fulfillment emite eventos `ShipmentDispatched`; Billing os consome para gerar invoices
- **Ordering ↔ Billing**: Tipos compartilhados para `CustomerId` e `Money`
```

A skill infere qual estrutura se aplica:

- Se `CONTEXT-MAP.md` existe, leia-o para encontrar os contextos
- Se apenas um `CONTEXT.md` raiz existe, contexto único
- Se nenhum dos dois existe, crie um `CONTEXT.md` raiz sob demanda quando o primeiro termo for definido

Quando múltiplos contextos existirem, infira a qual deles o tópico atual se relaciona. Se não estiver claro, pergunte.
