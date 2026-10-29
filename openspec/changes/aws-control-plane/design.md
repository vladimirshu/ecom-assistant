## Context

CloudFront can route a path to a Lambda Function URL, but it cannot start or stop the origin. The SPA is client-only: the page loads from S3 whether or not the backend is up, then evaluates the shared opening schedule locally. Inside hours it probes `GET /api/v1/health` and calls control on failure; outside hours it calls control directly on arrival and before each search, avoiding a stopped-origin timeout. During startup it polls control every 3 seconds and opens immediately on a valid UP response, without client-side backend health polling. The owner chose automatic wake on page visit plus a manual "Knock" button, a fixed 20-minute session outside opening hours, and no heartbeat.

Pricing and free-tier statements below are assumptions to verify (Lambda request and duration allowances, EventBridge Scheduler invocation allowance, DynamoDB always-free 25 provisioned capacity units and 25 GB, CloudFront free plan behavior).

## Goals / Non-Goals

**Goals:**
- Start the instance on demand and report readiness, safely callable from every visitor tab.
- Open the instance on weekdays 08:00-22:00 without any visitor.
- Stop the instance outside that window within 20 minutes of an off-hours wake.
- Keep a record of wakes without any cost.

**Non-Goals:**
- A per-day wake cap: worst case is the instance running 24/7, which is bounded and acceptable (about $8-12/month, assumption). LLM spend is limited by `demo-cost-controls`, not here.
- Heartbeats, idle detection, or session extension while a visitor is active.
- Visit counting (CloudFront logs, `aws-edge-hosting`).
- Authentication of visitors.

## Decisions

**1. `/api/v1/control/up` is a Lambda Function URL behind CloudFront.**
Node.js (arm64) Lambda, `AWS_IAM` auth on the Function URL with a CloudFront origin access control, so only the project's distribution can invoke it. No API Gateway (extra cost and a second origin type).
- Response: always HTTP 200 and `{"state":"UP"}` or `{"state":"STARTING"}`; unexpected AWS errors also answer 200 with `STARTING` after logging, because the SPA treats anything but a valid UP control response as "not open" and has its own stuck timeout. The CloudFront behavior uses a caching-disabled policy.

**2. State derived from EC2, no stored state.**
Algorithm per call: describe the instance; `stopped` → `StartInstances` then STARTING; `pending`/`stopping` → STARTING (a stopping instance is started on a later call, since the SPA nudges again every 3 s); `running` → probe `http://<EIP>:8080/api/v1/health` with a 2 s timeout and the `X-Origin-Verify` header → 200 gives UP, anything else STARTING. Idempotent by construction (`StartInstances` on a running instance is a no-op). The instance id, EIP and secret parameter name come from environment configuration; the secret is read from SSM at cold start.

**3. Schedules in EventBridge Scheduler with an explicit time zone.**
Two recurring schedules (start: Mon-Fri 08:00; stop: daily 22:00) call the EC2 `StartInstances`/`StopInstances` APIs through universal targets, so no Lambda is needed for them. The time zone is a required stack parameter so daylight-saving changes are handled by the service. Export days, from, to and zone from the same deployment configuration to the frontend build; deploy schedule changes with a rebuilt frontend. The opening interval is start-inclusive and end-exclusive in that zone.
- Alternative: cron rules in EventBridge Rules (UTC only, so DST shifts the window twice a year).

**4. Off-hours session by sweeper, based on instance launch time.**
A second Lambda runs every 5 minutes: if the instance is `running`, now is outside the window, and `now - LaunchTime >= 20 minutes`, stop it. The window parameters (days, from, to, zone) are the same stack constants used for the schedules. `LaunchTime` is assumed to be reset on each stop/start cycle (verify). A wake at 21:50 is stopped by the 22:00 schedule, so its session is shorter than 20 minutes; accepted.
- Alternative: a one-time EventBridge schedule created by `/up` at wake time (exact 20 minutes, but one more write permission and cleanup of schedules; rejected for now).
- Alternative: stop from inside the instance with a cron `shutdown` (no AWS API needed, but the window logic would be duplicated on the host).

**5. Wake events in DynamoDB, provisioned capacity.**
Table `wake-events`: partition key `day` (`YYYY-MM-DD`), sort key `ts#requestId`; attributes: state returned, instance state before the call, CloudFront-Viewer-Country, `ref` (value of the `ref` query parameter if the SPA forwards it on the control call), user-agent class (browser/bot/other). TTL attribute about 180 days. Provisioned 1 RCU / 1 WCU (inside the always-free allocation, on-demand mode deliberately avoided because it is not covered). The write is best effort: a failed write never changes the response.

**6. Least privilege.**
Control Lambda: `ec2:DescribeInstances`, `ec2:StartInstances` scoped to the instance ARN, `ssm:GetParameter` on the origin-secret parameter, `dynamodb:PutItem` on the table. Sweeper Lambda: describe and `StopInstances` on the instance. The Scheduler role may start/stop only this instance.

## Risks / Trade-offs

- [Crawlers or scanners that run JavaScript wake the instance] → Each wake costs at most a 20-minute session off-hours; the cost ceiling is the 24/7 case; wake events show it happening; the owner accepted auto-wake.
- [A client repeating `/up` can keep the instance up all night] → Cost bounded as above; if it is observed, add a per-day cap later (the wake-events table has the data).
- [Cold start of the Lambda adds latency to the first call] → Node.js on arm64 with small bundle; the call only starts a 60-90 s boot, so extra hundreds of milliseconds do not matter.
- [Lambda probes a public IP over plain HTTP] → Same trust model as CloudFront-to-origin; the secret header is sent, no sensitive data in the body.
- [Instance state transitions race (stop in progress when the visitor knocks)] → The SPA re-calls `/up` every 3 s; a stopping instance is started by a later call.
- [`StartInstances` can fail with insufficient capacity] → The call logs and answers STARTING; the SPA's 3-minute stuck state tells the visitor.
- [DST edge cases at the window boundary] → Zone-aware schedules; sweeper uses the same zone via the runtime time-zone library.

## Migration Plan

1. Deploy the stack with schedules disabled, invoke `/up` manually against the stopped instance, check STARTING then UP.
2. Enable the schedules; confirm a weekday start at 08:00 and the 22:00 stop for one day.
3. Wake off-hours once and confirm the instance stops about 20 minutes later.
4. Rollback: disable the schedules and Lambdas; the instance can be controlled by hand.

## Open Questions

- Schedule time zone: not yet chosen (required stack parameter, no default).
- IaC tool: CDK (TypeScript) is the working default, Terraform the alternative; not decided by the owner.
- Custom domain: decided later; the control endpoint works on the CloudFront domain.
- Whether the SPA should forward `?ref=` to `/up` (needed for the `ref` attribute of wake events; the same value is already visible in CloudFront logs).
