import type { Interpretation } from '../lib/api/types'
import { InterpretationBar } from './InterpretationBar'
import type { ExamplePrompt } from '../lib/examples'

export function EmptyResults({
  interpretation,
  examples,
  onSelectExample,
}: {
  interpretation: Interpretation
  examples: ExamplePrompt[]
  onSelectExample: (message: string) => void
}) {
  return (
    <div className="rounded-sm border border-border bg-paper px-4 py-4">
      <p className="text-ink">Nothing matched that description.</p>
      {interpretation && (
        <div className="mt-3">
          <InterpretationBar interpretation={interpretation} />
        </div>
      )}
      <p className="mt-3 text-sm text-muted">You might try one of these instead:</p>
      <ul className="mt-2 flex list-none flex-wrap gap-2 p-0">
        {examples.map((example) => (
          <li key={example.message}>
            <button
              type="button"
              onClick={() => onSelectExample(example.message)}
              className="rounded-full border border-action px-3 py-1 text-sm text-action hover:bg-wash/50 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
            >
              {example.label}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
