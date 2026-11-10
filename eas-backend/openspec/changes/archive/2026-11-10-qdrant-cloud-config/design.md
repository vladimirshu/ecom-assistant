## Context

`application.yml` configures Qdrant for Spring AI as:

    spring.ai.vectorstore.qdrant: { collection-name: products, host: ${QDRANT_HOST:localhost}, port: ${QDRANT_PORT:6334}, initialize-schema: true }

`host` and `port` are already environment-driven. `useTls` and `apiKey` are not set. Four components use Qdrant: the Spring AI `QdrantVectorStore` (embedding on `add`), `ProductImportService` (count and delete via `QdrantClient`), `PricePayloadIndexInitializer` (`QdrantClient`) and `QdrantRecommendDocumentRetriever` (`QdrantClient`). They all inject the `QdrantClient` bean produced by the Spring AI Qdrant auto-configuration, so one client configuration reaches all of them.

Qdrant Cloud uses the hostname `<cluster-id>.<region>.<provider>.cloud.qdrant.io` with gRPC on port 6334, TLS enabled, and a database API key in the `api-key` header.

## Goals / Non-Goals

**Goals:**
- Connect to Qdrant Cloud with environment variables only, with no change for local development.
- Keep one client for every Qdrant consumer so TLS and the key cannot be forgotten in one place.
- Keep the key out of logs and out of the repository.

**Non-Goals:**
- Qdrant on the application host, or a Qdrant cluster per environment.
- Rotating keys or reading them from a secret manager inside the application (the instance injects them as environment variables; see `aws-backend-host`).
- Collection migration or backups: the catalog is rebuildable from the bundled CSV.

## Decisions

**1. Use the Spring AI properties, driven by four environment variables.**
`spring.ai.vectorstore.qdrant.host/port/use-tls/api-key` are Spring AI 2.0.2 properties mapped to `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS` (default `false`) and `QDRANT_API_KEY` (default empty). The local container needs no key.
- Alternative: a Spring profile `qdrant-cloud` with a second set of properties. Rejected: four variables with local defaults are simpler than profile files, and the instance profile `aws` (see `aws-backend-host`) only needs to set variables.

**2. Use one shared `QdrantClient`.**
Spring AI's default client sends an empty API-key header when the bound value is empty. The backend supplies a single client using Spring AI's bound settings and connection details. It attaches the API-key header only for a nonblank value. Spring AI still constructs the vector store with the shared client. The same bean also serves catalog listing, the category index initializer and the health indicator.

**3. Startup log line without secrets.**
Log `Qdrant: host=<host> port=<port> tls=<bool> apiKey=<present|absent>` once. The key value, and any header containing it, never appears in logs. The existing `HttpClientConfig` logging applies only to the HTTP LLM calls, not gRPC.

**4. Clear failure on a bad connection.**
The vector store checks for the collection during bean initialization, before the startup runners. A gRPC interceptor on the shared channel handles both phases. Authentication errors (`UNAUTHENTICATED` and `PERMISSION_DENIED`) point to the API key and its permissions. Connection failures and timeouts point to the host, port and TLS settings. The client uses a 30-second RPC deadline and skips the SDK version probe, which runs before it attaches the API key. Spring Boot only reports ready after startup succeeds, and the public health response exposes only the aggregate status.

**5. Free-cluster behavior accepted.**
Each startup checks whether the catalog already matches the current revision. Qdrant suspends free clusters after one week of inactivity and deletes inactive clusters after four weeks. Reactivate a suspended cluster in the console. If it has been deleted, configure a replacement and restart to rebuild from the bundled CSV.

## Risks / Trade-offs

- [TLS flag defaults to `false`, and a cloud host without TLS fails with an obscure gRPC error] → Decision 4's message names the TLS setting; the deployment secrets set it explicitly.
- [Latency to a remote cluster adds to every search (retrieval is one gRPC call)] → Pick the cluster region next to the instance region; one extra round trip is small next to two LLM calls.
- [Free cluster is shared/limited (1 GB, small CPU)] → 100 products with 1536-dimension vectors is well under 1 MB.
- [API key leaking via the `/api/v1/health` response or logs] → Health shows no detail; the log line shows only `present|absent`.
- [Suspended free cluster at the time of a visit] → Readiness reports DOWN, the storefront shows the closed sign, and the next start recreates what is missing; this is an accepted limitation of the free tier.

## Migration Plan

1. Deploy with no variables set: behavior is identical to today (local, plain text).
2. Create the free cluster, store host and key in the instance's secret store, set `QDRANT_HOST`, `QDRANT_PORT=6334`, `QDRANT_USE_TLS=true`, `QDRANT_API_KEY`.
3. Start the application: the import creates and fills the `products` collection and the price index.
4. Rollback: unset the variables to return to the local Qdrant.
