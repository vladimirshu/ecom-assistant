## Context

The backend (`../eas-backend/`, Spring Boot) exposes one search endpoint. Its contract after the backend changes `api-versioned-search-path`, `storefront-search-api`, `conversation-memory` and `demo-cost-controls` is the source of truth:

```
GET /api/v1/search?message=<text>&conversationId=<[A-Za-z0-9_-]{1,50}>&clearMemory=<bool>
200 { products: Product[], interpretation: Interpretation, suggestedPrompts: null }
422 { products: null, interpretation: null, suggestedPrompts: string[] }   // guardrail rejection
400 { <field>: <message> }                                                 // input validation
429 { code: "RATE_LIMITED" | "DAILY_BUDGET_EXHAUSTED", message }          // usage limits, Retry-After header
500 { error: "Illegal assistant response" | … }

Product        = { sku, title, category, colors: string[], sizes: string[], price: number /*EUR*/, description, imageUrl }
Interpretation = { searchQuery, negativeExamples: string[],
                   priceConstraint: { operator: "LESS_THAN"|"LESS_THAN_OR_EQUAL"|"GREATER_THAN"|"GREATER_THAN_OR_EQUAL", value: number } | null,
                   sortByLowestPrice: boolean }
```

A successful search makes two LLM calls (about 3–6 s). Conversation memory is server-side, keyed by `conversationId`; `clearMemory=true` resets it. The backend returns at most 10 products. There is no product-detail, catalog or category endpoint. The same origin also serves `GET /api/v1/health`, which the separate change `service-availability-gate` uses.

Production shape: the SPA is a set of static files in S3. CloudFront serves everything from one domain: `/*` → S3 and `/api/v1/*` → the backend, so the browser only ever sees relative URLs.

## Goals / Non-Goals

**Goals:**
- A storefront that reads as a premium minimal fashion shop at first glance.
- Make the concept explicit: no browsing; you describe, we show what we understood and the matches.
- Robust behaviour around a slow, sometimes rejecting, LLM-backed API.
- A standalone project: own toolchain, own tests, own OpenSpec root.

**Non-Goals:**
- Cart, checkout, accounts, wishlist, product detail pages or SEO for product pages (out of product scope).
- Interface localization (English only). Request text can be in any language.
- Editing the interpretation by removing chips (a possible follow-up: it would send a refinement such as "remove the price limit").
- Streaming progress from the backend (the staged loading text is client-timed).
- Deployment pipeline and hosting (root changes `aws-edge-hosting`, `aws-backend-host`).
- Handling a sleeping backend (change `service-availability-gate`).

## Decisions

**1. Stack: Vite + React + TypeScript + Tailwind CSS, client-only.**
The storefront is one interactive page, so nothing needs server rendering. Vite builds it to static files (`dist/`) that S3 can serve, which is the deployment target. Versions are the current stable Vite, React and Tailwind CSS v4 at implementation time, pinned in `package.json`. Package manager: npm. Vitest shares the Vite config, so unit tests use the same module resolution as the app.
- Alternative considered: Next.js App Router. Its server features (Route Handler proxy, `next/image` optimization, `next/font`) would be unused or disabled in a static export, so it adds weight for no benefit. It was the first choice because a proxy was needed; CloudFront path routing removed that need.

**2. No application proxy: relative `/api/v1/...` URLs.**
The client calls `GET /api/v1/search` on its own origin. In production CloudFront routes `/api/v1/*` to the backend, so there is no CORS. In development, `vite.config.ts` sets `server.proxy` for `/api` to `VITE_DEV_BACKEND_URL` (default `http://localhost:8080`). The 30 s request timeout and error mapping move into the client (`AbortController`).
- Alternative: a Route Handler or Node proxy. Rejected: it needs a server in production, which a static site does not have.

**3. State: one reducer, no data-fetching library.**
`useSearchSession()` holds `{ conversationId, turns: {message}[], status: idle|loading|success|rejected|invalid|error, products, interpretation, suggestions, fieldError, lastRequest }`. A search is a sequential, conversational command, not cacheable server state, so TanStack Query would add little. An `AbortController` per request implements supersede and timeout, and a monotonically increasing request id guards against late responses. `conversationId` is `crypto.randomUUID()` (36 characters, within the backend's 50-character rule) kept in React state only: a new token on every page load and on "New search", as the backend change `conversation-memory` specifies. The turns are kept in memory too, so a reload starts a fresh conversation with an empty trail.

**4. Typed API client.**
`lib/api/search.ts` returns a discriminated union `{kind: "ok", products, interpretation} | {kind: "rejected", suggestions} | {kind: "invalid", message} | {kind: "limited", code: "RATE_LIMITED" | "DAILY_BUDGET_EXHAUSTED", retryAfterSeconds?} | {kind: "error"}`, which keeps HTTP details out of components. A network failure, timeout or 5xx result is also reported to the availability gate (`service-availability-gate`) so it can check health inside opening hours or control outside hours. Search submission also passes through the gate: outside opening hours a control UP response is required before sending the search; STARTING preserves input and opens the gate without submitting (see `service-availability-gate`). Types live in `lib/api/types.ts` and mirror the contract above.

**5. Component structure.**
```
index.html               title, meta, root element
src/main.tsx             React entry, imports fonts and styles
src/App.tsx              <Storefront/>
src/components/Header, Hero, SearchBar, ExamplePrompts, RotatingPlaceholder,
           ConversationTrail, InterpretationBar, ResultsGrid, ProductCard,
           ColorSwatches, QuickView, LoadingStory, SkeletonCard,
           RejectionNotice, ErrorNotice, EmptyResults
src/lib/api/             types.ts, search.ts
src/lib/format.ts        EUR formatting (Intl, en-IE), price chip wording, match count
src/lib/colors.ts        color name → hex map (catalog colors) with neutral fallback
src/lib/examples.ts      example prompts and placeholder rotation list
src/lib/brand.ts         wordmark constant
```

**6. Visual language.**
- Typography: `Cormorant Garamond` for the display headline and product titles, `Inter` for UI and body, self-hosted through the `@fontsource` npm packages (no request to Google Fonts, no layout shift from a late font, `font-display: swap`). The headline is large, with tight leading and a light weight. Labels use small uppercase text with letter spacing.
- Palette: off-white background (`#FAFAF8`), near-black text (`#111`), greys for secondary text and hairline borders. No accent color. Product color swatches are the only color on the page.
- Layout: generous white space and a centered hero at about 70 vh. After the first search the search bar becomes a slim sticky bar with the trail and interpretation below it. Cards have no borders or shadows. Hovering a card zooms the image slightly (`scale 1.03`, 400 ms) and underlines the title.
- Motion: subtle fades only, disabled under `prefers-reduced-motion`.
- Brand wordmark: placeholder `ATELIER` in one constant (`src/lib/brand.ts`).

**7. Example prompts.**
They are curated to show off the concept and to hit the catalog:
- "A light rain jacket for cycling to work"
- "Something warm for winter walks, no wool, under €200"
- "The cheapest puffer jacket you have"
- "A smart coat for the office"
- "Eine wasserdichte Jacke zum Wandern" (non-English)
- "Une veste en cuir style vintage" (non-English)

The placeholder rotates through these every 3.5 s while the field is empty and unfocused.

**8. Loading story.**
Progress steps are timed on the client at 0 s / 1.5 s / 3 s: "Reading your request" → "Searching the collection" → "Picking the best matches". Eight skeleton cards are shown on the first search. During a refinement the current grid dims to 40 % opacity instead. The step text is mirrored into an `aria-live="polite"` region.

**9. Images: one black-and-white image per category, delivered as static files.**
- Location and naming: `public/images/categories/<slug>.webp` for the 13 backend slugs plus `default.webp`. Vite copies `public/` into `dist/` unchanged, so the files are served at `/images/categories/<slug>.webp` from the same S3 bucket and CloudFront domain as the page. This is the contract with backend `product-catalog`, and the backend sends `imageUrl = /images/categories/<slug>.webp`.
- Format: 4:5 portrait, 1200×1500, WebP quality about 80, at most 200 KB each, true grayscale.
- Rendering: a plain `<img>` with `width`/`height` (4:5 frame, no layout shift), `loading="lazy"` below the fold, `decoding="async"`, and a `srcset` only if smaller variants are added later (the single 1200×1500 file is at most 200 KB). Any absolute `imageUrl` host works, since there is no image optimizer to configure. A CSS `grayscale` filter guarantees black and white even if a future per-product photo is in color. `onError` swaps to `default.webp`.
- Production: images are generated once with an image-generation model, reviewed by a person and committed. The prompt template keeps the set consistent: *"Black and white studio product photograph of a single {item} on an invisible mannequin, front view, centered, soft diffused light, seamless light grey background, high detail fabric texture, minimal luxury fashion catalog style, no text, no logo, no person, 4:5 portrait."*
  - `{item}` per slug: puffer → "quilted down puffer jacket"; parka → "hooded travel parka with large pockets"; coat → "tailored trench coat"; rain → "lightweight water-resistant nylon rain jacket"; softshell → "minimalist softshell jacket"; fleece → "zip-up fleece jacket"; track → "retro track jacket with contrast stripes"; technical → "technical ski and hiking shell jacket"; field → "waxed cotton field jacket"; bomber → "classic bomber jacket with ribbed cuffs"; denim → "vintage denim trucker jacket"; leather → "leather biker jacket"; windbreaker → "packable sporty windbreaker"; default → "folded neutral jacket on a table".
- Why static now and blob storage later: see backend design `storefront-search-api` decision 1. The frontend only ever uses `imageUrl` from the API, so moving images to a CDN is a backend configuration change only.

**10. Testing.**
- Vitest + React Testing Library + MSW for component and reducer tests: formatting, chip wording, card content, swatch fallback, the reducer state machine (supersede, retry, new search), and client-side validation.
- Playwright end-to-end against the Vite dev or preview server with `page.route('**/api/v1/search*')` mocks for happy path, refinement, rejection, 400, 429 (both codes), 500/timeout and empty results; viewport checks at 390 and 1440 px; `@axe-core/playwright` audit.
- `lib/api/search.ts` is unit-tested with MSW for every status, network failure and abort.
- An optional smoke test against a running backend (`E2E_BACKEND=1`), excluded from the default run.

## Risks / Trade-offs

- [All cards in one category look identical, e.g. many puffers in one result] → Titles, prices and swatches differ. Per-product images later via the backend `image` CSV column, no frontend change.
- [Client-timed loading steps do not reflect real progress] → The wording is honest about intent rather than claiming completion. Backend Server-Sent Events (SSE) are a follow-up.
- [Contract drift with the in-flight backend changes] → Types mirror the backend specs. Playwright fixtures are taken from the backend spec scenarios, and the optional live smoke test catches drift.
- [Reload loses the conversation] → By design: a reload creates a new token, so the backend history and the trail are both fresh. The backend never evicts or shares conversations of other tokens (`conversation-memory`).
- [Generated images may contain artifacts or logos] → Manual review before commit; the prompt says "no text, no logo".
- [LLM latency up to 30 s on cold start] → 30 s client timeout with retry. CloudFront's origin response timeout (default 30 s) must be at least as long; this is checked in `aws-edge-hosting`.
- [Usage limits reached during a demo] → HTTP 429 shows a calm message with the wait time from `Retry-After` when present, never the retry button for `DAILY_BUDGET_EXHAUSTED`.
- [Backend asleep] → handled by `service-availability-gate`; this change only reports failures to it.
