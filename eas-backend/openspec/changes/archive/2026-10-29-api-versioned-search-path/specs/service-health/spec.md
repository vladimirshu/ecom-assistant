## ADDED Requirements

### Requirement: Readiness endpoint
The system SHALL expose `GET /api/v1/health` that responds with HTTP 200 and a JSON body with `status` `UP` when the application is ready to serve searches, and with HTTP 503 and `status` `DOWN` otherwise. The application is ready only when all startup runners have completed (including the catalog import and the price index creation) and the vector store is reachable.

#### Scenario: Ready application
- **WHEN** the application has finished startup and the vector store is reachable and a client calls `GET /api/v1/health`
- **THEN** the response is HTTP 200 with `{"status":"UP"}`

#### Scenario: Catalog import still running
- **WHEN** the web server is accepting connections but the catalog import has not finished and a client calls `GET /api/v1/health`
- **THEN** the response is HTTP 503 with `status` `DOWN`

#### Scenario: Vector store unreachable
- **WHEN** the vector store does not answer within the check timeout and a client calls `GET /api/v1/health`
- **THEN** the response is HTTP 503 with `status` `DOWN`

### Requirement: Readiness check has no downstream cost
The readiness endpoint SHALL NOT call any LLM or embedding model, SHALL NOT run a similarity search, and SHALL NOT expose component details, versions or configuration in its response.

#### Scenario: No model calls
- **WHEN** a client calls `GET /api/v1/health` repeatedly
- **THEN** no request is sent to the LLM or embedding provider

#### Scenario: No detail in the body
- **WHEN** a client calls `GET /api/v1/health`
- **THEN** the body contains only the `status` property

### Requirement: Health endpoint is unauthenticated and unrestricted
The system SHALL serve `GET /api/v1/health` without authentication and SHALL NOT subject it to any search rate or budget limit.

#### Scenario: Frequent probing
- **WHEN** a client calls `GET /api/v1/health` every 3 seconds
- **THEN** every call is answered according to readiness and none is rejected for rate reasons
