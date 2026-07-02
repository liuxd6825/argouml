# argouml-ai

AI assistant subsystem for [ArgoUML](../..).

This module is **not** part of the Maven reactor (see `pom.xml` of the
root project) because it depends on `argouml-app` at compile time, which
would form a cycle. Install it standalone and let `argouml-app` pick it
up from the local repository (`~/.m2/repository/org/argouml/...`).

## Install (dev workflow)

```bash
mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o
```

`-f` points Maven directly at this module's POM (the module is
intentionally excluded from the root reactor — see `pom.xml` of the
root project, which means `mvn -pl src/argouml-ai ...` would error out
with "Could not find the selected project in the reactor").

`-o` switches Maven to **offline** mode and assumes the parent POM
`org.argouml:parentpom:0.35.5-SNAPSHOT` and the `argouml` /
`argouml-model` artifacts are already cached locally.

To pick the module up at runtime, declare a `<dependency>` on
`org.argouml:argouml-ai` inside `src/argouml-app/pom.xml` and rebuild
`argouml-build`, or run ArgoUML with the jar on the classpath:

```bash
java -cp src/argouml-build/target/argouml-jar-with-dependencies.jar:\
$(echo /tmp/argouml-deps/*.jar | tr ' ' ':'):\
~/.m2/repository/org/argouml/argouml-ai/0.35.2-SNAPSHOT/argouml-ai-0.35.2-SNAPSHOT.jar \
  org.argouml.application.Main
```

## Start the sidecar (optional)

The AI features talk to a small FastAPI sidecar over HTTP. Install
once and run with your OpenAI key in the environment:

```bash
pip install fastapi uvicorn openai && \
  OPENAI_API_KEY=sk-xxx python src/argouml-ai/examples/sidecar.py
```

The default endpoint is `http://127.0.0.1:8765/v1/chat/completions`.

For full setup instructions, environment variables (including
`OPENAI_BASE_URL` for DeepSeek / Moonshot / Ollama) and a `curl`
smoke test, see [`examples/README.md`](examples/README.md).

## Configure the endpoint

Inside ArgoUML, open **工具 → 设置 → AI 助手** and set:

- **Endpoint** — base URL of the sidecar (default `http://127.0.0.1:8765`).
  The Java client appends `/v1/chat/completions` itself.
- **API Key** — the bearer token sent to the sidecar
  (default: the `OPENAI_API_KEY` value the sidecar was started with).
- **Model** — model name passed through to the upstream provider
  (default `gpt-4o-mini`).

Settings persist per-user in `~/.argouml/ai-config.properties`.

## HTTP Server

A loopback-only HTTP server that exposes the class-diagram CRUD as REST
endpoints. This is the same code path the AI `OpExecutor` uses, so the
two clients always see a consistent model.

- **Default endpoint:** `http://127.0.0.1:8766`
- **Bind address:** `127.0.0.1` only (loopback). The server is not
  reachable from other hosts even if the firewall were open.
- **How to disable / change port:** Tools -> Settings -> HTTP Server.
  The setting is persisted to `~/.argouml/http-server.properties` and
  re-read at the next startup.
- **Quick check that the server is up:**

  ```bash
  curl -s http://127.0.0.1:8766/health
  ```

  Expected response:

  ```json
  {"ok":true,"data":{"ok":true,"enabled":true,"project":"<name>"}}
  ```

  Create a class on the default class diagram:

  ```bash
  curl -X POST http://127.0.0.1:8766/d/<diagram-name>/classes \
    -H 'Content-Type: application/json' \
    -d '{"name":"Order","x":200,"y":120}'
  ```

  Diagram names that contain non-ASCII characters must be
  percent-encoded in the URL.

- **Full route table, request/response shapes, error codes:** see
  `docs/plans/2026-06-30-http-server-design.md` and the smoke-test
  report at `docs/plans/2026-06-30-http-server-smoke-test.md`.

## Status

MVP complete. Class-diagram CRUD (add/modify/delete classes, interfaces, attributes, operations, associations, generalizations, dependencies) is wired end-to-end. Sequence and use-case diagram support is not yet implemented.
