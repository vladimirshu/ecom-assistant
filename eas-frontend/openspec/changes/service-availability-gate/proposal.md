## Why

The backend runs on a small EC2 instance that is up only on weekdays from 08:00 to 22:00 (plus 20-minute sessions started on demand outside those hours). Outside that window the static storefront still loads from S3, but every API call fails. A visitor, often a hiring manager opening a link, must not see a broken page: the storefront has to notice that the service is asleep, wake it, and explain what is happening while it starts.

## What Changes

- Add an availability gate to the storefront. It renders nothing while the service is healthy. When the service is not, it shows a shop door sign ("Closed") over the page, wakes the service, and tells the visitor it is opening.
- Evaluate the shared opening schedule locally on page load and before search submission. During opening hours, probe `GET /api/v1/health` (2.5 s timeout); outside opening hours, call the Lambda endpoint `GET /api/v1/control/up` immediately, before any backend health or search request. A valid `UP` confirms readiness directly; `STARTING` shows opening progress.
- While the service starts, the sign shows rotating light-hearted messages ("Waking up the assistant…", "Starting AWS services…", "Preparing the shop…") and an elapsed timer; the gate polls the idempotent `/up` endpoint every 3 s until `UP`, without backend health polling.
- A "Knock to wake us" button is always available while the shop is closed: it plays a knock animation and calls `/up` immediately to check readiness and wake the backend if needed.
- On the first valid `UP` control response the sign flips from "Closed" to "Open" once, then disappears.
- If the service is not healthy 3 minutes after the first wake call, show a "stuck" state with a "Try again" action.
- If a later search fails because the service went away (business hours ended, 20-minute session over), the gate returns and the customer's typed text is preserved.
- Opening hours are rendered in the visitor's local time zone from build-time configuration.
- Accessibility: live status announcements, keyboard operable button, reduced-motion alternative to the flip.

## Capabilities

### New Capabilities
- `service-availability-gate`: health probing, wake-up call, door sign states and messages, knock action, stuck state, runtime loss handling, hours display and accessibility.

### Modified Capabilities
<!-- none: the hook-up in `conversational-search` (failure reporting, 429 not treated as asleep) is specified in change `storefront-search-ui` -->

## Impact

- Code (new, in `eas-frontend/`): `src/gate/` (state machine hook, health and wake clients, door sign component, messages, hours formatting), mounted once around the storefront in `App.tsx`; no changes to search components beyond reporting failures.
- Configuration (build time, availability routing and hours display): `VITE_OPEN_TZ`, `VITE_OPEN_DAYS`, `VITE_OPEN_FROM`, `VITE_OPEN_TO`. The schedule itself lives in the infrastructure (`aws-control-plane`); the frontend values are generated from that same deployment configuration, and invalid or missing values fail the production build.
- API consumed: `GET /api/v1/health` (backend change `api-versioned-search-path`, capability `service-health`) and `GET /api/v1/control/up` returning `{"state": "UP" | "STARTING"}` (root change `aws-control-plane`, capability `service-wake-control`). In development both are served by a local mock or the local backend (`/up` is not available locally; see design).
- Statistics: the page URL, including a `?ref=<company>` campaign tag, stays untouched so CloudFront logs record it; the gate makes no extra statistics calls.
- Depends on: `storefront-search-ui` (the SPA it wraps). Out of scope: static pre-recorded example searches shown while the service is asleep (possible follow-up).
