#!/bin/bash

while true; do
    clear
    echo "=== ChopCut Performance ==="
    echo ""

    # Get PID
    PID=$(adb shell "ps | grep chopcut" | awk '{print $2}')

    if [ -n "$PID" ]; then
        # Memory in MB (Pss Total) - first occurrence only
        MEM=$(adb shell dumpsys meminfo $PID 2>/dev/null | grep "        TOTAL" | head -1 | awk '{printf "%.2f", $2/1024}')
        echo "Memória: ${MEM} MB"

        # CPU percentage
        CPU=$(adb shell top -n 1 2>/dev/null | grep chopcut | awk '{print $9}')
        echo "CPU: ${CPU}%"
    else
        echo "App não está rodando"
    fi

    echo ""
    sleep 1
done
