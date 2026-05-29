# ChopCut — Protocolo de Sessões

Guia de início e finalização de sessões para IA e devs. **Princípio: fonte única.** Cada fato vive em UM lugar; este protocolo aponta para ele em vez de repetir — duplicação é o que gera docs defasados.

| Preciso de… | Fonte única |
|---|---|
| Regras de arquitetura (package único, sem novos arquivos, sem nomes duplicados) | [docs/ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md) |
| Inventário vivo (arquivos/tipos/funções) | `docs/STRUCTURE.generated.md` (auto-gerado — **conferir, não editar**) |
| Estado atual: backlog, known-issues, decisões recentes | [STATE.md](file:///home/diego/Android/ChopCut/STATE.md) |
| Estrutura do código (símbolos, chamadas, impacto) | **CodeGraph** (`codegraph_*`) |
| Decisões/gotchas duráveis entre sessões | Memory (`memory/MEMORY.md`, carrega sozinha) |
| Padrões de performance de Canvas | `CLAUDE.md` + skill `/revisar-canvas` |

---

## 🚀 1. Início de Sessão

1. **Leia o [STATE.md](file:///home/diego/Android/ChopCut/STATE.md)** — é o único arquivo que dá o estado atual (backlog + known-issues + últimas decisões). Não reconstrua isso lendo notas de sessão antigas.
2. **Confira o estado da estrutura, não reconte:** se precisar saber quantos/quais arquivos existem, abra `docs/STRUCTURE.generated.md`. Para qualquer pergunta sobre *onde mora* um símbolo, pergunte ao **CodeGraph**.
3. **Compile só se for mexer em código** (não é ritual): `JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin`.

> Setup único por clone (ativa o hook que mantém a estrutura sincronizada): `git config core.hooksPath .githooks`

## 🛠️ 2. Durante a Sessão

As regras (package único, sem novos arquivos, sem nomes duplicados, commits modulares por escopo) estão nas **Regras da Arquitetura** — siga-as de lá. Apoios:

- **Antes de implementar algo não trivial:** `/desafiar-plano` estressa o plano contra as regras.
- **Ao mexer em Canvas/timeline:** `/revisar-canvas` audita os 3 padrões de performance.
- **Para validar UI de verdade:** `/rodar-app` (sobe o app; jank não é coberto por teste).
- **Antes de renomear/remover símbolo:** `codegraph_impact`/`codegraph_callers` para cobertura total.

## 🏁 3. Finalização de Sessão

1. **Valide o build:** `JAVA_HOME=./jdk17 ./gradlew assembleDebug`
2. **Atualize o [STATE.md](file:///home/diego/Android/ChopCut/STATE.md):** backlog (feito/novo), known-issues, e decisões da sessão. Este é o handoff para a próxima IA.
3. **Decisão não-óbvia? Salve na Memory** (`memory/`) com o porquê — não a enterre só na nota de sessão.
4. **Mudou o *propósito* de um arquivo, navegação ou um pipeline central?** Atualize as **Regras da Arquitetura**. (A contagem/inventário NÃO precisa de update manual — o hook regenera `STRUCTURE.generated.md`.)
5. **Crie a nota da sessão** `sessions/session#NN.md` (próximo sequencial) — histórico append-only enxuto, usando o template abaixo.
6. **Commit modular por escopo.** O hook de pré-commit regenera e adiciona `STRUCTURE.generated.md` sozinho.

---

## 📄 Template — `sessions/session#NN.md`

Histórico narrativo enxuto. O estado *vivo* mora no STATE.md, não aqui — não duplique o backlog inteiro.

```markdown
# Session #NN — [Título breve]

**Modelo:** [IA usada]  **Data:** [AAAA-MM-DD]
**Objetivo:** [o que foi atacado]

## O que foi feito
- [mudança] → [arquivo(s)]

## Decisões (e por quê)
- [decisão não-óbvia + razão] — salva na Memory como [[slug]] se for durável

## Delta do backlog
- Fechado: [...]
- Novo: [...] (refletido no STATE.md)
```
