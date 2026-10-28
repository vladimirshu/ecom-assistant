## Why

Vector similarity alone returns loosely related products: negations ("no wool"), materials, fits and occasions are poorly captured by embeddings. A second LLM call that reads the retrieved candidates and keeps only those that actually match the request gives much better precision. Implementing it as the generation step of Spring AI's `RetrievalAugmentationAdvisor` completes the modular RAG pipeline: guardrail + query analysis → retrieval → augmentation → generation (selection), with deterministic guardrails around the LLM's output.

## What Changes

- Add LLM call #2, **product filtering**, as the generation step of a `RetrievalAugmentationAdvisor` using the `QdrantRecommendDocumentRetriever`; the model returns `ProductSelection { skus }` via structured output.
- Add `QueryAnalysisGuardrailAdvisor` (outermost advisor) wrapping the `query-analysis-guardrail` components: runs call #1, throws on rejection, replaces the user message with the English `searchQuery` and puts the retrieval parameters into the request context.
- Add `ProductFilterQueryAugmenter`: injects the candidate products and the request's constraints (negatives, price, original wording) into the prompt.
- Skip call #2 when retrieval returns no documents.
- Add `SkuOutputGuardrail`: keep only SKUs that were retrieved, preserve retrieval order (so lowest-price ordering survives), drop duplicates, log hallucinated SKUs.
- Raise retrieval `topK` to 30 and cap the response at `ecom.search.max-results` (10).
- Simplify `ProductsController`/`ProductSearchService` to a single `ChatClient` call.
- Add the end-to-end happy-path integration test: `/search` → query analysis → vector search → LLM filtering → response, with both LLM calls mocked.

## Capabilities

### New Capabilities
- `rag-product-filtering`: LLM-based selection among retrieved products, its prompt inputs, output guardrails, ordering, empty-result behaviour and the end-to-end search response.

### Modified Capabilities
<!-- none: no baseline specs exist yet. query-analysis-guardrail and product-vector-retrieval requirements are unchanged; only their wiring moves into the advisor chain. -->

## Impact

- Code: new `guardrails/QueryAnalysisGuardrailAdvisor`, `guardrails/SkuOutputGuardrail`, `rag/ProductFilterQueryAugmenter`, `rag/EmptyRetrievalAdvisor`, `rag/ProductSelection`; `config/AiConfig` (`productSearchChatClient` bean), `service/ProductSearchService`, `controller/ProductsController`.
- Prompts: new `prompts/product-filter.md`.
- Configuration: `ecom.search.top-k: 30`, `ecom.search.max-results: 10`.
- Cost/latency: two LLM calls per successful search (one on rejection, one when retrieval is empty).
- Depends on: `qdrant-recommend-retriever`, `query-analysis-guardrail`.
