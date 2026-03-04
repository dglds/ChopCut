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
    echo "  -------------------------------------------------"
    echo "  5) Criar Grid de Monitoramento (ghostty)"
    echo
    echo "  q) Sair"
    echo "---------------------------------------------------"
}

# Loop principal do script
while true; do
    show_menu
    read -p "  Escolha uma opção [1-5, q]: " choice

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
        5)
            if ! command -v ghostty-pane-splitter &> /dev/null; then
                echo "Erro: 'ghostty-pane-splitter' não encontrado."
                echo "Por favor, instale-o com 'cargo install ghostty-pane-splitter' e tente novamente."
                read -p "Pressione Enter para continuar..."
                continue
            fi
            read -p "Digite o layout do grid (ex: 4 para 2x2, ou 2x3): " layout
            echo "Criando grid com layout '$layout'. Você precisará executar os scripts manualmente em cada painel."
            ghostty-pane-splitter "$layout"
            echo "Grid criado. Saindo do painel principal para que você possa usar os novos painéis."
            exit 0
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