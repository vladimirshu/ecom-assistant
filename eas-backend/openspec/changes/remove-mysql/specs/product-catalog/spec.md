## MODIFIED Requirements

### Requirement: Idempotent catalog import
At startup the system SHALL import the sample catalog into the vector store when the vector store holds no products or any stored product has no category, and SHALL upsert products by SKU, so that repeating the import does not create duplicates. The vector store SHALL be the only persistent store of products. Deciding whether to import SHALL NOT make embedding requests.

#### Scenario: Fresh start
- **WHEN** the application starts with an empty vector store
- **THEN** all valid CSV products are stored in the vector store, one point per SKU

#### Scenario: Restart with current data
- **WHEN** the application restarts and every stored product has a category
- **THEN** no import is performed and no embedding requests are made

#### Scenario: Upgrade from the previous schema
- **WHEN** the application starts and the vector store contains products without a category
- **THEN** all products are upserted with the current attributes and the vector store holds exactly one point per SKU

#### Scenario: No relational database required
- **WHEN** the application starts without any relational database configured or reachable
- **THEN** startup succeeds and searches return products built from the vector-store payload
