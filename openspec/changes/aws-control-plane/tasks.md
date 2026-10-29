## 1. Control Lambda

- [ ] 1.1 Implement the control Lambda (Node.js, arm64, TypeScript): describe instance, start when stopped, probe `http://<EIP>:8080/api/v1/health` with 2 s timeout and `X-Origin-Verify`, return `{"state":...}` always with HTTP 200, no-cache headers
- [ ] 1.2 Read the origin secret from SSM at cold start; read instance id, EIP and table name from environment variables; log AWS errors and map them to `STARTING`
- [ ] 1.3 Write the wake event (day, ts, state, instance state before, `CloudFront-Viewer-Country`, `ref`, user-agent class) best effort; no IP, no raw user-agent
- [ ] 1.4 Unit tests with mocked AWS SDK clients: stopped → start called + STARTING; pending/stopping → STARTING without start (stopping) ; running + 200 → UP; running + timeout/503 → STARTING; AWS error → 200 STARTING; DynamoDB failure does not change the response

## 2. Schedules and sweeper

- [ ] 2.1 Define EventBridge Scheduler start (Mon-Fri 08:00) and stop (daily 22:00) with universal targets for `StartInstances`/`StopInstances`, a dedicated role limited to the instance, and the required time-zone parameter (fail the deployment when it is missing)
- [ ] 2.2 Implement the sweeper Lambda (every 5 minutes): stop when running, outside the window (same constants as the schedules, same zone) and uptime from `LaunchTime` is at least 20 minutes
- [ ] 2.3 Unit tests for the window logic with a fixed clock: weekday inside/outside, weekend, DST change day, 19 vs 20 minutes of uptime, 21:50 start
- [ ] 2.4 Verify that `LaunchTime` changes after each stop/start cycle; if not, switch to a one-time schedule per wake (document in design)
- [ ] 2.5 Generate frontend `VITE_OPEN_TZ`, `VITE_OPEN_DAYS`, `VITE_OPEN_FROM`, `VITE_OPEN_TO` from the same deployment configuration as Scheduler and sweeper; validate them and document rebuilding/redeploying the frontend with schedule changes

## 3. Data and wiring

- [ ] 3.1 Create the `wake-events` table: key `day` / `ts#requestId`, provisioned 1 RCU / 1 WCU, TTL attribute (about 180 days); check the capacity against the current always-free allowance
- [ ] 3.2 Create the Function URL with `AWS_IAM` auth and the least-privilege IAM roles from the design; export the Function URL for `aws-edge-hosting`
- [ ] 3.3 Confirm the origin-secret parameter name and the instance id/EIP exports from `aws-backend-host`

## 4. Verification

- [ ] 4.1 Calls from the CloudFront domain: stopped → STARTING, later UP, repeated calls safe, direct Function URL call rejected
- [ ] 4.2 Observe one full weekday (08:00 start, 22:00 stop), one weekend wake with the 20-minute stop, and the DynamoDB events

## 5. Runbook and cost

- [ ] 5.1 Add to `infra/RUNBOOK.md`: how to disable the schedules, start/stop by hand, query wake events, change the window or zone
- [ ] 5.2 Cost verification after the first week: Cost Explorer lines for Lambda, EventBridge, DynamoDB and EC2 hours versus the 24/7 ceiling; confirm Budgets alerts from `aws-backend-host` fire at the expected levels
