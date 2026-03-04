#!/bin/bash
# ==============================================================================
# Script: logcat-monitor.sh
#
# Descrição:
#   Painel de monitoramento de Logcat para o ChopCut. Este script oferece um
#   menu claro com os principais fluxos de monitoramento de performance,
#   exibe o comando exato que será executado e permite interromper a
#   qualquer momento.
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./logcat-monitor.sh
#   3. Escolha uma das opções de fluxo para monitorar.
#   4. Pressione 'q' a qualquer momento para parar e voltar ao menu.
#
# ==============================================================================

# --- Definição dos Comandos Logcat ---
CMD_TIMELINE='adb logcat -s TimelineEditor:I | grep "TIMELINE PERFORMANCE LOG"'
CMD_EXTRACTION='adb logcat -s ThumbnailExtractorBatch:I ThumbnailStripManager:I'
CMD_CACHE='adb logcat -s ThumbnailCacheManager:D ThumbnailStripManager:I | grep -E "Cache HIT|Cache MISS"'
CMD_FULL='adb logcat -s TimelineEditor:I ThumbnailExtractorBatch:I ThumbnailStripManager:I ThumbnailCacheManager:D ThumbnailViewModel:D'

# --- Função para Exibir o Menu ---
show_menu() {
    clear
    echo "=========================================================="
    echo "       Painel de Monitoramento Logcat - ChopCut"
    echo "=========================================================="
    echo
    echo "  Selecione o fluxo de monitoramento:"
    echo "  ------------------------------------------------------"
    echo "  1) Performance da Timeline (UI/Renderização)"
    echo "  2) Pipeline de Extração de Thumbnails"
    echo "  3) Performance do Cache (Acertos/Falhas)"
    echo "  4) Fluxo Completo (Do início ao fim)"
    echo
    echo "  q) Sair"
    echo "----------------------------------------------------------"
}

# --- Função para Executar o Comando ---
run_command() {
    local cmd_description=$1
    local cmd_to_run=$2
    
    clear
    echo "=========================================================="
    echo "Fluxo de Monitoramento: $cmd_description"
    echo "=========================================================="
    echo
    echo "Comando a ser executado:"
    echo "  $cmd_to_run"
    echo
    echo "----------------------------------------------------------"
    echo ">>> Pressione 'q' a qualquer momento para voltar ao menu <<< "
    echo "----------------------------------------------------------"
    echo

    # Executa o comando em background
    eval $cmd_to_run &
    local cmd_pid=$!

    # Loop para checar a entrada do usuário ou se o processo terminou
    while kill -0 $cmd_pid 2>/dev/null; do
        read -t 0.5 -n 1 input
        if [[ $input = "q" ]]; then
            # Mata o processo do comando adb e seus filhos (grep)
            pkill -P $cmd_pid
            kill $cmd_pid
            echo -e "\n\nMonitoramento interrompido pelo usuário."
            sleep 1
            break
        fi
    done
}

# --- Loop Principal ---
while true; do
    show_menu
    read -p "  Escolha uma opção [1-4, q]: " choice

    case $choice in
        1)
            run_command "Performance da Timeline (UI/Renderização)" "$CMD_TIMELINE"
            ;; 
        2)
            run_command "Pipeline de Extração de Thumbnails" "$CMD_EXTRACTION"
            ;; 
        3)
            run_command "Performance do Cache (Acertos/Falhas)" "$CMD_CACHE"
            ;; 
        4)
            run_command "Fluxo Completo (Do início ao fim)" "$CMD_FULL"
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
