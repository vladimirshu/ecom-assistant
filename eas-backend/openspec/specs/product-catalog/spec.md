# product-catalog Specification

## Purpose
TBD - created by syncing change storefront-search-api. Update Purpose after archive.
## Requirements
### Requirement: Product attributes
Every product SHALL have a SKU, a title (1 to 80 characters), a category from the catalog category list, one or more colors, one or more sizes, a positive price in EUR, a description and an image key.

#### Scenario: Complete product
- **WHEN** the catalog is imported
- **THEN** every stored product has a non-blank SKU, title, category, at least one color, at least one size, a price greater than 0, a non-blank description and a non-blank image key

### Requirement: Catalog categories
The system SHALL support exactly these category slugs: `puffer`, `parka`, `coat`, `rain`, `softshell`, `fleece`, `track`, `technical`, `field`, `bomber`, `denim`, `leather`, `windbreaker`.

#### Scenario: Known category
- **WHEN** a CSV row has category `puffer`
- **THEN** the product is imported with category `puffer`

#### Scenario: Unknown category
- **WHEN** a CSV row has category `hoodie`
- **THEN** the row is skipped and a warning naming the SKU is logged

### Requirement: Catalog CSV format
The sample import SHALL read a semicolon-separated CSV with the header `sku;title;category;colors;sizes;price;image;description`, where `colors` and `sizes` are comma-separated lists and `image` is optional, and SHALL skip and log rows with a missing required field or an unparsable price.

#### Scenario: Row parsed
- **WHEN** the CSV contains `JKT0004;Classic Bomber Jacket;bomber;white,green,beige;XS,S,L,XL,M;66.93;;Classic bomber jacket with ribbed hems…`
- **THEN** a product `JKT0004` with title `Classic Bomber Jacket`, category `bomber` and price `66.93` is imported

#### Scenario: Missing title
- **WHEN** a CSV row has an empty `title`
- **THEN** the row is skipped and a warning naming the SKU is logged

### Requirement: Default category image
When the `image` column of a row is empty, the system SHALL set the product's image key to `categories/<category>.webp`; otherwise it SHALL use the given value unchanged.

#### Scenario: Image omitted
- **WHEN** a row with category `bomber` has an empty `image`
- **THEN** the product's image key is `categories/bomber.webp`

#### Scenario: Image provided
- **WHEN** a row has `image` = `products/JKT0004/front.webp`
- **THEN** the product's image key is `products/JKT0004/front.webp`

### Requirement: Public image URL
The system SHALL expose a product's image as `imageUrl` = the configured `ecom.assets.base-url` followed by the image key, with exactly one `/` between them, and SHALL default the base URL to `/images/`.

#### Scenario: Default base URL
- **WHEN** `ecom.assets.base-url` is not set and the image key is `categories/parka.webp`
- **THEN** `imageUrl` is `/images/categories/parka.webp`

#### Scenario: CDN base URL
- **WHEN** `ecom.assets.base-url` is `https://cdn.example.com/catalog` and the image key is `categories/parka.webp`
- **THEN** `imageUrl` is `https://cdn.example.com/catalog/categories/parka.webp`

### Requirement: Vector-store product payload
Each product SHALL be stored in the vector store with payload fields `sku`, `title`, `category`, `colors`, `sizes`, `price`, `description` and `imageKey`, and with an embedded text containing its title, category, colors, sizes and description, so that a document retrieved from the vector store maps back to a product with all attributes.

#### Scenario: Round trip
- **WHEN** product `JKT0004` is retrieved by similarity search
- **THEN** the mapped product has the same title, category, colors, sizes, price, description and image key as the imported product

#### Scenario: Title is searchable
- **WHEN** the retriever is called with query text `bomber jacket`
- **THEN** products with category `bomber` are among the results

### Requirement: Idempotent catalog import
At startup the system SHALL import the sample catalog when the products table is empty, the vector store is empty, or any stored product has no category, and SHALL upsert products by SKU in both stores, so that repeating the import does not create duplicates.

#### Scenario: Fresh start
- **WHEN** the application starts with an empty database and an empty vector store
- **THEN** all valid CSV products are stored in both

#### Scenario: Restart with current data
- **WHEN** the application restarts and every stored product has a category
- **THEN** no import is performed and no embedding requests are made

#### Scenario: Upgrade from the previous schema
- **WHEN** the application starts and the stored products have no category
- **THEN** all products are upserted with the new attributes and the vector store holds exactly one point per SKU

### Requirement: Product representation in API responses
The system SHALL represent a product in API responses as an object with `sku`, `title`, `category`, `colors` (array of strings in catalog order), `sizes` (array ordered `XXS, XS, S, M, L, XL, XXL, XXXL`, unknown sizes last in catalog order), `price` (number, EUR), `description` and `imageUrl`, and SHALL NOT expose the internal image key or persistence fields.

#### Scenario: Sizes ordered
- **WHEN** product `JKT0001` with sizes `M,XS,S` is returned
- **THEN** its `sizes` are `["XS", "S", "M"]`

#### Scenario: Colors as array
- **WHEN** product `JKT0001` with colors `navy,red` is returned
- **THEN** its `colors` are `["navy", "red"]`
