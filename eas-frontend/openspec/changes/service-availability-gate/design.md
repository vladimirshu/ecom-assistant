## Context

The backend is not always on (root changes `aws-backend-host`, `aws-control-plane`). Production request paths through CloudFront:

```
/*                  → S3         static SPA, always available
/api/v1/control/*   → Lambda     always on, wakes the instance
/api/v1/*           → EC2        up weekdays 08:00–22:00, or for 20 minutes after a wake outside those hours
```

Contract used by the gate:
- `GET /api/v1/health` → 200 only when the backend is ready (the catalog import is finished and the vector store is reachable). When the instance is stopped, CloudFront answers 504 within about 3 s (origin connection timeout configured in `aws-edge-hosting`); during boot the origin may answer 502/503.
- `GET /api/v1/control/up` → always HTTP 200 `{"state": "UP" | "STARTING"}`; idempotent; starts the instance if it is stopped. It is not cached.
- `GET /api/v1/search` may answer 429 `{code, message}` (change `demo-cost-controls`). A 429 means the service is awake and refusing, so it must never trigger a wake-up.

Startup takes about 60–90 s (instance start, container start, Spring Boot, catalog check), so the visitor waits more than a minute. The gate has to make that wait feel intentional, and its first duty is to be invisible when everything is fine.

## Goals / Non-Goals

**Goals:**
- During opening hours, zero visual footprint and one health probe when ready; outside opening hours, check Lambda first and never wait for a stopped backend timeout.
- Wake the service as soon as a visitor arrives, without needing a click.
- A memorable, on-brand waiting experience: a shop door sign that flips from "Closed" to "Open".
- Predictable behaviour for slow starts, stuck starts, and the service disappearing mid-session.

**Non-Goals:**
- Enforcing backend start/stop times or the 20-minute session (infrastructure). The frontend uses the shared schedule to choose its availability route.
- A heartbeat or session timer from the SPA: it is not needed and not allowed to keep the instance alive.
- Pre-recorded example searches while asleep (follow-up).
- Authentication or deciding who may wake the service.

## Decisions

**1. Render the storefront and choose the availability route locally.**
Render the storefront immediately. Evaluate the configured opening window before sending availability or search requests. Inside the window, probe backend health in the background; a healthy response leaves the gate invisible. Outside the window, call `/api/v1/control/up` immediately and show the sign while availability is checked, blocking search. No backend request precedes this control response. A valid UP response shows Open and releases the gate; STARTING or a failed/invalid response keeps it closed.

**2. One state machine, no data-fetching library.**
`useAvailabilityGate()` holds `{ phase, startedAt, messageIndex, lastWakeAt }` and uses injected clock, timers and clients:
- `CHECKING`: evaluate the schedule; inside hours probe health (2.5 s timeout), outside hours call control directly. Healthy in-hours visits enter `HEALTHY` invisibly; off-hours checks show the sign until control confirms UP.
- `CLOSED`: show the sign, make the page behind it inert, and immediately call control if no request is already in flight.
- `WAKING`: STARTING or failed/invalid control response; poll control every 3 s without overlapping requests, rotate messages every 4 s, show elapsed time. Do not poll backend health. A valid UP enters `OPENED` directly.
- `OPENED`: show Open for about 1 s, then remove the overlay and enter `HEALTHY`.
- `HEALTHY`: no background control polling or heartbeat. Before each off-hours search, recheck control; UP permits that search, otherwise reopen the gate and preserve input.
- `STUCK`: 3 minutes after the first control call without UP; stop polling. Try again starts a fresh control cycle and timeout.
Cancel obsolete timers and ignore stale responses after readiness, retry or unmount. A Knock request does not overlap an in-flight control call.

**3. "Unhealthy" is defined on the client.**
Not ok means: the request throws (network), aborts (timeout), the status is 5xx, or the status is 4xx other than 429. A 429 means the service is awake and rate limiting, so the gate treats it as healthy. Any 2xx is healthy whatever the body. The body is never parsed.
- Why 4xx counts as unhealthy: a stopped origin behind CloudFront can surface as 403 from a misconfigured origin and, more generally, a failed readiness check means the visitor cannot search.
- Risk: a persistent 4xx wakes the service repeatedly. Bounded by the stuck state and the 3 s control polling interval; the cost ceiling of the infrastructure is the 24/7 price.

**4. Auto-wake and the Knock button are both kept.**
Call control immediately on off-hours arrival or failed in-hours health probe. Knock plays the animation and calls control immediately, with no health probe; STARTING continues waiting and UP opens the shop. Ignore presses within one second and avoid concurrent control requests. The same control endpoint both starts a stopped instance and checks whether an already running instance is ready.

**5. Messages.**
`src/gate/messages.ts` exports an ordered list, rotated every 4 s and repeated:
"Waking up the assistant…", "Starting AWS services…", "Preparing the shop…", "Dusting off the jackets…", "Ironing the shelves…", "Checking the stockroom…". The first message is always the first in the list. Elapsed time is shown as `m:ss` below the message. The sign text under the "Closed" board states the hours in the visitor's time zone ("Open Mon–Fri, 09:00–23:00 your time").

**6. Hours in the visitor's time zone.**
`VITE_OPEN_TZ` (IANA name), `VITE_OPEN_DAYS` (`1-5`), `VITE_OPEN_FROM`, `VITE_OPEN_TO` (`08:00`, `22:00`) describe the schedule configured in the infrastructure. `formatHours()` converts the next-open-day times to the visitor's zone with `Intl.DateTimeFormat` and the day range accordingly. When days shift across midnight the range text lists the visitor's local weekday names. Generate these build variables from the same deployment configuration as the infrastructure schedules and sweeper. They control routing as well as display; missing or invalid values fail the production build. Evaluate weekdays and times in `VITE_OPEN_TZ` using a zone-aware clock (opening inclusive, closing exclusive), including DST; the visitor zone is used only for display. Schedule changes require a corresponding frontend rebuild/deployment. An already loaded page may retain an older schedule; the control endpoint remains authoritative for readiness whenever called.

**7. Motion and accessibility.**
- Sign: a CSS 3D `rotateY` flip, `transform-style: preserve-3d`, 0.8–1 s. Under `prefers-reduced-motion: reduce` the flip becomes a crossfade.
- Knock: a short shake of the sign and three tap rings; the same reduction rule applies.
- Overlay: `role="dialog"` with `aria-modal="true"`, label "Shop status", focus moves to the Knock button on open and returns to the page when the overlay closes; the page behind is `inert`.
- Status text (closed, opening message, stuck, open) is mirrored into one `aria-live="polite"` region; rotating messages are announced at most every 12 s to avoid chatter.

**8. Search submission and runtime loss.**
Before each search submission, re-evaluate the schedule. Outside hours, check control before sending the search, even if an earlier visit or submission received UP: a 20-minute session may have expired. UP permits the submission; STARTING or failure reopens the gate and keeps the typed text and last request. Do not automatically replay a search after startup.
For network/timeout/5xx search failures, use control outside hours and health inside hours. Confirmed readiness leaves the gate invisible and shows the ordinary search error; otherwise enter the control-based wake cycle. Never involve the gate on a search 429. A backend can stop between a readiness response and the search; handle that failure through the same flow.

**9. Local development.**
Local development uses an explicit always-available development mode so the gate probes the local backend without needing a local control Lambda. Production requires the shared schedule and cannot enable this bypass. To exercise it, MSW handlers and Playwright route mocks simulate health failures and `/up`; a dev-only query parameter `?gate=closed` forces the CLOSED/WAKING path against a mock `/up` so the sign can be reviewed without AWS. The flag is stripped from production builds (`import.meta.env.DEV`).

## Risks / Trade-offs

- [Crawlers and scanners that run JavaScript wake the service] → Bounded: an off-hours session ends 20 minutes after it starts, and the worst case is the instance running 24/7 (cost ceiling in `aws-control-plane`). CloudFront logs show the wakes.
- [60–90 s wait loses the visitor] → The sign is the first thing they see, messages show progress, and the hours text explains why. Pre-recorded examples while asleep are the follow-up.
- [A cached 504 after startup makes the probe fail for ~10 s more] → CloudFront error caching TTL is set to 0 for `/api/*` (`aws-edge-hosting`).
- [Session ends while the customer reads results] → The next off-hours submission checks control first, the gate returns if needed, the typed text is kept, and startup takes about a minute. A stop after readiness confirmation may still fail an in-flight search. Acceptable for a demo; documented in the README.
- [Frontend schedule drifts from infrastructure] → Generate both from one deployment configuration and deploy them together; test zone/DST boundaries. Loaded old tabs or incorrect client clocks can still choose an outdated route; control readiness remains authoritative when called.
- [429 mistaken for asleep] → Explicit exception in the probe and covered by a test.
- [`inert` unsupported in very old browsers] → Fallback: focus trap in the overlay, `aria-hidden` on the page root.

## Open Questions

- Should a successful wake-up of an off-hours session show how long the shop will stay open? The `/up` contract has only `state`, so this needs a contract change.
