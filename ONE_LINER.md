# One-Liner Simples (sem loops que travam)

## Versão 1: Com timeout (recomendado)
```bash
timeout 300s bash -c "adb logcat -c && adb logcat -v time --pid=\$(adb shell pidof com.chopcut) | grep --line-buffered -iE '(extract|Cache|Strip)'"
```

## Versão 2: Sem timeout (Ctrl+C para sair)
```bash
adb logcat -c && adb logcat -v time --pid=$(adb shell pidof com.chopcut) | grep --line-buffered -iE '(extract|Cache|Strip)'
```

## Versão 3: Apenas últimos logs (não monitora em tempo real)
```bash
adb logcat -v time --pid=$(adb shell pidof com.chopcut) | grep -iE '(extract|Cache|Strip)' | tail -20
```

## Versão 4: Monitora e salva em arquivo
```bash
adb logcat -c && adb logcat -v time --pid=$(adb shell pidof com.chopcut) | tee monitor.log | grep --line-buffered -iE '(extract|Cache|Strip)'
```
