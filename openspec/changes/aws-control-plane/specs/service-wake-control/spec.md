## ADDED Requirements

### Requirement: Wake endpoint
The system SHALL expose `GET /api/v1/control/up`, which starts the backend instance when it is stopped and answers HTTP 200 with a JSON body `{"state":"UP"}` or `{"state":"STARTING"}`.

#### Scenario: Instance stopped
- **WHEN** the endpoint is called while the instance is stopped
- **THEN** the instance is started and the response is `{"state":"STARTING"}`

#### Scenario: Instance running and ready
- **WHEN** the endpoint is called while the instance is running and `GET /api/v1/health` on the origin answers 200 within 2 seconds
- **THEN** the response is `{"state":"UP"}`

#### Scenario: Instance running but not ready
- **WHEN** the instance is running and the health probe answers non-200, times out or is refused
- **THEN** the response is `{"state":"STARTING"}`

### Requirement: Idempotent and repeatable
Repeated calls SHALL be safe: calling the endpoint while the instance is pending, running or already being started MUST NOT cause an error, a second start or any other side effect besides a wake event record.

#### Scenario: Concurrent visitors
- **WHEN** several visitors call the endpoint at the same time while the instance is stopped
- **THEN** the instance is started once and every response is `{"state":"STARTING"}`

#### Scenario: Instance stopping
- **WHEN** the endpoint is called while the instance is stopping
- **THEN** the response is `{"state":"STARTING"}` and a later call, once the instance is stopped, starts it

### Requirement: Always HTTP 200 with a state
The endpoint SHALL answer HTTP 200 with a valid state for every call, including when an AWS API call fails; failures SHALL be logged and reported as `STARTING`.

#### Scenario: Start request fails
- **WHEN** the start request fails because of an AWS error
- **THEN** the response is HTTP 200 with `{"state":"STARTING"}` and the error is logged

### Requirement: Not cached
Responses of the endpoint SHALL NOT be cached by CloudFront or by browsers.

#### Scenario: Two calls in a row
- **WHEN** the endpoint is called twice and the instance state changes between the calls
- **THEN** the second response reflects the new state

### Requirement: Restricted invocation
The Lambda Function URL SHALL be invocable only through the project's CloudFront distribution; direct calls to the Function URL SHALL be rejected, and the Lambda SHALL have permissions limited to this instance, its origin-secret parameter and the wake-events table.

#### Scenario: Direct call
- **WHEN** a client calls the Function URL directly without CloudFront
- **THEN** the call is rejected with an authorization error and the instance is not started
