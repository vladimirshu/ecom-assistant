## 1. Request contract

- [x] 1.1 Remove the `"1"` default from `ProductSearchRequestDto.conversationId` so `@NotBlank` rejects a missing or empty id
- [x] 1.2 Controller tests in `ProductsControllerTest`: missing `conversationId` → 400 with `$.conversationId`; empty `conversationId` → 400; UUID value (`3f2b9c1e-7a4d-4e8f-9b21-5c6d7e8f9a0b`) accepted; no `ProductSearchService` call on 400

## 2. Memory wiring

- [x] 2.1 Record only the final schema-valid analysis, not each `StructuredOutputValidationAdvisor` retry; leave history unchanged when analysis fails or retries are exhausted
- [x] 2.2 Add `config/EcomMemoryProperties` (`ecom.memory.inactivity-timeout` default `24h`, `ecom.memory.cleanup-interval` default `1h`) and the entries in `application.yml`; add a `Clock` bean (system UTC)
- [x] 2.3 Implement `memory/ExpiringInMemoryChatMemoryRepository`: `lastActivity` set on every `saveAll` (via `compute`), lazy expiry in `findByConversationId`/`findConversationIds`, single-key `deleteByConversationId`, `@Scheduled` cleanup of expired entries; enable scheduling
- [x] 2.4 Replace the `InMemoryChatMemoryRepository` bean in `AiConfig` and document the memory model in Javadoc (client-owned token, 20-message window, 24-hour inactivity expiry, in-process only, swap point for a persistent repository)
- [x] 2.5 Unit tests for the repository with a mutable test `Clock`: history kept at 23:59 h after the last write; gone at 24:00 h after the last write; a write at 23:00 h keeps it alive past the first 24 h; empty conversation after expiry and after delete; cleanup removes expired entries and keeps live ones; `deleteByConversationId("a")` leaves `"b"` untouched

## 3. Integration tests (WireMock LLM, `AbstractQueryAnalysisIntegrationTest`)

- [x] 3.1 Refinement: two searches with the same id; assert the second query-analysis request body contains the first message and its analysis JSON before the new message, and the product-filtering request contains neither
- [x] 3.2 Isolation: searches with ids `a` and `b`; assert the request for `b` contains no message of `a`
- [x] 3.3 Rejected turn: 422 for the first message, then a second search with the same id; assert the second query-analysis request contains the rejected message
- [x] 3.4 Reset: existing conversation, then `clearMemory=true`; assert no earlier messages are sent and the stored history holds only the new turn
- [x] 3.5 Scoped reset: populate conversations `a` and `b`, send `clearMemory=true` for `a`; assert `chatMemory.get("b")` is unchanged and the next request for `b` still carries its history
- [x] 3.6 Window: 11 completed turns, then a 12th; assert turn 1 is absent and turns 2–11 are present (may be a `ChatMemory` unit test if the HTTP version is too slow)
- [x] 3.7 Fix existing integration tests that share a fixed `conversationId` across test methods (e.g. `session-1`) so they use a unique id per test and remain independent now that history is asserted
- [x] 3.8 Test model failure, successful schema retry, exhausted schema retries, and overlapping searches and resets

## 4. Cross-project contract and wrap-up

- [x] 4.1 Update `openapi.yaml`: `conversationId` becomes `required: true` (remove any default), description states it is client-generated (UUID recommended), a new id starts an empty conversation, history expires after 24 hours without a new turn, memory is in-process only, and `clearMemory` clears only the given conversation; add a missing-id example to the 400 response
- [ ] 4.2 Hand-off: update `../eas-frontend` change `storefront-search-ui` ("Conversation identity" requirement and design decision 3) to the in-memory React token (new UUID on load and on "New search", no `sessionStorage`) via `/opsx:update` in the frontend project
- [x] 4.3 Run `./gradlew test` green
