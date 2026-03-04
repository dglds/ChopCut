#!/usr/bin/env python3
"""
Monitor de Atividade ChopCut - Python
Mostra na tela E salva em arquivo simultaneamente
"""

import subprocess
import sys
import re
import signal
from datetime import datetime

# Configurações
VERBOSIDADE = "--2"
ARQUIVO = "chopcut_monitor.log"


def write_to_file(timestamp, msg, type_):
    line = f"{timestamp} [{type_}] {msg}"
    try:
        with open(ARQUIVO, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except:
        pass


def init_file():
    header = "═══ MONITOR CHOPCUT ═══\n"
    header += f"Iniciado: {datetime.now().isoformat()}\n"
    header += f"Verbosidade: {VERBOSIDADE}\n"
    header += "═══ LOGS ═══\n"

    try:
        with open(ARQUIVO, "w", encoding="utf-8") as f:
            f.write(header)
    except:
        print("⚠ Erro ao criar arquivo de log")


def get_pid():
    try:
        result = subprocess.run(
            ["adb", "shell", "pidof", "com.chopcut"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        pid = result.stdout.strip()
        if pid:
            return pid
        raise Exception("ChopCut não está em execução")
    except subprocess.TimeoutExpired:
        raise Exception("Timeout ao buscar PID do ChopCut")
    except Exception as e:
        raise Exception(f"Erro ao buscar PID: {str(e)}")


def get_filter():
    filters = {
        "--1": r"(Cache HIT|Cache MISS|Segment.*COMPLETED|Erro|FAILED)",
        "--2": r"(extractSegment STARTED|Extracting segment|Segment.*COMPLETED|Strip.*loaded|Cache HIT|Cache MISS|Cached segment)",
        "--3": r"(extract|Cache|Strip|loaded|Batch)",
    }
    return filters.get(VERBOSIDADE, filters["--2"])


def signal_handler(signum, frame):
    print("\n═══ ENCERRADO ═══")
    print(f"Logs salvos em: {ARQUIVO}")
    try:
        footer = "\n═══ ENCERRADO ═══\n"
        footer += f"Finalizado: {datetime.now().isoformat()}\n"
        with open(ARQUIVO, "a", encoding="utf-8") as f:
            f.write(footer)
    except:
        pass
    sys.exit(0)


def monitor_logs(pid):
    print("Ctrl+C para encerrar")
    print("")

    init_file()

    # Limpa logcat anterior
    subprocess.run(["adb", "logcat", "-c"], capture_output=True, timeout=5)

    # Inicia monitoramento
    try:
        logcat = subprocess.Popen(
            ["adb", "logcat", "-v", "time", f"--pid={pid}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        signal.signal(signal.SIGINT, signal_handler)

        pattern = re.compile(get_filter(), re.IGNORECASE)
        line_pattern = re.compile(
            r"^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+\S+/[^:]+:\s+(.*)$"
        )

        if logcat.stdout:
            for line in logcat.stdout:
                if not line.strip():
                    continue

                # Filtro baseado na verbosidade
                if not pattern.search(line):
                    continue

                # Extrai timestamp e mensagem
                match = line_pattern.match(line)
                if not match:
                    continue

                time_ = match.group(2)
                msg = match.group(3)
                timestamp = time_

                # Detecta tipo de operação
                type_ = "INFO"

                if re.search(
                    r"extractSegment STARTED|Extracting segment|Batch|extractBatch",
                    msg,
                    re.IGNORECASE,
                ):
                    type_ = "EXTRAÇÃO"
                    print(f"{timestamp} ⚡ {msg}")
                    write_to_file(timestamp, msg, type_)
                elif re.search(
                    r"COMPLETED|drawBitmap|Strip.*loaded|frames.*extraídos|Segment.*BATCH MODE",
                    msg,
                    re.IGNORECASE,
                ):
                    type_ = "MONTAGEM"
                    print(f"{timestamp} 🖼️  {msg}")
                    write_to_file(timestamp, msg, type_)
                elif re.search(
                    r"Cache HIT|Cache MISS|Cached|saveToCache|Saving|Cache PUT",
                    msg,
                    re.IGNORECASE,
                ):
                    type_ = "CACHE"
                    print(f"{timestamp} 💾 {msg}")
                    write_to_file(timestamp, msg, type_)
                elif re.search(r"Erro|FAILED", msg, re.IGNORECASE):
                    type_ = "ERRO"
                    print(f"{timestamp} ❌ {msg}")
                    write_to_file(timestamp, msg, type_)
                else:
                    print(f"{timestamp} ▸ {msg}")
                    write_to_file(timestamp, msg, "INFO")

    except KeyboardInterrupt:
        signal_handler(signal.SIGINT, None)
    except Exception as e:
        print(f"⚠ Erro no monitoramento: {str(e)}")


def monitor():
    try:
        pid = get_pid()
        print(f"✓ ChopCut ativo (PID: {pid})")
        monitor_logs(pid)
    except Exception as e:
        print(f"⚠ {str(e)}")
        print("")
        print("Verifique se o app ChopCut está em execução.")
        sys.exit(1)


if __name__ == "__main__":
    monitor()
