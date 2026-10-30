## 1. Baseline and prerequisites

- [x] 1.1 Audit the configured repository seed, freeze its source hash/counts/repeated-description groups and document evidence gaps without claiming a live relevance result
- [x] 1.2 Complete/coordinate apparel-catalog-browsing schema, stable category ids and revision-safe import prerequisites; keep this change responsible for dataset quality rather than a second importer
- [x] 1.3 Define the authoring-facts schema and CSV export/parity checks, with explicit known/unknown/not-applicable feature and material values

## 2. Curated product source

- [x] 2.1 Author/review 100 distinct Men designs following the 50 jacket/coat, 20 sweater/hoodie, 15 jeans and 15 trousers allocation in catalog-plan.md
- [x] 2.2 Author/review 100 distinct Women designs with the same family allocation; shared products must not inflate distinct-design counts
- [x] 2.3 Ensure every agreed garment style, price band and useful constraint intersection is covered; add exact price-boundary products and plausible near misses
- [x] 2.4 Rewrite descriptions from complete functional facts, targeting 45–80 concise words; preserve stable identities and correct legacy title/protection/material contradictions
- [x] 2.5 Verify each same-style design differs in at least two substantive functional dimensions; consolidate metadata-only clones and reject cosmetic paraphrase padding
- [x] 2.6 Export the accepted CSV, align category/image-key manifests and explicitly update the configured seed resource path; keep test/demo fixture scope distinct

## 3. Offline data-quality gate

- [x] 3.1 Implement no-model checks for schema/percentages, unique identities, 200-design coverage, memberships, image keys, price bands and JSON/CSV equality
- [x] 3.2 Implement normalized duplicate-description and functional-signature checks excluding SKU/title/price/color/size/audience-only differences; produce SKU-addressable review reports
- [x] 3.3 Review near-duplicate prose and factual consistency, including waterproof versus water-resistant, complete wool/animal-fiber/down/leather exclusions and missing-feature uncertainty
- [x] 3.4 Require all blocking quality issues resolved before publishing; retain the frozen baseline audit separately from post-rewrite evidence

## 4. Judged search fixtures

- [x] 4.1 Create at least 44 concrete scenarios using relevance-plan.md, with at least 25% held-out wording and complete expected statuses, hard constraints and relevance/near-miss SKU judgments
- [x] 4.2 Verify all positive and broad-case eligibility against the full authored profiles, and verify the intentional-empty cases are valid requests with no eligible products
- [x] 4.3 Cover German/Russian/French intent, strict versus inclusive price boundaries, material/functional exclusions, contextual negation, color replacement and accumulated multi-turn constraints
- [x] 4.4 Freeze catalog/case/judgment revisions before acceptance; distinguish development tuning from held-out acceptance and correct any misleading small-fixture semantics deliberately

## 5. Actual pipeline evaluation

- [x] 5.1 Implement an explicit opt-in isolated-catalog evaluation runner, stage diagnostics and bounded reports, keeping ordinary tests free of external model calls
- [ ] 5.2 Verify actual imported revision/counts and representative public fields before evaluation; record model/embedding/topK/threshold/max-results configuration and estimate API/token/import/retry usage
- [ ] 5.3 Measure actual candidate Hit@30 and final per-case precision/Hit@3, all-rank hard violations, false empties, broad-result counts, multilingual intent and each conversation turn
- [ ] 5.4 Run two independent complete acceptance passes within a declared provider budget; retain failures/variance and label missing/quota-blocked cases incomplete rather than selecting only successful runs
- [ ] 5.5 Diagnose failures by data, analysis, retrieval, filtering, ordering or infrastructure; specify any required runtime/prompt changes separately without cheating the facts, judgments or candidate limits

## 6. Publication and frontend handoff

- [ ] 6.1 Publish the accepted content through apparel-catalog-browsing's verified revision/manifest importer and test that existing nonempty stores receive description/content updates
- [ ] 6.2 Confirm public fields, category/image mapping and representative real searches for the loaded revision; keep rollback source/revision and reports
- [ ] 6.3 Update the homepage demonstration requests only from validated real cases after the frontend is complete; preserve local demo versus live-search separation
- [x] 6.4 Document the source/export/update/evaluation workflow and run appropriate backend checks before closing the change
