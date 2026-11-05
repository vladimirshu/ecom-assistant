export function RejectionNotice({
  suggestions,
  onSelect,
}: {
  suggestions: string[]
  onSelect: (prompt: string) => void
}) {
  return (
    <div className="rounded-sm border border-border bg-paper px-4 py-4">
      <p className="text-ink">Let's try that another way</p>
      <ul className="mt-2 flex list-none flex-wrap gap-2 p-0">
        {suggestions.map((prompt) => (
          <li key={prompt}>
            <button
              type="button"
              onClick={() => onSelect(prompt)}
              className="rounded-full border border-action px-3 py-1 text-sm text-action hover:bg-wash/50 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
            >
              {prompt}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
