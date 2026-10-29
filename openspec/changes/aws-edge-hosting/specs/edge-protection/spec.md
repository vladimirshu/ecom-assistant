## ADDED Requirements

### Requirement: Origin custom header
CloudFront SHALL add the secret header `X-Origin-Verify` to every request sent to the backend origin, using the same secret the backend verifies.

#### Scenario: Request via CloudFront
- **WHEN** a search request passes through the distribution
- **THEN** the backend receives the `X-Origin-Verify` header with the configured secret

### Requirement: Lambda reachable only via CloudFront
The control Lambda Function URL SHALL be invocable only through the distribution's origin access control.

#### Scenario: Direct invocation
- **WHEN** the Function URL is called without CloudFront's signature
- **THEN** the call is rejected

### Requirement: Edge rate limit for search
Where the CloudFront plan includes WAF, the system SHALL apply a rate-based rule per client IP to `/api/v1/search` and answer blocked requests with HTTP 429 (not 403); where it does not, the system SHALL document that application-level limits are the only rate limiting.

#### Scenario: Burst from one client
- **WHEN** one client exceeds the configured rate on `/api/v1/search`
- **THEN** subsequent requests receive HTTP 429 until the rate falls

#### Scenario: Health and control paths
- **WHEN** a client calls `/api/v1/health` or `/api/v1/control/up`
- **THEN** the search rate rule does not apply to those paths

### Requirement: Security headers
Responses of the default behavior SHALL include HSTS, `X-Content-Type-Options: nosniff`, a referrer policy, a Content-Security-Policy restricting sources to the site itself, and a rule forbidding framing.

#### Scenario: Page response
- **WHEN** `index.html` is served
- **THEN** the response carries the security headers

### Requirement: Secret handling
The origin-verification secret SHALL be generated once, stored only in SSM Parameter Store and in the deployed distribution configuration, and SHALL NOT appear in the repository.

#### Scenario: Repository scan
- **WHEN** the repository is searched for the secret value
- **THEN** it is not found
