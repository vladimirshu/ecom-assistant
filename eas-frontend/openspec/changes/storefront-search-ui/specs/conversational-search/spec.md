## ADDED Requirements

### Requirement: Backend access through the same-origin API path
The browser SHALL call only its own origin, at `GET /api/v1/search` with the query parameters `message`, `conversationId` and `clearMemory`, SHALL use a relative URL so that no backend host is configured in the application, and SHALL handle the status code and JSON body returned by the backend.

#### Scenario: Request shape
- **WHEN** a visitor submits `rain jacket`
- **THEN** the browser requests `/api/v1/search?message=rain%20jacket&conversationId=<uuid>&clearMemory=true` on the page's own origin

#### Scenario: Development proxy
- **WHEN** the application runs on the Vite dev server
- **THEN** requests to `/api/v1/*` are forwarded to the backend at `http://localhost:8080` (or `VITE_DEV_BACKEND_URL`)

### Requirement: Conversation identity
The application SHALL generate a random UUID conversation id, keep it in application memory only (not in session or local storage), create a new one on every page load and on "New search", and send it with every search.

#### Scenario: Reload starts a new conversation
- **WHEN** a visitor searches, reloads the page and searches again
- **THEN** the two requests carry different conversation ids

#### Scenario: Same conversation while the page stays open
- **WHEN** a visitor searches and then refines without reloading
- **THEN** both requests carry the same conversation id

#### Scenario: Separate tabs
- **WHEN** the storefront is opened in two tabs
- **THEN** the tabs use different conversation ids

### Requirement: First search and refinement
The first search of a conversation SHALL be sent with `clearMemory=true`; every following search in the same conversation SHALL be sent with `clearMemory=false` and SHALL be presented as a refinement of the previous results.

#### Scenario: First search
- **WHEN** a visitor submits `warm winter jacket` on the landing page
- **THEN** the request has `message=warm winter jacket` and `clearMemory=true`

#### Scenario: Refinement
- **WHEN** results are shown and the visitor submits `same but in navy` in the compact search bar
- **THEN** the request has `message=same but in navy`, the same conversation id and `clearMemory=false`

### Requirement: New search
The "New search" action SHALL discard the conversation trail, results and interpretation, return to the hero, and make the next search start a fresh conversation with `clearMemory=true`.

#### Scenario: Start over
- **WHEN** a visitor clicks "New search" and then submits `leather jacket`
- **THEN** the hero is shown before the submit, and the request has `clearMemory=true`

### Requirement: Client-side input check
Before sending, the application SHALL trim the message and SHALL NOT send it if it is shorter than 3 or longer than 500 characters or contains `<` or `>`, showing an inline hint under the search field instead; the backend remains authoritative.

#### Scenario: Too short
- **WHEN** a visitor submits `ab`
- **THEN** no request is sent and an inline hint asks for a longer description

#### Scenario: Non-Latin text allowed
- **WHEN** a visitor submits `зимняя куртка до 200 евро`
- **THEN** the request is sent

### Requirement: Loading state
While a search is in progress, the application SHALL show skeleton product cards and a staged progress message advancing through "Reading your request", "Searching the collection" and "Picking the best matches", SHALL announce the progress in the live region, and SHALL keep the previous results dimmed during a refinement.

#### Scenario: Slow response
- **WHEN** the backend takes 5 seconds to respond
- **THEN** the progress message has advanced past "Reading your request" before the results appear

### Requirement: Superseded requests
When a new search is submitted while one is in progress, the application SHALL abort the previous request and SHALL render only the response of the latest request.

#### Scenario: Double submit
- **WHEN** a visitor submits `rain jacket` and, before it completes, submits `puffer jacket`
- **THEN** only the results for `puffer jacket` are shown

### Requirement: Rejected request
When the backend responds with HTTP 422, the application SHALL show a calm message ("Let's try that another way") with each suggested prompt as a clickable chip, SHALL keep the previous results if any, and SHALL submit a clicked suggestion as a refinement in the same conversation.

#### Scenario: Suggestion clicked
- **WHEN** the backend responds 422 with `suggestedPrompts = ["A warm winter jacket", "A light summer jacket"]` and the visitor clicks `A light summer jacket`
- **THEN** a search with message `A light summer jacket` and `clearMemory=false` is sent

#### Scenario: Suggestions in the customer's language
- **WHEN** the backend returns suggested prompts in Russian
- **THEN** the chips show the Russian text unchanged while the surrounding message stays in English

### Requirement: Invalid input from the backend
When the backend responds with HTTP 400, the application SHALL show the field error for `message` as an inline hint under the search field and SHALL keep the typed text.

#### Scenario: Backend rejects characters
- **WHEN** the backend responds 400 with `{"message": "Message contains unsupported characters"}`
- **THEN** that text is shown under the search field and the field still contains the typed message

### Requirement: Usage limits
When the backend responds with HTTP 429, the application SHALL show a calm message that depends on the body `code`: for `RATE_LIMITED`, a hint to wait (with the number of seconds from the `Retry-After` header when present) and a "Try again" action enabled after that time; for `DAILY_BUDGET_EXHAUSTED`, a message that today's demo quota is used up and that the shop reopens tomorrow, with no retry action. The application SHALL keep the previous results and the typed text, and SHALL NOT treat HTTP 429 as a sleeping service.

#### Scenario: Rate limited
- **WHEN** the backend responds 429 with `{"code": "RATE_LIMITED"}` and `Retry-After: 30`
- **THEN** the message mentions waiting about 30 seconds and "Try again" becomes available after 30 seconds

#### Scenario: Daily budget exhausted
- **WHEN** the backend responds 429 with `{"code": "DAILY_BUDGET_EXHAUSTED"}`
- **THEN** the quota message is shown without a retry action and the previous results remain visible

### Requirement: Server and network errors
When the backend responds with HTTP 5xx, the request fails on the network, or no response arrives within 30 seconds, the application SHALL report the failure to the availability gate (capability `service-availability-gate`) and, if the service is healthy, SHALL show "Something went wrong on our side" with a "Try again" action that resends the same request, and SHALL keep the previous results if any.

#### Scenario: Retry
- **WHEN** a search fails with HTTP 500 and the visitor clicks "Try again"
- **THEN** the same message is sent again with the same conversation id and `clearMemory` value

#### Scenario: Timeout
- **WHEN** the backend does not respond within 30 seconds
- **THEN** the request is aborted and the error state is shown

#### Scenario: Service is down
- **WHEN** a search fails with HTTP 502 and the availability check (health inside opening hours, control outside hours) does not confirm readiness
- **THEN** the availability gate takes over instead of the error state, and the typed message is kept
