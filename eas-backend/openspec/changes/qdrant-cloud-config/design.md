## Context

`application.yml` configures Qdrant for Spring AI as:

    spring.ai.vectorstore.qdrant: { collection-name: products, host: ${QDRANT_HOST:localhost}, port: ${QDRANT_PORT:6334}, initialize-schema: true }

`host` and `port` are already environment-driven. `useTls` and `apiKey` are not set. Four components use Qdrant: the Spring AI `QdrantVectorStore` (embedding on `add`), `ProductImportService` (count and delete via `QdrantClient`), `PricePayloadIndexInitializer` (`QdrantClient`) and `QdrantRecommendDocumentRetriever` (`QdrantClient`). They all inject the `QdrantClient` bean produced by the Spring AI Qdrant auto-configuration, so one client configuration reaches all of them.

Qdrant Cloud endpoints are `https://<cluster-id>.<region>.<cloud>.cloud.qdrant.io`; the gRPC API is on port 6334 with TLS and the key sent in an `api-key` header.

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
`spring.ai.vectorstore.qdrant.host/port/use-tls/api-key` mapped to `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS` (default `false`) and `QDRANT_API_KEY` (default empty). The implementer must confirm the exact property names of Spring AI 2.0.2 (the Qdrant starter's `use-tls` and `api-key` properties are expected). An empty key means "send no key"; the local container needs none.
- Alternative: a Spring profile `qdrant-cloud` with a second set of properties. Rejected: four variables with local defaults are simpler than profile files, and the instance profile `aws` (see `aws-backend-host`) only needs to set variables.

**2. One shared `QdrantClient`; verify, do not assume.**
The auto-configured client should honor TLS and the key for all consumers. If it does not (or the starter builds the client differently from the one the retriever uses), define one `QdrantClient` bean built from the same properties via `QdrantGrpcClient.newBuilder(host, port, useTls)` plus the API key, and let the vector store use it. Add a startup test that all four consumers receive the same bean.
- Alternative: build a second client only for the retriever. Rejected: two clients means two places for the TLS flag.

**3. Startup log line without secrets.**
Log `Qdrant: host=<host> port=<port> tls=<bool> apiKey=<present|absent>` once. The key value, and any header containing it, never appears in logs. The existing `HttpClientConfig` logging applies only to the HTTP LLM calls, not gRPC.

**4. Clear failure on a bad connection.**
The first Qdrant call is the price index initializer (an `ApplicationRunner`) and the import check. A failure there must fail startup with a message naming the host and the probable cause (`UNAUTHENTICATED` → API key rejected; `UNAVAILABLE` → host/port/TLS wrong) rather than a stack trace only. This also keeps the readiness endpoint (`service-health`) DOWN, which the storefront shows as a closed shop.

**5. Free-cluster behavior accepted.**
The import check at every start doubles as a keep-alive. Weekends have no start, which is well inside the suspension window. If the cluster is suspended or deleted anyway, a new cluster is created by hand, the variables are updated and the next start rebuilds the collection. This is documented rather than automated.

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

## Open Questions

- Whether the Spring AI auto-configured client exposes the API key for the direct-client consumers; resolved by the verification task, with the bean override as the fallback.
