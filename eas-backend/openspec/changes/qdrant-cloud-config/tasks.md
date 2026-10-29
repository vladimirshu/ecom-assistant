## 1. Configuration

- [ ] 1.1 In `application.yml`, bind `spring.ai.vectorstore.qdrant.use-tls` to `${QDRANT_USE_TLS:false}` and `api-key` to `${QDRANT_API_KEY:}`; keep `host` and `port` as they are; confirm the exact Spring AI 2.0.2 property names
- [ ] 1.2 Verify that the auto-configured `QdrantClient` honors TLS and the API key; if not, add a `QdrantClient` bean built from the same properties (`QdrantGrpcClient.newBuilder(host, port, useTls)` plus the key) and let the vector store use it
- [ ] 1.3 Log `Qdrant: host=<host> port=<port> tls=<bool> apiKey=<present|absent>` once at startup, without the key value

## 2. Failure behavior

- [ ] 2.1 Translate gRPC `UNAUTHENTICATED`/`PERMISSION_DENIED` and `UNAVAILABLE` from the first Qdrant calls (`PricePayloadIndexInitializer`, `ProductImportService`) into startup errors that name the host and the probable cause, without the key
- [ ] 2.2 Check that the error path leaves `/api/v1/health` not ready (503) until the connection works

## 3. Tests

- [ ] 3.1 Binding test: unset variables give `localhost`/`6334`/TLS off/no key; set variables give the cloud values; an empty key is treated as absent
- [ ] 3.2 Context test: `ProductImportService`, `PricePayloadIndexInitializer`, `QdrantRecommendDocumentRetriever` and the vector store receive the same `QdrantClient` bean
- [ ] 3.3 Log test: with a key configured, captured startup logs contain `apiKey=present` and never the key value
- [ ] 3.4 Run `./gradlew test`; Testcontainers integration tests still use the local defaults

## 4. Verification and documentation

- [ ] 4.1 Document the variables and the free-tier behavior (suspension after about a week of inactivity, rebuild from the CSV on a new cluster) in the project README
- [ ] 4.2 Against a real free cluster: start with an empty cluster (catalog imported, price index created), restart (import skipped), search returns products; record the cluster region used
