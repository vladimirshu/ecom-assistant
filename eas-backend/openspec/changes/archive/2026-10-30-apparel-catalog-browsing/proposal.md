## Why

The approved storefront browses real men's and women's apparel through root and nested categories. The backend currently imports only jacket styles and has no product-listing API, so the frontend cannot implement that design from the existing search endpoint alone.

## What Changes

- Add category-aware cursor-paged GET /api/v1/products with no semantic retrieval or model calls.
- Support the sample assortment expansion to sweaters, hoodies, jeans and trousers for Men and Women; authored content quality, 200-product allocation and real relevance acceptance are owned by `seed-catalog-quality`. Retain jacket styles and exclude underwear/lingerie/undershirts/tank tops/bras. T-shirts remain a future extension.
- Define stable hierarchical browsing ids, product membership and direct/descendant listing separately from garment-style category and reusable image keys.
- Preserve the current Product DTO used by search and product dialogs; no product-detail or category-tree API is needed.
- **BREAKING data import:** extend the authored CSV and Qdrant payload with browsing membership and a catalog revision; require a controlled, retry-safe upgrade of existing jacket-only data.
- Align ids and parent edges with the editable frontend category tree; label/order-only changes remain frontend concerns.

## Capabilities

### New Capabilities
- `catalog-browsing`: category-aware product listing, deterministic traversal, category-bound cursors, validation, readiness and no-LLM behavior.

### Modified Capabilities
- `product-catalog`: expanded garment styles, browsing membership, CSV/payload format and revision-aware recoverable import.

## Impact

- Catalog model/category definitions, CSV resources/parser, vector-store payload mapping and import lifecycle.
- New products controller/listing service and Qdrant metadata queries; existing search path, Product DTO and interpretation contract remain compatible.
- Frontend dependency: `../eas-frontend/openspec/changes/storefront-search-ui`; its src/data/categories.json owns navigation labels/order while both sides agree on stable ids and hierarchy.
- Qdrant remains the only product persistence store. No relational database, new hosting service, search-quota change, or new model call is introduced for browsing.
- This change creates implementation specifications only; no application code or catalog reimport runs during authoring.
