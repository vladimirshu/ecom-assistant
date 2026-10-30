## 1. Category and product contract

- [x] 1.1 Agree the exact Men/Women browsing ids and parent relationships with the frontend local tree; add backend configuration and a parity validator that ignores label/order/image-only edits
- [x] 1.2 Extend garment styles for sweater, hoodie, jeans and trousers; add direct categoryIds, computed categoryPathIds and catalogRevision to the catalog model/payload without changing the public Product DTO
- [x] 1.3 Extend CSV parsing/validation and add schema/listing integration fixtures across both roots and initial garment groups with enough records for multi-page traversal; production content and 200-design quality gates are owned by seed-catalog-quality
- [x] 1.4 Verify mapping/export compatibility for the seed-catalog-quality authoring dataset and frontend asset manifest; do not create a second content-authoring or relevance-evaluation workflow

## 2. Safe catalog upgrade

- [x] 2.1 Compute desired catalog revision/manifest and replace the category-present skip condition with completeness/revision validation that makes no embedding calls on a complete restart
- [x] 2.2 Implement deterministic upsert, expected-SKU/payload verification and cleanup only after replacement verification; fail readiness/startup on incomplete writes
- [x] 2.3 Test fresh import, complete restart, existing jacket-only upgrade, missing memberships/revision, invalid rows/configuration, failed partial upserts and recovery after restart
- [x] 2.4 Verify no pre-replacement legacy deletion, no duplicate SKU points and no relational database dependency; document backup/rollback and deployment coordination

## 3. Catalog listing

- [x] 3.1 Add metadata indexes required for categoryPathIds and implement deterministic Qdrant payload traversal without similarity search or model calls
- [x] 3.2 Implement opaque validated cursors bound to revision/category/limit/position, minimum 30-minute running-service validity and explicit restart/revision invalidation behavior
- [x] 3.3 Implement GET /api/v1/products with default limit 24, limits 1–100, existing Product mapping, known-empty 200 responses and typed 400 validation errors
- [x] 3.4 Guard listing with verified catalog readiness; map unavailable store/import to 503 and keep browsing independent of LLM search rate/daily budget checks

## 4. Contract and integration checks

- [x] 4.1 Test root/intermediate/leaf/direct membership, shared products, exclusion of other-root-only products and frontend/backend hierarchy parity
- [x] 4.2 Test 57-product traversal in 24/24/9 pages with no omissions/duplicates, final null cursor, empty categories and deterministic order for an unchanged revision
- [x] 4.3 Test malformed/expired/tampered cursors, unknown categories, invalid limits, scope/limit mismatch, revision changes and allowed restart invalidation
- [x] 4.4 Assert no embedding/LLM/search-controller calls and no search-quota consumption for listing; verify listing after exhausted daily search budget
- [x] 4.5 Compare search/listing Product representations by SKU and verify existing search, health/readiness and import tests remain valid
- [x] 4.6 Run the backend project's required checks and a Qdrant-backed integration test; document the final API/fixtures and verify against the dependent frontend before deployment
