# ChopCut — Protocolo de Início e Finalização de Sessões

Este documento serve como guia obrigatório para todos os assistentes de IA (como Claude e Gemini) e desenvolvedores humanos ao iniciar e finalizar sessões de trabalho no projeto **ChopCut**. O objetivo é garantir a consistência arquitetural, a integridade do código e a documentação contínua das alterações.

---

## 🚀 1. Protocolo de Início de Sessão

Antes de propor alterações ou escrever qualquer código, siga rigorosamente estes passos em ordem:

1. **Consulte as Regras da Arquitetura:**
   - Abra e leia o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md). Compreenda a estrutura atual de **16 arquivos** e a regra inegociável de **package único**.
2. **Resgate a Última Sessão:**
   - Procure a última nota de sessão na pasta `sessions/session#NN.md` (ex: `sessions/session#08.md`) para entender o que foi feito recentemente e pegar a lista de pendências (backlog).
3. **Verifique a Compilação Inicial:**
   - Execute a compilação no terminal para confirmar que o projeto inicia em estado estável:
     ```bash
     JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
     ```

---

## 🛠️ 2. Protocolo de Execução da Sessão

Durante o desenvolvimento, mantenha os seguintes padrões críticos:

* **Package Único (`com.chopcut`):** Todos os arquivos devem usar este package. **NUNCA** crie subpacotes ou use imports de arquivos internos do projeto.
* **Sem Criação de Novos Arquivos:** Adicione novas classes, utilitários ou telas exclusivamente dentro da estrutura de 16 arquivos existentes documentada nas regras da arquitetura.
* **Performance do Canvas:** Evite qualquer tipo de alocação de objetos (como `Paint`, `Rect`, `Path`) dentro do escopo de desenho de um Canvas.
* **Commits Modulares:** Crie commits atômicos com mensagens claras e estruturadas por escopo:
  - `refactor(editor): ...`
  - `feat(timeline): ...`
  - `fix(home): ...`
  - `docs: ...`
  - `chore: ...`

---

## 🔍 3. Utilização do CodeGraph

O projeto conta com um banco de dados de grafo semântico local do **CodeGraph** (armazenado na pasta `.codegraph/`). Esta estrutura é sincronizada automaticamente para apoiar agentes de IA e desenvolvedores:
1. **Busca Semântica Avançada:** Realize buscas conceituais por símbolos, fluxos ou lógicas específicas no projeto em vez de buscas parciais de string pura.
2. **Localização de Dependências:** Identifique em qual dos 16 arquivos do projeto um determinado símbolo (método, classe, interface) é referenciado ou herdado.
3. **Análise de Impacto de Refatorações:** Antes de alterar nomes de classes ou apagar pipelines (como na limpeza de áudio), faça uma varredura de referências pelo CodeGraph para obter 100% de cobertura de impacto e evitar falhas ocultas.

---

## 🏁 4. Protocolo de Finalização de Sessão

Para fechar a sessão com segurança e garantir a continuidade:

### Passo 4.1: Validação do Build Completo
Execute as tarefas do Gradle para garantir compilação e empacotamento perfeitos:
```bash
JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
JAVA_HOME=./jdk17 ./gradlew assembleDebug
```

### Passo 4.2: Atualizar a Arquitetura (Se Houver Mudança)
Se ocorreram mudanças na estrutura de arquivos, na navegação ou nos ViewModels, atualize imediatamente o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md).

### Passo 4.3: Criar Relatório de Sessão (`session#NN.md`)
Crie a nota da sessão atual na pasta `sessions/` com o nome `session#NN.md` (onde `NN` é o próximo sequencial disponível, ex: `sessions/session#09.md`). Siga o template contido em [SESSION_PROTOCOL.md](file:///home/diego/Android/ChopCut/SESSION_PROTOCOL.md).

### Passo 4.4: Commitar as Modificações
Adicione todos os arquivos criados e alterados e comite as modificações usando Git:
```bash
git add .
git commit -m "docs: complete session #NN and update reference files"
```

---

## 📄 5. Template do Relatório de Sessão (`session#NN.md`)

```markdown
# Session #NN — [Título Breve e Descritivo]

**Modelo:** [IA Utilizada, ex: Gemini Pro / Claude Sonnet]  
**Data:** [AAAA-MM-DD]  
**Objetivo:** [Objetivo principal trabalhado nesta sessão]

---

## O que foi feito

### 1. [Tópico 1]
- [Detalhe 1]

### 2. [Tópico 2]
- [Detalhe 1]

---

## Resultados e Impactos

| Métrica / Recurso | Antes | Depois | Diferença |
|---|---|---|---|
| ex: Arquivos | 21 arquivos | 16 arquivos | -5 arquivos |

---

## Arquivos Modificados / Deletados / Criados

| Arquivo | Estado | Mudança Principal |
|---|---|---|
| `caminho/do/arquivo.kt` | [MODIFICADO / CRIADO / DELETADO] | [Resumo] |

---

## Comandos Úteis Utilizados

```bash
JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
```

---

## 📋 Pendências e Próximos Passos (Backlog)

- [ ] [Próxima tarefa prioritária]
- [ ] [Melhoria planejada]

---

## 📊 Telemetria da Sessão (IA)

* **Uso Geral de Ferramentas:** [Ferramentas mais utilizadas]
* **Consumo de Contexto / Tokens:** [Estimativa de consumo]
* **Dicas de Otimização para a Próxima IA:**
  - [Sugestão 1 para economizar tokens na próxima rodada]
```
