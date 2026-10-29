# llm-cost-budget Specification

## Purpose
TBD - created by syncing change demo-cost-controls. Update Purpose after archive.

## Requirements
### Requirement: Global daily search budget
The system SHALL accept at most 300 searches per UTC day across all clients, where the number is configurable, and SHALL respond to a search above the budget with HTTP 429, a `Retry-After` header with the number of seconds until the next UTC midnight, and a JSON body whose `code` is `DAILY_BUDGET_EXHAUSTED`. The budget SHALL reset at the first search of each UTC day.

#### Scenario: Budget exhausted
- **WHEN** 300 searches have been accepted today and a client with free quota sends another valid search
- **THEN** the response is HTTP 429 with code `DAILY_BUDGET_EXHAUSTED` and no LLM call is made

#### Scenario: Budget resets at UTC midnight
- **WHEN** the budget was exhausted yesterday and a valid search arrives after 00:00 UTC
- **THEN** the search is processed

#### Scenario: Client code takes precedence
- **WHEN** a client over its own limit sends a search while the daily budget is also exhausted
- **THEN** the response has code `RATE_LIMITED`

#### Scenario: Budget rejection returns the client token
- **WHEN** a client with free quota is rejected because the daily budget is exhausted
- **THEN** the rejected request does not reduce that client's remaining quota

### Requirement: Budget counts accepted searches only
The system SHALL count against the daily budget each search that passed input validation and the client limits, and SHALL NOT count requests rejected by validation (HTTP 400), by the client limits (HTTP 429), or health checks.

#### Scenario: Rejected requests are not counted
- **WHEN** 50 requests are rejected with HTTP 400 or `RATE_LIMITED` and 10 are accepted
- **THEN** the daily budget has 10 searches counted

### Requirement: Budget state is in process memory
The system SHALL keep the daily budget counter in process memory, so the counter restarts at zero when the application restarts, and this limitation SHALL be documented together with the external hard spending cap.

#### Scenario: Restart resets the counter
- **WHEN** the application restarts after 200 accepted searches on the same UTC day
- **THEN** the counter starts at zero

### Requirement: Bounded LLM cost per accepted search
The system SHALL allow at most one schema-validation retry per LLM call (query analysis and product filtering) and SHALL set a configurable maximum output token limit on both calls (default 500 for the query analysis and 300 for the product filtering), so an accepted search makes at most 4 LLM calls.

#### Scenario: Retry limit
- **WHEN** every query-analysis response fails schema validation
- **THEN** the system makes 2 query-analysis calls in total and then fails the request with the existing schema-failure response

#### Scenario: Output cap is sent
- **WHEN** a search is processed
- **THEN** the query-analysis request carries a maximum output token limit of 500 and the product-filtering request carries 300

#### Scenario: Typical output fits the cap
- **WHEN** the query analysis returns a verdict, query, parameters and 3 suggested prompts of typical length
- **THEN** the response is within the token limit and parses against the result schema

### Requirement: Search limits are configurable
The system SHALL read the per-minute limit, the per-hour limit, the daily budget, the trusted proxy hop count and the two output token limits from configuration properties with the defaults named in this specification.

#### Scenario: Override a limit
- **WHEN** the configuration sets the daily budget to 10
- **THEN** the eleventh search of the UTC day is answered with `DAILY_BUDGET_EXHAUSTED`
