## Context

A search runs through one `ChatClient` whose advisors do query analysis (LLM call #1), retrieval and product filtering (LLM call #2). Both calls are wrapped by `StructuredOutputValidationAdvisor` with `maxRepeatAttempts(2)`, so a badly behaving model can make up to 3 attempts per call, 6 calls per search. Output length is not capped. Conversation memory already expires (`conversation-memory` spec), so it is not part of this change.

The deployed topology is CloudFront → one EC2 instance → Spring Boot. The instance is reachable only through CloudFront, which appends the viewer's IP to `X-Forwarded-For`. The instance is stopped at night and on weekends and started on demand, so the process restarts often. An edge WAF rate rule may also exist (see `aws-edge-hosting`) but is coarse; this change is the application-level control that knows what an expensive request is.

Rough cost (gpt-4o-mini, from memory, to be verified): about $0.001 per search, about $0.004 worst case with all retries. 300 searches per day is therefore about $0.30 on a normal day and a bounded few dollars in the worst case.

## Goals / Non-Goals

**Goals:**
- Bound the LLM spend per client and per day without breaking a visitor who tries a handful of searches.
- Keep invalid and rejected requests free: no LLM, embedding or vector call.
- Make every number configurable.

**Non-Goals:**
- Persistent or distributed counters (one instance, in-process state).
- Authentication, API keys or per-user quotas; invite codes are a possible later change.
- Edge/WAF configuration (`aws-edge-hosting`) and the OpenAI-side spending cap itself (an operations step).
- Caching responses to repeated queries.
- Changing the HTTP method of search (see `api-versioned-search-path`).

## Decisions

**1. Defaults (not yet confirmed by the owner): 5/minute and 20/hour per client; 300 per day globally.**
A person exploring the demo does a few searches per minute at most and typically fewer than 20 in total; the minute bucket stops scripted bursts, the hour bucket stops slow scripts. 300 per day is about 15 visitors at the hourly maximum, which is far above the expected 2 visitors/day. All three values live in `ecom.limits.per-minute`, `per-hour` and `daily-budget`.

**2. Admission happens in the controller, after input validation.**
`@Valid` binding runs first, so a request that fails validation (HTTP 400) costs nothing and consumes no quota. A request that passes validation calls `SearchAdmission.admit(clientKey)` before `ProductSearchService`. A query rejected by the guardrail (HTTP 422) already made one LLM call, so it consumes quota like any other accepted request.
- Alternative: a servlet filter or `HandlerInterceptor` before binding. Rejected: it would count invalid requests and would have to parse the query itself.
- Alternative: count LLM calls instead of requests, via an advisor. Rejected for the admission decision (the call count is only known after the fact), but retry and token caps (decision 5) bound the calls per accepted request, so requests are a good proxy.

**3. Per-client buckets are hand-written token buckets; no new dependency.**
Two token buckets per client key (capacity 5, refill 5 per minute; capacity 20, refill 20 per hour), stored in a `ConcurrentHashMap`, with a `Clock` for tests and a scheduled sweep that removes entries idle for more than an hour. The logic is small and the JVM runs in about 400 MB; a library (Bucket4j) would add a dependency and a cache library for the same result.
- Order of checks: per-client first, then the daily budget. If the daily budget is exhausted after the client bucket granted a token, the client token is returned, so a request rejected by the budget does not eat the client's quota.

**4. Client identification: rightmost `X-Forwarded-For` entry.**
The origin is reachable only through CloudFront, and CloudFront appends the viewer IP as the last entry, so earlier entries (client-supplied) are ignored. The property `ecom.limits.trusted-proxy-hops` (default 1) selects the entry counted from the right. When the header is absent (local development, tests) the servlet remote address is used. IPv6 addresses are reduced to their /64 prefix, because one client holds 2^64 addresses. The trust assumption is documented: if the origin were reachable directly, the header could be forged; the origin lock-down in `aws-backend-host` prevents that.
- Alternative: `CloudFront-Viewer-Address`. Needs an origin request policy and a port-stripping step; the XFF approach needs no CloudFront configuration. To be verified when the distribution exists.

**5. Cost per accepted search is bounded.**
- `StructuredOutputValidationAdvisor.maxRepeatAttempts` drops from 2 to 1 on both advisors, so at most 4 LLM calls per search instead of 6. The existing specs say "the configured retries", so no requirement changes; the "Schema retry" scenario remains valid with one retry.
- A maximum output token limit is set on both chat clients: `ecom.llm.query-analysis-max-output-tokens` (default 500; the result carries the verdict, query, parameters and up to a few suggested prompts) and `ecom.llm.product-filter-max-output-tokens` (default 300; at most 10 SKUs). A cap that is too low truncates JSON and turns into a schema failure, so the defaults leave a margin; the tests assert the typical outputs fit. The option name for the chosen model (`maxTokens` or `maxCompletionTokens`) is confirmed against Spring AI and the model.

**6. Global budget: UTC day, in memory, resets on restart.**
The counter is an atomic integer with the UTC date it belongs to; the first request of a new UTC day resets it. Because the instance is started and stopped on a schedule, a restart also resets it. This is accepted: the real hard cap is outside the application (decision 7), and the per-client limits still apply after every restart. A persistent counter (DynamoDB) was considered and rejected: it would bring the AWS SDK, instance credentials and a network call into the request path for a cap that is only a soft guard.

**7. Hard backstop outside the code: the OpenAI project budget.**
Use a dedicated OpenAI project with a monthly budget limit or prepaid credit, auto-recharge off, and notification emails. This is the only control that cannot be bypassed by an application bug or restart, so it is a required task (documented, not coded).

**8. The 429 contract.**
HTTP 429, `Retry-After` (seconds; for the daily budget, seconds until the next UTC midnight), JSON `{"code": "RATE_LIMITED" | "DAILY_BUDGET_EXHAUSTED", "message": "..."}`. The messages are in English; the storefront renders its own text per code. `DAILY_BUDGET_EXHAUSTED` is checked after `RATE_LIMITED`, so a client over its own limit always sees the client code.

## Risks / Trade-offs

- [All visitors behind one corporate NAT share a client key and the 20/hour limit] → Acceptable for a demo with a few visitors; the values are configurable.
- [The daily budget resets on restart, so repeated restarts raise the day's ceiling] → Restarts are scheduled (at most 2 per weekday, plus bounded off-hours sessions) and per-client limits still apply; the OpenAI cap is the backstop.
- [A rightmost-XFF assumption breaks if another proxy appears in front] → Configurable hop count and a documented assumption; a test covers 1 and 2 hops.
- [Lowering retries to 1 raises the rate of 500 "Illegal assistant response" under flaky model output] → Retry once is the compromise; the rate is visible in logs and the value is configurable via the existing advisor bean.
- [Token caps truncate a valid long answer] → Defaults have margin; a test feeds a maximal realistic response to check it fits.
- [The budget is exhausted by one aggressive client before others arrive] → The per-client limits cap each client at 20 per hour, so one client cannot take more than 20 of the 300 per hour.

## Migration Plan

1. Deploy with the defaults. No data migration.
2. Set up the OpenAI project budget before the first public link is sent.
3. Rollback: set the three limits to a very large value (or revert); retries and token caps revert with the commit.

## Open Questions

- Confirm or change the three numbers (5/minute, 20/hour, 300/day).
- Whether an invite-code quota (separate limits per campaign tag) is wanted later; not part of this change.
