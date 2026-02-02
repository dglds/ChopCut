#!/bin/bash

# =============================================================================
# run_tests_and_watch.sh - Executa testes e reproduz vídeos automaticamente
# =============================================================================
# Uso simples: ./run_tests_and_watch.sh
# =============================================================================

set -e

DIR="/tmp/chopcut_last_test"
DEVICE_DIR="/sdcard/Movies/ChopCut_Tests"

echo "🎬 ChopCut Test Runner"
echo "======================"

# Verifica ADB
if ! command -v adb &> /dev/null; then
    echo "❌ ADB não encontrado"
    exit 1
fi

# Verifica dispositivo
if ! adb devices | grep -q "device$"; then
    echo "❌ Conecte um dispositivo Android ou inicie o emulador"
    exit 1
fi

echo "📱 Dispositivo conectado"

# Limpa testes anteriores no dispositivo
echo "🧹 Limpando testes anteriores..."
adb shell "rm -rf $DEVICE_DIR/*" 2>/dev/null || true

# Executa os testes
echo "🚀 Executando testes instrumentados..."
./gradlew :app:connectedDebugAndroidTest || {
    echo "⚠️  Alguns testes falharam, mas continuando..."
}

# Aguarda gravações finalizarem
echo "⏳ Aguardando finalização das gravações..."
sleep 3

# Cria diretório local
rm -rf "$DIR"
mkdir -p "$DIR"

# Baixa vídeos
echo "📥 Baixando vídeos..."
VIDEOS=$(adb shell "find $DEVICE_DIR -name '*.mp4' 2>/dev/null" | tr -d '\r')

if [ -z "$VIDEOS" ]; then
    echo "⚠️  Nenhum vídeo encontrado em $DEVICE_DIR"
    echo "   Verificando screenshots..."
    
    # Tenta screenshots
    SCREENSHOT_DIR="/sdcard/Android/data/com.chopcut/files/Pictures/test_screenshots"
    REPORTS=$(adb shell "find $SCREENSHOT_DIR -name 'report.html' 2>/dev/null" | head -1 | tr -d '\r')
    
    if [ -n "$REPORTS" ]; then
        echo "📥 Baixando relatório de screenshots..."
        adb pull "$SCREENSHOT_DIR" "$DIR/" 2>/dev/null || true
        echo "✅ Screenshots salvos em: $DIR/"
        
        # Abre no navegador
        if command -v xdg-open &> /dev/null; then
            xdg-open "$DIR/report.html" 2>/dev/null || true
        fi
    fi
    
    exit 0
fi

# Baixa cada vídeo
echo "$VIDEOS" | while read -r video; do
    [ -z "$video" ] && continue
    name=$(basename "$video")
    echo "   📹 $name"
    adb pull "$video" "$DIR/$name" 2>/dev/null || echo "   ⚠️  Falha ao baixar $name"
done

# Lista vídeos baixados
echo ""
echo "📂 Vídeos baixados:"
ls -lh "$DIR"/*.mp4 2>/dev/null || echo "   Nenhum vídeo MP4"

# Reproduz no VLC
echo ""
echo "▶️  Iniciando VLC..."

# Cria playlist ordenada
PLAYLIST="$DIR/playlist.m3u"
echo "#EXTM3U" > "$PLAYLIST"
ls -1 "$DIR"/*.mp4 2>/dev/null | sort | while read -r f; do
    echo "#EXTINF:0,$(basename "$f")" >> "$PLAYLIST"
    echo "$f" >> "$PLAYLIST"
done

if command -v vlc &> /dev/null; then
    vlc --play-and-exit --fullscreen --qt-minimal-view "$PLAYLIST" &
    echo "✅ VLC iniciado!"
else
    echo "⚠️  VLC não encontrado. Vídeos estão em: $DIR"
    
    # Tenta outros players
    if command -v totem &> /dev/null; then
        totem "$DIR"/*.mp4 &
    elif command -v mpv &> /dev/null; then
        mpv --fs "$DIR"/*.mp4 &
    fi
fi

echo ""
echo "📍 Vídeos salvos em: $DIR"
echo "🎉 Pronto!"
