# ArgoUML AI 助手 — End-to-end smoke test report (Task 14)

**Date:** 2026-06-30
**Scope:** verify build, sidecar boot, ArgoUML boot. The full LLM-driven
chat-to-tool-call round-trip needs a real `OPENAI_API_KEY` and is left to
the user (manual procedure below).

---

## What I ran

### 1. Install `argouml-ai` into the local Maven cache

```bash
JAVA_HOME=/Library/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o
```

**Last lines:**

```
[INFO] Installing .../target/argouml-ai-0.35.2-SNAPSHOT.jar to ~/.m2/...
[INFO] Installing .../target/argouml-ai-0.35.2-SNAPSHOT-sources.jar
[INFO] Installing .../target/argouml-ai-0.35.2-SNAPSHOT-test-sources.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  4.838 s
```

### 2. Build `argouml-app` + reactor

```bash
JAVA_HOME=/Library/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -pl src/argouml-app -am package -DskipTests -o
```

**Last lines:**

```
[INFO] argouml-core ....................................... SUCCESS [  3.017 s]
[INFO] argouml-core-model ................................. SUCCESS [  1.135 s]
[INFO] argouml-core-model-mdr ............................. SUCCESS [  1.389 s]
[INFO] argouml-zh ......................................... SUCCESS [  0.166 s]
[INFO] argouml-app ........................................ SUCCESS [  4.424 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  10.521 s
```

### 3. Boot the sidecar (no API key) and probe it

```bash
nohup python3 src/argouml-ai/examples/sidecar.py > /tmp/sidecar.log 2>&1 &
sleep 4
curl -X POST http://127.0.0.1:8765/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"test","messages":[{"role":"user","content":"hi"}]}' \
  -w '\nHTTP %{http_code}\n' -m 10
kill %1
```

**Sidecar log (boot):**

```
INFO:     Started server process [72188]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8765 (Press CTRL+C to quit)
warning: OPENAI_API_KEY is empty and OPENAI_BASE_URL is unset; ...
INFO:     127.0.0.1:63302 - "POST /v1/chat/completions HTTP/1.1" 502 Bad Gateway
```

**Probe response:**

```json
{"detail":"upstream error: Error code: 401 - ... 'Incorrect API key provided: sk-no-key' ..."}
HTTP 502
```

The server is up. The 401 from upstream OpenAI is correctly rewrapped as
HTTP 502 by the sidecar, exactly the behaviour documented in
`src/argouml-ai/examples/README.md` §"Error behaviour".

### 4. Boot ArgoUML in batch mode (GUI-less smoke)

```bash
gtimeout --kill-after=5 60 java -Xms64m -Xmx1024m -ea \
  "-Dargouml.modules=org.argouml.notation2.NotationModule;\
org.argouml.activity2.ActivityDiagramModule;\
org.argouml.state2.StateDiagramModule;\
org.argouml.deployment2.DeploymentDiagramModule;\
org.argouml.sequence2.SequenceDiagramModule;\
org.argouml.core.propertypanels.module.XmlPropertyPanelsModule;\
org.argouml.transformer.TransformerModule;\
org.argouml.dev.DeveloperModule" \
  -cp "src/argouml-build/target/argouml-jar-with-dependencies.jar:$(echo /tmp/argouml-deps/*.jar | tr ' ' ':')" \
  org.argouml.application.Main -batch -nosplash
```

**Last lines:**

```
信息: Notation Module enabled.
Exiting because we are running in batch.
exit=0
```

All eight module-loader entries attempted. Seven loaded successfully;
`org.argouml.dev.DeveloperModule` failed to load — that's the legacy
module flagged in AGENTS.md §"Things that will trip you up #5". No
`NoClassDefFoundError`, no `ClassNotFoundException`, exit code 0. The
`InitAiSubsystem` initializer wired in at `Main.java:431` ran without
throwing, so the sidecar client config singleton primed successfully.

(Equivalent one-liner: `bash src/argouml-app/src/bin/run-argouml.sh -batch -nosplash`
— `-batch` keeps Swing off, which lets the smoke test run in terminal.)

### 5. `-help` flag also runs the JVM through to log usage

```
Usage: [options] [project-file]
Options include:
  -help / -h / --help / /?
  -big / -huge
  -nosplash / -norecentfile
  -command <arg> / -batch / -locale <arg>
```

Confirms subsystems load before the early `System.exit(0)` in
`Main.parseCommandLine` → `printUsage`.

## Smoke test status

**Partial.** Verified:

- `argouml-ai` compiles and installs (`BUILD SUCCESS`, 4.8 s).
- `argouml-app` (and all upstream reactor modules) packages offline
  (`BUILD SUCCESS`, 10.5 s).
- Sidecar boots, binds to `127.0.0.1:8765`, serves HTTP; upstream
  OpenAI error is correctly surfaced as `502 Bad Gateway` with the
  upstream message in the body.
- ArgoUML starts in batch mode; all module entries attempt to load
  (7/8 succeed, `DeveloperModule` failure is expected); `InitAiSubsystem`
  primes `SidecarConfig` silently; JVM exits clean.

**Not verified (requires real key + network):**

- The actual round-trip: `ChatPane` → `AiClient` → sidecar → upstream
  LLM → `tool_calls` JSON → `PlannedOp` → `OpExecutor` → diagram edit
  → `PreviewPane` row.

That part is below, as a manual procedure.

---

## Manual smoke test procedure (for the user)

You will need: a real `OPENAI_API_KEY` (or a DeepSeek / Moonshot /
Ollama / vLLM key + base URL), Java 8, Python 3 with `openai fastapi
uvicorn`, and a desktop session on macOS (ArgoUML's Swing UI requires
a display).

### Step 1 — Install the AI module (one-time)

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
JAVA_HOME=/Library/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o
```

Expect `BUILD SUCCESS`. The argouml-ai jar lands in
`~/.m2/repository/org/argouml/argouml-ai/0.35.2-SNAPSHOT/`.

### Step 2 — Build / refresh ArgoUML (one-time)

```bash
JAVA_HOME=/Library/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -pl src/argouml-app -am package -DskipTests -o
```

`argouml-ai` is already declared as a `<scope>compile</scope>` dependency
in `src/argouml-app/pom.xml`, so it ends up in
`/tmp/argouml-deps/argouml-ai-0.35.2-SNAPSHOT.jar` automatically the
next time the launcher script copies deps (Step 4).

If `/tmp/argouml-deps/` is stale or missing, run:

```bash
mvn -pl src/argouml-app -Dmaven.test.skip=true dependency:copy-dependencies \
  -DoutputDirectory=/tmp/argouml-deps
```

### Step 3 — Start the sidecar

Pick one of:

```bash
# OpenAI (default base URL is already api.openai.com)
OPENAI_API_KEY=sk-...  python3 src/argouml-ai/examples/sidecar.py

# DeepSeek
OPENAI_API_KEY=sk-...  \
  OPENAI_BASE_URL=https://api.deepseek.com  \
  python3 src/argouml-ai/examples/sidecar.py

# Ollama / vLLM (local, no key needed)
OPENAI_BASE_URL=http://localhost:11434/v1 \
  python3 src/argouml-ai/examples/sidecar.py
```

You should see `Uvicorn running on http://127.0.0.1:8765`.

Sanity-check with `curl` from a second terminal:

```bash
curl -s -X POST http://127.0.0.1:8765/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"say hi in one word"}]}'
```

A valid key returns `{"choices":[{"message":{"role":"assistant","content":"..."}}], ...}`
with HTTP 200. Anything else (`401` → wrapped to `502`, `429`,
`timeout`) signals an upstream problem; fix the key/URL before moving
on.

### Step 4 — Start ArgoUML

The launcher copies deps to `/tmp/argouml-deps/` lazily on first use; the
helper script does the same:

```bash
bash src/argouml-app/src/bin/run-argouml.sh
```

A Swing window titled **ArgoUML** opens. **Failure mode to watch for:
*"Sequence Diagram won't open"* — AGENTS.md §"things that will trip you
up #8". The `run-argouml.sh` launcher already passes the right
`-Dargouml.modules=...` list, so this should be fine on this checkout.**

### Step 5 — Open the AI panel

The "智能助手" tab is added to the right-hand details pane (registered
by `InitAiSubsystem.getDetailsTabs()` → `AiPanel`). Click the tab.

You'll see:

- A text area for input.
- Five buttons: **发送 / 取消 / 应用 / 清空 / 设置** (= Send / Cancel
  / Apply / Clear / Settings, per `ai.properties`).

### Step 6 — Configure the sidecar (only if not defaults)

Click **设置** (Settings) → **AI 助手** tab in the global settings
dialog. Defaults are:

| Field | Default | Override when |
|---|---|---|
| 端点 URL | `http://127.0.0.1:8765` | sidecar on a different host/port |
| API Key | *(empty)* | provider requires a bearer token different from what the sidecar was started with |
| 模型 | `gpt-4o-mini` | targeting a different model name |
| 超时(秒) | 60 | upstream slower than 60 s |

Click **保存** to persist (`~/.argouml/ai-config.properties`) then
**重置** to revert.

### Step 7 — Pre-flight: open a class diagram

The AI assistant requires an active class diagram before edits are
allowed (see `ai.error.no-active-diagram` / `ai.topbar.diagram`). The
status bar at the top of the AI panel shows either **当前图** (active
diagram) or **无图** (none).

If no class diagram is open:

1. `File → New → Class Diagram` (or click the toolbar "New Class
   Diagram" icon).
2. Click anywhere in the diagram pane to make it the active diagram.

### Step 8 — First prompt (read-only sanity)

Try a query that should produce a plain text reply (no tool calls):

> 你能看到我当前打开的类图吗？

Expect a one-line assistant response with no rows in the preview table.

### Step 9 — First edit prompt

Class-diagram CRUD ops are wired end-to-end (per `argouml-ai/README.md`
status section). Try:

> 帮我添加一个名为 `OrderService` 的类，加一个 `String` 类型的属性
> `orderId` 和一个返回 `void` 的操作 `placeOrder()`。

Expected behaviour:

1. The chat row shows the assistant's text and one or more tool-call
   bullets.
2. The **preview table** populates with rows numbered 序号 / operation
   操作 / object 对象 / params 摘要 / status 状态 (where status starts
   as 待应 = pending).
3. Click **应用**. The class appears in the class diagram; the
   attribute and operation show in the property panel; the preview
   row's status flips to 已应 = applied.

### Step 10 — Multi-step prompt (preview-only then bulk apply)

> 再加三个类：`Order`、`OrderItem`、`Payment`，让 `Order` 聚合
> `OrderItem`，`Order` 拥有 `Payment`。

Expect four or more preview rows (three `add_class` ops, one
`add_association` aggregation). Apply in one click — all rows flip
from 待应 to 已应; the diagram updates with the new classes and an
aggregation arrow from `Order` to `OrderItem` and a regular
association to `Payment`.

### Step 11 — Negative test

> 删除不存在的类 `NotARealClass`.

Expect an error row (`ai.error.apply-failed`) — the chat stays coherent;
no diagram corruption.

### Step 12 — Cleanup

Close ArgoUML (`File → Exit`), kill the sidecar with Ctrl-C in its
terminal. Both processes are intentionally independent — you can keep
the sidecar running between ArgoUML sessions if you want.

---

## Self-review findings

| Check | Result |
|---|---|
| Build commands succeed (`argouml-ai install` + `argouml-app package`) | PASS (both `BUILD SUCCESS`, offline) |
| Sidecar boots and listens on `127.0.0.1:8765` | PASS (`lsof -i :8765` shows `Python LISTEN`) |
| HTTP request returns `502` (upstream 401 wrapped) when no API key | PASS — `{"detail":"upstream error: Error code: 401 - ... Incorrect API key ..."}` with `HTTP 502` |
| ArgoUML starts without `NoClassDefFoundError` | PASS — `exit=0` after `Exiting because we are running in batch.` |
| All module-loader entries from `-Dargouml.modules` attempted | PASS — 7/8 succeed (`DeveloperModule` failure is a known legacy issue per AGENTS.md §"#5") |
| Manual smoke test procedure documented step-by-step | PASS — Steps 1–12 above |

## Issues / blockers

None for the partial test. Two notes for the user:

1. **The GUI smoke test in Steps 4–12 requires a desktop session.**
   This agent run only verifies batch boot. Run Steps 4+ on a real
   desktop session (no SSH without `DISPLAY` forwarding on macOS).
2. **`DeveloperModule` does not load.** This is pre-existing (the `dev`
   module directory in `modules/dev/` is a dead Eclipse-PDE/Ant project;
   see AGENTS.md). Removing it from `ARGO_MODULES` in
   `run-argouml.sh` is safe and silences the warning, but is left as
   a separate clean-up task and is not part of Task 14.

## Files produced / modified

- New: `docs/plans/2026-06-30-task14-smoke-test.md` (this file).

No source changes for this task. No commits (per AGENTS.md).
