## 1. Clients and schedule

- [ ] 1.1 Implement health probe (2.5 s timeout, 2xx/429 healthy), typed control UP/STARTING parsing and abort/stale-response handling; MSW checks for invalid bodies, network, timeout and HTTP failures
- [ ] 1.2 Generate and validate VITE_OPEN_TZ/DAYS/FROM/TO from shared deployment configuration; implement zone/DST-aware opening tests and visitor-zone hours formatting
- [ ] 1.3 Test opening/closing boundaries, weekends and weekday shifts for Berlin, New York and Tokyo; document rebuild requirements and local development mode

## 2. Readiness state machine

- [ ] 2.1 Implement CHECKING/CLOSED/WAKING/HEALTHY/STUCK, automatic initial routing, direct readiness on valid UP, 3 s non-overlapping control polling, 180 s timeout and timer cleanup; no OPENED flip delay or heartbeat
- [ ] 2.2 Implement manual Start demo/control check, disabled Starting… while active, one-second debounce and explicit Try again from STUCK
- [ ] 2.3 Test in-hours healthy loads without control, off-hours control-first routing without health requests, failed/invalid control, stop on UP/timeout, retry window reset and ignored stale results

## 3. Sage panel and integration

- [ ] 3.1 Implement non-modal AvailabilityPanel with approved resting/starting/failure copy, shared Sage styling, local hours, elapsed time and accessible actions; remove door board, flip and knock concepts
- [ ] 3.2 Implement 4 s intent-message rotation, 12 s announcement throttle and reduced-motion static treatment; preserve focus or return it to search when the focused panel action disappears
- [ ] 3.3 Gate search and product requests while preserving drafts, snapshots, history, category and loaded products; keep local navigation, demonstration playback and product dialogs usable
- [ ] 3.4 Wire pre-request schedule checks and request-failure reporting; never wake on search 429 or automatically replay a blocked search; initial catalog may resume automatically, interrupted append requires explicit action

## 4. Verification and handoff

- [ ] 4.1 Add development-only health/control fixtures and optional state preview controls, excluded from production; test that production cannot bypass readiness
- [ ] 4.2 Playwright flows for off-hours visit/startup, invalid control, stuck/retry, service loss, closing-time crossing, preserved draft/category/history, static demo while asleep, 429, no heartbeat and campaign tag preservation
- [ ] 4.3 Check 390 px and 1440 px layouts, keyboard access to static content while waiting, focus on panel removal, polite announcements and axe-core accessibility
- [ ] 4.4 Document runtime behavior and manually verify against deployed control/backend paths after infrastructure is ready; run lint, unit, E2E and production build checks
