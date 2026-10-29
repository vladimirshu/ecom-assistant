## 1. Versioned search path

- [ ] 1.1 Add `@RequestMapping("/api/v1")` to `ProductsController` and keep `@GetMapping("/search")`
- [ ] 1.2 Update every `get("/search")` and `.path("/search")` in `ProductsControllerTest`, `ConversationMemoryIntegrationTest`, `QueryAnalysisRetrievalIntegrationTest`, `QueryAnalysisGuardrailIntegrationTest`, `RagProductFilteringIntegrationTest` and the Javadoc in `AbstractQueryAnalysisIntegrationTest` to `/api/v1/search`
- [ ] 1.3 Add a controller test: `GET /search` returns 404 and calls neither the search service nor any LLM
- [ ] 1.4 Update `openapi.yaml`: path `/api/v1/search`, new path `/api/v1/health` with 200/503 responses, description of the readiness semantics

## 2. Readiness endpoint

- [ ] 2.1 Add `spring-boot-starter-actuator` to `build.gradle.kts`
- [ ] 2.2 Configure `application.yml`: base path `/api/v1`, expose only `health`, `show-details: never`, readiness state included in the group served at `/api/v1/health`; confirm the property names against the Spring Boot version in the build
- [ ] 2.3 Add a `HealthIndicator` that calls `qdrantClient.collectionExistsAsync(collection)` with a 1 s timeout and reports DOWN on timeout or error; it must not call the embedding model
- [ ] 2.4 Confirm `SecurityConfig` still permits `/api/v1/health` without changes

## 3. Tests

- [ ] 3.1 Health tests: 200 with `{"status":"UP"}` when started; 503 while a blocked import runner has not finished; 503 when the Qdrant check times out; body contains only `status`
- [ ] 3.2 Assert that probing `/api/v1/health` many times sends no request to the (WireMock) LLM or the embedding model
- [ ] 3.3 Run `./gradlew test`; `grep -rn '"/search"' src` finds nothing
