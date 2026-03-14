#!/bin/bash
# Coleta dados do git para snapshot de contexto
# Output: dados estruturados para o Claude montar o JSON

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "=== BRANCH ==="
git branch --show-current

echo "=== STATUS ==="
git status --short

echo "=== RECENT_COMMITS ==="
git log --oneline -10

echo "=== DIFF_STAT ==="
git diff --stat HEAD 2>/dev/null || true
git diff --stat --cached HEAD 2>/dev/null || true

echo "=== HOTSPOTS ==="
# Diretórios mais modificados (top 5)
git diff --name-only HEAD 2>/dev/null | sed 's|/[^/]*$||' | sort | uniq -c | sort -rn | head -5

echo "=== WARNINGS ==="
# Arquivos .backup
find app/src -name "*.backup" 2>/dev/null | while read f; do echo "BACKUP: $f"; done

# Arquivos untracked grandes
git ls-files --others --exclude-standard | head -10 | while read f; do
  if [ -f "$f" ]; then
    size=$(stat -c%s "$f" 2>/dev/null || echo 0)
    if [ "$size" -gt 100000 ]; then
      echo "LARGE_UNTRACKED: $f ($(numfmt --to=iec $size))"
    fi
  fi
done

echo "=== END ==="
