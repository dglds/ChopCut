---
name: start-session
description: Inicia oficialmente uma nova sessão de desenvolvimento no ChopCut. Executa a leitura do STATE.md, localiza o último arquivo de sessão em sessions/, resume o backlog pendente e alinha as prioridades imediatas.
---

<what-to-do>

Execute o ritual de início de sessão de forma rigorosa para alinhar seu contexto e informar o usuário sobre o ponto de partida atual:

1. **Leia o `STATE.md`** — identifique o backlog atual (tarefas ativas/novas), conhecidos (known issues) e últimas decisões.
2. **As lições duráveis já estão carregadas** — a Memory entra no contexto sozinha no boot; não precisa garimpá-las nas notas antigas. Para o contexto narrativo da última sessão, localize o maior `#NN` em `sessions/` e leia só o *delta* (o que ficou aberto). O estado vivo manda no `STATE.md`.
3. **Resuma e apresente** ao usuário o status de partida em uma mensagem curta e estruturada:
   - **Última Sessão:** Breve lembrete do que foi concluído na sessão `#NN`.
   - **Foco da Sessão Atual:** Qual o objetivo principal que estamos prestes a atacar.
   - **Backlog Prioritário:** Lista curta do que está pendente no `STATE.md`.
   - **Alerta de Regras:** Um lembrete rápido das restrições de arquitetura aplicáveis ao objetivo (ex: se formos mexer em UI, lembrar do Canvas; se formos mexer em lógica, lembrar do package único).

</what-to-do>

<supporting-info>

## Diretrizes de Contexto

- **Nunca adivinhe o estado:** O `STATE.md` e a última sessão são as únicas fontes do estado vivo. Se houver divergências, priorize o `STATE.md`.
- **Compilação rápida opcional:** Caso vá mexer em código logo no início, sugira ou execute o build rápido para garantir estabilidade de partida: `JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin`.

</supporting-info>
