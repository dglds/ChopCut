#!/bin/bash

# Configurações
PACKAGE_NAME="com.chopcut"
ACTIVITY_NAME="com.chopcut.MainActivity"
TRACE_DURATION_SEC=20
OUTPUT_FILE="chopcut_startup_$(date +%Y%m%d_%H%M%S).perfetto-trace"
DEVICE_PATH="/data/misc/perfetto-traces/trace"

echo "==========================================="
echo "🏎️  Iniciando Profiling do ChopCut (Perfetto)"
echo "==========================================="

# 1. Fechar o aplicativo se estiver rodando
echo "1. Matando o app $PACKAGE_NAME..."
adb shell am force-stop $PACKAGE_NAME
sleep 1

# 2. Configuração do Perfetto
echo "2. Preparando a configuração do Perfetto..."

# Limpar trace anterior no dispositivo
adb shell rm -f $DEVICE_PATH

# Configuração que será passada para a ferramenta perfetto no Android
# Ativamos: sched, gfx, view, wm, dalvik, res, am, e as tags customizadas (app)
CONFIG="
buffers: {
    size_kb: 63488
    fill_policy: RING_BUFFER
}
data_sources: {
    config {
        name: \"linux.process_stats\"
        target_buffer: 0
        process_stats_config {
            scan_all_processes_on_start: true
        }
    }
}
data_sources: {
    config {
        name: \"android.surfaceflinger.frametimeline\"
    }
}
data_sources: {
    config {
        name: \"linux.sys_stats\"
        sys_stats_config {
            stat_period_ms: 1000
            stat_counters: STAT_CPU_TIMES
            stat_counters: STAT_FORK_COUNT
        }
    }
}
data_sources: {
    config {
        name: \"linux.ftrace\"
        ftrace_config {
            ftrace_events: \"sched/sched_switch\"
            ftrace_events: \"power/suspend_resume\"
            ftrace_events: \"sched/sched_wakeup\"
            ftrace_events: \"sched/sched_wakeup_new\"
            ftrace_events: \"sched/sched_waking\"
            ftrace_events: \"power/cpu_frequency\"
            ftrace_events: \"power/cpu_idle\"
            ftrace_events: \"power/gpu_frequency\"
            ftrace_events: \"raw_syscalls/sys_enter\"
            ftrace_events: \"raw_syscalls/sys_exit\"
            ftrace_events: \"kmem/rss_stat\"
            ftrace_events: \"ion/ion_stat\"
            ftrace_events: \"mm_event/mm_event_record\"
            ftrace_events: \"kmem/ion_heap_grow\"
            ftrace_events: \"kmem/ion_heap_shrink\"
            atrace_categories: \"am\"
            atrace_categories: \"dalvik\"
            atrace_categories: \"gfx\"
            atrace_categories: \"hal\"
            atrace_categories: \"input\"
            atrace_categories: \"res\"
            atrace_categories: \"sched\"
            atrace_categories: \"view\"
            atrace_categories: \"wm\"
            atrace_categories: \"bionic\"
            atrace_categories: \"video\"
            atrace_categories: \"camera\"
            atrace_categories: \"audio\"
            atrace_apps: \"$PACKAGE_NAME\"
        }
    }
}
duration_ms: $((TRACE_DURATION_SEC * 1000))
"

echo "$CONFIG" > perfetto_config.pbtx
adb push perfetto_config.pbtx /data/local/tmp/perfetto_config.pbtx > /dev/null

# 3. Iniciar o aplicativo
echo "3. Abrindo o aplicativo..."
adb shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" > /dev/null

echo "⏳ Aguardando 5 segundos para você chegar na tela correta..."
sleep 5

# 4. Iniciar o trace em background
echo "4. Iniciando gravação do Perfetto ($TRACE_DURATION_SEC segundos)..."
adb shell "cat /data/local/tmp/perfetto_config.pbtx | perfetto --txt -c - -o $DEVICE_PATH" &
PERFETTO_PID=$!

# Aguardar 1 segundo para o perfetto engatar
sleep 1

echo "🚀 GRAVANDO AGORA! Realize a operação no app..."
# Barra de progresso marota
for ((i=$TRACE_DURATION_SEC; i>0; i--)); do
    printf "\rFaltam %2d segundos..." "$i"
    sleep 1
done
echo ""

# Garantir que o processo finalizou
wait $PERFETTO_PID

# 5. Baixar o arquivo
echo "5. Baixando o trace para o seu computador..."
adb pull $DEVICE_PATH $OUTPUT_FILE
adb shell rm -f $DEVICE_PATH /data/local/tmp/perfetto_config.pbtx
rm -f perfetto_config.pbtx

echo "==========================================="
echo "✅ Pronto! Arquivo gerado: $OUTPUT_FILE"
echo "👉 Abra https://ui.perfetto.dev e arraste este arquivo para lá."
echo "==========================================="
