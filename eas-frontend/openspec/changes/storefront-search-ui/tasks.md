## 1. Setup and design source

- [ ] 1.1 Scaffold standalone Vite/React/strict TypeScript/Tailwind v4 with npm, ESLint, Vitest/RTL/MSW, Playwright and axe; preserve openspec/ and unrelated work
- [ ] 1.2 Configure same-origin /api development proxy and document VITE_DEV_BACKEND_URL, run/build/test commands and static hosting fallback
- [ ] 1.3 Inspect pinned MagicPath references from design-reference.md, export reusable React source and consolidate shared components; remove fixed mobile wrappers, review toolbars, simulated matching/readiness and fake pagination from production code
- [ ] 1.4 Implement shared Sage theme, Georgia/Arial system font stacks, spacing and responsive container-based grid rules

## 2. API and session

- [ ] 2.1 Define Product, Interpretation, typed status/error bodies and CatalogPage against backend specs; coordinate integration with apparel-catalog-browsing
- [ ] 2.2 Implement search/catalog clients with relative URLs, 30 s timeouts, abort support, opaque cursor forwarding, category/page-size binding and MSW tests for responses and failures
- [ ] 2.3 Implement in-memory conversation UUID, first-request clearMemory, raw-message history, validated trimmed requests, retained drafts, per-turn snapshots and selected turn
- [ ] 2.4 Implement supersede/route aborts and generation guards, request-preserving retry without duplicate visible turns, New search reset and catalog-return preservation
- [ ] 2.5 Implement 3–500 character/angle-bracket validation and inline backend 400 errors; tests for non-Latin text and draft retention

## 3. Homepage and demonstration

- [ ] 3.1 Build Header with wordmark, small portfolio note and Men/Women links visible on the first screen; no Catalog link or non-functional shop controls
- [ ] 3.2 Build small Describe it. We'll find it. headline, full-width compact Sage search, stable placeholder, concise example actions and approved explanatory copy
- [ ] 3.3 Build isolated SearchDemonstration from shared visual components and labelled fixtures: query/loading/cards/refinement, Play/Replay/Pause, no API calls or conversation mutation
- [ ] 3.4 Pause automatic playback on field focus, stop on navigation/real search, limit automatic cycles and provide reduced-motion/static alternative; verify playback while backend unavailable

## 4. Conversation and results

- [ ] 4.1 Build desktop history/composer beside CurrentMatches; history overflow must not push first-row results or composer out of view at 1440×900
- [ ] 4.2 Implement Show these matches, earlier/current labels, Return to latest matches and explicit-selection preservation during request completion; no requests or backend-memory changes on inspection
- [ ] 4.3 Build mobile Matches/Conversation tabs preserving draft, history and selection; submissions and historical selection open Matches; render every returned product in order
- [ ] 4.4 Implement structured interpretation chips, EUR/price operators and returned-count wording; preserve original backend ranking without pagination or local sorting
- [ ] 4.5 Implement selected pending/skeleton state, successful empty snapshot, 422 suggestions, inline 400, 429 retry timing/daily quota, network/timeout/5xx recovery and labelled previous successful results
- [ ] 4.6 Test session races, historical selection during pending requests, no branching, failed-turn retention, snapshot reset and mobile tab semantics

## 5. Product presentation and assets

- [ ] 5.1 Build shared compact ProductCard/ProductGrid with reserved 4:5 transparent image, title, precise EUR price and View details; no card-body descriptions or attribute lists
- [ ] 5.2 Build centered ProductDialog from loaded Product data, full description/category/colors/sizes/selectable SKU, keyboard containment/Escape/Close, safe focus return and mobile scrolling; no detail endpoint or route
- [ ] 5.3 Preserve catalog pages/scroll and selected snapshot on dialog close; close stale dialogs on route/category/snapshot changes; test opening with zero network requests
- [ ] 5.4 Implement the catalog image manifest from image-generation.md: all 17 initial garment keys plus default, product/category mappings, optional cut-specific keys, prompt/reference/revision/provenance and review/export status; keep it separate from navigation and runtime UI
- [ ] 5.5 Prepare the approved style reference and shared prompt plus garment briefs; verify the baseline key list against actual backend image references and the frontend tree without adding out-of-scope accessories or underwear
- [ ] 5.6 Generate/revise individual puffer, parka, coat, rain, softshell, fleece and track drawings with native transparency and retained source prompts
- [ ] 5.7 Generate/revise individual technical, field, bomber, denim, leather and windbreaker drawings in the same light graphite style
- [ ] 5.8 Generate/revise individual sweater, hoodie, jeans, trousers and default drawings; create extra cut-specific assets only where actual mappings require them
- [ ] 5.9 Export individual alpha WebP around 1200×1500 and <=200 KB without stretching, flattening or clipping; preserve original transparent sources and accepted revisions
- [ ] 5.10 Verify coverage, dimensions, size and meaningful alpha; inspect edge gaps/halos, garment construction and a mixed-category grid on paper/wash/dialog/contrasting QA surfaces at mobile card and dialog sizes; resolve needs-revision items before approval
- [ ] 5.11 Place accepted exports in public/images/categories/, implement one-shot transparent fallback, verify static build copies and fail release checks on any missing required product/category key

## 6. Catalog

- [ ] 6.1 Define editable src/data/categories.json and validate required fields, unique stable ids and optional imageKey files; compare backend ids/parent edges before integration
- [ ] 6.2 Build Men/Women roots, child navigation and breadcrumbs from the local file; all levels list direct+descendant products, no attribute filters; reflow navigation above the mobile grid
- [ ] 6.3 Implement URL category selection, direct entry/reload/Back/Forward, preserved campaign tags, empty/unknown categories and navigation before backend readiness
- [ ] 6.4 Implement initial readiness-guarded load, single-flight infinite append, keyboard Load more, backend order, defensive deduplication and explicit end state
- [ ] 6.5 Implement category-switch abort/reset/dialog closure, same-cursor retry, invalid-cursor reload for the selected category and availability recovery without automatic interrupted append

## 7. Availability and production boundaries

- [ ] 7.1 Integrate service-availability-gate without duplicating its state machine; keep static demo/navigation and drafts usable while API operations wait
- [ ] 7.2 Preserve existing schedule-aware routing, control-first off-hours checks, no heartbeat and no wake on search 429; remove prototype startup timers
- [ ] 7.3 Keep reference-state switching, fake search data and development-only readiness bypasses out of production paths; retain explicit fixtures in tests/demo modules only

## 8. Verification and release example

- [ ] 8.1 Run API/session/component tests plus Playwright search success/refinement/history/pending/rejection/400/429/5xx/timeout/empty/supersede/New search flows
- [ ] 8.2 Verify catalog category roots/intermediate/leaves, URL state, pagination/retry/cursor expiry, availability, race cancellation and conversation-return preservation
- [ ] 8.3 Check 390×844, 1440×900, 320 px and wide layouts: first-screen category links, search hierarchy, no horizontal overflow, history/results/composer visibility, complete product grids and usable dialogs
- [ ] 8.4 Run keyboard, reduced-motion, live-region and axe checks for homepage, workspace, catalog, dialog and availability states
- [ ] 8.5 Against the actual backend, inspect SKU/description in a product dialog and compare returned ranks without assuming the target always ranks first; verify a non-English query
- [ ] 8.6 Finalize the homepage example only after live UI/catalog validation against the accepted backend seed-catalog-quality revision and cases; verify its actual query/refinement/results, control behavior and accessible static alternative
- [ ] 8.7 Run npm run lint, unit tests, Playwright tests and npm run build; verify static alpha images, SPA route fallback requirements and README handoff
