## ADDED Requirements

### Requirement: Schedule-aware availability check
On every page load the application SHALL render the storefront immediately and SHALL evaluate the opening window locally before making any backend request. The frontend SHALL hold the same opening days, times and IANA time zone as the infrastructure, generated from one deployment configuration. The window SHALL include its opening instant and exclude its closing instant; evaluation SHALL use the configured zone and its daylight-saving rules, independently of the visitor's local zone. Missing or invalid schedule configuration SHALL fail the production build.

Inside the opening window, the application SHALL probe `GET /api/v1/health` in the background with a 2.5 second timeout. Network failures, timeouts, 5xx and 4xx other than 429 SHALL be considered unhealthy; 2xx and 429 SHALL be considered healthy. Outside the window, the application SHALL immediately call `GET /api/v1/control/up` without a preceding backend health or search request. Until availability is confirmed, search submission SHALL be blocked and typed input SHALL be preserved.

#### Scenario: Healthy service during opening hours
- **WHEN** the page loads inside the opening window and `/api/v1/health` answers 200
- **THEN** no availability panel or spinner is rendered and no control call is made

#### Scenario: Off-hours arrival
- **WHEN** the visitor arrives outside the configured opening window
- **THEN** the first availability request is `/api/v1/control/up`, no backend request precedes it, and the page shows that availability is being checked

#### Scenario: Time-zone and boundary evaluation
- **WHEN** the schedule is Mon–Fri 08:00–22:00 in Europe/Berlin and the browser uses another zone
- **THEN** a weekday at 08:00 Berlin time is inside the window, 22:00 is outside, weekends are outside, and daylight-saving changes follow Europe/Berlin

#### Scenario: Invalid configuration
- **WHEN** the production build has missing or invalid opening days, times or IANA time zone
- **THEN** the build fails with a clear configuration error

#### Scenario: Failed health probe during opening hours
- **WHEN** the health probe answers 504 or 403, or does not answer within 2.5 seconds
- **THEN** it is considered unhealthy, a timed-out request is aborted, and the wake-up cycle starts

#### Scenario: Rate limited is not asleep
- **WHEN** the health probe inside opening hours answers 429
- **THEN** the service is considered healthy and no control call is made

### Requirement: Wake-up on visit
Outside opening hours, or after an unhealthy in-hours probe, the application SHALL immediately call `GET /api/v1/control/up` without user action. A valid `{"state":"UP"}` response SHALL confirm readiness directly, without a follow-up backend health probe. `{"state":"STARTING"}`, a failed call or an invalid response SHALL keep the gate closed and start or continue the opening cycle. A control response SHALL NOT be treated as UP solely because its HTTP status is 200.

#### Scenario: Automatic wake during opening hours
- **WHEN** the initial in-hours health probe fails
- **THEN** exactly one immediate control request is sent and the panel shows the opening state

#### Scenario: Already running outside opening hours
- **WHEN** an off-hours visit receives `{"state":"UP"}` from the control endpoint
- **THEN** the panel disappears and backend operations become available, without any backend health request

#### Scenario: Off-hours startup
- **WHEN** an off-hours visit receives `{"state":"STARTING"}`
- **THEN** the panel shows opening progress and only the control endpoint is polled until UP is confirmed

#### Scenario: Wake call fails
- **WHEN** the control request fails or returns an invalid body
- **THEN** the panel remains in the opening state and the request is repeated at the next control polling interval

### Requirement: Sage availability panel
Unconfirmed availability SHALL be presented by an accessible, non-modal Sage status panel within the static storefront shell, using the shared palette, typography and quiet header. A resting/checking state SHALL use A little rest for the demo.; startup SHALL use Getting the shop ready.; a failed or stuck startup SHALL use The demo couldn’t start. Copy MAY explain that the search service rests when unused. Opening hours SHALL be displayed in the visitor's local time zone from the existing build configuration. The old Closed/Open door board, 3D flip, knock animation and blocking page overlay SHALL NOT be implemented.

The panel SHALL block only backend-dependent requests. The visitor SHALL still be able to read the homepage, play the local search demonstration, type a draft, navigate the locally configured categories and inspect already loaded products. Search and product fetching SHALL wait for confirmed readiness, with an explanation next to disabled submit/loading actions. Healthy visits SHALL have no availability panel.

#### Scenario: Backend starting
- **WHEN** the control response is STARTING
- **THEN** the Sage startup panel is shown, no search or product request is sent, and static copy, demonstration and category links remain usable

#### Scenario: Local hours
- **WHEN** the schedule is configured in Europe/Berlin and the visitor is in America/New_York
- **THEN** the panel shows the equivalent local opening times and weekdays

#### Scenario: Draft preserved
- **WHEN** a visitor types while service readiness is unconfirmed
- **THEN** the draft is preserved and no search is submitted until readiness and an explicit submit action

### Requirement: Opening progress
While waiting for the service, the application SHALL poll `/api/v1/control/up` every 3 seconds, with no overlapping control requests, and SHALL NOT poll the backend health endpoint. It SHALL show an elapsed timer and rotate the status message every 4 seconds through a fixed list starting with "Waking up the assistant…" and including "Preparing the shop…" and "Checking the collection…". Control polling SHALL stop as soon as UP is confirmed or the stuck state is reached. Polling SHALL NOT continue as a heartbeat after the gate opens.

#### Scenario: Message rotation
- **WHEN** the opening state has lasted 9 seconds
- **THEN** the third message is shown and the elapsed time reads `0:09`

#### Scenario: Polling cadence
- **WHEN** the opening state has lasted 9 seconds with prompt STARTING responses
- **THEN** control requests have been sent at 0, 3, 6 and 9 seconds and no backend health or search request has been sent

### Requirement: Start demo action
The resting/checking panel SHALL offer Start demo, which requests an immediate control check without an overlapping control call; presses within one second SHALL be ignored. While a control call or startup polling is active, the action SHALL show Starting… and be disabled to avoid duplicate requests. Automatic wake-up on visit SHALL remain enabled; the button SHALL NOT be a prerequisite for the first wake call. Failed/stuck startup SHALL instead offer Try again, which restarts the control cycle. Actions SHALL NOT probe backend health outside the existing in-hours initial/failure routing.

#### Scenario: Automatic startup already in progress
- **WHEN** automatic control polling is waiting for UP
- **THEN** the action is labelled Starting… and repeated presses cannot send concurrent control requests

#### Scenario: Start action without an active request
- **WHEN** Start demo is available and activated
- **THEN** an immediate control request is sent and UP confirms readiness directly without a backend health probe

### Requirement: Readiness presentation
Only a valid UP control response, or a healthy in-hours health response, SHALL enable real backend operations. When startup completes the panel SHALL be removed without a door-sign transition or mandatory delay. Existing draft text, conversation history, selected result snapshot, category and loaded cards SHALL be retained. A blocked search SHALL require explicit resubmission; initial catalog loading may continue automatically as specified by `catalog-browsing`.

#### Scenario: Ready after startup
- **WHEN** a valid UP control response arrives
- **THEN** startup polling stops, the availability panel disappears and real API actions become available with the preserved draft

#### Scenario: Prototype timer is not readiness
- **WHEN** a fixed amount of time has elapsed but control still returns STARTING
- **THEN** the frontend continues the real waiting cycle and does not enable search based on the MagicPath prototype's simulated timer

### Requirement: Stuck state
If the service is not healthy 3 minutes after the first wake-up call, the application SHALL stop polling, SHALL show that startup could not be completed, and SHALL offer a "Try again" action that restarts the opening cycle with a new wake-up call.

#### Scenario: Timeout of the opening phase
- **WHEN** 180 seconds pass after the first `/api/v1/control/up` call without a valid UP control response
- **THEN** the panel shows the stuck message and "Try again" and no further polling takes place

#### Scenario: Try again
- **WHEN** the visitor presses "Try again"
- **THEN** `/api/v1/control/up` is called, polling resumes, and the 3-minute window restarts

### Requirement: Service disappears during a session
Before each search submission, the application SHALL re-evaluate the opening window. Outside the window it SHALL check `/api/v1/control/up` before sending the search, including after a previous off-hours UP response; UP permits that submission, while STARTING or failure reopens the gate and preserves input without sending the search. This check is triggered by submission, not a heartbeat. After startup the visitor SHALL resubmit explicitly; the search SHALL NOT be replayed automatically.

When a search fails on the network, times out or answers 5xx, the application SHALL check availability through the control endpoint outside opening hours, or through the health endpoint inside opening hours. Confirmed readiness SHALL leave the gate invisible and show the ordinary search error; otherwise the gate SHALL return and start the control-based wake-up cycle, preserving typed text and the last request. Already in-flight searches may still fail if the backend stops after readiness was confirmed.

#### Scenario: Off-hours session ended before submission
- **WHEN** the off-hours session has ended and the visitor submits `warm jacket`
- **THEN** the frontend calls the control endpoint first, a STARTING response reopens the panel, no search request is sent, and `warm jacket` is preserved

#### Scenario: Opening hours end while the page remains open
- **WHEN** the page loaded during opening hours and the next search is submitted after closing
- **THEN** the frontend checks the control endpoint before sending the search, without first probing backend health

#### Scenario: Ordinary backend error during opening hours
- **WHEN** a search fails with 500 inside opening hours and `/api/v1/health` answers 200
- **THEN** the gate stays invisible and the ordinary search error is shown

#### Scenario: Ordinary backend error outside opening hours
- **WHEN** an off-hours search fails with 500 and the control endpoint answers UP
- **THEN** the gate stays invisible and the ordinary search error is shown without a backend health probe

#### Scenario: Usage limit
- **WHEN** a search answers 429
- **THEN** the gate is not involved and the usage-limit message of the search flow is shown

### Requirement: Campaign tag preserved
The application SHALL NOT modify or strip the query string of the page URL (for example `?ref=acme`) and SHALL NOT send additional requests to record visits.

#### Scenario: Reference tag
- **WHEN** the page is opened at `/?ref=acme` and the service is asleep
- **THEN** the URL still reads `/?ref=acme` after the panel has disappeared, and the only availability calls made are `/api/v1/control/up` outside opening hours, plus the initial health probe if the visit began inside opening hours

### Requirement: Accessibility of availability feedback
The panel SHALL be a labelled non-modal status region with a polite live region, accessible action names and visible focus. It SHALL NOT trap focus or make the static page inert. Rotating messages SHALL be announced at most every 12 seconds and elapsed seconds SHALL NOT be announced individually. Motion SHALL be subtle and disabled under reduced motion. Removing the panel SHALL retain focus where possible or move it to the relevant search action if the previously focused action was removed. axe-core SHALL report no critical or serious violations.

#### Scenario: Static content remains reachable
- **WHEN** the startup panel is shown and a visitor uses Tab
- **THEN** header category links, demonstration controls and the draft field remain reachable, while unavailable API actions remain blocked

#### Scenario: Readiness while the action is focused
- **WHEN** readiness removes the focused startup action
- **THEN** focus moves to the relevant search action rather than being lost
