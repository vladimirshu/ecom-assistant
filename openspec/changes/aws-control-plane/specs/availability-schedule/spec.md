## ADDED Requirements

### Requirement: Weekday opening window
The system SHALL start the backend instance every Monday to Friday at 08:00 and stop it every day at 22:00, both evaluated in a configured IANA time zone that follows daylight-saving changes. The time zone SHALL be a required deployment parameter with no default.

#### Scenario: Weekday morning
- **WHEN** it is 08:00 on a Wednesday in the configured zone
- **THEN** the instance is started if it is not already running

#### Scenario: Evening
- **WHEN** it is 22:00 on any day in the configured zone
- **THEN** the instance is stopped if it is running

#### Scenario: Weekend
- **WHEN** it is Saturday or Sunday and nobody has woken the instance
- **THEN** the instance stays stopped

#### Scenario: Missing time zone
- **WHEN** the stack is deployed without a time zone parameter
- **THEN** the deployment fails with a clear message

### Requirement: Twenty-minute off-hours session
When the instance is running outside the opening window, the system SHALL stop it once it has been running for at least 20 minutes, checking at least every 5 minutes. The opening window used by this check SHALL be the same configuration as the schedules.

#### Scenario: Wake at night
- **WHEN** a visitor wakes the instance at 23:00 and nobody else acts
- **THEN** the instance is stopped between 20 and 25 minutes after it started

#### Scenario: Wake during opening hours
- **WHEN** the instance is running inside the opening window for any length of time
- **THEN** the sweeper does not stop it

#### Scenario: Wake shortly before closing
- **WHEN** the instance is woken at 21:50 on a weekday
- **THEN** it is stopped by the 22:00 schedule

#### Scenario: Wake again after a session
- **WHEN** a visitor wakes the instance again after the 20-minute session ended
- **THEN** a new 20-minute session starts from the new launch time

### Requirement: No heartbeat dependency
Starting and stopping SHALL NOT depend on any signal from visitors other than calls to the wake endpoint.

#### Scenario: Active visitor at the end of a session
- **WHEN** a visitor is searching when the 20-minute session expires
- **THEN** the instance is stopped regardless

### Requirement: Shared frontend schedule
The deployment SHALL generate the frontend opening days, times and IANA time zone from the same configuration used by the schedules and sweeper. The opening interval SHALL include its start and exclude its end in that zone. Schedule changes SHALL include rebuilding and deploying the frontend with the updated configuration.

#### Scenario: Matching configuration
- **WHEN** the stack and frontend are built for a deployment
- **THEN** their opening days, times and IANA time zone match, including daylight-saving rules

#### Scenario: Schedule change
- **WHEN** the opening window or time zone changes
- **THEN** the corresponding frontend build receives the updated configuration and is deployed with the schedule change
