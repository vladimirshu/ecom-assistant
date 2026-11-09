import { useEffect, useState } from 'react'

/**
 * Usage-limit feedback. RATE_LIMITED offers a "Try again" action enabled after
 * Retry-After seconds; DAILY_BUDGET_EXHAUSTED offers no retry and catalog browsing
 * remains available.
 */
export function LimitNotice({
  code,
  retryAt,
  onRetry,
}: {
  code: 'RATE_LIMITED' | 'DAILY_BUDGET_EXHAUSTED'
  retryAt?: number
  onRetry?: () => void
}) {
  const effectiveDeadline = retryAt ?? 0
  const [remaining, setRemaining] = useState(() => Math.max(0, Math.ceil((effectiveDeadline - Date.now()) / 1000)))

  useEffect(() => {
    const update = () => setRemaining(Math.max(0, Math.ceil((effectiveDeadline - Date.now()) / 1000)))
    update()
    const id = window.setInterval(update, 1000)
    return () => window.clearInterval(id)
  }, [effectiveDeadline])

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
      {onRetry && <p className="mt-1 text-sm text-muted">
        {remaining > 0 ? `Please wait about ${remaining} seconds and try again.` : 'You can try again now.'}
      </p>}
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
