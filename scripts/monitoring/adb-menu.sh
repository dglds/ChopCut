#!/bin/bash
# ==============================================================================
# Script: adb-menu.sh
#
# Descrição:
#   Painel interativo para selecionar e executar comandos ADB de monitoramento
#   de performance do ChopCut. Este script exibe os comandos brutos para
#   maior clareza e controle. Permite voltar ao menu durante a monitoração.
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./adb-menu.sh
#   3. Escolha um dos comandos do menu para iniciar o monitoramento.
#   4. Pressione 'q' a qualquer momento para parar o comando e retornar ao menu.
#
# ==============================================================================

# Definição dos comandos ADB
CMD1='adb logcat -s TimelineEditor:I | grep "TIMELINE PERFORMANCE LOG"'
CMD2='adb logcat -s ThumbnailExtractorBatch:I ThumbnailStripManager:I'
CMD3='adb logcat -s ThumbnailCacheManager:D ThumbnailStripManager:I | grep -E "Cache HIT|Cache MISS"'
CMD4='adb logcat -s TimelineEditor:I ThumbnailExtractorBatch:I ThumbnailStripManager:I ThumbnailCacheManager:D ThumbnailViewModel:D'

# Função para exibir o menu
show_menu() {
    clear
    echo "==================================================="
    echo "         Menu de Comandos ADB para ChopCut"
    echo "==================================================="
    echo
    echo "  Selecione um comando para executar:"
    echo "  -------------------------------------------------"
    echo "  1) [Timeline UI] $CMD1"
    echo "  2) [Extração]    $CMD2"
    echo "  3) [Cache]       $CMD3"
    echo "  4) [Fluxo Full]  $CMD4"
    echo
    echo "  q) Sair"
    echo "---------------------------------------------------"
}

# Função para executar um comando com a opção de voltar
run_command() {
    local cmd_to_run=$1
    echo "Executando: $cmd_to_run"
    echo ">>> Pressione 'q' a qualquer momento para voltar ao menu <<<"
    
    # Executa o comando em background
    eval $cmd_to_run & 
    local cmd_pid=$!

    # Loop para checar a entrada do usuário ou se o processo terminou
    while kill -0 $cmd_pid 2>/dev/null; do
        read -t 0.5 -n 1 input
        if [[ $input = "q" ]]; then
            # Mata o processo do comando adb
            kill $cmd_pid
            # Mata processos filhos (grep)
            pkill -P $cmd_pid
            echo -e "\n\nMonitoramento interrompido pelo usuário."
            sleep 1
            break
        fi
d    done
}


# Loop principal do script
while true; do
    show_menu
    read -p "  Escolha uma opção [1-4, q]: " choice

    case $choice in
        1)
            run_command "$CMD1"
            ;;
        2)
            run_command "$CMD2"
            ;;
        3)
            run_command "$CMD3"
            ;;
        4)
            run_command "$CMD4"
            ;;
        q)
            echo "Saindo..."
            exit 0
            ;;
        *)
            read -p "Opção inválida. Pressione Enter para tentar novamente..."
            ;;
    esac
done