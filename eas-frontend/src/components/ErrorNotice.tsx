export function ErrorNotice({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="rounded-sm border border-border bg-paper px-4 py-4">
      <p className="text-ink">Something went wrong on our side.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-3 rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
      >
        Try again
      </button>
    </div>
  )
}
