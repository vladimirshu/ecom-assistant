## ADDED Requirements

### Requirement: Origin reachable only through CloudFront
The backend host SHALL accept inbound traffic only on TCP port 8080 from the CloudFront origin-facing managed prefix list, SHALL NOT open port 22 or any other inbound port, and SHALL use an Elastic IP so that its address is stable across stop and start.

#### Scenario: Direct request from the internet
- **WHEN** a client outside CloudFront connects to the Elastic IP on port 8080
- **THEN** the connection is not accepted

#### Scenario: Address after restart
- **WHEN** the instance is stopped and started again
- **THEN** it has the same public IPv4 address

### Requirement: Origin verification header
When `ECOM_ORIGIN_VERIFY_SECRET` is set, the backend SHALL answer HTTP 403 with an empty body to every request whose `X-Origin-Verify` header is missing or differs from the secret, comparing in constant time. When the variable is not set, no verification SHALL be applied.

#### Scenario: Request through CloudFront
- **WHEN** a request arrives with the correct `X-Origin-Verify` header
- **THEN** it is processed normally

#### Scenario: Request from another CloudFront distribution
- **WHEN** a request reaches the origin without the header or with a wrong value
- **THEN** the response is 403 and the search pipeline is not invoked

#### Scenario: Local development
- **WHEN** the backend runs without `ECOM_ORIGIN_VERIFY_SECRET`
- **THEN** requests are processed without the header

### Requirement: Secrets outside the image and the repository
The system SHALL store the OpenAI API key, the Qdrant Cloud URL and API key, and the origin-verification secret as SSM Parameter Store SecureString parameters, SHALL grant the instance role read access to only that parameter path, and SHALL NOT include any of them in the container image, instance user data, repository files or application logs.

#### Scenario: Start with secrets
- **WHEN** the backend container starts on the instance
- **THEN** it receives the secret values as environment variables read from Parameter Store at start

#### Scenario: Log inspection
- **WHEN** the application logs of a normal search session are searched for the key values
- **THEN** no secret value appears

### Requirement: Unattended start and supervision
The host SHALL start the backend container automatically on every boot and SHALL restart it when it exits abnormally, without manual action.

#### Scenario: Start after the instance is started
- **WHEN** the instance transitions from stopped to running
- **THEN** the container is started and `GET /api/v1/health` returns 200 after startup completes

#### Scenario: Crash
- **WHEN** the backend process exits with an error
- **THEN** it is restarted automatically

### Requirement: Production logging policy
With the `aws` profile active, the backend SHALL log at INFO, SHALL NOT log customer prompts, LLM requests/responses or SQL, and the container log driver SHALL cap total log size so logs cannot exhaust the volume.

#### Scenario: Search under the aws profile
- **WHEN** a search is processed with the `aws` profile
- **THEN** the application log contains no message text or LLM payload

#### Scenario: Long uptime
- **WHEN** the instance runs for weeks under traffic
- **THEN** container logs occupy no more than the configured cap

### Requirement: Memory-bounded runtime
The container SHALL run with a capped JVM heap sized for a 1 GB instance and the instance SHALL have swap configured, so that a normal search workload does not cause the operating system to kill the process.

#### Scenario: Search load
- **WHEN** a sequence of searches runs for ten minutes
- **THEN** the process stays running and resident memory stays below the instance memory

### Requirement: Administration without SSH
The instance SHALL be administrable through SSM Session Manager and SSM Run Command, and IMDSv2 SHALL be required.

#### Scenario: Deploying a new image
- **WHEN** the operator runs the documented deploy command after pushing an image tag
- **THEN** the instance pulls that tag and restarts the service without any inbound connection to the instance
