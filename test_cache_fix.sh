#!/bin/bash

# Script para testar correção de cache
# Uso: ./test_cache_fix.sh [teste|produção]

if [ -z "$1" ]; then
    echo "Uso: $0 [teste|produção]"
    echo ""
    echo "  teste      - Habilita clearCacheOnStartup = true (para testes)"
    echo "  produção   - Desabilita clearCacheOnStartup = false (recomendado)"
    exit 1
fi

MODE=$1

echo "========================================"
echo "Configurando modo: $MODE"
echo "========================================"

case $MODE in
    teste)
        echo "Habilitando limpeza de cache no startup..."
        adb shell "run-as com.chopcut chmod 666 /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
        adb shell "run-as com.chopcut sed -i 's/clear_cache_on_startup\" value=\"false\"/clear_cache_on_startup\" value=\"true\"/' /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"

        # Verificar mudança
        CURRENT_VALUE=$(adb shell "run-as com.chopcut cat /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml" | grep clear_cache_on_startup | grep -oP 'value=\"[^\"]*\"' | cut -d'"' -f2)

        echo ""
        echo "✅ Configuração alterada:"
        echo "   clearCacheOnStartup = $CURRENT_VALUE"
        echo ""
        echo "🧪 MODO TESTE HABILITADO"
        echo "   - Cache será limpo a cada inicialização"
        echo "   - Útil para testes automatizados"
        echo "   - NÃO recomendado para produção"
        ;;

    produção)
        echo "Desabilitando limpeza de cache no startup..."
        adb shell "run-as com.chopcut chmod 666 /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
        adb shell "run-as com.chopcut sed -i 's/clear_cache_on_startup\" value=\"true\"/clear_cache_on_startup\" value=\"false\"/' /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"

        # Verificar mudança
        CURRENT_VALUE=$(adb shell "run-as com.chopcut cat /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml" | grep clear_cache_on_startup | grep -oP 'value=\"[^\"]*\"' | cut -d'"' -f2)

        echo ""
        echo "✅ Configuração alterada:"
        echo "   clearCacheOnStartup = $CURRENT_VALUE"
        echo ""
        echo "🚀 MODO PRODUÇÃO HABILITADO"
        echo "   - Cache PERSISTE entre sessões"
        echo "   - Thumbnails carregam instantaneamente"
        echo "   - Cache LRU gerencia automaticamente"
        ;;

    *)
        echo "❌ Modo inválido: $1"
        echo "Use: $0 [teste|produção]"
        exit 1
        ;;
esac

echo ""
echo "Reiniciando app..."
adb shell am force-stop com.chopcut
sleep 2
adb shell am start -n com.chopcut/.MainActivity

echo "✅ App reiniciado"
echo ""
echo "📊 Verifique os logs:"
echo "   adb logcat | grep 'LIMPEZA DE CACHE AO INICIAR'"
echo ""
echo "========================================"
