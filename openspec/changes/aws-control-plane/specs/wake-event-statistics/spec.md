## ADDED Requirements

### Requirement: Wake event record
For every call to `/api/v1/control/up`, the system SHALL write one event to the `wake-events` DynamoDB table containing the timestamp, the day, the state returned, the instance state before the call, the viewer country when provided by CloudFront, the `ref` value when provided, and a coarse user-agent class (browser, bot, other). A failed write SHALL NOT change the response of the endpoint.

#### Scenario: Wake from a tagged link
- **WHEN** a visitor opens the page with `?ref=acme`, the SPA forwards it and the instance is stopped
- **THEN** an event with `ref` = `acme`, state `STARTING` and instance state `stopped` is stored

#### Scenario: Write failure
- **WHEN** the table write fails
- **THEN** the endpoint still answers with the correct state

### Requirement: Free-tier sizing and expiry
The table SHALL use provisioned capacity within the provider's always-free allowance (not on-demand capacity) and SHALL expire events automatically after about 180 days.

#### Scenario: Old events
- **WHEN** an event is older than the retention period
- **THEN** it is removed automatically by TTL

### Requirement: No personal identifiers
Wake events SHALL NOT store IP addresses or full user-agent strings.

#### Scenario: Stored attributes
- **WHEN** an event is read from the table
- **THEN** it contains no IP address and no raw user-agent string
