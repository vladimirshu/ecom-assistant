# Storefront verification

## Review, 2026-11-09

- Control requests now time out after five seconds so a stalled response cannot
  hold the storefront closed indefinitely. Submitting while startup has failed
  preserves the draft and leaves restarting to the panel's Try again action.
- Conversation history can reopen rejection, quota and error feedback without a
  search request. Historical feedback shows only earlier results and only the
  latest failed turn can be retried. Leaving during a retry restores its feedback,
  and edits typed while a readiness check is pending survive dispatch.
- The unknown URL category `all` has separate state from the unfiltered catalog.
  Returning to All clothing starts a fresh first-page request.

Lint, 51 unit tests, 23 Chromium tests with API fixtures, the production build,
the 18-entry imagery check and strict OpenSpec validation passed.

Task 8.6 still requires live acceptance and a published backend catalog revision.
The backend's documented opt-in command registers `SearchEvaluationRunner` as a
Spring bean. There is currently no entry point that loads the judged suite, invokes
`run` and saves its report. Its metrics also use only the last turn of each
conversation for scoring. The dated smoke test below remains the available live
evidence for the bundled example.

## Review, 2026-11-06

Reviewed the implementation against the seven capability deltas. Fixed:

- Catalog append blocked by readiness now enters a paused state, preserving cards
  and cursor. Restored readiness cannot replay it through the scroll observer;
  Load more explicitly resumes it. An interrupted initial load can still resume.
- Search rate limits now store an absolute deadline with the turn. Catalog
  navigation no longer restarts the wait, and retry also checks that deadline.
- Animated demonstration requests now have screen-reader text without announcing
  each typed character. The visible typing remains hidden from assistive technology.
- Defensive SKU deduplication now also covers the first catalog page.

Validation on the reviewed implementation:

- `npm run lint`: passed.
- `npm test`: 50 tests passed across eight files.
- `npm run build`: passed.
- `npm run check:images`: passed for all 18 entries.
- `npm run test:e2e`: 20 Chromium tests passed, including axe checks, responsive
  layouts, explicit pagination recovery, elapsed rate limits during navigation,
  complete example query/refinement playback, replay and reduced-motion content.
- `openspec validate storefront-search-ui --strict`: passed.

The browser suite uses API fixtures; no new live-model relevance acceptance was
performed in this review. The live evidence below remains dated 2026-11-04.

### Remaining release dependency (task 8.6)

The owning backend checklist is now at
`../../../../eas-backend/openspec/changes/archive/2026-10-30-seed-catalog-quality/tasks.md`.
Tasks 5.2–5.5 and 6.1–6.3 remain unchecked: imported-revision verification, full
pipeline measurements, two independent budgeted model passes, diagnosis,
accepted-content publication and final frontend handoff. Its archived location
is not evidence of acceptance. There are no completed acceptance reports in the
repository to replace those missing results.

The existing bundled jeans example has observed live provenance and passing UI
checks, but final approval against an accepted/published backend revision remains
pending. Keep task 8.6 unchecked and this frontend change active until that
dependency is satisfied. Proposal/design wording has been updated to distinguish
implemented browsing from unfinished content acceptance.

## Live smoke test, 2026-11-04

An isolated local Docker Compose backend and Qdrant loaded the repository’s
`seed-catalog-quality/v1` CSV. Chromium used the normal storefront routes and
same-origin proxy. The UI rendered every product in API order and displayed the
first product’s exact SKU and description in its dialog on each successful query.

| Request | Interpretation | First returned SKUs |
| --- | --- | --- |
| navy jeans | navy jeans | M-JNS-10, W-JNS-11, M-JNS-15 |
| actually make it black | black jeans | W-JNS-09, M-JNS-11, M-JNS-05 |
| Un pantalon confortable pour le bureau, avec de l’élasticité | comfortable office trousers with elasticity | M-TRS-04, W-TRS-04, M-TRS-15 |

The refinement used the same conversation ID and `clearMemory=false`. New search
reset the workspace and sent the French request with a new conversation ID.
The homepage example uses the first two observed cards from each jeans turn.

Live catalog checks returned 24 products plus a cursor for `women` and
`women-jackets`, five products for `women-puffer`, and 15 for `women-jeans`.
The frontend category tree has the same IDs and parent relationships as the API.

## Findings affecting release

- The first live smoke test returned HTTP 500 for a query without a price. Both
  backend query-analysis validators now use one schema that accepts explicit
  `priceConstraint: null` and still validates non-null objects.
- The rain-jacket request succeeded after the schema fix. The subsequent
  “I also need it warm for winter” request returned 422 because the model
  considered rain protection and winter warmth contradictory.
- The black-jeans refinement included one indigo-only SKU at rank five
  (`M-JNS-01`). The UI showed the backend's returned list in order.
- The example has been checked against a live run of the authored seed. Full
  model relevance acceptance and catalog publication remain open in the backend
  seed-catalog-quality checklist.

## Artwork

The manifest records each asset's built-in image generation prompt, reference,
source and reviewed WebP export. The 18 files have 1200×1500 canvases, meaningful
alpha and sizes below 200 KB. The mixed garment grid was checked over Sage paper,
wash, the dialog background and a dark contrast surface. The production build
copies the reviewed WebPs byte for byte.
