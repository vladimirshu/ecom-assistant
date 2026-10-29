## Why

The storefront is a client-only Vite SPA and the backend and control Lambda sit behind different origins. One CloudFront distribution gives a single domain (no CORS), HTTPS from the browser, caching of static files, protection of the origin, and the access logs that are the only zero-cost source of visit statistics. The owner assumes CloudFront's free flat-rate plan (1 million requests included) covers the traffic; this must be verified, including which features the plan allows.

## What Changes

- Create a private S3 bucket for the SPA build, served through CloudFront with origin access control; hashed assets cached as immutable, `index.html` with a short TTL.
- Create one CloudFront distribution with three origins and path behaviors: `/*` → S3, `/api/v1/control/*` → Lambda Function URL (origin access control), `/api/v1/*` → EC2 origin over HTTP port 8080 with the `X-Origin-Verify` custom header.
- For `/api/*`: caching disabled, origin connection attempts 1, connection timeout 2-3 seconds, error caching minimum TTL 0, so a stopped instance yields a fast, uncached 5xx.
- Protect the origin and the edge: security group limited to the CloudFront origin-facing prefix list (defined in `aws-backend-host`), secret header, and a rate-based WAF rule that answers 429 when the plan includes WAF.
- Enable CloudFront standard logging to a private S3 bucket with 90-day expiry; document local analysis with DuckDB (Athena optional) for visits, campaign tags (`?ref=`), searches and wakes.
- Add a deploy step for the SPA (sync to S3, invalidate `index.html`) and an optional section for a custom domain (ACM certificate in us-east-1, DNS alias), explicitly not required.

## Capabilities

### New Capabilities
- `static-site-hosting`: S3 + CloudFront delivery of the SPA, routing of API paths, cache and timeout behavior.
- `edge-protection`: origin lock-down, rate limiting at the edge and security headers.
- `visit-statistics`: access logging, retention, privacy handling and the queries that answer visit/campaign questions.

### Modified Capabilities
<!-- none -->

## Impact

- New infrastructure code (distribution, buckets, optional WAF web ACL) and a frontend deploy script.
- Depends on: `aws-backend-host` (origin address, secret, prefix-list rule), `aws-control-plane` (Function URL), `static-client-frontend` work in `storefront-search-ui`/`service-availability-gate` (build output with hashed assets), `api-versioned-search-path` (`/api/v1` prefix makes path routing possible without URL rewriting).
- Cost assumptions to verify: CloudFront free plan, S3 storage in cents, logs storage in cents, WAF possibly not included (if not, the app-level limits from `demo-cost-controls` are the only rate limiting).
