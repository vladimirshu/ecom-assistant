## MODIFIED Requirements

### Requirement: Conversation id validation
The system SHALL require a conversation id on every search request and SHALL accept it only if it is 1 to 50 characters long and consists solely of ASCII letters, digits, `-` and `_`, and SHALL use it unchanged. The system SHALL NOT substitute a default conversation id.

#### Scenario: Invalid conversation id
- **WHEN** a client sends the conversation id `abc;drop`
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`

#### Scenario: Valid conversation id
- **WHEN** a client sends the conversation id `session_42-a`
- **THEN** the request passes input validation and `session_42-a` is used as the conversation id

#### Scenario: Missing conversation id
- **WHEN** a client sends `GET /api/v1/search?message=rain jacket` without a `conversationId` parameter
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`, and no LLM or vector store call is made

#### Scenario: Blank conversation id
- **WHEN** a client sends an empty `conversationId` parameter
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`
