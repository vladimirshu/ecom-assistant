## ADDED Requirements

### Requirement: Health probe on page load
On every page load the application SHALL render the storefront immediately and SHALL probe `GET /api/v1/health` in the background with a 2.5 second timeout. The service SHALL be considered unhealthy when the request fails on the network, times out, answers with a 5xx status, or answers with a 4xx status other than 429; any 2xx answer, and HTTP 429, SHALL be considered healthy.

#### Scenario: Healthy service
- **WHEN** the page loads and `/api/v1/health` answers 200
- **THEN** no sign, overlay or spinner is rendered and no call to `/api/v1/control/up` is made

#### Scenario: Stopped service answers with a gateway error
- **WHEN** `/api/v1/health` answers 504
- **THEN** the service is considered unhealthy

#### Scenario: Probe times out
- **WHEN** `/api/v1/health` does not answer within 2.5 seconds
- **THEN** the request is aborted and the service is considered unhealthy

#### Scenario: Unexpected client error
- **WHEN** `/api/v1/health` answers 403
- **THEN** the service is considered unhealthy

#### Scenario: Rate limited is not asleep
- **WHEN** `/api/v1/health` answers 429
- **THEN** the service is considered healthy and `/api/v1/control/up` is not called

### Requirement: Wake-up on visit
When the service is considered unhealthy, the application SHALL immediately call `GET /api/v1/control/up` once, without waiting for any user action, and SHALL treat the answers `{"state":"STARTING"}` and `{"state":"UP"}` alike as "opening"; only a healthy `/api/v1/health` response ends the wait.

#### Scenario: Automatic wake
- **WHEN** the first health probe fails
- **THEN** exactly one request to `/api/v1/control/up` is sent and the sign shows the opening state

#### Scenario: Wake call fails
- **WHEN** the call to `/api/v1/control/up` fails on the network
- **THEN** the sign stays in the opening state and the call is repeated at the next 30-second interval

#### Scenario: Control says UP but health still fails
- **WHEN** `/api/v1/control/up` answers `{"state":"UP"}` and `/api/v1/health` still fails
- **THEN** the sign stays in the opening state and polling continues

### Requirement: Door sign
While the service is unhealthy, the application SHALL show a shop door sign over the page that reads "Closed", names the opening hours converted to the visitor's local time zone, and offers a "Knock to wake us" button. The page behind the sign SHALL NOT be operable. When the service is healthy the sign SHALL NOT be present.

#### Scenario: Closed sign
- **WHEN** the health probe fails
- **THEN** a sign reading "Closed" and a button labelled "Knock to wake us" are visible and the search field behind it cannot be focused

#### Scenario: Hours in the visitor's time zone
- **WHEN** the build is configured with `VITE_OPEN_TZ=Europe/Berlin`, `VITE_OPEN_DAYS=1-5`, `VITE_OPEN_FROM=08:00`, `VITE_OPEN_TO=22:00` and the visitor's browser time zone is `America/New_York`
- **THEN** the sign names the hours as the equivalent New York local times and weekdays

#### Scenario: No sign in steady state
- **WHEN** the service has been healthy since page load
- **THEN** no element of the gate is rendered, including after later successful searches

### Requirement: Opening progress
While waiting for the service, the application SHALL poll `/api/v1/health` every 3 seconds, SHALL repeat the `/api/v1/control/up` call every 30 seconds, SHALL show an elapsed timer, and SHALL rotate the sign's status message every 4 seconds through a fixed list that starts with "Waking up the assistant…" and includes "Starting AWS services…" and "Preparing the shop…".

#### Scenario: Message rotation
- **WHEN** the opening state has lasted 9 seconds
- **THEN** the third message of the list is shown and the elapsed time reads `0:09`

#### Scenario: Polling cadence
- **WHEN** the opening state has lasted 31 seconds with the service still unhealthy
- **THEN** about ten health probes and exactly two `/api/v1/control/up` calls have been sent (the automatic one and one repeat)

### Requirement: Knock action
The "Knock to wake us" button SHALL be available whenever the sign is shown. Pressing it SHALL play a knock animation, SHALL immediately probe `/api/v1/health` without waiting for the next poll, and SHALL call `/api/v1/control/up` again if the probe is unhealthy. Presses within one second of a previous press SHALL be ignored.

#### Scenario: Knock while the service is starting
- **WHEN** the visitor presses the button during the opening state and the probe still fails
- **THEN** a health probe is sent immediately and `/api/v1/control/up` is called again

#### Scenario: Knock after the service became ready
- **WHEN** the visitor presses the button and the probe succeeds
- **THEN** the sign flips to "Open" and then disappears, and `/api/v1/control/up` is not called

#### Scenario: Rapid presses
- **WHEN** the button is pressed twice within 500 milliseconds
- **THEN** only one probe is sent

### Requirement: Opening the shop
When the first healthy `/api/v1/health` response arrives while the sign is shown, the sign SHALL flip from "Closed" to "Open" once, remain visible for about one second, and then be removed so that the storefront is usable.

#### Scenario: Flip and disappear
- **WHEN** a health probe succeeds during the opening state
- **THEN** the sign reads "Open", and after about one second the gate renders nothing and the page behind it is operable

#### Scenario: Reduced motion
- **WHEN** the visitor's system requests reduced motion
- **THEN** the change from "Closed" to "Open" is a crossfade without rotation

### Requirement: Stuck state
If the service is not healthy 3 minutes after the first wake-up call, the application SHALL stop polling, SHALL show that the door seems stuck, and SHALL offer a "Try again" action that restarts the opening cycle with a new wake-up call.

#### Scenario: Timeout of the opening phase
- **WHEN** 180 seconds pass after the first `/api/v1/control/up` call without a healthy health probe
- **THEN** the sign shows the stuck message and "Try again" and no further polling takes place

#### Scenario: Try again
- **WHEN** the visitor presses "Try again"
- **THEN** `/api/v1/control/up` is called, polling resumes, and the 3-minute window restarts

### Requirement: Service disappears during a session
When a search fails on the network, times out, or answers with a 5xx status, the application SHALL probe `/api/v1/health`. If the probe is unhealthy, the sign SHALL return and the wake-up cycle SHALL start again, preserving the typed text and the last search request; if the probe is healthy, the search error SHALL be shown as an ordinary error.

#### Scenario: Session ended
- **WHEN** the service stopped after 20 minutes and the visitor submits `warm jacket`, the search fails with 504, and `/api/v1/health` also fails
- **THEN** the sign "Closed" is shown, a wake-up call is sent, and after the sign disappears the search field still contains `warm jacket`

#### Scenario: Ordinary backend error
- **WHEN** a search fails with 500 and `/api/v1/health` answers 200
- **THEN** the gate stays invisible and the search error state "Something went wrong on our side" is shown

#### Scenario: Usage limit
- **WHEN** a search answers 429
- **THEN** the gate is not involved and the usage-limit message of the search flow is shown

### Requirement: Campaign tag preserved
The application SHALL NOT modify or strip the query string of the page URL (for example `?ref=acme`) and SHALL NOT send additional requests to record visits.

#### Scenario: Reference tag
- **WHEN** the page is opened at `/?ref=acme` and the service is asleep
- **THEN** the URL still reads `/?ref=acme` after the sign has disappeared, and the only API calls made are the health probes and `/api/v1/control/up`

### Requirement: Accessibility of the gate
The sign SHALL be exposed as a modal dialog labelled "Shop status", SHALL move keyboard focus to the "Knock to wake us" button when shown and return focus to the page when removed, SHALL announce its current status in a polite live region (rotating messages at most every 12 seconds), and SHALL have no critical or serious axe-core violations.

#### Scenario: Focus handling
- **WHEN** the sign appears
- **THEN** focus is on "Knock to wake us", and pressing Tab does not reach elements behind the sign

#### Scenario: Status announcement
- **WHEN** the state changes from closed to opening
- **THEN** the live region contains the opening message exactly once
