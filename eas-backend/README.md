# E-Commerce Assistant Backend

Spring Boot service behind the storefront search API (`GET /api/v1/search`). See the
repository root `Readme.md` for the overall project and how to run it locally.

## Cost controls and operations

Every accepted search makes up to two LLM calls (query analysis, then product filtering)
plus at most one schema-validation retry per call, so this service rate-limits searches
and bounds the per-search LLM cost. The limits are configurable defaults in
`application.yml` under `ecom.limits.*` and `ecom.llm.*`:

| Property | Default | Meaning |
|---|---|---|
| `ecom.limits.per-minute` | 5 | accepted searches per client per minute |
| `ecom.limits.per-hour` | 20 | accepted searches per client per hour |
| `ecom.limits.daily-budget` | 300 | accepted searches per UTC day across all clients |
| `ecom.limits.trusted-proxy-hops` | 1 | trusted `X-Forwarded-For` entries, counted from the right |
| `ecom.llm.query-analysis-max-output-tokens` | 500 | max output tokens for the query-analysis call |
| `ecom.llm.product-filter-max-output-tokens` | 300 | max output tokens for the product-filtering call |

A rejected search returns HTTP 429 with a `Retry-After` header and a body
`{"code":"RATE_LIMITED"|"DAILY_BUDGET_EXHAUSTED","message":"..."}` (see `openapi.yaml`).

### In-memory limitation

The per-client rate-limit buckets and the daily-budget counter live in process memory
only. They reset to zero whenever the application restarts, and they are not shared
between instances. Repeated restarts can therefore raise the day's effective ceiling; the
per-client limits still apply after every restart. The hard spending cap is outside this
application (below).

### Required before the first public link (runbook step)

The application-level limits are soft guards only — an application bug or a restart can
bypass them. Set the hard backstop in OpenAI before sending the first public link:

1. Create a **dedicated OpenAI project** used only by this demo.
2. Give it a **monthly budget limit** (or **prepaid credit**), and set **auto-recharge off**.
3. Add **notification e-mails** for budget threshold alerts.

This is the only control that cannot be exceeded by an application bug or a restart.
