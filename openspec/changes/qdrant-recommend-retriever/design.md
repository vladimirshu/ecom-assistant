## Context

Current flow in `ProductsController`: the tool-calling LLM sets price/negatives/sort flags on `AssistantToolsCallback`; the controller loads all products matching the price from MySQL (`findByPrice`), then `ProductSearchService` runs a Qdrant `recommend` query restricted to those SKUs (`sku IN (...)`), and finally sorts by price in Java.

Spring AI 2.0.2 `RetrievalAugmentationAdvisor.before()` builds the `Query` with `context(chatClientRequest.context())`, so any key placed in the `ChatClient` request context reaches `DocumentRetriever.retrieve(Query)`. `VectorStoreDocumentRetriever` reads `FILTER_EXPRESSION` (`"vector_store_filter_expression"`) from that context, but only issues a plain similarity search.

Qdrant Java client 1.18 provides `QueryFactory.recommend(...)`, `QueryFactory.orderBy(...)` and `PrefetchQuery`.

## Goals / Non-Goals

**Goals:**
- A `DocumentRetriever` usable as-is by `RetrievalAugmentationAdvisor` in `rag-product-filtering`.
- Keep negative-example search (Qdrant recommend with negative vectors).
- Price filtering and lowest-price ordering executed by Qdrant in a single request.
- Qdrant connection configured by properties so tests can point at a container.

**Non-Goals:**
- Deciding *what* the filters are (that is `query-analysis-guardrail`).
- LLM post-filtering (`rag-product-filtering`).
- Removing MySQL product storage (still the import source of truth).

## Decisions

**1. Context contract.**
`RetrievalContextKeys`:
- `VectorStoreDocumentRetriever.FILTER_EXPRESSION` — `Filter.Expression` or string, e.g. `price <= 200` (reused standard key so the retriever stays interchangeable with `VectorStoreDocumentRetriever`).
- `NEGATIVE_EXAMPLES = "ecom_negative_examples"` — `List<String>`.
- `SORT_BY_LOWEST_PRICE = "ecom_sort_by_lowest_price"` — `Boolean`.
Missing keys mean "not applied". Unknown value types fail fast with `IllegalArgumentException`.

**2. Query shape.**
- No negatives: `nearest(embed(query.text()))`.
- Negatives present: `recommend(positive=[embed(text)], negative=[embed(n) …], strategy=BEST_SCORE)`.
- Filter and score threshold are applied to the similarity stage; `limit = topK`.
- Lowest price requested: the similarity stage becomes a `PrefetchQuery` (with filter, threshold, `limit = topK`) and the outer query is `orderBy(price ASC)` with `limit = topK`. Ordering therefore applies to the relevant candidates only, never to the whole collection.
Rejected alternative: sorting in Java after retrieval — works, but the user asked for ordering in the vector DB retrieval and it would have to be repeated after every later step.

**3. Payload index on `price`.**
`order_by` and efficient range filtering require a payload index. An `ApplicationRunner` calls `createPayloadIndexAsync(collection, "price", PayloadSchemaType.Float, …)` after the collection exists; "already exists" is treated as success.

**4. Spring AI Qdrant starter instead of manual beans.**
Removes the hard-coded host/port and gives `@ServiceConnection` support for a Testcontainers `QdrantContainer` in tests. The native `QdrantClient` is obtained from the auto-configured bean.

**5. Embeddings.**
The retriever uses the application `EmbeddingModel`. Negative examples are embedded in one batch call (`embed(List<String>)`) to avoid N round trips.

**6. Document mapping.**
Keep `VectorStoreUtils.qdrantPointToDocument`; `documentToProduct` reads `description` from metadata (the document text is the composite string used for embedding).

**7. Interim wiring.**
`ProductSearchService.searchProducts` builds a `Query` from the existing `AssistantToolsCallback` values (price → `Filter.Expression` via `FilterExpressionBuilder`, `PriceOperator.LOWEST` → sort flag) and delegates to the retriever. This keeps the API working until `query-analysis-guardrail` replaces the tool flow.

**8. Testing with a fake embedding model.**
`FakeEmbeddingModel` (test source set): deterministic hashed bag-of-words vectors (e.g. 256 dims, L2-normalized) so that texts sharing words are close. This makes similarity assertions meaningful without calling OpenAI. Qdrant runs in Testcontainers.

## Risks / Trade-offs

- [Recommend `BEST_SCORE` scores are not cosine similarities when negatives are present; the similarity threshold behaves differently] → Threshold applies to the similarity stage; integration tests cover with/without negatives; threshold is configurable.
- [Prefetch + `order_by` combination] → Verified by the Qdrant integration test before the interim wiring is switched over; fallback is sorting the retrieved documents in the retriever.
- [Starter auto-configuration conflicts with remaining manual beans] → Remove `qdrantClient`/`vectorStore` beans from `AiConfig` in the same step.
- [Existing Qdrant collection created without the payload index] → Index creation is idempotent and runs on every start.
