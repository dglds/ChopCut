---
name: finish-session
description: Executa o protocolo de encerramento de sessão do ChopCut. Valida o build, extrai lições aprendidas (Memory-first), atualiza o STATE.md, cria a nota de sessão no formato correto (session#NN-objetivo-da-session.md) e gera o rascunho do commit de fechamento.
---

<what-to-do>

Execute rigorosamente as etapas de encerramento de sessão para garantir a integridade dos metadados e o handoff limpo para a próxima iteração:

1. **Valide a compilação local (Build de Segurança):**
   - Execute o build completo para garantir que nada foi quebrado: `JAVA_HOME=./jdk17 ./gradlew assembleDebug` (ou `make build`).
2. **Abordagem Memory-First (Lições Aprendidas):**
   - Capture qualquer regra de performance, gotcha de API ou antipadrão encontrado durante a sessão.
   - **Inclua o que deu errado:** se algo falhou ou precisou refazer nesta sessão, registre a regra que evitaria o próximo erro. Esse loop é o que faz o protocolo *evitar* o erro, não só narrá-lo.
   - Escreva na **Memory** (`memory/MEMORY.md` — `type: feedback` para erros, `type: project` para decisões) ou, se for regra estrita do projeto, no [`docs/O que não fazer.md`](file:///home/diego/Android/ChopCut/docs/O%20que%20n%C3%A3o%20fazer.md). **Nunca** deixe lições úteis restritas apenas à nota de sessão.
3. **Atualize o [STATE.md](file:///home/diego/Android/ChopCut/STATE.md):**
   - Atualize a seção de backlog (marque o que foi feito, insira novos itens gerados).
   - Atualize os bugs/problemas conhecidos (known issues) e decisões recentes.
4. **Crie a nota de finalização de sessão:**
   - Descubra o número da sessão atual analisando os arquivos existentes no diretório `sessions/`. A sessão atual deve ser `último número + 1` (ex: se o último é `session#12.md`, a atual será a `13`).
   - Identifique o objetivo principal atacado nesta sessão (ex: "reestruturar-timeline" ou "ajustar-aspect-ratio").
   - O nome do arquivo **DEVE** seguir exatamente o padrão:
     `sessions/session#NN-objetivo-da-session.md`
     *(Exemplo real: `sessions/session#13-reestruturar-timeline.md`)*
   - Crie o arquivo no formato do template fornecido em `SESSION_PROTOCOL.md`.
5. **Gere uma mensagem de commit sugerida:**
   - Forneça um resumo dos escopos alterados seguindo o formato padrão: `feat(editor): ...` ou `fix(home): ...`.

</what-to-do>

<supporting-info>

## Template e formato da nota

O template da nota `sessions/session#NN-objetivo-da-session.md` é **fonte única** em [SESSION_PROTOCOL.md](file:///home/diego/Android/ChopCut/SESSION_PROTOCOL.md) §"Template" — siga de lá, não duplique aqui.

</supporting-info>
