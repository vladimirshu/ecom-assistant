## Why

The backend is about to sit behind CloudFront, which routes by URL path prefix: static files go to S3, the wake-up function to a Lambda, and everything under `/api/v1/` to the backend. The search endpoint is currently `GET /search`, which does not fit that routing without URL rewriting. The storefront also needs a way to ask "is the backend ready?" before it sends a search: the instance is stopped at night and on weekends, and a plain 200/503 readiness endpoint is how the page decides whether to show the "closed" door sign.

## What Changes

- **BREAKING**: `GET /search` moves to `GET /api/v1/search`. Parameters, validation, responses (200, 400, 422) and behavior are unchanged. The old path is removed without a redirect; there are no other clients.
- Add `GET /api/v1/health`: HTTP 200 only when the application is ready to serve searches (startup runners, including the catalog import and the price index creation, have finished and the vector store is reachable); HTTP 503 otherwise. It makes no LLM or embedding call.
- Add Spring Boot Actuator, with only the `health` endpoint exposed, served under the `/api/v1` base path.
- Update `openapi.yaml`, controller and integration tests, and the two spec scenarios that name the old path.
- The HTTP method stays GET: the API URLs are only called by the storefront script and are never linked or crawled.

## Capabilities

### New Capabilities
- `service-health`: the readiness endpoint contract (status codes, readiness semantics, no downstream LLM calls, no sensitive detail).

### Modified Capabilities
- `search-input-validation`: the search endpoint path is `/api/v1/search` (missing-conversation-id scenario).
- `rag-product-filtering`: the end-to-end search scenario calls `GET /api/v1/search`.

## Impact

- Code: `controller/ProductsController` (class-level `@RequestMapping("/api/v1")`), `config/SecurityConfig` (health stays permitted; no change expected), `application.yml` (management settings), `build.gradle.kts` (`spring-boot-starter-actuator`), `openapi.yaml` (path, new health path, server URL unchanged).
- Tests: every `get("/search")` / `.path("/search")` in the controller and integration tests changes to `/api/v1/search` (about 25 call sites); new health tests.
- API: breaking path change for `/search`; new `/api/v1/health`.
- Cross-project contract: the storefront (`eas-frontend`) calls `/api/v1/search` and probes `/api/v1/health`; CloudFront behaviors `/api/v1/*` → backend origin.
- No in-flight change mentions the old path: `conversation-memory` and `storefront-search-api` are already archived and merged into the specs listed above.
