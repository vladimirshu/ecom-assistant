## Why

To keep cost near zero, the backend instance runs only on weekdays from 08:00 to 22:00 and is otherwise stopped. A visitor arriving while it is stopped must be able to wake it, and the owner wants to know who woke it. The page's JavaScript can only reach the backend through CloudFront, which cannot start a stopped instance, so a small always-on control plane (a Lambda) is needed. It must cost nothing when idle and must bound the cost when someone keeps waking the instance.

## What Changes

- Add `GET /api/v1/control/up`: an idempotent Lambda endpoint that starts the EC2 instance if it is stopped and answers `{"state":"UP"|"STARTING"}` (always HTTP 200). UP only when the instance is running and its readiness endpoint answers 200.
- Add EventBridge Scheduler rules in a configurable time zone: start Monday to Friday 08:00, stop every day 22:00.
- Add a sweeper (every 5 minutes) that stops the instance when it is outside the open window and has been running for at least 20 minutes, so an off-hours wake lasts 20 minutes.
- Record every `/up` call as a wake event in a DynamoDB table sized within the always-free tier, with TTL expiry.
- The SPA sends no heartbeat; the Lambda is called only when the SPA's health probe fails.

## Capabilities

### New Capabilities
- `service-wake-control`: the `/api/v1/control/up` contract, idempotency, UP/STARTING decision, access restriction.
- `availability-schedule`: weekday open window, daily stop, and the 20-minute off-hours session.
- `wake-event-statistics`: wake events stored for later analysis, free-tier sizing and retention.

### Modified Capabilities
<!-- none -->

## Impact

- New infrastructure code and two Lambda functions (control, sweeper) plus two schedules, one DynamoDB table.
- Depends on: `aws-backend-host` (instance id, Elastic IP, origin-verify secret), `aws-edge-hosting` (route `/api/v1/control/*` to the Lambda Function URL), `api-versioned-search-path` (`/api/v1/health`).
- Cross-project contract: the SPA (`service-availability-gate`) relies on the exact JSON shape and on `/up` being safe to call repeatedly.
- Cost: Lambda, EventBridge Scheduler and DynamoDB are expected to stay within free allowances at this traffic (assumptions to verify); the real cost driver is instance hours, bounded by 24/7 operation.
