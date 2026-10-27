## Why

Vector search is implemented as a bespoke service (`ProductSearchService`) that talks to the Qdrant gRPC client directly, pre-filters by price in MySQL and passes the resulting SKUs back to Qdrant as an `IN` filter, and sorts by price in Java. To build a modular Spring AI RAG pipeline (`query-analysis-guardrail`, `rag-product-filtering`) retrieval must be a Spring AI `DocumentRetriever` that takes all search parameters from the `Query` context. `VectorStoreDocumentRetriever` cannot be used because it supports neither Qdrant's recommend query with negative examples nor payload ordering, both of which we keep.

## What Changes

- Add `QdrantRecommendDocumentRetriever implements DocumentRetriever`:
  - positive query text → embedding; negative examples (from `Query.context()`) → Qdrant recommend query with negative vectors;
  - metadata filter from the standard `VectorStoreDocumentRetriever.FILTER_EXPRESSION` context key (string or `Filter.Expression`), converted with the existing `QdrantFilterExpressionConverter`;
  - optional lowest-price ordering executed inside Qdrant (prefetch similarity candidates, outer `order_by price`).
- Create a float payload index on `price` at startup (idempotent), required by `order_by` and range filters.
- Replace hand-built `QdrantClient`/`QdrantVectorStore` beans (hard-coded `localhost:6334`) with the Spring AI Qdrant vector store starter configured via `spring.ai.vectorstore.qdrant.*`.
- Make `topK`, similarity threshold and sample-data file configurable (`ecom.search.*`, `ecom.import.*`).
- **BREAKING (internal)**: price filtering no longer queries MySQL; `ProductRepository.findByPrice` is removed. The existing tool-based controller flow builds a `Query` with context keys and calls the new retriever, so the public API behaves the same.
- Fix `VectorStoreUtils.documentToProduct` to return the product description from metadata rather than the composite embedding text.

## Capabilities

### New Capabilities
- `product-vector-retrieval`: retrieving candidate products from the vector store by query text, negative examples, metadata price filter and optional lowest-price ordering, driven entirely by `Query` context keys.

### Modified Capabilities
<!-- none: no baseline specs exist yet -->

## Impact

- Code: new `retrieval/QdrantRecommendDocumentRetriever`, `retrieval/RetrievalContextKeys`, `config/AiConfig` (Qdrant beans removed), `service/ProductSearchService` (reduced to building the `Query`), `repository/ProductRepository`, `VectorStoreUtils`, `service/ProductImportService`, `controller/ProductsController`.
- Configuration: `spring.ai.vectorstore.qdrant.host/port/collection-name/initialize-schema`, `ecom.search.top-k`, `ecom.search.similarity-threshold`, `ecom.import.sample-file`.
- Dependencies: `spring-ai-qdrant-store` → `spring-ai-starter-vector-store-qdrant`; test: Testcontainers Qdrant module, `spring-ai-spring-boot-testcontainers`.
- Depends on: `simplify-input-validation` (working build/test classpath).
