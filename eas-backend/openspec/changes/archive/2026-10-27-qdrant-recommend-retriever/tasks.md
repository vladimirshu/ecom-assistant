## 1. Configuration

- [x] 1.1 Replace `spring-ai-qdrant-store` with `spring-ai-starter-vector-store-qdrant`; add `spring.ai.vectorstore.qdrant.host/port/initialize-schema` to `application.yml`
- [x] 1.2 Remove the `qdrantClient` and `vectorStore` beans and Qdrant constants from `AiConfig`
- [x] 1.3 Add `ecom.search.top-k`, `ecom.search.similarity-threshold` and `ecom.import.sample-file` properties (`@ConfigurationProperties` record)
- [x] 1.4 Make `ProductImportService` read the sample file from `ecom.import.sample-file`

## 2. Retriever

- [x] 2.1 Add `retrieval/RetrievalContextKeys` (reuse `VectorStoreDocumentRetriever.FILTER_EXPRESSION`, add `NEGATIVE_EXAMPLES`, `SORT_BY_LOWEST_PRICE`)
- [x] 2.2 Implement `retrieval/QdrantRecommendDocumentRetriever`: nearest vs recommend query, batch-embedded negatives, filter via `QdrantFilterExpressionConverter` (string parsed with `FilterExpressionTextParser`), threshold, `topK`
- [x] 2.3 Implement lowest-price ordering as prefetch + `orderBy("price")`
- [x] 2.4 Add startup runner creating the `price` float payload index idempotently
- [x] 2.5 Fix `VectorStoreUtils.documentToProduct` (description from metadata, single price conversion path)

## 3. Interim wiring

- [x] 3.1 Rewrite `ProductSearchService.searchProducts` to build a `Query` with context keys from `AssistantToolsCallback` values and delegate to the retriever
- [x] 3.2 Remove the MySQL price pre-filter from `ProductsController` and delete `ProductRepository.findByPrice`
- [x] 3.3 Remove the Java price sorting from `ProductSearchService`

## 4. Tests

- [x] 4.1 Add test dependencies: Testcontainers Qdrant module, `spring-ai-spring-boot-testcontainers`
- [x] 4.2 Add `FakeEmbeddingModel` (deterministic hashed bag-of-words) and a small fixture CSV under `src/test/resources`
- [x] 4.3 Add a reusable test configuration providing Qdrant (`@ServiceConnection`), MySQL and the fake embedding model
- [x] 4.4 Integration tests for every `product-vector-retrieval` scenario (similarity, threshold, negatives, filter as expression and string, invalid filter type, ordering alone and combined, index idempotency)
- [x] 4.5 Run `./gradlew test` green
