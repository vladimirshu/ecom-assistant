## Why

The current LLM call is an input guardrail (irrelevant or confusing content) mixed with query understanding (summarizing history, extracting negatives and price) and is wired through tool calls whose only purpose is to set flags on a side-channel object (`AssistantToolsCallback`). Spring AI's modular RAG would split this into a guardrail plus `CompressionQueryTransformer`, `TranslationQueryTransformer` and `RewriteQueryTransformer`, but each transformer is a separate LLM call and a transformer cannot stop a request. We want exactly one LLM call that does all of this and returns a typed result, and a typed rejection that reaches the client.

## What Changes

- Add LLM call #1, **query analysis**: one structured-output call returning `QueryAnalysis` with
  - guardrail verdict: `allowed`, `rejectionReasons`, `suggestedPrompts`;
  - `searchQuery`: conversation compressed into one sentence, translated to English, rewritten for similarity search with price and negative parts removed;
  - `negativeExamples`, `priceConstraint {operator, value}`, `sortByLowestPrice`.
- Schema conformance enforced by Spring AI `StructuredOutputValidationAdvisor` (bounded retries).
- Semantic output validation of `QueryAnalysis` (plain-text fields, length caps, positive price, non-blank query when allowed).
- **BREAKING**: a rejected request returns **HTTP 422** with `{ "suggestedPrompts": [...] }` via `GuardrailRejectedException` (was HTTP 200 with `products: null`).
- Map an allowed analysis to the retrieval context keys defined by `product-vector-retrieval` and call the retriever.
- Remove `AssistantTools`, `AssistantToolsCallback`, `LlmOutputValidationAdvisor`, `PriceOperator.LOWEST/NOT_RELEVANT`, the interim `PlainTextPolicy` use in tools, and `prompts/system-instructions.md` (replaced by `prompts/query-analysis.md`).
- Introduce the LLM test harness: WireMock stubbing the OpenAI chat-completions endpoint.

## Capabilities

### New Capabilities
- `query-analysis-guardrail`: single-LLM-call input guardrail and query transformation, its structured result, output validation, rejection response, and mapping to retrieval parameters.

### Modified Capabilities
<!-- none: no baseline specs exist yet; product-vector-retrieval context contract is consumed unchanged -->

## Impact

- Code: new `guardrails/` package (`QueryAnalysis`, `QueryAnalyzer`, `QueryAnalysisValidator`, `GuardrailRejectedException`, `RetrievalParametersMapper`), `config/AiConfig` (query-analysis `ChatClient`), `controller/ProductsController`, `exception/GlobalExceptionHandler`, `service/ProductSearchService`; deleted `tools/*`, `advisors/LlmOutputValidationAdvisor`.
- API: rejection status 200 → 422.
- Prompts: `prompts/query-analysis.md` replaces `prompts/system-instructions.md`.
- Test dependencies: WireMock.
- Depends on: `simplify-input-validation`, `qdrant-recommend-retriever`.
