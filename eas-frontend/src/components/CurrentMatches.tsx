import type { Turn } from '../session/useSearchSession'
import { EXAMPLE_PROMPTS } from '../lib/examples'
import type { Product } from '../lib/api/types'
import { InterpretationBar } from './InterpretationBar'
import { ProductGrid } from './ProductGrid'
import { LoadingStory } from './LoadingStory'
import { RejectionNotice } from './RejectionNotice'
import { LimitNotice } from './LimitNotice'
import { ErrorNotice } from './ErrorNotice'
import { EmptyResults } from './EmptyResults'

function lastSuccessfulTurn(turns: Turn[]): Turn | null {
  for (let i = turns.length - 1; i >= 0; i -= 1) {
    const t = turns[i]
    if (t.status === 'success' || t.status === 'empty') return t
  }
  return null
}

function isFailure(status: Turn['status']): boolean {
  return status === 'rejected' || status === 'rateLimited' || status === 'budgetExhausted' || status === 'error'
}

export function CurrentMatches({
  turns,
  activeTurnId,
  newMatchesAvailable,
  onReturnToLatest,
  onAcceptSuggestion,
  onRetry,
  onOpenProduct,
}: {
  turns: Turn[]
  activeTurnId: string | null
  newMatchesAvailable: boolean
  onReturnToLatest: () => void
  onAcceptSuggestion: (prompt: string) => void
  onRetry: (turnId: string) => void
  onOpenProduct: (product: Product) => void
}) {
  const activeTurn = turns.find((t) => t.id === activeTurnId) ?? null
  const latest = lastSuccessfulTurn(turns)
  const retained = activeTurn && isFailure(activeTurn.status)
    ? lastSuccessfulTurn(turns.slice(0, turns.indexOf(activeTurn)))
    : null
  const retryActive = activeTurn && activeTurn === turns.at(-1)
    ? () => onRetry(activeTurn.id)
    : undefined

  const isLatest = latest !== null && activeTurn?.id === latest.id
  const isEarlier = activeTurn?.status === 'success' || activeTurn?.status === 'empty'
  const announcement = newMatchesAvailable ? 'New matches available' : {
    success: `${activeTurn?.products.length ?? 0} matches returned`,
    empty: 'Nothing matched that description',
    rejected: 'Search needs a different description',
    rateLimited: 'Search rate limit reached',
    budgetExhausted: 'Daily search quota reached',
    error: 'Search failed',
    pending: 'Search in progress',
  }[activeTurn?.status ?? 'pending']

  return (
    <section aria-label="Selected matches" className="flex flex-col gap-3">
      <p className="sr-only" role="status">{activeTurn ? announcement : ''}</p>
      {!activeTurn ? <p className="text-muted">Your matches will appear here.</p> : <>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-serif text-lg text-ink">
            {activeTurn.status === 'success'
              ? activeTurn.products.length === 1
                ? '1 match'
                : `${activeTurn.products.length} matches`
              : isEarlier
                ? 'Earlier request'
                : 'Results'}
          </h2>
          <div className="flex gap-3">
            {newMatchesAvailable && (
              <button
                type="button"
                onClick={onReturnToLatest}
                className="text-sm text-action underline-offset-2 hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
              >
                New matches available
              </button>
            )}
            {isEarlier && !isLatest && !newMatchesAvailable && (
              <button
                type="button"
                onClick={onReturnToLatest}
                className="text-sm text-action underline-offset-2 hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
              >
                Return to latest matches
              </button>
            )}
          </div>
        </div>

        <p className="text-sm text-muted">Request: {activeTurn.customerText}</p>
        {isEarlier && !isLatest && <p className="text-sm text-muted">Earlier request</p>}

        {activeTurn.status === 'pending' && <LoadingStory resetKey={activeTurn.id} />}

        {activeTurn.status === 'success' && activeTurn.interpretation && (
          <InterpretationBar interpretation={activeTurn.interpretation} />
        )}
        {activeTurn.status === 'success' && (
          <ProductGrid products={activeTurn.products} variant="search" onOpen={onOpenProduct} />
        )}

        {activeTurn.status === 'empty' && activeTurn.interpretation && (
          <EmptyResults
            interpretation={activeTurn.interpretation}
            examples={EXAMPLE_PROMPTS}
            onSelectExample={onAcceptSuggestion}
          />
        )}

        {activeTurn.status === 'rejected' && (
          <RejectionNotice suggestions={activeTurn.suggestedPrompts} onSelect={onAcceptSuggestion} />
        )}
        {activeTurn.status === 'rateLimited' && (
          <LimitNotice
            code="RATE_LIMITED"
            retryAt={activeTurn.retryAt}
            onRetry={retryActive}
          />
        )}
        {activeTurn.status === 'budgetExhausted' && (
          <LimitNotice code="DAILY_BUDGET_EXHAUSTED" />
        )}
        {activeTurn.status === 'error' && <ErrorNotice onRetry={retryActive} />}

        {retained?.status === 'success' && (
          <div className="rounded-sm border border-dashed border-border p-3">
            <p className="text-xs uppercase tracking-wide text-muted">Last successful results</p>
            <div className="mt-2">
              <ProductGrid products={retained.products} variant="search" onOpen={onOpenProduct} />
            </div>
          </div>
        )}
      </>}
    </section>
  )
}
