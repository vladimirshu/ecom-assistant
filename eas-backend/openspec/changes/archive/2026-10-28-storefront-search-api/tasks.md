## 1. Catalog data

- [x] 1.1 Add `catalog/ProductCategory` enum (slug, display name) with the 13 categories from the design and a JPA `AttributeConverter` persisting the slug
- [x] 1.2 Extend `entity/Product` with `title` (80), `category`, `imageKey` (nullable columns); widen `sizes` to 50; add `ProductRepository.existsByCategoryIsNull()`
- [x] 1.3 Rewrite the sample CSV header to `sku;title;category;colors;sizes;price;image;description` and curate a title and category for all 100 rows using the design's mapping table (`image` left empty); review titles for distinctness

## 2. Import and vector store

- [x] 2.1 Update `ProductImportService` CSV parsing: header-based column access, required-field and category validation with skip + WARN by SKU, default image key `categories/<category>.webp`
- [x] 2.2 Change the import trigger to empty table OR empty vector store OR `existsByCategoryIsNull()`; upsert into MySQL and Qdrant with point ID `UUID.nameUUIDFromBytes(sku)`; delete Qdrant points without `category` payload before upserting
- [x] 2.3 Update `VectorStoreUtils.productToDocument` (payload incl. `title`, `category`, `imageKey`; embedded text title, category name, colors, sizes, description) and `documentToProduct` (map all fields)

## 3. API representation

- [x] 3.1 Add `config/EcomAssetsProperties` (`ecom.assets.base-url`, default `/images/`) and URL joining with exactly one `/`
- [x] 3.2 Add `dto/ProductDto` and `ProductDtoMapper` (colors/sizes split and trimmed, canonical size order, `imageUrl`)
- [x] 3.3 Add `dto/SearchInterpretationDto` + `PriceConstraintDto` mapped from `QueryAnalysis`; extend `ChatResponseDto` to `(products, interpretation, suggestedPrompts)`; keep 422 body via `GlobalExceptionHandler`
- [x] 3.4 Store the validated `QueryAnalysis` under context key `ecom_query_analysis` in `QueryAnalysisGuardrailAdvisor`; make sure `EmptyRetrievalAdvisor` preserves it; read it in `ProductSearchService` and return products + interpretation to `ProductsController`

## 4. Prompts

- [x] 4.1 `prompts/product-filter.md` + `ProductFilterQueryAugmenter`: candidate line `SKU | title | category | colors | sizes | price | description`
- [x] 4.2 `prompts/query-analysis.md`: suggested prompts in the language of the latest customer message, `searchQuery` always English; add a non-English rejection few-shot example

## 5. Tests

- [x] 5.1 Unit tests: CSV row parsing (valid, missing title, unknown category, image default/explicit), `ProductDtoMapper` (size order, unknown sizes last, colors array, URL joining incl. CDN base URL with/without trailing slash), `SearchInterpretationDto` mapping
- [x] 5.2 Import integration test (Testcontainers MySQL + Qdrant, `FakeEmbeddingModel`): fresh import; restart without embedding calls; upgrade from rows without category leaves exactly one point per SKU; document round trip keeps all attributes
- [x] 5.3 Augmenter test: title and category present in the candidate line
- [x] 5.4 Extend end-to-end tests: happy path asserts new product fields and `interpretation`; empty retrieval returns `products: []` with interpretation; refinement in one conversation returns the second analysis' `searchQuery`; rejection body unchanged and passes non-English suggestions through
- [x] 5.5 Run `./gradlew test` green
