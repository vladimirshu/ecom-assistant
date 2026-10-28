## Context

The storefront (`../eas-frontend`, change `storefront-search-ui`) is a conversational search: the first message describes what the customer wants, follow-ups refine it ("same but in navy", "cheaper"). The backend already has the pieces for memory:

- `AiConfig` defines `InMemoryChatMemoryRepository` + `MessageWindowChatMemory` (`MAX_MESSAGES = 20`).
- `queryAnalysisChatClient` has `MessageChatMemoryAdvisor`; `QueryAnalyzer` passes `ChatMemory.CONVERSATION_ID`. The analysis prompt already asks for a `searchQuery` that summarizes the conversation's intent.
- `productSearchChatClient` (product filtering, call #2) deliberately has no memory advisor.
- `ProductsController` clears the conversation on `clearMemory=true`.
- `ProductSearchRequestDto.conversationId` defaults to `"1"`, so a client that forgets the id shares one global conversation with every other such client.

What is missing is an explicit contract between the client and the backend, and the removal of the shared default.

## Goals / Non-Goals

**Goals:**
- One simple, documented model: the browser holds a random token in React memory and sends it as `conversationId`; the backend keys its in-process history by it.
- No accidental sharing of history between customers, including through `clearMemory`.
- Bounded memory: a conversation is removed 24 hours after its last turn; an active one is bounded by the 20-message window.
- Refinements and follow-ups after a rejection work within a tab session.

**Non-Goals:**
- Persistent memory (JDBC/Redis repository), survival across backend restarts, multi-instance deployments.
- Server-issued or signed session tokens, cookies, authentication, or protection against a client guessing another client's token.
- An absolute maximum lifetime per conversation, a cap on the number of conversations, or expiry notifications to the client.
- Restoring a conversation after a browser reload or sharing it between tabs.
- Summarizing long histories; the window simply drops old messages.

## Decisions

**1. Client-generated token, kept in React state.**
The storefront creates the token with `crypto.randomUUID()` when the search session hook is initialised and again on "New search", holds it in a `useRef`/state of `useSearchSession`, and sends it as `conversationId`. A reload or a new tab gives a new token, so the visible trail (already in memory only) and the server history start fresh together. This removes the `sessionStorage` mismatch noted in `storefront-search-ui` ("trail lost on reload while server memory persists").
- Alternatives: `sessionStorage` (survives reload, but the trail does not, so the customer refines a conversation they cannot see); a server-issued id in an `HttpOnly` cookie (needs a session endpoint and cookie handling in the proxy, more than the prototype needs); a JWT (signing, rotation and validation for no prototype benefit).
- A UUID is 36 characters of hex and `-`, so it fits the existing `[A-Za-z0-9_-]{1,50}` rule without a validation change. 122 random bits make collisions and guessing irrelevant for the prototype.

**2. The backend treats the id as an opaque key.**
No registration endpoint and no "unknown id" error: `MessageWindowChatMemory.get` returns an empty list for a new key, which is exactly "start a new conversation". This keeps `/search` the only endpoint.

**3. Remove the `"1"` default; require the id.**
Drop the field initializer in `ProductSearchRequestDto`; the existing `@NotBlank` then produces the 400 field error for a missing or empty parameter through the existing `GlobalExceptionHandler`. A silent shared default is the only real correctness hazard of the current setup; failing loudly is cheaper than debugging cross-talk.

**4. Memory only around the query-analysis call.**
Unchanged from the current wiring and now made a requirement: call #1 sees the history and is the one place where the conversation is condensed into `searchQuery`, negative examples and the price constraint. Call #2 works from that condensed analysis and the retrieved candidates; giving it the history would duplicate tokens and invite it to re-interpret the request.
- A dedicated memory advisor loads the history and stores the customer message and query-analysis JSON together only after a schema-valid response has been converted successfully. Failed calls and exhausted schema retries do not add a turn; a requested reset still takes effect before the call. The advisor is outside `StructuredOutputValidationAdvisor` retries and checks the final response because that advisor can return an invalid response after retries are exhausted.
- Rejected turns are stored before `QueryAnalysisValidator` throws `GuardrailRejectedException`, so a follow-up after a 422 has context. Requests using the same token, including a reset and its search, are serialized; requests using different tokens can proceed independently. The per-token locks are removed when no requests use or await them.

**5. Window of 20 messages, in process memory.**
Use the expiring in-memory repository and `MessageWindowChatMemory` with 20 messages (10 turns), which comfortably covers a shopping refinement session and bounds prompt size per conversation. `MAX_MESSAGES` stays a constant in `AiConfig`; making it a property is unnecessary until someone needs to tune it.

**6. Keep `clearMemory`, scoped to the request's token.**
With a fresh token per conversation the storefront does not need it, but it costs nothing, is already covered by tests, and lets API clients (curl, tests) reuse a fixed id. The storefront may keep sending `clearMemory=true` on the first turn as in `storefront-search-ui`; it is a no-op for a new token.
- Scope: the controller calls `chatMemory.clear(conversationId)` with the validated id of the same request, which reaches `ChatMemoryRepository.deleteByConversationId(id)` — a single-key removal. There is deliberately no "clear all" path reachable from the API, and the repository implementation must not use prefix or pattern matching on keys. An integration test with two populated conversations pins this down, so a later repository swap cannot regress it unnoticed.

**7. 24-hour sliding (inactivity) expiry, in a custom repository.**
`memory/ExpiringInMemoryChatMemoryRepository implements ChatMemoryRepository` replaces `InMemoryChatMemoryRepository`. It holds `ConcurrentHashMap<String, StoredConversation(Instant lastActivity, List<Message> messages)>` and an injected `java.time.Clock`.
- *Activity* is a stored turn: `saveAll` (called by `MessageWindowChatMemory.add` after each query-analysis call) sets `lastActivity = clock.instant()` inside `compute`. Reads do not extend the period; every search writes a turn anyway, so the distinction only matters for internal reads.
- *Sliding, not absolute*: an active conversation never expires, which is fine because `MessageWindowChatMemory` keeps it at 20 messages, so neither prompt size nor memory per conversation grows with its age. Only conversations nobody returns to are removed.
- *Lazy check*: `findByConversationId` and `findConversationIds` treat entries with `lastActivity + inactivityTimeout <= now` as absent (and remove them), so correctness never depends on the cleanup timing.
- *Periodic cleanup*: a `@Scheduled(fixedDelayString = "${ecom.memory.cleanup-interval:1h}")` method removes expired entries, so abandoned conversations free memory at most one interval after they expire. The interval is only the sweep cadence; it is not a timeout. Scheduling is enabled with `@EnableScheduling` on a small config class.
- *Reset*: `deleteByConversationId` removes the entry; the next turn creates a new one.
- Configuration: `EcomMemoryProperties` bound to `ecom.memory.inactivity-timeout` (`Duration`, default `24h`) and `ecom.memory.cleanup-interval` (default `1h`).
- Alternatives: absolute expiry from creation — gives a hard retention bound but cuts off active sessions, and the bound is not needed since the window already limits size; Caffeine `expireAfterWrite` — the same semantics, but a new dependency for ~60 lines of code; `JdbcChatMemoryRepository` with a cleanup job — persistence is a non-goal; checking only lazily without a sweep — abandoned conversations would stay in the heap forever.

## Risks / Trade-offs

- [Many conversations active within 24 hours] → Each conversation is capped at 20 short messages and an abandoned one is removed within 24 hours plus one cleanup interval, so demo traffic stays at a few MB. Before any public deployment, add a cap on conversations or move to a persistent store (`JdbcChatMemoryRepository` + cleanup job); this is a bean swap in `AiConfig`.
- [A long-lived token keeps its history indefinitely while used] → Only the last 20 messages are kept, the token lives in one browser tab's memory, and history holds no personal data. Add an absolute lifetime if retention rules ever require one.
- [A customer returns to an open tab after more than 24 hours] → The next search starts a new conversation silently; the visible trail may still show old turns. Acceptable for the prototype; no client notification is planned.
- [Clock jumps on the host] → Using an injected `Clock` (system UTC) keeps the logic testable; wall-clock jumps only shift expiry by the jump, acceptable for a prototype.
- [History lost on backend restart] → The next search simply starts a new conversation; the customer may need to restate the request. Acceptable for a prototype.
- [Anyone who knows a token can read the conversation's effect / pollute it] → Tokens are random UUIDs never shown in the UI or logged by the frontend; there is no personal data in the history. Not a concern for the prototype.
- [Rejected or off-topic turns stay in the window and may bias later analysis] → The analysis prompt already evaluates the latest message; the customer can start over with "New search". Revisit if tests show bias.
- [Breaking change for clients that omit `conversationId`] → The only clients are tests and the storefront, which always sends it.

## Migration Plan

1. Deploy the backend; existing in-memory history is lost on restart anyway. The inactivity timeout defaults to 24 hours; no configuration is required.
2. Update `storefront-search-ui` (frontend change) to the in-memory token before or together with its implementation.
3. Rollback: restore the `"1"` default and the `InMemoryChatMemoryRepository` bean; no data is involved.

## Open Questions

- None blocking. Whether the storefront should keep sending `clearMemory=true` on the first turn is left to the frontend change; the backend behaves identically either way.
