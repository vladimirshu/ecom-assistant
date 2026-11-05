import { describe, expect, it } from 'vitest'
import {
  isWithinOpeningHours,
  parseSchedule,
  parseTime,
  zonedParts,
} from './schedule'

const config = () => {
  const result = parseSchedule({
    tz: 'Europe/Berlin',
    days: 'mon,tue,wed,thu,fri',
    from: '08:00',
    to: '22:00',
  })
  if (!result.ok) throw new Error(result.error)
  return result.config
}

describe('schedule parsing', () => {
  it('parses a valid schedule', () => {
    expect(config().days).toEqual(['mon', 'tue', 'wed', 'thu', 'fri'])
    expect(config().fromMinutes).toBe(480)
    expect(config().toMinutes).toBe(1320)
  })

  it('rejects an invalid time zone', () => {
    expect(parseSchedule({ tz: 'Not/AZone', days: 'mon', from: '08:00', to: '22:00' }).ok).toBe(false)
  })

  it('rejects invalid times', () => {
    expect(parseSchedule({ tz: 'Europe/Berlin', days: 'mon', from: '25:00', to: '22:00' }).ok).toBe(false)
  })

  it('rejects an empty day list', () => {
    expect(parseSchedule({ tz: 'Europe/Berlin', days: '', from: '08:00', to: '22:00' }).ok).toBe(false)
  })

  it('parses times', () => {
    expect(parseTime('08:00')).toBe(480)
    expect(parseTime('22:00')).toBe(1320)
    expect(parseTime('bad')).toBeNull()
  })
})

describe('opening window', () => {
  // 2026-10-30 is a Friday; Europe/Berlin is on CET (UTC+1) by then.
  it('opens at the opening instant and closes at the closing instant', () => {
    const open = new Date('2026-10-30T07:00:00Z') // 08:00 Berlin
    const close = new Date('2026-10-30T21:00:00Z') // 22:00 Berlin
    const before = new Date('2026-10-30T06:59:00Z') // 07:59 Berlin
    expect(isWithinOpeningHours(open, config())).toBe(true)
    expect(isWithinOpeningHours(close, config())).toBe(false)
    expect(isWithinOpeningHours(before, config())).toBe(false)
  })

  it('is closed on weekends in the configured zone', () => {
    // Saturday 2026-10-31 at 12:00 Berlin == 11:00 UTC.
    const saturday = new Date('2026-10-31T11:00:00Z')
    expect(isWithinOpeningHours(saturday, config())).toBe(false)
  })

  it('evaluates using the configured zone regardless of the local offset', () => {
    // 08:00 Berlin == 07:00 UTC during winter time.
    const utcView = new Date('2026-10-30T07:00:00Z')
    expect(isWithinOpeningHours(utcView, config())).toBe(true)
  })

  it('returns zoned parts', () => {
    const parts = zonedParts(new Date('2026-10-30T07:00:00Z'), 'Europe/Berlin')
    expect(parts.dayName).toBe('fri')
    expect(parts.hours).toBe(8)
    expect(parts.minutes).toBe(0)
  })
})
