## Context

The backend (`../backend/`, Spring Boot) exposes one endpoint. Its contract after backend change `storefront-search-api` is the source of truth:

```
GET /search?message=<text>&conversationId=<[A-Za-z0-9_-]{1,50}>&clearMemory=<bool>
200 { products: Product[], interpretation: Interpretation, suggestedPrompts: null }
422 { products: null, interpretation: null, suggestedPrompts: string[] }   // guardrail rejection
400 { <field>: <message> }                                                 // input validation
500 { error: "Illegal assistant response" | … }

Product        = { sku, title, category, colors: string[], sizes: string[], price: number /*EUR*/, description, imageUrl }
Interpretation = { searchQuery, negativeExamples: string[],
                   priceConstraint: { operator: "LESS_THAN"|"LESS_THAN_OR_EQUAL"|"GREATER_THAN"|"GREATER_THAN_OR_EQUAL", value: number } | null,
                   sortByLowestPrice: boolean }
```

A successful search makes two LLM calls (about 3–6 s). Conversation memory is server-side, keyed by `conversationId`; `clearMemory=true` resets it. The backend returns at most 10 products. There is no product-detail, catalog or category endpoint.

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
- Deployment pipeline and containerization.

## Decisions

**1. Stack: Next.js App Router + React + TypeScript + Tailwind CSS.**
These are the most widely used choices for a React storefront. App Router gives a server-side Route Handler for the proxy, `next/font` for self-hosted fonts and `next/image` for responsive images. The page itself is a client component, since everything is interactive after load. Versions are the current stable Next.js, React and Tailwind CSS v4 at implementation time, pinned in `package.json`. Package manager: npm. Alternative considered: Vite SPA (lighter, but needs a separate proxy story in production and is less "most popular" for shops).

**2. Proxy as a Route Handler, not `rewrites`.**
`app/api/search/route.ts` forwards the query string to `${BACKEND_URL}/search` with a 30 s timeout. It passes status and JSON through unchanged and maps connection failures to 502. Compared with `rewrites` it gives explicit timeout and error mapping, and the backend needs no CORS configuration. `BACKEND_URL` is read only on the server.

**3. State: one reducer, no data-fetching library.**
`useSearchSession()` holds `{ conversationId, turns: {message}[], status: idle|loading|success|rejected|invalid|error, products, interpretation, suggestions, fieldError, lastRequest }`. A search is a sequential, conversational command, not cacheable server state, so TanStack Query would add little. An `AbortController` per request implements supersede and timeout, and a monotonically increasing request id guards against late responses. `conversationId` is `crypto.randomUUID()` stored in `sessionStorage` (per tab, survives reload). The turns are kept in memory only: after a reload the server still has the context, but the trail starts empty.

**4. Typed API client.**
`lib/api/search.ts` returns a discriminated union `{kind: "ok", products, interpretation} | {kind: "rejected", suggestions} | {kind: "invalid", message} | {kind: "error"}`, which keeps HTTP details out of components. Types live in `lib/api/types.ts` and mirror the contract above.

**5. Component structure.**
```
app/layout.tsx            fonts, metadata, <body> chrome
app/page.tsx              <Storefront/>
app/api/search/route.ts   proxy
components/Header, Hero, SearchBar, ExamplePrompts, RotatingPlaceholder,
           ConversationTrail, InterpretationBar, ResultsGrid, ProductCard,
           ColorSwatches, QuickView, LoadingStory, SkeletonCard,
           RejectionNotice, ErrorNotice, EmptyResults
lib/format.ts             EUR formatting (Intl, en-IE), price chip wording, match count
lib/colors.ts             color name → hex map (catalog colors) with neutral fallback
lib/examples.ts           example prompts and placeholder rotation list
```

**6. Visual language.**
- Typography: `Cormorant Garamond` for the display headline and product titles, `Inter` for UI and body, both via `next/font/google`. The headline is large, with tight leading and a light weight. Labels use small uppercase text with letter spacing.
- Palette: off-white background (`#FAFAF8`), near-black text (`#111`), greys for secondary text and hairline borders. No accent color. Product color swatches are the only color on the page.
- Layout: generous white space and a centered hero at about 70 vh. After the first search the search bar becomes a slim sticky bar with the trail and interpretation below it. Cards have no borders or shadows. Hovering a card zooms the image slightly (`scale 1.03`, 400 ms) and underlines the title.
- Motion: subtle fades only, disabled under `prefers-reduced-motion`.
- Brand wordmark: placeholder `ATELIER` in one constant (`lib/brand.ts`).

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
- Location and naming: `public/images/categories/<slug>.webp` for the 13 backend slugs plus `default.webp`. This is the contract with backend `product-catalog`, and the backend sends `imageUrl = /images/categories/<slug>.webp`.
- Format: 4:5 portrait, 1200×1500, WebP quality about 80, at most 200 KB each, true grayscale.
- Rendering: `next/image` with `sizes` for the grid breakpoints. `remotePatterns` is left open for a future CDN host via an env var. A CSS `grayscale` filter guarantees black and white even if a future per-product photo is in color. `onError` swaps to `default.webp`.
- Production: images are generated once with an image-generation model, reviewed by a person and committed. The prompt template keeps the set consistent: *"Black and white studio product photograph of a single {item} on an invisible mannequin, front view, centered, soft diffused light, seamless light grey background, high detail fabric texture, minimal luxury fashion catalog style, no text, no logo, no person, 4:5 portrait."*
  - `{item}` per slug: puffer → "quilted down puffer jacket"; parka → "hooded travel parka with large pockets"; coat → "tailored trench coat"; rain → "lightweight water-resistant nylon rain jacket"; softshell → "minimalist softshell jacket"; fleece → "zip-up fleece jacket"; track → "retro track jacket with contrast stripes"; technical → "technical ski and hiking shell jacket"; field → "waxed cotton field jacket"; bomber → "classic bomber jacket with ribbed cuffs"; denim → "vintage denim trucker jacket"; leather → "leather biker jacket"; windbreaker → "packable sporty windbreaker"; default → "folded neutral jacket on a table".
- Why static now and blob storage later: see backend design `storefront-search-api` decision 1. The frontend only ever uses `imageUrl` from the API, so moving images to a CDN is a backend configuration change plus adding the host to `remotePatterns`.

**10. Testing.**
- Vitest + React Testing Library + MSW for component and reducer tests: formatting, chip wording, card content, swatch fallback, the reducer state machine (supersede, retry, new search), and client-side validation.
- Playwright end-to-end with `page.route('/api/search*')` mocks for happy path, refinement, rejection, 400, 500/timeout and empty results; viewport checks at 390 and 1440 px; `@axe-core/playwright` audit.
- A Route Handler unit test with a mocked `fetch` covers forwarding, status passthrough and 502.
- An optional smoke test against a running backend (`E2E_BACKEND=1`), excluded from the default run.

## Risks / Trade-offs

- [All cards in one category look identical, e.g. many puffers in one result] → Titles, prices and swatches differ. Per-product images later via the backend `image` CSV column, no frontend change.
- [Client-timed loading steps do not reflect real progress] → The wording is honest about intent rather than claiming completion. Backend Server-Sent Events (SSE) are a follow-up.
- [Contract drift with the in-flight backend changes] → Types mirror the backend specs. Playwright fixtures are taken from the backend spec scenarios, and the optional live smoke test catches drift.
- [Trail lost on reload while server memory persists] → Acceptable for a demo. Persisting turns in `sessionStorage` is a small follow-up if needed.
- [Generated images may contain artifacts or logos] → Manual review before commit; the prompt says "no text, no logo".
- [LLM latency up to 30 s on cold start] → 30 s timeout with retry; the proxy timeout is aligned with it.
