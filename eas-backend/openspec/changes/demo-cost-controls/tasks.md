## 1. Configuration

- [ ] 1.1 Add `config/EcomLimitsProperties` (`ecom.limits.per-minute` 5, `per-hour` 20, `daily-budget` 300, `trusted-proxy-hops` 1) and `ecom.llm.query-analysis-max-output-tokens` 500, `product-filter-max-output-tokens` 300 with the entries in `application.yml`
- [ ] 1.2 Provide a `Clock` bean (system UTC) if none exists yet, for limiter and budget tests

## 2. Limiter and budget

- [ ] 2.1 Implement client identification in `limits/ClientKeyResolver`: rightmost `X-Forwarded-For` entry by hop count, remote-address fallback, IPv6 reduced to /64
- [ ] 2.2 Implement the per-client token buckets (minute and hour) in `limits/ClientRateLimiter` with a `Clock`, plus a scheduled sweep that drops clients idle for over an hour
- [ ] 2.3 Implement `limits/DailySearchBudget` (atomic counter tied to the UTC date, reset on the first search of a new day, `secondsUntilReset`)
- [ ] 2.4 Implement `limits/SearchAdmission.admit(clientKey)`: client limit first, then the daily budget, returning the client token when the budget rejects; throws `SearchRejectedException(code, retryAfterSeconds)`
- [ ] 2.5 Call `SearchAdmission` in `ProductsController.findProducts` after validation and before `ProductSearchService`; map `SearchRejectedException` to 429 with `Retry-After` and `{code, message}` in `GlobalExceptionHandler`

## 3. LLM cost bounds

- [ ] 3.1 Change `maxRepeatAttempts(2)` to `maxRepeatAttempts(1)` on both `StructuredOutputValidationAdvisor` beans in `AiConfig`
- [ ] 3.2 Set the maximum output token option on both chat clients from `ecom.llm.*` (confirm `maxTokens` vs `maxCompletionTokens` for the configured model)

## 4. Tests

- [ ] 4.1 Unit tests for buckets and budget with a mutable `Clock`: minute and hour limits, recovery after `Retry-After`, clients independent, idle sweep, UTC day rollover, budget returns the client token
- [ ] 4.2 Client key tests: forged leading entries ignored, one and two trusted hops, no header, IPv6 /64 grouping
- [ ] 4.3 Controller/integration tests: 6th search in a minute gives 429 `RATE_LIMITED` with `Retry-After`; budget of 2 gives `DAILY_BUDGET_EXHAUSTED`; 400 requests consume nothing; rejected requests make no WireMock LLM, embedding or Qdrant call; `/api/v1/health` is not limited
- [ ] 4.4 Retry and token cap tests: a persistently invalid query-analysis response produces exactly 2 calls; both requests carry the configured output token limit; a typical full response fits the cap
- [ ] 4.5 Run `./gradlew test`

## 5. Documentation and operations

- [ ] 5.1 Document the 429 responses and their codes in `openapi.yaml`
- [ ] 5.2 Write the runbook step: create a dedicated OpenAI project, set a monthly budget or prepaid credit with auto-recharge off, add notification emails; do this before the first public link is sent
- [ ] 5.3 Document that limiter and budget state are in memory and reset on restart
