# ChopCut — Documentação

ChopCut é um editor de vídeo Android (Kotlin + Jetpack Compose) focado em corte cirúrgico: timeline em Canvas, export lossless (`CopyPipeline`) e re-encode com níveis de qualidade (`TransformerPipeline`).

> Este arquivo é só um **índice**. Cada assunto tem **uma** fonte única — não duplique conteúdo aqui. (A versão anterior deste README acumulou uma árvore de arquivos e uma contagem que defasaram; por isso virou índice.)

## Onde está cada coisa

| Preciso de… | Fonte única |
|---|---|
| Como iniciar/finalizar uma sessão | [SESSION_PROTOCOL.md](../SESSION_PROTOCOL.md) |
| Estado atual (backlog, known-issues, decisões) | [STATE.md](../STATE.md) |
| Regras de arquitetura + "onde adicionar cada coisa" | [ChopCut - Regras da Arquitetura.md](ChopCut%20-%20Regras%20da%20Arquitetura.md) |
| Inventário vivo (arquivos/tipos/funções) | [STRUCTURE.generated.md](STRUCTURE.generated.md) — auto-gerado, **conferir, não editar** |
| Anti-padrões (o que não fazer) | [O que não fazer.md](O%20que%20n%C3%A3o%20fazer.md) |
| Padrões de performance de Canvas | [../CLAUDE.md](../CLAUDE.md) + skill `/revisar-canvas` |
| Build / install / testes | [../.claude/CLAUDE.md](../.claude/CLAUDE.md) (atalhos do `Makefile`) |
| Localizar um símbolo específico | CodeGraph (`codegraph_search` / `codegraph_context`) |
