## Why

The prototype is going to be public: hiring managers and anyone holding the link can run searches, and every successful search costs two LLM calls (query analysis, then product filtering) plus up to two schema-validation retries each. Today nothing limits how often one visitor, or all visitors together, can trigger them, so a script, a crawler that executes the page, or an enthusiastic visitor can run the LLM bill up. The goal is to keep the cost near zero while the demo stays usable for a person who tries ten or twenty searches.

## What Changes

- Add a per-client rate limit on `GET /api/v1/search`: at most 5 searches per minute and 20 per hour per client. The client is identified by the viewer IP address the CDN adds to the request; IPv6 clients are grouped by their /64 prefix.
- Add a global daily search budget: at most 300 accepted searches per UTC day across all clients.
- Respond to a rejected request with HTTP 429, a `Retry-After` header and a JSON body `{"code":"RATE_LIMITED"|"DAILY_BUDGET_EXHAUSTED","message":"..."}`. No LLM, embedding or vector store call is made for a rejected request.
- Reduce the cost of one accepted search: schema-validation retries drop from 2 to 1 for both LLM calls, and both calls get a maximum output token limit.
- Limits apply to search only. `GET /api/v1/health` is never limited.
- Document the hard backstop as an operations step, not code: a prepaid or budget-limited OpenAI project with auto-recharge off.
- All numbers are configurable defaults, set through `ecom.limits.*` properties.

## Capabilities

### New Capabilities
- `search-rate-limiting`: per-client minute and hour limits, client identification behind a CDN, the 429 response, what is and is not limited.
- `llm-cost-budget`: global daily budget of accepted searches, UTC reset, per-search cost bounds (retries and output tokens), documented in-memory limitation and the external hard cap.

### Modified Capabilities
<!-- none: existing specs say "the configured retries", so lowering the count does not change their requirements -->

## Impact

- Code: new `limits/` package (client identification, token buckets, daily budget, admission service), `config/EcomLimitsProperties`, an admission call in `controller/ProductsController` after input validation, `exception/GlobalExceptionHandler` (429 mapping), `config/AiConfig` (`maxRepeatAttempts(1)`, output token options on both chat clients), `application.yml` (`ecom.limits.*`, `ecom.llm.*`).
- API: new HTTP 429 response on `/api/v1/search` (documented in `openapi.yaml`).
- Tests: unit tests for the buckets and budget with a mutable `Clock`; controller/integration tests for 429 and for "rejected requests make no LLM call".
- Dependencies: none new (hand-written token buckets; see design).
- Cross-project contract: the storefront shows a message for each 429 code and never treats a 429 as "service asleep" (`service-availability-gate`).
- Depends on: `api-versioned-search-path` (the limited path is `/api/v1/search`).
- Operations: the numbers above are defaults the owner has not yet confirmed; they can be changed in configuration without code changes.
