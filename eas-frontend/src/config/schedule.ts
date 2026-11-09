import { parseSchedule, type ScheduleConfig } from '../gate/schedule'

/**
 * Service availability schedule.
 *
 * This is the single source of truth for the storefront's opening hours and
 * lives in code rather than environment variables. Changing these values
 * requires a frontend rebuild.
 */
export const scheduleSettings = {
  /** IANA time zone (e.g. `Europe/Berlin`). */
  tz: 'Europe/Berlin',
  /** Comma-separated lowercase three-letter day names. */
  days: 'mon,tue,wed,thu,fri',
  /** Opening time, 24-hour `HH:MM`. */
  from: '08:00',
  /** Closing time, 24-hour `HH:MM`. */
  to: '22:00',
} as const

/** Parsed schedule, or `null` when the configured values are invalid. */
export function loadSchedule(): ScheduleConfig | null {
  const result = parseSchedule(scheduleSettings)
  return result.ok ? result.config : null
}
