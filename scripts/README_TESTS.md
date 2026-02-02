# 🎬 Testes com Gravação de Vídeo - ChopCut

## Comando Único (Faz Tudo)

```bash
cd /home/diego/mobile/ChopCut
./scripts/run_tests_and_watch.sh
```

**O que esse comando faz:**
1. ✅ Verifica dispositivo conectado
2. ✅ Limpa gravações anteriores
3. ✅ Executa testes instrumentados
4. ✅ Baixa vídeos automaticamente
5. ✅ Abre no VLC em fullscreen

---

## Pré-requisitos

### 1. Dispositivo/Emulador
Conecte um dispositivo Android ou inicie o emulador:
```bash
adb devices  # Deve mostrar um dispositivo listado
```

### 2. Permissões no AndroidManifest.xml

Adicione no `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Para Android 10+ -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

### 3. Permissão de Gravação de Tela

Ao executar o teste pela primeira vez, o Android pedirá permissão para gravar a tela.

---

## Onde os Vídeos São Salvos

| Local | Caminho |
|-------|---------|
| **No dispositivo** | `/sdcard/Movies/ChopCut_Tests/` |
| **Na sua máquina** | `/tmp/chopcut_last_test/` |

---

## Comandos Individuais

Se preferir controlar cada etapa:

### Executar testes apenas
```bash
./gradlew :app:connectedDebugAndroidTest
```

### Baixar vídeos manualmente
```bash
adb pull /sdcard/Movies/ChopCut_Tests/ ./meus_videos/
```

### Listar vídeos no dispositivo
```bash
adb shell "ls -la /sdcard/Movies/ChopCut_Tests/"
```

---

## Estrutura dos Testes

```
app/src/androidTest/java/com/chopcut/ui/timeline/
├── ScreenRecordingRule.kt    # Regra de gravação
├── TimelineFlowTest.kt        # Testes de fluxo
└── ...
```

### Testes Disponíveis

| Teste | Descrição | Vídeo Gerado |
|-------|-----------|--------------|
| `testTwoClickRangeCreationFlow` | Fluxo de 2 cliques para criar range | ✅ Sim |
| `testRangeDeletionFlow` | Deleção via FAB | ✅ Sim |
| `testRangeHandleDragFlow` | Drag nas alças | ✅ Sim |
| `testAutoAdjustOverlapPrevention` | Auto-ajuste de sobreposição | ✅ Sim |
| `testSwipeUpToDeleteGesture` | Gesture de delete (swipe up) | ✅ Sim |

---

## Solução de Problemas

### "Nenhum dispositivo encontrado"
```bash
adb devices          # Verifique se aparece
adb kill-server      # Reinicia ADB
adb start-server
```

### "Permissão negada para gravar"
1. Execute o app manualmente uma vez
2. Conceda permissão de gravação de tela quando solicitado
3. Execute os testes novamente

### "VLC não abre"
Instale o VLC:
```bash
sudo apt update && sudo apt install vlc    # Ubuntu/Debian
brew install vlc                            # macOS
```

### "Testes falham"
Verifique logs:
```bash
./gradlew :app:connectedDebugAndroidTest 2>&1 | tee test_log.txt
```

---

## Visualizar Screenshots (alternativa)

Se os vídeos não funcionarem, os testes também salvam screenshots:

```bash
# Baixa screenshots
adb pull /sdcard/Android/data/com.chopcut/files/Pictures/test_screenshots/ ./screenshots/

# Abre relatório no navegador
firefox ./screenshots/*/report.html
```

---

## Dica: Gravação Manual

Para gravar manualmente durante uso do app:

1. Habilite "Modo Demo" nas configurações do ChopCut
2. Use o botão flutuante de gravação
3. Os vídeos aparecerão na mesma pasta

---

*Última atualização: 2026-01-30*
