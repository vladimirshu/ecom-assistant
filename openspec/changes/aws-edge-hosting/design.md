## Context

Traffic estimate from the owner: about 2 visitors per day with about 20 API calls each, plus page loads, so a few thousand requests per month, far below 1 million. The SPA is built by Vite into static files. The backend serves `/api/v1/search` and `/api/v1/health` from an EC2 origin that is stopped most of the time; the control Lambda serves `/api/v1/control/up`.

Everything about pricing, plan inclusions (flat-rate plan, WAF, standard logging, custom domain, Lambda@Edge), and behavior defaults (error caching, origin timeouts) is an assumption to verify in the console and documentation before building.

## Goals / Non-Goals

**Goals:**
- One public origin name for page, API and control calls.
- A stopped origin produces a quick, uncached 5xx so the SPA can react within seconds.
- The origin and the control Lambda are unreachable except through this distribution.
- Visit statistics at zero cost without any code in the SPA.

**Non-Goals:**
- A custom domain as a requirement (optional section only).
- HTTPS between CloudFront and the origin (no domain, no certificate on the host).
- Server-side rendering, image optimization, or any edge compute.
- Application-level rate limits (`demo-cost-controls`).

## Decisions

**1. Three origins, path-based behaviors.**

    /api/v1/control/*  → Lambda Function URL   (OAC, AWS_IAM, caching disabled)
    /api/v1/*          → EC2 origin :8080 HTTP (custom header, caching disabled)
    /*  (default)      → S3 bucket             (OAC, caching enabled)

Behavior order puts the more specific `control` path first. The `/api/v1` prefix means no URL rewriting function is needed.
- Alternative: separate subdomains per origin (needs a domain and CORS). Rejected.

**2. S3 private bucket with origin access control.**
Block all public access; the bucket policy admits only this distribution. The default root object is `index.html`; there is a single page, so no SPA fallback rule is required. Unknown paths return the S3 error mapped to `index.html` only if the frontend later adds client-side routes.

**3. Cache policy.**
Vite emits content-hashed files under `assets/`; they are uploaded with `Cache-Control: public, max-age=31536000, immutable`. `index.html` and images without hashes are uploaded with a short `max-age` (for example 60 s); the deploy invalidates `/index.html`. Images referenced as `/images/...` (`ecom.assets.base-url` default) are cached for a day.

**4. Fast failure for a stopped origin.**
The `/api/v1/*` origin uses connection attempts = 1 and connection timeout of 2-3 seconds, read timeout above the longest search (the LLM pipeline takes 3-6 s, up to about 30 s cold; the default 30 s response timeout is assumed to be the limit to verify). Custom error responses for 502/503/504 under `/api/*` use error caching minimum TTL 0, otherwise a cached 504 (assumed 10 s default) would be served just after the instance becomes ready.

**5. Origin lock-down in layers.**
(a) Security group allows only the managed CloudFront origin-facing prefix list (`aws-backend-host`); (b) CloudFront adds `X-Origin-Verify: <secret>` and the backend rejects other requests (a prefix list admits any distribution); (c) the Function URL uses IAM auth with OAC. The secret is generated once, stored in SSM and passed to CloudFront as a deploy-time value; rotation is a runbook procedure.

**6. Rate limit at the edge when available.**
If the free flat-rate plan includes WAF, add a rate-based rule per client IP on `/api/v1/search` with a custom 429 response (the SPA treats 429 as "slow down", not "closed"). If it does not, skip WAF (a separate web ACL has its own monthly charge, assumption) and rely on `demo-cost-controls`.

**7. Security headers.**
A response headers policy on the default behavior: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, a restrictive `Content-Security-Policy` (`default-src 'self'`, fonts and images from self, `connect-src 'self'`), `frame-ancestors 'none'`. The SPA uses self-hosted fonts, which keeps the policy tight.

**8. Visit statistics from CloudFront standard logs.**
Standard logging to a private S3 bucket with a 90-day lifecycle rule. One log line per request includes timestamp, edge location, path, query string, status, user agent and client IP. Mapping:
- visit = request for `/` or `/index.html` (excluding obvious bots by user agent);
- campaign = `ref` value in the query string of that request (the SPA keeps `?ref=` in the URL; no beacon, no Lambda call);
- search = `/api/v1/search` requests grouped by status (200, 422, 429, 5xx);
- wake = `/api/v1/control/up`.
Analysis runs locally with DuckDB reading the downloaded log files (zero service cost, small request charges); Athena over the same bucket is optional.
- Privacy: client IP addresses are personal data. Retention is limited to 90 days, the bucket is private, and the README states what is logged and why. Queries aggregate and do not export raw IPs.
- Alternative: JavaScript beacon to a Lambda on every page view (contradicts "call the Lambda only when the health probe fails" and costs requests).

**9. Optional custom domain section.**
When the owner decides on a domain: ACM public certificate in us-east-1 (DNS validation), alternate domain name on the distribution, DNS alias record (Route 53 or the registrar's DNS), then optionally HTTPS to the origin (Caddy or similar on the host with a DNS-challenge certificate) and removal of the plain-HTTP exception. None of the other tasks depend on it.

## Risks / Trade-offs

- [Free flat-rate plan may exclude WAF, standard logging or a feature used here] → Verification task first; documented fallbacks (no WAF, built-in CloudFront reports plus wake events only).
- [Plain HTTP between CloudFront and the origin] → Payload is product search text; locked down by prefix list and secret header; HTTPS is a follow-up with a domain.
- [Cached 504 after wake] → Error caching TTL 0 on `/api/*` and caching disabled; test explicitly.
- [Search takes longer than the CloudFront response timeout] → Backend `maxTokens` and retries are limited in `demo-cost-controls`; check the plan's timeout limit.
- [Log lines contain IP addresses] → 90-day expiry, private bucket, aggregate-only analysis, README note.
- [Bots that execute JavaScript inflate visit counts and wake the instance] → User-agent classification in analysis; wake events show the effect; cost ceiling is the 24/7 case.
- [Link-preview or monitoring traffic hitting the API origin repeatedly] → Edge rate rule when available; otherwise backend limits.

## Migration Plan

1. Verify plan inclusions; create buckets and the distribution with the S3 behavior only; upload a placeholder page.
2. Add the Lambda and EC2 behaviors; check `/api/v1/control/up` and `/api/v1/health` with the instance stopped (fast 504, not cached) and running.
3. Deploy the real SPA build, enable logging, run through the full wake flow from a browser.
4. Rollback: point the default behavior back to a placeholder object or disable the distribution; the buckets hold only rebuildable content and logs.

## Open Questions

- Does the free flat-rate plan allow the origin timeouts, custom error caching, response headers policy, WAF rule, standard logging and (later) a custom domain used here?
- Custom domain: decided later by the owner.
- IaC tool: CDK (TypeScript) as default, Terraform as alternative; not decided.
- Deployment time zone is relevant only to `aws-control-plane`, but stack parameters are shared.
- Region (the certificate for a future domain must be in us-east-1 regardless).
