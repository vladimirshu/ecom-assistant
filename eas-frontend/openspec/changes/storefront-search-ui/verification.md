# Storefront verification

## Live smoke test, 2026-11-04

An isolated local Docker Compose backend and Qdrant loaded the repository’s
`seed-catalog-quality/v1` CSV. Chromium used the normal storefront routes and
same-origin proxy. The UI rendered every product in API order and displayed the
first product’s exact SKU and description in its dialog on each successful query.

| Request | Interpretation | First returned SKUs |
| --- | --- | --- |
| navy jeans | navy jeans | M-JNS-10, W-JNS-11, M-JNS-15 |
| actually make it black | black jeans | W-JNS-09, M-JNS-11, M-JNS-05 |
| Un pantalon confortable pour le bureau, avec de l’élasticité | comfortable office trousers with elasticity | M-TRS-04, W-TRS-04, M-TRS-15 |

The refinement used the same conversation ID and `clearMemory=false`. New search
reset the workspace and sent the French request with a new conversation ID.
The homepage example uses the first two observed cards from each jeans turn.

Live catalog checks returned 24 products plus a cursor for `women` and
`women-jackets`, five products for `women-puffer`, and 15 for `women-jeans`.
The frontend category tree has the same IDs and parent relationships as the API.

## Findings affecting release

- The first live smoke test returned HTTP 500 for a query without a price. Both
  backend query-analysis validators now use one schema that accepts explicit
  `priceConstraint: null` and still validates non-null objects.
- The rain-jacket request succeeded after the schema fix. The subsequent
  “I also need it warm for winter” request returned 422 because the model
  considered rain protection and winter warmth contradictory.
- The black-jeans refinement included one indigo-only SKU at rank five
  (`M-JNS-01`). The UI showed the backend's returned list in order.
- The example has been checked against a live run of the authored seed. Full
  model relevance acceptance and catalog publication remain open in the backend
  seed-catalog-quality checklist.

## Artwork

The manifest records each asset's built-in image generation prompt, reference,
source and reviewed WebP export. The 18 files have 1200×1500 canvases, meaningful
alpha and sizes below 200 KB. The mixed garment grid was checked over Sage paper,
wash, the dialog background and a dark contrast surface. The production build
copies the reviewed WebPs byte for byte.
