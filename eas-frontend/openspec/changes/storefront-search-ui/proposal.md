## Why

The assistant's value is that a customer describes what they want, in their own words and language, and gets the best matches without browsing a catalog. Today this is only reachable through a raw JSON endpoint. A storefront that looks like a real premium fashion shop and visibly shows "what we understood" makes the concept tangible for customers and stakeholders.

## What Changes

- New standalone client-only Vite + React + TypeScript + Tailwind CSS single-page application in `eas-frontend/`, independent of the Gradle build. It builds to static files that are served from S3 behind CloudFront.
- One-page storefront: a minimal header and a hero with a single large search field, rotating example placeholders (including non-English ones) and clickable example prompts. There is deliberately no category navigation.
- Conversational search: the first request starts a conversation, follow-ups refine it ("same but in navy"), and "New search" clears it. The conversation token lives in application memory only: a new token on every page load and on "New search".
- "We understood" bar showing the backend's interpretation: English search query, price limit, excluded items and lowest-price ordering, plus the trail of the customer's own messages.
- Product grid of premium cards (black-and-white category image, serif title, EUR price, color swatches, sizes) and a quick-view panel with the full description.
- Staged loading story during the backend's multi-second LLM pipeline, skeleton cards, cancellation of superseded requests.
- Friendly states for rejection (HTTP 422 suggested prompts as clickable chips), invalid input (HTTP 400), usage limits (HTTP 429 `RATE_LIMITED` / `DAILY_BUDGET_EXHAUSTED`), empty results, and server/network failure with retry.
- Black-and-white images, one per catalog category plus a default, in `public/images/categories/`.
- The browser calls the same-origin path `/api/v1/search`. In production CloudFront routes `/api/v1/*` to the backend, and in development the Vite dev server proxies it to `http://localhost:8080`. No application proxy and no CORS configuration are needed.
- Backend availability (the service is asleep outside business hours) is handled by the separate change `service-availability-gate`.

## Capabilities

### New Capabilities
- `storefront-shell`: page layout, brand, hero, example prompts, visual language, responsiveness and accessibility baseline.
- `conversational-search`: submitting and refining searches, conversation lifecycle, backend communication, loading, rejection, validation and error states.
- `search-results`: interpretation bar, product cards and grid, category images, quick view, empty results.

### Modified Capabilities
<!-- none: first change of the frontend project -->

## Impact

- New project `eas-frontend/` (package.json, Vite app, tests, static images); no changes to backend code.
- No runtime configuration: the browser uses relative `/api/v1/...` URLs. The Vite dev server proxy target is a development setting (`VITE_DEV_BACKEND_URL`, default `http://localhost:8080`).
- API contract consumed: backend `GET /api/v1/search` as specified by backend changes `api-versioned-search-path`, `storefront-search-api`, `rag-product-filtering`, `query-analysis-guardrail`, `conversation-memory` and `demo-cost-controls` (`../eas-backend/openspec/changes/`). Category slugs must match the backend `product-catalog` category list.
- Depends on: backend change `storefront-search-api` for titles, images and interpretation, and `api-versioned-search-path` for the path. Until they are deployed, end-to-end tests run against a mocked backend.
- Deployment (S3 + CloudFront) is out of scope here; see the root-level `aws-edge-hosting` change.
