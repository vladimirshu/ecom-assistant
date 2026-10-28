## 1. Domain model

- [ ] 1.1 Move `entity/Product` to `catalog/Product`, remove all JPA annotations (`@Entity`, `@Table`, `@Id`, `@Column`, `@Convert`) and keep Lombok `@Data @NoArgsConstructor`; update imports in main and test sources
- [ ] 1.2 Delete `catalog/ProductCategoryConverter`, `repository/ProductRepository` and `config/DatabaseConfig`

## 2. Import on Qdrant only

- [ ] 2.1 In `ProductImportService`, remove the `ProductRepository` dependency and the `saveAll` call; replace `isVectorStoreInitialized()` (`similaritySearch` probe) with exact `QdrantClient.countAsync` counts (total, and `isNull("category")`), returning 0 when the collection does not exist
- [ ] 2.2 Import when `total == 0 || withoutCategory > 0`; keep `deletePointsWithoutCategory()` before the upsert; update the Javadoc to describe the Qdrant-only trigger

## 3. Build and configuration

- [ ] 3.1 Remove `spring-boot-starter-data-jpa`, `mysql-connector-j` and `testcontainers-mysql` from `build.gradle.kts`
- [ ] 3.2 Remove `spring.datasource`, `spring.jpa` and the `org.hibernate` logger from `application.yml`
- [ ] 3.3 Remove the `mysql` service and `mysql-data` volume from `../docker-compose.yml`, and the `SPRING_DATASOURCE_*` variables and `depends_on: mysql` from the commented-out backend service

## 4. Tests

- [ ] 4.1 Remove `MySQLContainer` and the datasource `@DynamicPropertySource` entries from `AbstractRetrievalIntegrationTest`, `RagProductFilteringIntegrationTest`, `ProductImportServiceIntegrationTest` and the Javadoc of `AbstractQueryAnalysisIntegrationTest`
- [ ] 4.2 `ProductImportServiceTest`: drop the `ProductRepository` mock from the constructor
- [ ] 4.3 `ProductImportServiceIntegrationTest`: fresh import asserts 8 Qdrant points; restart asserts zero embedding calls (counting every `FakeEmbeddingModel` call, including single-text `embed`) and 8 points; upgrade test seeds a legacy point without `category` alongside current points and asserts exactly 8 points afterwards; round-trip test builds the `Product` from the parsed CSV row
- [ ] 4.4 Run `./gradlew test`. All tests pass with only Docker Qdrant available, and no test references MySQL or JPA (`grep -ri "mysql\|jakarta.persistence\|jpa" src` is empty)

## 5. Verification

- [ ] 5.1 Start the app with `docker compose up qdrant` only and an empty volume: the catalog is imported. Restart: the import is skipped and no embedding request is logged. Run a `/search` request: it returns products
