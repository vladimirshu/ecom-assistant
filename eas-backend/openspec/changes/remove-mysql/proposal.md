## Why

MySQL is an unused copy of the catalog. `ProductRepository` is only called by `ProductImportService` (to decide whether to import and to save the CSV rows). Search reads every product attribute from the Qdrant payload (`VectorStoreUtils::documentToProduct`), and nothing reads the table. For a public prototype on the AWS free tier, every stateful service costs money and operational work (a managed database or an extra container, credentials, a connection pool, startup ordering). Removing MySQL leaves Qdrant as the only product store and makes the deployment one application container plus Qdrant.

## What Changes

- Remove the MySQL/JPA layer: `repository/ProductRepository`, `config/DatabaseConfig`, the JPA annotations on `Product` and the JPA `catalog/ProductCategoryConverter`. `Product` becomes a plain domain class and moves from `entity` to `catalog`.
- Remove the dependencies `spring-boot-starter-data-jpa`, `mysql-connector-j` and `testcontainers-mysql`, the `spring.datasource` / `spring.jpa` configuration and the Hibernate logger setting.
- Remove the `mysql` service and the `mysql-data` volume from `docker-compose.yml`, and drop the datasource variables from the commented-out backend service.
- Base the catalog import decision on Qdrant only: import when the collection has no points or any point has no `category` payload. Count the points through the Qdrant client instead of the current `similaritySearch(" ")` probe, which sends an embedding request on every startup.
- Integration tests start only a Qdrant Testcontainer.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `product-catalog`: the "Idempotent catalog import" requirement refers to the vector store only (no products table). A restart with current data makes no embedding requests, including for the initialization check.

## Impact

- Code: delete `repository/ProductRepository`, `config/DatabaseConfig`, `catalog/ProductCategoryConverter`. Move `entity/Product` to `catalog/Product` (about 10 importing files) and update `service/ProductImportService`.
- Build: three dependencies removed. The application no longer needs a datasource to start.
- Configuration: `SPRING_DATASOURCE_*` environment variables no longer exist; `application.yml` loses the `datasource`/`jpa` blocks.
- Local environment: `docker compose up` starts Qdrant only. An existing `mysql-data` volume is orphaned and can be removed manually.
- Tests: `AbstractRetrievalIntegrationTest`, `RagProductFilteringIntegrationTest` and `ProductImportServiceIntegrationTest` drop the MySQL container. Import tests assert on Qdrant point counts instead of repository counts, and `ProductImportServiceTest` drops the repository mock.
- API: none. `/search` responses are unchanged.
- Depends on: `storefront-search-api`, which introduces the `product-catalog` capability modified here. Archive it first, or sync its specs.
