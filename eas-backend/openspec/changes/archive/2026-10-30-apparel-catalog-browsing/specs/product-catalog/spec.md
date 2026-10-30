## MODIFIED Requirements

### Requirement: Product attributes
Every product SHALL have a SKU, a title (1 to 80 characters), a category from the catalog category list, one or more colors, one or more sizes, a positive price in EUR, a description, an image key and at least one valid direct browsing category id.

#### Scenario: Complete product
- **WHEN** the catalog is imported
- **THEN** every stored product has a non-blank SKU, title, category, at least one color, at least one size, a price greater than 0, a non-blank description, a non-blank image key and valid browsing memberships

### Requirement: Catalog categories
The system SHALL retain the existing garment-style slugs puffer, parka, coat, rain, softshell, fleece, track, technical, field, bomber, denim, leather and windbreaker, and SHALL also support sweater, hoodie, jeans and trousers. These slugs SHALL describe garments independently of hierarchical Men/Women browsing ids. The initial authored assortment SHALL include both Men and Women branches with Jackets & Coats, Sweaters, Jeans and Trousers represented. Underwear, briefs, lingerie, undershirts, tank tops and bras SHALL be excluded. Unknown garment styles SHALL still be skipped with a warning naming the SKU. T-shirts SHALL remain outside the initial required assortment.

#### Scenario: Existing jacket
- **WHEN** an authored row has style puffer and a valid browsing membership
- **THEN** it remains a valid puffer product

#### Scenario: Expanded garment
- **WHEN** an authored row has style hoodie and a valid Women sweaters membership
- **THEN** it is imported as a hoodie in that branch

#### Scenario: Unknown style
- **WHEN** a row has an unsupported category value
- **THEN** the row is skipped with a warning identifying its SKU

### Requirement: Catalog CSV format
The authored import SHALL use semicolon-separated header `sku;title;category;categoryIds;colors;sizes;price;image;description`. categoryIds, colors and sizes SHALL be comma-separated lists; image SHALL remain optional. Direct categoryIds SHALL resolve against the agreed backend browsing configuration. Rows missing required fields, using unknown membership ids or containing unparsable/non-positive prices SHALL be skipped and logged with their SKU. Title length SHALL remain 1–80 characters. An old eight-column dataset SHALL require explicit upgrade before it can be accepted as the current catalog revision; memberships SHALL NOT be guessed from a jacket style alone.

#### Scenario: Expanded row
- **WHEN** a row has `category=jeans` and `categoryIds=women-jeans`
- **THEN** it is imported with jeans style and direct Women jeans membership

#### Scenario: Missing title
- **WHEN** an authored row has an empty title
- **THEN** it is skipped with a warning naming the SKU

#### Scenario: Unknown membership
- **WHEN** an authored row names a category id absent from the agreed tree
- **THEN** it is skipped with a warning and cannot silently become an unscoped product

### Requirement: Vector-store product payload
Each product SHALL retain sku, title, category, colors, sizes, price, description and imageKey payload fields and add direct categoryIds, ancestor-inclusive categoryPathIds and catalogRevision. Its embedded text SHALL contain title, readable garment category, colors, sizes and description, and meaningful audience/ancestry text where applicable. Category metadata used for browsing SHALL be queryable without embeddings. Search retrieval and direct catalog listing SHALL map the same product payload through the same public Product representation, without exposing internal membership/revision/image-key fields in that DTO.

#### Scenario: Round trip
- **WHEN** a product is returned from search or catalog listing
- **THEN** its title, readable category, colors, sizes, EUR price, description and resolved imageUrl agree

#### Scenario: Ancestor membership
- **WHEN** a product directly belongs to women-jeans
- **THEN** its categoryPathIds includes women-jeans and women for non-semantic metadata listing

### Requirement: Idempotent catalog import
The system SHALL use a desired catalog revision and valid SKU manifest to decide whether the authored dataset is fully imported. Initial empty stores, legacy/missing payloads, revision mismatch, missing expected SKUs or an interrupted prior import SHALL trigger import/recovery. Products SHALL be upserted by deterministic SKU-derived ids without duplicates. No embeddings SHALL be requested merely to decide whether an already complete current revision can be skipped. Qdrant SHALL remain the only persistent product store.

Replacement products SHALL be upserted and verified before obsolete or legacy points are removed. Any failed write or incomplete manifest SHALL leave the revision unverified, SHALL prevent ready status and successful catalog listing, and SHALL be retried on the next startup. Having some points with category populated SHALL NOT cause a partial upgrade to be skipped. Completing the manifest and cleanup SHALL leave exactly the desired products, one point per SKU.

#### Scenario: Fresh start
- **WHEN** the vector store is empty
- **THEN** all valid authored products are imported and verified before readiness

#### Scenario: Current restart
- **WHEN** all desired SKUs and required payloads match the current revision
- **THEN** import is skipped without embedding calls

#### Scenario: Jacket-only upgrade
- **WHEN** the stored jacket-only catalog has category populated but no current browsing/revision metadata
- **THEN** the expanded catalog is imported and verified rather than skipped

#### Scenario: Interrupted upgrade
- **WHEN** upserting the new catalog fails after some products were written and the application restarts
- **THEN** the incomplete revision is detected and retried, with no successful partial catalog response

#### Scenario: Safe replacement
- **WHEN** replacement upserts fail before the full desired manifest is verified
- **THEN** legacy points are not deleted before those replacement writes complete

#### Scenario: No relational database
- **WHEN** the application starts without a relational database
- **THEN** import and verified catalog access still use Qdrant alone

## ADDED Requirements

### Requirement: Direct browsing membership
Every imported product SHALL have one or more direct browsing category ids, and the backend SHALL compute a unique ancestor-inclusive membership set from the configured hierarchy. A direct membership MAY be a root, intermediate node or leaf. Invalid or cyclic hierarchy configuration SHALL fail validation before import. Frontend labels and image keys SHALL NOT determine membership.

#### Scenario: Shared roots
- **WHEN** a valid unisex SKU is assigned to Men and Women descendants
- **THEN** it has both ancestor paths but remains one stored point and one product in an unfiltered traversal

#### Scenario: Invalid hierarchy
- **WHEN** category parentage forms a cycle or references a missing parent
- **THEN** import configuration validation fails before products are written
