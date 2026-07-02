#!/usr/bin/env bash
# tests/smoke/stop-server.sh
# 杀掉 start-server.sh 启动的 server，清理 pidfile
set -e

PIDFILE=${PIDFILE:-$(dirname "$0")/.server.pid}

if [ -f "${PIDFILE}" ]; then
    PID=$(cat "${PIDFILE}" 2>/dev/null || true)
    if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
        echo "[stop-server] Stopping server PID=${PID}"
        # 先 SIGTERM 让 shutdown hook 跑 (优雅停止 NanoHTTPD)
        kill -TERM "${PID}" 2>/dev/null || true
        for i in 1 2 3 4 5; do
            sleep 1
            if ! kill -0 "${PID}" 2>/dev/null; then
                echo "[stop-server] Stopped cleanly after ${i}s"
                break
            fi
        done
        # 强杀兜底
        if kill -0 "${PID}" 2>/dev/null; then
            echo "[stop-server] Force-killing PID=${PID}"
            kill -9 "${PID}" 2>/dev/null || true
        fi
    else
        echo "[stop-server] Stale PID file, no live process"
    fi
    rm -f "${PIDFILE}"
else
    echo "[stop-server] No PID file at ${PIDFILE}"
fi

# 兜底: 任何残留的 StandaloneHttpServer 进程
pkill -9 -f "StandaloneHttpServer" 2>/dev/null || true
echo "[stop-server] Done"
