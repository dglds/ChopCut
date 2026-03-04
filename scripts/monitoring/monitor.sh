#!/bin/bash
# ==============================================================================
# Script: monitor.sh
#
# Descrição:
#   Painel interativo para selecionar e executar scripts de monitoramento
#   de performance do ChopCut. Este script atua como um wrapper para os
#   outros scripts de monitoramento, oferecendo uma interface de usuário
#   simples para escolher qual aspecto do aplicativo monitorar.
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./monitor.sh
#   3. Escolha uma das opções do menu para iniciar o monitoramento.
#   4. Pressione Ctrl+C para parar o script de monitoramento e retornar ao menu.
#
# ==============================================================================

# Função para exibir o menu
show_menu() {
    clear
    echo "==================================================="
    echo "    Painel de Monitoramento de Performance ChopCut"
    echo "==================================================="
    echo
    echo "  Selecione um script para executar:"
    echo "  -------------------------------------------------"
    echo "  1) Monitorar Performance da Timeline (UI/Render)"
    echo "  2) Monitorar Pipeline de Extração de Thumbnails"
    echo "  3) Monitorar Performance do Cache (Hits/Misses)"
    echo "  4) Monitorar Fluxo Completo (End-to-End)"
    echo
    echo "  q) Sair"
    echo "---------------------------------------------------"
}

# Loop principal do script
while true; do
    show_menu
    read -p "  Escolha uma opção [1-4, q]: " choice

    case $choice in
        1)
            echo "Iniciando 'monitor-timeline-performance.sh'..."
            ./scripts/monitoring/monitor-timeline-performance.sh
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        2)
            echo "Iniciando 'monitor-extraction-pipeline.sh'..."
            ./scripts/monitoring/monitor-extraction-pipeline.sh
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        3)
            echo "Iniciando 'monitor-cache-performance.sh'..."
            ./scripts/monitoring/monitor-cache-performance.sh
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        4)
            echo "Iniciando 'monitor-full-flow.sh'..."
            ./scripts/monitoring/monitor-full-flow.sh
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
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
