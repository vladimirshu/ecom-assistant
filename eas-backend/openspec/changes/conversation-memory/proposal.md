## Why

Refinements such as "same but in navy" only work if the backend remembers the previous turns of the same customer. The memory plumbing exists (`MessageWindowChatMemory` behind the query-analysis call, keyed by `conversationId`), but the contract is implicit: a request without `conversationId` silently falls back to the shared id `"1"`, so unrelated customers can end up in one conversation, and nothing defines who creates the id, how long it lives or what is remembered. For the prototype we want the simplest model that works: the storefront (React) generates a random token, keeps it in application memory and sends it as `conversationId`; the backend keeps the history in process memory under that token.

## What Changes

- Define the conversation memory contract: the client owns the conversation token, the backend stores the turns per token in process memory (window of the last 20 messages), and only the query-analysis call (call #1) reads and writes the history.
- Specify what a turn contains: the customer's message and the query-analysis result, recorded for allowed and rejected requests alike, so a refinement after a 422 suggestion still has context.
- **BREAKING**: `conversationId` becomes a required request parameter; the default `"1"` is removed, and a missing or blank id is answered with HTTP 400 and a field error for `conversationId`.
- Keep `clearMemory=true` as the explicit reset of a conversation, scoped strictly to the request's own token; a token the backend has never seen starts an empty conversation.
- Expire a conversation after 24 hours without a new turn (sliding, configurable): every turn restarts the period, so active conversations live on while the 20-message window bounds their size; expired conversations are ignored on read and removed by a periodic cleanup.
- Document the prototype limits: memory is lost on backend restart and is not shared between instances.

## Capabilities

### New Capabilities
- `conversation-memory`: client-owned conversation token, per-token history window, what is remembered, per-token reset, isolation between conversations, 24-hour inactivity expiry, and prototype lifetime limits.

### Modified Capabilities
- `search-input-validation`: the conversation id is required; a missing id is rejected instead of defaulting to `"1"`.

## Impact

- Code: `dto/ProductSearchRequestDto` (remove the `"1"` default), new `memory/ExpiringInMemoryChatMemoryRepository` and `config/EcomMemoryProperties`, `config/AiConfig` (repository bean, `Clock` bean, advisor order check), scheduling enabled for the cleanup, `openapi.yaml` (`conversationId` required, expiry documented). No new dependencies.
- API: `GET /search` without `conversationId` now returns 400 instead of running in the shared conversation `"1"`.
- Configuration: `ecom.memory.inactivity-timeout` (default `24h`), `ecom.memory.cleanup-interval` (default `1h`).
- Tests: controller test for the missing id; repository unit tests with a controllable clock for expiry and per-token delete; integration tests proving two turns of one token share context, two tokens do not, and `clearMemory` affects only its own token.
- Cross-project contract: the storefront (`../eas-frontend`, change `storefront-search-ui`) generates the token with `crypto.randomUUID()` (36 characters, within the 50-character id rule), keeps it in React state only (no `sessionStorage`), creates a new token on "New search" and on page load, and sends it with every search. That change's "Conversation identity" requirement, which currently specifies `sessionStorage`, needs to be updated to match.
