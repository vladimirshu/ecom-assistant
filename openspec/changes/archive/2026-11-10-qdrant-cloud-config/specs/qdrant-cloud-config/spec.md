## ADDED Requirements

### Requirement: Environment-driven Qdrant connection
The backend SHALL resolve its Qdrant connection settings (host, port, TLS, and API key) from environment variables, so it can target either a local Qdrant instance or a remote Qdrant Cloud cluster without code changes.

#### Scenario: Local defaults
- **WHEN** none of `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS`, or `QDRANT_API_KEY` are set
- **THEN** the backend connects to `localhost:6334` over plain gRPC with no TLS and no API key

#### Scenario: Qdrant Cloud over TLS with API key
- **WHEN** `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS=true`, and `QDRANT_API_KEY` are set to a Qdrant Cloud endpoint
- **THEN** the backend connects to that host and port over TLS and authenticates with the given API key

### Requirement: API key and TLS are optional
The backend SHALL treat TLS and API-key authentication as optional, enabled only when the corresponding variable is provided, so a local Qdrant instance without auth keeps working.

#### Scenario: TLS disabled when not requested
- **WHEN** `QDRANT_USE_TLS` is unset or `false`
- **THEN** the Qdrant client does not use TLS

#### Scenario: No API key sent when unset
- **WHEN** `QDRANT_API_KEY` is unset or empty
- **THEN** the Qdrant client sends no API key
