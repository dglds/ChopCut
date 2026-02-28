#!/bin/bash

# Script para gerar vídeos de teste para os testes instrumentados
# Executar no diretório raiz do projeto

set -e

echo "🎬 Gerando vídeos de teste para testes instrumentados..."

# Criar diretório de assets de teste
mkdir -p app/src/androidTest/assets/videos

# Criar vídeo de 8.25 segundos (8 segundos e 250 ms)
DURACAO="8.25"
ffmpeg -y -f lavfi -i testsrc=duration=${DURACAO}:size=640x480:rate=30 \
       -c:v libx264 -preset veryfast -crf 23 \
       -c:a aac -b:a 128k \
       -t ${DURACAO} \
       app/src/androidTest/assets/videos/test_video_8.25s.mp4

echo "✅ Vídeo ${DURACAO}s criado: app/src/androidTest/assets/videos/test_video_8.25s.mp4"

# Criar vídeo mais longo para testes adicionais
DURACAO_LONGA="30"
ffmpeg -y -f lavfi -i testsrc=duration=${DURACAO_LONGA}:size=1280x720:rate=30 \
       -c:v libx264 -preset veryfast -crf 23 \
       -c:a aac -b:a 128k \
       -t ${DURACAO_LONGA} \
       app/src/androidTest/assets/videos/test_video_${DURACAO_LONGA}s.mp4

echo "✅ Vídeo ${DURACAO_LONGA}s criado: app/src/androidTest/assets/videos/test_video_${DURACAO_LONGA}s.mp4"

# Listar arquivos gerados
echo ""
echo "📁 Arquivos gerados:"
ls -lh app/src/androidTest/assets/videos/

# Validar tamanhos
echo ""
echo "📏 Tamanhos dos vídeos:"
du -h app/src/androidTest/assets/videos/*.mp4

echo ""
echo "🎉 Vídeos de teste criados com sucesso!"
echo "📝 Lembre-se de atualizar TestVideoConfig.kt com as durações reais."