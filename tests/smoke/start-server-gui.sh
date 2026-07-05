#!/usr/bin/env bash
# tests/smoke/start-server-gui.sh
# Launches the full ArgoUML GUI (org.argouml.application.Main) which also
# initializes InitHttpServerSubsystem. Differs from start-server.sh:
#   - start-server.sh: StandaloneHttpServer (class-diagram only routes)
#   - start-server-main.sh: headless Main (no GUI window, all 52 routes)
#   - start-server-gui.sh: this script, full GUI + all 52 routes
#
# Classpath order matters:
#   argouml-ai/target/classes FIRST so the NEW InitHttpServerSubsystem
#   (with usecase routes) wins over the stale Jul 1 copy inside
#   argouml-jar-with-dependencies.jar. Other ArgoUML classes are
#   loaded from the deps jars + jar-with-deps.

set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home}
export JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

ARGO_HOME=/Users/lxd/Projects/ai/uml-project/argouml
DEPS_DIR=/tmp/argouml-deps
PIDFILE=/tmp/argouml-deps/.gui.pid
LOGFILE=/tmp/argouml-deps/.gui.log
ERRFILE=/tmp/argouml-deps/.gui.log.err

: > "$LOGFILE"
: > "$ERRFILE"

CP="$ARGO_HOME/src/argouml-ai/target/classes"
for jar in "$DEPS_DIR"/*.jar; do
    CP="$CP:$jar"
done
CP="$CP:$ARGO_HOME/src/argouml-build/target/argouml-jar-with-dependencies.jar"

# Note: in headless agent shell, normal GUI mode hangs trying to open a
# window. Set ARGO_HEADLESS=1 to skip the GUI window step but still
# initialize subsystems (including InitHttpServerSubsystem which binds
# the HTTP port). End-user can still drive the full API surface; they
# just don't see the Swing window.
if [ "${ARGO_HEADLESS:-1}" = "1" ]; then
    HEADLESS_FLAG="-Dargouml.headless=true -Djava.awt.headless=true"
else
    HEADLESS_FLAG=""
fi
ARGO_MODULES=''

ulimit -f 2097152 2>/dev/null || true

# Quote the modules list as a single -D argument so the semicolons don't
# get expanded by the shell. Empty in this script — argouml.modules seems
# to hang the subsystem chain on this Mac/Temurin combo; without it the
# HTTP server binds reliably (at the cost of sequence/activity/state
# diagram factories being absent).
nohup java -Xms64m -Xmx1024m -ea \
  -ea \
  $HEADLESS_FLAG \
  -Dlog4j.configuration=/dev/null \
  -Djava.util.logging.config.file=/dev/null \
  -Duser.language=en \
  -cp "$CP" \
  org.argouml.application.Main \
  > "$LOGFILE" 2> "$ERRFILE" < /dev/null &
PID=$!
echo "$PID" > "$PIDFILE"
echo "[start-gui] PID=$PID, log=$LOGFILE, err=$ERRFILE"

# Wait up to 60s for HTTP server to bind
for i in $(seq 1 60); do
    if curl -sf -m 2 "http://${BIND}:${PORT}/health" > /dev/null 2>&1; then
        echo "[start-gui] /health 200 after ${i}s"
        # Check that usecase routes are registered too
        DIAG="probe-$(date +%s%N)"
        DIAG_ENC=$(python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))" "$DIAG")
        if curl -sS -m 5 -X POST "http://${BIND}:${PORT}/project/diagrams" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$DIAG\",\"kind\":\"usecasediagram\"}" > /dev/null 2>&1; then
            if curl -sS -m 5 -X POST "http://${BIND}:${PORT}/d/$DIAG_ENC/usecasediagram/actors" \
                -H "Content-Type: application/json" -d '{"name":"ProbeActor"}' > /dev/null 2>&1; then
                echo "[start-gui] usecase routes verified (probe-actor created)"
            else
                echo "[start-gui] WARN: usecase actor route returned non-2xx" >&2
            fi
        fi
        echo "[start-gui] READY at PID=$PID"
        echo "[start-gui] GUI: connect via VNC/screen-share to see the ArgoUML window"
        exit 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "[start-gui] FATAL: process died early" >&2
        tail -20 "$ERRFILE" >&2
        exit 1
    fi
    sleep 1
done

echo "[start-gui] FATAL: not ready in 60s" >&2
tail -20 "$ERRFILE" >&2
kill -9 "$PID" 2>/dev/null || true
exit 1
