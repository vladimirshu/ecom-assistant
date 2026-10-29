## ADDED Requirements

### Requirement: Environment-driven Qdrant connection
The system SHALL read the Qdrant host, port, TLS flag and API key from the environment variables `QDRANT_HOST`, `QDRANT_PORT`, `QDRANT_USE_TLS` and `QDRANT_API_KEY`, and SHALL default to host `localhost`, port `6334`, TLS off and no API key when a variable is not set.

#### Scenario: Local defaults
- **WHEN** the application starts with none of the four variables set
- **THEN** it connects to `localhost:6334` without TLS and without an API key

#### Scenario: Cloud configuration
- **WHEN** the application starts with `QDRANT_HOST=abc.eu-central.aws.cloud.qdrant.io`, `QDRANT_PORT=6334`, `QDRANT_USE_TLS=true` and `QDRANT_API_KEY=secret`
- **THEN** it connects to that host over TLS and sends the API key with every request

#### Scenario: Empty key is no key
- **WHEN** `QDRANT_API_KEY` is set to an empty value
- **THEN** no API key is sent

### Requirement: One connection configuration for every consumer
The vector store, the catalog import, the price payload index initializer and the recommend retriever SHALL use the same Qdrant connection configuration, so that TLS and the API key apply to every Qdrant call.

#### Scenario: Retriever uses TLS and key
- **WHEN** the application is configured for a TLS cluster with an API key and a search is performed
- **THEN** the recommend retrieval request is sent over TLS with the API key

#### Scenario: Import and index creation use TLS and key
- **WHEN** the application starts against a TLS cluster with an API key
- **THEN** the collection check, the point count, the upsert and the price index creation all succeed

### Requirement: The API key is never exposed
The system SHALL NOT write the Qdrant API key to logs, to exception messages or to any HTTP response, and SHALL log at startup only the host, the port, whether TLS is enabled and whether an API key is present.

#### Scenario: Startup log
- **WHEN** the application starts with an API key configured
- **THEN** the log contains the host, port, TLS flag and `apiKey=present`, and does not contain the key value

#### Scenario: Connection failure message
- **WHEN** the connection fails because the key is rejected
- **THEN** the error message and the log do not contain the key value

### Requirement: Clear startup failure on a bad connection
The system SHALL fail startup, or report not ready on the readiness endpoint, with an error that names the configured host and states whether the failure is authentication (key rejected) or connectivity (host, port or TLS), when the Qdrant cluster cannot be used.

#### Scenario: Wrong API key
- **WHEN** the application starts with an API key the cluster rejects
- **THEN** the error names the host and states that authentication failed

#### Scenario: Unreachable host
- **WHEN** the application starts with a host or port that does not answer
- **THEN** the error names the host and states that the cluster is unreachable, and `GET /api/v1/health` does not return 200
