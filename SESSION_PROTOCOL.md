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
| Comandos de build / install / testes | Atalhos do `Makefile` (`make build`/`install`/`test`) ou `JAVA_HOME=./jdk17 ./gradlew …` — ver [.claude/CLAUDE.md](file:///home/diego/Android/ChopCut/.claude/CLAUDE.md). Flags de perf em `gradle.properties`. |

---

## 🚀 1. Início de Sessão

Para iniciar uma nova sessão, você pode executar o hook/skill **/start-session**. Este ritual garante o alinhamento correto:

1. **Leia o [STATE.md](file:///home/diego/Android/ChopCut/STATE.md)** — é o único arquivo que dá o estado atual (backlog + known-issues + últimas decisões). Não reconstrua isso lendo notas de sessão antigas.
2. **Confira o estado da estrutura, não reconte:** se precisar saber quantos/quais arquivos existem, abra `docs/STRUCTURE.generated.md`. Para qualquer pergunta sobre *onde mora* um símbolo, pergunte ao **CodeGraph**.
3. **Compile só se for mexer em código** (não é ritual): `make compile` (ou `JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin`).

> Setup único por clone (ativa o hook que mantém a estrutura sincronizada): `git config core.hooksPath .githooks`

## 🛠️ 2. Durante a Sessão

As regras (package único, sem novos arquivos, sem nomes duplicados, commits modulares por escopo) estão nas **Regras da Arquitetura** — siga-as de lá. Apoios:

- **Antes de implementar algo não trivial:** `/desafiar-plano` estressa o plano contra as regras.
- **Ao mexer em Canvas/timeline:** `/revisar-canvas` audita os 3 padrões de performance.
- **Para validar UI de verdade:** `/rodar-app` (sobe o app; jank não é coberto por teste).
- **Antes de renomear/remover símbolo:** `codegraph_impact`/`codegraph_callers` para cobertura total.

## 🏁 3. Finalização de Sessão

Ao encerrar, você deve executar o hook/skill **/finish-session** para automatizar a geração do handoff. A ordem conceitual é **Memory-first**: a lição que evita o próximo erro é capturada *antes* do changelog.

1. **Valide o build:** `make build` (ou `JAVA_HOME=./jdk17 ./gradlew assembleDebug`).
2. **Capture a lição (Memory-first).** Toda regra do tipo *"não faça X porque Y"* ou decisão não-óbvia vai para a **Memory** (`memory/`, com o **porquê**) ou, se for regra dura de projeto, para [`docs/O que não fazer.md`](file:///home/diego/Android/ChopCut/docs/O%20que%20n%C3%A3o%20fazer.md). **Nunca** deixe a lição só na nota de sessão — a Memory carrega sozinha no próximo boot.
3. **Atualize o [STATE.md](file:///home/diego/Android/ChopCut/STATE.md)** — único dono do estado vivo: backlog (feito/novo), known-issues, decisões.
4. **Mudou o *propósito* de um arquivo, navegação ou um pipeline central?** Atualize as **Regras da Arquitetura**. (Contagem/inventário NÃO precisa de update manual — o hook regenera `STRUCTURE.generated.md`.)
5. **Crie a nota** no padrão `sessions/session#NN-objetivo-da-session.md` (onde `#NN` é o próximo sequencial de dois dígitos e `objetivo-da-session` é o objetivo em letras minúsculas com hífens, ex: `sessions/session#13-reestruturar-timeline.md`), usando o template abaixo.
6. **Commit modular por escopo.** O hook de pré-commit regenera e adiciona `STRUCTURE.generated.md` sozinho.

---

## 📄 Template — `sessions/session#NN-objetivo-da-session.md`

Changelog enxuto. O estado *vivo* mora no STATE.md, a lição durável na Memory — a nota só costura a narrativa do "o que e por quê" desta sessão.

```markdown
# Session #NN — [Título breve]

**Modelo:** [IA usada]  **Data:** [AAAA-MM-DD]
**Objetivo:** [o que foi atacado — 1 linha]

## O que mudou
- [mudança em 1 linha] — *por quê, se não for óbvio*

## Decisões / lições
- [decisão] → salva na Memory como [[slug]] (ou em "O que não fazer")

## Backlog (delta)
- Fechado: [...]  ·  Novo: [...]  → refletido no STATE.md (não copie o backlog inteiro)
```

> **NÃO inclua na nota** (vive em fonte melhor — aponte, não copie):
> - **Arquivos modificados** → `git log` / `git diff` são a fonte exata.
> - **Comandos usados** → estão no `.claude/CLAUDE.md`.
> - **Tabelas antes/depois, "resultados e impactos", telemetria** → ruído; ninguém relê.
> - **O backlog inteiro** → mora só no STATE.md; aqui vai só o *delta*.

