# Comandos Logcat ChopCut 📱

Este arquivo contém comandos úteis para monitorar o comportamento do aplicativo via ADB Logcat.

## 1. Monitorar Telas e Navegação 🖼️

Para ver quando novas Activities são iniciadas ou telas do Compose Navigation mudam:

```bash
adb logcat | grep -iE "ActivityManager|START|ActivityTaskManager"
```

*Nota: Esse comando mostra as intenções de início de atividades e transições do sistema.*

## 2. Monitorar Todos os Logs do Projeto 🚀

Filtra os logs apenas para o processo atual do ChopCut:

```bash
adb logcat --pid=$(adb shell pidof -s com.chopcut)
```

Ou usando o pacote diretamente (útil se o app reiniciar):

```bash
adb logcat *:V | grep $(adb shell ps | grep com.chopcut | awk '{print $2}')
```

## 3. Monitorar por Prioridade (Erros e Avisos) ⚠️

Para ver apenas erros e avisos do projeto:

```bash
adb logcat com.chopcut:W *:S
```

## 4. Limpar o Buffer do Logcat 🧹

Sempre bom limpar antes de começar uma nova análise:

```bash
adb logcat -c
```

## 5. Salvar Logs em um Arquivo 💾

```bash
adb logcat --pid=$(adb shell pidof -s com.chopcut) > logs_chopcut.txt
```

---

### Dica: Monitor Integrado
Você também pode usar o monitor de logs web integrado ao projeto:
```bash
./gradlew logMonitor
```
