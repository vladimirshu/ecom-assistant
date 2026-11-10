## Context

The backend is a Spring Boot 4 / Spring AI 2.0.2 application. Its Qdrant connection is configured in `eas-backend/src/main/resources/application.yml` under `spring.ai.vectorstore.qdrant` and auto-wired by `QdrantVectorStoreAutoConfiguration`, which builds an `io.qdrant.client.QdrantClient` from those properties. Today the file only sets `collection-name`, `host`, `port`, and `initialize-schema`; TLS and API key are not exposed, so the client always connects over plain gRPC with no auth.

The AWS host (see `aws-backend-host`) must reach a remote Qdrant Cloud cluster, which requires TLS and an API key. Local development (docker-compose) uses a plain local Qdrant.

## Goals / Non-Goals

**Goals:**
- Expose `use-tls` and `api-key` as environment-driven settings.
- Keep the current local default (plain gRPC, `localhost:6334`) when no variables are set.
- Document the Qdrant Cloud variables so switching is configuration-only.

**Non-Goals:**
- No schema/collection migration and no import changes.
- No changes to embeddings or retrieval logic.
- No provisioning of the Qdrant Cloud cluster itself (done in the cloud console, out of band).

## Decisions

### Decision 1: Reuse Spring AI binding and configure the shared client
Spring AI 2.0.2 binds the four settings and uses the same client for its vector store as the application does for direct calls. Its default client attaches an API-key header for an empty string. The backend provides a single client bean that omits blank keys and supplies safe connection diagnostics. Spring AI still binds the settings and builds the vector store. The backend's `vector-store-connection` spec covers the client behavior.

### Decision 2: Environment variable names
Use `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS`, `QDRANT_API_KEY` to match the names already referenced in `aws-backend-host` (the SSM parameters map to these). The port remains the gRPC port (6334 by default), which is what Qdrant Cloud exposes for gRPC.
- Alternative considered: reuse generic datasource-style names — rejected; these names are already established in the AWS design.

### Decision 3: Keep local defaults
`${QDRANT_HOST:localhost}`, `${QDRANT_PORT:6334}`, `${QDRANT_USE_TLS:false}`, and `${QDRANT_API_KEY:}` keep docker-compose/local behavior unchanged when nothing is set.
- The explicit empty default for `api-key` (rather than a bare `${QDRANT_API_KEY}`) avoids Spring failing on an unresolvable placeholder when run outside docker-compose.

### Decision 4: Document, don't force, docker-compose override
`docker-compose.yml` keeps hardcoding `QDRANT_HOST: qdrant` for the local stack; the cloud path is documented in `.env.example`/README for "individual services" runs and for the AWS host. This avoids changing the local quickstart.
- Alternative considered: parameterize docker-compose with `${QDRANT_HOST:-qdrant}` — deferred to `aws-backend-host` if a cloud run must go through compose.

## Risks / Trade-offs

- TLS or credential misconfiguration fails startup with a diagnostic that identifies the endpoint and the setting to check. After startup, the readiness endpoint reports `DOWN` if the Qdrant collection becomes unreachable.
- [API key in the environment] → Mitigation: on AWS the key is injected at container start from SSM and never baked into the image (see `aws-backend-host`); locally it lives in `.env`, which is git-ignored.
- [Port assumption (6334 gRPC)] → Mitigation: Qdrant Cloud serves gRPC on 6334 with TLS; the port remains configurable via `QDRANT_PORT` if a provider differs.

## Migration Plan

1. Add the two properties and docs (no default behavior change).
2. Verify local `docker compose up` still works unchanged.
3. To point at Qdrant Cloud, set `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS=true`, `QDRANT_API_KEY` and restart; rollback is reverting the env vars.

## Open Questions

- None currently; the Qdrant Cloud cluster URL and key are supplied out of band (see `aws-backend-host`).
