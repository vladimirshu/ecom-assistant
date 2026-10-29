## Why

The deployed backend will not run Qdrant on its own small instance: the free Qdrant Cloud tier is enough for a 100-product catalog and removes a stateful container, a volume and about 150 MB of RAM from a 1 GB machine. A Qdrant Cloud cluster is reached over gRPC with TLS and an API key. Today the connection is only `host` and `port` (plain text, no key), so the application cannot connect to a managed cluster, and there is no way to pass the key without putting it in a file.

## What Changes

- Make the whole Qdrant connection configurable by environment variables: `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS` and `QDRANT_API_KEY`. The defaults reproduce the current local setup (`localhost`, `6334`, no TLS, no key), so `docker compose up` keeps working unchanged.
- Require that the single `QdrantClient` used by the vector store, the catalog import, the price index initializer and the recommend retriever uses TLS and the API key, so no component bypasses the configuration.
- Never log the API key; log the target host, port and TLS flag once at startup.
- Fail startup with a clear message when TLS or the key is wrong (for example, an `UNAUTHENTICATED` or unreachable cluster) instead of an obscure gRPC stack trace.
- Document the keep-alive and rebuild behavior of the free cluster.

## Capabilities

### New Capabilities
- `vector-store-connection`: environment-driven Qdrant connection (host, port, TLS, API key), one shared client, secret handling, defaults for local development, startup failure behavior.

### Modified Capabilities
<!-- none: no existing requirement changes behavior -->

## Impact

- Code/config: `application.yml` (Qdrant block uses the four variables); possibly one configuration class if the auto-configured client does not honor TLS and key for every consumer; startup logging.
- Dependencies: none new.
- Tests: configuration binding test; the Testcontainers integration tests keep using plain local Qdrant with defaults.
- Operations: the cluster URL and API key live in the instance's secret store (see `aws-backend-host`); local development needs no change.
- Free-tier cluster behavior to verify when deploying: inactive free clusters are suspended after about a week and may be deleted later. The startup import check (`countAsync`, from `remove-mysql`) contacts the cluster on every start, which counts as activity; if the cluster is ever lost, the idempotent import recreates the collection and re-embeds the catalog (about $0.0002).
- Depends on: `remove-mysql` (archived), which made Qdrant the only product store and the startup import its only caller.
