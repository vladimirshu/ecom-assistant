# ATELIER storefront (eas-frontend)

Client-only static SPA for the ATELIER apparel discovery demo: a Sage-themed
natural-language clothing search with a side-by-side conversation workspace,
local category browsing, an isolated search demonstration, and schedule-aware
service availability.

## Stack

- Vite + React 18 + strict TypeScript
- Tailwind CSS v4 (Sage theme tokens defined in `src/index.css`)
- Vitest + React Testing Library + MSW for unit/component tests
- Playwright for end-to-end tests
- axe-core for accessibility checks

## Commands

```bash
npm install          # install dependencies
npm run dev          # start the Vite dev server
npm run build        # type-check and produce static files in dist/
npm run preview      # serve the production build locally
npm run lint         # ESLint
npm test             # Vitest (unit/component)
npm run test:e2e     # Playwright against dist/ (build first, then install Chromium)
npm run check:images # validate artwork, provenance and backend seed mappings
```

## Environment

Copy `.env.example` to `.env` for local development. Run `npx playwright install chromium` once before running the browser tests.

| Variable | Purpose |
|---|---|
| `VITE_DEV_BACKEND_URL` | Backend target for the Vite `/api` dev proxy (default `http://localhost:8080`) |

## Availability schedule configuration

The service availability schedule is configured in code, not via environment
variables. Edit `src/config/schedule.ts` (`tz`, `days`, `from`, `to`) to change
opening hours. Changing it requires a frontend rebuild, and the values are
validated by `src/config/schedule.test.ts`.

## API contract

All requests are same-origin and relative. In development the Vite server
proxies `/api/*` to `VITE_DEV_BACKEND_URL`; in production CloudFront routes the
same paths to the backend.

- `GET /api/v1/search?message=&conversationId=&clearMemory=` (search/refinement)
- `GET /api/v1/products?limit=24[&category=][&cursor=]` (cursor-paged catalog)
- `GET /api/v1/health` (in-hours readiness probe)
- `GET /api/v1/control/up` (control-plane `{ state: "UP" | "STARTING" }`)

## Static hosting / SPA fallback

`dist/` is a static build for S3/CloudFront. The host must serve the SPA entry
(`index.html`) for `/` and `/catalog` without rewriting `/api/v1/*` responses.
Owned by the root `aws-edge-hosting` change.

## Project layout

```
src/
  components/       UI components (Header, SearchBar, ProductCard/Grid, ...)
  session/          useSearchSession (conversation state machine)
  catalog/          useCatalog (cursor-paged browsing state)
  gate/             service-availability-gate (schedule, state machine, panel)
  lib/api/          typed API clients (search, catalog, health, control)
  lib/              format, brand, examples, fixtures, categories helpers
  data/             categories.json (navigation) and catalog-images.json (imagery manifest)
public/images/categories/   alpha WebP product illustrations (incl. default.webp)
scripts/check-images.mjs    release coverage check for imagery
```

## Catalog imagery

The imagery brief, prompt template and acceptance checklist live in
`openspec/changes/storefront-search-ui/image-generation.md`. The baseline
manifest (`src/data/catalog-images.json`) records 17 garment keys plus
`default`. The exported alpha WebP assets are in `public/images/categories/`. Their original
transparent sources are kept in `artwork/catalog/`.
Run `npm run check:images` from the repository checkout to validate
asset coverage, export metadata and references in the backend seed.
