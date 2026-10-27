## Why

The assistant's value is that a customer describes what they want, in their own words and language, and gets the best matches without browsing a catalog. Today this is only reachable through a raw JSON endpoint. A storefront that looks like a real premium fashion shop and visibly shows "what we understood" makes the concept tangible for customers and stakeholders.

## What Changes

- New standalone Next.js (App Router) + React + TypeScript + Tailwind CSS application in `frontend/`, independent of the Gradle build.
- One-page storefront: a minimal header and a hero with a single large search field, rotating example placeholders (including non-English ones) and clickable example prompts. There is deliberately no category navigation.
- Conversational search: the first request starts a conversation, follow-ups refine it ("same but in navy"), and "New search" clears it. The conversation is kept for the browser tab.
- "We understood" bar showing the backend's interpretation: English search query, price limit, excluded items and lowest-price ordering, plus the trail of the customer's own messages.
- Product grid of premium cards (black-and-white category image, serif title, EUR price, color swatches, sizes) and a quick-view panel with the full description.
- Staged loading story during the backend's multi-second LLM pipeline, skeleton cards, cancellation of superseded requests.
- Friendly states for rejection (HTTP 422 suggested prompts as clickable chips), invalid input (HTTP 400), empty results, and server/network failure with retry.
- Black-and-white images, one per catalog category plus a default, in `public/images/categories/`.
- Same-origin proxy `/api/*` → backend, so the browser never calls the backend directly.

## Capabilities

### New Capabilities
- `storefront-shell`: page layout, brand, hero, example prompts, visual language, responsiveness and accessibility baseline.
- `conversational-search`: submitting and refining searches, conversation lifecycle, backend communication, loading, rejection, validation and error states.
- `search-results`: interpretation bar, product cards and grid, category images, quick view, empty results.

### Modified Capabilities
<!-- none: first change of the frontend project -->

## Impact

- New project `frontend/` (package.json, Next.js app, tests, static images); no changes to backend code.
- Runtime configuration: `BACKEND_URL` (server-side, default `http://localhost:8080`).
- API contract consumed: backend `GET /search` as specified by backend changes `storefront-search-api`, `rag-product-filtering` and `query-analysis-guardrail` (`../backend/openspec/changes/`). Category slugs must match the backend `product-catalog` category list.
- Depends on: backend change `storefront-search-api` for titles, images and interpretation. Until it is deployed, end-to-end tests run against a mocked backend.
