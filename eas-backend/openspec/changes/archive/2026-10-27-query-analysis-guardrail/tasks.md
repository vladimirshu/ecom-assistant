## 1. Types and prompt

- [x] 1.1 Add `guardrails/QueryAnalysis`, `PriceConstraint` and a reduced `PriceOperator` (four comparison operators)
- [x] 1.2 Write `prompts/query-analysis.md`: role, security guidelines, the five jobs, rejection rules, price-conflict rule, migrated few-shot examples expressed as `QueryAnalysis` JSON

## 2. Analysis call and guardrail

- [x] 2.1 Add `queryAnalysisChatClient` bean (system prompt, `MessageChatMemoryAdvisor`, `StructuredOutputValidationAdvisor` with 2 retries, logging advisor, no tools)
- [x] 2.2 Implement `QueryAnalyzer.analyze(message, conversationId)` using `.entity(QueryAnalysis.class)`
- [x] 2.3 Implement `QueryAnalysisValidator` (semantic checks, generic suggested-prompt fallback, throws `GuardrailRejectedException` / `IllegalAssistantResponseException`)
- [x] 2.4 Add `GuardrailRejectedException` and its 422 mapping in `GlobalExceptionHandler`
- [x] 2.5 Implement `RetrievalParametersMapper.toContext(QueryAnalysis)`

## 3. Wiring and removal

- [x] 3.1 Rewrite `ProductsController`/`ProductSearchService`: validate input → analyze → validate analysis → map → retrieve → map documents to products
- [x] 3.2 Delete `AssistantTools`, `AssistantToolsCallback`, `LlmOutputValidationAdvisor`, `prompts/system-instructions.md` and the `assistantChatClient` bean

## 4. Tests

- [x] 4.1 Add WireMock test dependency and a test helper that builds OpenAI chat-completion responses from a JSON payload; verify the Spring AI 2.0.2 base-URL property
- [x] 4.2 Unit tests for `QueryAnalysisValidator` and `RetrievalParametersMapper` (every scenario in the spec)
- [x] 4.3 Integration test (WireMock + Testcontainers Qdrant/MySQL + `FakeEmbeddingModel`): allowed request → retriever receives mapped parameters and products are returned
- [x] 4.4 Integration tests: rejection → 422 with suggestions and no Qdrant query; rejection without suggestions → generic prompt; schema retry; invalid analysis → 500; request body contains no `tools`
- [x] 4.5 Run `./gradlew test` green

## 5. Review follow-ups

- [x] 5.1 Sanitize rejected analyses: drop non-plain-text/over-long suggested prompts and rejection reasons, cap suggestions at 5, fall back to the generic prompt
- [x] 5.2 Map an unconvertible response after exhausted schema retries to `IllegalAssistantResponseException` (500)
- [x] 5.3 Document the HTTP 422 rejection response in `openapi.yaml`
- [x] 5.4 Integration tests: retriever receives query text and mapped context; contradictory request; invalid suggestions; schema retries exhausted
