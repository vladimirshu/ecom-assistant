# search-rate-limiting Specification

## Purpose
TBD - created by syncing change demo-cost-controls. Update Purpose after archive.

## Requirements
### Requirement: Per-client search rate limit
The system SHALL allow each client at most 5 accepted searches per minute and 20 accepted searches per hour on `GET /api/v1/search`, where both limits are configurable, and SHALL respond to a search above either limit with HTTP 429, a `Retry-After` header with the number of seconds until a search is allowed again, and a JSON body whose `code` is `RATE_LIMITED`.

#### Scenario: Minute limit
- **WHEN** one client sends 6 valid searches within one minute
- **THEN** the first 5 are processed and the 6th is answered with HTTP 429, code `RATE_LIMITED` and a `Retry-After` of at most 60 seconds

#### Scenario: Hour limit
- **WHEN** one client has had 20 searches accepted within the last hour, spread so that no minute limit applies, and sends another valid search
- **THEN** the response is HTTP 429 with code `RATE_LIMITED`

#### Scenario: Limit recovers
- **WHEN** a client was rejected by the minute limit and sends a valid search after the `Retry-After` period
- **THEN** the search is processed

#### Scenario: Other clients are unaffected
- **WHEN** client A has exhausted its limits and client B sends a valid search
- **THEN** client B's search is processed

### Requirement: Rejected and invalid requests are free
The system SHALL make no LLM, embedding or vector store call for a request rejected with HTTP 429, and SHALL NOT count a request that fails input validation (HTTP 400) against any limit.

#### Scenario: No downstream call on 429
- **WHEN** a search is rejected with HTTP 429
- **THEN** no request is sent to the LLM provider, the embedding model or the vector store

#### Scenario: Invalid requests consume no quota
- **WHEN** a client sends 10 searches that fail validation and then 5 valid searches within one minute
- **THEN** all 5 valid searches are processed

#### Scenario: Guardrail rejection counts
- **WHEN** a valid search is rejected by the query analysis with HTTP 422
- **THEN** it counts as one accepted search against the client's limits and the daily budget

### Requirement: Client identification behind the CDN
The system SHALL identify a client by the viewer IP address that the CDN appended as the last `X-Forwarded-For` entry (counting from the right by the configured number of trusted proxy hops, default 1), SHALL ignore earlier entries, SHALL use the remote address when the header is absent, and SHALL identify an IPv6 client by its /64 prefix.

#### Scenario: Forged header entries are ignored
- **WHEN** a client sends `X-Forwarded-For: 1.2.3.4` and the CDN forwards `X-Forwarded-For: 1.2.3.4, 203.0.113.9`
- **THEN** the client is identified as `203.0.113.9`

#### Scenario: No header
- **WHEN** a request has no `X-Forwarded-For` header
- **THEN** the client is identified by the connection's remote address

#### Scenario: IPv6 clients share a prefix
- **WHEN** requests arrive from `2001:db8:1:2::1` and `2001:db8:1:2:ffff::7`
- **THEN** both are counted as one client

### Requirement: Health and non-search endpoints are not limited
The system SHALL NOT apply the search limits to `GET /api/v1/health` or to any endpoint other than `GET /api/v1/search`.

#### Scenario: Health during a limit
- **WHEN** a client has exhausted its search limits and calls `GET /api/v1/health`
- **THEN** the response is not 429

### Requirement: Limiter memory is bounded
The system SHALL discard the limiter state of a client that has been idle for more than one hour, so the state does not grow without bound.

#### Scenario: Idle client is forgotten
- **WHEN** a client has sent no search for more than one hour and the cleanup has run
- **THEN** no state is held for that client and its next search is evaluated with full buckets
