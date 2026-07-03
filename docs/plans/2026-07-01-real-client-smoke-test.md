# Real-Client Smoke Test — Usage Guide

> **Date:** 2026-07-01
> **Status:** COMPLETE (48/48 pass)
> **Scope:** End-to-end test of argouml-ai HTTP server with real curl clients

## What this is

A bash-based smoke test that:

1. **Starts a real HTTP server** — `StandaloneHttpServer` (a new headless launcher in `argouml.ai.tools` that bypasses the ArgoUML GUI window).
2. **Runs ~48 real curl commands** — no mocks, no in-memory, no JUnit. Every assertion hits a real `127.0.0.1:PORT` endpoint with `curl` and inspects the HTTP status code + JSON body.
3. **Auto-starts and auto-stops** the server if one is not already running.

This complements the JUnit integration test suite (112 tests) by providing a CI-friendly shell entry point that does not need Maven test infrastructure.

## Files

| File | Purpose |
|---|---|
| `src/argouml-ai/src/org/argouml/ai/tools/StandaloneHttpServer.java` | Headless HTTP server launcher (bypasses GUI) |
| `tests/smoke/start-server.sh` | Background server start; waits for /health; writes PID file |
| `tests/smoke/run-smoke-tests.sh` | Main test runner — ~48 curl assertions |
| `tests/smoke/stop-server.sh` | Stops the server; cleans up PID file |
| `docs/plans/2026-07-01-real-client-smoke-report.md` | Latest test report (generated after a clean run) |

## Usage

### One-shot (auto-start + test + auto-stop)

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke
./run-smoke-tests.sh
```

The script:
1. Checks if a server is already running on `$BIND:$PORT` (default 127.0.0.1:18766)
2. If not, calls `start-server.sh` which spawns `StandaloneHttpServer` in the background and waits for `/health` to return 200
3. Runs ~48 curl tests organized in 6 sections
4. Prints a summary with PASS/FAIL counts
5. If the script started the server itself, calls `stop-server.sh` to clean up (via `trap cleanup EXIT`)
6. Exits 0 on all pass, 1 on any fail

### Against an already-running server

```bash
# Terminal 1: start the server manually
cd /Users/lxd/Projects/ai/uml-project/argouml
./tests/smoke/start-server.sh  # uses PORT=18766 BIND=127.0.0.1

# Terminal 2: run the tests (server stays up after)
cd /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke
./run-smoke-tests.sh
./stop-server.sh
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `BIND` | `127.0.0.1` | Interface to bind NanoHTTPD to |
| `PORT` | `18766` | TCP port (8766 is the production default; 18766 avoids conflicts) |
| `DEPS_DIR` | `/tmp/argouml-deps` | Where argouml transitive dependencies are |
| `PIDFILE` | `$(dirname $0)/.server.pid` | Where start-server.sh writes the PID |
| `LOGFILE` | `$(dirname $0)/.server.log` | Where server stdout/stderr goes |

## What the tests cover (48 assertions)

| Section | Tests | Coverage |
|---|---|---|
| Health & Routing | 4 | `/health` 200 + envelope; unknown route 404 + ROUTE_NOT_FOUND |
| Class CRUD | 8 | POST/GET list/GET single/PUT rename/GET renamed/empty name 400/duplicate 409 |
| Attribute CRUD | 4 | POST/GET list/empty name 400/DELETE 204 |
| Operation CRUD | 4 | POST/GET list/empty name 400/DELETE 204 |
| Relationship CRUD | 6 | POST assoc with mult+label, GET list, missing class 404, POST gen, POST dep, DELETE |
| Snapshot | 2 | GET snapshot (validates JSON shape) + unknown diagram 404 |
| Error envelopes | 4 | Malformed JSON 400 + INVALID_BODY, unknown diagram 404 + DIAGRAM_NOT_FOUND, unknown class 404, DELETE 204 |

Plus a trailing `Cleanup` (DELETE the class to leave project empty) which is also asserted.

Total: **48 distinct assertions** (more curl calls if you count the 4 "snapshot shape keys" + the trailing 5 "error envelope" body checks).

## How the test loop is structured

Each test is a curl + 1-2 assertions:

```bash
R=$(req POST /d/Test/classes '{"name":"Customer","x":100,"y":80}')
assert_status "POST /d/Test/classes (Customer) → 201" 201 "$R"
assert_contains "POST Customer body has name" '"name":"Customer"' "$R"
```

- `req` returns `<body>\n<status_code>` (curl `-w "\n%{http_code}"`)
- `assert_status` parses the last line as status code, compares to expected
- `assert_contains` greps the body for a substring (URL-decoded JSON literals are escaped with `\"` in bash double-quotes)
- Both `assert_*` functions increment counters and record failures to `FAILURES[]`

The whole script uses `set -e` only at the top (so failed `req` calls return non-zero but don't abort the script). Test failures are accumulated, not propagated via `exit 1` until the end.

## Why a standalone server instead of `org.argouml.application.Main`?

`Main.main()` spawns the ArgoUML GUI window as part of startup. In a non-interactive shell (no real display, no Mac user session), the AWT window cannot be created, leading to:

- `HeadlessException` if `-Djava.awt.headless=true` is set
- A zombie process if the GUI is forced — the window never opens, the main thread waits for window events that never come

`StandaloneHttpServer` does only what the HTTP server needs:
- `InitializeModel.initializeDefault()` — model facade
- `InitProfileSubsystem().init()` — UML profile
- `InitNotation().init()` + `InitNotationUml().init()` + `InitNotationJava().init()` — notation providers (needed for `FigSingleLineTextWithNotation` to not NPE)
- `makeEmptyProject()` + `new UMLClassDiagram("Test", ns)` + `project.addDiagram(d)` — one class diagram
- `buildRouter()` + `Dispatcher` + `NanoHttpAdapter` + bind

This produces a real, fully-functional HTTP server with all 25 routes active, no GUI, no AWT dependencies.

## CI integration

Add to `.github/workflows/` or similar:

```yaml
- name: Real-client smoke test
  run: |
    cd tests/smoke
    ./run-smoke-tests.sh
```

Exit code 0 = all pass; non-zero = fail. The script handles server lifecycle automatically.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `port 18766 already in use` | Stale server | `./stop-server.sh` or `pkill -9 -f StandaloneHttpServer` |
| `server did not become ready in 30s` | JVM slow to initialize (Profile loading) | Check `.server.log`; may need to bump the timeout in `start-server.sh` |
| `Cannot invoke "NotationProvider" because "this.notationProvider" is null` | Forgot the InitNotation calls | Use the latest `StandaloneHttpServer.java` (it includes them) |
| Test 47/48, only POST assoc fails | Same as above | Same fix |
| 500 on mutations | EDT contention in Navigator refresh (F2 documented) | Visual clients see the mutation; HTTP client may see 500; consider polling |

## See also

- `docs/plans/2026-07-01-real-client-smoke-report.md` — actual run results
- `docs/plans/2026-07-01-rest-integration-tests-coverage.md` — JUnit coverage matrix (complementary)
- `docs/plans/2026-06-30-http-server-design.md` — design doc
- `docs/plans/2026-06-30-http-server-plan.md` — implementation plan
- `docs/plans/2026-06-30-http-server-smoke-test.md` — F2 initial smoke test (manual)
