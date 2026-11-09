import type { Turn } from '../session/useSearchSession'

function summaryFor(turn: Turn): string {
  switch (turn.status) {
    case 'pending':
      return 'Searching…'
    case 'success':
      return turn.products.length === 1 ? '1 match' : `${turn.products.length} matches`
    case 'empty':
      return 'Nothing matched that description'
    case 'rejected':
      return "Let's try that another way"
    case 'rateLimited':
      return 'That was a little too fast'
    case 'budgetExhausted':
      return "Today's quota is used up"
    case 'error':
      return 'Something went wrong on our side'
  }
}

export function AssistantReply({
  turn,
  onSelectTurn,
  onRetry,
}: {
  turn: Turn
  onSelectTurn: () => void
  onRetry?: () => void
}) {
  const canShowMatches = turn.status === 'success' || turn.status === 'empty'
  const canRetry = turn.status === 'error' && onRetry

  return (
    <div className="text-sm text-ink">
      <span>{summaryFor(turn)}</span>
      {turn.status !== 'pending' && (
        <button
          type="button"
          onClick={onSelectTurn}
          className="ml-2 text-action underline-offset-2 hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
        >
          {canShowMatches ? 'Show these matches' : 'View feedback'}
        </button>
      )}
      {canRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="ml-2 text-action underline-offset-2 hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
        >
          Try again
        </button>
      )}
    </div>
  )
}
