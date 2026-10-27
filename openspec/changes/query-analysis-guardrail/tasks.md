## 1. Types and prompt

- [ ] 1.1 Add `guardrails/QueryAnalysis`, `PriceConstraint` and a reduced `PriceOperator` (four comparison operators)
- [ ] 1.2 Write `prompts/query-analysis.md`: role, security guidelines, the five jobs, rejection rules, price-conflict rule, migrated few-shot examples expressed as `QueryAnalysis` JSON

## 2. Analysis call and guardrail

- [ ] 2.1 Add `queryAnalysisChatClient` bean (system prompt, `MessageChatMemoryAdvisor`, `StructuredOutputValidationAdvisor` with 2 retries, logging advisor, no tools)
- [ ] 2.2 Implement `QueryAnalyzer.analyze(message, conversationId)` using `.entity(QueryAnalysis.class)`
- [ ] 2.3 Implement `QueryAnalysisValidator` (semantic checks, generic suggested-prompt fallback, throws `GuardrailRejectedException` / `IllegalAssistantResponseException`)
- [ ] 2.4 Add `GuardrailRejectedException` and its 422 mapping in `GlobalExceptionHandler`
- [ ] 2.5 Implement `RetrievalParametersMapper.toContext(QueryAnalysis)`

## 3. Wiring and removal

- [ ] 3.1 Rewrite `ProductsController`/`ProductSearchService`: validate input → analyze → validate analysis → map → retrieve → map documents to products
- [ ] 3.2 Delete `AssistantTools`, `AssistantToolsCallback`, `LlmOutputValidationAdvisor`, `prompts/system-instructions.md` and the `assistantChatClient` bean

## 4. Tests

- [ ] 4.1 Add WireMock test dependency and a test helper that builds OpenAI chat-completion responses from a JSON payload; verify the Spring AI 2.0.2 base-URL property
- [ ] 4.2 Unit tests for `QueryAnalysisValidator` and `RetrievalParametersMapper` (every scenario in the spec)
- [ ] 4.3 Integration test (WireMock + Testcontainers Qdrant/MySQL + `FakeEmbeddingModel`): allowed request → retriever receives mapped parameters and products are returned
- [ ] 4.4 Integration tests: rejection → 422 with suggestions and no Qdrant query; rejection without suggestions → generic prompt; schema retry; invalid analysis → 500; request body contains no `tools`
- [ ] 4.5 Run `./gradlew test` green
