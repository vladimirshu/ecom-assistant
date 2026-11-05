import { describe, expect, it } from 'vitest'
import { validateMessage } from './validation'

describe('validateMessage', () => {
  it('rejects messages shorter than 3 characters', () => {
    expect(validateMessage('ab').ok).toBe(false)
  })

  it('rejects angle brackets', () => {
    expect(validateMessage('a <jacket>').ok).toBe(false)
  })

  it('rejects messages longer than 500 characters', () => {
    expect(validateMessage('x'.repeat(501)).ok).toBe(false)
  })

  it('trims and accepts valid input', () => {
    expect(validateMessage('  rain jacket  ')).toEqual({ ok: true, value: 'rain jacket' })
  })

  it('allows non-Latin text', () => {
    expect(validateMessage('зимняя куртка до 200 евро')).toEqual({
      ok: true,
      value: 'зимняя куртка до 200 евро',
    })
  })
})
