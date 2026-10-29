## Context

`ProductsController` maps `@GetMapping("/search")` with no common prefix. Startup work happens in two runners: `PricePayloadIndexInitializer` (an `ApplicationRunner` creating the payload index on `price`) and `DataInitializerRunner` (a `CommandLineRunner` calling `ProductImportService.importSampleProducts()`, which checks and fills the Qdrant collection). The web server accepts requests before these runners finish, so a request arriving during the first-boot import would run against an empty or partial collection.

The service runs on a small EC2 instance that is started on demand (the storefront shows a closed sign when it is down). The page needs a cheap, unambiguous signal for "ready to search". The security config permits every request, so no auth change is needed.

## Goals / Non-Goals

**Goals:**
- Put the whole public API under `/api/v1/` so one CloudFront behavior (`/api/v1/*`) covers it.
- Provide a readiness endpoint that is false while the catalog import is still running or the vector store is unreachable.

**Non-Goals:**
- Changing the HTTP method of search (see decision 3).
- A redirect or alias from `/search` (no other clients exist).
- Liveness/readiness probe groups for an orchestrator; the instance has no orchestrator.
- Exposing other Actuator endpoints (metrics, info, env).

## Decisions

**1. Prefix via `@RequestMapping("/api/v1")` on the controller, not `server.servlet.context-path`.**
A context path would also move Actuator and any future endpoint without a choice. A class-level mapping makes the versioned surface explicit. Alternative: a global `PathMatchConfigurer` prefix for all `@RestController`s; rejected, it hides the version from the code that defines the endpoint.

**2. Health through Actuator, served at `/api/v1/health`.**
`spring-boot-starter-actuator` with `management.endpoints.web.base-path=/api/v1`, `management.endpoints.web.exposure.include=health` and `management.endpoint.health.show-details=never`. The response body is just `{"status":"UP"}` or `{"status":"DOWN"}`, with 200 and 503. The requirement is the semantics, not the exact properties; the implementer must confirm against the Spring Boot version in the build:
- Readiness must become true only after all `CommandLineRunner`/`ApplicationRunner` beans have completed. Spring Boot publishes the readiness state `ACCEPTING_TRAFFIC` after the runners, so the health endpoint should be backed by the readiness state (health probes enabled, readiness state included in the group served at `/api/v1/health`), not only by the liveness of the process.
- The vector store connection is part of readiness: a custom `HealthIndicator` calls `qdrantClient.collectionExistsAsync(collection)` with a short timeout. It must not call the embedding model or any LLM.
- Alternatives: a hand-written `@GetMapping("/health")` returning a flag set by the import runner (no new dependency, but duplicates what Actuator does and invites drift); Actuator default `/actuator/health` (wrong prefix for CloudFront routing).

**3. Search stays `GET`.**
The method was reconsidered because GET endpoints that spend money can be triggered by crawlers and link previews. Here the API URLs are called only by the storefront script; the pages that people share are S3 objects. No page links to `/api/v1/search?...`. Switching to POST would change the DTO binding, validation, OpenAPI and frontend for no protection against anything actually present, so it is left out.

**4. Old path removed, no redirect.**
There are no deployed clients. A redirect would also need CloudFront and WAF consideration for no benefit.

**5. Health is exempt from every request limit.**
The health endpoint is probed every few seconds by each waiting visitor and by the wake-up function. `demo-cost-controls` applies limits to `/api/v1/search` only; this change records that health makes no LLM call so it is safe to probe frequently.

## Risks / Trade-offs

- [Readiness not wired to the runners, so health reports 200 during the first-boot import] → Add an integration test that blocks the import runner and asserts 503 until it completes.
- [Qdrant check slows the endpoint] → Short timeout (about 1 s); a timeout reports DOWN. The check is a collection-exists call, not a search.
- [Actuator adds startup time and memory on a 1 GB instance] → Only the health endpoint is exposed; no metrics registries are added.
- [The path change breaks the `/search` scenarios in the specs and every test call site] → Mechanical update, listed in the tasks.

## Migration Plan

1. Deploy backend and frontend together: the storefront switches to `/api/v1/search` in the same release (the storefront change already uses the new path).
2. Rollback: revert the commit; the old path returns.

## Open Questions

- Whether `/api/v1/health` should return a 200 body with `status: "UP"` only, or also the Actuator `groups`; the proposal requires only the status code. Default: status only.
