#!/bin/bash
# ==============================================================================
# Script: adb-menu.sh
#
# Descrição:
#   Painel interativo para selecionar e executar comandos ADB de monitoramento
#   de performance do ChopCut. Este script exibe os comandos brutos para
#   maior clareza e controle.
#
# Como usar:
#   1. Conecte seu dispositivo com 'adb connect'
#   2. Execute o script: ./adb-menu.sh
#   3. Escolha um dos comandos do menu para iniciar o monitoramento.
#   4. Pressione Ctrl+C para parar o comando e retornar ao menu.
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

# Loop principal do script
while true; do
    show_menu
    read -p "  Escolha uma opção [1-4, q]: " choice

    case $choice in
        1)
            echo "Executando: $CMD1"
            eval $CMD1
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        2)
            echo "Executando: $CMD2"
            eval $CMD2
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        3)
            echo "Executando: $CMD3"
            eval $CMD3
            read -p "Monitoramento parado. Pressione Enter para voltar ao menu..."
            ;;
        4)
            echo "Executando: $CMD4"
            eval $CMD4
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
