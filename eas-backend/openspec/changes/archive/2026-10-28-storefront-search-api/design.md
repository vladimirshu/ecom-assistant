## Context

The storefront frontend (separate Next.js project in `../frontend/`, change `storefront-search-ui`) renders a premium shop: product cards with an image, title and price, and a "We understood" bar showing the analyzed query. It talks to the backend only through `GET /search` via a same-origin proxy.

State after the in-flight changes (`simplify-input-validation` → `qdrant-recommend-retriever` → `query-analysis-guardrail` → `rag-product-filtering`), which this change builds on and treats as the source of truth rather than the current code:
- `QueryAnalysisGuardrailAdvisor` runs call #1 and produces a validated `QueryAnalysis`; a rejection throws `GuardrailRejectedException` → 422 `{ suggestedPrompts }`.
- `QdrantRecommendDocumentRetriever` returns documents whose metadata is mapped to `Product` by `VectorStoreUtils.documentToProduct`.
- `SkuOutputGuardrail` produces the final ordered product list; the controller returns `ChatResponseDto(products, suggestedPrompts)`.
- The import is "all or nothing" on startup: skipped if both stores contain anything. Qdrant point IDs are random UUIDs.

The catalog (100 rows) is outerwear only: puffers (25), urban water-resistant shells (12), track (11), fleece (11), trench/overcoats (5), cycling (5), softshell (5), cashmere/wool (7), ski (3), waxed field (3), denim, windbreaker, hiking, camo tactical (2 each), parka, leather, rain, convertible, bomber (1 each).

## Goals / Non-Goals

**Goals:**
- Give every product what a shop card needs: title, category, image.
- Make images relocatable (static directory today, blob storage/CDN later) without data migration.
- Show the client what the system understood, without an extra LLM call.
- Keep the catalog import safe to re-run after a schema change.

**Non-Goals:**
- Per-product photography and image upload/management.
- Category browsing or category filters in search (the product concept is "describe, don't browse"); category is presentation and embedding context only.
- CORS: the frontend calls the backend through its own server-side proxy, so the browser never makes cross-origin requests.
- Multi-currency or localized product data (EUR and English catalog only; the customer's input language is unrestricted).
- Pagination, product detail endpoint, cart.

## Decisions

**1. Store an image key, not a URL.**
`products.image_key` holds a storage-relative object key (`categories/puffer.webp`, later `products/JKT0004/front.webp`). The public URL is computed at response time: `ecom.assets.base-url` + key.
- Today: base URL `/images/` → `/images/categories/puffer.webp`, served by the frontend from `public/images/categories/`. The URL is relative, so it resolves against the frontend origin.
- Production: upload the same keys to a bucket (S3/GCS/Azure Blob) behind a CDN and set `ecom.assets.base-url=https://cdn.example.com/catalog/`. No row changes.
- Alternatives: full URL in the DB (couples data to one host/environment, every CDN move is a migration); key derived from SKU by convention only (no way to share one image across products, as we do now with category images); pre-signed URLs (unnecessary for public catalog images).
- Production evolution, for later: a `product_images(sku, key, role, position, alt_text, width, height)` table for several images per product, the key containing a content hash (`products/JKT0004/front-3f9a1c.webp`) so CDN caching can be immutable, and image variants produced by the CDN's resize service or at upload.

**2. Category image as data default, not a frontend rule.**
The import writes `categories/<category>.webp` into `image_key` when the CSV `image` column is empty. The frontend never guesses image paths, so switching a single product to its own photo is a one-cell CSV edit.

**3. Category list.**
`catalog/ProductCategory` enum with slug and display name, persisted as the slug (JPA `AttributeConverter`). The list groups the 20 description types into 13 visually distinct outerwear categories:

| slug | name | catalog rows mapped |
|---|---|---|
| `puffer` | Puffer Jackets | quilted puffer |
| `parka` | Parkas | travel parka |
| `coat` | Coats | trench/overcoat, heritage wool-blend, cashmere blend |
| `rain` | Rain Jackets | urban explorer water-resistant, eco rain, convertible |
| `softshell` | Softshell Jackets | minimalist softshell |
| `fleece` | Fleece Jackets | hybrid fleece |
| `track` | Track Jackets | retro track |
| `technical` | Technical Jackets | cycling, ski, mountain hiking |
| `field` | Field Jackets | waxed field, camo tactical |
| `bomber` | Bomber Jackets | classic bomber |
| `denim` | Denim Jackets | vintage denim |
| `leather` | Leather Jackets | luxury leather |
| `windbreaker` | Windbreakers | sporty windbreaker |

These are the "major categories" of this catalog. Broader apparel categories (knitwear, shirts, trousers…) are added to the enum and to the frontend images together when the catalog contains such products. The slug list is a cross-project contract with `../frontend/public/images/categories/`.

**4. Titles and categories are curated in the CSV.**
They are written once (LLM-assisted, reviewed by a person) and committed to the CSV, not generated at import. Import stays deterministic, offline and free. Titles follow `<Distinctive adjective> <Type>` (e.g. `Urban Explorer Rain Jacket`, `Alpine Quilted Puffer`). Rows sharing a description type get different titles where their materials or features differ.

**5. Vector-store payload and embedded text.**
The payload gains `title`, `category`, `imageKey`. The embedded text becomes `Title. Category name. Colors: … Sizes: … Description` (SKU and price are removed: they are noise for similarity, and price is handled by the payload filter). `documentToProduct` maps all fields.

**6. Idempotent upsert import.**
Point ID = `UUID.nameUUIDFromBytes(sku)`, so `vectorStore.add` overwrites the point for a SKU. MySQL `saveAll` with SKU as the primary key merges. Import trigger: products table empty, OR vector store empty, OR `productRepository.existsByCategoryIsNull()`. The last condition is the upgrade path from the current schema. New columns are nullable in the DDL (Hibernate `update` cannot add NOT NULL columns to populated tables); non-null is enforced by import validation. `sizes` column widened to 50.

**7. API DTOs instead of the entity.**
`ProductDto(sku, title, category, colors[], sizes[], price, description, imageUrl)` built by a `ProductDtoMapper` that owns the size ordering and URL resolution. `ChatResponseDto(products, interpretation, suggestedPrompts)`. `SearchInterpretationDto(searchQuery, negativeExamples, priceConstraint, sortByLowestPrice)` maps from `QueryAnalysis` and omits `rejectionReasons`, `suggestedPrompts` and `allowed`. The 422 body stays `{ products: null, interpretation: null, suggestedPrompts }`.

**8. Getting the analysis out of the advisor chain.**
`QueryAnalysisGuardrailAdvisor` already puts retrieval parameters into the request context. It additionally stores the validated `QueryAnalysis` under the context key `ecom_query_analysis`. `ProductSearchService` reads it from `ChatClientResponse.context()` (`.call().chatClientResponse()` then `BeanOutputConverter`, the fallback already foreseen in `rag-product-filtering`). `EmptyRetrievalAdvisor` passes the context through, so the empty-result response also carries the interpretation. Because it reuses the analysis from call #1, the interpretation needs no extra LLM call.

**9. Language of suggestions.**
`prompts/query-analysis.md` instructs the model to write `suggestedPrompts` in the language of the latest customer message, and `searchQuery` stays English for embeddings. The generic fallback added by `QueryAnalysisValidator` stays English, because the system cannot localize it without an LLM.

## Risks / Trade-offs

- [Category image URLs depend on the frontend having the files] → The slug list is documented in both changes, and the frontend falls back to a default image on load error.
- [Relative `imageUrl` only works when served by the frontend origin] → That is the deployment model. Any other consumer sets an absolute `ecom.assets.base-url`.
- [Re-embedding on upgrade costs 100 embedding calls once] → Negligible; it only happens when products lack a category.
- [Changed embedded text shifts similarity scores and the tuned threshold] → Re-check `ecom.search.similarity-threshold` against the retrieval integration tests after the change.
- [Reading the advisor context from `ChatClientResponse` under Spring AI 2.0.2] → Covered by the end-to-end test. If the context is not propagated, the advisor writes the analysis into `ChatResponse` metadata instead.
- [Breaking response shape] → No existing clients; the frontend is built against this contract.

## Migration Plan

1. Deploy the backend. On startup, `existsByCategoryIsNull()` triggers the upsert import, which fills the new columns and re-upserts the Qdrant points under deterministic IDs.
2. Old random-ID points from the previous import remain in Qdrant as duplicates → the import deletes the collection's points that have no `category` payload before upserting (local/dev data only; documented in the README).
3. Rollback: the previous version ignores the extra columns and payload fields.
