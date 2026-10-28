# conversation-memory Specification

## Purpose
TBD - client-owned conversation token, per-token history in query analysis, reset, isolation and expiry.

## Requirements

### Requirement: Client-owned conversation token
The client SHALL own the conversation: it SHALL generate an opaque conversation token, send it as `conversationId` with every search request of that conversation, and start a new conversation by sending a new token. The system SHALL treat the token as an opaque key, SHALL NOT issue, register or authenticate tokens, and SHALL start an empty conversation for a token it has not seen before.

#### Scenario: Unknown token starts a new conversation
- **WHEN** a client sends `message=warm winter jacket&conversationId=3f2b9c1e-7a4d-4e8f-9b21-5c6d7e8f9a0b` and the system has no history for that token
- **THEN** the query analysis receives only the system prompt and the message `warm winter jacket`

#### Scenario: UUID token is accepted
- **WHEN** a client sends a `crypto.randomUUID()` value such as `3f2b9c1e-7a4d-4e8f-9b21-5c6d7e8f9a0b` as `conversationId`
- **THEN** the request passes input validation and the value is used unchanged as the memory key

### Requirement: Conversation history in query analysis
For each search request the query-analysis LLM call SHALL receive the stored history of the request's conversation followed by the new customer message, and after the call the system SHALL append the customer message and the query-analysis result to that conversation. The product-filtering call SHALL NOT read or write the conversation history.

#### Scenario: Refinement uses the previous turn
- **WHEN** a client sends `warm winter jacket` and then `same but in navy` with the same `conversationId`
- **THEN** the query-analysis request of the second search contains the first message and its analysis result before `same but in navy`

#### Scenario: Product filtering is stateless
- **WHEN** a client sends the second search of a conversation
- **THEN** the product-filtering LLM request contains no messages from earlier turns

#### Scenario: Query analysis fails
- **WHEN** the query-analysis call fails or exhausts its schema-validation retries without a requested reset
- **THEN** the conversation history remains unchanged

#### Scenario: Overlapping requests
- **WHEN** requests for the same conversation overlap, including a reset
- **THEN** their searches and resets are processed serially without losing a completed turn or restoring history cleared by a later reset

### Requirement: Rejected turns are remembered
The system SHALL record a turn whose query analysis returned `allowed = false` in the same way as an allowed turn, so that a following request in the conversation is analysed with that context.

#### Scenario: Suggestion after rejection
- **WHEN** a client sends `a winter jacket for summer`, receives HTTP 422, and then sends a suggested prompt with the same `conversationId`
- **THEN** the query-analysis request of the second search contains the rejected message and its analysis result

### Requirement: History window
The system SHALL keep at most the 20 most recent messages (10 turns) per conversation and SHALL drop the oldest messages first; the system prompt SHALL NOT count toward the window.

#### Scenario: Long conversation
- **WHEN** a conversation has 11 completed turns and the client sends the 12th search
- **THEN** the query-analysis request contains the messages of turns 2 to 11 and not those of turn 1

### Requirement: Conversation isolation
The system SHALL never include messages of one conversation in the LLM requests of another conversation.

#### Scenario: Two customers
- **WHEN** client A sends `leather jacket` with `conversationId=a` and client B then sends `same but in navy` with `conversationId=b`
- **THEN** the query-analysis request of client B contains no message from conversation `a`

### Requirement: Conversation reset
When a request carries `clearMemory=true`, the system SHALL delete the stored history of the request's own `conversationId` only, before processing the message, so the message is analysed as the first turn of a fresh conversation. The histories of all other conversations SHALL remain unchanged.

#### Scenario: Reset with the same token
- **WHEN** a client has an existing conversation `c1` and sends `leather jacket` with `conversationId=c1&clearMemory=true`
- **THEN** the query-analysis request contains no earlier messages and afterwards the history of `c1` contains only the `leather jacket` turn

#### Scenario: Reset does not touch other conversations
- **WHEN** conversations `a` and `b` both have history and a client sends a search with `conversationId=a&clearMemory=true`
- **THEN** the history of `a` is cleared and the history of `b` contains exactly the same messages as before the request

#### Scenario: Reset of an unknown token
- **WHEN** a client sends `clearMemory=true` with a `conversationId` the system has no history for
- **THEN** no other conversation is changed and the search is processed as the first turn of a new conversation

### Requirement: Conversation expiry
The system SHALL record, for each `conversationId`, the time of the last turn stored for it, and SHALL delete the conversation's history once 24 hours have passed since that last turn without a new turn being stored. Every stored turn SHALL restart the 24-hour period, so an active conversation lives as long as it is used; its size remains bounded by the history window. An expired conversation SHALL NOT be returned to the query analysis even before it has been physically removed, and a following request with the same token SHALL start a new, empty conversation. The inactivity period SHALL be configurable and default to 24 hours.

#### Scenario: Active conversation is kept
- **WHEN** a conversation's first turn was stored at 10:00 and the client sends a search with the same token every few hours until 12:00 two days later
- **THEN** every one of these searches is analysed with the conversation history (limited to the history window)

#### Scenario: Inactive conversation expires
- **WHEN** the last turn of a conversation was stored at 10:00 and the client sends the next search with the same token at 10:01 the next day
- **THEN** that search is analysed without any of the earlier messages

#### Scenario: Within the inactivity period
- **WHEN** the last turn of a conversation was stored at 10:00 and the client sends a refinement at 09:59 the next day
- **THEN** the query-analysis request contains the earlier messages of the conversation

#### Scenario: Abandoned conversation is removed
- **WHEN** no turn has been stored for a conversation for more than 24 hours
- **THEN** the periodic cleanup removes its history from memory even though no request uses its token again

### Requirement: Prototype memory lifetime
The system SHALL keep conversation history in the memory of the running backend process only. History SHALL be lost when the process restarts and SHALL NOT be shared between backend instances.

#### Scenario: Backend restart
- **WHEN** the backend restarts between two searches of the same conversation
- **THEN** the second search is analysed as the first turn of an empty conversation and succeeds
