# ArgoUML AI sidecar — examples

A minimal FastAPI proxy that exposes an OpenAI-compatible
`POST /v1/chat/completions` endpoint on `http://127.0.0.1:8765` and
forwards every request to an upstream OpenAI-compatible provider
(OpenAI, DeepSeek, Moonshot, Ollama, vLLM, ...). The Java
`org.argouml.ai.agent.AiClient` class already targets this endpoint.

## Install

The sidecar needs Python 3.8+ and three packages:

```bash
pip install openai fastapi uvicorn
```

## Run

Set your provider key (and, optionally, the base URL for a
non-OpenAI provider) and start the script:

```bash
# OpenAI
OPENAI_API_KEY=sk-xxx python sidecar.py

# DeepSeek
OPENAI_API_KEY=sk-xxx \
  OPENAI_BASE_URL=https://api.deepseek.com \
  python sidecar.py

# Moonshot
OPENAI_API_KEY=sk-xxx \
  OPENAI_BASE_URL=https://api.moonshot.cn \
  python sidecar.py

# Ollama (local, no key required)
OPENAI_BASE_URL=http://localhost:11434/v1 python sidecar.py
```

The sidecar binds to `127.0.0.1:8765` and prints a warning on startup
if neither `OPENAI_API_KEY` nor `OPENAI_BASE_URL` is set.

## Verify

A successful `chat.completions` call returns the upstream provider's
JSON response verbatim. The simplest smoke test is a one-line `curl`:

```bash
curl -X POST http://127.0.0.1:8765/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}'
```

If the sidecar is running and your API key is valid, the response is a
`{"id":...,"choices":[{"message":{"role":"assistant","content":"..."}}],...}`
JSON document, identical to what the upstream provider returns
directly.

## Wire it into ArgoUML

The sidecar's bind address matches
`org.argouml.ai.agent.SidecarConfig.DEFAULT_ENDPOINT`, so no
configuration is required for the default case. Open
**工具 → 设置 → AI 助手** if you need to point ArgoUML at a different
host or port, or to set a per-user model name.

## Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `OPENAI_API_KEY` | No (only if upstream requires auth) | empty | Bearer token forwarded to the upstream provider. A placeholder is used when empty so the local sidecar can still be constructed. |
| `OPENAI_BASE_URL` | No | empty (= `https://api.openai.com/v1`) | Base URL of the upstream provider. Set to a DeepSeek / Moonshot / Ollama / vLLM endpoint to route around OpenAI. |
| `OPENAI_MODEL` | No | `gpt-4o-mini` | Default model name — currently unused, the sidecar is pass-through. The Java client sends the model on every request. |

## Error behaviour

| Status | Cause |
|---|---|
| `400` | Request body is not a JSON object, or `messages` is missing / not a list. |
| `502` | Upstream returned an error (rate limit, invalid key, etc.). The message contains the upstream error. |
| `504` | Upstream timed out after 60 seconds. |
