## Why

The backend can only reach Qdrant on `localhost:6334` over plain gRPC (no TLS, no auth), which is correct for the local `docker-compose.yml` but cannot connect to a managed Qdrant Cloud cluster. Qdrant Cloud requires TLS and an API key, and its host/port differ from the local defaults. The AWS host (see `aws-backend-host`) must use a remote Qdrant Cloud cluster, so the backend needs to support it without code changes.

## What Changes

- Make the Qdrant connection fully environment-driven: add `use-tls` and `api-key` settings under `spring.ai.vectorstore.qdrant`, wired to `QDRANT_USE_TLS` and `QDRANT_API_KEY`, alongside the existing `QDRANT_HOST`/`QDRANT_PORT`.
- Keep the current local defaults (plain gRPC to `localhost:6334`) so local development is unchanged.
- Document the Qdrant Cloud variables in `.env.example` and the README so pointing the stack at a remote cluster is a configuration-only change (no code or image rebuild).

## Capabilities

### New Capabilities
- `qdrant-cloud-config`: the backend's Qdrant connection supports TLS and an API key and is fully driven by environment variables, so it can target either local Qdrant or a remote Qdrant Cloud cluster without code changes.

### Modified Capabilities
<!-- none: no existing spec covers the Qdrant connection; this is the first capability for it -->

## Impact

- `eas-backend/src/main/resources/application.yml`: add `use-tls` and `api-key` under `spring.ai.vectorstore.qdrant`.
- Backend Qdrant client configuration: omit blank API-key headers and surface startup connection errors without exposing credentials.
- `.env.example`: document `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_API_KEY`, `QDRANT_USE_TLS`.
- `Readme.md`: document the Qdrant Cloud option alongside local Qdrant.
- No dependency or public API changes. Local `docker-compose.yml` behavior is unchanged by default.
