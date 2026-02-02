#!/bin/bash

# =============================================================================
# tvTest.sh - Script para reproduzir vídeos de testes no VLC
# =============================================================================
# Este script:
# 1. Busca o diretório mais recente de screenshots/vídeos de testes no dispositivo
# 2. Baixa todos os vídeos MP4 encontrados
# 3. Reproduz em sequência no VLC
#
# Uso: ./tvTest.sh [opções]
#   -h, --help      Mostra esta ajuda
#   -l, --list      Apenas lista os vídeos disponíveis (não baixa)
#   -k, --keep      Mantém os arquivos baixados após reproduzir
#   -d, --dir DIR   Especifica diretório de destino (padrão: /tmp/chopcut_tests)
# =============================================================================

set -e

# Configurações
DEVICE_TEST_DIR="/sdcard/Movies/ChopCut_Tests"
DEVICE_SCREENSHOT_DIR="/sdcard/Android/data/com.chopcut/files/Pictures/test_screenshots"
LOCAL_DIR="/tmp/chopcut_tests"
KEEP_FILES=false
LIST_ONLY=false
VLC_OPTIONS="--play-and-exit --fullscreen --qt-minimal-view"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# =============================================================================
# Funções
# =============================================================================

show_help() {
    head -n 15 "$0" | tail -n 13
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN[SUCESSO]}${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[AVISO]}${NC} $1"
}

log_error() {
    echo -e "${RED}[ERRO]}${NC} $1"
}

check_dependencies() {
    local deps_ok=true
    
    if ! command -v adb &> /dev/null; then
        log_error "ADB não encontrado. Instale o Android SDK."
        deps_ok=false
    fi
    
    if ! command -v vlc &> /dev/null; then
        log_error "VLC não encontrado. Instale: sudo apt install vlc"
        deps_ok=false
    fi
    
    if [ "$deps_ok" = false ]; then
        exit 1
    fi
}

check_device() {
    log_info "Verificando dispositivo conectado..."
    
    if ! adb devices | grep -q "device$"; then
        log_error "Nenhum dispositivo Android conectado."
        log_info "Conecte um dispositivo via USB ou inicie um emulador."
        exit 1
    fi
    
    local device=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
    log_success "Dispositivo conectado: $device"
}

find_latest_test_dir() {
    log_info "Buscando diretório de teste mais recente..."
    
    # Tenta encontrar em diferentes locais
    local dirs="$DEVICE_TEST_DIR $DEVICE_SCREENSHOT_DIR"
    local latest_dir=""
    local latest_time=0
    
    for dir in $dirs; do
        if adb shell "[ -d $dir ]" 2>/dev/null; then
            # Verifica se há arquivos MP4 neste diretório ou subdiretórios
            local has_videos=$(adb shell "find $dir -name '*.mp4' -type f 2>/dev/null | head -1" | tr -d '\r')
            
            if [ -n "$has_videos" ]; then
                # Pega o timestamp do arquivo mais recente
                local dir_time=$(adb shell "stat -c %Y $dir 2>/dev/null || echo 0" | tr -d '\r')
                
                if [ "$dir_time" -gt "$latest_time" ]; then
                    latest_time=$dir_time
                    latest_dir=$dir
                fi
            fi
        fi
    done
    
    if [ -z "$latest_dir" ]; then
        log_error "Nenhum diretório de teste com vídeos encontrado."
        log_info "Diretórios verificados: $dirs"
        return 1
    fi
    
    log_success "Diretório encontrado: $latest_dir"
    echo "$latest_dir"
}

list_videos() {
    local dir=$1
    log_info "Vídeos disponíveis em $dir:"
    
    adb shell "find $dir -name '*.mp4' -type f 2>/dev/null | sort" | while read -r video; do
        local size=$(adb shell "stat -c %s \"$video\" 2>/dev/null || echo 0" | tr -d '\r')
        local size_mb=$((size / 1024 / 1024))
        local name=$(basename "$video")
        echo "  📹 $name (${size_mb}MB)"
    done
}

download_videos() {
    local remote_dir=$1
    local local_dir=$2
    
    log_info "Criando diretório local: $local_dir"
    mkdir -p "$local_dir"
    
    log_info "Buscando vídeos no dispositivo..."
    local videos=$(adb shell "find $remote_dir -name '*.mp4' -type f 2>/dev/null" | tr -d '\r')
    
    if [ -z "$videos" ]; then
        log_warn "Nenhum vídeo .mp4 encontrado em $remote_dir"
        return 1
    fi
    
    local count=0
    echo "$videos" | while read -r video; do
        if [ -n "$video" ]; then
            local filename=$(basename "$video")
            local local_path="$local_dir/$filename"
            
            log_info "Baixando: $filename"
            if adb pull "$video" "$local_path" 2>/dev/null; then
                log_success "✓ $filename"
                count=$((count + 1))
            else
                log_error "✗ Falha ao baixar: $filename"
            fi
        fi
    done
    
    log_success "Download concluído. Vídeos salvos em: $local_dir"
}

play_in_vlc() {
    local dir=$1
    
    log_info "Preparando playlist no VLC..."
    
    # Cria arquivo de playlist
    local playlist="$dir/playlist.m3u"
    echo "#EXTM3U" > "$playlist"
    
    # Adiciona vídeos à playlist em ordem alfabética
    find "$dir" -name "*.mp4" -type f | sort | while read -r video; do
        local filename=$(basename "$video")
        echo "#EXTINF:0,$filename" >> "$playlist"
        echo "$video" >> "$playlist"
    done
    
    local video_count=$(find "$dir" -name "*.mp4" -type f | wc -l)
    log_success "Playlist criada com $video_count vídeo(s)"
    
    log_info "Iniciando VLC..."
    vlc $VLC_OPTIONS "$playlist" &
    
    log_info "VLC iniciado em background (PID: $!)"
    log_info "Pressione 'q' no VLC ou feche a janela para encerrar"
}

cleanup() {
    if [ "$KEEP_FILES" = false ]; then
        log_info "Limpando arquivos temporários..."
        rm -rf "$LOCAL_DIR"
    else
        log_info "Arquivos mantidos em: $LOCAL_DIR"
    fi
}

# =============================================================================
# Main
# =============================================================================

main() {
    # Parse argumentos
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -l|--list)
                LIST_ONLY=true
                shift
                ;;
            -k|--keep)
                KEEP_FILES=true
                shift
                ;;
            -d|--dir)
                LOCAL_DIR="$2"
                shift 2
                ;;
            *)
                log_error "Opção desconhecida: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Verifica dependências
    check_dependencies
    
    # Verifica dispositivo
    check_device
    
    # Encontra diretório de teste
    LATEST_DIR=$(find_latest_test_dir)
    if [ $? -ne 0 ]; then
        exit 1
    fi
    
    # Apenas lista se solicitado
    if [ "$LIST_ONLY" = true ]; then
        list_videos "$LATEST_DIR"
        exit 0
    fi
    
    # Limpa diretório anterior se existir
    if [ -d "$LOCAL_DIR" ] && [ "$KEEP_FILES" = false ]; then
        rm -rf "$LOCAL_DIR"
    fi
    
    # Baixa vídeos
    download_videos "$LATEST_DIR" "$LOCAL_DIR"
    
    # Reproduz no VLC
    play_in_vlc "$LOCAL_DIR"
    
    # Registra cleanup no exit
    trap cleanup EXIT
    
    log_success "Script concluído!"
}

# Executa main
main "$@"
