## Why

The current 100-row seed catalog has only 20 distinct descriptions: 95 products belong to repeated-description groups, and it contains only jacket/coat styles. Different names, prices and colors do not provide enough functional variety to demonstrate relevant apparel search across the approved Men/Women assortment.

## What Changes

- Audit and record the actual configured seed source, duplication, category distribution and search-evidence gaps.
- Curate an initial 200 distinct products covering both roots, all 17 agreed garment styles and meaningful combinations of materials, weather protection, warmth, fit, occasion, functionality, size, color and price.
- Introduce explicit authoring facts as a quality/evaluation companion to the import CSV; write concise, consistent descriptions from those facts rather than rephrasing a handful of templates.
- Add offline dataset-quality validation and a judged search-scenario suite with candidate-stage and final-response measurements, including hard negatives, multilingual requests, refinements and intentional empty results.
- Keep deterministic plumbing tests separate from opt-in evaluation using the real embedding/search pipeline. Do not claim measured relevance from the seed audit or the current fake embedding model.
- Deploy content updates through the revision-aware, retry-safe import already owned by `apparel-catalog-browsing`; preserve stable SKU identities for unchanged products.

## Capabilities

### New Capabilities
- `seed-catalog-quality`: authored assortment size/coverage, factual profiles, functional diversity, description consistency and offline quality gates.
- `search-relevance-evaluation`: judged scenarios, hard constraints, stage-specific diagnostics, real-model evaluation, acceptance metrics and repeatable reports.

### Modified Capabilities
None. This change adds content-quality and evaluation requirements; `apparel-catalog-browsing` remains the owner of the Product/CSV membership schema, catalog revision and import/recovery behavior. Retrieval/API algorithm changes require a separate owning specification if the benchmark exposes that need.

## Impact

- Future authored product source and sample CSV under backend resources, corresponding title/description fixtures, an authoring-facts sidecar and offline validators.
- Relevance fixtures/reports and an explicitly invoked evaluation runner for the actual pipeline; no paid model evaluation runs during this specification update.
- Coordination with frontend category ids, image-key manifest and the homepage example. No new public Product fields or runtime authoring metadata are required.
- Current seed CSV, Qdrant data, prompts, retrieval configuration and runtime code remain unchanged while these specifications are authored.
- Baseline evidence: `seed-audit.md` and `audit/seed-baseline.json`. Data design and scenario plan: `catalog-plan.md` and `relevance-plan.md`.
