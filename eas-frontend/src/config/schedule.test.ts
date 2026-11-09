import { describe, expect, it } from 'vitest'
import { loadSchedule, scheduleSettings } from './schedule'

describe('schedule configuration', () => {
  it('is valid and parses into opening hours', () => {
    expect(loadSchedule()).not.toBeNull()
  })

  it('opens Mon–Fri, 08:00–22:00 in Europe/Berlin', () => {
    const config = loadSchedule()
    expect(config).not.toBeNull()
    if (!config) return
    expect(config.tz).toBe('Europe/Berlin')
    expect(config.days).toEqual(['mon', 'tue', 'wed', 'thu', 'fri'])
    expect(config.fromMinutes).toBe(480)
    expect(config.toMinutes).toBe(1320)
  })

  it('exposes the raw settings in the config file', () => {
    expect(scheduleSettings.tz).toBe('Europe/Berlin')
    expect(scheduleSettings.from).toBe('08:00')
    expect(scheduleSettings.to).toBe('22:00')
  })
})
