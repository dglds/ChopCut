#!/bin/bash

# timber_log.sh - Script simples para filtrar logs do Timber
# Uso: ./timber_log.sh [opção]
# Opções: -d (debug), -e (error), -w (warn), -i (info), -v (verbose)
# Sem opção: mostra todos os logs Timber

filtro="Timber."

while [[ $# -gt 0 ]]; do
  case $1 in
    -d|--debug)
      filtro="Timber\.d\(.*"
      shift
      ;;
    -e|--error)
      filtro="Timber\.e\(.*"
      shift
      ;;
    -w|--warn)
      filtro="Timber\.w\(.*"
      shift
      ;;
    -i|--info)
      filtro="Timber\.i\(.*"
      shift
      ;;
    -v|--verbose)
      filtro="Timber\.v\(.*"
      shift
      ;;
    -h|--help)
      echo "Uso: ./timber_log.sh [opção]"
      echo ""
      echo "Opções:"
      echo "  -d, --debug    Mostra só logs DEBUG"
      echo "  -e, --error    Mostra só logs ERROR"
      echo "  -w, --warn     Mostra só logs WARN"
      echo "  -i, --info     Mostra só logs INFO"
      echo "  -v, --verbose  Mostra só logs VERBOSE"
      echo "  (sem opção)    Mostra todos os logs Timber"
      echo ""
      echo "Exemplos:"
      echo "  ./timber_log.sh          # Todos os logs Timber"
      echo "  ./timber_log.sh -d       # Só debug"
      echo "  ./timber_log.sh -e       # Só errors"
      exit 0
      ;;
    *)
      echo "Opção desconhecida: $1"
      echo "Use -h para ajuda"
      exit 1
      ;;
  esac
done

# Verifica se há dispositivo conectado
if ! adb devices | grep -q "device$"; then
  echo "❌ Nenhum dispositivo Android conectado!"
  echo "Conecte um dispositivo e execute novamente."
  exit 1
fi

echo "🔍 Filtrando: $filtro"
echo "📱 Pacote: com.chopcut"
echo "Pressione Ctrl+C para parar"
echo "---"
echo "✅ Monitor ATIVO - Aguardando logs..."
echo ""

# Trap para mostrar mensagem ao sair
trap 'echo ""; echo "⏹ Monitor encerrado"; exit 0' INT TERM

# Limpa logcat antigo para mostrar só logs novos
adb logcat -c

# Filtra logs em tempo real
adb logcat -v time *:E *:W *:I *:D *:V | grep --line-buffered "com.chopcut" | grep --line-buffered "$filtro"
