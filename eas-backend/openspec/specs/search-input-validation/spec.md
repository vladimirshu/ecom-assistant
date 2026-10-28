# search-input-validation Specification

## Purpose
TBD - created by archiving change simplify-input-validation. Update Purpose after archive.
## Requirements
### Requirement: Search message normalization
The system SHALL normalize the search message with Unicode NFKC, collapse consecutive whitespace into a single space and trim it before validating it and before passing it to any downstream component.

#### Scenario: Full-width characters are normalized
- **WHEN** a client sends the message `ｊａｃｋｅｔ  under ２００`
- **THEN** the message used for validation and search is `jacket under 200`

#### Scenario: Surrounding and repeated whitespace is collapsed
- **WHEN** a client sends the message `  blue    jacket `
- **THEN** the message used for validation and search is `blue jacket`

### Requirement: Search message whitelist
The system SHALL accept a search message only if, after normalization, it is 3 to 500 characters long and consists solely of letters and combining marks of any script, digits, whitespace and the characters `' ’ " - _ . , ! ? ( ) € $ £ ¥ % / & +`.

#### Scenario: Apostrophe is accepted
- **WHEN** a client sends the message `I'd like a waterproof jacket`
- **THEN** the request passes input validation

#### Scenario: Former SQL keywords are accepted
- **WHEN** a client sends the message `alternative to an executive coat with dropped shoulders`
- **THEN** the request passes input validation

#### Scenario: Non-Latin script is accepted
- **WHEN** a client sends the message `зимняя куртка до 200 евро`
- **THEN** the request passes input validation

#### Scenario: Angle brackets are rejected
- **WHEN** a client sends the message `jacket <script>`
- **THEN** the system responds with HTTP 400 and a field error for `message`

#### Scenario: Too short after normalization
- **WHEN** a client sends the message `  a  `
- **THEN** the system responds with HTTP 400 and a field error for `message`

#### Scenario: Too long
- **WHEN** a client sends a message of 501 characters
- **THEN** the system responds with HTTP 400 and a field error for `message`

### Requirement: Search message is not altered beyond normalization
The system SHALL NOT HTML-encode, strip or otherwise rewrite an accepted search message beyond the normalization requirement.

#### Scenario: Ampersand is preserved
- **WHEN** a client sends the message `M&S style rain jacket`
- **THEN** the text passed downstream is exactly `M&S style rain jacket`

### Requirement: Conversation id validation
The system SHALL require a conversation id on every search request and SHALL accept it only if it is 1 to 50 characters long and consists solely of ASCII letters, digits, `-` and `_`, and SHALL use it unchanged. The system SHALL NOT substitute a default conversation id.

#### Scenario: Invalid conversation id
- **WHEN** a client sends the conversation id `abc;drop`
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`

#### Scenario: Valid conversation id
- **WHEN** a client sends the conversation id `session_42-a`
- **THEN** the request passes input validation and `session_42-a` is used as the conversation id

#### Scenario: Missing conversation id
- **WHEN** a client sends `GET /search?message=rain jacket` without a `conversationId` parameter
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`, and no LLM or vector store call is made

#### Scenario: Blank conversation id
- **WHEN** a client sends an empty `conversationId` parameter
- **THEN** the system responds with HTTP 400 and a field error for `conversationId`

### Requirement: Validation error response
The system SHALL respond to an input validation failure with HTTP 400 and a JSON object mapping each invalid field name to its error message, without calling any LLM or the vector store.

#### Scenario: No downstream calls on invalid input
- **WHEN** a client sends an invalid message
- **THEN** the response is HTTP 400 and no LLM or vector store call is made

### Requirement: LLM-produced text is plain text
The system SHALL reject LLM-produced text that is blank, exceeds its configured maximum length or contains control characters other than whitespace, and SHALL report such a rejection as HTTP 500 with error `Illegal assistant response`, never as an input validation error.

#### Scenario: Control character in LLM output
- **WHEN** the LLM returns text containing the character U+0007
- **THEN** the system responds with HTTP 500 and error `Illegal assistant response`

