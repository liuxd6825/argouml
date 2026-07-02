"""ArgoUML AI sidecar.

Minimal FastAPI proxy that exposes an OpenAI-compatible
POST /v1/chat/completions endpoint and forwards each request to an
upstream OpenAI-compatible provider (OpenAI, DeepSeek, Moonshot,
Ollama, vLLM, ...) using the openai Python SDK.

Configuration via environment variables:

  OPENAI_API_KEY    Bearer token sent to the upstream provider.
                    Optional when OPENAI_BASE_URL points to a local
                    server (Ollama / vLLM) that does not require auth;
                    a placeholder is used in that case so the openai
                    client can still be constructed.
  OPENAI_BASE_URL   Base URL of the upstream provider. Defaults to
                    OpenAI's public endpoint. Examples:
                      https://api.deepseek.com
                      https://api.moonshot.cn
                      http://localhost:11434/v1   (Ollama)
  OPENAI_MODEL      Default model when the client omits one
                    (currently unused; pass-through only).

The default bind address 127.0.0.1:8765 matches
org.argouml.ai.agent.SidecarConfig.DEFAULT_ENDPOINT.
"""

import os

import openai
import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "").strip()
OPENAI_BASE_URL = os.environ.get("OPENAI_BASE_URL", "").strip() or None
UPSTREAM_TIMEOUT_SEC = 60.0

client = openai.OpenAI(
    api_key=OPENAI_API_KEY or "sk-no-key",
    base_url=OPENAI_BASE_URL,
    timeout=UPSTREAM_TIMEOUT_SEC,
)

app = FastAPI(title="ArgoUML AI sidecar")


@app.post("/v1/chat/completions")
async def chat_completions(request: Request) -> JSONResponse:
    body = await request.json()
    if not isinstance(body, dict):
        raise HTTPException(status_code=400,
                            detail="body must be a JSON object")
    if not isinstance(body.get("messages"), list):
        raise HTTPException(status_code=400,
                            detail="missing required field: messages")
    try:
        completion = client.chat.completions.create(**body)
    except openai.APITimeoutError as exc:
        raise HTTPException(status_code=504,
                            detail=f"upstream timeout: {exc}") from exc
    except openai.APIError as exc:
        raise HTTPException(status_code=502,
                            detail=f"upstream error: {exc}") from exc
    return JSONResponse(content=completion.model_dump())


if __name__ == "__main__":
    if not OPENAI_API_KEY and not OPENAI_BASE_URL:
        print("warning: OPENAI_API_KEY is empty and OPENAI_BASE_URL is unset;"
              " the sidecar will only succeed against a local provider that"
              " does not require auth (e.g. Ollama on http://localhost:11434/v1).")
    uvicorn.run(app, host="127.0.0.1", port=8765)
