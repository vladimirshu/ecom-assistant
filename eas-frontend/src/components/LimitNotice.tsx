import { useEffect, useState } from 'react'

/**
 * Usage-limit feedback. RATE_LIMITED offers a "Try again" action enabled after
 * Retry-After seconds; DAILY_BUDGET_EXHAUSTED offers no retry and catalog browsing
 * remains available.
 */
export function LimitNotice({
  code,
  retryAfterSeconds,
  onRetry,
}: {
  code: 'RATE_LIMITED' | 'DAILY_BUDGET_EXHAUSTED'
  retryAfterSeconds: number | null
  onRetry?: () => void
}) {
  const [remaining, setRemaining] = useState(retryAfterSeconds ?? 0)

  useEffect(() => {
    setRemaining(retryAfterSeconds ?? 0)
    if (!retryAfterSeconds) return
    const id = window.setInterval(() => setRemaining((s) => Math.max(0, s - 1)), 1000)
    return () => window.clearInterval(id)
  }, [retryAfterSeconds])

  if (code === 'DAILY_BUDGET_EXHAUSTED') {
    return (
      <div className="rounded-sm border border-border bg-paper px-4 py-4">
        <p className="text-ink">Today's search demo quota is used up.</p>
        <p className="mt-1 text-sm text-muted">Search can be retried tomorrow. You can keep browsing the catalog.</p>
      </div>
    )
  }

  return (
    <div className="rounded-sm border border-border bg-paper px-4 py-4">
      <p className="text-ink">That was a little too fast.</p>
      <p className="mt-1 text-sm text-muted">
        {remaining > 0 ? `Please wait about ${remaining} seconds and try again.` : 'You can try again now.'}
      </p>
      {onRetry && (
        <button
          type="button"
          disabled={remaining > 0}
          onClick={onRetry}
          className="mt-3 rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 disabled:cursor-not-allowed disabled:opacity-50 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
        >
          Try again
        </button>
      )}
    </div>
  )
}
