## Context

Target pipeline has exactly two LLM calls: (1) query analysis = guardrail + compression + translation + rewrite + parameter extraction, (2) product filtering (`rag-product-filtering`). This change delivers call #1 and connects it to the retriever from `qdrant-recommend-retriever`; call #2 does not exist yet, so the controller calls the retriever directly.

Spring AI 2.0.2 facts relevant here:
- `CompressionQueryTransformer`, `TranslationQueryTransformer`, `RewriteQueryTransformer` each own a `ChatClient` and are applied sequentially by `RetrievalAugmentationAdvisor` → one LLM call each. A `QueryTransformer` returns a `Query` and cannot short-circuit the request.
- `StructuredOutputValidationAdvisor` validates the response against the JSON schema of the target type and re-invokes the downstream chain (`callAdvisorChain.copy(this).nextCall(...)`) up to `maxRepeatAttempts`. It does not check semantics.

## Goals / Non-Goals

**Goals:**
- One LLM call producing a typed `QueryAnalysis`.
- A rejection reaches the client as HTTP 422 with suggested prompts.
- Remove the tool-call side channel.
- Components shaped so `rag-product-filtering` can wrap them in an advisor without rewriting them.

**Non-Goals:**
- Chat-memory policy (e.g. not storing rejected turns) — deferred; current `MessageChatMemoryAdvisor` behaviour is kept on the analysis call.
- LLM product filtering and the `RetrievalAugmentationAdvisor` (`rag-product-filtering`).
- Using a separate/cheaper model for the guardrail.

## Decisions

**1. One combined call instead of Spring AI's transformer modules.**
Saves 3 LLM calls per request. Trade-off: a jailbreak that defeats the analysis prompt defeats both guardrail and rewrite, and the guardrail cannot run on a different model. Accepted for now; the result type keeps verdict and query fields separate so the call can be split later without changing consumers.

**2. Result type.**
```java
record QueryAnalysis(
    boolean allowed,
    List<String> rejectionReasons,
    List<String> suggestedPrompts,
    String searchQuery,              // English, compressed, price/negatives removed
    List<String> negativeExamples,
    PriceConstraint priceConstraint, // nullable
    boolean sortByLowestPrice) {}
record PriceConstraint(PriceOperator operator, BigDecimal value) {}
enum PriceOperator { LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL }
```
Contradictory price conditions resolve as today: the condition with the highest price is used, unless they are genuinely contradictory, in which case the request is rejected.

**3. Dedicated `ChatClient` with `.entity(QueryAnalysis.class)` and `StructuredOutputValidationAdvisor`.**
`QueryAnalyzer.analyze(message, conversationId)` uses a `queryAnalysisChatClient` bean with system prompt `prompts/query-analysis.md`, `MessageChatMemoryAdvisor` (history available for compression), `StructuredOutputValidationAdvisor` (`maxRepeatAttempts = 2`), and the logging advisor. Temperature stays low.

**4. Guardrail rejection is not recursive — it short-circuits by exception.**
The recursive-advisor pattern re-invokes the chain; a rejection must stop it. `QueryAnalysisValidator` throws `GuardrailRejectedException(QueryAnalysis)` when `allowed == false`; `GlobalExceptionHandler` maps it to 422 with `ChatResponseDto(null, suggestedPrompts)`. An exception also works unchanged once the analysis moves into an advisor in `rag-product-filtering`, where a short-circuit `ChatClientResponse` would break `.entity(...)` parsing of the main call.

**5. Semantic validation after schema validation.**
`QueryAnalysisValidator` (deterministic): when allowed, `searchQuery` non-blank ≤ 500 chars; all text fields plain text (`PlainTextPolicy`), `suggestedPrompts` ≤ 5 items × 300 chars; `negativeExamples` ≤ 10; price value > 0; when rejected, at least one suggested prompt (a generic fallback is added if the LLM gives none). Violations → `IllegalAssistantResponseException` (500).

**6. Mapping to retrieval.**
`RetrievalParametersMapper.toContext(QueryAnalysis)` → `Map<String,Object>` with `FILTER_EXPRESSION` (`FilterExpressionBuilder` `lt/lte/gt/gte("price", value)`), `NEGATIVE_EXAMPLES`, `SORT_BY_LOWEST_PRICE`. `ProductSearchService` builds `Query(searchQuery, context)` and calls the retriever.

**7. Test harness: WireMock for OpenAI.**
`spring.ai.openai.base-url` points at a WireMock server; stubs for `POST /v1/chat/completions` return canned OpenAI responses whose message content is the `QueryAnalysis` JSON. Stubs are selected by matching the request body (system prompt marker). This exercises the real OpenAI client, structured-output conversion and the advisor chain — a stub `ChatModel` bean would skip them. Embeddings use the `FakeEmbeddingModel` from `qdrant-recommend-retriever`. The property name for the base URL under Spring AI 2.0.2 is verified during implementation.

## Risks / Trade-offs

- [Single prompt doing five jobs degrades quality] → Few-shot examples in `query-analysis.md` per job; the existing examples from `system-instructions.md` are migrated.
- [Rejected turns stored in chat memory pollute later analyses] → Known, deferred by decision; documented in the prompt and in a follow-up.
- [422 is a breaking change for clients expecting 200] → Called out in the proposal.
- [Retries on schema failure add latency] → Capped at 2 retries.
