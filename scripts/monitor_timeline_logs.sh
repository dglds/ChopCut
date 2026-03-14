#!/bin/bash

# Script para monitorar logs de timeline em tempo real

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  📋 MONITORANDO LOGS DE TIMELINE                           ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "Pressione Ctrl+C para parar."
echo ""

# Limpar logcat
echo "🧹 Limpando logcat..."
adb logcat -c
echo "✅ Logcat limpo"
echo ""
echo "▶️ Iniciando monitoramento..."
echo ""

# Filtrar logs relevantes
adb logcat -s TEST_* | grep --line-buffered -E "(✅|❌|⚠️|▶️|🏁|📏|⏳|╔|╠|╚|║)"
