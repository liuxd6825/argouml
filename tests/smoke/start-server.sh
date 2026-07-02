#!/usr/bin/env bash
# tests/smoke/start-server.sh
# 在后台启动 StandaloneHttpServer，把 PID 写到 .server.pid
# 等待 /health 200 OK 才返回退出码 0
#
# 内存保护 (2026-07-02 修复):
#   1) JVM 抑制 log4j / JUL 输出到 stderr (减少 OpenIDE ActiveQueue 死循环噪声)
#   2) 启动时清理旧大 log (>200MB 自动删)
#   3) 启动时 truncate 输出文件
#   4) stderr 重定向到独立 .err 文件，加 ulimit 2GB 硬限防止无界增长
set -e

BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
PIDFILE=${PIDFILE:-$(dirname "$0")/.server.pid}
LOGFILE=${LOGFILE:-$(dirname "$0")/.server.log}
ERRFILE=${LOGFILE}.err

# 启动时清旧大 log（>200MB）
echo "[start-server] 清理旧 log（>200MB）..."
for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-*.log /tmp/argouml-launcher/.server.log; do
    [ -f "$f" ] && [ "$(stat -f%z "$f" 2>/dev/null || echo 0)" -gt 209715200 ] && rm -f "$f" && echo "  removed: $f"
done

# Argouml 完整 classpath: target/classes + 测试 classes + /tmp/argouml-deps
ARGOUML_AI_DIR="$(cd "$(dirname "$0")/../../src/argouml-ai" && pwd)"
ARGOUML_BUILD_TARGET="../../src/argouml-build/target/argouml-jar-with-dependencies.jar"
DEPS_DIR=${DEPS_DIR:-/tmp/argouml-deps}

CP="${ARGOUML_AI_DIR}/target/classes:${ARGOUML_AI_DIR}/target/test-classes"
for jar in "${DEPS_DIR}"/*.jar; do
    CP="${CP}:${jar}"
done

# 防呆: 如果上次的 server 没杀干净，先清掉
if [ -f "${PIDFILE}" ]; then
    OLDPID=$(cat "${PIDFILE}" 2>/dev/null || true)
    if [ -n "${OLDPID}" ] && kill -0 "${OLDPID}" 2>/dev/null; then
        echo "[start-server] Killing stale server PID=${OLDPID}"
        kill -9 "${OLDPID}" 2>/dev/null || true
    fi
    rm -f "${PIDFILE}"
fi
# 也清掉任何残留的 StandaloneHttpServer
pkill -9 -f "StandaloneHttpServer" 2>/dev/null || true
sleep 1

# 防呆: 端口必须空闲
if lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN 2>/dev/null | grep -q LISTEN; then
    echo "[start-server] ERROR: port ${PORT} already in use" >&2
    lsof -nP -iTCP:"${PORT}" -sTCP:LISTEN >&2
    exit 1
fi

# 内存保护: 限制输出文件总大小到 2GB
ulimit -f 2097152 2>/dev/null || true

# 启动时 truncate 输出文件
: > "${LOGFILE}" 2>/dev/null || true
: > "${ERRFILE}" 2>/dev/null || true

echo "[start-server] Starting StandaloneHttpServer on ${BIND}:${PORT}"
# JVM 参数抑制 OpenIDE 的 JUL/log4j 噪声到 stderr
#   -Dlog4j.configuration=/dev/null → log4j 拒绝读 → 静默
#   -Djava.util.logging.config.file=/dev/null → JUL 同理
nohup java -Xms64m -Xmx1024m \
  -Dlog4j.configuration=/dev/null \
  -Djava.util.logging.config.file=/dev/null \
  -cp "${CP}" \
  org.argouml.ai.tools.StandaloneHttpServer "${PORT}" "${BIND}" \
  > "${LOGFILE}" 2> "${ERRFILE}" < /dev/null &
PID=$!
echo "${PID}" > "${PIDFILE}"
echo "[start-server] PID=${PID}, log=${LOGFILE}, err=${ERRFILE}"

# 等待 /health (最长 30s)
for i in $(seq 1 30); do
    if curl -sf -m 2 "http://${BIND}:${PORT}/health" > /dev/null 2>&1; then
        echo "[start-server] /health returned 200 after ${i}s"
        exit 0
    fi
    if ! kill -0 "${PID}" 2>/dev/null; then
        echo "[start-server] ERROR: server process died" >&2
        tail -30 "${LOGFILE}" >&2
        exit 1
    fi
    sleep 1
done

echo "[start-server] ERROR: server did not become ready in 30s" >&2
tail -30 "${LOGFILE}" >&2
exit 1
