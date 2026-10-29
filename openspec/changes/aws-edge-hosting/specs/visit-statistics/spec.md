## ADDED Requirements

### Requirement: Access logging
The system SHALL write CloudFront standard access logs for all behaviors to a private S3 bucket, and SHALL expire log objects automatically after 90 days.

#### Scenario: Request logged
- **WHEN** a visitor loads the page
- **THEN** a log record with timestamp, path, query string, status and user agent is stored in the log bucket

#### Scenario: Old logs
- **WHEN** a log object is older than 90 days
- **THEN** it is removed by the lifecycle rule

### Requirement: Campaign tags without extra requests
Visits from a link containing `?ref=<tag>` SHALL be attributable to the tag from the logged query string of the page request, without any additional request from the SPA.

#### Scenario: Tagged link
- **WHEN** a visitor opens `https://<site>/?ref=acme`
- **THEN** the log record of the page request contains `ref=acme`

### Requirement: Answerable questions
The documentation SHALL show, with runnable queries over the logs, how to obtain: visits per day, visits per campaign tag, visits per country, searches per day by status, and wake calls per day, distinguishing likely bots by user agent.

#### Scenario: Weekly review
- **WHEN** the owner runs the documented queries on a week of logs
- **THEN** they obtain the numbers listed above

### Requirement: Zero service cost for analysis
The documented analysis SHALL work with a local tool on downloaded log files, with an optional managed-query variant clearly marked as optional.

#### Scenario: Local analysis
- **WHEN** the owner downloads the log files and runs the local queries
- **THEN** no AWS analytics service is required

### Requirement: Privacy handling
The log bucket SHALL be private, and the repository documentation SHALL state that client IP addresses are logged, for what purpose and for how long, and analysis outputs SHALL be aggregated without raw IP addresses.

#### Scenario: Documentation
- **WHEN** a reader opens the README privacy section
- **THEN** it states what is logged, the purpose and the 90-day retention
