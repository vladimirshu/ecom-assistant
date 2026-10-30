## Why

The approved Sage design makes natural-language clothing search the primary action and lets visitors inspect both conversation history and current matches. The earlier monochrome, card-only specification conflicts with that design and must be aligned before implementation.

## What Changes

- Standalone Vite + React + TypeScript + Tailwind static SPA, with `/` for search/workspace and `/catalog` for browsing.
- Sage palette, system serif/sans typography, small header portfolio note, quiet Men/Women entry points and a compact full-width search field. No separate Catalog link.
- Homepage copy and an isolated, accessible search/refinement demonstration based on bundled fixtures; final example reviewed against the completed live flow.
- Desktop conversation/history beside one selected results grid, historical snapshot selection without API calls, and mobile Matches/Conversation tabs.
- Compact shared product cards with transparent graphite drawings, title and price; complete details in an accessible local dialog, without a new product endpoint or page.
- Preserve editable `src/data/categories.json`, Men/Women hierarchy and descendant-inclusive category browsing with cursor pagination, no attribute-filter panels.
- Actual API loading, empty, rejection, validation, quota and failure behavior; keep drafts/history/results appropriately labelled and integrate the Sage availability panel.
- Add a complete catalog image-generation brief: light graphite style, genuine transparency, 17 garment types plus fallback, reusable prompts, manifest/provenance, alpha WebP delivery and whole-set review.
- Record pinned MagicPath references and implementation checks; reuse React source only after consolidating shared components and replacing prototype logic.

## Capabilities

### New Capabilities
- `storefront-shell`: Sage search-first layout, header, copy, responsive composition and accessibility.
- `conversational-search`: real API requests, conversation lifecycle, request races and feedback.
- `search-results`: side-by-side workspace, result snapshots, mobile tabs, interpretation and ranking.
- `product-details`: compact cards and accessible product dialog using loaded data.
- `search-demonstration`: isolated local playback, controls, motion preferences and final live-flow review.
- `catalog-browsing`: local category tree, URL state, descendant product listing and cursor pagination.
- `catalog-imagery`: generation style/reference, full garment coverage, prompts, transparent assets, mapping/provenance and release acceptance.

### Modified Capabilities
None; this remains an unimplemented frontend change.

## Impact

- Documentation changes only in this update; application implementation remains unchecked in tasks.md.
- Existing search/Product DTO contract is unchanged. No product-detail or category-tree endpoint is introduced.
- New backend dependency: `../eas-backend/openspec/changes/apparel-catalog-browsing` owns expanded apparel and cursor-paged GET /api/v1/products. The frontend category file remains the navigation source.
- Backend content/relevance dependency: `seed-catalog-quality` owns the curated dataset and real-model scenario acceptance; final frontend examples must use its verified catalog cases.
- Coordinated with frontend `service-availability-gate` for non-modal Sage status presentation and actual readiness behavior.
- Static hosting and SPA route fallback remain in root `aws-edge-hosting`. Development proxies `/api` to the backend; production uses same-origin CloudFront routing.
- MagicPath project/revision references and a reviewed illustration sheet are recorded in design-reference.md and reference-assets/; the production asset plan and prompts are in image-generation.md. Prototype sample data, timers and state selectors must not become production API logic.
