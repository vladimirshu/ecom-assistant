## 1. Backend configuration

- [x] 1.1 Add `use-tls: ${QDRANT_USE_TLS:false}` and `api-key: ${QDRANT_API_KEY:}` under `spring.ai.vectorstore.qdrant` in `eas-backend/src/main/resources/application.yml`, keeping the existing `host`/`port`/`collection-name`/`initialize-schema` unchanged

## 2. Documentation

- [x] 2.1 Add `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS`, and `QDRANT_API_KEY` (with comments explaining the Qdrant Cloud usage) to `.env.example`
- [x] 2.2 Document the Qdrant Cloud option (host, port, TLS, API key env vars) in the repository root `Readme.md` under "Running Locally", keeping the local-Qdrant quickstart as the default path

## 3. Verify

- [x] 3.1 Verify the default binding with all four variables unset. Verify import and search against local plaintext Qdrant in the backend's Testcontainers suite.
- [x] 3.2 Verify the four variables over real gRPC against a disposable TLS-enabled Qdrant instance that requires an API key. Verify import on first boot, import skip on restart, health 200 and a search that returns products. The smoke test used a local certificate and stubbed AI providers.

Managed Qdrant Cloud endpoint verification is pending deployment with a cluster and database API key. See the backend change's verification tasks.
