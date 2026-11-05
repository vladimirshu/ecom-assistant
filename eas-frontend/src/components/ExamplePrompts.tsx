import type { ExamplePrompt } from '../lib/examples'

export function ExamplePrompts({
  examples,
  onSelect,
}: {
  examples: ExamplePrompt[]
  onSelect: (message: string) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-sm text-muted">Try:</span>
      {examples.map((example) => (
        <button
          key={example.message}
          type="button"
          onClick={() => onSelect(example.message)}
          className="rounded-full border border-border bg-paper px-3 py-1 text-sm text-ink hover:border-action hover:text-action focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
        >
          {example.label}
        </button>
      ))}
    </div>
  )
}
