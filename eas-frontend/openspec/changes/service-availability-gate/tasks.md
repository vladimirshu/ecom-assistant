## 1. Clients and configuration

- [ ] 1.1 `src/gate/health.ts`: `probeHealth(signal)` for `GET /api/v1/health` with a 2.5 s abort timeout returning `healthy | unhealthy` per the classification (network, timeout, 5xx, 4xx except 429 are unhealthy); unit tests with MSW for 200, 429, 403, 502, 504, network error and timeout
- [ ] 1.2 `src/gate/wake.ts`: `callUp()` for `GET /api/v1/control/up`, returning `STARTING | UP | failed`, never throwing; unit tests (both states, malformed body, network error)
- [ ] 1.3 `src/gate/hours.ts`: parse `VITE_OPEN_TZ`, `VITE_OPEN_DAYS`, `VITE_OPEN_FROM`, `VITE_OPEN_TO` and a zone-aware `isWithinOpeningHours(now)` (opening inclusive, closing exclusive), plus `formatHours()` converting to the visitor's zone with `Intl.DateTimeFormat`, including weekday shifts across midnight; unit tests for Europe/Berlin shown in America/New_York, Asia/Tokyo and the same zone; generate the production variables from the same deployment configuration as infrastructure; fail production builds on invalid/missing configuration; test weekdays, weekends, exact boundaries and DST independently of the visitor zone; add the variables to `.env.example` and the README
- [ ] 1.4 `src/gate/messages.ts`: ordered message list and `messageAt(elapsedSeconds)`; unit tests (first message at 0 s, rotation every 4 s, wrap-around)

## 2. State machine

- [ ] 2.1 `useAvailabilityGate()` reducer and hook with phases `CHECKING`, `CLOSED`, `WAKING`, `OPENED`, `HEALTHY`, `STUCK`; injected clock and clients; auto-wake on entering `CLOSED`; schedule-aware initial routing; valid control UP opens directly; 3 s control-only polling without overlapping requests, 3 min stuck timeout
- [ ] 2.2 `knock()` (immediate `/up`, no health probe or overlapping control calls, ignore presses within 1 s), `retry()` from `STUCK`, and a pre-submit schedule check (off-hours `/up` before every search), and `reportFailure()` (in-hours health, off-hours control; unready → `CLOSED`)
- [ ] 2.3 State machine tests with fake timers: in-hours healthy load has no control call; off-hours first request is control with zero backend requests; STARTING polls only control at 0/3/6/9 s; UP opens directly with no health probe; malformed/failed control never opens; in-hours 504/timeout wakes and 429 does not; Knock and rapid presses; no overlapping control calls; flip removal; stuck at 180 s stops requests and retry restarts; no polling after UP; off-hours submission after session expiry and crossing closing time preserves input without sending search; ordinary errors with confirmed readiness stay in the search flow


## 3. UI

- [ ] 3.1 `DoorSign` component: sign board "Closed"/"Open" faces with a CSS 3D flip, hours line, status message with elapsed time, "Knock to wake us" button, stuck variant with "Try again"
- [ ] 3.2 Knock animation (sign shake and tap rings) and the `prefers-reduced-motion` crossfade for both the flip and the knock
- [ ] 3.3 Overlay behaviour: `role="dialog"`, `aria-modal`, label "Shop status", focus to the button on open and back on close, `inert` (with `aria-hidden` and focus-trap fallback) on the page root, polite live region with the 12 s announcement throttle
- [ ] 3.4 Mount the gate in `App.tsx` next to the storefront; wire search submission through the schedule-aware pre-submit check and `ErrorNotice`/search failures to `reportFailure()`, keep the typed text and last request in the search session; component tests (overlay content, hours text, focus, reduced motion, no DOM when healthy)

## 4. Development and end-to-end

- [ ] 4.1 Dev-only `?gate=closed` switch (guarded by `import.meta.env.DEV`) plus MSW handlers for `/api/v1/health` and `/api/v1/control/up` so the sign can be reviewed locally; check that it is absent from the production bundle
- [ ] 4.2 Playwright with route mocks: healthy load shows no sign; off-hours load calls control before any backend request, STARTING shows the sign and progress, UP flips and removes it; already-running off-hours load opens directly from UP; knock; stuck and try again; off-hours session expiry blocks the next search until control confirms UP and keeps typed text; closing-time transition; failure after readiness; `?ref=acme` preserved; 429 never wakes
- [ ] 4.3 Playwright viewport checks (390 px and 1440 px) and an axe audit on the sign in the closed, opening and stuck states
- [ ] 4.4 README section describing the gate, the build variables, the 20-minute off-hours session behaviour and how to preview the sign locally
- [ ] 4.5 Manual check against the deployed stack (after `aws-control-plane` and `aws-edge-hosting`): visit off-hours with the instance stopped and already running; verify control is the first request, no backend health polling, UP opens the shop, and then search works; `npm run lint && npm test && npm run test:e2e && npm run build` green
