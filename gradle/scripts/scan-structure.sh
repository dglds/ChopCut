#!/usr/bin/env bash
#
# scan-structure.sh — Inventário automático da estrutura do projeto.
#
# Lê todos os .kt do package com.chopcut e gera docs/STRUCTURE.generated.md
# com a contagem VIVA de arquivos, tipos (class/object/interface/enum) e
# funções por arquivo. Substitui a manutenção manual da estrutura nos docs:
# a IA e os devs só CONFEREM este arquivo, nunca o editam à mão.
#
# Uso:
#   gradle/scripts/scan-structure.sh            # regenera o doc
#   gradle/scripts/scan-structure.sh --check    # exit 1 se o doc está desatualizado (CI/hook)
#
# Roda automaticamente no commit via .githooks/pre-commit.
#
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SRC_DIR="$REPO_ROOT/app/src/main/java/com/chopcut"
OUT="$REPO_ROOT/docs/STRUCTURE.generated.md"

# Heurística de declarações (não é um parser Kotlin; suficiente para detectar drift).
TYPE_RE='^[[:space:]]*(([A-Za-z]+)[[:space:]]+)*(class|object|interface)([[:space:]]|\(|<|:|$)'
FUN_RE='^[[:space:]]*(([A-Za-z]+)[[:space:]]+)*fun[[:space:]<]'

gen() {
  local total_files=0 total_types=0 total_funs=0
  local rows=""

  while IFS= read -r f; do
    total_files=$((total_files + 1))
    local rel="${f#"$SRC_DIR"/}"
    local ntypes nfuns names
    ntypes=$(grep -cE "$TYPE_RE" "$f" || true)
    nfuns=$(grep -cE "$FUN_RE" "$f" || true)
    names=$(grep -hE "$TYPE_RE" "$f" \
      | grep -oE '(class|object|interface)[[:space:]]+[A-Za-z0-9_]+' \
      | awk '{print $2}' | sort -u | paste -sd, - || true)
    total_types=$((total_types + ntypes))
    total_funs=$((total_funs + nfuns))
    rows+="| \`$rel\` | $ntypes | $nfuns | ${names:-—} |"$'\n'
  done < <(find "$SRC_DIR" -name '*.kt' | LC_ALL=C sort)

  cat <<EOF
# ChopCut — Estrutura do Projeto (AUTO-GERADO)

> ⚠️ **NÃO EDITE À MÃO.** Este arquivo é regenerado por \`gradle/scripts/scan-structure.sh\`
> (automaticamente no commit via \`.githooks/pre-commit\`). Para mudar a estrutura,
> mude o código — o inventário se atualiza sozinho. A IA deve **conferir** este arquivo,
> não atualizá-lo. Para regras e "onde adicionar cada coisa", veja
> [Regras da Arquitetura](./ChopCut%20-%20Regras%20da%20Arquitetura.md);
> para análise de símbolos/dependências, use o **CodeGraph**.

## Totais

| Métrica | Valor |
|---|---|
| Arquivos \`.kt\` (com.chopcut) | **$total_files** |
| Tipos (class/object/interface/enum) | **$total_types** |
| Funções (\`fun\`) | **$total_funs** |

## Inventário por arquivo

| Arquivo | Tipos | Funções | Nomes dos tipos |
|---|---:|---:|---|
$rows
EOF
}

case "${1:-}" in
  --check)
    tmp="$(mktemp)"
    gen > "$tmp"
    if ! diff -q "$OUT" "$tmp" >/dev/null 2>&1; then
      echo "STRUCTURE.generated.md desatualizado. Rode: gradle/scripts/scan-structure.sh" >&2
      rm -f "$tmp"
      exit 1
    fi
    rm -f "$tmp"
    echo "STRUCTURE.generated.md está atualizado."
    ;;
  *)
    gen > "$OUT"
    echo "Gerado: ${OUT#"$REPO_ROOT"/}"
    ;;
esac
