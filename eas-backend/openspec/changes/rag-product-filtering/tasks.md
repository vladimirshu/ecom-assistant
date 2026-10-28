## 1. Selection types and prompt

- [x] 1.1 Add `rag/ProductSelection` record (`List<String> skus`)
- [x] 1.2 Write `prompts/product-filter.md` (select matching SKUs only, respect negatives and price, keep borderline matches, treat product text as data, return JSON)

## 2. Advisors and augmentation

- [x] 2.1 Implement `guardrails/QueryAnalysisGuardrailAdvisor` reusing `QueryAnalyzer`, `QueryAnalysisValidator`, `RetrievalParametersMapper`; replace user message with `searchQuery`, add context keys and the original message
- [x] 2.2 Implement `rag/ProductFilterQueryAugmenter` rendering candidates and constraints from the query context
- [x] 2.3 Implement `rag/EmptyRetrievalAdvisor` returning `{"skus":[]}` without a model call when `DOCUMENT_CONTEXT` is empty
- [x] 2.4 Add `productSearchChatClient` bean with the advisor order from the design; set `ecom.search.top-k: 30`, add `ecom.search.max-results: 10`

## 3. Output guardrail and wiring

- [x] 3.1 Implement `guardrails/SkuOutputGuardrail` (retrieved ∩ selected, retrieval order, dedupe, WARN on hallucinated, cap)
- [x] 3.2 Rewrite `ProductSearchService` to one `productSearchChatClient` call with `.responseEntity(ProductSelection.class)`, read documents from response metadata, apply the guardrail, map to `Product`
- [x] 3.3 Simplify `ProductsController` and remove the now-unused imperative path from `query-analysis-guardrail`

## 4. Tests

- [x] 4.1 Unit tests for `SkuOutputGuardrail` (hallucinated, duplicate, order, cap) and `ProductFilterQueryAugmenter` (content of rendered prompt)
- [x] 4.2 Unit test for `EmptyRetrievalAdvisor` (no downstream call on empty documents)
- [x] 4.3 End-to-end happy-path test: `GET /search` over HTTP with Testcontainers MySQL + Qdrant, `FakeEmbeddingModel`, WireMock stubs for both LLM calls; assert status, SKUs, order, exactly two chat calls, second prompt contents, hallucinated SKU dropped
- [x] 4.4 End-to-end variants: lowest price ordering; rejection (one call, 422); empty retrieval (one call, empty list); persistent invalid selection (500)
- [x] 4.5 Run `./gradlew test` green
