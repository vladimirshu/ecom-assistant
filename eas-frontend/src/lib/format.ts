/** Format a EUR price, preserving fractional values and dropping `.00` on whole numbers. */
export function formatPrice(value: number): string {
  if (!Number.isFinite(value)) return '€—'
  const fixed = value.toFixed(2)
  const trimmed = fixed.endsWith('.00') ? fixed.slice(0, -3) : fixed
  return `€${trimmed}`
}

import type { PriceConstraint } from './api/types'

/** Wording for a price-constraint interpretation chip. */
export function priceConstraintLabel(constraint: PriceConstraint): string {
  const amount = formatPrice(constraint.value)
  switch (constraint.operator) {
    case 'LESS_THAN':
      return `Under ${amount}`
    case 'LESS_THAN_OR_EQUAL':
      return `Up to ${amount}`
    case 'GREATER_THAN':
      return `Over ${amount}`
    case 'GREATER_THAN_OR_EQUAL':
      return `From ${amount}`
  }
}
