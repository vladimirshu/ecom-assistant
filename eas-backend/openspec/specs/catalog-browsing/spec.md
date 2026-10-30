# catalog-browsing Specification

## Purpose
TBD - created by syncing change apparel-catalog-browsing. Update Purpose after archive.

## Requirements
### Requirement: Catalog listing endpoint
The backend SHALL expose GET `/api/v1/products` with optional `category`, `limit` and `cursor` query parameters. A successful response SHALL be `{ "products": Product[], "nextCursor": string | null }`, using exactly the Product representation returned by search. The default limit SHALL be 24 and allowed integer limits SHALL be 1 through 100. Missing category SHALL select the full apparel assortment. Listing SHALL NOT call the search pipeline, embedding model or any LLM, and SHALL NOT consume the search rate limit or daily LLM budget.

#### Scenario: First page
- **WHEN** GET /api/v1/products?limit=24 is called on a ready catalog
- **THEN** up to 24 stored products are returned in deterministic traversal order, with a next cursor only if more remain

#### Scenario: Search quota exhausted
- **WHEN** the search daily budget is exhausted and the ready catalog is requested
- **THEN** browsing still returns its normal catalog response without consuming search quota

### Requirement: Hierarchical category scope
The backend SHALL maintain the agreed stable Men/Women browsing ids and parent relationships matching the frontend category configuration. Selecting any root, intermediate node or leaf SHALL return products assigned directly to that node or any descendant, once per SKU. Products assigned exclusively to the other root SHALL be excluded. A shared/unisex product MAY belong to both branches but SHALL appear only once in a combined listing. No backend category-tree endpoint SHALL be required; display labels, sibling order and optional representative image keys remain frontend configuration concerns.

#### Scenario: Root selection
- **WHEN** category=women is requested
- **THEN** all products with direct or descendant Women membership are eligible and Men-only products are excluded

#### Scenario: Intermediate selection
- **WHEN** category=men-jackets-coats is requested
- **THEN** directly assigned products, men's coats and every nested men's jacket style are eligible without duplicate SKUs

#### Scenario: Shared product
- **WHEN** one SKU belongs to both Men and Women and no category is selected
- **THEN** that SKU appears once in the traversal

### Requirement: Stable complete cursor traversal
For an unchanged catalog revision and category scope, following successive cursors SHALL visit every eligible product once without omissions or duplicates. The backend SHALL use a stable stored-point traversal order independent of semantic rank; it SHALL NOT promise price or alphabetical ordering. Each non-null cursor SHALL be opaque to the client and bound to the category scope, requested limit, traversal position and catalog revision. A request SHALL use the same explicit/default limit and category as its first page. The last page SHALL have nextCursor=null, including an empty valid category.

#### Scenario: Multiple pages
- **WHEN** a ready unchanged category contains 57 products and limit=24
- **THEN** its traversal returns pages of 24, 24 and 9 distinct products and only the final cursor is null

#### Scenario: Category cursor mismatch
- **WHEN** a cursor from Men is sent with category=women
- **THEN** the request returns HTTP 400 and no products are traversed under the mismatched token

#### Scenario: Catalog changed between pages
- **WHEN** a supplied cursor belongs to an earlier catalog revision
- **THEN** HTTP 400 is returned so the client can restart the selected category

### Requirement: Listing validation and empty categories
Unknown category ids, invalid limits, malformed/expired cursors and category/limit cursor mismatches SHALL return HTTP 400 with `{ "error": string }` and no search/LLM calls. A known category with no eligible products SHALL return HTTP 200 with an empty products array and null nextCursor. Errors SHALL NOT expose internal cursor state, persistence credentials or exception traces. Cursor validity SHALL last at least 30 minutes for an unchanged running service; a restart MAY invalidate issued cursors and SHALL report that as HTTP 400.

#### Scenario: Unknown category
- **WHEN** category=unknown-branch is supplied
- **THEN** HTTP 400 with a concise error body is returned

#### Scenario: Valid empty branch
- **WHEN** a configured category currently has zero members
- **THEN** HTTP 200 returns products=[] and nextCursor=null

#### Scenario: Page size changed
- **WHEN** a limit=24 cursor is supplied with limit=12
- **THEN** HTTP 400 is returned rather than changing the traversal implicitly

### Requirement: Readiness and failures
The listing endpoint SHALL serve only a verified complete current catalog revision. Until import/upgrade verification completes or while required vector-store access is unavailable, listing SHALL fail with HTTP 503 rather than returning an apparently complete partial assortment. It SHALL reuse the existing service readiness model and standard API failure handling, without starting infrastructure itself. After an interrupted import, startup/recovery SHALL complete or retry the catalog upgrade before reporting ready.

#### Scenario: Partial import
- **WHEN** only some expected products have been upserted and import fails
- **THEN** the catalog is not marked ready and GET /api/v1/products does not return that partial data as a successful collection

### Requirement: Frontend contract parity
The implementation SHALL provide a validation/integration check comparing frontend and backend category ids and parent edges. It SHALL reject uncoordinated new ids or hierarchy changes before integration deployment. Label, sibling-order or representative-image changes alone SHALL NOT require backend membership changes. Listing and search SHALL return the same attribute values for the same SKU from the same catalog revision, so the frontend product dialog needs no additional endpoint.

#### Scenario: Labels change
- **WHEN** only a frontend category label or sibling order is edited
- **THEN** stable category requests and descendant scope remain unchanged

#### Scenario: Parent relationship drifts
- **WHEN** a frontend category is reparented without matching backend category scope
- **THEN** the parity check identifies the changed id/edge before deployment
