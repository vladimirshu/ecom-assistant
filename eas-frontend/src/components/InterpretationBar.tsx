import type { Interpretation } from '../lib/api/types'
import { priceConstraintLabel } from '../lib/format'

export function InterpretationBar({ interpretation }: { interpretation: Interpretation }) {
  const chips: { key: string; label: string }[] = []
  if (interpretation.searchQuery) chips.push({ key: 'query', label: interpretation.searchQuery })
  interpretation.negativeExamples.forEach((n) => chips.push({ key: `neg-${n}`, label: `No ${n}` }))
  if (interpretation.priceConstraint) {
    chips.push({ key: 'price', label: priceConstraintLabel(interpretation.priceConstraint) })
  }
  if (interpretation.sortByLowestPrice) chips.push({ key: 'sort', label: 'Lowest price first' })

  if (chips.length === 0) return null

  return (
    <div className="rounded-sm border border-border bg-wash/40 px-4 py-3">
      <p className="text-xs uppercase tracking-wide text-ink">We understood</p>
      <ul className="mt-1 flex list-none flex-wrap gap-2 p-0">
        {chips.map((chip) => (
          <li key={chip.key} className="rounded-full border border-border bg-paper px-3 py-1 text-sm text-ink">
            {chip.label}
          </li>
        ))}
      </ul>
    </div>
  )
}
