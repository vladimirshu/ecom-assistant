## 1. Configuration

- [x] 1.1 Confirm the Spring AI 2.0.2 property names and bind `use-tls` and `api-key` in `application.yml` alongside the existing host and port.
- [x] 1.2 Supply one client bean that uses the bound connection details, enables TLS when requested and omits the header for blank keys. Let Spring AI use that client in its vector store.
- [x] 1.3 Log the host, port, TLS flag and API-key presence once at startup.

## 2. Failure behavior

- [x] 2.1 Translate authentication and connectivity errors on the shared channel, including calls during vector store schema initialization and startup runners.
- [x] 2.2 Verify startup fails on authentication and protocol mismatch. The readiness integration tests verify 503 while startup is in progress and 200 only after it succeeds.

## 3. Tests

- [x] 3.1 Verify default binding, TLS selection and actual gRPC API-key headers for blank and nonblank keys.
- [x] 3.2 Verify all Qdrant consumers receive the same client in the full backend context.
- [x] 3.3 Verify captured startup logs report key presence and that neither startup logs nor a rejected-key exception contain the key.
- [x] 3.4 Run `./gradlew --offline test` with Docker available. The Testcontainers integration tests pass with local plaintext Qdrant.

## 4. Verification and documentation

- [x] 4.1 Document the variables and free-tier suspension, reactivation and rebuild in the project README.
- [x] 4.2 Against a disposable local Qdrant instance over TLS with a required API key, verify catalog import and both indexes on first boot, import skip on restart, health 200 and a search with results. The smoke test used a local certificate and stubbed AI providers.
- [ ] 4.3 Deployment acceptance: repeat the first boot, restart, health and search checks against a managed Qdrant Cloud cluster and record its region. This requires the cluster endpoint and database API key.
