# ChopCut — Protocolo de Início e Finalização de Sessões

Este documento serve como guia obrigatório para todos os desenvolvedores e assistentes de IA que iniciarem uma sessão de trabalho no projeto **ChopCut**. O objetivo é garantir a consistência arquitetural, a rastreabilidade das alterações e a documentação contínua do projeto.

---

## 🚀 1. Protocolo de Início de Sessão

Antes de escrever qualquer linha de código ou propor alterações, o assistente/desenvolvedor **DEVE** seguir estes passos em ordem:

1. **Ler as Regras de Arquitetura:**
   - Abra e leia o arquivo [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md). Preste atenção à lista de arquivos ativos (atualmente 16 arquivos) e regras críticas de package único.
2. **Ler as Instruções do Projeto:**
   - Leia o arquivo `CLAUDE.md` na raiz para entender o padrão de commits, comandos úteis e diretrizes de performance.
3. **Resgatar o Histórico Recente:**
   - Identifique e leia o último documento de sessão em `docs/session#NN.md` (por exemplo, `session#08.md`). Isso fornecerá o contexto exato do que foi concluído na última etapa e quais eram os próximos passos prioritários.
4. **Verificar o Estado Atual do Código:**
   - Execute `git status` para verificar se existem alterações não commitadas.
   - Execute a compilação inicial para confirmar que a base está íntegra:
     ```bash
     JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
     ```

---

## 🛠️ 2. Protocolo de Execução da Sessão

Durante o desenvolvimento, siga estritamente estas diretrizes:

* **Package Único (`com.chopcut`):** Todos os arquivos devem pertencer a este package. Não importe arquivos internos entre si.
* **Sem Criação Desnecessária de Arquivos:** Qualquer código novo deve ser implementado preferencialmente dentro dos 16 arquivos existentes documentados nas regras de arquitetura.
* **Performance em Primeiro Lugar:**
  - Evite alocações dentro do escopo de Canvas (`onDraw` / `DrawScope`).
  - Isole Canvas de animações (cursor/playhead) do Canvas de renderização estática (miniaturas da timeline).
* **Commits Modulares:** Crie commits atômicos e bem descritos seguindo a convenção:
  - `refactor(editor): ...` — para refatorações
  - `feat(timeline): ...` — para novos recursos
  - `fix(home): ...` — para correções
  - `docs: ...` — para documentação
  - `chore: ...` — para manutenção do build ou scripts

---

## 🏁 3. Protocolo de Finalização de Sessão

Ao concluir o trabalho planejado ou ao final do limite da sessão, siga estas etapas para fechamento e passagem de bastão:

### Passo 3.1: Validar Compilação e Build
Certifique-se de que a aplicação compila e empacota perfeitamente:
```bash
JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
JAVA_HOME=./jdk17 ./gradlew assembleDebug
```

### Passo 3.2: Sincronizar Arquitetura (Se Houver Mudança)
Se você adicionou, removeu ou renomeou algum arquivo `.kt` ou mudou rotas essenciais, **atualize imediatamente** o gráfico de arquivos em [ChopCut - Regras da Arquitetura.md](file:///home/diego/Android/ChopCut/docs/ChopCut%20-%20Regras%20da%20Arquitetura.md).

### Passo 3.3: Criar Nota de Sessão (`session#NN.md`)
Crie um arquivo na pasta `docs/` com o nome `session#NN.md` (onde `NN` é o próximo número sequencial, ex: `session#09.md`). Utilize exatamente o template fornecido na seção a seguir.

### Passo 3.4: Commitar Tudo
Adicione todos os arquivos novos e modificados (incluindo as notas de sessão e documentos atualizados) e faça o commit final:
```bash
git add .
git commit -m "docs: complete session #NN and document changes"
```

---

## 📄 4. Template para o Documento `session#NN.md`

Copie e preencha o bloco abaixo ao criar a nova nota de sessão em `docs/`:

```markdown
# Session #NN — [Breve Título Descritivo da Sessão]

**Modelo:** [Ex: Gemini Pro / Claude Sonnet 4.6]  
**Data:** [AAAA-MM-DD]  
**Objetivo:** [Descreva sucintamente o objetivo principal que foi proposto e trabalhado nesta sessão]

---

## O que foi feito

### 1. [Tópico 1 de Alteração]
- [Detalhe 1]
- [Detalhe 2]

### 2. [Tópico 2 de Alteração]
- [Detalhe 1]

---

## Resultados e Métricas (Se aplicável)

| Métrica / Recurso | Antes | Depois | Diferença / Impacto |
|---|---|---|---|
| ex: Arquivos | 21 arquivos | 16 arquivos | -5 arquivos |
| ex: Erros de Build | 4 warnings | 3 warnings | -1 warning |

---

## Arquivos Modificados / Deletados / Criados

| Arquivo | Estado | Mudança Principal |
|---|---|---|
| `caminho/do/arquivo.kt` | [CRIADO / DELETADO / MODIFICADO] | [Descrição breve do que mudou] |

---

## Comandos Úteis Utilizados

```bash
# Comandos específicos executados na sessão que facilitam a vida do próximo desenvolvedor
JAVA_HOME=./jdk17 ./gradlew compileDebugKotlin
```

---

## 📋 Pendências e Próximos Passos (Backlog)

- [ ] [Próxima tarefa prioritária decorrente desta sessão]
- [ ] [Segunda tarefa em ordem de importância]
- [ ] [Melhoria ou refinamento planejado]

---

## 📊 Telemetria da Sessão (IA)

* **Uso Geral de Ferramentas:** [Ferramentas mais utilizadas, ex: read_file, run_command]
* **Consumo de Contexto / Tokens:** [Estimativa de tokens consumidos e o que mais gerou consumo, ex: leitura de arquivos grandes]
* **Dicas de Otimização para a Próxima IA:**
  - [Sugestão 1 para economizar tokens na próxima sessão, ex: leia apenas o trecho X com visualização específica de linhas]
  - [Sugestão 2]
```
