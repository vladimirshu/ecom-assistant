## Context

Current products live only in Qdrant. ProductCategory is a closed jacket-style enum; the CSV has eight fields; ProductDto already contains every field required by the approved product dialog. There is no browsing controller. The frontend owns its editable build-time category navigation, while backend metadata must support matching ids and descendant scopes.

## Goals / Non-Goals

Goals: complete no-LLM browsing, root/descendant scope, compatible Product DTOs, expanded apparel fixtures and a recoverable revision-aware import.

Non-goals: new database/service, category-tree or product-detail API, attribute facets, semantic search redesign, cart/checkout, arbitrary live catalog editing or generating 300 products without curated coverage.

## Decisions

### 1. Separate garment style, navigation membership and image key

Keep category as the garment-style slug used by existing import/search mappings, extending it with sweater, hoodie, jeans and trousers. Keep ProductDto.category as its readable garment label, as current code does. Store direct browsing categoryIds separately. Backend configuration defines stable ids and parent ids, aligned with frontend src/data/categories.json; label/order/imageKey are not membership semantics. Initial branches have men/women roots, <root>-jackets-coats, <root>-jackets, <root>-coats, jacket style leaves, <root>-sweaters, <root>-jeans and <root>-trousers. A hoodie may be a garment style assigned under the appropriate sweaters branch. Coordinate these exact ids with the frontend file before implementation tests.

At import, compute a deduplicated ancestor-inclusive categoryPathIds set for each product from its direct memberships. The Qdrant payload retains direct categoryIds and expanded categoryPathIds. Filtering one requested category is then an indexed keyword membership match without runtime recursive joins or model calls. Shared products can belong to both roots. The unfiltered traversal still contains one point per SKU. An alternative of overloading the old flat category field would break jacket-style search and cannot represent both roots.

### 2. Stable Qdrant metadata traversal

Use Qdrant scroll/payload retrieval with deterministic SKU-derived point ids and an indexed categoryPathIds filter. Map payloads through the same Product DTO mapper as search. Do not call similarity search, embeddings or the existing search controller. Fetch enough metadata to determine whether another page exists; never issue a spurious next cursor for a final page. No ranking guarantee beyond stable unchanged-catalog traversal is exposed.

Use an opaque validated cursor envelope containing catalog revision, normalized category scope, limit and stored traversal position, protected against unsupported/tampered state. An implementation may use signed tokens or server-issued token records; it must satisfy the 30-minute running-service validity contract. No internal offset is exposed as a frontend construct. Requests with mismatched scope/limit or old revision return 400. Restart invalidation is allowed and follows the same restartable error path. Page limit is 24 by default, 1–100 allowed.

### 3. Versioned authored dataset and safe import

Extend the CSV with a comma-separated categoryIds column after category. Its canonical content and relevant category id/edge configuration determine desired catalogRevision. Validate rows, memberships and required initial assortment coverage before writes. Store the revision on every payload. A simple count of products that have category is no longer a sufficient skip condition.

On initial import or revision mismatch, idempotently upsert the desired valid SKU manifest first. Verify every expected SKU and required payload/revision against the intended manifest before marking readiness. Only after successful verification remove obsolete/legacy points. Never delete the legacy catalog before replacement upserts succeed. Fail startup/readiness on partial/failed import and retry on the next startup rather than serving or skipping a partial dataset. A completed revision match makes no embedding calls. This migration behavior is necessary because existing jacket-only products already have category and would otherwise skip the new assortment permanently.

A side-by-side collection/alias publication scheme is a possible future operational improvement; it is not required here. Readiness must prevent exposure of a partially upgraded same-collection dataset. Retain a recoverable dataset/backup and deploy compatible frontend/backend category changes together.

### 4. Assortment and product presentation

The dedicated `seed-catalog-quality` change owns the 200-design allocation, factual profiles, description quality, offline dataset checks and judged real-model acceptance. This change supplies compatible import/listing mechanics and small integration fixtures.

Curate enough products to exercise both roots, initial garment groups, material exclusions, prices and multiple pages. Descriptions should differ meaningfully by garment function, warmth, fabric and occasion. Include audience text in searchable content where appropriate, using readable category ancestry rather than opaque ids alone. Avoid unsupported marketing claims. Image keys remain reusable and independent of root membership; alpha image files are frontend assets. Existing ProductDto fields are sufficient; no structured material field or browsing ids need to be added to that public DTO.

### 5. API behavior and readiness

ProductsController handles GET /api/v1/products with validated category/limit/cursor. Valid empty categories return 200/empty/null. Invalid requests return the agreed error shape. Readiness/vector-store failures return 503, never successful partial pages. The API does not start EC2 or poll control itself; the frontend availability gate owns those requests. Browsing bypasses LLM/search budget checks while retaining any ordinary infrastructure edge controls.

## Migration Plan

1. Agree the exact frontend/backend stable category ids and parent edges; add parity fixtures.
2. Add metadata/model/parser/mapping support and curate the revised CSV plus image-key manifest.
3. Implement revision-aware upsert/verify/cleanup with interruption tests and make readiness depend on verification.
4. Add cursor-paged browsing and contract tests; verify an upgrade from the present jacket-only collection.
5. Deploy backend support before the dependent frontend; validate returned Product fields and category traversals.
6. For rollback, restore the previous catalog backup/config and compatible backend/frontend pair; do not leave newer cursor revisions valid against old data.

## Risks / Trade-offs

- [Frontend tree and backend scope drift] → Compare ids and parent edges in integration checks; labels/order remain independent.
- [Partial migration is mistaken for current data] → Desired revision/manifest verification gates readiness and restart skip logic; upsert before cleanup.
- [Qdrant scrolling/filter semantics differ by installed version] → Confirm against the project's dependency and run meaningful multi-page integration tests before relying on its cursor.
- [Examples look richer than the real dataset] → Validate catalog coverage and homepage example against actual imported products before release.
