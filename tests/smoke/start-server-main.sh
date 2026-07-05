#!/usr/bin/env bash
# tests/smoke/start-server-main.sh (transient helper)
# Launches org.argouml.application.Main with -Dargouml.headless=true
# so the GUI is skipped but InitHttpServerSubsystem registers the full
# 52 routes (including /d/{d}/usecasediagram/* via InitHttpServerSubsystem
# in target/classes, NOT the stale StandaloneHttpServer copy).
#
# Classpath: target/classes FIRST (load new argouml-ai code), then
# /tmp/argouml-deps/*.jar, then NO argouml-jar-with-dependencies.jar
# (which has stale InitHttpServerSubsystem from Jul 1 that would lose).
#
# Env: BIND (127.0.0.1), PORT (18766).

set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home}
export JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

ARGO_HOME=/Users/lxd/Projects/ai/uml-project/argouml
DEPS_DIR=/tmp/argouml-deps
PIDFILE=/tmp/argouml-deps/.main.pid
LOGFILE=/tmp/argouml-deps/.main.log
ERRFILE=/tmp/argouml-deps/.main.log.err

: > "$LOGFILE"
: > "$ERRFILE"

CP="$ARGO_HOME/src/argouml-ai/target/classes"
for jar in "$DEPS_DIR"/*.jar; do
    CP="$CP:$jar"
done

# Modules list (joined with ;). Quoted to keep the semicolons as one arg.
ARGO_MODULES='org.argouml.notation2.NotationModule;org.argouml.activity2.ActivityDiagramModule;org.argouml.state2.StateDiagramModule;org.argouml.deployment2.DeploymentDiagramModule;org.argouml.sequence2.SequenceDiagramModule;org.argouml.core.propertypanels.module.XmlPropertyPanelsModule;org.argouml.transformer.TransformerModule'

ulimit -f 2097152 2>/dev/null || true

# Pass the modules string as a single -D argument to Java.
nohup java -Xms64m -Xmx1024m -ea \
  "-Dargouml.headless=true" \
  -Dlog4j.configuration=/dev/null \
  -Djava.util.logging.config.file=/dev/null \
  -Duser.language=en \
  -cp "$CP" \
  org.argouml.application.Main \
  > "$LOGFILE" 2> "$ERRFILE" < /dev/null &
disown
sleep 1
PID=$!
echo "$PID" > "$PIDFILE"
echo "[start-main] PID=$PID, log=$LOGFILE, err=$ERRFILE"

# Wait up to 60s for HTTP server to bind
READY=0
for i in $(seq 1 60); do
    if curl -sf -m 2 "http://${BIND}:${PORT}/health" > /dev/null 2>&1; then
        READY=1
        echo "[start-main] /health 200 after ${i}s"
        break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "[start-main] FATAL: process died early" >&2
        tail -20 "$ERRFILE" >&2
        exit 1
    fi
    sleep 1
done

if [ "$READY" != "1" ]; then
    echo "[start-main] FATAL: not ready in 60s" >&2
    tail -20 "$ERRFILE" >&2
    kill -9 "$PID" 2>/dev/null || true
    exit 1
fi

# Give modules 3s to load before accepting traffic.
sleep 3
if ! kill -0 "$PID" 2>/dev/null; then
    echo "[start-main] WARN: process died during module load" >&2
    tail -20 "$ERRFILE" >&2
    exit 1
fi
if ! curl -sf -m 2 "http://${BIND}:${PORT}/health" > /dev/null 2>&1; then
    echo "[start-main] WARN: server died after module load" >&2
    tail -20 "$ERRFILE" >&2
    exit 1
fi

echo "[start-main] READY at PID=$PID"
exit 0
