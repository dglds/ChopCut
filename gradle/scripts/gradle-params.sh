#!/bin/bash
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#              CONFIGURAÇÕES E PARÂMETROS GRADLE (CHOPCUT)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Edite as variáveis abaixo alterando para 'true' ou 'false' para ativar/desativar
# as flags correspondentes ao rodar tarefas do Gradle pelo menu.

# ── NÍVEL DE VERBOSIDADE (LOGGING) ────────────────────────────────────────────────
# (Defina apenas UM como true por vez. Se todos forem false, o Gradle usará o padrão)

# Modo Silencioso: Exibe apenas erros críticos.
GRADLE_QUIET=false

# Modo de Avisos: Exibe avisos (warnings) e erros. (Recomendado para uso normal)
GRADLE_WARN=false

# Modo Informativo: Exibe mensagens de ciclo de vida e detalhes adicionais.
GRADLE_INFO=true

# Modo Depuração: Logs extremamente detalhados de toda a atividade do Daemon.
GRADLE_DEBUG=false


# ── OPÇÕES DE RASTREAMENTO DE ERROS (STACKTRACE) ──────────────────────────────────

# Exibe stacktrace para exceções do código do usuário.
GRADLE_STACKTRACE=false

# Exibe stacktrace completo incluindo código interno do Gradle e plugins.
GRADLE_FULL_STACKTRACE=true


# ── OTIMIZAÇÃO E PERFORMANCE ─────────────────────────────────────────────────────

# Execução Paralela: Executa tarefas em paralelo em múltiplos módulos (mais rápido).
GRADLE_PARALLEL=true

# Cache de Build: Reutiliza arquivos compilados em tarefas que não mudaram.
GRADLE_BUILD_CACHE=true

# Configuração sob Demanda: Configura apenas os projetos necessários para a tarefa atual.
GRADLE_CONFIGURE_ON_DEMAND=true

# Limite de Workers: Define a concorrência de threads. (Se true, usa --max-workers=4)
GRADLE_LIMIT_WORKERS=false


# ── MODOS DE EXECUÇÃO ESPECIAIS ──────────────────────────────────────────────────

# Gradle Daemon: Mantém o processo Gradle ativo em segundo plano para builds mais rápidos.
GRADLE_DAEMON=true

# Modo Offline: Roda tarefas sem internet, usando apenas dependências já cacheadas.
GRADLE_OFFLINE=false

# Forçar Execução: Executa todas as tarefas do zero, ignorando o cache do build.
GRADLE_RERUN_TASKS=false

# Continuar em Falha: Executa outras tarefas independentes mesmo que uma falhe.
GRADLE_CONTINUE=false

# Execução Simulada: Mostra as tarefas que seriam executadas sem de fato rodá-las.
GRADLE_DRY_RUN=false
