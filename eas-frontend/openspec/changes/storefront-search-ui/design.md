## Context

The approved ATELIER Sage design is recorded in `design-reference.md`, with pinned MagicPath component/revision ids and a checked-in illustration reference. MagicPath frames are interactive prototypes with sample products, simulated matches/timers and reference-only state selectors. This change specifies the real implementation. The latest written requirements take precedence over prototype shortcuts.

Backend contract already available:

```
GET /api/v1/search?message=<text>&conversationId=<[A-Za-z0-9_-]{1,50}>&clearMemory=<bool>
200 { products: Product[], interpretation: Interpretation, suggestedPrompts: null }
422 { products: null, interpretation: null, suggestedPrompts: string[] }
400 { <field>: <message> }
429 { code: "RATE_LIMITED" | "DAILY_BUDGET_EXHAUSTED", message }
500 { error: string }

Product = { sku, title, category, colors: string[], sizes: string[], price: number, description, imageUrl }
Interpretation = { searchQuery, negativeExamples: string[],
  priceConstraint: { operator: "LESS_THAN"|"LESS_THAN_OR_EQUAL"|"GREATER_THAN"|"GREATER_THAN_OR_EQUAL", value: number } | null,
  sortByLowestPrice: boolean }
```

Search returns at most 10 products in ranked order. The product dialog can be populated entirely from this representation: no product-detail endpoint or new material attribute is needed. The expanded apparel assortment and browsing API are owned by backend change `apparel-catalog-browsing`; they are not yet implemented.

## Goals / Non-Goals

Goals: match the approved Sage visual hierarchy; make search/refinement immediately understandable; keep history and current matches visible together on desktop; offer compact products with complete accessible dialogs; browse the Men/Women hierarchy; handle actual availability and API feedback without losing work.

Non-goals: cart, checkout, accounts, wishlist, separate product routes, attribute-filter panels, manual catalog sorting, search pagination, ranking changes, interface localization, extra prose-generation APIs, deploying the website or turning prototype matching logic into production behavior.

## Decisions

### 1. Application and API boundaries

Use client-only Vite + React + strict TypeScript + Tailwind v4, npm, Vitest/RTL/MSW and Playwright. Build static files for S3/CloudFront. Requests use relative same-origin `/api/v1/...` URLs; the Vite `/api` proxy targets VITE_DEV_BACKEND_URL (default localhost:8080). No application server, Next.js migration or CORS layer is required. Keep production package versions pinned at implementation time. Use system Georgia/Arial stacks to match the approved sketch; no font service is required.

### 2. Theme and first screen

Tokens: paper #F6F8F0, wash #E5ECD7, action #4E704E, ink #303B30, muted #68745F, border #D6DFCC. The header contains ATELIER, the small portfolio note, and quiet Men/Women links. No Catalog link. A 23–25 px serif headline, Describe it. We'll find it., sits above a full-content-width search component around 48–52 px high. Desktop input is normal 14 px text; mobile uses at least 16 px to avoid browser zoom. A Sage fill, border and green action give search priority over headings. Under it, display concise clickable examples. Keep a stable real-field placeholder.

The next row places Clothes for what you have in mind. / Tell us what you need, in any language. to the left of an isolated demonstration. Stack these on mobile. Use generous spacing, unobtrusive hairlines and borderless product cards. Price formatting preserves fractional EUR values. Service feedback uses the same theme rather than a separate decorative shop-door treatment.

### 3. Search session and selected results

`useSearchSession` owns conversationId, completed/attempted turns, per-turn interpretation/products/suggestions/status, draft, activeTurnId, request generation and lastRequest. Generate a UUID in memory on load/New search; use clearMemory=true only on the first request, false for subsequent requests. No browser storage. Preserve app-level state during catalog navigation.

Render `/` as Homepage before submission and SearchWorkspace afterwards. SearchWorkspace consists of ConversationHistory + FollowUpComposer and CurrentMatches. History stores structured replies but never duplicates product grids. A successful turn offers Show these matches. Inspecting stored results is local: it neither rewinds the backend conversation nor changes subsequent refinement semantics. New requests select their pending state. If a visitor explicitly switches to an earlier snapshot during a request, preserve that choice when the response arrives and offer New matches available. Return to latest matches uses the newest successful snapshot. New search clears all snapshots/draft/dialog state and returns to the introduction.

Desktop uses a roughly 280–310 px history column and a flexible results pane. Constrain history overflow locally while keeping its composer visible. The pane shows one selected product array, its interpretation and returned-match count. The first row and composer must coexist at 1440×900. Additional rows may scroll; no promise that all 10 matches fit one screen. On mobile use Matches/Conversation tabs, retaining the same state and draft; new requests and historical result selection open Matches. A selected-request summary remains above the mobile grid. Avoid a page-wide chat feed that buries current products below prior turns.

### 4. Asynchronous behavior and feedback

Use AbortController (30 s timeout) and monotonically increasing generation ids. Abort superseded/route-exited requests and ignore late completions. Store original customer text for history while sending validated trimmed text. Guard 3–500 characters and angle brackets locally; backend validation remains authoritative. Retry repeats the same request tuple without creating duplicate visible history entries.

During selected pending state show loading skeletons and client-timed intent messages at 0/1.5/3 s: Reading your request → Searching the collection → Picking the best matches. Do not claim pipeline-stage completion. Keep earlier snapshots available through history. Success with zero products selects an empty snapshot, not a stale grid.

422 uses backend suggestions unchanged. 400 stays by the relevant input. 429 RATE_LIMITED uses Retry-After when present; DAILY_BUDGET_EXHAUSTED has no retry and does not disable catalog browsing. Network/timeout/5xx report to availability; confirmed readiness leaves a normal retry notice. Retained products after a failed/rejected/limited attempt must be labelled last successful results. Do not replace them with fictional matches. Polite announcements describe terminal states rather than every animation frame.

### 5. Product components and dialog

Use one ProductCard/ProductGrid for catalog, selected search snapshots and example fixtures. Cards contain drawing, title, price and View details only. Full Product data remains in memory for ProductDialog. Use a native dialog or equivalent accessible implementation: opaque Sage panel centered with an explicit margin:auto, subdued backdrop, focus containment, Escape/Close, return focus to the invoking card, and vertically scrollable content on narrow screens. Show image/title/price/full description/category/colors/sizes/SKU; optional swatches never replace readable color names. Material information comes from description, not an invented API property.

A dialog opens without network requests, quota use or route changes. Closing preserves category, pagination, scroll position, draft and selected snapshot. Close stale dialogs on route/category/snapshot changes. Catalog and search must display the same data for the same loaded SKU. No separate product pages.

### 6. Illustration assets

Use separate graphite drawings with genuine transparent alpha. Fine contour lines, light crosshatching, no solid black fills, paper textures, people, hangers, labels or neighboring garments. Keep fabric shading inside the silhouette. Reserve a 4:5 frame and contain the full garment with consistent margins; do not crop it with object-fit:cover. Export individual alpha WebP around 1200×1500, targeting <=200 KB after visual review; transparent default.webp is mandatory. The checked-in sheet is a style reference, not a production sprite to crop in CSS.

Keep reusable files flat in public/images/categories/<image-key>.webp. Vite copies them to dist. Backend imageUrl chooses each card asset; frontend navigation ids do not derive that URL. Optional imageKey references in the local tree select representative category illustrations only. Preserve alpha during optimization and verify appearance on paper, wash and dialog surfaces. Test fallback once to avoid loops. Existing 13 jacket styles are only the initial coverage; add the agreed sweaters/hoodies, jeans and trousers.

Generation details are owned by `catalog-imagery` and `image-generation.md`. The baseline manifest has 17 garment-style keys plus default (18 files), retaining the 13 jacket keys and adding sweater, hoodie, jeans and trousers. The garment table supplies distinguishing details and one shared reference-based prompt. Additional keys are required for actually used product-specific silhouettes; do not duplicate the set for each color, size or browsing root. Keep a build/review manifest of prompt/reference/revision/status/export data, generate one distinct asset per request and review individual images plus a mixed grid before release. The manifest is a tooling/release input, not product-facing UI or a runtime image-generation service.

### 7. Editable category hierarchy and browsing contract

Keep `src/data/categories.json` as the editable build-time navigation source, with `{ id, name, imageKey?, children }`. Roots Men/men and Women/women each contain Jackets & Coats, Sweaters, Jeans and Trousers, with deeper jacket types. T-shirts may be added later. Exclude underwear, lingerie, undershirts, tank tops and bras. Labels/order are frontend concerns; ids/parentage must align with backend category membership. Validate unique ids, fields and optional image files. Do not fetch a category-tree endpoint or duplicate the tree in components.

Backend-owned proposed API:

```
GET /api/v1/products?limit=24[&category=<URL-encoded id>][&cursor=<opaque URL-encoded token>]
200 { products: Product[], nextCursor: string | null }
400 { error: string }
```

The backend includes direct and descendant members once per SKU, traverses deterministically and binds cursors to category, page size and catalog revision. The frontend forwards tokens unchanged; never emulate browsing with search, offsets or a bundled product CSV. The owning backend spec defines the full traversal/error contract. No category parameter means All clothing. Roots and intermediate categories always show combined product lists.

Header Men/Women links navigate to `/catalog?category=men|women`. Catalog has a quiet tree on the left and heading/breadcrumb/grid on the right, reflowing above the grid on mobile. All clothing lives within catalog navigation. Preserve campaign parameters. Selection changes abort old requests, close dialogs, reset products/cursor and load the first page after readiness. Re-entry/history restores URL selection and starts its first page. Unknown ids show Category not found without requesting that id; valid empty categories keep navigation visible.

Use IntersectionObserver with a keyboard Load more fallback, one in-flight request, generation guards and defensive SKU deduplication. Append in backend order, preserve focus and earlier cards, announce new items. Null nextCursor shows the end message. Failed append preserves the cursor/cards; retry is explicit. Invalid/expired cursor offers Reload collection for the same category. Catalog requests use 30 s timeouts. Category navigation itself works while asleep; product fetching uses availability.

### 8. Demonstration and reuse of MagicPath React source

SearchDemonstration has isolated fixtures, playback state and controls. It uses the same visual components and performs no API or readiness calls. Demonstration products are labelled examples. A real-field focus pauses automatic playback; real submission or leaving the homepage stops it. Limit automatic playback to one visible, unfocused cycle per entry; reduced motion provides a static/manual alternative. Final playback must be compared with an actual completed query/refinement flow; a final recording can be produced only then.

The MagicPath frames contain React code and duplicated copies of common components. Export/inspect the pinned reference source and consolidate it into one local component set. Remove fixed-width mobile wrappers, state-switching review toolbars, sample matching, simulated timers, placeholder prices and fake Load more behavior from production paths. Component reuse must not bypass real API, routing, availability, cursor or accessibility requirements. UI-only fixtures remain in tests/demo modules.

### 9. Suggested component boundaries

```
src/components/Header, Hero, SearchBar, ExamplePrompts, SearchDemonstration
src/components/SearchWorkspace, ConversationHistory, CustomerMessage, AssistantReply,
               FollowUpComposer, CurrentMatches, InterpretationBar, ProductGrid, ProductCard,
               ProductDialog, CatalogNavigation, CategoryBreadcrumbs, CatalogList, LoadMore,
               LoadingStory, SkeletonCard, RejectionNotice, LimitNotice, ErrorNotice, EmptyResults
src/lib/api/types.ts, search.ts, catalog.ts
src/lib/format.ts, examples.ts, brand.ts
src/data/categories.json
src/gate/*                         separate service-availability-gate change
public/images/categories/*.webp
```

### 10. Verification and reference states

Use Vitest/RTL/MSW for API status mapping, session races, stored result selection, no-request historical inspection/dialog opening, formatting and local category validation. Use Playwright for live-route behavior with API fixtures, modal focus/dismissal, mobile tabs, keyboard pagination, category switches, offline demonstration and service states. Check 390×844, 1440×900 and width extremes; assert no horizontal overflow and no missing products. Run axe-core on initial, workspace, catalog, dialog and gate states. Verify alpha assets on Sage surfaces. Final manual validation compares actual backend-ranked SKUs and interpretation, then records/reviews the homepage example. Lint, unit, E2E and production build must pass before implementation is considered complete.

## Risks / Trade-offs

- Existing backend jacket-only import lacks hierarchical apparel browsing: implement `apparel-catalog-browsing` first or use explicit MSW fixtures during UI work.
- Category configuration exists on both sides: compare stable ids/edges during integration, allowing frontend label/order changes.
- A narrow viewport cannot show full history and products simultaneously: mobile tabs prioritize current matches while retaining history.
- Category drawings may repeat across products: text and dialog data distinguish them; introduce per-product imageUrl values without changing the UI contract when available.
- Prototype source is useful visual material but not production readiness evidence. Shared code must be consolidated and integrated deliberately.
- Availability is governed by real UP/health responses; never enable search from a fixed prototype timer or use example playback to keep the backend alive.
