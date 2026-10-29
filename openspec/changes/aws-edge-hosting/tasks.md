## 1. Verification first

- [ ] 1.1 Verify against the current CloudFront free flat-rate plan documentation and console: included requests, WAF rate rule, standard logging, response headers policy, origin timeout/attempt settings, custom error caching, custom domain; record the answers and the chosen fallbacks in the design

## 2. Buckets and distribution

- [ ] 2.1 Create the private SPA bucket (block public access, origin access control) and the private log bucket with a 90-day lifecycle rule
- [ ] 2.2 Create the distribution with three origins and the behaviors `/api/v1/control/*` (Lambda, OAC, caching disabled), `/api/v1/*` (EC2 HTTP :8080, custom header `X-Origin-Verify`, caching disabled, 1 attempt, 2-3 s connect timeout), default `/*` (S3)
- [ ] 2.3 Configure error handling for `/api/*` so 5xx responses are not cached (minimum TTL 0); confirm the response timeout covers the longest search
- [ ] 2.4 Add the response headers policy (HSTS, nosniff, referrer policy, CSP with `default-src 'self'`, no framing) to the default behavior
- [ ] 2.5 Generate the origin secret once, store it in SSM (shared with `aws-backend-host` and the control Lambda), and keep it out of the repository

## 3. Edge protection

- [ ] 3.1 If WAF is available on the plan: rate-based rule on `/api/v1/search` per client IP with a custom 429 response; exclude `/api/v1/health` and `/api/v1/control/*`; otherwise record the decision and rely on `demo-cost-controls`
- [ ] 3.2 Test that direct calls to the EC2 address, to the Function URL and to the bucket URL are rejected

## 4. SPA deployment

- [ ] 4.1 Add a deploy script/target: sync the Vite build to the bucket with long immutable cache for `assets/`, short cache for `index.html` and unhashed images, invalidate `/index.html`
- [ ] 4.2 End-to-end check on the `*.cloudfront.net` name with the instance stopped: fast 5xx on `/api/v1/health` (not cached), `/up` wakes it, health turns 200 without a stale 504, search works

## 5. Statistics

- [ ] 5.1 Enable standard logging to the log bucket; load a page with `?ref=test` and confirm the query string is in the log
- [ ] 5.2 Write the DuckDB queries (visits/day, by `ref`, by country, searches/day by status, wakes/day, bot split by user agent) in `infra/stats/` with a short README; mark Athena as optional
- [ ] 5.3 Add the privacy note (IP addresses logged, purpose, 90-day retention) to the README

## 6. Optional: custom domain (only when decided)

- [ ] 6.1 Request the ACM certificate in us-east-1, add the alternate domain name and DNS alias; verify nothing else changes
- [ ] 6.2 Optionally add HTTPS to the origin (certificate on the host) and remove the plain-HTTP exception from the design

## 7. Runbook and cost

- [ ] 7.1 Extend `infra/RUNBOOK.md`: deploy SPA, invalidate, rotate the origin secret (SSM, host, distribution, Lambda in that order), read logs, disable the distribution
- [ ] 7.2 Cost verification: check the AWS Budgets alert exists; after the first week review Cost Explorer for CloudFront, S3, logging and WAF lines against the plan assumptions and record actual figures
