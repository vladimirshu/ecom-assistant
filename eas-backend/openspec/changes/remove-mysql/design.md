## Context

The catalog is stored twice: in MySQL (`products` table, JPA entity `entity/Product`, `ProductRepository`) and in Qdrant (one point per SKU, with every product attribute in the payload). Only the import touches MySQL:

- `ProductImportService.importSampleProducts()` checks `productRepository.count() == 0` and `productRepository.existsByCategoryIsNull()`, then calls `saveAll`.
- Search (`QdrantRecommendDocumentRetriever` → `VectorStoreUtils.documentToProduct`) builds `Product` objects from the Qdrant payload only.

The vector-store check `isVectorStoreInitialized()` runs `vectorStore.similaritySearch(" ")`. That embeds the query text, so every startup sends one embedding request to OpenAI.

The application is about to be deployed publicly on low-cost AWS infrastructure, and every stateful service needs hosting, credentials and startup ordering. The next steps are rate limiting and an AWS deployment, and they are simpler with one data store.

## Goals / Non-Goals

**Goals:**
- Remove the MySQL server, the JPA stack and their configuration from the backend, the tests and `docker-compose.yml`.
- Keep the import idempotent and safe to re-run, with Qdrant as the only source of truth.
- Make a startup with current data free: no embedding requests.

**Non-Goals:**
- Replacing MySQL with another database (DynamoDB, etc.). Chat memory stays in process; storing it durably is a separate decision for the deployment change.
- Detecting edits to existing CSV rows. As today, the import only re-runs when the collection is empty or holds points without a category. To pick up catalog edits, delete the collection or its volume.
- Changing the `/search` API or the Qdrant payload format.

## Decisions

**1. Qdrant is the only product store.**
The payload already contains every attribute (`sku, title, category, colors, sizes, price, description, imageKey`), and `documentToProduct` reconstructs a complete `Product` from it. The catalog is a 100-row CSV bundled with the application, so the CSV is the durable source and Qdrant is a rebuildable index of it. A relational copy adds nothing.
- Alternative: keep MySQL for a future admin/catalog-management feature. Rejected for now: no such feature is planned, and if one is added it can choose its own store.

**2. Import trigger computed from Qdrant point counts.**
Use `QdrantClient.countAsync(collection, filter, exact=true)`:
- `total = count(no filter)`. If the collection does not exist, treat it as 0.
- `withoutCategory = count(isNull("category"))`.
- Import when `total == 0 || withoutCategory > 0`.

This replaces the three MySQL/probe conditions with one rule. It keeps the existing upgrade path, because `deletePointsWithoutCategory()` still runs before the upsert. It also removes the `similaritySearch(" ")` probe, so the check never calls the embedding model.
- Alternative: keep the `similaritySearch` probe. Rejected: it costs an embedding request per startup and only answers "empty or not".
- Alternative: compare `total` with the number of valid CSV rows, so added or removed rows trigger a re-import. Not needed now (see Non-Goals). It is cheap to add later, because parsing the CSV makes no network calls.

**3. `Product` becomes a plain class in `catalog`.**
Remove the `@Entity`/`@Table`/`@Id`/`@Column`/`@Convert` annotations and keep Lombok `@Data @NoArgsConstructor`. Move it to `com.ecomassistant.catalog` next to `ProductCategory`, because `entity` would no longer describe it. `ProductCategoryConverter` exists only for JPA and is deleted. `ProductCategory.fromSlug` already covers the conversion `documentToProduct` needs.
- Alternative: keep the `entity` package to keep the diff small. Rejected: the package name would be misleading, and the move is a mechanical import change.

**4. No datasource auto-configuration.**
Removing `spring-boot-starter-data-jpa` and `mysql-connector-j` removes DataSource, Hibernate and transaction-manager auto-configuration from the classpath. `DatabaseConfig` (`@EnableJpaRepositories`, `@EntityScan`, `@EnableTransactionManagement`) is deleted. No code uses `@Transactional`.

**5. Tests use Qdrant only.**
Delete the `MySQLContainer` and its `@DynamicPropertySource` datasource properties from the integration test bases. `ProductImportServiceIntegrationTest` asserts on `qdrantPointCount()`. The round-trip test builds its `Product` from the parsed CSV instead of `findById`. The upgrade test simulates legacy data only in Qdrant: it upserts a point without `category` next to the current points. That point alone must trigger the re-import, which then leaves exactly one point per SKU. The restart test verifies that the initialization check makes no embedding requests at all.

## Risks / Trade-offs

- [Qdrant data loss means a full re-embedding of the catalog] → It happens automatically at the next startup. For 100 products it costs a fraction of a cent and a few seconds.
- [Existing local `mysql-data` volumes are left behind] → Harmless. Mention `docker volume rm` in the change notes.
- [Any later need for relational data] → Add a store for that feature when it exists. Nothing in the current design depends on its absence.
- [`countAsync` on a missing collection throws] → Check `collectionExistsAsync` first, as `deletePointsWithoutCategory()` already does. `initialize-schema: true` creates the collection at startup anyway.

## Migration Plan

1. Deploy the backend without MySQL. On the first start against an existing Qdrant volume, every point has a category, so the import is skipped. Against an empty Qdrant, the catalog is imported from the CSV.
2. Stop and remove the MySQL container and volume.
3. Rollback: revert the commit. The JPA import would see an empty table and re-run the upsert import, which is idempotent in Qdrant.
