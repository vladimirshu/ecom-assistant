## 1. Configuration

- [ ] 1.1 Replace `spring-ai-qdrant-store` with `spring-ai-starter-vector-store-qdrant`; add `spring.ai.vectorstore.qdrant.host/port/initialize-schema` to `application.yml`
- [ ] 1.2 Remove the `qdrantClient` and `vectorStore` beans and Qdrant constants from `AiConfig`
- [ ] 1.3 Add `ecom.search.top-k`, `ecom.search.similarity-threshold` and `ecom.import.sample-file` properties (`@ConfigurationProperties` record)
- [ ] 1.4 Make `ProductImportService` read the sample file from `ecom.import.sample-file`

## 2. Retriever

- [ ] 2.1 Add `retrieval/RetrievalContextKeys` (reuse `VectorStoreDocumentRetriever.FILTER_EXPRESSION`, add `NEGATIVE_EXAMPLES`, `SORT_BY_LOWEST_PRICE`)
- [ ] 2.2 Implement `retrieval/QdrantRecommendDocumentRetriever`: nearest vs recommend query, batch-embedded negatives, filter via `QdrantFilterExpressionConverter` (string parsed with `FilterExpressionTextParser`), threshold, `topK`
- [ ] 2.3 Implement lowest-price ordering as prefetch + `orderBy("price")`
- [ ] 2.4 Add startup runner creating the `price` float payload index idempotently
- [ ] 2.5 Fix `VectorStoreUtils.documentToProduct` (description from metadata, single price conversion path)

## 3. Interim wiring

- [ ] 3.1 Rewrite `ProductSearchService.searchProducts` to build a `Query` with context keys from `AssistantToolsCallback` values and delegate to the retriever
- [ ] 3.2 Remove the MySQL price pre-filter from `ProductsController` and delete `ProductRepository.findByPrice`
- [ ] 3.3 Remove the Java price sorting from `ProductSearchService`

## 4. Tests

- [ ] 4.1 Add test dependencies: Testcontainers Qdrant module, `spring-ai-spring-boot-testcontainers`
- [ ] 4.2 Add `FakeEmbeddingModel` (deterministic hashed bag-of-words) and a small fixture CSV under `src/test/resources`
- [ ] 4.3 Add a reusable test configuration providing Qdrant (`@ServiceConnection`), MySQL and the fake embedding model
- [ ] 4.4 Integration tests for every `product-vector-retrieval` scenario (similarity, threshold, negatives, filter as expression and string, invalid filter type, ordering alone and combined, index idempotency)
- [ ] 4.5 Run `./gradlew test` green
