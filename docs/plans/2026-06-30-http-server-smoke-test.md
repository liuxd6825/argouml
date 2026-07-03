# ArgoUML AI HTTP Server - End-to-end smoke test report (Task 28)

**Date:** 2026-07-01
**Branch / status:** local working tree on top of master; full reactor builds;
argouml-ai test suite 537/537 passing.
**Scope:** boot ArgoUML with the embedded HTTP server, drive every MVP REST
endpoint end-to-end against the running JVM, and document which assertions
hold and which ones reveal a real-world issue.

> **TL;DR - result: PARTIAL.**
> The HTTP server binds, every route is reachable, the read-only endpoints
> (`GET`) return JSON envelopes, and every model mutation triggered through
> the server is correctly persisted in the ArgoUML MDR model (confirmed by
> follow-up `GET` snapshots). However, mutation requests (`POST`,
> `PUT`, `DELETE` that adds model elements) frequently return
> `Empty reply from server` to curl, because ArgoUML's Swing EDT is busy
> refreshing the Navigator / Explorer tree after each mutation. The
> handler eventually runs and the model is updated, but curl's default
> timeout (and even 60s / 120s / 180s) is reached before NanoHTTPD can
> write the response - the client has already torn the socket down. See
> *Known issue* at the bottom for diagnosis and a workaround.

---

## Build & launch commands (verbatim)

Per `AGENTS.md`, the runnable `argouml-jar-with-dependencies.jar` is broken
(it does not include external deps). The pre-made launcher
`src/argouml-app/src/bin/run-argouml.sh` does the same thing as the steps
below; this report documents the manual recipe so anyone can reproduce.

```bash
# 1. Install argouml-ai into local Maven
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o

# 2. Build the reactor + the runnable jar (argouml-build module)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -pl src/argouml-build -am package -DskipTests -o

# 3. Copy all transitive deps
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -pl src/argouml-app -Dmaven.test.skip=true dependency:copy-dependencies \
  -DoutputDirectory=/tmp/argouml-deps -o

# 4. Run argouml (argouml-ai jar is bundled into the new runnable jar
#    because we built with -am; the dep-cache jar is harmless to keep
#    on the classpath).
CP="src/argouml-build/target/argouml-jar-with-dependencies.jar"
for j in /tmp/argouml-deps/*.jar; do CP="$CP:$j"; done
nohup /Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -Xms64m -Xmx1024m -cp "$CP" \
  org.argouml.application.Main > /tmp/argouml-smoke.log 2>&1 &
```

A successful boot logs (in `/tmp/argouml-smoke.log`):

```
argouml-ai HTTP server bound to 127.0.0.1:8766
        (enabled=true, timeoutSec=30, maxBodyBytes=1048576)
```

> **Note:** the startup log line is intentionally added by
> `InitHttpServerSubsystem.init()` during this smoke test so the operator
> can verify the server bound without having to probe the port. The line
> uses `System.out` (matches the surrounding `nohup ... > log 2>&1`
> pattern) and ASCII-only, so it does not violate the `ISO-8859-1` source
> encoding rule.

A fresh empty project exposes two default diagrams:

```
用例图   (Use case diagram, kind=usecase)
类图     (Class diagram,   kind=class)
```

The remainder of this report uses `类图` as the target diagram.
URL-encoding once: `%E7%B1%BB%E5%9B%BE`.

---

## Test results

Each step below records: command, expected HTTP status (and envelope),
actual status, observed response. The full unedited curl output is in
`/tmp/argouml-smoke-responses/full-smoke.log` and
`/tmp/argouml-smoke-responses/full-smoke2.log`.

### Step 1 - GET /health

```
curl -sS -m 60 http://127.0.0.1:8766/health
```

Expected: `200` + envelope `{ok:true, data:{ok:true, enabled:true, project:<name>}}`.
Actual: **HTTP 200**, body:

```
{"ok":true,"data":{"ok":true,"enabled":true,"project":"\u672a\u547d\u540d"}}
```

PASS.

### Step 2 - GET /project/diagrams

```
curl -sS -m 60 http://127.0.0.1:8766/project/diagrams
```

Expected: `200` + envelope with two diagrams (`用例图`, `类图`).
Actual: **HTTP 200**, body:

```
{"ok":true,"data":[
  {"name":"用例图","kind":"usecase","namespace":"..."},
  {"name":"类图","kind":"class","namespace":"..."}
]}
```

PASS. The class diagram's `name` is `类图` (Chinese; URL-encoded as
`%E7%B1%BB%E5%9B%BE`).

### Step 3 - POST /d/{类图}/classes {name:"Customer"}

```
curl -sS -m 60 -X POST http://127.0.0.1:8766/d/%E7%B1%BB%E5%9B%BE/classes \
  -H 'Content-Type: application/json' \
  -d '{"name":"Customer","x":100,"y":80,"isAbstract":false}'
```

Expected: `201` + envelope `{"ok":true,"data":{"name":"Customer"}}`.
Actual: **curl (52) Empty reply from server / HTTP 000**.

Handler did run (verified by follow-up `GET /d/{类图}/classes` in
Step 6 which returns `Customer` in the list - see *Known issue* for why
curl did not see the response). The handler log line
`handler-returned status=201` is visible in `/tmp/argouml-smoke.log`,
followed by NanoHTTPD's `Could not send response to the client /
Socket closed` because the client disconnected first.

PARTIAL (handler OK, response delivery blocked).

### Step 4 - POST /d/{类图}/classes/Customer/attributes {name:"id"}

```
curl -sS -m 60 -X POST http://127.0.0.1:8766/d/%E7%B1%BB%E5%9B%BE/classes/Customer/attributes \
  -H 'Content-Type: application/json' \
  -d '{"name":"id","type":"long","visibility":"private"}'
```

Expected: `201` + envelope describing the new attribute.
Actual: **curl (52) Empty reply / HTTP 000**, same shape as Step 3.
Follow-up snapshot (Step 5) confirms `id:long` was added.

PARTIAL.

### Step 5 - GET /project/diagrams/{类图}/snapshot

```
curl -sS -m 60 http://127.0.0.1:8766/project/diagrams/%E7%B1%BB%E5%9B%BE/snapshot
```

Expected: `200` + envelope with `diagram`, `classes`, `associations`.
Actual: **HTTP 200**, body:

```
{"ok":true,"data":{
  "diagram":{"id":"d25e2a451","type":"Class","namespace":"\u65e0\u6807\u9898\u6a21\u578b"},
  "classes":[{"name":"Customer","attrs":["id:long"],"ops":[]}],
  "associations":[]}}
```

Note: route is `/project/diagrams/{d}/snapshot` (under the project
prefix), not `/d/{d}/snapshot`. Both `classSvc` lookups work; only the
snapshot endpoint is namespaced under the project path because it is
diagram-kind-agnostic.

PASS.

### Step 6 - GET /d/{类图}/classes

```
curl -sS -m 60 http://127.0.0.1:8766/d/%E7%B1%BB%E5%9B%BE/classes
```

Expected: `200` + envelope listing `Customer` with its attributes.
Actual: **HTTP 200**, body:

```
{"ok":true,"data":[
  {"name":"Customer","isAbstract":false,"stereotypeNames":[],"attributes":["id:long"],"operations":[]}
]}
```

Confirms Steps 3 + 4 mutated the model successfully.

PASS.

### Step 7 - create Order + association

```
curl -sS -m 60 -X POST .../classes -d '{"name":"Order","x":300,"y":80}'
curl -sS -m 60 -X POST .../associations \
  -d '{"classA":"Customer","classB":"Order","multA":"1","multB":"0..*"}'
```

Expected: `201` + envelope for both.
Actual: both POSTs returned **curl (52) Empty reply / HTTP 000**.
Re-running with 5s sleep between calls and again later with 15s sleep
finally got the association created - `GET /d/{类图}/associations`
returned:

```
[{"a":"Customer","b":"Order","multA":"1","multB":"0..*","labelA":"","labelB":""}]
```

So the handler chain works; only the HTTP response delivery is delayed.

PARTIAL (handler OK, response delivery blocked).

### Step 8 - error cases

| Case                                   | Expected | Actual |
|----------------------------------------|----------|--------|
| `GET /d/no-such/classes`               | 404 DIAGRAM_NOT_FOUND | **HTTP 404** `{"ok":false,"error":{"code":"DIAGRAM_NOT_FOUND","message":"diagram not found: no-such"}}` PASS |
| `POST /d/{类图}/classes` body=`{}`      | 400 INVALID_NAME      | curl (52) Empty reply / HTTP 000 (handler ran, response lost). Re-run later returned **HTTP 400** with the expected envelope. PARTIAL. |
| `POST /d/{类图}/classes` duplicate `Customer` | 409 DUPLICATE_CLASS | curl (52) Empty reply / HTTP 000 (handler ran, response lost). Re-run later returned **HTTP 409**. PARTIAL. |

### Step 9 - Ctrl+Z undoes the latest mutation

Could not be exercised in this headless smoke test (no GUI). The wiring
is in place: `ClassDiagramService.createClassImpl` (and every other
mutation path) opens an `UndoScope` which calls
`Project.getUndoManager().startInteraction(...)`, so the editor's
standard UndoManager sees the mutation. Verified by reading
`src/argouml-ai/src/org/argouml/ai/application/common/UndoScope.java:46`
(`p.getUndoManager().startInteraction(label)`) and confirming the same
`UndoManager` instance is what `org.argouml.uml.diagram.ui.ActionUndo`
listens to. (This is exactly the integration that the existing 537
`argouml-ai` JUnit 3 tests cover - see the
`TestUndoScopeRegistration` test in
`src/argouml-ai/tests/org/argouml/ai/infrastructure/undo/`.)

### Step 10 - DELETE /d/{类图}/classes/Order

```
curl -sS -m 60 -X DELETE -i http://127.0.0.1:8766/d/%E7%B1%BB%E5%9B%BE/classes/Order
```

Expected: `204 No Content`, empty body.
Actual:

```
HTTP/1.1 204 No Content
Content-Type: application/json; charset=utf-8
Date: ...
Connection: keep-alive
Content-Length: 0
```

PASS. DELETE returns promptly because deleting a class does not
trigger the heavy Explorer-tree recomputation that adding one does -
see *Known issue* below.

### Step 11 - stop ArgoUML

```
kill 29281
```

PASS (process exits, port 8766 released).

---

## Pass/fail summary

| Step | Endpoint                                | HTTP status seen | Verdict  |
|------|-----------------------------------------|------------------|----------|
| 1    | GET  /health                            | 200              | PASS     |
| 2    | GET  /project/diagrams                  | 200              | PASS     |
| 3    | POST /d/{类图}/classes                   | 000 (handler 201)| PARTIAL  |
| 4    | POST /d/{类图}/classes/Customer/attrs    | 000 (handler 201)| PARTIAL  |
| 5    | GET  /project/diagrams/{类图}/snapshot  | 200              | PASS     |
| 6    | GET  /d/{类图}/classes                  | 200              | PASS     |
| 7a   | POST /d/{类图}/classes (Order)          | 000 (handler 201)| PARTIAL  |
| 7b   | POST /d/{类图}/associations             | 000 (handler 201)| PARTIAL  |
| 8a   | GET  /d/no-such/classes                 | 404 DIAGRAM_NOT_FOUND | PASS |
| 8b   | POST /d/{类图}/classes empty body       | 400 INVALID_NAME (on retry) | PARTIAL |
| 8c   | POST /d/{类图}/classes duplicate        | 409 DUPLICATE_CLASS (on retry) | PARTIAL |
| 10   | DELETE /d/{类图}/classes/Order          | 204              | PASS     |

**Overall: PARTIAL.** Every CRUD handler runs end-to-end, the model
state is correct, every error path produces the right exception code.
The HTTP response delivery from `POST` / mutation endpoints is blocked
by the Swing EDT being busy with Explorer tree refreshes triggered by
the model mutation itself.

---

## Anomalies / things that didn't go as the task script described

1. **Default diagram names are Chinese, not `untitledModel`.** ArgoUML
   creates two default diagrams named `用例图` (use case) and `类图`
   (class). The smoke test substitutes the real name (URL-encoded) -
   this is fine, but anyone re-running should be aware.

2. **Snapshot route is under `/project/diagrams/{d}/snapshot`, not
   `/d/{d}/snapshot`.** Plan Task 28 Step 4 used `/d/{name}/snapshot`
   but the actual router registers the snapshot under the
   project-scoped namespace because the handler is diagram-kind
   agnostic (`SnapshotHandler` is wired in `buildRouter` line 137 of
   `InitHttpServerSubsystem.java`). Same data, different path.

3. **`InitHttpServerSubsystem.init()` did not log a bind line.** The
   subsystem was silent on stdout (only JUL-level info messages went to
   the log). I added a single `System.out.println("argouml-ai HTTP
   server bound to ...")` line in
   `src/argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java:103`
   to make verification trivial. The line is ASCII-only and uses no
   project state, so it does not need translation.

4. **`Dispatcher.readBody()` had a blocking bug.** The original loop
   was `while ((n = in.read(tmp)) > 0)` which deadlocks on the second
   `read()` because the socket is in keep-alive mode and there is no
   more data to read (NanoHTTPD 2.3.1's HTTPSession does not drain the
   body upfront). Fixed in
   `src/argouml-ai/src/org/argouml/ai/inbound/rest/common/Dispatcher.java:184`
   to bound reads by `Content-Length`. A 5-line standalone reproducer
   (NanoHTTPD only, no ArgoUML) confirmed the fix.

5. **POST responses frequently return `Empty reply from server`.** See
   *Known issue* below.

---

## Known issue: ArgoUML EDT contention blocks HTTP responses

The mutation handlers (every `POST /d/.../classes`, `POST /d/.../associations`,
etc.) call `org.argouml.ai.inbound.rest.common.Dispatcher.serve()`
which dispatches the handler onto the Swing EDT via
`SwingUtilities.invokeAndWait`. After the handler mutates the MDR model
the EDT propagates the change to the **Navigator / Explorer tree**, which
performs an MDR `getAllModelElementsOfKind(...)` walk. On a fresh
project that walk takes several seconds; on a non-empty project it can
take 30s+.

`jstack` capture during a `POST /d/.../classes` shows the EDT parked in:

```
"AWT-EventQueue-0" RUNNABLE
  at org.netbeans.mdr.storagemodel.CompositeCollection$CompositeIterator.hasNext(...)
  at org.argouml.model.mdr.ModelManagementHelperMDRImpl.getAllModelElementsOfKind(...)
  at org.argouml.ui.explorer.rules.GoModelElementToContainedLostElements.getChildren(...)
  at org.argouml.ui.explorer.ExplorerTreeModel.collectChildren(...)
  at org.argouml.ui.explorer.ExplorerTreeModel.updateChildren(...)
  at org.argouml.ui.explorer.ExplorerTreeModel$ExplorerUpdater.run(...)
  at java.awt.EventQueue.dispatchEventImpl(...)
```

Meanwhile the curl client (and any non-GUI HTTP client) is sitting on
`read()`. Once it gives up, the connection is closed, and when our
handler eventually finishes and NanoHTTPD tries to write the response it
sees `Socket closed` (logged at JUL `SEVERE` level):

```
七月 01, 2026 1:35:34 上午 fi.iki.elonen.NanoHTTPD$Response send
严重: Could not send response to the client
java.net.SocketException: Socket closed
```

`GET` endpoints are unaffected because they do not mutate the model
and therefore do not trigger the heavy Explorer refresh.

### Workarounds

- **For automation:** use a client that retries with backoff, or run
  with `-m 180` and accept the latency. The handler does complete, the
  model is updated correctly. Subsequent `GET` calls return the new
  state immediately. (See Steps 5 / 6 / final snapshot above.)
- **For interactive use:** use the GUI - Ctrl+Z undoes the mutation
  normally because the handler is wired through `UndoScope` and
  ArgoUML's standard `UndoManager`. The HTTP client sees no response,
  but the GUI does, so the user can verify each step visually.
- **For a real fix:** either (a) move the model mutation off the EDT
  onto a background MDR worker thread, or (b) suspend the
  Explorer-tree listener while the REST request is running and re-fire
  it once the response is built. Both are non-trivial changes to
  ArgoUML core and out of scope for the HTTP-server MVP. Filed as a
  follow-up in the design doc.

---

## Manual reproduction recipe

```bash
# 1. Build & install
mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o
mvn -pl src/argouml-build -am package -DskipTests -o
mvn -pl src/argouml-app -Dmaven.test.skip=true \
  dependency:copy-dependencies -DoutputDirectory=/tmp/argouml-deps -o

# 2. Launch
CP="src/argouml-build/target/argouml-jar-with-dependencies.jar"
for j in /tmp/argouml-deps/*.jar; do CP="$CP:$j"; done
nohup /Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java \
  -Xms64m -Xmx1024m -cp "$CP" \
  org.argouml.application.Main > /tmp/argouml-smoke.log 2>&1 &
# wait ~18s for startup, look for "argouml-ai HTTP server bound to ..."
grep "argouml-ai HTTP server bound" /tmp/argouml-smoke.log

# 3. Smoke test
ENC=$(python3 -c 'import urllib.parse;print(urllib.parse.quote("类图"))')
curl -sS -m 60 -w "\nHTTP %{http_code}\n" http://127.0.0.1:8766/health
curl -sS -m 60 -w "\nHTTP %{http_code}\n" http://127.0.0.1:8766/project/diagrams
curl -sS -m 60 -w "\nHTTP %{http_code}\n" \
  -X POST "http://127.0.0.1:8766/d/${ENC}/classes" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Customer","x":100,"y":80}'
# (POST may return Empty reply; verify via GET below.)
sleep 5
curl -sS -m 60 -w "\nHTTP %{http_code}\n" \
  "http://127.0.0.1:8766/d/${ENC}/classes"      # confirms Customer
curl -sS -m 60 -w "\nHTTP %{http_code}\n" \
  "http://127.0.0.1:8766/project/diagrams/${ENC}/snapshot"
curl -sS -m 60 -w "\nHTTP %{http_code}\n" \
  -i "http://127.0.0.1:8766/d/no-such/classes"   # 404 DIAGRAM_NOT_FOUND

# 4. Stop
pkill -f argouml.application.Main
```

---

## References

- Design doc: `docs/plans/2026-06-30-http-server-design.md`
- Plan (Tasks 1-29): `docs/plans/2026-06-30-http-server-plan.md`
- `argouml-ai` test suite: **537/537 passing** (run with
  `mvn -f src/argouml-ai/pom.xml surefire:test -o`)
- Captured curl logs: `/tmp/argouml-smoke-responses/full-smoke.log`
  and `/tmp/argouml-smoke-responses/full-smoke2.log`
- ArgoUML startup log: `/tmp/argouml-smoke.log`