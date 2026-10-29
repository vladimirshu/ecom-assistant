## ADDED Requirements

### Requirement: Schedule-aware availability check
On every page load the application SHALL render the storefront immediately and SHALL evaluate the opening window locally before making any backend request. The frontend SHALL hold the same opening days, times and IANA time zone as the infrastructure, generated from one deployment configuration. The window SHALL include its opening instant and exclude its closing instant; evaluation SHALL use the configured zone and its daylight-saving rules, independently of the visitor's local zone. Missing or invalid schedule configuration SHALL fail the production build.

Inside the opening window, the application SHALL probe `GET /api/v1/health` in the background with a 2.5 second timeout. Network failures, timeouts, 5xx and 4xx other than 429 SHALL be considered unhealthy; 2xx and 429 SHALL be considered healthy. Outside the window, the application SHALL immediately call `GET /api/v1/control/up` without a preceding backend health or search request. Until availability is confirmed, search submission SHALL be blocked and typed input SHALL be preserved.

#### Scenario: Healthy service during opening hours
- **WHEN** the page loads inside the opening window and `/api/v1/health` answers 200
- **THEN** no sign, overlay or spinner is rendered and no control call is made

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
- **THEN** exactly one immediate control request is sent and the sign shows the opening state

#### Scenario: Already running outside opening hours
- **WHEN** an off-hours visit receives `{"state":"UP"}` from the control endpoint
- **THEN** the page shows "Open", then makes the storefront usable, without any backend health request

#### Scenario: Off-hours startup
- **WHEN** an off-hours visit receives `{"state":"STARTING"}`
- **THEN** the sign shows opening progress and only the control endpoint is polled until UP is confirmed

#### Scenario: Wake call fails
- **WHEN** the control request fails or returns an invalid body
- **THEN** the sign remains in the opening state and the request is repeated at the next control polling interval

### Requirement: Door sign
While availability is unconfirmed or the service is starting, the application SHALL show a shop door sign over the page that reads "Closed", names the opening hours converted to the visitor's local time zone, and offers a "Knock to wake us" button. The page behind the sign SHALL NOT be operable. When the service is healthy the sign SHALL NOT be present.

#### Scenario: Closed sign
- **WHEN** an in-hours health probe fails or an off-hours availability check begins
- **THEN** a sign reading "Closed" and a button labelled "Knock to wake us" are visible and the search field behind it cannot be focused

#### Scenario: Hours in the visitor's time zone
- **WHEN** the build is configured with `VITE_OPEN_TZ=Europe/Berlin`, `VITE_OPEN_DAYS=1-5`, `VITE_OPEN_FROM=08:00`, `VITE_OPEN_TO=22:00` and the visitor's browser time zone is `America/New_York`
- **THEN** the sign names the hours as the equivalent New York local times and weekdays

#### Scenario: No sign in steady state
- **WHEN** the service has been healthy since page load
- **THEN** no element of the gate is rendered, including after later successful searches

### Requirement: Opening progress
While waiting for the service, the application SHALL poll `/api/v1/control/up` every 3 seconds, with no overlapping control requests, and SHALL NOT poll the backend health endpoint. It SHALL show an elapsed timer and rotate the sign's status message every 4 seconds through a fixed list starting with "Waking up the assistant…" and including "Starting AWS services…" and "Preparing the shop…". Control polling SHALL stop as soon as UP is confirmed or the stuck state is reached. Polling SHALL NOT continue as a heartbeat after the gate opens.

#### Scenario: Message rotation
- **WHEN** the opening state has lasted 9 seconds
- **THEN** the third message is shown and the elapsed time reads `0:09`

#### Scenario: Polling cadence
- **WHEN** the opening state has lasted 9 seconds with prompt STARTING responses
- **THEN** control requests have been sent at 0, 3, 6 and 9 seconds and no backend health or search request has been sent

### Requirement: Knock action
The "Knock to wake us" button SHALL be available whenever the sign is shown. Pressing it SHALL play a knock animation and immediately call `/api/v1/control/up`, without a backend health probe or overlapping an existing control request. Presses within one second of a previous press SHALL be ignored.

#### Scenario: Knock while starting
- **WHEN** the visitor presses the button and the control response is STARTING
- **THEN** the sign stays in the opening state

#### Scenario: Knock when ready
- **WHEN** the visitor presses the button and the control response is UP
- **THEN** the sign flips to "Open" and disappears without a backend health probe

#### Scenario: Rapid presses
- **WHEN** the button is pressed twice within 500 milliseconds
- **THEN** only one control request is sent

### Requirement: Opening the shop
When the first valid UP control response arrives while the sign is shown, the sign SHALL flip from "Closed" to "Open" once, remain visible for about one second, and then be removed so that the storefront is usable.

#### Scenario: Flip and disappear
- **WHEN** the control endpoint returns UP during the opening state
- **THEN** the sign reads "Open", and after about one second the gate renders nothing and the page behind it is operable

#### Scenario: Reduced motion
- **WHEN** the visitor's system requests reduced motion
- **THEN** the change from "Closed" to "Open" is a crossfade without rotation

### Requirement: Stuck state
If the service is not healthy 3 minutes after the first wake-up call, the application SHALL stop polling, SHALL show that the door seems stuck, and SHALL offer a "Try again" action that restarts the opening cycle with a new wake-up call.

#### Scenario: Timeout of the opening phase
- **WHEN** 180 seconds pass after the first `/api/v1/control/up` call without a valid UP control response
- **THEN** the sign shows the stuck message and "Try again" and no further polling takes place

#### Scenario: Try again
- **WHEN** the visitor presses "Try again"
- **THEN** `/api/v1/control/up` is called, polling resumes, and the 3-minute window restarts

### Requirement: Service disappears during a session
Before each search submission, the application SHALL re-evaluate the opening window. Outside the window it SHALL check `/api/v1/control/up` before sending the search, including after a previous off-hours UP response; UP permits that submission, while STARTING or failure reopens the gate and preserves input without sending the search. This check is triggered by submission, not a heartbeat. After startup the visitor SHALL resubmit explicitly; the search SHALL NOT be replayed automatically.

When a search fails on the network, times out or answers 5xx, the application SHALL check availability through the control endpoint outside opening hours, or through the health endpoint inside opening hours. Confirmed readiness SHALL leave the gate invisible and show the ordinary search error; otherwise the gate SHALL return and start the control-based wake-up cycle, preserving typed text and the last request. Already in-flight searches may still fail if the backend stops after readiness was confirmed.

#### Scenario: Off-hours session ended before submission
- **WHEN** the off-hours session has ended and the visitor submits `warm jacket`
- **THEN** the frontend calls the control endpoint first, a STARTING response reopens the sign, no search request is sent, and `warm jacket` is preserved

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
- **THEN** the URL still reads `/?ref=acme` after the sign has disappeared, and the only availability calls made are `/api/v1/control/up` outside opening hours, plus the initial health probe if the visit began inside opening hours

### Requirement: Accessibility of the gate
The sign SHALL be exposed as a modal dialog labelled "Shop status", SHALL move keyboard focus to the "Knock to wake us" button when shown and return focus to the page when removed, SHALL announce its current status in a polite live region (rotating messages at most every 12 seconds), and SHALL have no critical or serious axe-core violations.

#### Scenario: Focus handling
- **WHEN** the sign appears
- **THEN** focus is on "Knock to wake us", and pressing Tab does not reach elements behind the sign

#### Scenario: Status announcement
- **WHEN** the state changes from closed to opening
- **THEN** the live region contains the opening message exactly once
