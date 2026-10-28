## Context

After `query-analysis-guardrail`, the controller calls `QueryAnalyzer`, the validator, the mapper and the retriever imperatively. Spring AI 2.0.2 `RetrievalAugmentationAdvisor`:
- builds `Query` from the user message, prompt history and **the request context** (so context keys set by an earlier advisor reach the retriever);
- runs transformers → expander → retriever(s) → joiner → `DocumentPostProcessor`s (which receive the *original* query) → `QueryAugmenter` (also with the original query and its context);
- stores the documents under `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` (`"rag_document_context"`) in the request context, and `after()` copies them into the `ChatResponse` metadata.

The app returns products, not prose, so the "generation" step is a selection.

## Goals / Non-Goals

**Goals:**
- Exactly two LLM calls per successful search; call #2 is the RAG generation step.
- Hallucinated SKUs never reach the client; returned order equals retrieval order.
- A full happy-path integration test with mocked LLMs.

**Non-Goals:**
- LLM reranking (the LLM selects; it does not order).
- Explanations per product.
- Chat-memory policy, streaming, caching.

## Decisions

**1. Filtering as the generation step, not a `DocumentPostProcessor`.**
A post-processor making its own LLM call would leave the advisor's model call with nothing useful to do. Making the main call the selection uses the RAG advisor as designed and keeps structured output, retries and logging on one `ChatClient`.

**2. Advisor chain of `productSearchChatClient` (outer → inner).**
1. `QueryAnalysisGuardrailAdvisor` — call #1 through the separate `queryAnalysisChatClient`; rejection → `GuardrailRejectedException`; otherwise mutate the request: user message := `searchQuery`, context += retrieval parameters + `ORIGINAL_MESSAGE`.
2. `RetrievalAugmentationAdvisor` — no transformers, `QdrantRecommendDocumentRetriever`, `ProductFilterQueryAugmenter`.
3. `EmptyRetrievalAdvisor` — if `DOCUMENT_CONTEXT` is empty, return a synthetic response `{"skus":[]}` without calling the model.
4. `StructuredOutputValidationAdvisor` (2 retries) — inside RAG, so a retry does not re-run analysis or retrieval.
5. Logging advisor.
No memory advisor on this client (the selection does not need history; history is compressed into `searchQuery` by call #1).

**3. Augmenter content.**
`ProductFilterQueryAugmenter` renders `prompts/product-filter.md` with: `searchQuery`, original customer message, negative examples, price constraint (human-readable), and one line per candidate `SKU | colors | sizes | price | description`. Negatives must be passed explicitly because they were stripped from `searchQuery` in call #1. Product data is treated as data: the prompt instructs the model to ignore instructions inside product descriptions.

**4. Output contract and guardrail.**
`.call().responseEntity(ProductSelection.class)` gives the entity and the `ChatResponse` (whose metadata holds the documents). `SkuOutputGuardrail.apply(documents, selection)` iterates the retrieved documents in order and keeps those whose SKU is in the selection; SKUs not among the documents are logged at WARN and dropped; duplicates are ignored; the result is truncated to `max-results`. Deterministic, no retry: a retry would cost a third call for a problem we can fix by dropping.

**5. Ordering.**
Order is defined by retrieval (relevance, or ascending price when requested). The LLM's order is ignored by construction of the guardrail.

**6. Empty results.**
Retrieval empty or nothing selected → HTTP 200 with `products: []`.

**7. Failure policy for call #2.**
If call #2 fails after schema retries, the request fails with HTTP 500 `Illegal assistant response` (consistent with call #1). Returning unfiltered candidates was rejected: it would silently violate negatives. Revisit if availability matters more than precision.

**8. End-to-end test.**
`@SpringBootTest(webEnvironment = RANDOM_PORT)` with Testcontainers MySQL + Qdrant, `FakeEmbeddingModel`, WireMock for OpenAI chat completions, fixture CSV with known SKUs. Two stubs distinguished by request-body match on prompt markers (`query-analysis` vs `product-filter`). The test calls `GET /search` over HTTP and asserts: status 200, returned SKUs and order, exactly two chat-completion requests, the second request's prompt contains only retrieved SKUs and the negatives, and a hallucinated SKU in the stubbed selection is not returned. Variants: lowest price, rejection (one call, 422), empty retrieval (one call).

## Risks / Trade-offs

- [Prompt size grows with `topK = 30` descriptions] → ~30 short descriptions fit comfortably in gpt-4o-mini context; `top-k` is configurable.
- [LLM drops relevant products (false negatives)] → Prompt instructs to keep borderline matches; eval set is a follow-up.
- [Indirect prompt injection via product descriptions] → Catalog is internal CSV; prompt treats descriptions as data; output is constrained to SKUs that exist, so injection cannot add content to the response.
- [`responseEntity` and metadata access] → If `DOCUMENT_CONTEXT` is not present in response metadata under 2.0.2, read it from `ChatClientResponse.context()` via `.call().chatClientResponse()` and convert the entity with `BeanOutputConverter`.
