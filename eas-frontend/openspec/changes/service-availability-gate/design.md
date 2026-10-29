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
- Zero visual footprint and no extra requests beyond one health probe when the service is healthy.
- Wake the service as soon as a visitor arrives, without needing a click.
- A memorable, on-brand waiting experience: a shop door sign that flips from "Closed" to "Open".
- Predictable behaviour for slow starts, stuck starts, and the service disappearing mid-session.

**Non-Goals:**
- Deciding or enforcing the schedule or the 20-minute session (infrastructure).
- A heartbeat or session timer from the SPA: it is not needed and not allowed to keep the instance alive.
- Pre-recorded example searches while asleep (follow-up).
- Authentication or deciding who may wake the service.

## Decisions

**1. The storefront renders first; the gate is an overlay.**
`App.tsx` renders `<Storefront/>` immediately and mounts `<AvailabilityGate/>` next to it. The probe runs in the background. If the service is healthy nothing is added to the DOM (no sign, no spinner, no skeleton), so there is no flash of "Closed" for the common case. If it is not, a modal-style overlay covers the page; the page behind it is made `inert` so keyboard focus cannot reach it.
- Alternative: block rendering until the first probe returns. Rejected: it delays every healthy visit by a round trip for the benefit of the rare asleep case.

**2. One state machine, no data-fetching library.**
`useAvailabilityGate()` holds `{ phase, startedAt, messageIndex, lastWakeAt }` with phases:

```
 PROBING ──ok──► HEALTHY (invisible)
    │
    └─ not ok ─► CLOSED ──(immediately)──► WAKING ──ok──► OPENED ──(~1 s flip)──► HEALTHY
                                              │                                     │
                                              │ 3 min since first /up               │ search fails, re-probe fails
                                              ▼                                     ▼
                                            STUCK ──[Try again]──► WAKING          CLOSED (auto-wake again)
```

- `PROBING`: initial health probe (2.5 s abort timeout).
- `CLOSED`: the sign is shown; `/up` is called once on entry (auto-wake) and the phase moves to `WAKING` as soon as the call has been sent (the response is not awaited for the display; `STARTING` and `UP` are both treated as "opening", and only a healthy `/health` ends the wait).
- `WAKING`: poll `/health` every 3 s; call `/up` again every 30 s; rotate messages every 4 s; show the elapsed time.
- `OPENED`: first healthy response; the sign flips once (about 1 s), then the overlay is removed.
- `STUCK`: 3 minutes after the first `/up` without a healthy response.
Timers use injected `setTimeout`/`fetch`, so tests use fake timers.
- Why a plain reducer plus effects, as in `useSearchSession`: the gate is a small, sequential state machine and not cacheable server state.

**3. "Unhealthy" is defined on the client.**
Not ok means: the request throws (network), aborts (timeout), the status is 5xx, or the status is 4xx other than 429. A 429 means the service is awake and rate limiting, so the gate treats it as healthy. A 200 is healthy whatever the body. The body is never parsed.
- Why 4xx counts as unhealthy: a stopped origin behind CloudFront can surface as 403 from a misconfigured origin and, more generally, anything other than a healthy 200 means the visitor cannot search.
- Risk: a persistent 4xx wakes the service repeatedly. Bounded by the stuck state and the 30 s nudge interval; the cost ceiling of the infrastructure is the 24/7 price.

**4. Auto-wake and the Knock button are both kept.**
Wake-on-visit saves the visitor the time it would take to read the sign. The button remains useful and keeps the metaphor: it gives control to someone who sees nothing happening, and it re-checks status immediately instead of waiting for the next 3 s tick. `/up` is idempotent, so extra calls are harmless. The knock press is rate-limited to once per second.
- Alternative: knock-only. Rejected by the product owner in favour of both.

**5. Messages.**
`src/gate/messages.ts` exports an ordered list, rotated every 4 s and repeated:
"Waking up the assistant…", "Starting AWS services…", "Preparing the shop…", "Dusting off the jackets…", "Ironing the shelves…", "Checking the stockroom…". The first message is always the first in the list. Elapsed time is shown as `m:ss` below the message. The sign text under the "Closed" board states the hours in the visitor's time zone ("Open Mon–Fri, 09:00–23:00 your time").

**6. Hours in the visitor's time zone.**
`VITE_OPEN_TZ` (IANA name), `VITE_OPEN_DAYS` (`1-5`), `VITE_OPEN_FROM`, `VITE_OPEN_TO` (`08:00`, `22:00`) describe the schedule configured in the infrastructure. `formatHours()` converts the next-open-day times to the visitor's zone with `Intl.DateTimeFormat` and the day range accordingly. When days shift across midnight the range text lists the visitor's local weekday names. These variables are duplicated knowledge: a mismatch with the infrastructure only makes the text wrong, never the behaviour.

**7. Motion and accessibility.**
- Sign: a CSS 3D `rotateY` flip, `transform-style: preserve-3d`, 0.8–1 s. Under `prefers-reduced-motion: reduce` the flip becomes a crossfade.
- Knock: a short shake of the sign and three tap rings; the same reduction rule applies.
- Overlay: `role="dialog"` with `aria-modal="true"`, label "Shop status", focus moves to the Knock button on open and returns to the page when the overlay closes; the page behind is `inert`.
- Status text (closed, opening message, stuck, open) is mirrored into one `aria-live="polite"` region; rotating messages are announced at most every 12 s to avoid chatter.

**8. Runtime loss.**
`ErrorNotice` in the search flow calls `gate.reportFailure()` after a network error, timeout or 5xx. The gate runs one health probe: healthy means "an ordinary backend error" (the search UI shows its own error); unhealthy moves to `CLOSED` and wakes again. The search session keeps the typed text and last request, so after `OPENED` the visitor can submit again. The session is not resubmitted automatically.

**9. Local development.**
Locally the backend is always up, so the gate stays invisible. To exercise it, MSW handlers and Playwright route mocks simulate health failures and `/up`; a dev-only query parameter `?gate=closed` forces the CLOSED/WAKING path against a mock `/up` so the sign can be reviewed without AWS. The flag is stripped from production builds (`import.meta.env.DEV`).

## Risks / Trade-offs

- [Crawlers and scanners that run JavaScript wake the service] → Bounded: an off-hours session ends 20 minutes after it starts, and the worst case is the instance running 24/7 (cost ceiling in `aws-control-plane`). CloudFront logs show the wakes.
- [60–90 s wait loses the visitor] → The sign is the first thing they see, messages show progress, and the hours text explains why. Pre-recorded examples while asleep are the follow-up.
- [A cached 504 after startup makes the probe fail for ~10 s more] → CloudFront error caching TTL is set to 0 for `/api/*` (`aws-edge-hosting`).
- [Session ends while the customer reads results] → The next search fails, the gate returns, the typed text is kept, and a wake takes about a minute. Acceptable for a demo; documented in the README.
- [Hours variables drift from the infrastructure schedule] → Text only; one place in the README lists both.
- [429 mistaken for asleep] → Explicit exception in the probe and covered by a test.
- [`inert` unsupported in very old browsers] → Fallback: focus trap in the overlay, `aria-hidden` on the page root.

## Open Questions

- Should a successful wake-up of an off-hours session show how long the shop will stay open? The `/up` contract has only `state`, so this needs a contract change.
