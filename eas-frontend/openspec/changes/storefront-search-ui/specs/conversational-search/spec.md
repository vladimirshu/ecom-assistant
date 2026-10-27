## ADDED Requirements

### Requirement: Backend access through same-origin proxy
The browser SHALL call only the storefront's own origin at `GET /api/search`, which SHALL forward the query parameters unchanged to `<BACKEND_URL>/search` and return the backend's status code and JSON body unchanged; `BACKEND_URL` SHALL be server-side configuration defaulting to `http://localhost:8080`.

#### Scenario: Forwarding
- **WHEN** the browser requests `/api/search?message=rain%20jacket&conversationId=abc&clearMemory=false`
- **THEN** the backend receives `GET /search?message=rain%20jacket&conversationId=abc&clearMemory=false` and the browser receives the backend's status and body

#### Scenario: Backend unreachable
- **WHEN** the backend cannot be reached
- **THEN** `/api/search` responds with HTTP 502 and the UI shows the server error state

### Requirement: Conversation identity
The application SHALL generate a random UUID conversation id per browser tab, keep it in session storage across reloads of that tab, and send it with every search.

#### Scenario: Reload keeps conversation
- **WHEN** a visitor searches, reloads the tab and refines
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

### Requirement: Server and network errors
When the backend responds with HTTP 5xx, the request fails on the network, or no response arrives within 30 seconds, the application SHALL show "Something went wrong on our side" with a "Try again" action that resends the same request, and SHALL keep the previous results if any.

#### Scenario: Retry
- **WHEN** a search fails with HTTP 500 and the visitor clicks "Try again"
- **THEN** the same message is sent again with the same conversation id and `clearMemory` value

#### Scenario: Timeout
- **WHEN** the backend does not respond within 30 seconds
- **THEN** the request is aborted and the error state is shown
