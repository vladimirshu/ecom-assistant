## 1. Project setup

- [ ] 1.1 Scaffold Next.js (App Router, TypeScript strict, Tailwind CSS v4, ESLint, npm) in `frontend/` without overwriting `openspec/` and `.codex/`; add `frontend/.gitignore` (`node_modules`, `.next`, `test-results`, `playwright-report`)
- [ ] 1.2 Add Vitest + React Testing Library + MSW and Playwright + `@axe-core/playwright`; npm scripts `dev`, `build`, `lint`, `test`, `test:e2e`
- [ ] 1.3 Add `.env.example` with `BACKEND_URL=http://localhost:8080` and a `frontend/README.md` (run with backend, env vars, tests, image contract)

## 2. API layer

- [ ] 2.1 `lib/api/types.ts` mirroring the backend contract (Product, Interpretation, PriceConstraint, error bodies)
- [ ] 2.2 `app/api/search/route.ts` proxy: forward query string to `${BACKEND_URL}/search`, 30 s timeout, status/body passthrough, 502 on connection failure; unit tests
- [ ] 2.3 `lib/api/search.ts` client returning the discriminated union, with `AbortSignal` support; unit tests per status (200, 422, 400, 5xx, network, abort)

## 3. Search session

- [ ] 3.1 `useSearchSession` reducer + hook: conversation id in `sessionStorage`, `clearMemory` on first turn only, turns trail, supersede via `AbortController` + request id, 30 s timeout, retry of `lastRequest`, new search reset
- [ ] 3.2 Client-side input check (trim, 3–500 chars, no `<`/`>`) with inline hint text
- [ ] 3.3 Reducer/hook tests covering every scenario in `conversational-search`

## 4. Shell and visual language

- [ ] 4.1 `app/layout.tsx` with `next/font` (Cormorant Garamond, Inter), Tailwind theme tokens (palette, type scale, spacing), metadata
- [ ] 4.2 `Header` (wordmark, conditional "New search"), `Hero` (headline, sub-line), `SearchBar` (hero and compact variants, labelled, Enter submits), `RotatingPlaceholder` (reduced-motion aware), `ExamplePrompts`
- [ ] 4.3 Responsive layout and hero → compact bar transition

## 5. Results

- [ ] 5.1 `lib/format.ts` (EUR, price chip wording, match count) and `lib/colors.ts` (catalog color map + neutral fallback) with unit tests
- [ ] 5.2 `ConversationTrail`, `InterpretationBar`, result count
- [ ] 5.3 `ProductCard` (4:5 `next/image`, grayscale, alt = title, `onError` → default image, swatches, sizes) and `ResultsGrid` (2/3/4 columns)
- [ ] 5.4 `QuickView` dialog (focus trap, Escape, click outside, focus return)
- [ ] 5.5 `LoadingStory` + `SkeletonCard` (staged steps, live region, dimmed previous results), `RejectionNotice` (suggestion chips), `ErrorNotice` (Try again), `EmptyResults`

## 6. Images

- [ ] 6.1 Generate 13 category images + `default` with the prompt template from the design; review for artifacts/logos; convert to grayscale WebP 1200×1500 ≤ 200 KB into `public/images/categories/`
- [ ] 6.2 Test that every backend category slug has a file in `public/images/categories/`

## 7. End-to-end and quality

- [ ] 7.1 Playwright fixtures derived from backend spec scenarios; tests for happy path, refinement (same conversation id, `clearMemory=false`), new search, rejection + suggestion click, 400 inline hint, 500 + retry, timeout, empty results, double submit
- [ ] 7.2 Playwright viewport checks (390 px 2 columns, no horizontal scroll; 1440 px 4 columns) and axe audit on landing and results pages
- [ ] 7.3 Manual check against a running backend with `storefront-search-api` deployed (English and one non-English request)
- [ ] 7.4 `npm run lint && npm test && npm run test:e2e && npm run build` green
