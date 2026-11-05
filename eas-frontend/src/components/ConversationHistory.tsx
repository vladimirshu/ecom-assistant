import type { Turn } from '../session/useSearchSession'
import { CustomerMessage } from './CustomerMessage'
import { AssistantReply } from './AssistantReply'

export function ConversationHistory({
  turns,
  activeTurnId,
  onSelectTurn,
  onRetry,
}: {
  turns: Turn[]
  activeTurnId: string | null
  onSelectTurn: (turnId: string) => void
  onRetry: (turnId: string) => void
}) {
  return (
    <ol aria-label="Conversation history" className="m-0 list-none space-y-4 p-0">
      {turns.map((turn) => (
        <li
          key={turn.id}
          className={`rounded-sm border px-3 py-2 ${turn.id === activeTurnId ? 'border-action bg-wash/30' : 'border-transparent'}`}
        >
          <CustomerMessage text={turn.customerText} />
          <AssistantReply
            turn={turn}
            onShowMatches={() => onSelectTurn(turn.id)}
            onRetry={turn === turns.at(-1) ? () => onRetry(turn.id) : undefined}
          />
        </li>
      ))}
    </ol>
  )
}
