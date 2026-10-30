## Why

A visitor must understand when the demo backend is starting and retain their work if it stops. The approved Sage design calls for calm availability feedback while the static explanation and search example remain usable.

## What Changes

- Add schedule-aware readiness and automatic wake using the existing health/control contracts.
- Replace the earlier Closed/Open door board and knock effects with a non-modal Sage AvailabilityPanel: resting/checking, starting and failed/stuck states.
- Gate backend search/product operations while retaining draft text, conversation, result selection, categories and loaded cards. Static demonstration, navigation and local product inspection remain usable.
- Keep 3 s control-only polling, no overlapping calls, 180 s timeout, explicit retry and no heartbeat.
- Preserve automatic wake on arrival. Start demo is a manual control action, Starting… is disabled while active, and stuck startup offers Try again.
- Keep local-zone opening hours and accessible, reduced-motion status feedback. Remove the modal focus trap and decorative flip delay.

## Capabilities

### New Capabilities
- `service-availability-gate`: schedule-aware checks, auto-wake, Sage availability states, guarded API access, recovery, hours display and accessibility.

### Modified Capabilities
None; integration with search, catalog and demonstration is coordinated with `storefront-search-ui`.

## Impact

- New frontend `src/gate/` clients, schedule helpers, state machine, AvailabilityPanel and status messages; no application code is added by this specification update.
- Consumes GET /api/v1/health and GET /api/v1/control/up with UP/STARTING. No API changes.
- Build-time VITE_OPEN_TZ/DAYS/FROM/TO come from deployment configuration shared with infrastructure.
- Depends on the frontend shell and root `aws-control-plane`/`aws-edge-hosting` deployment changes. Does not alter schedules, session length or analytics behavior.
