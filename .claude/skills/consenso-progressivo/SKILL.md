---
name: consenso-progressivo
description: Use quando o usuário solicitar desenvolvimento de features para Clip Chop (Android/Kotlin/Compose) ou projetos Web (Next/React/TS) que exigem validação arquitetural prévia. Ativa em requests como "quero adicionar [feature]", "preciso implementar [funcionalidade]", "como faço [objetivo técnico]" quando há preocupações com performance em hardware limitado (Intel Celeron N5095A), estabilidade do sistema, ou necessidade de evitar retrabalho.
---

# Protocolo de Consenso Progressivo

Sistema de governança Architecture-First para alinhar intenção humana e execução computacional antes da codificação.

## Princípios

- **Liderar o debate**: Iniciar discussão técnica propondo teses para acelerar o consenso
- **Performance primeiro**: Otimizar para Celeron N5095A (simplicidade sobre complexidade)
- **Uma sugestão superior**: Apresentar uma única abordagem baseada em bom senso técnico
- **Consenso explícito**: Prosseguir apenas após confirmação clara do usuário

## Fases do Protocolo

O fluxo é linear e sequencial. Cada fase requer confirmação antes de avançar.

| Fase | Foco | Entregável |
|------|------|------------|
| 1. Intenção | Entender o objetivo | Decodificação validada |
| 2. Lógica | Regras e algoritmos | Ata de Core & Performance |
| 3. Frontend | UI/UX e componentes | Ata de Interface |
| 4. Veredito | Consolidação final | Especificação assinada |

## Regras de Transição

### Avanço de Fase

Prosseguir quando o usuário confirmar com **qualquer uma das variações**:
- "sim", "SIM", "Sim", "s"
- "aprovado", "APROVADO", "Aprovado"
- "segue", "pode seguir", "vamos", "bora"
- "👍", "✅", "ok", "certo", "isso"
- "próxima", "avança", "continua", "vai"

### Loop de Rejeição

Se o usuário indicar discordância ("não", "errou", "refazer", "corrige", etc):
1. Identificar o ponto de divergência
2. Reformular a proposta da fase atual
3. Apresentar nova abordagem sem avançar de fase

### Bloqueio de Código

**Não gerar código de implementação antes da Fase 3 aprovada.**

Exceção: Pseudocódigo, diagramas ou exemplos ilustrativos são permitidos para facilitar o consenso.

## Comportamento por Fase

### Fase 1: Intenção

Expor o entendimento do objetivo em linguagem do domínio do usuário.

**Captura de Intenção:**

Quando a intenção não estiver clara, solicitar ao usuário as 3 perguntas fundamentais:

| # | Pergunta | Exemplo de resposta |
|---|----------|---------------------|
| 1 | O que o usuário deve conseguir fazer? | "Cortar fora partes de um vídeo" |
| 2 | Como faz isso hoje (se existe)? | "Existe em parte, mas está tudo bagunçado" |
| 3 | Qual problema isso resolve? | "Resolve o principal problema que o app faz" |

**Template de saída (quando intenção clara):**
```
### Fase 1: Intenção

**Entendimento:** [Resumo do que será construído]

**O que:** [Resposta da pergunta 1]
**Como hoje:** [Resposta da pergunta 2]
**Problema resolvido:** [Resposta da pergunta 3]

**Escopo confirmado:**
- Inclui: [lista]
- Exclui: [lista]

**Ata de Decisão:**
| Fase | Status | Resumo |
|------|--------|--------|
| 1. Intenção | 🟡 Em validação | [breve descrição] |
| 2. Lógica | ⚪ Bloqueado | - |
| 3. Frontend | ⚪ Bloqueado | - |
| 4. Veredito | ⚪ Bloqueado | - |

Confirmar para prosseguir para Lógica.
```

### Fase 2: Lógica

Definir regras de negócio, fluxos de dados e estratégias de performance.

**Foco no Celeron N5095A:**
- Preferir processamento síncrono quando possível
- Evitar abstrações desnecessárias
- Minimizar alocações de memória
- Usar Coroutines (Kotlin) com dispatchers adequados

### Fase 3: Frontend

Definir hierarquia de componentes e experiência do usuário.

**Foco de performance:**
- Jetpack Compose: minimizar recompositions, usar derivedStateOf
- React/Next: memoização, lazy loading, code splitting
- Animações: preferir transform CSS/GPU-accelerated

### Fase 4: Veredito

Consolidar todas as decisões em especificação executável.

**Entregável:**
- Resumo das 3 fases anteriores
- Lista de tarefas de implementação
- Liberação para código

## Status Visual

Usar em toda resposta:

| Status | Significado |
|--------|-------------|
| 🟢 | Aprovado - pode prosseguir |
| 🟡 | Em loop - aguardando confirmação ou reformulando |
| ⚪ | Bloqueado - depende de fase anterior |

## Inicialização

Quando esta skill ativar:

1. Identificar qual fase está sendo discutida (ou iniciar na Fase 1 se novo tópico)
2. Apresentar Ata de Decisão atualizada
3. Propor próximo passo ou reformular fase atual conforme contexto

Não assumir que toda conversa começa do zero - verificar histórico para continuar fluxo existente.
