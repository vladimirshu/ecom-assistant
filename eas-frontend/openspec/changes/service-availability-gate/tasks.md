## 1. Clients and configuration

- [ ] 1.1 `src/gate/health.ts`: `probeHealth(signal)` for `GET /api/v1/health` with a 2.5 s abort timeout returning `healthy | unhealthy` per the classification (network, timeout, 5xx, 4xx except 429 are unhealthy); unit tests with MSW for 200, 429, 403, 502, 504, network error and timeout
- [ ] 1.2 `src/gate/wake.ts`: `callUp()` for `GET /api/v1/control/up`, returning `STARTING | UP | failed`, never throwing; unit tests (both states, malformed body, network error)
- [ ] 1.3 `src/gate/hours.ts`: parse `VITE_OPEN_TZ`, `VITE_OPEN_DAYS`, `VITE_OPEN_FROM`, `VITE_OPEN_TO` and `formatHours()` converting to the visitor's zone with `Intl.DateTimeFormat`, including weekday shifts across midnight; unit tests for Europe/Berlin shown in America/New_York, Asia/Tokyo and the same zone; add the variables to `.env.example` and the README
- [ ] 1.4 `src/gate/messages.ts`: ordered message list and `messageAt(elapsedSeconds)`; unit tests (first message at 0 s, rotation every 4 s, wrap-around)

## 2. State machine

- [ ] 2.1 `useAvailabilityGate()` reducer and hook with phases `PROBING`, `CLOSED`, `WAKING`, `OPENED`, `HEALTHY`, `STUCK`; injected clock and clients; auto-wake on entering `CLOSED`; 3 s health poll, 30 s wake nudge, 3 min stuck timeout
- [ ] 2.2 `knock()` (immediate probe, `/up` if still unhealthy, ignore presses within 1 s), `retry()` from `STUCK`, and `reportFailure()` for the search flow (one probe; unhealthy → `CLOSED`)
- [ ] 2.3 State machine tests with fake timers for every scenario in `service-availability-gate`: healthy page load makes no `/up` call; 504 and timeout wake once; 429 is healthy; polling and nudge cadence at 9 s and 31 s; knock when ready and when not; rapid knock; flip then removal after 1 s; stuck at 180 s and retry; runtime loss with preserved input; ordinary 500 leaves the gate invisible

## 3. UI

- [ ] 3.1 `DoorSign` component: sign board "Closed"/"Open" faces with a CSS 3D flip, hours line, status message with elapsed time, "Knock to wake us" button, stuck variant with "Try again"
- [ ] 3.2 Knock animation (sign shake and tap rings) and the `prefers-reduced-motion` crossfade for both the flip and the knock
- [ ] 3.3 Overlay behaviour: `role="dialog"`, `aria-modal`, label "Shop status", focus to the button on open and back on close, `inert` (with `aria-hidden` and focus-trap fallback) on the page root, polite live region with the 12 s announcement throttle
- [ ] 3.4 Mount the gate in `App.tsx` next to the storefront; wire `ErrorNotice`/search failures to `reportFailure()`, keep the typed text and last request in the search session; component tests (overlay content, hours text, focus, reduced motion, no DOM when healthy)

## 4. Development and end-to-end

- [ ] 4.1 Dev-only `?gate=closed` switch (guarded by `import.meta.env.DEV`) plus MSW handlers for `/api/v1/health` and `/api/v1/control/up` so the sign can be reviewed locally; check that it is absent from the production bundle
- [ ] 4.2 Playwright with route mocks: healthy load shows no sign; asleep load shows the sign, auto-wake, opening messages, flip and disappearance; knock; stuck and try again; session end during a search keeps the typed text; `?ref=acme` preserved; 429 never wakes
- [ ] 4.3 Playwright viewport checks (390 px and 1440 px) and an axe audit on the sign in the closed, opening and stuck states
- [ ] 4.4 README section describing the gate, the build variables, the 20-minute off-hours session behaviour and how to preview the sign locally
- [ ] 4.5 Manual check against the deployed stack (after `aws-control-plane` and `aws-edge-hosting`): visit while the instance is stopped, observe wake, flip, and a search; `npm run lint && npm test && npm run test:e2e && npm run build` green
