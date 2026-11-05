import { describe, expect, it } from 'vitest'
import { formatPrice, priceConstraintLabel } from './format'

describe('formatPrice', () => {
  it('preserves fractional EUR values', () => {
    expect(formatPrice(262.73)).toBe('€262.73')
  })

  it('drops .00 on whole numbers', () => {
    expect(formatPrice(150)).toBe('€150')
  })

  it('keeps trailing zeros for one decimal place', () => {
    expect(formatPrice(99.5)).toBe('€99.50')
  })
})

describe('priceConstraintLabel', () => {
  it('maps operators to the approved wording', () => {
    expect(priceConstraintLabel({ operator: 'LESS_THAN', value: 200 })).toBe('Under €200')
    expect(priceConstraintLabel({ operator: 'LESS_THAN_OR_EQUAL', value: 150 })).toBe('Up to €150')
    expect(priceConstraintLabel({ operator: 'GREATER_THAN', value: 99.5 })).toBe('Over €99.50')
    expect(priceConstraintLabel({ operator: 'GREATER_THAN_OR_EQUAL', value: 20 })).toBe('From €20')
  })
})
