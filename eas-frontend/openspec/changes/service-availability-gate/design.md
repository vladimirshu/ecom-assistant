## Context

The static SPA is always available from S3/CloudFront. Backend search and catalog requests require the scheduled EC2 service; the Lambda control path is always on. The chosen Sage design replaces the earlier decorative door sign with a quiet status panel. Runtime readiness and wake policies remain unchanged.

- `GET /api/v1/health`: in-hours readiness probe with a 2.5 s timeout; 2xx and 429 mean reachable, network/timeout/5xx and other 4xx mean unhealthy.
- `GET /api/v1/control/up`: valid JSON `{ state: "UP" | "STARTING" }`, always checked before any backend request outside opening hours. HTTP 200 alone is insufficient.
- Search HTTP 429 never triggers this gate.

## Goals / Non-Goals

Goals: schedule-aware readiness; immediate automatic wake when necessary; calm Sage waiting/error states; preserve work; allow static explanation, examples and local navigation while waiting.

Non-goals: change infrastructure schedules or the off-hours session length; background heartbeat; add control APIs; show a shutdown countdown unsupported by the API; copy simulated prototype timers into production.

## Decisions

### 1. Readiness state machine

`useAvailabilityGate` keeps CHECKING, CLOSED, WAKING, HEALTHY and STUCK phases. Evaluate the deployment schedule on load and before each backend operation. In-hours initial checks use health; off-hours checks use control first. CLOSED immediately starts the control cycle without needing Start demo. STARTING or failed/invalid control calls enter/continue WAKING. Poll control only, every 3 s without overlaps, until valid UP or 180 s since the first wake call. Healthy state makes no further polling or heartbeat. Ignore obsolete responses and cancel timers on retry/unmount. The earlier OPENED/door-flip delay is removed.

### 2. Non-modal Sage status panel

Use the `storefront-search-ui` theme and Header. Present AvailabilityPanel within the shell, alongside the page's explanation rather than as an inert-page overlay. Sleep/checking: A little rest for the demo.; waking: Getting the shop ready.; stuck: The demo couldn’t start. Include local opening hours, elapsed time, honest status copy and the appropriate action. Search submission and product fetching are gated; drafts, bundled demonstration playback, local category navigation and already loaded product dialogs remain usable.

Start demo requests an immediate control check when none is active. While starting, show disabled Starting…. Automatic wake remains the default on arrival. Debounce manual requests for one second. STUCK stops polling and offers Try again, resetting the 180 s window. Do not use 3D flips, knock effects, infrastructure-specific UI copy or a modal focus trap.

### 3. Status copy, motion and focus

Rotate the client-timed list every 4 s: Waking up the assistant…, Preparing the shop…, Checking the collection…. The wording describes intent, not completed backend pipeline stages. Show elapsed m:ss without announcing every second. Use one polite live region and throttle message announcements to 12 s. Reduced motion uses a static icon and text. Keep ordinary page focus; if a focused startup action is removed, focus the relevant search action.

### 4. Shared schedule configuration

Use VITE_OPEN_TZ, VITE_OPEN_DAYS, VITE_OPEN_FROM and VITE_OPEN_TO generated from the same deployment configuration as infrastructure. Validate production builds. Evaluate weekday/time in the configured IANA zone with DST, opening inclusive and closing exclusive; convert hours and weekday shifts to the visitor's zone for display. A schedule change requires a frontend rebuild. Control remains authoritative when called, including for old tabs or incorrect local clocks.

### 5. Runtime loss and catalog integration

Before every off-hours search/catalog request, confirm UP with control. STARTING/failure blocks that request and preserves draft, history, result snapshot, category and loaded cards. A network/timeout/5xx failure checks health inside hours and control outside; confirmed readiness leaves the normal inline request error. Otherwise show the panel and restart control polling. Never automatically replay a search after startup. Initial catalog loading may proceed once ready; interrupted pagination requires explicit Load more/Try again. Category configuration is local and renders without readiness.

### 6. Demonstration and development

The local search demonstration is implemented in `storefront-search-ui`; it has no API calls and does not depend on readiness. It can play while the panel is visible. Development uses explicit always-available mode or MSW control/health fixtures. Any dev-only state selector, `?gate=closed` or prototype state-switching toolbar must be absent from production behavior. Campaign query parameters remain unchanged by the gate.

## Risks / Trade-offs

- Automatic wake on visit can wake the service for JavaScript-enabled scanners; infrastructure bounds its cost.
- A 60–90 s start is noticeable; static explanation and examples remain usable without implying readiness.
- A backend may stop just after UP; preserve work and re-enter the same recovery flow on failure.
- Deployment and frontend schedules can drift; generate both together and test DST/boundaries.
- The MagicPath availability frame is a visual reference, not a readiness implementation.
