## Why

A separate storefront frontend (`../frontend/openspec/changes/storefront-search-ui`) will present search results as a premium fashion shop and make the core idea visible: the customer describes what they want, and the system shows what it understood and the best matches. The current product model (SKU, comma-separated colors and sizes, price, description) has no title, category or image, and the search response discards the query analysis, so the frontend can neither render a shop-like product card nor show "what we understood".

## What Changes

- Add `title`, `category` and `imageKey` to the product model, the database schema, the sample CSV and the vector-store payload; include title and category in the embedded text.
- Introduce a closed list of catalog categories (outerwear types present in the catalog), each with a default category image key `categories/<category>.webp`.
- Resolve `imageKey` to a public `imageUrl` with a configurable assets base URL (`ecom.assets.base-url`); the default points at the frontend's static directory, so moving images to blob storage/CDN later is a configuration change.
- Make the sample import an idempotent upsert by SKU (deterministic vector-store point IDs) that also re-runs when stored products predate the new fields.
- **BREAKING**: `/search` returns a dedicated product DTO instead of the JPA entity: `colors` and `sizes` become arrays (sizes in canonical order), and `title`, `category`, `imageUrl` are added.
- Return the query analysis to the client as `interpretation` (English search query, negative examples, price constraint, lowest-price flag) on successful searches.
- Suggested prompts on a rejected request are written in the language of the customer's message.
- Product filtering (LLM call #2) receives title and category of each candidate.

## Capabilities

### New Capabilities
- `product-catalog`: product attributes, categories, CSV import format and idempotent re-import, vector-store payload and embedded text, image key and public image URL resolution.

### Modified Capabilities
- `query-analysis-guardrail`: interpretation returned to the client; suggested prompts in the customer's language.
- `rag-product-filtering`: product-filtering input includes title and category; end-to-end response uses the new product DTO and includes `interpretation`.

## Impact

- Code: `entity/Product`, new `catalog/ProductCategory`, `service/ProductImportService`, `service/DataInitializerRunner`, `VectorStoreUtils`, new `dto/ProductDto`, `dto/SearchInterpretationDto`, `dto/ChatResponseDto`, new `config/EcomAssetsProperties`, `service/ProductSearchService`, `controller/ProductsController`, `rag/ProductFilterQueryAugmenter`, `prompts/query-analysis.md`, `prompts/product-filter.md`.
- Data: `sampledata/apparel_jackets_sample.csv` gains `title;category;image` columns; MySQL `products` gains `title`, `category`, `image_key` columns (Hibernate `ddl-auto: update`); Qdrant points are re-upserted with the new payload.
- API: `/search` 200 response shape (breaking, no existing clients); 422 body unchanged.
- Configuration: `ecom.assets.base-url` (default `/images/`).
- Cross-project contract: category slugs and image file names must exist in `../frontend/public/images/categories/`.
- Depends on: `rag-product-filtering` (and transitively `query-analysis-guardrail`, `qdrant-recommend-retriever`, `simplify-input-validation`).
