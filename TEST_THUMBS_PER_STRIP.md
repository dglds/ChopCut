# Script de Teste para thumbsPerStrip

Este script facilita os testes com diferentes valores de thumbsPerStrip.

## Como Usar

### 1. Via ADB (Dispositivo Conectado)

#### thumbsPerStrip = 10 (Padrão)
```bash
adb shell settings put global chopcut_thumbs_per_strip 10
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
```

#### thumbsPerStrip = 5 (Mais Granular)
```bash
adb shell settings put global chopcut_thumbs_per_strip 5
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
```

#### thumbsPerStrip = 15 (Equilíbrio)
```bash
adb shell settings put global chopcut_thumbs_per_strip 15
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
```

#### thumbsPerStrip = 20 (Máxima Performance)
```bash
adb shell settings put global chopcut_thumbs_per_strip 20
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
```

### 2. Via Código (PreferencesManager)

No `MainActivity.kt` ou similar:

```kotlin
// Importar
import com.chopcut.data.local.PreferencesManager

// Definir valor
val prefsManager = PreferencesManager(this)
prefsManager.thumbsPerStrip = 15  // Alterar conforme teste

// Reiniciar app (opcional)
recreate()
```

### 3. Via SharedPreferences Direto

```bash
adb shell "run-as com.chopcut chmod 666 /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell "run-as com.chopcut sed -i 's/thumbs_per_strip\" value=\"[0-9]*\"/thumbs_per_strip\" value=\"15\"/' /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml"
adb shell am force-stop com.chopcut
adb shell am start -n com.chopcut/.MainActivity
```

---

## Script Automatizado

Crie o arquivo `test_thumbs_per_strip.sh`:

```bash
#!/bin/bash

# Script para testar diferentes valores de thumbsPerStrip
# Uso: ./test_thumbs_per_strip.sh <valor>

if [ -z "$1" ]; then
    echo "Uso: $0 <valor> (5, 10, 15, 20)"
    exit 1
fi

VALUE=$1
echo "========================================="
echo "Testando thumbsPerStrip = $VALUE"
echo "========================================="

# Ajustar preferências
adb shell settings put global chopcut_thumbs_per_strip $VALUE

# Reiniciar app
adb shell am force-stop com.chopcut
sleep 2
adb shell am start -n com.chopcut/.MainActivity

echo "✅ App reiniciado com thumbsPerStrip = $VALUE"
echo ""
echo "📊 Visualize os logs de performance:"
echo "adb logcat | grep 'TIMELINE PERFORMANCE LOG'"
```

Dar permissão de execução:
```bash
chmod +x test_thumbs_per_strip.sh
```

Usar:
```bash
./test_thumbs_per_strip.sh 10
```

---

## Coletar Logs de Performance

### Capturar logs por 30 segundos
```bash
adb logcat -d -v time | grep "TIMELINE PERFORMANCE LOG" > timeline_performance.log
```

### Capturar logs em tempo real
```bash
adb logcat -v time | grep "TIMELINE PERFORMANCE LOG"
```

### Capturar logs com filtros adicionais
```bash
adb logcat -v time -s ChopCut:I | grep "TIMELINE PERFORMANCE LOG\|Draw calls"
```

---

## Matriz de Testes

| Teste | thumbsPerStrip | Dispositivo | FPS Esperado | Observações |
|--------|----------------|------------|---------------|-------------|
| 1 | 5 | Médio (4GB) | 55-60 | Mais granular |
| 2 | 10 | Médio (4GB) | 58-60 | Padrão |
| 3 | 15 | Médio (4GB) | 58-60 | Equilíbrio |
| 4 | 20 | Médio (4GB) | 60 | Máxima performance |

---

## Checklist de Teste

Para cada valor de thumbsPerStrip:

- [ ] Abrir vídeo
- [ ] Navegar no timeline (scroll suave)
- [ ] Verificar FPS (deve ser ≥ 55)
- [ ] Verificar draw calls (deve ser ≤ 4)
- [ ] Verificar shimmer placeholder (por strip inteira)
- [ ] Capturar logs de performance
- [ ] Comparar com outros valores

---

## Análise de Resultados

Após coletar os logs, compare:

```
Teste 1 (thumbsPerStrip = 5):
├─ Draw calls: ~4
├─ FPS: 55-60
└─ Observações: Mais granularidade

Teste 2 (thumbsPerStrip = 10):
├─ Draw calls: ~2
├─ FPS: 58-60
└─ Observações: Balanceado

Teste 3 (thumbsPerStrip = 15):
├─ Draw calls: ~1-2
├─ FPS: 58-60
└─ Observações: Performance otimizada

Teste 4 (thumbsPerStrip = 20):
├─ Draw calls: ~1
├─ FPS: 60
└─ Observações: Máxima performance
```

---

## Comandos Úteis

### Limpar Cache
```bash
adb shell run-as com.chopcut rm -rf cache/thumbnail_strips/
```

### Verificar Configuração Atual
```bash
adb shell "run-as com.chopcut cat /data/data/com.chopcut/shared_prefs/chopcut_prefs.xml" | grep thumbs_per_strip
```

### Capturar Screenshots
```bash
adb exec-out screencap -p > timeline_${value}.png
```

### Capturar Vídeo do Dispositivo
```bash
adb shell screenrecord /sdcard/timeline_test.mp4
# Ctrl+C para parar
adb pull /sdcard/timeline_test.mp4
```

---

## Dicas de Teste

1. **Mesmo Vídeo:** Use sempre o mesmo vídeo para comparação justa
2. **Mesma Posição:** Comece sempre na mesma posição do timeline
3. **Mesma Velocidade:** Faça scroll com velocidade similar
4. **Captura Contínua:** Capture logs durante pelo menos 1 minuto
5. **Comparação:** Compare os logs lado a lado

---

## Resumo de Performance Esperada

### thumbsPerStrip = 5
- Draw calls: ~4 por frame
- FPS: 55-60
- Granularidade: Alta
- Uso recomendado: Vídeos curtos (< 1 min)

### thumbsPerStrip = 10 (Padrão)
- Draw calls: ~2 por frame
- FPS: 58-60
- Granularidade: Média
- Uso recomendado: Vídeos médios (1-5 min)

### thumbsPerStrip = 15
- Draw calls: ~1-2 por frame
- FPS: 58-60
- Granularidade: Baixa
- Uso recomendado: Vídeos longos (5-10 min)

### thumbsPerStrip = 20
- Draw calls: ~1 por frame
- FPS: 60
- Granularidade: Muito baixa
- Uso recomendado: Vídeos muito longos (> 10 min)

---

## Contato e Suporte

Para dúvidas ou problemas:
- Ver logs do Logcat
- Revisar documentação: `TIMELINE_OPTIMIZATION.md`
- Comparar com código original: `git diff`
